package app.meshpigeon.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * RadioSession (05 §1): owns the framing codec, a command queue with
 * nonces/timeouts, the RX push stream, and uptime→wall-clock re-anchoring.
 * Works over any [RadioAdapter].
 */
class RadioSession(
    private val adapter: RadioAdapter,
    private val scope: CoroutineScope,
    private val commandTimeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS,
) {
    data class RadioInfo(
        val protoVersion: Int,
        val boardName: String,
        val uptimeMs: Long,
        val bootCount: Long,
        val storeCount: Long,
        /** Store byte budget (GET_INFO @31). Entries are variable-length, so
         *  packet capacity depends on packet sizes. */
        val storeCapacityBytes: Long,
        val storeDropped: Long,
        val oldestSeq: Long,
        val configEpoch: Long,
        val batteryMv: Int,
    )

    data class RadioSettings(
        val version: Int,
        val region: Int,
        val freqHz: Long,
        val bandwidthX100Khz: Int,
        val spreadingFactor: Int,
        val codingRate: Int,
        val powerDbm: Int,
        val configEpoch: Long,
    ) {
        fun encode(): ByteArray {
            val out = ByteArray(17)
            out[0] = version.toByte()
            out[1] = region.toByte()
            freqHz.leU32Into(out, 2)
            bandwidthX100Khz.leU16Into(out, 6)
            out[8] = spreadingFactor.toByte()
            out[9] = codingRate.toByte()
            out[10] = powerDbm.toByte()
            configEpoch.leU32Into(out, 11)
            val crc = TransportFrameCodec.crc16(out.copyOfRange(0, 15))
            out[15] = (crc and 0xFF).toByte()
            out[16] = ((crc shr 8) and 0xFF).toByte()
            return out
        }

        companion object {
            fun decode(b: ByteArray): RadioSettings? {
                if (b.size != 17) return null
                return RadioSettings(
                    version = b[0].toInt() and 0xFF,
                    region = b[1].toInt() and 0xFF,
                    freqHz = b.leU32(2),
                    bandwidthX100Khz = b.leU16(6),
                    spreadingFactor = b[8].toInt() and 0xFF,
                    codingRate = b[9].toInt() and 0xFF,
                    powerDbm = b[10].toInt() and 0xFF,
                    configEpoch = b.leU32(11),
                )
            }
        }
    }

    sealed class CommandException(message: String) : Exception(message) {
        class Status(val status: Int) : CommandException("radio status $status")
        class Timeout : CommandException("command timed out")
    }

    private val nonceCounter = MutableStateFlow(1)
    private val pending = HashMap<Int, CompletableDeferred<RadioFrame>>()
    /** Per-nonce streaming callbacks (FETCH_PACKETS entry frames). */
    private val streamCallbacks = HashMap<Int, (RadioFrame) -> Unit>()
    private val mutex = Mutex()
    private var readerJob: Job? = null

    /** Live pushes and async events (RX_PACKET, TX_RESULT, RADIO_CHANGED). */
    val events: SharedFlow<RadioFrame> = adapter.frames

    val state: StateFlow<RadioLinkState> get() = adapter.state

    /**
     * Begin frame routing. Adapters await their first subscriber inside
     * send(), so responses are never lost to a not-yet-active collector.
     */
    fun start() {
        if (readerJob != null) return
        readerJob = scope.launch {
            adapter.frames.collect { frame ->
                mutex.withLock {
                    val stream = streamCallbacks[frame.nonce]
                    if (stream != null && frame.cmd == RadioFrame.CMD_RX_PACKET) {
                        stream(frame)
                        return@withLock
                    }
                    pending.remove(frame.nonce)?.complete(frame)
                }
            }
        }
    }

    fun stop() {
        readerJob?.cancel()
        readerJob = null
    }

    /** Issue a command and await the matching-nonce response. */
    suspend fun command(cmd: Int, payload: ByteArray = ByteArray(0), timeoutMs: Long = commandTimeoutMs): RadioFrame {
        val nonce = nextNonce()
        val deferred = CompletableDeferred<RadioFrame>()
        mutex.withLock { pending[nonce] = deferred }
        try {
            return withTimeout(timeoutMs) {
                adapter.send(RadioFrame(cmd, nonce, 0, payload))
                val resp = deferred.await()
                if (resp.status != RadioFrame.STATUS_OK) throw CommandException.Status(resp.status)
                resp
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw CommandException.Timeout()
        } finally {
            mutex.withLock { pending.remove(nonce) }
        }
    }

    private fun nextNonce(): Int {
        val n = (nonceCounter.value % 255) + 1 // 1..255, never 0 (async frames)
        nonceCounter.value = n
        return n
    }

    suspend fun ping(payload: ByteArray = "meshpigeon".toByteArray()): RadioFrame =
        command(RadioFrame.CMD_PING, payload)

    suspend fun getInfo(): RadioInfo {
        val resp = command(RadioFrame.CMD_GET_INFO)
        val p = resp.payload
        check(p.size == 49) { "GET_INFO payload size" }
        return RadioInfo(
            protoVersion = p[0].toInt() and 0xFF,
            boardName = p.decodeToString(3, 19).trimEnd('\u0000'),
            uptimeMs = p.leU32(19),
            bootCount = p.leU32(23),
            storeCount = p.leU32(27),
            storeCapacityBytes = p.leU32(31),
            storeDropped = p.leU32(35),
            oldestSeq = p.leU32(39),
            configEpoch = p.leU32(43),
            batteryMv = p.leU16(47),
        )
    }

    suspend fun getRadioSettings(): RadioSettings {
        val resp = command(RadioFrame.CMD_GET_RADIO)
        return RadioSettings.decode(resp.payload)
            ?: throw CommandException.Status(RadioFrame.STATUS_ERR_BAD_PAYLOAD)
    }

    suspend fun setRadioSettings(settings: RadioSettings): RadioSettings {
        val resp = command(RadioFrame.CMD_SET_RADIO, settings.encode())
        return RadioSettings.decode(resp.payload)
            ?: throw CommandException.Status(RadioFrame.STATUS_ERR_BAD_PAYLOAD)
    }

    /** Send raw packet bytes; returns the assigned store seq. */
    suspend fun sendPacket(raw: ByteArray): Long {
        require(raw.size <= 200) { "raw packet too large" }
        val payload = byteArrayOf(raw.size.toByte()) + raw
        val resp = command(RadioFrame.CMD_SEND_PACKET, payload)
        return resp.payload.leU32(0)
    }

    suspend fun purgeStore() {
        command(RadioFrame.CMD_PURGE_STORE)
    }

    suspend fun rebootToBootloader() {
        command(RadioFrame.CMD_BOOTLOADER)
    }

    /**
     * Fetch retained packets with a resumable cursor (05 §6). Streams
     * entries via [onEntry] and returns the final count.
     */
    suspend fun fetchPackets(
        sinceSeq: Long,
        maxCount: Int,
        onEntry: (PacketEntry) -> Unit,
    ): Int {
        val nonce = nextNonce()
        val payload = ByteArray(6)
        sinceSeq.leU32Into(payload, 0)
        maxCount.leU16Into(payload, 4)
        val done = CompletableDeferred<RadioFrame>()
        var count = 0
        mutex.withLock {
            pending[nonce] = done
            streamCallbacks[nonce] = { frame ->
                parsePacketEntry(frame.payload)?.let {
                    count++
                    onEntry(it)
                }
            }
        }
        try {
            withTimeout(commandTimeoutMs * 4) { // streams take longer
                adapter.send(RadioFrame(RadioFrame.CMD_FETCH_PACKETS, nonce, 0, payload))
                done.await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw CommandException.Timeout()
        } finally {
            mutex.withLock {
                pending.remove(nonce)
                streamCallbacks.remove(nonce)
            }
        }
        return count
    }

    /** One RX_PACKET/fetch payload → store record. Async frames use nonce 0. */
    fun parsePacketEntry(p: ByteArray): PacketEntry? {
        if (p.size < 12) return null
        val seq = p.leU32(0)
        val uptime = p.leU32(4)
        val rssi = p[8].toInt()
        val snr = p[9].toInt()
        val flags = p[10].toInt() and 0xFF
        val len = p[11].toInt() and 0xFF
        if (p.size < 12 + len) return null
        return PacketEntry(seq, uptime, rssi, snr, flags, p.copyOfRange(12, 12 + len))
    }

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MS = 5_000L
    }
}

internal fun Long.leU32Into(out: ByteArray, off: Int) {
    out[off] = (this and 0xFF).toByte()
    out[off + 1] = ((this shr 8) and 0xFF).toByte()
    out[off + 2] = ((this shr 16) and 0xFF).toByte()
    out[off + 3] = ((this shr 24) and 0xFF).toByte()
}

internal fun Int.leU16Into(out: ByteArray, off: Int) {
    out[off] = (this and 0xFF).toByte()
    out[off + 1] = ((this shr 8) and 0xFF).toByte()
}

internal fun ByteArray.leU32(off: Int): Long =
    (this[off].toLong() and 0xFF) or
        ((this[off + 1].toLong() and 0xFF) shl 8) or
        ((this[off + 2].toLong() and 0xFF) shl 16) or
        ((this[off + 3].toLong() and 0xFF) shl 24)

internal fun ByteArray.leU16(off: Int): Int =
    (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)
