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
import app.meshpigeon.domain.PacketTagCache
import app.meshpigeon.domain.ReceivePipeline
import app.meshpigeon.protocol.AckTracker
import app.meshpigeon.protocol.BouncyMeshCrypto
import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.ClockMapper
import app.meshpigeon.protocol.Messages
import app.meshpigeon.transport.RadioFrame
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
