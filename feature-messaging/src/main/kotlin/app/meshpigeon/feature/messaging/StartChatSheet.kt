package app.meshpigeon.feature.messaging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meshpigeon.domain.ChannelRepository
import app.meshpigeon.domain.Contact
import app.meshpigeon.domain.ContactRepository
import app.meshpigeon.domain.ConversationKind
import app.meshpigeon.domain.ConversationRepository
import app.meshpigeon.domain.CreateChannel
import app.meshpigeon.domain.IdentityRepository
import app.meshpigeon.protocol.ShareCodec
import app.meshpigeon.ui.ConfirmDialog
import app.meshpigeon.ui.InitialAvatar
import app.meshpigeon.ui.MeshPigeonSpacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "Start chat" sheet (07 §3): Direct message (pick contact), Private
 * channel (name + optional key), Shared channel (name-derived key), and
 * Join with QR/link (paste → preview → commit, 07 §6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartChatViewModel(
    private val identities: IdentityRepository,
    private val contacts: ContactRepository,
    private val conversations: ConversationRepository,
    private val createChannel: CreateChannel,
) : ViewModel() {
    enum class Panel { MENU, DIRECT, PRIVATE, SHARED, JOIN }

    data class State(
        val people: List<Contact> = emptyList(),
        val query: String = "",
    ) {
        val visible: List<Contact>
            get() = if (query.isBlank()) people
            else people.filter { it.name.contains(query, true) }
    }

    private val query = MutableStateFlow("")

    private val people = identities.active().flatMapLatest { identity ->
        if (identity == null) {
            flowOf(emptyList<Contact>())
        } else {
            contacts.observe(identity.id).map { list ->
                list.filter { it.accepted && !it.isBlocked && !it.isRepeater }
            }
        }
    }

    val state: StateFlow<State> = combine(people, query) { p, q -> State(p, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setQuery(value: String) {
        query.value = value
    }

    /** Open (or create) the DM conversation for a contact and jump into it. */
    fun message(contact: Contact, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            val identity = identities.active().first() ?: return@launch
            onOpen(conversations.ensure(identity.id, ConversationKind.DM, contact.id))
        }
    }

    fun createPrivateChannel(name: String, keyHex: String, onOpen: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val identity = identities.active().first() ?: return@launch
            val result = createChannel.createPrivate(identity.id, name, keyHex.ifBlank { null })
            if (result == null) onError("Enter a name (and a 32-hex key if you typed one).")
            else onOpen(result.second)
        }
    }

    fun createSharedChannel(name: String, onOpen: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val identity = identities.active().first() ?: return@launch
            val result = createChannel.createShared(identity.id, name)
            if (result == null) onError("Enter a channel name.")
            else onOpen(result.second)
        }
    }

    fun decode(text: String): ShareCodec.Decoded? = ShareCodec.decode(text)

    /** Commit a previewed channel join. */
    fun joinChannel(name: String, secret: ByteArray, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            val identity = identities.active().first() ?: return@launch
            createChannel.join(identity.id, name, secret)?.let { onOpen(it.second) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartChatSheet(
    viewModel: StartChatViewModel,
    onOpenConversation: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf(StartChatViewModel.Panel.MENU) }

    var joinName by remember { mutableStateOf<String?>(null) }
    var joinSecret by remember { mutableStateOf<ByteArray?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (panel) {
            StartChatViewModel.Panel.MENU -> MenuPanel(onPick = { panel = it })
            StartChatViewModel.Panel.DIRECT -> DirectPanel(
                state = state,
                onQuery = viewModel::setQuery,
                onPick = { contact ->
                    viewModel.message(contact) { id ->
                        onDismiss()
                        onOpenConversation(id)
                    }
                },
            )
            StartChatViewModel.Panel.PRIVATE -> ChannelFormPanel(
                title = "Private channel",
                hint = "Only people with the key can read it. Leave the key blank to generate one.",
                hasKeyField = true,
                onSubmit = { name, key, report ->
                    viewModel.createPrivateChannel(
                        name,
                        key,
                        onOpen = { id ->
                            onDismiss()
                            onOpenConversation(id)
                        },
                        onError = report,
                    )
                },
            )
            StartChatViewModel.Panel.SHARED -> ChannelFormPanel(
                title = "Shared channel",
                hint = "Anyone who knows the name can join — the key comes from the name.",
                hasKeyField = false,
                onSubmit = { name, _, report ->
                    viewModel.createSharedChannel(
                        name,
                        onOpen = { id ->
                            onDismiss()
                            onOpenConversation(id)
                        },
                        onError = report,
                    )
                },
            )
            StartChatViewModel.Panel.JOIN -> JoinPanel(
                onPreview = { text ->
                    val decoded = viewModel.decode(text)
                    if (decoded is ShareCodec.Decoded.Channel) {
                        joinName = decoded.name
                        joinSecret = decoded.secret
                        null
                    } else {
                        "That doesn't look like a channel code."
                    }
                },
            )
        }
    }

    // preview before commit (07 §6: "Join channel **X**?")
    if (joinName != null && joinSecret != null) {
        val name = joinName!!
        val secret = joinSecret!!
        ConfirmDialog(
            title = "Join channel \"$name\"?",
            text = "You will see messages encrypted with this channel's key.",
            confirmLabel = "Join",
            onConfirm = {
                joinName = null
                joinSecret = null
                viewModel.joinChannel(name, secret) { id ->
                    onDismiss()
                    onOpenConversation(id)
                }
            },
            onDismiss = {
                joinName = null
                joinSecret = null
            },
        )
    }
}

@Composable
private fun MenuPanel(onPick: (StartChatViewModel.Panel) -> Unit) {
    Column(modifier = Modifier.padding(bottom = MeshPigeonSpacing.lg)) {
        Text(
            "Start chat",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = MeshPigeonSpacing.lg, vertical = MeshPigeonSpacing.sm),
        )
        listOf(
            StartChatViewModel.Panel.DIRECT to "Direct message",
            StartChatViewModel.Panel.PRIVATE to "Private channel",
            StartChatViewModel.Panel.SHARED to "Shared channel",
            StartChatViewModel.Panel.JOIN to "Join with QR or link",
        ).forEach { (panel, label) ->
            ListItem(
                headlineContent = { Text(label) },
                modifier = Modifier.clickable { onPick(panel) },
            )
        }
    }
}

@Composable
private fun DirectPanel(
    state: StartChatViewModel.State,
    onQuery: (String) -> Unit,
    onPick: (Contact) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = MeshPigeonSpacing.lg)) {
        Text(
            "Direct message",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = MeshPigeonSpacing.lg, vertical = MeshPigeonSpacing.sm),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text("Search contacts") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeshPigeonSpacing.lg),
        )
        if (state.visible.isEmpty()) {
            Text(
                "No contacts yet — share your contact or say hi nearby.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MeshPigeonSpacing.lg),
            )
        } else {
            LazyColumn {
                items(state.visible, key = { it.id }) { contact ->
                    ListItem(
                        leadingContent = { InitialAvatar(name = contact.name, key = contact.publicKey) },
                        headlineContent = { Text(contact.name) },
                        modifier = Modifier.clickable { onPick(contact) },
                    )
                }
            }
        }
    }
}

/** Name (+ optional key) form for private/shared channels. */
@Composable
private fun ChannelFormPanel(
    title: String,
    hint: String,
    hasKeyField: Boolean,
    onSubmit: (name: String, keyHex: String, report: (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .padding(horizontal = MeshPigeonSpacing.lg)
            .padding(bottom = MeshPigeonSpacing.lg),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MeshPigeonSpacing.xs),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Channel name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MeshPigeonSpacing.md),
        )
        if (hasKeyField) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                placeholder = { Text("Key (optional, 32 hex characters)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MeshPigeonSpacing.sm),
            )
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = MeshPigeonSpacing.sm),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MeshPigeonSpacing.md),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Enter a channel name."
                } else {
                    error = null
                    onSubmit(name.trim(), key.trim()) { error = it }
                }
            }) { Text("Create") }
        }
    }
}

@Composable
private fun JoinPanel(onPreview: (text: String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .padding(horizontal = MeshPigeonSpacing.lg)
            .padding(bottom = MeshPigeonSpacing.lg),
    ) {
        Text("Join with QR or link", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste a channel code (QR payload or link). MeshPigeon and MeshCore codes both work.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MeshPigeonSpacing.xs, bottom = MeshPigeonSpacing.md),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Paste a channel code or link") },
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = MeshPigeonSpacing.sm),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MeshPigeonSpacing.md),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { error = onPreview(text) }) { Text("Preview") }
        }
    }
}
