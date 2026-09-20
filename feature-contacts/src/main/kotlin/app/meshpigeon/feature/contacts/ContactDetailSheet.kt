package app.meshpigeon.feature.contacts

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.meshpigeon.domain.Contact
import app.meshpigeon.ui.InitialAvatar
import app.meshpigeon.ui.MeshPigeonSpacing

/**
 * Contact detail sheet (07 §7): message, rename (local alias), copy pubkey,
 * block/unblock, remove (pending only). Sharing a contact card lands with
 * the composer attachments (07 §4) — it needs the contact's signed advert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailSheet(
    contact: Contact,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onRename: (String) -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onRemove: () -> Unit,
    onAccept: () -> Unit = {},
) {
    var renaming by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val pubkeyHex = contact.publicKey.joinToString("") { "%02x".format(it) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeshPigeonSpacing.md)
                .padding(bottom = MeshPigeonSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.md),
                modifier = Modifier.padding(bottom = MeshPigeonSpacing.sm),
            ) {
                InitialAvatar(name = contact.name, key = contact.publicKey)
                Column {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            contact.isBlocked -> "Blocked"
                            contact.isPending -> "Not added yet"
                            contact.isRepeater -> "Repeater"
                            else -> "Contact"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.xs),
            ) {
                Text(
                    pubkeyHex.take(24) + "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(pubkeyHex)) },
                    modifier = Modifier.semantics { contentDescription = "Copy public key" },
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                }
            }
            if (!contact.isBlocked && !contact.isPending && !contact.isRepeater) {
                TextButton(onClick = { onDismiss(); onMessage() }) { Text("Message") }
            }
            if (contact.isPending) {
                TextButton(onClick = { onDismiss(); onAccept() }) { Text("Add") }
            }
            if (!contact.isBlocked) {
                TextButton(onClick = { renaming = true }) { Text("Rename…") }
            }
            if (contact.isBlocked) {
                TextButton(onClick = { onDismiss(); onUnblock() }) { Text("Unblock") }
            } else {
                TextButton(onClick = { onDismiss(); onBlock() }) { Text("Block") }
            }
            if (contact.isPending) {
                TextButton(onClick = { onDismiss(); onRemove() }) { Text("Remove") }
            }
            Text(
                contact.lastSeenAt?.let { "Last seen recently" } ?: "Never seen on air",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renaming) {
        RenameDialog(
            current = contact.name,
            onRename = {
                renaming = false
                onRename(it)
            },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename contact") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Local name (only you see this)") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
