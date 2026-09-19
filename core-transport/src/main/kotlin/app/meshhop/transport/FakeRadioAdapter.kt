package app.meshhop.transport

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * In-memory scripted radio for tests and offline development. It answers
 * commands exactly like the firmware core does (same store semantics) so
 * domain use cases run with zero transport.
 */
class FakeRadioAdapter : RadioAdapter {

    override val state = MutableStateFlow(RadioLinkState(RadioLinkState.Phase.RADIO_READY, fakeTarget))
    override val frames = MutableSharedFlow<RadioFrame>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    var store: ArrayDeque<PacketEntry> = ArrayDeque()
    var nextSeq: Long = 1
    var uptimeMs: Long = 42_000
    var bootCount: Long = 3
    var settings = RadioSession.RadioSettings(1, 0, 869_525_000, 12_500, 9, 5, 14, 0)
    var dropNextTx = false

    override suspend fun send(frame: RadioFrame) {
        // hold until the session's collector is subscribed (no lost frames)
        frames.subscriptionCount.first { it >= 1 }
        when (frame.cmd) {
            RadioFrame.CMD_PING -> respond(frame, frame.payload)
            RadioFrame.CMD_GET_INFO -> respond(frame, infoPayload())
            RadioFrame.CMD_GET_RADIO -> respond(frame, settings.encode())
            RadioFrame.CMD_SET_RADIO -> {
                RadioSession.RadioSettings.decode(frame.payload)?.let {
                    settings = it.copy(configEpoch = settings.configEpoch + 1)
                    respond(frame, settings.encode())
                    emitAsync(
                        RadioFrame(
                            RadioFrame.CMD_RADIO_CHANGED,
                            0,
                            0,
                            byteArrayOf(0, 0, 0, 0) + settings.encode(),
                        ),
                    )
                } ?: respond(frame, ByteArray(0), RadioFrame.STATUS_ERR_BAD_PAYLOAD)
            }
            RadioFrame.CMD_SEND_PACKET -> {
                val len = frame.payload[0].toInt() and 0xFF
                val raw = frame.payload.copyOfRange(1, 1 + len)
                val seq = nextSeq++
                if (dropNextTx) {
                    dropNextTx = false
                    respond(frame, ByteArray(0), RadioFrame.STATUS_ERR_TX_FAILED)
                } else {
                    store.addLast(PacketEntry(seq, uptimeMs, 0, 0, 0x01, raw))
                    respond(frame, seq.leU32Bytes())
                    emitAsync(RadioFrame(RadioFrame.CMD_TX_RESULT, 0, 0, seq.leU32Bytes() + byteArrayOf(0)))
                }
            }
            RadioFrame.CMD_FETCH_PACKETS -> {
                val since = frame.payload.leU32(0)
                val max = frame.payload.leU16(4).toLong()
                var delivered = 0
                for (e in store) {
                    if (e.seq <= since) continue
                    if (delivered >= max) break
                    emitAsync(entryFrame(frame.nonce, e))
                    delivered++
                }
                respondCmd(RadioFrame.CMD_FETCH_END, frame.nonce, byteArrayOf((delivered and 0xFF).toByte(), ((delivered shr 8) and 0xFF).toByte()))
            }
            RadioFrame.CMD_PURGE_STORE -> {
                store.clear()
                respond(frame)
            }
        }
    }

    /** Simulate an over-the-air packet arriving (live push + store). */
    suspend fun injectPacket(raw: ByteArray, rssi: Int = -80, snr: Int = 10) {
        val seq = nextSeq++
        store.addLast(PacketEntry(seq, uptimeMs, rssi, snr, 0x02, raw))
        emitAsync(entryFrame(0, store.last()))
    }

    private fun entryFrame(nonce: Int, e: PacketEntry): RadioFrame {
        val p = ByteArray(12 + e.raw.size)
        e.seq.leU32Into(p, 0)
        e.uptimeMs.leU32Into(p, 4)
        p[8] = e.rssi.toByte()
        p[9] = e.snr.toByte()
        p[10] = e.flags.toByte()
        p[11] = e.raw.size.toByte()
        e.raw.copyInto(p, 12)
        return RadioFrame(RadioFrame.CMD_RX_PACKET, nonce, 0, p)
    }

    private fun infoPayload(): ByteArray {
        val p = ByteArray(49)
        p[0] = 1 // protocol version
        // fw version bytes, board name at 3..18
        "FAKE".toByteArray().copyInto(p, 3)
        uptimeMs.leU32Into(p, 19)
        bootCount.leU32Into(p, 23)
        store.size.toLong().leU32Into(p, 27)
        2000L.leU32Into(p, 31)
        0L.leU32Into(p, 35)
        (store.firstOrNull()?.seq ?: nextSeq).leU32Into(p, 39)
        settings.configEpoch.leU32Into(p, 43)
        0xFFFF.leU16Into(p, 47) // battery unknown
        return p
    }

    private fun respond(req: RadioFrame, payload: ByteArray = ByteArray(0), status: Int = RadioFrame.STATUS_OK) =
        respondCmd(req.cmd, req.nonce, payload, status)

    private fun respondCmd(cmd: Int, nonce: Int, payload: ByteArray, status: Int = RadioFrame.STATUS_OK) {
        emitAsync(RadioFrame(cmd, nonce, status, payload))
    }

    private fun emitAsync(frame: RadioFrame) {
        frames.tryEmit(frame)
    }

    override fun scan(): Flow<RadioTarget> = flow {
        emit(fakeTarget)
    }

    companion object {
        val fakeTarget = RadioTarget("fake:1", "Fake Radio", RadioLink.Wifi("localhost", 0))
    }
}

private fun Long.leU32Bytes(): ByteArray = ByteArray(4).also { leU32Into(it, 0) }
