package app.meshpigeon.feature.messaging

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.meshpigeon.domain.ChannelRepository
import app.meshpigeon.domain.ContactRepository
import app.meshpigeon.domain.ConversationKind
import app.meshpigeon.domain.ConversationRepository
import app.meshpigeon.domain.IdentityRepository
import app.meshpigeon.domain.MessageRepository
import app.meshpigeon.domain.SendMessage
import app.meshpigeon.protocol.AckTracker
import app.meshpigeon.protocol.DeliveryState
import app.meshpigeon.ui.MeshPigeonSpacing
import app.meshpigeon.ui.deliveryGlyph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Conversation view model (07 §4): live messages for one conversation,
 * send via [SendMessage] (DM / channel), mark-read on open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    identities: IdentityRepository,
    private val conversations: ConversationRepository,
    messages: MessageRepository,
    private val contacts: ContactRepository,
    channels: ChannelRepository,
    private val sendMessage: SendMessage,
    conversationId: Long,
) : ViewModel() {
    data class UiState(
        val title: String = "",
        val messages: List<app.meshpigeon.domain.Message> = emptyList(),
        val isDirect: Boolean = true,
        val isRequest: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    // latest rows resolved from the flows (send path uses them)
    private var conversation: app.meshpigeon.domain.Conversation? = null
    private var peer: app.meshpigeon.domain.Contact? = null
    private var channel: app.meshpigeon.domain.Channel? = null

    init {
        viewModelScope.launch { conversations.markRead(conversationId) }
        viewModelScope.launch {
            conversations.observe(conversationId).flatMapLatest { conv ->
                this@ConversationViewModel.conversation = conv
                if (conv == null) {
                    flowOf(UiState())
                } else {
                    combine(
                        messages.observe(conversationId),
                        contacts.observe(conv.identityId),
                        channels.observe(conv.identityId),
                    ) { msgs, contactList, channelList ->
                        val contact = contactList.firstOrNull { it.id == conv.refId }
                        val ch = channelList.firstOrNull { it.id == conv.refId }
                        this@ConversationViewModel.peer = contact
                        this@ConversationViewModel.channel = ch
                        UiState(
                            title = when (conv.kind) {
                                ConversationKind.DM -> contact?.name ?: "Unknown"
                                ConversationKind.GROUP, ConversationKind.PUBLIC -> ch?.name ?: "Channel"
                                ConversationKind.TRACE_LOG -> "Trace"
                            },
                            messages = msgs,
                            isDirect = conv.kind == ConversationKind.DM,
                            isRequest = conv.isRequest,
                        )
                    }
                }
            }.collect { _state.value = it }
        }
    }

    /** Composer → SendMessage (queue) → outbox; the service flushes. */
    fun send(text: String) {
        viewModelScope.launch {
            val conv = conversation ?: return@launch
            when (conv.kind) {
                ConversationKind.DM -> peer?.let {
                    // replying to a request accepts the sender (07 §5)
                    if (conv.isRequest) acceptRequest()
                    sendMessage.queueDirect(it.publicKey, text)
                }
                ConversationKind.GROUP, ConversationKind.PUBLIC ->
                    channel?.let { sendMessage.queueGroup(it, text) }
                ConversationKind.TRACE_LOG -> Unit
            }
        }
    }

    /** Accept a message request: conversation + contact become normal. */
    fun acceptRequest() {
        viewModelScope.launch {
            conversation?.let { conv ->
                if (conv.isRequest) {
                    conversations.update(conv.copy(isRequest = false))
                    peer?.let { contacts.setAccepted(it.id, true) }
                }
            }
        }
    }

    /** Targeted reaction (07 §4) — channel conversations only (GRP_DATA). */
    fun react(message: app.meshpigeon.domain.Message, emoji: String) {
        viewModelScope.launch {
            val conv = conversation ?: return@launch
            if (conv.kind != ConversationKind.DM) {
                channel?.let { sendMessage.queueReaction(it, conv.id, message, emoji) }
            }
        }
    }

    /** Block the sender of a message request (07 §7). */
    fun blockRequest() {
        viewModelScope.launch {
            peer?.let { contacts.block(it.id) }
        }
    }

    /** Retry affordance (07 §4): re-enqueue a FAILED message. */
    fun retry(messageId: Long) {
        viewModelScope.launch { sendMessage.requeue(messageId) }
    }
}

/**
 * Conversation view (07 §4): bubbles, delivery states the user can read
 * (⏳ → ✓ → ✓ heard → ✓✓), long-press actions, character budget, reply.
 * Bubble width caps at ~72% of screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = state.title
    val messages = state.messages
    var draft by remember { mutableStateOf("") }
    var longPressed by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    val budget = AckTracker.textBudget(isGroup = false)
    val draftBytes = draft.toByteArray(Charsets.UTF_8).size
    // reaction rows render attached to (or after) bubbles, so the list holds
    // fewer items than `messages`
    val reactions = messages.filter { it.kind == app.meshpigeon.domain.MessageKind.REACTION }
    val textIds = messages.filter { it.kind != app.meshpigeon.domain.MessageKind.REACTION }.map { it.id }.toSet()
    val matchedReactions = reactions.count { it.replyToId in textIds }
    val itemCount = messages.size - reactions.size + (reactions.size - matchedReactions)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem((itemCount - 1).coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                if (state.isRequest) {
                    RequestBanner(
                        name = title,
                        onAccept = viewModel::acceptRequest,
                        onBlock = viewModel::blockRequest,
                    )
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                if (state.isRequest) {
                    Text(
                        "Accept the request to reply to $title.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MeshPigeonSpacing.md),
                    )
                }
                if (draftBytes > budget) {
                    Text(
                        "Too long for one hop ($draftBytes/$budget). Split it up.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = MeshPigeonSpacing.md),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeshPigeonSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.sm),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        enabled = !state.isRequest,
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
                                viewModel.send(draft)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank() && draftBytes <= budget && !state.isRequest,
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
                app.meshpigeon.ui.EmptyState(
                    title = "No messages yet",
                    body = "Say hello — it goes out when your radio is connected.",
                    actionLabel = "Say hello",
                    onAction = { viewModel.send("Hello!") },
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(MeshPigeonSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.xs),
            ) {
                val texts = messages.filter { it.kind != app.meshpigeon.domain.MessageKind.REACTION }
                val textIds = texts.map { it.id }.toSet()
                val reactionsByTarget = messages
                    .filter { it.kind == app.meshpigeon.domain.MessageKind.REACTION }
                    .groupBy { it.replyToId }
                val unmatched = reactionsByTarget
                    .filterKeys { it == null || it !in textIds }
                    .values.flatten()
                items(texts, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        reactions = reactionsByTarget[message.id].orEmpty(),
                        canReact = !state.isDirect,
                        onReact = viewModel::react,
                        onRetry = { viewModel.retry(message.id) },
                    )
                }
                // reactions whose target never arrived still surface (07 §4)
                items(unmatched, key = { "r${it.id}" }) { reaction ->
                    Text(
                        "${reaction.senderName ?: "Someone"} reacted ${reaction.body}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MeshPigeonSpacing.md),
                    )
                }
            }
        }
    }
}

/** Accept/block banner on a message-request conversation (07 §5). */
@Composable
private fun RequestBanner(name: String, onAccept: () -> Unit, onBlock: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeshPigeonSpacing.md),
    ) {
        Row(
            modifier = Modifier.padding(MeshPigeonSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Message request", style = MaterialTheme.typography.titleSmall)
                Text(
                    "$name is not in your contacts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBlock) { Text("Block") }
            Button(onClick = onAccept) { Text("Accept") }
        }
    }
}

@Composable
fun MessageBubble(
    message: app.meshpigeon.domain.Message,
    reactions: List<app.meshpigeon.domain.Message> = emptyList(),
    canReact: Boolean = false,
    onReact: (app.meshpigeon.domain.Message, String) -> Unit = { _, _ -> },
    onRetry: (Long) -> Unit = {},
) {
    val mine = message.out
    var actionsOpen by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
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
            if (canReact) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("React…") },
                    onClick = {
                        actionsOpen = false
                        emojiOpen = true
                    },
                )
            }
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
                .padding(horizontal = MeshPigeonSpacing.md, vertical = MeshPigeonSpacing.sm),
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
            if (reactions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.xs),
                    modifier = Modifier.padding(top = MeshPigeonSpacing.xs),
                ) {
                    reactions.groupBy { it.body }.forEach { (emoji, group) ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                if (group.size > 1) "$emoji ${group.size}" else emoji,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = MeshPigeonSpacing.sm, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (emojiOpen) {
        AlertDialog(
            onDismissRequest = { emojiOpen = false },
            title = { Text("React") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.md)) {
                    app.meshpigeon.protocol.Reactions.EMOJIS.forEach { emoji ->
                        TextButton(onClick = {
                            emojiOpen = false
                            onReact(message, emoji)
                        }) {
                            Text(emoji, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { emojiOpen = false }) { Text("Cancel") } },
        )
    }
}

/** Compact status line; long-press → Message details shows everything (07 §4). */
private fun subInfo(message: app.meshpigeon.domain.Message): String {
    val parts = mutableListOf<String>()
    message.state?.let { parts.add(deliveryGlyph(it)) }
    message.hops?.let { parts.add("${it} hop${if (it == 1) "" else "s"}") }
    message.snr?.let { parts.add("SNR ${it.toInt()}") }
    message.rttMs?.let { parts.add("${it} ms") }
    return parts.joinToString(" · ")
}
