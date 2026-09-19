package app.meshpigeon.transport

import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end over a real TCP socket: a Kotlin server that speaks the
 * firmware's wire protocol (same framing, same command semantics as
 * meshpigeon-firmware's command core) on one side, TcpRadioAdapter +
 * RadioSession on the other. This is the no-hardware stand-in for driving
 * the actual C simulator (which CI wires up via `pio run -e sim`).
 */
class TcpSessionIntegrationTest {

    /** Minimal firmware-equivalent TCP server for PING/GET_INFO/SEND. */
    private class SimServer(private val serverSocket: ServerSocket) : Thread() {
        @Volatile
        var shutdown = false

        override fun run() {
            while (!shutdown) {
                val client = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    return
                }
                object : Thread() {
                    override fun run() {
                        val stream = FrameStream()
                        val buf = ByteArray(4096)
                        val out: OutputStream = client.getOutputStream()
                        try {
                            val input = client.getInputStream()
                            while (!shutdown) {
                                val n = input.read(buf)
                                if (n < 0) break
                                for (f in stream.feed(buf.copyOf(n))) {
                                    handle(f, out)
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            client.close()
                        }
                    }

                    fun handle(f: RadioFrame, out: OutputStream) {
                        val resp: RadioFrame = when (f.cmd) {
                            RadioFrame.CMD_PING -> RadioFrame(f.cmd, f.nonce, 0, f.payload)
                            RadioFrame.CMD_GET_INFO -> {
                                val p = ByteArray(49)
                                p[0] = 1
                                "XIAO WIO".toByteArray().copyInto(p, 3)
                                123_456L.leU32Into(p, 19)
                                RadioFrame(f.cmd, f.nonce, 0, p)
                            }
                            RadioFrame.CMD_SEND_PACKET -> {
                                val len = f.payload[0].toInt() and 0xFF
                                val seq = 7L
                                RadioFrame(f.cmd, f.nonce, 0, seq.leU32Bytes())
                                    .also {
                                        // async TX_RESULT follows
                                        val tx = RadioFrame(
                                            RadioFrame.CMD_TX_RESULT, 0, 0,
                                            seq.leU32Bytes() + byteArrayOf(0),
                                        )
                                        out.write(TransportFrameCodec.toWire(tx))
                                        out.flush()
                                    }
                            }
                            else -> RadioFrame(f.cmd, f.nonce, RadioFrame.STATUS_ERR_BAD_CMD, ByteArray(0))
                        }
                        out.write(TransportFrameCodec.toWire(resp))
                        out.flush()
                    }
                }.start()
            }
        }
    }

    @Test
    fun `ping, info and send round trip over tcp`() = runBlocking {
        // real time: a live socket feeds frames from another thread
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ServerSocket(0).use { server ->
            val sim = SimServer(server).also { it.isDaemon = true; it.start() }
            val port = server.localPort
            val adapter = TcpRadioAdapter("127.0.0.1", port)
            val session = RadioSession(adapter, scope)
            try {
                adapter.connectBlocking()
                session.start()
                val state = adapter.state.value
                assertEquals(RadioLinkState.Phase.CONNECTED, state.phase)

                // PING
                val pong = session.ping("bench".toByteArray())
                assertEquals("bench".toByteArray().toList(), pong.payload.toList())

                // GET_INFO
                val info = session.getInfo()
                assertEquals(1, info.protoVersion)
                assertEquals("XIAO WIO", info.boardName)
                assertEquals(123_456L, info.uptimeMs)

                // SEND_PACKET + async TX_RESULT
                val txResults = mutableListOf<RadioFrame>()
                val tap = launch { adapter.frames.collect { if (it.cmd == RadioFrame.CMD_TX_RESULT) txResults.add(it) } }
                val seq = session.sendPacket(byteArrayOf(0x45, 1, 2))
                assertEquals(7L, seq)
                // let the async TX_RESULT drain
                var sawTx = false
                val deadline = System.currentTimeMillis() + 2000
                while (System.currentTimeMillis() < deadline && !sawTx) {
                    sawTx = txResults.isNotEmpty()
                    if (!sawTx) Thread.sleep(20)
                }
                tap.cancel()
                assertTrue("expected async TX_RESULT", sawTx)
            } finally {
                session.stop()
                adapter.close()
                sim.shutdown = true
                sim.interrupt()
                scope.coroutineContext.cancelChildren()
            }
        }
    }
}

private fun Long.leU32Bytes(): ByteArray = ByteArray(4).also { leU32Into(it, 0) }
