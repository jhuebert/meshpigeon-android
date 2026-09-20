package app.meshpigeon.domain

import app.meshpigeon.protocol.Ack
import app.meshpigeon.protocol.AckTracker
import app.meshpigeon.protocol.BouncyMeshCrypto
import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.ClockMapper
import app.meshpigeon.protocol.DeliveryState
import app.meshpigeon.protocol.Messages
import app.meshpigeon.protocol.PacketCodec
import app.meshpigeon.protocol.PacketSpec
import app.meshpigeon.protocol.Path
import app.meshpigeon.protocol.PathHashSize
import app.meshpigeon.protocol.TxtMsgPayload
import app.meshpigeon.transport.FakeRadioAdapter
import app.meshpigeon.transport.RadioSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private var clockNow = 1_700_000_000_000L

class DomainUseCasesTest {

    private val crypto = BouncyMeshCrypto()
    private lateinit var identities: FakeIdentityRepository
    private lateinit var contacts: FakeContactRepository
    private lateinit var channels: FakeChannelRepository
    private lateinit var conversations: FakeConversationRepository
    private lateinit var messages: FakeMessageRepository
    private lateinit var outbox: FakeOutboxRepository
    private lateinit var targets: FakeRadioTargetRepository
    private lateinit var tagCache: PacketTagCache
    private lateinit var clockMapper: ClockMapper
    private lateinit var pipeline: ReceivePipeline
    private val ackTracker = AckTracker({ clockNow })
    private val pathCache = InMemoryPathCache({ clockNow })
    private val notifications = mutableListOf<ReceivePipeline.Notification>()

    @Before
    fun setUp() = runTest {
        identities = FakeIdentityRepository()
        contacts = FakeContactRepository()
        channels = FakeChannelRepository()
        conversations = FakeConversationRepository()
        messages = FakeMessageRepository()
        outbox = FakeOutboxRepository()
        targets = FakeRadioTargetRepository()
        tagCache = PacketTagCache()
        clockMapper = ClockMapper()
        clockNow = 1_700_000_000_000L
        notifications.clear()
        pipeline = ReceivePipeline(
            identities, contacts, channels, conversations, messages,
            tagCache, ackTracker, pathCache, clockMapper, crypto,
            object : ReceivePipeline.Notifier {
                override suspend fun notify(notification: ReceivePipeline.Notification) {
                    notifications.add(notification)
                }
            },
        )
    }

    private suspend fun me(): Identity {
        val keys = crypto.newIdentity()
        identities.upsert(
            Identity(
                id = 0, name = "mia", publicKey = keys.publicKey,
                privateKeyEnc = keys.privateKey, flags = 0, createdAt = 0,
                isActive = true, advertPolicy = AdvertPolicy.MANUAL,
            ),
        )
        return identities.active().first()!!
    }

    @Test
    fun `direct message queues to outbox with envelope`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(
            Contact(0, identity.id, peer.publicKey, "bob", firstSeenAt = 0),
        )
        val send = SendMessage(
            identities, contacts, conversations, messages, outbox,
            channels, ackTracker, pathCache, tagCache, crypto,
            airtimeEstimator = object : AirtimeEstimator {
                override fun estimate(packetLen: Int) = 100.0
            },
            wallClockSec = { clockNow / 1_000 },
            wallClockMs = { clockNow },
        )
        send.queueDirect(peer.publicKey, "hey bob")
        val entry = outbox.store.value.single()
        assertTrue(entry.packet.size in 20..184)
        val decoded = PacketCodec.decode(entry.packet)!!
        assertEquals(PacketSpec.PAYLOAD_TXT_MSG, decoded.payloadType)
        assertEquals(PacketSpec.ROUTE_FLOOD, decoded.routeType)
        // the expected ACK rides on both the outbox row and the message row
        assertNotNull(entry.ackKey)
        val msg = messages.store.value.single()
        assertTrue(msg.ackKey!!.contentEquals(entry.ackKey!!))
        assertEquals(msg.id, entry.messageId)
    }

    @Test
    fun `over-budget message is rejected`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", 0))
        val send = SendMessage(
            identities, contacts, conversations, messages, outbox,
            channels, ackTracker, pathCache, tagCache, crypto,
            airtimeEstimator = object : AirtimeEstimator {
                override fun estimate(packetLen: Int) = 100.0
            },
            wallClockSec = { clockNow / 1_000 },
            wallClockMs = { clockNow },
        )
        val tooLong = "x".repeat(300)
        try {
            send.queueDirect(peer.publicKey, tooLong)
            throw AssertionError("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("budget"))
        }
    }

    @Test
    fun `end to end dm confirms over the scripted radio`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", firstSeenAt = 0))
        newSendMessage().queueDirect(peer.publicKey, "hello bob")

        val adapter = FakeRadioAdapter()
        val session = RadioSession(adapter, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        kotlinx.coroutines.runBlocking { session.start() }
        val flush = FlushOutbox(outbox, messages, ackTracker, { clockNow })
        flush.resume()

        // the service loop: flush → SEND_PACKET, and route async frames back
        val collector = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            session.events.collect { frame ->
                if (frame.cmd != app.meshpigeon.transport.RadioFrame.CMD_RX_PACKET || frame.nonce != 0) return@collect
                val entry = session.parsePacketEntry(frame.payload) ?: return@collect
                pipeline.onPacket(raw = entry.raw, rssi = entry.rssi, snr = entry.snr, radioUptimeMs = entry.uptimeMs)
                if (PacketCodec.decode(entry.raw)?.payloadType == PacketSpec.PAYLOAD_ACK) {
                    flush.onConfirmed(PacketCodec.decode(entry.raw)!!.payload)
                }
            }
        }
        kotlinx.coroutines.runBlocking {
            val tx = flush.tick()
            assertEquals(1, tx.size)
            session.sendPacket(tx.single().entry.packet)

            // the peer received and ACKed: it computes the same checksum
            val expectedAck = Ack.compute(
                app.meshpigeon.protocol.TxtMsgPayload(clockNow / 1_000, PacketSpec.TXT_TYPE_PLAIN, 0, "hello bob").encode(),
                identity.publicKey,
            )
            adapter.injectPacket(Messages.buildAck(expectedAck))
        }
        collector.cancel()

        assertEquals(DeliveryState.CONFIRMED, messages.store.value.single().state)
        assertTrue(outbox.store.value.isEmpty())

        // and a lost ACK retransmits on the tracker's physics timeout
        newSendMessage().queueDirect(peer.publicKey, "retry me")
        flush.resume()
        assertEquals(1, flush.tick().size)
        clockNow += 3_000
        val retry = flush.tick().single()
        assertTrue(retry.isRetry)
    }

    @Test
    fun `received DM opens, persists and acks`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", 0))
        val dm = Messages.buildDirectMessage(crypto, peer, identity.publicKey, 1_700_000_000L, "yo mia")
        pipeline.onPacket(raw = dm.raw, rssi = -71, snr = 9, radioUptimeMs = 1000, wallClock = clockNow)

        val msg = messages.store.value.single()
        assertEquals("yo mia", msg.body)
        assertEquals(-71, msg.rssi)
        assertEquals("bob", msg.senderName)
        // ACK was queued for the radio loop
        assertEquals(1, pipeline.pendingAcks.size)
        // ack matches what bob expects
        val expected = app.meshpigeon.protocol.Ack.compute(
            app.meshpigeon.protocol.TxtMsgPayload(1_700_000_000L, 0, 0, "yo mia").encode(),
            peer.publicKey,
        )
        assertTrue(pipeline.pendingAcks.single().contentEquals(expected))
    }

    @Test
    fun `duplicate packets are dropped by tag`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", 0))
        val dm = Messages.buildDirectMessage(crypto, peer, identity.publicKey, 1_700_000_000L, "yo")
        pipeline.onPacket(dm.raw)
        pipeline.onPacket(dm.raw) // rebroadcast
        assertEquals(1, messages.store.value.size)
    }

    @Test
    fun `group message lands in its conversation`() = runTest {
        val identity = me()
        val ch = Channels.Channel("hikers", Channels.hashtagKey("hikers"))
        channels.upsert(
            Channel(0, identity.id, ch.name, ch.secret, ChannelKind.HASHTAG, 0),
        )
        val raw = Messages.buildGroupMessage(ch, 1_700_000_001L, "li: heading out")
        pipeline.onPacket(raw, wallClock = clockNow)
        val msg = messages.store.value.single()
        assertEquals("heading out", msg.body)
        assertEquals("li", msg.senderName)
        assertEquals(ConversationKind.GROUP, conversations.store.value.single().kind)
        assertEquals(1, conversations.store.value.single().unreadCount)
    }

    @Test
    fun `unknown channel traffic is ignored`() = runTest {
        val identity = me()
        val unknown = Channels.Channel("secret-society", Channels.privateKey())
        val raw = Messages.buildGroupMessage(unknown, 1_700_000_002L, "x: y")
        pipeline.onPacket(raw, wallClock = clockNow)
        assertEquals(0, messages.store.value.size)
    }

    @Test
    fun `blocked sender never renders`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        val stored = contacts.upsert(Contact(0, identity.id, peer.publicKey, "spam", 0))
        contacts.block(stored)
        val dm = Messages.buildDirectMessage(crypto, peer, identity.publicKey, 1_700_000_003L, "buy stuff")
        pipeline.onPacket(dm.raw, wallClock = clockNow)
        assertEquals(0, messages.store.value.size)
    }

    @Test
    fun `ack flips message to confirmed and dup ack is suppressed`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", 0))
        // outgoing message persisted with its ack key
        val ack = byteArrayOf(1, 2, 3, 4)
        val msgId = messages.insert(
            Message(0, 1, identity.id, body = "out", sentAt = 0, out = true, state = DeliveryState.SENT, ackKey = ack),
        )
        ackTracker.track(ack, 1, 100.0, 0, false)
        val raw = app.meshpigeon.protocol.PacketCodec.encode(
            PacketSpec.ROUTE_FLOOD, PacketSpec.PAYLOAD_ACK,
            Path(PathHashSize.THREE, emptyList()), ack,
        )
        pipeline.onPacket(raw)
        assertEquals(DeliveryState.CONFIRMED, messages.store.value.single { it.id == msgId }.state)
        // duplicate ACK suppressed
        pipeline.onPacket(raw)
        assertEquals(DeliveryState.CONFIRMED, messages.store.value.single { it.id == msgId }.state)
    }

    @Test
    fun `advert creates pending contact with location`() = runTest {
        val identity = me()
        val stranger = crypto.newIdentity()
        val app = app.meshpigeon.protocol.AdvertAppData(
            flags = app.meshpigeon.protocol.AdvertAppData.FLAG_CHAT_NODE or
                app.meshpigeon.protocol.AdvertAppData.FLAG_HAS_LOCATION or
                app.meshpigeon.protocol.AdvertAppData.FLAG_HAS_NAME,
            latitude = 41.25,
            longitude = -95.93,
            name = "riley",
        )
        val raw = Messages.buildAdvert(crypto, stranger, 1_700_000_004L, app)
        pipeline.onPacket(raw, wallClock = clockNow)
        val contact = contacts.store.value.single()
        assertEquals("riley", contact.name)
        assertEquals(41.25, contact.lastLatitude!!, 1e-6)
        assertEquals(-95.93, contact.lastLongitude!!, 1e-6)
        // forge attempt with wrong signature is dropped
        val forged = raw.copyOf().also { it[raw.size - 3] = (it[raw.size - 3] + 1).toByte() }
        pipeline.onPacket(forged, wallClock = clockNow)
        assertEquals(1, contacts.store.value.size)
    }

    @Test
    fun `send advert builds zero hop and flood packets for the active identity`() = runTest {
        val identity = me()
        val sendAdvert = SendAdvert(identities, crypto, { clockNow / 1000 })

        val zeroHop = sendAdvert.build(zeroHop = true)!!
        val zeroPacket = PacketCodec.decode(zeroHop)!!
        assertEquals(PacketSpec.ROUTE_DIRECT, zeroPacket.routeType)
        assertEquals(PacketSpec.PAYLOAD_ADVERT, zeroPacket.payloadType)
        val zeroAdv = app.meshpigeon.protocol.AdvertPayload.decode(zeroPacket.payload)
        assertArrayEquals(identity.publicKey, zeroAdv.publicKey)
        assertEquals("mia", zeroAdv.appData.name)
        assertTrue(crypto.verify(zeroAdv.publicKey, zeroAdv.signature, zeroAdv.publicKey,
            app.meshpigeon.protocol.Crypto.leU32(zeroAdv.timestamp), zeroAdv.appData.encode()))

        val flood = sendAdvert.build(zeroHop = false)!!
        assertEquals(PacketSpec.ROUTE_FLOOD, PacketCodec.decode(flood)!!.routeType)

        // no active identity -> nothing to build
        identities.delete(identity.id)
        assertNull(sendAdvert.build(zeroHop = true))
    }

    @Test
    fun `advert refresh updates existing contact without dup`() = runTest {
        val identity = me()
        val stranger = crypto.newIdentity()
        val app = app.meshpigeon.protocol.AdvertAppData(
            flags = app.meshpigeon.protocol.AdvertAppData.FLAG_CHAT_NODE or
                app.meshpigeon.protocol.AdvertAppData.FLAG_HAS_NAME,
            name = "riley",
        )
        pipeline.onPacket(Messages.buildAdvert(crypto, stranger, 1_700_000_004L, app), wallClock = clockNow)
        pipeline.onPacket(Messages.buildAdvert(crypto, stranger, 1_700_000_005L, app), wallClock = clockNow + 1)
        assertEquals(1, contacts.store.value.size)
    }

    @Test
    fun `sync history pulls store through pipeline with dedup`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", 0))
        val adapter = FakeRadioAdapter()
        val dm = Messages.buildDirectMessage(crypto, peer, identity.publicKey, 1_700_000_006L, "stored msg")
        adapter.store.addLast(
            app.meshpigeon.transport.PacketEntry(1, 100, -70, 9, 0x02, dm.raw),
        )
        // duplicate in the store (rebroadcast captured twice)
        adapter.store.addLast(
            app.meshpigeon.transport.PacketEntry(2, 150, -70, 9, 0x02, dm.raw),
        )
        val session = RadioSession(adapter, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        kotlinx.coroutines.runBlocking { session.start() }
        val sync = SyncRadioHistory(identities, PacketTagCache(), pipeline)
        val result = sync.sync(session, cursor = 0)
        assertEquals(2, result.fetched)
        assertEquals(1, result.newPackets) // duplicate collapsed
        assertEquals(1, messages.store.value.size)
    }

    @Test
    fun `connect selects preferred radio with silent fallback`() = runTest {
        targets.upsert(RadioTarget(1, "ble:AA", "Pocket Node", RadioLinkKind.BLE, "AA", prefOrder = 0, preferred = true))
        targets.upsert(RadioTarget(2, "ble:BB", "Shelf Radio", RadioLinkKind.BLE, "BB", prefOrder = 1, preferred = false))
        val connect = ConnectToRadio(targets)
        // BB is in the preference list (second) → normal selection
        val sel = connect.select(
            listOf(
                app.meshpigeon.transport.RadioTarget("ble:BB", "Shelf Radio", app.meshpigeon.transport.RadioLink.Ble("BB")),
            ),
        )!!
        assertTrue(!sel.fallbackUsed)
        assertEquals("Shelf Radio", sel.target.name)
        // a radio unknown to the list is a silent fallback
        val selFb = connect.select(
            listOf(
                app.meshpigeon.transport.RadioTarget("ble:CC", "Borrowed", app.meshpigeon.transport.RadioLink.Ble("CC")),
            ),
        )!!
        assertTrue(selFb.fallbackUsed)
        // preference order honored when both present
        val sel2 = connect.select(
            listOf(
                app.meshpigeon.transport.RadioTarget("ble:BB", "Shelf", app.meshpigeon.transport.RadioLink.Ble("BB")),
                app.meshpigeon.transport.RadioTarget("ble:AA", "Pocket", app.meshpigeon.transport.RadioLink.Ble("AA")),
            ),
        )!!
        assertEquals("Pocket", sel2.target.name)
        assertTrue(!sel2.fallbackUsed)
        // one-time override
        val sel3 = connect.select(emptyList(), override = app.meshpigeon.transport.RadioTarget("usb:x", "OTG", app.meshpigeon.transport.RadioLink.Usb(1, 2, "x")))
        assertEquals("OTG", sel3!!.target.name)
        assertTrue(!sel3.fallbackUsed)
    }

    @Test
    fun `settings divergence banner triggers only on real difference`() {
        val guard = RadioSettingsGuard()
        val radio = RadioSession.RadioSettings(1, 2, 869_525_000, 12_500, 9, 5, 14, 7)
        guard.onRadioSettings(radio, radio.copy(configEpoch = 99))
        assertNull(guard.current.value) // same tuning, different epoch → fine
        guard.onRadioSettings(radio, radio.copy(freqHz = 906_875_000))
        assertNotNull(guard.current.value) // US vs EU → banner
        guard.resolve()
        assertNull(guard.current.value)
    }

    @Test
    fun `flush sends fifo per conversation and ack confirms`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", firstSeenAt = 0))
        val send = newSendMessage()
        send.queueDirect(peer.publicKey, "one")
        send.queueDirect(peer.publicKey, "two")

        val flush = FlushOutbox(outbox, messages, ackTracker, { clockNow })
        flush.resume()
        val tx = flush.tick()
        assertEquals(1, tx.size) // FIFO: only the first per conversation
        assertEquals(0, flush.tick().size) // conversation stays busy until ACK

        val inFlight = outbox.store.value.single { it.state == DeliveryState.SENT }
        assertTrue(ackTracker.onAcked(inFlight.ackKey!!))
        flush.onConfirmed(inFlight.ackKey!!)
        assertTrue(outbox.store.value.none { it.ackKey?.contentEquals(inFlight.ackKey) == true })
        assertEquals(DeliveryState.CONFIRMED, messages.store.value.single { it.body == "one" }.state)

        val next = flush.tick()
        assertEquals(1, next.size)
        assertEquals("two", messages.store.value.single { it.id == next.single().entry.messageId }.body)
        ackTracker.onAcked(next.single().entry.ackKey!!)
        flush.onConfirmed(next.single().entry.ackKey!!)
        assertTrue(outbox.store.value.isEmpty())
    }

    @Test
    fun `flush finalizes exhausted sends as failed and late ack confirms`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", firstSeenAt = 0))
        val send = newSendMessage()
        send.queueDirect(peer.publicKey, "lost")

        val flush = FlushOutbox(outbox, messages, ackTracker, { clockNow })
        flush.resume()
        assertEquals(1, flush.tick().size)
        repeat(6) { clockNow += 60_000; flush.tick() } // exhaust retries → FAILED
        assertTrue(outbox.store.value.isEmpty())
        assertEquals(DeliveryState.FAILED, messages.store.value.single().state)

        // a late ACK inside the grace window still confirms (pipeline path)
        pipeline.onPacket(Messages.buildAck(messages.store.value.single().ackKey!!))
        assertEquals(DeliveryState.CONFIRMED, messages.store.value.single().state)
    }

    @Test
    fun `resume requeues rows orphaned in flight`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", firstSeenAt = 0))
        newSendMessage().queueDirect(peer.publicKey, "stuck")
        // a claim orphaned by a restart: in-flight, never ACKed
        outbox.update(outbox.store.value.single().copy(state = DeliveryState.SENT))

        val flush = FlushOutbox(outbox, messages, ackTracker, { clockNow })
        flush.resume()
        assertEquals(DeliveryState.QUEUED, outbox.store.value.single().state)
        assertEquals(1, flush.tick().size)
    }

    @Test
    fun `dm from a stranger opens as a request conversation`() = runTest {
        val identity = me()
        val stranger = crypto.newIdentity()
        // stranger heard via advert: pending, never accepted
        contacts.upsert(Contact(0, identity.id, stranger.publicKey, "riley", 0, accepted = false))
        pipeline.onPacket(
            Messages.buildDirectMessage(crypto, stranger, identity.publicKey, 1_700_000_006L, "hi there").raw,
            wallClock = clockNow,
        )
        val conv = conversations.store.value.single()
        assertTrue(conv.isRequest)
        assertEquals(ConversationKind.DM, conv.kind)
        assertEquals(1, notifications.size)
        assertTrue(notifications.single().isRequest)

        // once accepted, later dms land in the normal conversation
        contacts.setAccepted(contacts.store.value.single().id, true)
        pipeline.onPacket(
            Messages.buildDirectMessage(crypto, stranger, identity.publicKey, 1_700_000_007L, "again").raw,
            wallClock = clockNow + 1,
        )
        assertTrue(!conversations.store.value.single().isRequest)
        assertEquals(1, conversations.store.value.size)
    }

    @Test
    fun `notification policy matrix is applied`() = runTest {
        val identity = me()
        suspend fun channel(name: String, mode: NotifyMode, kind: ChannelKind = ChannelKind.HASHTAG): Channel {
            val ch = Channels.Channel(name, Channels.hashtagKey(name))
            return Channel(0, identity.id, name, ch.secret, kind, 0, notifyMode = mode).also {
                channels.upsert(it)
            }
        }

        // default channel: only mentions notify
        val hikers = channel("hikers", NotifyMode.DEFAULT)
        pipeline.onPacket(Messages.buildGroupMessage(Channels.Channel("hikers", hikers.keyEnc), 1_700_000_008L, "li: nice day"), wallClock = clockNow)
        assertEquals(0, notifications.size)
        pipeline.onPacket(Messages.buildGroupMessage(Channels.Channel("hikers", hikers.keyEnc), 1_700_000_009L, "li: hey @mia look"), wallClock = clockNow)
        assertEquals(1, notifications.size)

        // muted channel: even a mention is silent
        val silent = channel("silent", NotifyMode.MUTED).copy(muted = true)
        channels.upsert(silent)
        pipeline.onPacket(Messages.buildGroupMessage(Channels.Channel("silent", silent.keyEnc), 1_700_000_010L, "li: @mia"), wallClock = clockNow)
        assertEquals(1, notifications.size)

        // important-only: everything notifies
        val firehose = channel("firehose", NotifyMode.IMPORTANT_ONLY)
        pipeline.onPacket(Messages.buildGroupMessage(Channels.Channel("firehose", firehose.keyEnc), 1_700_000_011L, "li: chatter"), wallClock = clockNow)
        assertEquals(2, notifications.size)
    }

    @Test
    fun `group message from a blocked name never renders`() = runTest {
        val identity = me()
        val ch = Channels.Channel("hikers", Channels.hashtagKey("hikers"))
        channels.upsert(Channel(0, identity.id, ch.name, ch.secret, ChannelKind.HASHTAG, 0))
        contacts.upsert(Contact(0, identity.id, crypto.newIdentity().publicKey, "spam", 0).let { it.copy(blockedAt = 1) })
        pipeline.onPacket(Messages.buildGroupMessage(ch, 1_700_000_012L, "spam: buy stuff"), wallClock = clockNow)
        assertEquals(0, messages.store.value.size)
    }

    @Test
    fun `group outbox entry is sent once and never retransmitted`() = runTest {
        val identity = me()
        val ch = Channels.Channel("hikers", Channels.hashtagKey("hikers"))
        val channel = channels.upsert(Channel(0, identity.id, ch.name, ch.secret, ChannelKind.HASHTAG, 0))
            .let { channels.byId(it)!! }
        newSendMessage().queueGroup(channel, "heading out")

        val flush = FlushOutbox(outbox, messages, ackTracker, { clockNow })
        assertEquals(1, flush.tick().size)
        // no ACKs for group traffic (03 §6): the row must be gone, or every
        // tick would re-claim and re-broadcast the same packet forever
        assertTrue(outbox.store.value.isEmpty())
        assertEquals(DeliveryState.SENT, messages.store.value.single().state)
        assertEquals(0, flush.tick().size)
    }

    @Test
    fun `reaction sends as a quoted text message every client can read`() = runTest {
        val identity = me()
        val ch = Channels.Channel("hikers", Channels.hashtagKey("hikers"))
        val channel = channels.upsert(Channel(0, identity.id, ch.name, ch.secret, ChannelKind.HASHTAG, 0))
            .let { channels.byId(it)!! } // the fake assigns ids on insert
        val send = newSendMessage()
        send.queueGroup(channel, "summit by noon")
        val target = messages.store.value.single()

        send.queueReaction(channel, target.conversationId, target, "👍")
        val ownReaction = messages.store.value.last { it.kind == MessageKind.REACTION && it.out }
        assertEquals(target.id, ownReaction.replyToId)
        val entry = outbox.store.value.last()
        assertNull(entry.ackKey) // fire-once: no ACK, no retry

        // the on-air packet is a plain GRP_TXT quoting the target message
        val packet = PacketCodec.decode(entry.packet)!!
        assertEquals(PacketSpec.PAYLOAD_GRP_TXT, packet.payloadType)
        val framed = packet.payload.copyOfRange(1, packet.payload.size)
        val plain = ch.macThenDecrypt(crypto, framed)!!
        val text = TxtMsgPayload.nulTerminated(plain, 5)
        assertEquals("mia: @[mia] summit by noon 👍", text)
    }

    @Test
    fun `received quoted reaction attaches to its target`() = runTest {
        val identity = me()
        val ch = Channels.Channel("hikers", Channels.hashtagKey("hikers"))
        val channel = channels.upsert(Channel(0, identity.id, ch.name, ch.secret, ChannelKind.HASHTAG, 0))
            .let { channels.byId(it)!! }
        val send = newSendMessage()
        send.queueGroup(channel, "I had a really good time at the fair this evening")
        val target = messages.store.value.single()

        // a peer reacts with the same quoted-text convention
        pipeline.onPacket(
            Messages.buildGroupMessage(
                ch, 1_700_000_002L,
                "li: @[mia] I had a really good time at the fair this evening ❤️",
            ),
            wallClock = clockNow,
        )
        val reaction = messages.store.value.last { it.kind == MessageKind.REACTION }
        assertEquals("❤️", reaction.body)
        assertEquals("li", reaction.senderName)
        assertEquals(target.id, reaction.replyToId)
        // reactions stay calm: no unread bump, no notification
        assertEquals(0, conversations.store.value.single().unreadCount)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `reaction with no matching target lands as a normal message`() = runTest {
        val identity = me()
        val ch = Channels.Channel("hikers", Channels.hashtagKey("hikers"))
        channels.upsert(Channel(0, identity.id, ch.name, ch.secret, ChannelKind.HASHTAG, 0))
        // nobody sent "wrong quote" — the reaction text cannot attach
        pipeline.onPacket(
            Messages.buildGroupMessage(
                ch, 1_700_000_003L,
                "li: @[mia] wrong quote 👍",
            ),
            wallClock = clockNow,
        )
        val msg = messages.store.value.single()
        assertEquals(MessageKind.TEXT, msg.kind)
        assertEquals("@[mia] wrong quote 👍", msg.body)
    }

    @Test
    fun `legacy GRP_DATA reaction still surfaces`() = runTest {
        val identity = me()
        val ch = Channels.Channel("hikers", Channels.hashtagKey("hikers"))
        channels.upsert(Channel(0, identity.id, ch.name, ch.secret, ChannelKind.HASHTAG, 0))
        // nobody sent anything: the legacy reaction's target tag matches nothing
        pipeline.onPacket(
            Messages.buildReaction(ch, byteArrayOf(1, 2, 3, 4), "li", "❤️"),
            wallClock = clockNow,
        )
        val reaction = messages.store.value.single()
        assertEquals(MessageKind.REACTION, reaction.kind)
        assertEquals("❤️", reaction.body)
        assertNull(reaction.replyToId)
    }

    @Test
    fun `requeue rebuilds a failed dm and it confirms`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", firstSeenAt = 0))
        val send = newSendMessage()
        val msgId = send.queueDirect(peer.publicKey, "lost")

        val flush = FlushOutbox(outbox, messages, ackTracker, { clockNow })
        flush.resume()
        assertEquals(1, flush.tick().size)
        repeat(6) { clockNow += 60_000; flush.tick() } // exhaust retries → FAILED
        assertEquals(DeliveryState.FAILED, messages.store.value.single().state)

        assertTrue(send.requeue(msgId))
        val entry = outbox.store.value.single()
        assertEquals(DeliveryState.QUEUED, entry.state)
        val msg = messages.store.value.single()
        assertEquals(DeliveryState.QUEUED, msg.state)
        assertTrue(msg.ackKey!!.contentEquals(entry.ackKey!!))
        // new packet confirms like any other
        flush.resume()
        assertEquals(1, flush.tick().size)
        val ack = outbox.store.value.single().ackKey!!
        assertTrue(ackTracker.onAcked(ack))
        flush.onConfirmed(ack)
        assertEquals(DeliveryState.CONFIRMED, messages.store.value.single { it.id == msgId }.state)

        // only failed messages can be requeued
        assertTrue(!send.requeue(msgId))
    }

    @Test
    fun `sending on public does not fork a second conversation`() = runTest {
        val identity = me()
        val pub = Channel(0, identity.id, Channels.PUBLIC_NAME, Channels.Channel.public().secret, ChannelKind.PUBLIC, 0)
        channels.upsert(pub)
        val publicConv = conversations.ensure(identity.id, ConversationKind.PUBLIC, pub.id)
        newSendMessage().queueGroup(pub, "hi all")
        assertEquals(1, conversations.store.value.size)
        assertEquals(publicConv, conversations.store.value.single().id)
        assertEquals(1, outbox.store.value.size)
    }

    private fun newSendMessage() = SendMessage(
        identities, contacts, conversations, messages, outbox,
        channels, ackTracker, pathCache, tagCache, crypto,
        airtimeEstimator = object : AirtimeEstimator {
            override fun estimate(packetLen: Int) = 100.0
        },
        wallClockSec = { clockNow / 1_000 },
        wallClockMs = { clockNow },
    )
}
