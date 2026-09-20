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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meshpigeon.domain.Channel
import app.meshpigeon.domain.ChannelKind
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
import app.meshpigeon.protocol.ShareCodec
import app.meshpigeon.ui.EmptyState
import app.meshpigeon.ui.InitialAvatar
import app.meshpigeon.ui.MeshPigeonSpacing
import app.meshpigeon.ui.QrImage
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
    private val messages: MessageRepository,
    contacts: ContactRepository,
    private val channels: ChannelRepository,
) : ViewModel() {
    data class Row(
        val conversation: Conversation,
        val name: String,
        val snippet: String,
        val unread: Int,
        val avatarKey: ByteArray,
        val isRequest: Boolean = false,
        val channel: Channel? = null,
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
                            Row(conv, channel?.name ?: "Channel", last?.body ?: "", conv.unreadCount, channel?.keyEnc ?: ByteArray(3), channel = channel)
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

    /** Channel-level mute (07 §3); applies when the row itself is Default. */
    fun setChannelMuted(channel: Channel, muted: Boolean) {
        viewModelScope.launch { channels.upsert(channel.copy(muted = muted)) }
    }

    /** Pinned first, then recency (07 §3) — the DAO orders, this persists. */
    fun setPinned(conversation: Conversation, pinned: Boolean) {
        viewModelScope.launch { conversations.update(conversation.copy(pinned = pinned)) }
    }

    /** Rename is local-only: the join key never changes (07 §6). */
    fun renameChannel(channel: Channel, name: String) {
        viewModelScope.launch { channels.upsert(channel.copy(name = name)) }
    }

    /** Local leave (07 §6): channel + conversation go; history optional. */
    fun deleteChannel(channel: Channel, conversation: Conversation, deleteHistory: Boolean) {
        viewModelScope.launch {
            channels.delete(channel.id)
            conversations.delete(conversation.id)
            if (deleteHistory) messages.deleteForConversation(conversation.id)
        }
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
                        channel = row.channel,
                        onClick = { onOpenConversation(row.conversation) },
                        onMarkRead = { viewModel.markRead(row.conversation) },
                        onSetNotifyMode = { viewModel.setNotifyMode(row.conversation, it) },
                        onSetChannelMuted = { muted -> row.channel?.let { viewModel.setChannelMuted(it, muted) } },
                        onTogglePin = { viewModel.setPinned(row.conversation, !row.conversation.pinned) },
                        onRenameChannel = { name -> row.channel?.let { viewModel.renameChannel(it, name) } },
                        onDeleteChannel = { deleteHistory ->
                            row.channel?.let { viewModel.deleteChannel(it, row.conversation, deleteHistory) }
                        },
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
    channel: Channel? = null,
    onClick: () -> Unit,
    onMarkRead: () -> Unit = {},
    onSetNotifyMode: (NotifyMode) -> Unit = {},
    onSetChannelMuted: (Boolean) -> Unit = {},
    onTogglePin: () -> Unit = {},
    onRenameChannel: (String) -> Unit = {},
    onDeleteChannel: (Boolean) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var notifyDialog by remember { mutableStateOf(false) }
    var shareChannel by remember { mutableStateOf(false) }
    var renameChannel by remember { mutableStateOf(false) }
    var deleteChannel by remember { mutableStateOf(false) }
    // the Public channel is permanent (07 §6) — pin/mute/share only
    val manageable = channel != null && channel.kind != ChannelKind.PUBLIC
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
                    if (conversation.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    if (isRequest) {
                        Text(
                            "Request",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (conversation.muted || channel?.muted == true) {
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

    // long-press actions (07 §3): Pin, Mark read, Notifications…; channels
    // also get mute, rename/delete and QR/link sharing (07 §6)
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(if (conversation.pinned) "Unpin" else "Pin") },
            onClick = {
                menuOpen = false
                onTogglePin()
            },
        )
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
        channel?.let {
            DropdownMenuItem(
                text = { Text(if (it.muted) "Unmute channel" else "Mute channel") },
                onClick = {
                    menuOpen = false
                    onSetChannelMuted(!it.muted)
                },
            )
            DropdownMenuItem(
                text = { Text("Share channel…") },
                onClick = {
                    menuOpen = false
                    shareChannel = true
                },
            )
        }
        if (manageable) {
            DropdownMenuItem(
                text = { Text("Rename…") },
                onClick = {
                    menuOpen = false
                    renameChannel = true
                },
            )
            DropdownMenuItem(
                text = { Text("Delete channel") },
                onClick = {
                    menuOpen = false
                    deleteChannel = true
                },
            )
        }
    }

    if (shareChannel && channel != null) {
        ShareChannelDialog(channel = channel, onDismiss = { shareChannel = false })
    }

    if (renameChannel && channel != null) {
        RenameChannelDialog(
            channel = channel,
            onRename = {
                renameChannel = false
                onRenameChannel(it)
            },
            onDismiss = { renameChannel = false },
        )
    }

    if (deleteChannel && channel != null) {
        DeleteChannelDialog(
            channel = channel,
            onDelete = { deleteHistory ->
                deleteChannel = false
                onDeleteChannel(deleteHistory)
            },
            onDismiss = { deleteChannel = false },
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

/** Local rename (07 §6): the label changes, the join key never does. */
@Composable
private fun RenameChannelDialog(
    channel: Channel,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(channel.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename channel") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Channel name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Local leave (07 §6): the channel stays on the mesh; history is optional. */
@Composable
private fun DeleteChannelDialog(
    channel: Channel,
    onDelete: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteHistory by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave \"${channel.name}\"?") },
        text = {
            Column {
                Text("You will stop seeing messages from this channel on this device. Others keep chatting — you can always re-join.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteHistory, onCheckedChange = { deleteHistory = it })
                    Text("Also delete history", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(deleteHistory) }) {
                Text("Leave", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Channel join code (07 §6): QR + copyable link, generated fully offline. */
@Composable
private fun ShareChannelDialog(channel: Channel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share \"${channel.name}\"") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QrImage(
                    data = ShareCodec.encodeChannel(channel.name, channel.keyEnc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MeshPigeonSpacing.md),
                )
                Text(
                    "Others scan this QR or paste the link to join. Anyone with it can read the channel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (copied) {
                    Text(
                        "Link copied",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = MeshPigeonSpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(ShareCodec.encodeChannel(channel.name, channel.keyEnc)))
                copied = true
            }) { Text("Copy link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
