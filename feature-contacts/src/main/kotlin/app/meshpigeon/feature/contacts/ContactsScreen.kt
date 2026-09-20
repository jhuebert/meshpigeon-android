package app.meshpigeon.feature.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meshpigeon.domain.Contact
import app.meshpigeon.domain.ContactRepository
import app.meshpigeon.domain.ContactSource
import app.meshpigeon.domain.IdentityRepository
import app.meshpigeon.protocol.ShareCodec
import app.meshpigeon.ui.ConfirmDialog
import app.meshpigeon.ui.EmptyState
import app.meshpigeon.ui.InitialAvatar
import app.meshpigeon.ui.MeshPigeonSpacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Contacts tab view model (07 §7): People (accepted), Discovered (pending
 * adverts — tap to add, ignore, or block), Blocked (reversible).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel(
    private val identities: IdentityRepository,
    private val contacts: ContactRepository,
) : ViewModel() {
    data class State(
        val people: List<Contact> = emptyList(),
        val discovered: List<Contact> = emptyList(),
        val blocked: List<Contact> = emptyList(),
    )

    val state: StateFlow<State> = identities.active().flatMapLatest { identity ->
        if (identity == null) {
            flowOf(State())
        } else {
            contacts.observe(identity.id).map { list ->
                State(
                    people = list.filter { it.accepted && !it.isBlocked && !it.isRepeater },
                    discovered = list.filter { it.isPending && !it.isRepeater },
                    blocked = list.filter { it.isBlocked },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun accept(contact: Contact) = launch { contacts.setAccepted(contact.id, true) }
    fun remove(contact: Contact) = launch { contacts.delete(contact.id) }
    fun block(contact: Contact) = launch { contacts.block(contact.id) }
    fun unblock(contact: Contact) = launch { contacts.unblock(contact.id) }
    fun rename(contact: Contact, name: String) = launch { contacts.rename(contact.id, name) }

    fun clearAllPending() {
        viewModelScope.launch {
            identities.active().first()?.let { contacts.clearPending(it.id) }
        }
    }

    /** Import a contact from a shared code (07 §5); reports user-facing feedback. */
    fun importContact(decoded: ShareCodec.Decoded.Contact, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val identity = identities.active().first() ?: return@launch
            val existing = contacts.byPublicKey(identity.id, decoded.publicKey)
            val name = decoded.name.ifBlank { "Contact" }
            if (existing != null) {
                onResult("${existing.name} is already in your contacts")
            } else {
                contacts.upsert(
                    Contact(
                        id = 0,
                        identityId = identity.id,
                        publicKey = decoded.publicKey,
                        name = name,
                        firstSeenAt = System.currentTimeMillis(),
                        source = ContactSource.CLIPBOARD,
                        isRepeater = decoded.meshCoreType == 2,
                    ),
                )
                onResult("Added $name")
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/**
 * Contacts tab (07 §5/§7): people, discovered (pending) contacts, and the
 * reversible Blocked list. Long-press opens the contact detail sheet (07 §7);
 * overflow offers "Say hi nearby" (zero-hop advert, 07 §5). Repeaters get a
 * read-only section later (07 §7).
 */
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onMessage: (Contact) -> Unit = {},
    onSayHi: suspend () -> String? = { null },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Contact?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }
    var sayHiFeedback by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ShareCodec.Decoded.Contact?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val tabs = listOf("People", "Discovered", "Blocked")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (sayHiFeedback != null) {
                Text(
                    sayHiFeedback!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = MeshPigeonSpacing.md),
                )
            }
            Box {
                IconButton(onClick = { overflowOpen = true }, modifier = Modifier.semantics { contentDescription = "Contacts options" }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Say hi nearby") },
                        onClick = {
                            overflowOpen = false
                            scope.launch { sayHiFeedback = onSayHi() }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import contact from clipboard") },
                        onClick = {
                            overflowOpen = false
                            val text = clipboard.getText()?.toString().orEmpty()
                            when (val decoded = ShareCodec.decode(text)) {
                                is ShareCodec.Decoded.Contact -> pendingImport = decoded
                                is ShareCodec.Decoded.Channel ->
                                    sayHiFeedback = "That's a channel code — join it from Chats → Start chat."
                                null -> sayHiFeedback = "Nothing to import in the clipboard."
                            }
                        },
                    )
                }
            }
        }
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        if (tab == 1 && state.discovered.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeshPigeonSpacing.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Radios that announced themselves — tap one to add.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmClearAll = true }) { Text("Clear all") }
            }
        }
        val shown = when (tab) {
            0 -> state.people
            1 -> state.discovered
            else -> state.blocked
        }
        if (shown.isEmpty()) {
            EmptyState(
                title = when (tab) {
                    0 -> "No contacts yet"
                    1 -> "No discovered radios nearby"
                    else -> "No blocked contacts"
                },
                body = when (tab) {
                    0 -> "Share your contact or wait for an advert from someone nearby."
                    1 -> "Radios that announce themselves appear here — tap to add."
                    else -> "Blocked people and adverts never render."
                },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        onOpenDetails = { selected = contact },
                        onMessage = { onMessage(contact) },
                    )
                }
            }
        }
    }

    pendingImport?.let { decoded ->
        ConfirmDialog(
            title = "Add contact \"${decoded.name.ifBlank { "Contact" }}\"?",
            text = "You will be able to message them directly.",
            confirmLabel = "Add",
            onConfirm = {
                val d = decoded
                pendingImport = null
                viewModel.importContact(d) { sayHiFeedback = it }
            },
            onDismiss = { pendingImport = null },
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "Clear all discovered?",
            text = "Passively heard radios are removed locally. They reappear when heard again.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClearAll = false
                viewModel.clearAllPending()
            },
            onDismiss = { confirmClearAll = false },
        )
    }

    selected?.let { contact ->
        ContactDetailSheet(
            contact = contact,
            onDismiss = { selected = null },
            onMessage = { onMessage(contact) },
            onRename = { viewModel.rename(contact, it) },
            onBlock = { viewModel.block(contact) },
            onUnblock = { viewModel.unblock(contact) },
            onRemove = { viewModel.remove(contact) },
            onAccept = { viewModel.accept(contact) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    onOpenDetails: () -> Unit,
    onMessage: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    when {
                        contact.isBlocked -> onOpenDetails()
                        contact.isPending -> onOpenDetails()
                        else -> onMessage()
                    }
                },
                onLongClick = onOpenDetails,
            ),
    ) {
        Row(
            modifier = Modifier.padding(MeshPigeonSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.md),
        ) {
            InitialAvatar(name = contact.name, key = contact.publicKey)
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        if (contact.isRepeater) "repeater" else null,
                        when {
                            contact.isBlocked -> "blocked"
                            contact.isPending -> "tap to add"
                            else -> null
                        },
                        contact.lastSeenAt?.let { "last seen recently" },
                    ).joinToString(" · ").ifBlank { "never seen" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (contact.isPending) {
                Button(onClick = onOpenDetails, modifier = Modifier.semantics { contentDescription = "Add ${contact.name}" }) {
                    Text("Add")
                }
            }
            if (contact.isBlocked) {
                OutlinedButton(onClick = onOpenDetails, modifier = Modifier.semantics { contentDescription = "Unblock ${contact.name}" }) {
                    Text("Unblock")
                }
            }
        }
    }
}
