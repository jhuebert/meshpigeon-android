package app.meshpigeon.domain

import app.meshpigeon.protocol.Ack
import app.meshpigeon.protocol.AckTracker
import app.meshpigeon.protocol.AdvertAppData
import app.meshpigeon.protocol.AdvertPayload
import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.ClockMapper
import app.meshpigeon.protocol.Crypto
import app.meshpigeon.protocol.DeliveryState
import app.meshpigeon.protocol.GroupDataPayload
import app.meshpigeon.protocol.GroupDataTypes
import app.meshpigeon.protocol.IdentityKeyPair
import app.meshpigeon.protocol.Messages
import app.meshpigeon.protocol.MeshCrypto
import app.meshpigeon.protocol.PacketCodec
import app.meshpigeon.protocol.PacketSpec
import app.meshpigeon.protocol.RawPacket
import app.meshpigeon.protocol.TxtMsgPayload
import kotlinx.coroutines.flow.first

/**
 * Incoming-packet orchestrator (06 §4): dedup by tag → decode → route by
 * payload type (DM / group / public / advert / ACK) → persist + notification
 * decision. Pure Kotlin; adversarial inputs are dropped, never crash (09 §1).
 */
class ReceivePipeline(
    private val identities: IdentityRepository,
    private val contacts: ContactRepository,
    private val channels: ChannelRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val tagCache: PacketTagCache,
    private val ackTracker: AckTracker,
    private val pathCache: PathCache2,
    private val clockMapper: ClockMapper,
    private val crypto: MeshCrypto,
    private val notifier: Notifier,
) {
    /** Notification decision output for the service layer. */
    data class Notification(
        val conversationId: Long,
        val title: String,
        val body: String,
        val isRequest: Boolean,
    )

    interface Notifier {
        suspend fun notify(notification: Notification)
    }

    /** ACKs to transmit — drained by the radio loop (fast path). */
    val pendingAcks = ArrayDeque<ByteArray>()

    /**
     * Handle one packet received off the air (or replayed from the store).
     * `rssi/snr/hops` are the persisted reception metadata (03 §7);
     * `radioUptimeMs` maps to wall clock through the [ClockMapper].
     */
    suspend fun onPacket(
        raw: ByteArray,
        rssi: Int? = null,
        snr: Int? = null,
        hops: Int? = null,
        radioUptimeMs: Long? = null,
        wallClock: Long? = null,
    ) {
        val identity = identities.active().first() ?: return
        val tag = PacketCodec.packetTag(raw)
        if (!tagCache.remember(tag)) return // duplicate — dropped (03 §4)

        val packet = PacketCodec.decode(raw) ?: return
        val recvAt = wallClock
            ?: (radioUptimeMs?.let { clockMapper.toWallClock(it) } ?: System.currentTimeMillis())

        when (packet.payloadType) {
            PacketSpec.PAYLOAD_TXT_MSG -> onDirectTxt(identity, packet, rssi, snr, hops, recvAt)
            PacketSpec.PAYLOAD_GRP_TXT -> onGroupTxt(identity, packet, rssi, snr, hops, recvAt)
            PacketSpec.PAYLOAD_GRP_DATA -> onGroupData(identity, packet)
            PacketSpec.PAYLOAD_ACK -> onAck(identity, packet)
            PacketSpec.PAYLOAD_ADVERT -> onAdvert(identity, packet, recvAt)
            else -> Unit // REQ/RESPONSE/PATH/TRACE land with later milestones
        }
    }

    /** Encrypted DM — opens only with the sender's known public key. */
    private suspend fun onDirectTxt(
        identity: Identity,
        packet: RawPacket,
        rssi: Int?,
        snr: Int?,
        hops: Int?,
        recvAt: Long,
    ) {
        val known = contacts.observe(identity.id).first().filter { !it.isBlocked }
        val decoded = Messages.decodeDirectMessage(crypto, keypair(identity), packet, known.map { it.publicKey })
            ?: return // not for us, unknown sender, or bad MAC → drop silently

        val sender = known.first { (it.publicKey[0].toInt() and 0xFF) == decoded.srcHash }
        // learn the reply path from the incoming direct route (03 §4)
        if (!packet.isFlood && packet.path.hopCount > 0) {
            pathCache.learn(sender.publicKey[0].toInt() and 0xFF, packet.path)
        }

        val convId = conversations.ensure(identity.id, ConversationKind.DM, sender.id)
        // Unknown-sender DMs become request conversations (07 §5): never
        // silently dropped, never auto-opened — accepted from the banner.
        val conv = conversations.byId(convId)
        val isRequest = !sender.accepted
        if (conv != null && conv.isRequest != isRequest) {
            conversations.update(conv.copy(isRequest = isRequest))
        }
        conversations.bumpUnread(convId, 1)
        messages.insert(
            Message(
                id = 0,
                conversationId = convId,
                identityId = identity.id,
                senderContactId = sender.id,
                senderName = sender.name,
                body = decoded.text,
                sentAt = recvAt,
                out = false,
                rssi = rssi,
                snr = snr?.toFloat(),
                hops = hops,
                packetTag = PacketCodec.packetTag(PacketCodec.encode(packet)),
            ),
        )
        // ACK the sender with their expected-ACK checksum (flood)
        pendingAcks.add(
            Ack.compute(
                TxtMsgPayload(decoded.timestamp, decoded.txtType, decoded.attempt, decoded.text).encode(),
                sender.publicKey,
            ),
        )
        if (NotificationPolicy.shouldNotify(
                NotificationPolicy.effectiveMode(conv, null),
                isDirect = true, body = decoded.text, myName = identity.name,
            )
        ) {
            notifier.notify(Notification(convId, sender.name, decoded.text, isRequest = isRequest))
        }
    }

    private suspend fun onGroupTxt(
        identity: Identity,
        packet: RawPacket,
        rssi: Int?,
        snr: Int?,
        hops: Int?,
        recvAt: Long,
    ) {
        val channelHash = packet.payload.getOrNull(0)?.toInt()?.and(0xFF) ?: return
        val channel = channels.observe(identity.id).first()
            .firstOrNull { Channels.channelHash(it.keyEnc) == channelHash } ?: return // unknown channel
        val framed = packet.payload.copyOfRange(1, packet.payload.size)
        val plain = crypto.macThenDecrypt(channel.keyEnc, framed) ?: return

        val ts = Crypto.leU32At(plain, 0)
        val text = TxtMsgPayload.nulTerminated(plain, 5) // "name: text"
        // Blocked senders never render in groups either (07 §7); group
        // traffic carries only the display name, so match by name.
        val senderName = senderNameOf(text)
        if (contacts.observeBlocked(identity.id).first().any { it.name.equals(senderName, ignoreCase = true) }) {
            return
        }
        // The Public channel is a first-class conversation (07 §6); other
        // channels get GROUP conversations.
        val convId = conversations.ensure(
            identity.id,
            if (channel.kind == ChannelKind.PUBLIC) ConversationKind.PUBLIC else ConversationKind.GROUP,
            channel.id,
        )
        conversations.bumpUnread(convId, 1)
        messages.insert(
            Message(
                id = 0,
                conversationId = convId,
                identityId = identity.id,
                senderName = senderName,
                body = bodyOf(text),
                // group ts is unix seconds; guard nonsense values
                sentAt = if (ts in 1_000_000_000..4_000_000_000L) ts * 1000 else recvAt,
                out = false,
                rssi = rssi,
                snr = snr?.toFloat(),
                hops = hops,
                packetTag = PacketCodec.packetTag(PacketCodec.encode(packet)),
            ),
        )
        val conv = conversations.byId(convId)
        // Calm mesh default (07 §8): channels notify only on mentions.
        if (NotificationPolicy.shouldNotify(
                NotificationPolicy.effectiveMode(conv, channel),
                isDirect = false, body = bodyOf(text), myName = identity.name,
            )
        ) {
            notifier.notify(Notification(convId, channel.name, bodyOf(text), isRequest = false))
        }
    }

    private suspend fun onGroupData(identity: Identity, packet: RawPacket) {
        val channelHash = packet.payload.getOrNull(0)?.toInt()?.and(0xFF) ?: return
        val channel = channels.observe(identity.id).first()
            .firstOrNull { Channels.channelHash(it.keyEnc) == channelHash } ?: return
        val framed = packet.payload.copyOfRange(1, packet.payload.size)
        val plain = crypto.macThenDecrypt(channel.keyEnc, framed) ?: return
        val (dtype, data) = GroupDataPayload.decodePlaintext(plain) ?: return
        // Reactions/receipts/typing are app-level conventions (03 §6); v1
        // records reactions as messages so nothing is lost.
        if (dtype == GroupDataTypes.REACTION && data.isNotEmpty()) {
            val convId = conversations.ensure(
                identity.id,
                if (channel.kind == ChannelKind.PUBLIC) ConversationKind.PUBLIC else ConversationKind.GROUP,
                channel.id,
            )
            messages.insert(
                Message(
                    id = 0,
                    conversationId = convId,
                    identityId = identity.id,
                    body = data.decodeToString(),
                    kind = MessageKind.REACTION,
                    sentAt = System.currentTimeMillis(),
                    out = false,
                ),
            )
        }
    }

    private suspend fun onAck(identity: Identity, packet: RawPacket) {
        if (packet.payload.size < 4) return
        val key = packet.payload.copyOf(4)
        val confirmed = ackTracker.onAcked(key) || ackTracker.onLateAck(key)
        if (confirmed) {
            messages.byAckKey(identity.id, key)?.let { msg ->
                messages.updateState(msg.id, DeliveryState.CONFIRMED)
            }
        }
    }

    private suspend fun onAdvert(identity: Identity, packet: RawPacket, recvAt: Long) {
        val adv = try {
            AdvertPayload.decode(packet.payload)
        } catch (_: Exception) {
            return
        }
        val verified = crypto.verify(
            adv.publicKey, adv.signature,
            adv.publicKey, Crypto.leU32(adv.timestamp), adv.appData.encode(),
        )
        if (!verified) return // forged advert — drop

        val existing = contacts.byPublicKey(identity.id, adv.publicKey)
        val app = adv.appData
        val isRepeater = app.flags and AdvertAppData.FLAG_REPEATER != 0
        if (existing != null) {
            contacts.upsert(
                existing.copy(
                    lastSeenAt = recvAt,
                    lastLatitude = app.latitude ?: existing.lastLatitude,
                    lastLongitude = app.longitude ?: existing.lastLongitude,
                    isRepeater = isRepeater || existing.isRepeater,
                ),
            )
        } else {
            contacts.upsert(
                Contact(
                    id = 0,
                    identityId = identity.id,
                    publicKey = adv.publicKey,
                    name = app.name ?: "node-%02x".format(adv.publicKey[0].toInt() and 0xFF),
                    firstSeenAt = recvAt,
                    lastSeenAt = recvAt,
                    source = ContactSource.ADVERT,
                    flags = app.flags,
                    lastLatitude = app.latitude,
                    lastLongitude = app.longitude,
                    isRepeater = isRepeater,
                    accepted = false, // pending until the user adds them (07 §5)
                ),
            )
            notifier.notify(
                Notification(0, "New contact discovered", app.name ?: "Unknown node", isRequest = true),
            )
        }
    }

    private fun keypair(identity: Identity) = IdentityKeyPair(identity.publicKey, identity.privateKeyEnc)

    private fun senderNameOf(text: String): String =
        text.substringBefore(": ").ifBlank { "Unknown" }

    private fun bodyOf(text: String): String =
        text.substringAfter(": ", missingDelimiterValue = text)
}
