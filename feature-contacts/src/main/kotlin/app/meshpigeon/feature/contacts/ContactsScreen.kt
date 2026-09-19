package app.meshpigeon.feature.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.meshpigeon.domain.Contact
import app.meshpigeon.ui.EmptyState
import app.meshpigeon.ui.InitialAvatar
import app.meshpigeon.ui.MeshPigeonSpacing

/**
 * Contacts tab (07 §7): people, discovered (pending) contacts, and a
 * read-only Radios section for repeaters. Blocking is reversible from
 * the Blocked list.
 */
@Composable
fun ContactsScreen(contacts: List<Contact> = emptyList()) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("People", "Discovered", "Blocked")
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        val shown = when (tab) {
            0 -> contacts.filter { !it.isRepeater && !it.isBlocked }
            1 -> contacts.filter { it.source == app.meshpigeon.domain.ContactSource.ADVERT && !it.isBlocked }
            else -> contacts.filter { it.isBlocked }
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
                    ContactRow(contact)
                }
            }
        }
    }
}

@Composable
fun ContactRow(contact: Contact) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                        contact.lastSeenAt?.let { "last seen recently" },
                    ).joinToString(" · ").ifBlank { "never seen" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
