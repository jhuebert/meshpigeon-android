package app.meshpigeon.transport

import java.io.ByteArrayOutputStream

/**
 * Kotlin mirror of the firmware's framing (docs/radio-protocol.md §2):
 * decoded frame = [cmd:1][nonce:1][status:1][payload:n][crc16:2],
 * wire = COBS(decoded) + 0x00 delimiter. CRC-16/CCITT-FALSE.
 * Identical constants to meshpigeon-firmware's `protocol.h` — keep in sync.
 */
object TransportFrameCodec {
    const val MAX_PAYLOAD = 220
    const val MAX_DECODED = 3 + MAX_PAYLOAD + 2

    fun crc16(data: ByteArray, init: Int = 0xFFFF): Int {
        var crc = init
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }

    fun build(cmd: Int, nonce: Int, status: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too large" }
        val out = ByteArray(3 + payload.size + 2)
        out[0] = cmd.toByte()
        out[1] = nonce.toByte()
        out[2] = status.toByte()
        payload.copyInto(out, 3)
        val crc = crc16(out.copyOfRange(0, 3 + payload.size))
        out[3 + payload.size] = (crc and 0xFF).toByte()
        out[4 + payload.size] = ((crc shr 8) and 0xFF).toByte()
        return out
    }

    fun cobsEncode(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(src.size + src.size / 254 + 2)
        var code = 1
        var blockStart = out.size()
        out.write(0) // placeholder for the code byte
        for (b in src) {
            if (b.toInt() != 0) {
                out.write(b.toInt())
                code++
            }
            if (b.toInt() == 0 || code == 0xFF) {
                val buf = out.toByteArray()
                buf[blockStart] = code.toByte()
                out.reset()
                out.write(buf)
                blockStart = out.size()
                out.write(0) // next code placeholder
                code = 1
            }
        }
        val buf = out.toByteArray()
        buf[blockStart] = code.toByte()
        out.reset()
        out.write(buf)
        return out.toByteArray()
    }

    /** Returns null on malformed input. */
    fun cobsDecode(src: ByteArray): ByteArray? {
        if (src.isEmpty()) return null
        val out = ByteArrayOutputStream(src.size)
        var i = 0
        while (i < src.size) {
            val code = src[i].toInt() and 0xFF
            i++
            if (code == 0) return null // interior zero: malformed
            val block = code - 1
            if (src.size - i < block) return null
            out.write(src, i, block)
            i += block
            if (code < 0xFF && i < src.size) out.write(0)
        }
        return out.toByteArray()
    }

    fun encodeWire(decoded: ByteArray): ByteArray = cobsEncode(decoded) + 0x00

    /** Feed one delimiter-terminated wire chunk; returns the decoded frame or null. */
    fun decodeWire(chunk: ByteArray): ByteArray? {
        if (chunk.isEmpty() || chunk.last() != 0x00.toByte()) return null
        val body = chunk.copyOfRange(0, chunk.size - 1)
        val decoded = cobsDecode(body) ?: return null
        if (decoded.size < 5 || decoded.size > MAX_DECODED) return null
        val expected = crc16(decoded.copyOfRange(0, decoded.size - 2))
        val actual = (decoded[decoded.size - 2].toInt() and 0xFF) or
            ((decoded[decoded.size - 1].toInt() and 0xFF) shl 8)
        if (expected != actual) return null
        return decoded
    }

    fun parse(decoded: ByteArray): RadioFrame = RadioFrame(
        cmd = decoded[0].toInt() and 0xFF,
        nonce = decoded[1].toInt() and 0xFF,
        status = decoded[2].toInt() and 0xFF,
        payload = decoded.copyOfRange(3, decoded.size - 2),
    )

    fun toWire(frame: RadioFrame): ByteArray = encodeWire(build(frame.cmd, frame.nonce, frame.status, frame.payload))
}

/**
 * Streaming frame reader: feed wire bytes (any chunking), receive complete
 * decoded frames. Drops corrupt frames and self-heals at the next delimiter.
 */
class FrameStream {
    private val buf = ArrayList<Byte>(256)

    /** Returns decoded frames completed by this byte (0 or 1). */
    fun feed(bytes: ByteArray): List<RadioFrame> {
        val out = ArrayList<RadioFrame>(1)
        for (b in bytes) {
            if (b == 0x00.toByte()) {
                if (buf.isEmpty()) continue // idle delimiters
                val arr = buf.toByteArray()
                buf.clear()
                TransportFrameCodec.decodeWire(arr + 0x00)?.let { out.add(TransportFrameCodec.parse(it)) }
            } else {
                if (buf.size < TransportFrameCodec.MAX_DECODED * 2) buf.add(b)
                else buf.clear() // runaway: drop until the next delimiter
            }
        }
        return out
    }
}
