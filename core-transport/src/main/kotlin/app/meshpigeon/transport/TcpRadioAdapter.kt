package app.meshpigeon.transport

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * TCP radio adapter — speaks the identical wire protocol to the firmware's
 * desktop simulator (and to Wi-Fi boards, 04 §2). Pure JVM; this is the
 * adapter every CI test and the app's dev loop runs against.
 */
class TcpRadioAdapter(
    private val host: String,
    private val port: Int,
) : RadioAdapter {

    override val state = MutableStateFlow(RadioLinkState(RadioLinkState.Phase.DISCONNECTED))
    override val frames = MutableSharedFlow<RadioFrame>(extraBufferCapacity = 256)

    private var socket: Socket? = null
    private var reader: Thread? = null

    override suspend fun send(frame: RadioFrame) {
        frames.subscriptionCount.first { it >= 1 } // session collector active
        val s = socket ?: error("not connected")
        val wire = TransportFrameCodec.toWire(frame)
        synchronized(s.getOutputStream()) {
            s.getOutputStream().write(wire)
            s.getOutputStream().flush()
        }
    }

    override fun scan(): Flow<RadioTarget> = callbackFlow {
        // Wi-Fi radios advertise via mDNS (meshpigeon-radio._tcp) in firmware;
        // for direct-address targets the scan is a simple reachability probe.
        launch(Dispatchers.IO) {
            try {
                Socket().use { probe ->
                    probe.connect(InetSocketAddress(host, port), 1500)
                    trySend(
                        RadioTarget(
                            persistentId = "wifi:$host:$port",
                            name = host,
                            link = RadioLink.Wifi(host, port),
                        ),
                    )
                }
            } catch (_: Exception) {
                // unreachable — nothing to report
            }
            close()
        }
        awaitClose()
    }

    /** Blocking connect (call from IO); frames flow into [frames]. */
    fun connectBlocking() {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 3000)
        socket = s
        state.value = RadioLinkState(RadioLinkState.Phase.CONNECTED, RadioTarget("wifi:$host:$port", host, RadioLink.Wifi(host, port)))
        reader = Thread {
            val stream = FrameStream()
            val buf = ByteArray(4096)
            try {
                val input: InputStream = s.getInputStream()
                while (!s.isClosed) {
                    val n = input.read(buf)
                    if (n < 0) break
                    val parsed = stream.feed(buf.copyOf(n))
                    parsed.forEach { f -> frames.tryEmit(f) }
                }
            } catch (_: Exception) {
                // connection dropped; state updates below
            } finally {
                state.value = RadioLinkState(RadioLinkState.Phase.DISCONNECTED)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun close() {
        reader?.interrupt()
        socket?.close()
        socket = null
    }
}
