package app.meshpigeon.transport

import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Drives the ACTUAL meshpigeon-firmware desktop simulator (the C command core)
 * over TCP when it is reachable. Opt-in: run the sim first
 * (meshpigeon-firmware: `pio run -e sim && .pio/build/sim/program --port 8765`)
 * then `gradle :core-transport:test -Dmeshpigeon.sim.port=8765`.
 * CI's cross-repo job starts the sim and passes the port.
 */
class TransportSimInteropTest {

    private fun simPort(): Int? = System.getProperty("meshpigeon.sim.port", "8765")
        .takeIf { it.isNotBlank() }
        ?.toIntOrNull()
        ?.takeIf { port ->
            try {
                Socket("127.0.0.1", port).use { true }
            } catch (_: Exception) {
                false
            }
        }
        ?: run {
            // also accept an explicit opt-out to keep this cheap when absent
            null
        }

    @Test
    fun `full command set against the real firmware simulator`() = runBlocking {
        val port = simPort() ?: run {
            assumeTrue("simulator not running; skipped (start sim + -Dmeshpigeon.sim.port)", false)
            return@runBlocking
        }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val adapter = TcpRadioAdapter("127.0.0.1", port)
        val session = RadioSession(adapter, scope)
        try {
            adapter.connectBlocking()
            session.start()
            val pong = session.ping("interop".toByteArray())
            assertEquals("interop", pong.payload.decodeToString())

            val info = session.getInfo()
            assertEquals(1, info.protoVersion)
            assertEquals("SIM", info.boardName)

            val before = session.getRadioSettings()
            // The first client after boot owns the tuning for the grace
            // window (05 §3); a re-run against a live sim is BUSY — skip
            // the retune assert rather than failing on the lock.
            val tuned = try {
                session.setRadioSettings(
                    RadioSession.RadioSettings(1, 2, 869_525_000, 12_500, 9, 5, 14, before.configEpoch),
                )
            } catch (e: RadioSession.CommandException.Status) {
                if (e.status == RadioFrame.STATUS_ERR_BUSY) null else throw e
            }
            if (tuned != null) assertEquals(2, tuned.region)

            val seq = session.sendPacket(byteArrayOf(0x45, 1, 2, 3))
            // The sim loopbacks its own TX onto the air: the store ends up
            // with the sent entry plus its received echo once the
            // transmission completes (SimRadio kTxTicks × poll).
            var seen = 0
            val deadline = System.currentTimeMillis() + 2_000
            while (System.currentTimeMillis() < deadline) {
                seen = session.fetchPackets(0, 50) { }
                if (seen >= 2) break
                kotlinx.coroutines.delay(50)
            }
            assertEquals(2, seen)
            val entries = mutableListOf<PacketEntry>()
            assertEquals(2, session.fetchPackets(0, 50) { entries.add(it) })
            assertEquals(1, entries.count { it.isSent })
            assertEquals(seq, entries.first { it.isSent }.seq)

            session.purgeStore()
            assertEquals(0, session.fetchPackets(0, 50) { })
        } finally {
            session.stop()
            adapter.close()
            scope.coroutineContext.cancelChildren()
        }
    }
}
