package app.meshhop.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * RadioAdapter SPI (05-transport §1). Nothing above this layer knows
 * whether the radio is BLE, USB, or Wi-Fi. The SPI is pure Kotlin (JVM) so
 * the domain and its tests never touch Android; the Android-bound
 * implementations (BLE/USB) live in :transport-android.
 *
 * Note (deviation from plan 02): BLE/USB adapters live in :transport-android
 * rather than inside :core-transport so this module stays JVM-pure and
 * :core-domain can depend on the SPI. The contracts here are unchanged.
 */
interface RadioAdapter {
    /** Connection state machine: disconnected → scanning → connected → radioReady. */
    val state: StateFlow<RadioLinkState>

    /** Decoded command/response/async frames (after framing + CRC). */
    val frames: SharedFlow<RadioFrame>

    suspend fun send(frame: RadioFrame)

    fun scan(): Flow<RadioTarget>
}

sealed interface RadioLink {
    data class Ble(val mac: String) : RadioLink
    data class Usb(val vendorId: Int, val productId: Int, val port: String) : RadioLink
    data class Wifi(val host: String, val port: Int) : RadioLink
}

/** Link-agnostic radio identity, stable across reboots (05 §2). */
data class RadioTarget(
    override val persistentId: String,
    override val name: String,
    override val link: RadioLink,
) : RadioKey

interface RadioKey {
    val persistentId: String
    val name: String
    val link: RadioLink
}

data class RadioLinkState(
    val phase: Phase,
    val target: RadioKey? = null,
    val error: String? = null,
) {
    enum class Phase { DISCONNECTED, SCANNING, CONNECTED, RADIO_READY }
}

/** One decoded transport frame: cmd/nonce/status + payload (docs/radio-protocol.md). */
data class RadioFrame(
    val cmd: Int,
    val nonce: Int,
    val status: Int,
    val payload: ByteArray,
) {
    companion object {
        const val CMD_PING = 0x01
        const val CMD_GET_INFO = 0x02
        const val CMD_GET_RADIO = 0x03
        const val CMD_SET_RADIO = 0x04
        const val CMD_SEND_PACKET = 0x05
        const val CMD_FETCH_PACKETS = 0x06
        const val CMD_PURGE_STORE = 0x07
        const val CMD_BOOTLOADER = 0x08

        const val CMD_RX_PACKET = 0x10
        const val CMD_FETCH_END = 0x11
        const val CMD_TX_RESULT = 0x12
        const val CMD_RADIO_CHANGED = 0x13

        const val STATUS_OK = 0x00
        const val STATUS_ERR_BAD_CMD = 0x01
        const val STATUS_ERR_BAD_PAYLOAD = 0x02
        const val STATUS_ERR_BAD_CRC = 0x03
        const val STATUS_ERR_BUSY = 0x04
        const val STATUS_ERR_TX_FAILED = 0x05
        const val STATUS_ERR_NO_RADIO = 0x06
    }

    override fun equals(other: Any?): Boolean =
        other is RadioFrame && other.cmd == cmd && other.nonce == nonce &&
            other.status == status && other.payload.contentEquals(payload)

    override fun hashCode(): Int = cmd * 31 + nonce
}

/** One retained packet entry (same layout as the firmware store record). */
data class PacketEntry(
    val seq: Long,
    val uptimeMs: Long,
    val rssi: Int,
    val snr: Int,
    val flags: Int,
    val raw: ByteArray,
) {
    val isSent: Boolean get() = flags and 0x01 != 0
    val isReceived: Boolean get() = flags and 0x02 != 0
}
