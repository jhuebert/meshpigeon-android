package app.meshhop.transport

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
 * Drives the ACTUAL meshhop-firmware desktop simulator (the C command core)
 * over TCP when it is reachable. Opt-in: run the sim first
 * (meshhop-firmware: `pio run -e sim && .pio/build/sim/program --port 8765`)
 * then `gradle :core-transport:test -Dmeshhop.sim.port=8765`.
 * CI's cross-repo job starts the sim and passes the port.
 */
class TransportSimInteropTest {

    private fun simPort(): Int? = System.getProperty("meshhop.sim.port", "8765")
        .takeIf { it.isNotBlank() }
        ?.toIntOrNull()
        ?.takeIf { port ->
            try {
                Socket("127.0.0.1", port).use { s ->
                    s.soTimeout = 100
                    s.getInputStream().read() >= 0 || true
                }
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
            assumeTrue("simulator not running; skipped (start sim + -Dmeshhop.sim.port)", false)
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
            val tuned = session.setRadioSettings(
                RadioSession.RadioSettings(1, 2, 869_525_000, 12_500, 9, 5, 14, before.configEpoch),
            )
            assertEquals(2, tuned.region)

            val seq = session.sendPacket(byteArrayOf(0x45, 1, 2, 3))
            val entries = mutableListOf<PacketEntry>()
            val n = session.fetchPackets(0, 50) { entries.add(it) }
            assertEquals(1, n)
            assertEquals(seq, entries.single().seq)
            assertEquals(0x01, entries.single().flags)

            session.purgeStore()
            assertEquals(0, session.fetchPackets(0, 50) { })
        } finally {
            session.stop()
            adapter.close()
            scope.coroutineContext.cancelChildren()
        }
    }
}
