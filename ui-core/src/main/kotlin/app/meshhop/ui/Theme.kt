package app.meshhop.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MeshHop design system (07 §13): Material 3, dynamic color, one accent,
 * 16 sp base type, metadata never below 12 sp, high-contrast themes.
 */
object MeshHopSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

private val HopGreen = Color(0xFF2E7D5B)
private val HopGreenDark = Color(0xFF7FD8AE)

private val LightColors = lightColorScheme(
    primary = HopGreen,
    onPrimary = Color.White,
    secondary = Color(0xFF3D5A80),
    surfaceVariant = Color(0xFFE8EEE9),
)

private val DarkColors = darkColorScheme(
    primary = HopGreenDark,
    onPrimary = Color(0xFF00391F),
    secondary = Color(0xFF9BB8D3),
    surfaceVariant = Color(0xFF2A332D),
)

val MeshHopTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), // floor: 12 sp
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MeshHopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        // dynamic color where the platform provides it (12L+)
        android.os.Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MeshHopTypography,
        content = content,
    )
}

/** Delivery state glyph (07 §4): the user must see the mesh heard them. */
fun deliveryGlyph(state: app.meshhop.protocol.DeliveryState?): String = when (state) {
    null -> ""
    app.meshhop.protocol.DeliveryState.QUEUED -> "⏳"
    app.meshhop.protocol.DeliveryState.SENT -> "✓"
    app.meshhop.protocol.DeliveryState.HEARD -> "✓ heard"
    app.meshhop.protocol.DeliveryState.CONFIRMED -> "✓✓"
    app.meshhop.protocol.DeliveryState.FAILED -> "✗"
}
