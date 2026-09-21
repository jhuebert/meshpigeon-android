package app.meshpigeon.feature.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import app.meshpigeon.domain.Message
import app.meshpigeon.ui.MeshPigeonSpacing
import app.meshpigeon.ui.deliveryGlyph
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Message details (03 §7): long-press → everything the app persisted for a
 * message — mapped time, SNR/RSSI at our radio, hop count, region, packet
 * tag and ACK-derived relay info (delivery state, round trip).
 */
fun messageDetailRows(message: Message): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows.add("Direction" to if (message.out) "Sent" else "Received")
    rows.add("Time" to TIME_FORMAT.format(Instant.ofEpochMilli(message.sentAt)))
    if (message.out) {
        message.state?.let { rows.add("Delivery" to "${deliveryGlyph(it)} ${stateLabel(it)}") }
        message.rttMs?.let { rows.add("Round trip" to "$it ms") }
    }
    message.hops?.let { rows.add("Hops" to it.toString()) }
    message.snr?.let { rows.add("SNR" to "${if (it >= 0) "+" else ""}${it.toInt()} dB") }
    message.rssi?.let { rows.add("RSSI" to "$it dBm") }
    message.region?.let { rows.add("Region" to it) }
    message.packetTag?.let { rows.add("Packet tag" to it.toHex()) }
    return rows
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private fun stateLabel(state: app.meshpigeon.protocol.DeliveryState): String = when (state) {
    app.meshpigeon.protocol.DeliveryState.QUEUED -> "Queued"
    app.meshpigeon.protocol.DeliveryState.SENT -> "Sent"
    app.meshpigeon.protocol.DeliveryState.HEARD -> "Heard (a repeater relayed it)"
    app.meshpigeon.protocol.DeliveryState.CONFIRMED -> "Confirmed"
    app.meshpigeon.protocol.DeliveryState.FAILED -> "Failed"
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailSheet(
    message: Message,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeshPigeonSpacing.md)
                .padding(bottom = MeshPigeonSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.xs),
        ) {
            Text(
                "Message details",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = MeshPigeonSpacing.sm),
            )
            messageDetailRows(message).forEach { (label, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.md),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                    if (label == "Packet tag") {
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(value)) },
                            modifier = Modifier.semantics { contentDescription = "Copy packet tag" },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
