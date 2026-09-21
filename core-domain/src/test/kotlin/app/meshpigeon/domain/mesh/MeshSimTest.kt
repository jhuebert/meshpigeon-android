package app.meshpigeon.domain.mesh

import app.meshpigeon.domain.AdvertPolicy
import app.meshpigeon.domain.Channel
import app.meshpigeon.domain.ChannelKind
import app.meshpigeon.domain.FakeChannelRepository
import app.meshpigeon.domain.FakeContactRepository
import app.meshpigeon.domain.FakeConversationRepository
import app.meshpigeon.domain.FakeIdentityRepository
import app.meshpigeon.domain.FakeMessageRepository
import app.meshpigeon.domain.Identity
import app.meshpigeon.domain.InMemoryPathCache
import app.meshpigeon.domain.PacketRepeater
import app.meshpigeon.domain.PacketTagCache
import app.meshpigeon.domain.ReceivePipeline
import app.meshpigeon.protocol.AckTracker
import app.meshpigeon.protocol.BouncyMeshCrypto
import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.ClockMapper
import app.meshpigeon.protocol.Messages
import app.meshpigeon.transport.RadioFrame
import app.meshpigeon.transport.RadioSession
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Mesh-scale pipeline tests over the REAL firmware simulators, bridged by
 * [MeshSimHarness] (09-testing §2). Opt-in: boot a mesh of sims first
 * (meshpigeon-firmware: `scripts/mesh-sim.sh 3 8801`) then
 * `gradle :core-domain:test -Dmeshpigeon.sim.ports=8801,8802,8803`.
 * CI's mesh-sim job boots three sims and passes the ports. Without the
 * property (or with dead ports) every test skips, so plain
 * `gradlew test` stays hardware/sim free.
 */
class MeshSimTest {

    private fun simPorts(): List<Int>? {
        val raw = System.getProperty("meshpigeon.sim.ports", "").orEmpty()
        val ports = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (ports.size < 2) return null
        val alive = ports.filter { p ->
            try {
                Socket("127.0.0.1", p).use { true }
            } catch (_: Exception) {
                false
            }
        }
        return alive.takeIf { it.size == ports.size }
    }

    private suspend fun awaitTrue(timeoutMs: Long = 5_000, cond: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            delay(25)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    @Test
    fun `a broadcast on one radio reaches every other radio`() = runBlocking {
        val ports = simPorts() ?: run {
            assumeTrue("mesh sims not running; skipped (start sims + -Dmeshpigeon.sim.ports)", false)
            return@runBlocking
        }
        val harness = MeshSimHarness(ports = ports)
        try {
            harness.connect()
            harness.start()
            val raw = byteArrayOf(0x45, 1, 2, 3, 4) + "mesh: broadcast probe".toByteArray()
            harness.send(1, raw)

            // every other radio hears it exactly once
            awaitTrue {
                (0 until harness.radioCount).all { i ->
                    i == 1 || harness.storeEntries(i).any { it.raw.contentEquals(raw) }
                }
            }
            delay(300) // let any stray re-relay surface
            for (i in 0 until harness.radioCount) {
                if (i == 1) continue
                // one SEND_PACKET per relay leaves sent + self-echo entries;
                // only the received copies count
                assertEquals(1, harness.storeEntries(i).count { it.isReceived && it.raw.contentEquals(raw) })
            }
        } finally {
            harness.stop()
        }
    }

    @Test
    fun `a fully lost broadcast never reaches the other radios`() = runBlocking {
        val ports = simPorts() ?: run {
            assumeTrue("mesh sims not running; skipped (start sims + -Dmeshpigeon.sim.ports)", false)
            return@runBlocking
        }
        val harness = MeshSimHarness(ports = ports, lossPercent = 100)
        try {
            harness.connect()
            harness.start()
            val raw = byteArrayOf(0x45, 9, 9, 9, 9) + "mesh: lost probe".toByteArray()
            harness.send(0, raw)

            // the send itself happened (radio 0 keeps its own copy)...
            awaitTrue { harness.storeEntries(0).any { it.raw.contentEquals(raw) } }
            delay(400)
            // ...but the RF loss model dropped it for every other radio
            for (i in 1 until harness.radioCount) {
                assertEquals(0, harness.storeEntries(i).count { it.raw.contentEquals(raw) })
            }
        } finally {
            harness.stop()
        }
    }

    @Test
    fun `the receive pipeline collapses duplicate copies of one mesh broadcast`() = runBlocking {
        val ports = simPorts() ?: run {
            assumeTrue("mesh sims not running; skipped (start sims + -Dmeshpigeon.sim.ports)", false)
            return@runBlocking
        }
        // dupPercent = 100: every relay is heard twice, like a real mesh echo
        val harness = MeshSimHarness(ports = ports, dupPercent = 100)
        val crypto = BouncyMeshCrypto()
        try {
            harness.connect()
            harness.start()

            // app "phone B" attached to radio 1: real decode path over live pushes
            val identities = FakeIdentityRepository()
            val channels = FakeChannelRepository()
            val conversations = FakeConversationRepository()
            val messages = FakeMessageRepository()
            val keys = crypto.newIdentity()
            identities.upsert(
                Identity(
                    id = 0, name = "bob", publicKey = keys.publicKey,
                    privateKeyEnc = keys.privateKey, flags = 0, createdAt = 0,
                    isActive = true, advertPolicy = AdvertPolicy.MANUAL,
                ),
            )
            channels.upsert(
                Channel(
                    id = 0, identityId = 1, name = Channels.PUBLIC_NAME,
                    keyEnc = Channels.publicKey, kind = ChannelKind.PUBLIC, createdAt = 0,
                ),
            )
            val pipeline = ReceivePipeline(
                identities = identities,
                contacts = FakeContactRepository(),
                channels = channels,
                conversations = conversations,
                messages = messages,
                tagCache = PacketTagCache(),
                ackTracker = AckTracker({ System.currentTimeMillis() }),
                pathCache = InMemoryPathCache({ System.currentTimeMillis() }),
                clockMapper = ClockMapper(),
                crypto = crypto,
                notifier = object : ReceivePipeline.Notifier {
                    override suspend fun notify(notification: ReceivePipeline.Notification) {}
                },
            )
            val session = harness.sessions[1]
            val collector = launch(Dispatchers.IO) {
                session.events.collect { frame ->
                    if (frame.cmd != RadioFrame.CMD_RX_PACKET || frame.nonce != 0) return@collect
                    session.parsePacketEntry(frame.payload)?.let {
                        pipeline.onPacket(raw = it.raw, rssi = it.rssi, snr = it.snr)
                    }
                }
            }

            val raw = Messages.buildGroupMessage(
                Channels.Channel.public(),
                System.currentTimeMillis() / 1_000,
                "alice: hello mesh",
            )
            harness.send(0, raw)
            awaitTrue { messages.store.value.isNotEmpty() }
            delay(400) // let the duplicate land too
            collector.cancel()

            assertEquals(1, messages.store.value.size)
            val msg = messages.store.value.single()
            assertEquals("hello mesh", msg.body)
            assertEquals("alice", msg.senderName)
        } finally {
            harness.stop()
        }
    }

    /**
     * The app-driven repeater at mesh scale (03 §4, M4): radios 0–1–2 in a
     * line — radio 0 and 2 are OUT of each other's range. The harness is
     * only the RF layer (`relay = false`); packets move solely when a
     * phone's [PacketRepeater] decides to re-send. A group text originated
     * at radio 0 must reach radio 2's receive pipeline exactly once, and
     * the flood must converge (each radio transmits the packet once).
     */
    @Test
    fun `a group text hops a one-radio gap through the app-driven repeater`() = runBlocking {
        val ports = simPorts() ?: run {
            assumeTrue("mesh sims not running; skipped (start sims + -Dmeshpigeon.sim.ports)", false)
            return@runBlocking
        }
        assumeTrue("needs three sims", ports.size >= 3)
        // line topology: 0 <-> 1 <-> 2 (no direct 0 <-> 2 link)
        val harness = MeshSimHarness(
            ports = ports.take(3), relay = false,
            adjacency = listOf(setOf(1), setOf(0, 2), setOf(1)),
        )
        val crypto = BouncyMeshCrypto()
        try {
            harness.connect()
            harness.start()

            // a phone (pipeline + repeater) behind each radio
            val phones = List(3) { newPhone(crypto) }
            val repeaters = List(3) { i ->
                attachRepeater(harness, crypto, i, phones[i].pipeline)
            }

            val raw = Messages.buildGroupMessage(
                Channels.Channel.public(),
                System.currentTimeMillis() / 1_000,
                "alice: multi-hop hello",
            )
            // originate at radio 0: arm its repeater like every local send
            repeaters[0].observeOutgoing(raw)
            harness.send(0, raw)
            harness.air(0, raw)

            awaitTrue { phones[2].messages.store.value.isNotEmpty() }
            delay(600) // let any stray relay surface

            // radio 2 decoded the message exactly once despite the gap + echoes
            val rows = phones[2].messages.store.value
            assertEquals(1, rows.size)
            assertEquals("multi-hop hello", rows[0].body)
            assertEquals("alice", rows[0].senderName)

            // flood converged with no repeater storm: bounded copies per
            // radio (an injection counts as a sent entry on the receiver —
            // so radio 1, hearing everyone, holds the max of 6), and the
            // stores stop growing once every repeater has seen the packet
            val settled = (0 until 3).map { harness.storeEntries(it).count { e -> e.raw.contentEquals(raw) } }
            delay(400)
            for (i in 0 until 3) {
                val now = harness.storeEntries(i).count { it.raw.contentEquals(raw) }
                assertEquals(settled[i], now)
                assertTrue(now <= 6)
            }
        } finally {
            harness.stop()
        }
    }

    /** A repeater wired to radio [i]: RX events in, SEND_PACKET + air out. */
    private fun attachRepeater(
        harness: MeshSimHarness,
        crypto: BouncyMeshCrypto,
        i: Int,
        pipeline: ReceivePipeline? = null,
    ): PacketRepeater {
        val repeater = PacketRepeater(crypto, myHash = { null })
        repeater.transmit = { raw ->
            // sims key up serially — wait out STATUS_ERR_BUSY
            var attempts = 0
            while (true) {
                try {
                    harness.sessions[i].sendPacket(raw)
                    harness.air(i, raw)
                    break
                } catch (e: RadioSession.CommandException.Status) {
                    if (e.status != RadioFrame.STATUS_ERR_BUSY || ++attempts > 40) throw e
                    delay(25)
                }
            }
        }
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        scope.launch {
            harness.sessions[i].events.collect { frame ->
                if (frame.cmd != RadioFrame.CMD_RX_PACKET || frame.nonce != 0) return@collect
                val entry = harness.sessions[i].parsePacketEntry(frame.payload) ?: return@collect
                repeater.onPacket(entry.raw)
                pipeline?.onPacket(raw = entry.raw, rssi = entry.rssi, snr = entry.snr)
            }
        }
        return repeater
    }

    private class Phone(val messages: FakeMessageRepository, val pipeline: ReceivePipeline)

    /** Stand-in phone plumbing for one radio (real decode path, fake repos). */
    private suspend fun newPhone(crypto: BouncyMeshCrypto): Phone {
        val messages = FakeMessageRepository()
        val identities = FakeIdentityRepository()
        val keys = crypto.newIdentity()
        identities.upsert(
            Identity(
                id = 0, name = "bob", publicKey = keys.publicKey,
                privateKeyEnc = keys.privateKey, flags = 0, createdAt = 0,
                isActive = true, advertPolicy = AdvertPolicy.MANUAL,
            ),
        )
        val channels = FakeChannelRepository()
        channels.upsert(
            Channel(
                id = 0, identityId = 1, name = Channels.PUBLIC_NAME,
                keyEnc = Channels.publicKey, kind = ChannelKind.PUBLIC, createdAt = 0,
            ),
        )
        return Phone(
            messages,
            ReceivePipeline(
                identities = identities,
                contacts = FakeContactRepository(),
                channels = channels,
                conversations = FakeConversationRepository(),
                messages = messages,
                tagCache = PacketTagCache(),
                ackTracker = AckTracker({ System.currentTimeMillis() }),
                pathCache = InMemoryPathCache({ System.currentTimeMillis() }),
                clockMapper = ClockMapper(),
                crypto = crypto,
                notifier = object : ReceivePipeline.Notifier {
                    override suspend fun notify(notification: ReceivePipeline.Notification) {}
                },
            ),
        )
    }
}
