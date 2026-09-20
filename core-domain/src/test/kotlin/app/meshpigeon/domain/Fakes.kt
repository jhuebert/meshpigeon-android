package app.meshpigeon.domain

import app.meshpigeon.protocol.DeliveryState

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory repositories for use-case tests (09 §1: fake repositories). */
class FakeIdentityRepository : IdentityRepository {
    val store = MutableStateFlow<List<Identity>>(emptyList())
    private var nextId = 1L

    override fun active(): Flow<Identity?> = store.map { list -> list.firstOrNull { it.isActive } }
    override fun all(): Flow<List<Identity>> = store
    override suspend fun upsert(identity: Identity): Long {
        val id = if (identity.id == 0L) nextId++ else identity.id
        store.value = (store.value.filter { it.id != id } + identity.copy(id = id))
            .sortedBy { it.id }
        return id
    }

    override suspend fun setActive(id: Long) {
        store.value = store.value.map { it.copy(isActive = it.id == id) }
    }

    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }
}

class FakeContactRepository : ContactRepository {
    val store = MutableStateFlow<List<Contact>>(emptyList())
    private var nextId = 1L

    override fun observe(identityId: Long): Flow<List<Contact>> =
        store.map { list -> list.filter { it.identityId == identityId } }

    override fun observeBlocked(identityId: Long): Flow<List<Contact>> =
        store.map { list -> list.filter { it.identityId == identityId && it.isBlocked } }

    override fun observePending(identityId: Long): Flow<List<Contact>> =
        store.map { list -> list.filter { it.identityId == identityId && it.isPending && !it.isBlocked } }

    override suspend fun byId(id: Long): Contact? = store.value.firstOrNull { it.id == id }

    override suspend fun byPublicKey(identityId: Long, publicKey: ByteArray): Contact? =
        store.value.firstOrNull { it.identityId == identityId && it.publicKey.contentEquals(publicKey) }

    override suspend fun upsert(contact: Contact): Long {
        val existing = store.value.firstOrNull { it.id == contact.id || (contact.id == 0L && it.identityId == contact.identityId && it.publicKey.contentEquals(contact.publicKey)) }
        val id = existing?.id ?: nextId++
        store.value = (store.value.filter { it.id != id } + contact.copy(id = id)).sortedBy { it.id }
        return id
    }

    override suspend fun block(id: Long) {
        store.value = store.value.map { if (it.id == id) it.copy(blockedAt = 1) else it }
    }

    override suspend fun unblock(id: Long) {
        store.value = store.value.map { if (it.id == id) it.copy(blockedAt = null) else it }
    }

    override suspend fun rename(id: Long, name: String) {
        store.value = store.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun clearPending(identityId: Long) {
        store.value = store.value.filterNot { it.identityId == identityId && it.isPending && !it.isBlocked }
    }

    override suspend fun setAccepted(id: Long, accepted: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(accepted = accepted) else it }
    }

    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }
}

class FakeChannelRepository : ChannelRepository {
    val store = MutableStateFlow<List<Channel>>(emptyList())
    private var nextId = 1L

    override fun observe(identityId: Long): Flow<List<Channel>> =
        store.map { list -> list.filter { it.identityId == identityId } }

    override suspend fun byId(id: Long): Channel? = store.value.firstOrNull { it.id == id }

    override suspend fun upsert(channel: Channel): Long {
        val id = if (channel.id == 0L) nextId++ else channel.id
        store.value = (store.value.filter { it.id != id } + channel.copy(id = id)).sortedBy { it.id }
        return id
    }

    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }
}

class FakeConversationRepository : ConversationRepository {
    val store = MutableStateFlow<List<Conversation>>(emptyList())
    private var nextId = 1L

    override fun observeAll(identityId: Long): Flow<List<Conversation>> =
        store.map { list -> list.filter { it.identityId == identityId } }

    override fun observe(conversationId: Long): Flow<Conversation?> =
        store.map { list -> list.firstOrNull { it.id == conversationId } }

    override suspend fun byKind(identityId: Long, kind: ConversationKind): Conversation? =
        store.value.firstOrNull { it.identityId == identityId && it.kind == kind }

    override suspend fun byId(conversationId: Long): Conversation? =
        store.value.firstOrNull { it.id == conversationId }

    override suspend fun ensure(identityId: Long, kind: ConversationKind, refId: Long?): Long {
        val existing = store.value.firstOrNull { it.identityId == identityId && it.kind == kind && it.refId == refId }
        if (existing != null) return existing.id
        val id = nextId++
        store.value = store.value + Conversation(id, identityId, kind, refId)
        return id
    }

    override suspend fun update(conversation: Conversation) {
        store.value = store.value.map { if (it.id == conversation.id) conversation else it }
    }

    override suspend fun delete(conversationId: Long) {
        store.value = store.value.filterNot { it.id == conversationId }
    }

    override suspend fun bumpUnread(conversationId: Long, delta: Int) {
        store.value = store.value.map { if (it.id == conversationId) it.copy(unreadCount = it.unreadCount + delta) else it }
    }

    override suspend fun markRead(conversationId: Long) {
        store.value = store.value.map { if (it.id == conversationId) it.copy(unreadCount = 0) else it }
    }

    override suspend fun markAllRead(identityId: Long) {
        store.value = store.value.map { if (it.identityId == identityId) it.copy(unreadCount = 0) else it }
    }
}

class FakeMessageRepository : MessageRepository {
    val store = MutableStateFlow<List<Message>>(emptyList())
    private var nextId = 1L

    override fun observe(conversationId: Long, limit: Int): Flow<List<Message>> =
        store.map { list -> list.filter { it.conversationId == conversationId }.takeLast(limit) }

    override fun observeLastPerConversation(identityId: Long): Flow<List<Message>> =
        store.map { list ->
            list.filter { it.identityId == identityId }
                .groupBy { it.conversationId }.values.map { it.last() }
        }

    override suspend fun insert(message: Message): Long {
        val id = nextId++
        store.value = store.value + message.copy(id = id)
        return id
    }

    override suspend fun update(message: Message) {
        store.value = store.value.map { if (it.id == message.id) message else it }
    }

    override suspend fun byId(messageId: Long): Message? =
        store.value.firstOrNull { it.id == messageId }

    override suspend fun updateState(id: Long, state: DeliveryState, rttMs: Long?) {
        store.value = store.value.map { if (it.id == id) it.copy(state = state, rttMs = rttMs ?: it.rttMs) else it }
    }

    override suspend fun byAckKey(identityId: Long, ackKey: ByteArray): Message? =
        store.value.firstOrNull { it.identityId == identityId && it.ackKey?.contentEquals(ackKey) == true }

    override suspend fun byPacketTag(identityId: Long, tag: ByteArray): Message? =
        store.value.firstOrNull { it.identityId == identityId && it.packetTag?.contentEquals(tag) == true }

    override suspend fun markUnreadFrom(conversationId: Long, messageId: Long) {
        // simplified: records the marker on the conversation in real impl
    }

    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun deleteForConversation(conversationId: Long) {
        store.value = store.value.filterNot { it.conversationId == conversationId }
    }
}

class FakeOutboxRepository : OutboxRepository {
    val store = MutableStateFlow<List<OutboxEntry>>(emptyList())
    private var nextId = 1L

    override suspend fun enqueue(entry: OutboxEntry): Long {
        val id = nextId++
        store.value = store.value + entry.copy(id = id)
        return id
    }

    override fun due(now: Long, limit: Int): Flow<List<OutboxEntry>> =
        store.map { list -> list.filter { it.nextRetryAt <= now }.take(limit) }

    override suspend fun update(entry: OutboxEntry) {
        store.value = store.value.map { if (it.id == entry.id) entry else it }
    }

    override suspend fun remove(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun markInFlight(conversationId: Long): OutboxEntry? =
        store.value.firstOrNull { it.conversationId == conversationId && it.state == DeliveryState.QUEUED }
            ?.let { queued ->
                val inFlight = queued.copy(state = DeliveryState.SENT)
                store.value = store.value.map { if (it.id == queued.id) inFlight else it }
                inFlight
            }
}

class FakeRadioTargetRepository : RadioTargetRepository {
    val store = MutableStateFlow<List<RadioTarget>>(emptyList())

    override fun observeAll(): Flow<List<RadioTarget>> = store
    override suspend fun upsert(target: RadioTarget): Long {
        store.value = (store.value.filter { it.id != target.id } + target).sortedBy { it.prefOrder }
        return target.id
    }

    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun reorder(orderedIds: List<Long>) {
        store.value = store.value.map { t -> t.copy(prefOrder = orderedIds.indexOf(t.id).coerceAtLeast(0)) }
    }

    override suspend fun setPreferred(id: Long, preferred: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(preferred = preferred) else it }
    }
}

class FakeSettingsRepository : SettingsRepository {
    private val map = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun string(key: String): String? = map.value[key]
    override suspend fun putString(key: String, value: String) {
        map.value = map.value + (key to value)
    }

    override suspend fun bool(key: String, default: Boolean): Boolean =
        map.value[key]?.toBoolean() ?: default

    override suspend fun putBool(key: String, value: Boolean) {
        map.value = map.value + (key to value.toString())
    }
}
