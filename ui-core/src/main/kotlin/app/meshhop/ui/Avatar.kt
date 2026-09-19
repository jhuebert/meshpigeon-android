package app.meshhop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Deterministic avatar color from a key (pubkey or channel key) — the
 * hash-color convention from 07 §3.
 */
fun avatarColor(bytes: ByteArray): Color {
    val hue = (bytes.fold(0) { acc, b -> acc * 31 + (b.toInt() and 0xFF) }.absoluteValue % 360).toFloat()
    return Color.hsl(hue, saturation = 0.55f, lightness = 0.55f)
}

@Composable
fun InitialAvatar(name: String, key: ByteArray, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(avatarColor(key), RoundedCornerShape(size / 2)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
