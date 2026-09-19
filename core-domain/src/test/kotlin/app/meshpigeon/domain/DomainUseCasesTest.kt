package app.meshpigeon.domain

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
import app.meshpigeon.transport.FakeRadioAdapter
import app.meshpigeon.transport.RadioSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
            ackTracker, pathCache, crypto,
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
    }

    @Test
    fun `over-budget message is rejected`() = runTest {
        val identity = me()
        val peer = crypto.newIdentity()
        contacts.upsert(Contact(0, identity.id, peer.publicKey, "bob", 0))
        val send = SendMessage(
            identities, contacts, conversations, messages, outbox,
            ackTracker, pathCache, crypto,
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
}
