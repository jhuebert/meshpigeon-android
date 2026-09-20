package app.meshpigeon.domain

import app.meshpigeon.protocol.DeliveryState

import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts (06-android-app §2/§4). Implementations live in
 * :core-data (Room); fakes back the use-case tests. Flows are the read
 * model; suspend funcs are the writes.
 */
interface IdentityRepository {
    fun active(): Flow<Identity?>
    fun all(): Flow<List<Identity>>
    suspend fun upsert(identity: Identity): Long
    suspend fun setActive(id: Long)
    suspend fun delete(id: Long)
}

interface ContactRepository {
    fun observe(identityId: Long): Flow<List<Contact>>
    fun observeBlocked(identityId: Long): Flow<List<Contact>>
    fun observePending(identityId: Long): Flow<List<Contact>>
    suspend fun byId(id: Long): Contact?
    suspend fun byPublicKey(identityId: Long, publicKey: ByteArray): Contact?
    suspend fun upsert(contact: Contact): Long
    suspend fun block(id: Long)
    suspend fun unblock(id: Long)
    suspend fun rename(id: Long, name: String)
    /** Tap-to-add from the pending/discovered list (07 §5). */
    suspend fun setAccepted(id: Long, accepted: Boolean)
    /** Remove a pending contact locally (ignore). */
    suspend fun delete(id: Long)
    suspend fun clearPending(identityId: Long)
}

interface ChannelRepository {
    fun observe(identityId: Long): Flow<List<Channel>>
    suspend fun byId(id: Long): Channel?
    suspend fun upsert(channel: Channel): Long
    suspend fun delete(id: Long)
}

interface ConversationRepository {
    fun observeAll(identityId: Long): Flow<List<Conversation>>
    fun observe(conversationId: Long): Flow<Conversation?>
    suspend fun byId(conversationId: Long): Conversation?
    suspend fun byKind(identityId: Long, kind: ConversationKind): Conversation?
    suspend fun ensure(identityId: Long, kind: ConversationKind, refId: Long?): Long
    suspend fun update(conversation: Conversation)
    suspend fun bumpUnread(conversationId: Long, delta: Int)
    suspend fun markRead(conversationId: Long)
    suspend fun markAllRead(identityId: Long)
}

interface MessageRepository {
    fun observe(conversationId: Long, limit: Int = 200): Flow<List<Message>>
    /** The newest message per conversation (chat-list snippets, 07 §3). */
    fun observeLastPerConversation(identityId: Long): Flow<List<Message>>
    suspend fun insert(message: Message): Long
    suspend fun update(message: Message)
    suspend fun byId(messageId: Long): Message?
    suspend fun updateState(id: Long, state: DeliveryState, rttMs: Long? = null)
    suspend fun byAckKey(identityId: Long, ackKey: ByteArray): Message?
    suspend fun markUnreadFrom(conversationId: Long, messageId: Long)
    suspend fun delete(id: Long)
}

interface OutboxRepository {
    suspend fun enqueue(entry: OutboxEntry): Long
    fun due(now: Long, limit: Int = 8): Flow<List<OutboxEntry>>
    suspend fun update(entry: OutboxEntry)
    suspend fun remove(id: Long)
    /** Per-conversation FIFO: one in flight, rest queued (11 §2.2). */
    suspend fun markInFlight(conversationId: Long): OutboxEntry?
}

interface RadioTargetRepository {
    fun observeAll(): Flow<List<RadioTarget>>
    suspend fun upsert(target: RadioTarget): Long
    suspend fun delete(id: Long)
    suspend fun reorder(orderedIds: List<Long>)
    suspend fun setPreferred(id: Long, preferred: Boolean)
}

interface SettingsRepository {
    suspend fun string(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun bool(key: String, default: Boolean = false): Boolean
    suspend fun putBool(key: String, value: Boolean)
}
