package app.meshpigeon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Renders an arbitrary string as a QR code image (07 §11): channel join and
 * contact-card codes. Pure client-side — no network, works off-grid.
 */
@Composable
fun QrImage(
    data: String,
    modifier: Modifier = Modifier,
    size: Int = 480,
) {
    val bitmap = remember(data, size) {
        val matrix = QRCodeWriter().encode(
            data,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val white = Color.White.toArgb()
        val black = Color.Black.toArgb()
        val pixels = IntArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) black else white
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR code",
        modifier = modifier,
    )
}
