package app.meshpigeon.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Camera decode pipeline (07 §11): luminance packing, rotation handling and
 * QR-only decoding — all plain JVM via zxing, no Android camera types.
 */
class QrDecoderTest {
    private fun encodeLuminance(text: String): Pair<ByteArray, Int> {
        val size = 256
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 4),
        )
        val luminance = ByteArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) 0 else 255.toByte()
        }
        return luminance to size
    }

    @Test
    fun `decodes an upright frame`() {
        val (luminance, size) = encodeLuminance("https://meshpigeon.app/c/abc")
        assertEquals("https://meshpigeon.app/c/abc", decodeQrLuminance(luminance, size, size))
    }

    @Test
    fun `decodes a camera image that is one quarter-turn off`() {
        // raw sensor image = upright rotated 270° clockwise; rotationDegrees = 90
        val (luminance, size) = encodeLuminance("MPGS smoke")
        val raw = rotateClockwise90(rotateClockwise90(rotateClockwise90(luminance, size, size), size, size), size, size)
        assertEquals("MPGS smoke", decodeQrLuminance(raw, size, size, 90))
    }

    @Test
    fun `decodes a camera image that is three quarter-turns off`() {
        // raw sensor image = upright rotated 90° clockwise; rotationDegrees = 270
        val (luminance, size) = encodeLuminance("https://meshpigeon.app/u/x")
        val raw = rotateClockwise90(luminance, size, size)
        assertEquals("https://meshpigeon.app/u/x", decodeQrLuminance(raw, size, size, 270))
    }

    @Test
    fun `unpacks a strided camera plane before decoding`() {
        val (luminance, size) = encodeLuminance("stride test")
        val stride = size + 8
        val plane = ByteBuffer.allocate(stride * size)
        for (row in 0 until size) {
            plane.position(row * stride)
            plane.put(luminance, row * size, size)
        }
        val packed = packedYPlane(plane, stride, size, size)
        assertEquals("stride test", decodeQrLuminance(packed, size, size))
    }

    @Test
    fun `noise without a QR code decodes to null`() {
        val size = 256
        val noise = ByteArray(size * size).also { Random(7).nextBytes(it) }
        assertNull(decodeQrLuminance(noise, size, size))
    }
}
