package app.meshhop.feature.messaging

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.meshhop.domain.Conversation
import app.meshhop.domain.ConversationKind
import app.meshhop.domain.Message
import app.meshhop.protocol.DeliveryState
import app.meshhop.ui.EmptyState
import app.meshhop.ui.InitialAvatar
import app.meshhop.ui.MeshHopSpacing
import app.meshhop.ui.deliveryGlyph
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Chats tab (07 §3): conversations ordered pinned-then-recent, unread
 * badges, FAB → Start chat sheet. Offline-first: fully usable with no
 * radio (06 §5).
 */
class ChatsViewModel : ViewModel() {
    data class ChatsState(
        val conversations: List<Conversation> = emptyList(),
        val lastSnippets: Map<Long, String> = emptyMap(),
        val searching: Boolean = false,
        val query: String = "",
    )

    private val _state = MutableStateFlow(ChatsState())
    val state: StateFlow<ChatsState> = _state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenConversation: (Conversation) -> Unit,
    onStartChat: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    // v1 local sample state until the service wires live flows (M1);
    // Public is the only conversation on first use (hard requirement 07 §6).
    val public = remember { Conversation(id = 1, identityId = 1, kind = ConversationKind.PUBLIC, refId = null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
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
                            onClick = { menuOpen = false },
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
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search chats and messages") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeshHopSpacing.md),
                singleLine = true,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    ChatRow(
                        conversation = public,
                        name = "Public",
                        snippet = "Say hello to the mesh",
                        unread = 0,
                        avatarKey = byteArrayOf(0x2E),
                        onClick = { onOpenConversation(public) },
                    )
                }
            }
        }
    }
}

@Composable
fun ChatRow(
    conversation: Conversation,
    name: String,
    snippet: String,
    unread: Int,
    avatarKey: ByteArray,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MeshHopSpacing.md, vertical = MeshHopSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeshHopSpacing.md),
        ) {
            InitialAvatar(name = name, key = avatarKey)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
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
}
