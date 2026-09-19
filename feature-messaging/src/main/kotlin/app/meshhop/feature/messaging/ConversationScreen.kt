package app.meshhop.feature.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.meshhop.protocol.AckTracker
import app.meshhop.protocol.DeliveryState
import app.meshhop.ui.MeshHopSpacing
import app.meshhop.ui.deliveryGlyph

/**
 * Conversation view (07 §4): bubbles, delivery states the user can read
 * (⏳ → ✓ → ✓ heard → ✓✓), long-press actions, character budget, reply.
 * Bubble width caps at ~72% of screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    title: String,
    messages: List<app.meshhop.domain.Message>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRetry: (Long) -> Unit = {},
) {
    var draft by remember { mutableStateOf("") }
    var longPressed by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    val budget = AckTracker.textBudget(isGroup = false)
    val draftBytes = draft.toByteArray(Charsets.UTF_8).size

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                if (draftBytes > budget) {
                    Text(
                        "Too long for one hop ($draftBytes/$budget). Split it up.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = MeshHopSpacing.md),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeshHopSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeshHopSpacing.sm),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        supportingText = {
                            Text(
                                "$draftBytes / $budget",
                                color = if (draftBytes > budget * 0.85f) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank() && draftBytes <= budget) {
                                onSend(draft)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank() && draftBytes <= budget,
                        modifier = Modifier.semantics { contentDescription = "Send message" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                app.meshhop.ui.EmptyState(
                    title = "No messages yet",
                    body = "Say hello — it goes out when your radio is connected.",
                    actionLabel = "Say hello",
                    onAction = { onSend("Hello!") },
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(MeshHopSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MeshHopSpacing.xs),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: app.meshhop.domain.Message,
    onRetry: (Long) -> Unit = {},
) {
    val mine = message.out
    var actionsOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { actionsOpen = true },
            ),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        androidx.compose.material3.DropdownMenu(
            expanded = actionsOpen,
            onDismissRequest = { actionsOpen = false },
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Reply") },
                onClick = { actionsOpen = false },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Copy text") },
                onClick = { actionsOpen = false },
            )
            if (mine && message.state == DeliveryState.FAILED) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Retry") },
                    onClick = {
                        actionsOpen = false
                        onRetry(message.id)
                    },
                )
            }
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Delete locally") },
                onClick = { actionsOpen = false },
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (mine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = MeshHopSpacing.md, vertical = MeshHopSpacing.sm),
        ) {
            if (!mine && message.senderName != null) {
                Text(
                    message.senderName!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(message.body, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subInfo(message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Compact status line; long-press → Message details shows everything (07 §4). */
private fun subInfo(message: app.meshhop.domain.Message): String {
    val parts = mutableListOf<String>()
    message.state?.let { parts.add(deliveryGlyph(it)) }
    message.hops?.let { parts.add("${it} hop${if (it == 1) "" else "s"}") }
    message.snr?.let { parts.add("SNR ${it.toInt()}") }
    message.rttMs?.let { parts.add("${it} ms") }
    return parts.joinToString(" · ")
}
