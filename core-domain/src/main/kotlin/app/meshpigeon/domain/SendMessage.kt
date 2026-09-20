package app.meshpigeon.domain

import app.meshpigeon.protocol.Airtime
import app.meshpigeon.protocol.AckTracker
import app.meshpigeon.protocol.BouncyMeshCrypto
import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.DeliveryState
import app.meshpigeon.protocol.IdentityKeyPair
import app.meshpigeon.protocol.Messages
import app.meshpigeon.protocol.MeshCrypto
import app.meshpigeon.protocol.PacketCodec
import kotlinx.coroutines.flow.first

/**
 * Outgoing-message orchestrator (06 §4): builds the packet, enqueues to the
 * outbox with ACK tracking, and drives the per-conversation FIFO queue
 * (one in flight, 11 §2.2). The radio never retries — this class does.
 */
class SendMessage(
    private val identities: IdentityRepository,
    private val contacts: ContactRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val outbox: OutboxRepository,
    private val channels: ChannelRepository,
    private val ackTracker: AckTracker,
    private val pathCache: PathCache2,
    private val crypto: MeshCrypto,
    private val airtimeEstimator: AirtimeEstimator,
    private val wallClockSec: () -> Long,
    private val wallClockMs: () -> Long,
) {
    /**
     * Queue a DM for sending. Returns the message id. The radio loop flushes
     * the outbox to the radio and retransmits on AckTracker.tick results.
     */
    suspend fun queueDirect(peerPublicKey: ByteArray, text: String, replyToId: Long? = null): Long {
        val identity = identities.active().first() ?: error("no active identity")
        val contact = contacts.byPublicKey(identity.id, peerPublicKey)
            ?: error("unknown contact")
        val convId = conversations.ensure(identity.id, ConversationKind.DM, contact.id)
        return enqueueDirect(identity, contact, convId, text, replyToId)
    }

    private suspend fun enqueueDirect(
        identity: Identity,
        contact: Contact,
        convId: Long,
        text: String,
        replyToId: Long? = null,
        retryOf: Long? = null, // update this message row instead of inserting
    ): Long {
        val budget = AckTracker.textBudget(isGroup = false)
        require(text.toByteArray(Charsets.UTF_8).size <= budget) {
            "message exceeds protocol budget ($budget bytes)"
        }

        val timestamp = wallClockSec().coerceAtMost(Int.MAX_VALUE.toLong())
        val route = pathCache.routeFor(contact.publicKey[0].toInt() and 0xFF)
        val keypair = IdentityKeyPair(identity.publicKey, identity.privateKeyEnc)
        // One build serves both routes: the flood packet carries the expected
        // ACK; direct-routed just rewrites the header path.
        val flood = Messages.buildDirectMessage(crypto, keypair, contact.publicKey, timestamp, text)
        val built = if (route != null && route.hopCount > 0) {
            Messages.reRouteToDirect(flood.raw, route)
        } else {
            flood.raw
        }

        val msgId = if (retryOf != null) {
            messages.update(
                (messages.byId(retryOf) ?: return 0).copy(
                    state = DeliveryState.QUEUED,
                    sentAt = wallClockMs(),
                    ackKey = flood.expectedAck,
                ),
            )
            retryOf
        } else {
            messages.insert(
                Message(
                    id = 0,
                    conversationId = convId,
                    identityId = identity.id,
                    body = text,
                    sentAt = wallClockMs(),
                    out = true,
                    state = DeliveryState.QUEUED,
                    replyToId = replyToId,
                    ackKey = flood.expectedAck,
                ),
            )
        }
        outbox.enqueue(
            OutboxEntry(
                id = 0,
                conversationId = convId,
                messageId = msgId,
                packet = built,
                ackKey = flood.expectedAck,
                attempts = 0,
                nextRetryAt = 0,
                state = DeliveryState.QUEUED,
                airtimeMs = airtimeEstimator.estimate(built.size),
                hops = route?.hopCount ?: 0,
                direct = route != null && route.hopCount > 0,
            ),
        )
        return msgId
    }

    /** Queue an encrypted channel message (GRP_TXT, flood, no ACKs). */
    suspend fun queueGroup(channel: Channel, text: String): Long {
        val identity = identities.active().first() ?: error("no active identity")
        // The Public channel has its own PUBLIC-kind conversation (07 §6);
        // don't fork a second row under GROUP for the same channel.
        val kind = if (channel.kind == ChannelKind.PUBLIC) ConversationKind.PUBLIC else ConversationKind.GROUP
        val convId = conversations.ensure(identity.id, kind, channel.id)
        return enqueueGroup(identity, channel, convId, text)
    }

    private suspend fun enqueueGroup(identity: Identity, channel: Channel, convId: Long, text: String, retryOf: Long? = null): Long {
        val prefix = "${identity.name}: "
        val budget = AckTracker.textBudget(isGroup = true, senderPrefixLen = prefix.length)
        require(text.toByteArray(Charsets.UTF_8).size <= budget) {
            "message exceeds protocol budget ($budget bytes)"
        }
        val timestamp = wallClockSec().coerceAtMost(Int.MAX_VALUE.toLong())
        val raw = Messages.buildGroupMessage(
            Channels.Channel(channel.name, channel.keyEnc),
            timestamp,
            prefix + text,
        )
        val msgId = if (retryOf != null) {
            messages.update(
                (messages.byId(retryOf) ?: return 0).copy(state = DeliveryState.QUEUED, sentAt = wallClockMs()),
            )
            retryOf
        } else {
            messages.insert(
                Message(
                    id = 0,
                    conversationId = convId,
                    identityId = identity.id,
                    body = text,
                    sentAt = wallClockMs(),
                    out = true,
                    state = DeliveryState.QUEUED,
                ),
            )
        }
        outbox.enqueue(
            OutboxEntry(
                id = 0,
                conversationId = convId,
                messageId = msgId,
                packet = raw,
                ackKey = null, // group messages have no ACKs
                attempts = 0,
                nextRetryAt = 0,
                state = DeliveryState.QUEUED,
                airtimeMs = airtimeEstimator.estimate(raw.size),
                hops = 0,
                direct = false,
            ),
        )
        return msgId
    }

    /**
     * Retry affordance (07 §4): a FAILED message re-enters the outbox.
     * Rebuilds the packet with a fresh timestamp (the old one is gone from
     * the outbox), so the expected ACK key changes with it.
     */
    suspend fun requeue(messageId: Long): Boolean {
        val msg = messages.byId(messageId) ?: return false
        if (!msg.out || msg.state != DeliveryState.FAILED) return false
        val identity = identities.active().first() ?: return false
        val conv = conversations.byId(msg.conversationId) ?: return false
        when (conv.kind) {
            ConversationKind.DM -> {
                val contact = contacts.byId(conv.refId ?: return false) ?: return false
                enqueueDirect(identity, contact, conv.id, msg.body, msg.replyToId, retryOf = msg.id)
            }
            ConversationKind.GROUP, ConversationKind.PUBLIC -> {
                val channel = channels.byId(conv.refId ?: return false) ?: return false
                enqueueGroup(identity, channel, conv.id, msg.body, retryOf = msg.id)
            }
            ConversationKind.TRACE_LOG -> return false
        }
        return true
    }
}

/** SF/BW/CR for airtime estimates come from the connected radio's settings. */
interface AirtimeEstimator {
    fun estimate(packetLen: Int): Double
}

/** Default estimator using the current region's LoRa parameters. */
class DefaultAirtimeEstimator(settings: () -> AirtimeParams) : AirtimeEstimator {
    data class AirtimeParams(val sf: Int, val bandwidthKhz: Double, val codingRateDenominator: Int)

    private val params by lazy { settings() }

    override fun estimate(packetLen: Int): Double =
        Airtime.estimateMs(packetLen, params.sf, params.bandwidthKhz, params.codingRateDenominator)
}

/**
 * Path cache port. The concrete [app.meshpigeon.protocol.PathCache] lives in
 * the protocol layer; domain depends on this interface so the DB-backed
 * implementation (:core-data) can persist learned paths (03 §4).
 */
interface PathCache2 {
    fun routeFor(destHash: Int): app.meshpigeon.protocol.Path?
    fun learn(destHash: Int, path: app.meshpigeon.protocol.Path)
    fun onAck(destHash: Int, usedPath: app.meshpigeon.protocol.Path?)
    fun onExhausted(destHash: Int)
}

/** In-memory default (tests, first run). */
class InMemoryPathCache(private val clock: () -> Long) : PathCache2 {
    private val paths = HashMap<Int, app.meshpigeon.protocol.Path>()

    override fun routeFor(destHash: Int): app.meshpigeon.protocol.Path? = paths[destHash]
    override fun learn(destHash: Int, path: app.meshpigeon.protocol.Path) {
        paths[destHash] = path
    }
    override fun onAck(destHash: Int, usedPath: app.meshpigeon.protocol.Path?) {}
    override fun onExhausted(destHash: Int) {
        paths[destHash] = app.meshpigeon.protocol.Path(app.meshpigeon.protocol.PathHashSize.THREE, emptyList())
    }
}
