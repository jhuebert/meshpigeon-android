package app.meshpigeon.feature.messaging

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meshpigeon.domain.ChannelRepository
import app.meshpigeon.domain.ContactRepository
import app.meshpigeon.domain.Conversation
import app.meshpigeon.domain.ConversationKind
import app.meshpigeon.domain.ConversationRepository
import app.meshpigeon.domain.IdentityRepository
import app.meshpigeon.domain.Message
import app.meshpigeon.domain.MessageRepository
import app.meshpigeon.domain.NotifyMode
import app.meshpigeon.protocol.DeliveryState
import app.meshpigeon.ui.EmptyState
import app.meshpigeon.ui.InitialAvatar
import app.meshpigeon.ui.MeshPigeonSpacing
import app.meshpigeon.ui.deliveryGlyph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Chats list (07 §3): live conversations for the active identity, ordered
 * by the repository (recent first), with last-message snippets and names
 * resolved from contacts/channels.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModel(
    private val identities: IdentityRepository,
    private val conversations: ConversationRepository,
    messages: MessageRepository,
    contacts: ContactRepository,
    channels: ChannelRepository,
) : ViewModel() {
    data class Row(
        val conversation: Conversation,
        val name: String,
        val snippet: String,
        val unread: Int,
        val avatarKey: ByteArray,
        val isRequest: Boolean = false,
    )

    data class ChatsState(
        val rows: List<Row> = emptyList(),
        val query: String = "",
    ) {
        val visible: List<Row>
            get() = if (query.isBlank()) rows
            else rows.filter { it.name.contains(query, true) || it.snippet.contains(query, true) }
    }

    private val query = MutableStateFlow("")

    private val rows = identities.active().flatMapLatest { identity ->
        if (identity == null) {
            flowOf(emptyList())
        } else {
            combine(
                conversations.observeAll(identity.id),
                messages.observeLastPerConversation(identity.id),
                contacts.observe(identity.id),
                channels.observe(identity.id),
            ) { convs, lastByConv, contactList, channelList ->
                convs.mapNotNull { conv ->
                    val last = lastByConv.firstOrNull { it.conversationId == conv.id }
                    when (conv.kind) {
                        ConversationKind.DM -> {
                            val contact = contactList.firstOrNull { it.id == conv.refId }
                            Row(conv, contact?.name ?: "Unknown", last?.body ?: "", conv.unreadCount, contact?.publicKey ?: ByteArray(3), conv.isRequest)
                        }
                        ConversationKind.GROUP, ConversationKind.PUBLIC -> {
                            val channel = channelList.firstOrNull { it.id == conv.refId }
                            Row(conv, channel?.name ?: "Channel", last?.body ?: "", conv.unreadCount, channel?.keyEnc ?: ByteArray(3))
                        }
                        ConversationKind.TRACE_LOG -> null
                    }
                }
            }
        }
    }

    val state: StateFlow<ChatsState> = combine(rows, query) { r, q -> ChatsState(r, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun markAllRead() {
        viewModelScope.launch {
            identities.active().first()?.let { conversations.markAllRead(it.id) }
        }
    }

    fun markRead(conversation: Conversation) {
        viewModelScope.launch { conversations.markRead(conversation.id) }
    }

    /** Per-conversation notification mode (07 §8); the row wins over the channel. */
    fun setNotifyMode(conversation: Conversation, mode: NotifyMode) {
        viewModelScope.launch { conversations.update(conversation.copy(notifyMode = mode)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    onOpenConversation: (Conversation) -> Unit,
    onStartChat: () -> Unit,
    onConnectRadio: () -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.semantics { contentDescription = "Open menu" }) {
                        Icon(Icons.Filled.Menu, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { /* search bar toggles below */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search chats")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Mark all as read") },
                            onClick = {
                                menuOpen = false
                                viewModel.markAllRead()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Connect radio…") },
                            onClick = {
                                menuOpen = false
                                onConnectRadio()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { menuOpen = false },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onStartChat,
                modifier = Modifier.semantics { contentDescription = "Start chat" },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search chats and messages") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeshPigeonSpacing.md),
                singleLine = true,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val visible = state.visible
                if (visible.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MeshPigeonSpacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                title = if (state.query.isBlank()) "Just Public so far" else "No matches",
                                body = if (state.query.isBlank()) {
                                    "People and channels appear here as the mesh carries them in."
                                } else {
                                    "Try a different search."
                                },
                            )
                        }
                    }
                }
                items(visible, key = { it.conversation.id }) { row ->
                    ChatRow(
                        conversation = row.conversation,
                        name = row.name,
                        snippet = row.snippet,
                        unread = row.unread,
                        avatarKey = row.avatarKey,
                        isRequest = row.isRequest,
                        onClick = { onOpenConversation(row.conversation) },
                        onMarkRead = { viewModel.markRead(row.conversation) },
                        onSetNotifyMode = { viewModel.setNotifyMode(row.conversation, it) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRow(
    conversation: Conversation,
    name: String,
    snippet: String,
    unread: Int,
    avatarKey: ByteArray,
    isRequest: Boolean = false,
    onClick: () -> Unit,
    onMarkRead: () -> Unit = {},
    onSetNotifyMode: (NotifyMode) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var notifyDialog by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MeshPigeonSpacing.md, vertical = MeshPigeonSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.md),
        ) {
            InitialAvatar(name = name, key = avatarKey)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.sm)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    if (isRequest) {
                        Text(
                            "Request",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (unread > 0) {
                Badge { Text(unread.toString()) }
            }
        }
    }

    // long-press actions (07 §3): Mark read, Notifications…
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text("Mark read") },
            onClick = {
                menuOpen = false
                onMarkRead()
            },
        )
        DropdownMenuItem(
            text = { Text("Notifications…") },
            onClick = {
                menuOpen = false
                notifyDialog = true
            },
        )
    }

    if (notifyDialog) {
        NotifyModeDialog(
            current = conversation.notifyMode,
            onSelect = {
                notifyDialog = false
                onSetNotifyMode(it)
            },
            onDismiss = { notifyDialog = false },
        )
    }
}

/** Default / Important only / Muted (07 §8). */
@Composable
private fun NotifyModeDialog(current: NotifyMode, onSelect: (NotifyMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifications") },
        text = {
            Column {
                listOf(
                    NotifyMode.DEFAULT to "Default",
                    NotifyMode.IMPORTANT_ONLY to "Important only",
                    NotifyMode.MUTED to "Muted",
                ).forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (mode == current) "$label ✓" else label,
                            )
                        },
                        onClick = { onSelect(mode) },
                    )
                }
                Text(
                    "Default: direct messages always notify; channels only when @mentioned.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MeshPigeonSpacing.sm),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
