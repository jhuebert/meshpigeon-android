package app.meshpigeon.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/**
 * QR decoding for the camera scanner (07 §11 "QR & links everywhere").
 * Deliberately free of Android camera types so the luminance/rotation
 * pipeline is plain JVM-testable; the scanner composable feeds it the
 * camera frame's Y plane.
 */

/** Copy a camera Y plane into a tightly packed luminance array (row stride may exceed width). */
fun packedYPlane(plane: ByteBuffer, rowStride: Int, width: Int, height: Int): ByteArray {
    val out = ByteArray(width * height)
    var dst = 0
    for (row in 0 until height) {
        plane.position(row * rowStride)
        plane.get(out, dst, width)
        dst += width
    }
    return out
}

/**
 * Decode a QR code from raw camera luminance, returning its text or null.
 *
 * [clockwiseDegrees] follows ImageProxy semantics: the clockwise rotation
 * the frame needs to become upright (camera sensor orientation).
 */
fun decodeQrLuminance(
    luminance: ByteArray,
    width: Int,
    height: Int,
    clockwiseDegrees: Int = 0,
): String? {
    var data = luminance
    var w = width
    var h = height
    var remaining = ((clockwiseDegrees % 360) + 360) % 360
    while (remaining > 0) {
        data = rotateClockwise90(data, w, h)
        val t = w
        w = h
        h = t
        remaining -= 90
    }
    val reader = MultiFormatReader()
    return try {
        val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        val result = reader.decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
        result.text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

/** Rotate a packed luminance array 90° clockwise (destination width = source height). */
fun rotateClockwise90(src: ByteArray, width: Int, height: Int): ByteArray {
    val dst = ByteArray(src.size)
    for (row in 0 until height) {
        for (col in 0 until width) {
            dst[row * height + col] = src[(height - 1 - col) * width + row]
        }
    }
    return dst
}
