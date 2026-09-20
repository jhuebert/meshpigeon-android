package app.meshpigeon.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import app.meshpigeon.domain.Channel
import app.meshpigeon.domain.ChannelKind
import app.meshpigeon.domain.ChannelRepository
import app.meshpigeon.domain.Contact
import app.meshpigeon.domain.ContactRepository
import app.meshpigeon.domain.ContactSource
import app.meshpigeon.domain.Conversation
import app.meshpigeon.domain.ConversationKind
import app.meshpigeon.domain.ConversationRepository
import app.meshpigeon.domain.Identity
import app.meshpigeon.protocol.DeliveryState
import app.meshpigeon.domain.IdentityRepository
import app.meshpigeon.domain.Message
import app.meshpigeon.domain.MessageKind
import app.meshpigeon.domain.MessageRepository
import app.meshpigeon.domain.AdvertPolicy
import app.meshpigeon.domain.NotifyMode
import app.meshpigeon.domain.OutboxEntry
import app.meshpigeon.domain.OutboxRepository
import app.meshpigeon.domain.RadioLinkKind
import app.meshpigeon.domain.RadioTarget
import app.meshpigeon.domain.RadioTargetRepository
import app.meshpigeon.domain.SecretSealer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room schema (06 §2). Per-identity tables carry identity_id; keys are
 * stored sealed (privkey_enc/key_enc); FTS lives over message bodies.
 */
@Entity(tableName = "identities")
data class IdentityRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pubkey: ByteArray,
    val privkey_enc: ByteArray,
    val flags: Int,
    val created_at: Long,
    val is_active: Boolean,
    val advert_policy: String,
    val last_advert_at: Long?,
)

@Entity(tableName = "contacts")
data class ContactRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identity_id: Long,
    val pubkey: ByteArray,
    val name: String,
    val first_seen_at: Long,
    val last_seen_at: Long?,
    val source: String,
    val blocked_at: Long?,
    val flags: Int,
    val note: String?,
    val last_lat: Double?,
    val last_lon: Double?,
    val is_repeater: Boolean,
    val accepted: Boolean,
)

@Entity(tableName = "channels")
data class ChannelRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identity_id: Long,
    val name: String,
    val key_enc: ByteArray,
    val kind: String,
    val created_at: Long,
    val pinned: Boolean,
    val muted: Boolean,
    val notify_mode: String,
)

@Entity(tableName = "conversations")
data class ConversationRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identity_id: Long,
    val kind: String,
    val ref_id: Long?,
    val unread_count: Int,
    val pinned: Boolean,
    val muted: Boolean,
    val notify_mode: String,
    val last_message_at: Long?,
    val is_request: Boolean,
)

@Entity(tableName = "messages")
data class MessageRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversation_id: Long,
    val identity_id: Long,
    val sender_contact_id: Long?,
    val sender_name: String?,
    val body: String,
    val kind: String,
    val sent_at: Long,
    val out: Boolean,
    val state: String?,
    val snr: Float?,
    val rssi: Int?,
    val hops: Int?,
    val region: String?,
    val packet_tag: ByteArray?,
    val reply_to_id: Long?,
    val ack_key: ByteArray?,
    val rtt_ms: Long?,
)

@Entity(tableName = "outbox")
data class OutboxRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversation_id: Long,
    val message_id: Long,
    val packet: ByteArray,
    val ack_key: ByteArray?,
    val attempts: Int,
    val next_retry_at: Long,
    val state: String,
    val airtime_ms: Double,
    val hops: Int,
    val direct: Boolean,
)

@Entity(tableName = "radio_targets")
data class RadioTargetRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val persistent_id: String,
    val name: String,
    val link_kind: String,
    val link_addr: String,
    val pref_order: Int,
    val preferred: Boolean,
    val desired_settings_json: String?,
    val last_connected_at: Long?,
)

@Entity(tableName = "packet_tags")
data class PacketTagRow(
    @PrimaryKey val tag: ByteArray,
    val seen_at: Long,
)

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<IdentityRow?>

    @Query("SELECT * FROM identities ORDER BY created_at")
    fun observeAll(): Flow<List<IdentityRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: IdentityRow): Long

    @Query("UPDATE identities SET is_active = (id = :id)")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE identity_id = :identityId ORDER BY last_seen_at DESC")
    fun observe(identityId: Long): Flow<List<ContactRow>>

    @Query("SELECT * FROM contacts WHERE identity_id = :identityId AND blocked_at IS NOT NULL")
    fun observeBlocked(identityId: Long): Flow<List<ContactRow>>

    @Query("SELECT * FROM contacts WHERE identity_id = :identityId AND source = 'ADVERT' AND accepted = 0 AND blocked_at IS NULL")
    fun observePending(identityId: Long): Flow<List<ContactRow>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun byId(id: Long): ContactRow?

    @Query("SELECT * FROM contacts WHERE identity_id = :identityId AND pubkey = :pubkey LIMIT 1")
    suspend fun byPubkey(identityId: Long, pubkey: ByteArray): ContactRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ContactRow): Long

    @Query("UPDATE contacts SET blocked_at = :at WHERE id = :id")
    suspend fun block(id: Long, at: Long)

    @Query("UPDATE contacts SET blocked_at = NULL WHERE id = :id")
    suspend fun unblock(id: Long)

    @Query("UPDATE contacts SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE contacts SET accepted = :accepted WHERE id = :id")
    suspend fun setAccepted(id: Long, accepted: Boolean)

    @Query("DELETE FROM contacts WHERE identity_id = :identityId AND source = 'ADVERT' AND accepted = 0 AND blocked_at IS NULL")
    suspend fun clearPending(identityId: Long)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE identity_id = :identityId ORDER BY pinned DESC, created_at DESC")
    fun observe(identityId: Long): Flow<List<ChannelRow>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun byId(id: Long): ChannelRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ChannelRow): Long

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE identity_id = :identityId ORDER BY last_message_at IS NULL, last_message_at DESC")
    fun observeAll(identityId: Long): Flow<List<ConversationRow>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationRow?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: Long): ConversationRow?

    @Query("SELECT * FROM conversations WHERE identity_id = :identityId AND kind = :kind LIMIT 1")
    suspend fun byKind(identityId: Long, kind: String): ConversationRow?

    @Query("SELECT * FROM conversations WHERE identity_id = :identityId AND kind = :kind AND (ref_id = :refId OR (ref_id IS NULL AND :refId IS NULL)) LIMIT 1")
    suspend fun byKindAndRef(identityId: Long, kind: String, refId: Long?): ConversationRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ConversationRow): Long

    @Query("UPDATE conversations SET unread_count = unread_count + :delta WHERE id = :id")
    suspend fun bumpUnread(id: Long, delta: Int)

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE conversations SET unread_count = 0 WHERE identity_id = :identityId")
    suspend fun markAllRead(identityId: Long)

    @Query("UPDATE conversations SET last_message_at = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY sent_at ASC LIMIT :limit")
    fun observe(conversationId: Long, limit: Int): Flow<List<MessageRow>>

    @Query("SELECT * FROM messages WHERE identity_id = :identityId AND id IN (SELECT MAX(id) FROM messages GROUP BY conversation_id)")
    fun observeLastPerConversation(identityId: Long): Flow<List<MessageRow>>

    @Insert
    suspend fun insert(row: MessageRow): Long

    @Update
    suspend fun update(row: MessageRow)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageRow?

    @Query("UPDATE messages SET state = :state, rtt_ms = COALESCE(:rtt, rtt_ms) WHERE id = :id")
    suspend fun updateState(id: Long, state: String, rtt: Long?)

    @Query("SELECT * FROM messages WHERE identity_id = :identityId AND ack_key = :ackKey LIMIT 1")
    suspend fun byAckKey(identityId: Long, ackKey: ByteArray): MessageRow?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(row: OutboxRow): Long

    @Query("SELECT * FROM outbox WHERE next_retry_at <= :now ORDER BY id LIMIT :limit")
    fun due(now: Long, limit: Int): Flow<List<OutboxRow>>

    @Query("SELECT * FROM outbox WHERE conversation_id = :conversationId AND state = 'QUEUED' ORDER BY id LIMIT 1")
    suspend fun firstQueuedFor(conversationId: Long): OutboxRow?

    @Update
    suspend fun update(row: OutboxRow)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RadioTargetDao {
    @Query("SELECT * FROM radio_targets ORDER BY pref_order")
    fun observeAll(): Flow<List<RadioTargetRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RadioTargetRow): Long

    @Query("DELETE FROM radio_targets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE radio_targets SET pref_order = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("UPDATE radio_targets SET preferred = :preferred WHERE id = :id")
    suspend fun setPreferred(id: Long, preferred: Boolean)
}

@Dao
interface PacketTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: PacketTagRow)

    @Query("DELETE FROM packet_tags WHERE seen_at < :before")
    suspend fun prune(before: Long)
}

@Database(
    entities = [
        IdentityRow::class, ContactRow::class, ChannelRow::class,
        ConversationRow::class, MessageRow::class, OutboxRow::class,
        RadioTargetRow::class, PacketTagRow::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MeshPigeonDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun contactDao(): ContactDao
    abstract fun channelDao(): ChannelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
    abstract fun radioTargetDao(): RadioTargetDao
    abstract fun packetTagDao(): PacketTagDao

    companion object {
        const val NAME = "meshpigeon.db"
    }
}

// ---- mappers ----------------------------------------------------------------

internal fun IdentityRow.toDomain(sealer: SecretSealer) = Identity(
    id, name, pubkey, sealer.unseal(privkey_enc), flags, created_at, is_active,
    AdvertPolicy.valueOf(advert_policy), last_advert_at,
)

internal fun Identity.toRow(sealer: SecretSealer) = IdentityRow(
    id, name, publicKey, sealer.seal(privateKeyEnc), flags, createdAt, isActive,
    advertPolicy.name, lastAdvertAt,
)

internal fun ContactRow.toDomain() = Contact(
    id, identity_id, pubkey, name, first_seen_at, last_seen_at,
    ContactSource.valueOf(source), blocked_at, flags, note,
    last_lat, last_lon, is_repeater, accepted,
)

internal fun Contact.toRow() = ContactRow(
    id, identityId, publicKey, name, firstSeenAt, lastSeenAt,
    source.name, blockedAt, flags, note, lastLatitude, lastLongitude, isRepeater, accepted,
)

internal fun ChannelRow.toDomain(sealer: SecretSealer) = Channel(
    id, identity_id, name, sealer.unseal(key_enc), ChannelKind.valueOf(kind), created_at,
    pinned, muted, NotifyMode.valueOf(notify_mode),
)

internal fun Channel.toRow(sealer: SecretSealer) = ChannelRow(
    id, identityId, name, sealer.seal(keyEnc), kind.name, createdAt, pinned, muted, notifyMode.name,
)

internal fun ConversationRow.toDomain() = Conversation(
    id, identity_id, ConversationKind.valueOf(kind), ref_id,
    unread_count, pinned, muted, NotifyMode.valueOf(notify_mode), last_message_at,
    markUnreadFromId = null, isRequest = is_request,
)

internal fun Conversation.toRow() = ConversationRow(
    id, identityId, kind.name, refId, unreadCount, pinned, muted, notifyMode.name,
    lastMessageAt, is_request = isRequest,
)

internal fun MessageRow.toDomain() = Message(
    id, conversation_id, identity_id, sender_contact_id, sender_name, body,
    MessageKind.valueOf(kind), sent_at, out, state?.let { DeliveryState.valueOf(it) },
    snr, rssi, hops, region, packet_tag, reply_to_id, ack_key, rtt_ms,
)

internal fun Message.toRow() = MessageRow(
    id, conversationId, identityId, senderContactId, senderName, body, kind.name,
    sentAt, out, state?.name, snr, rssi, hops, region, packetTag, replyToId, ackKey, rttMs,
)

internal fun OutboxRow.toDomain() = OutboxEntry(
    id, conversation_id, message_id, packet, ack_key, attempts, next_retry_at,
    DeliveryState.valueOf(state), airtime_ms, hops, direct,
)

internal fun OutboxEntry.toRow() = OutboxRow(
    id, conversationId, messageId, packet, ackKey, attempts, nextRetryAt, state.name,
    airtimeMs, hops, direct,
)

internal fun RadioTargetRow.toDomain() = RadioTarget(
    id, persistent_id, name, RadioLinkKind.valueOf(link_kind), link_addr,
    pref_order, preferred, desired_settings_json, last_connected_at,
)

internal fun RadioTarget.toRow() = RadioTargetRow(
    id, persistentId, name, linkKind.name, linkAddr, prefOrder, preferred,
    desiredSettingsJson, lastConnectedAt,
)

// ---- repositories ------------------------------------------------------------

class RoomIdentityRepository(private val db: MeshPigeonDatabase, private val sealer: SecretSealer) : IdentityRepository {
    override fun active(): Flow<Identity?> = db.identityDao().observeActive().map { it?.toDomain(sealer) }
    override fun all(): Flow<List<Identity>> = db.identityDao().observeAll().map { l -> l.map { it.toDomain(sealer) } }
    override suspend fun upsert(identity: Identity): Long = db.identityDao().upsert(identity.toRow(sealer))
    override suspend fun setActive(id: Long) = db.identityDao().setActive(id)
    override suspend fun delete(id: Long) = db.identityDao().delete(id)
}

class RoomContactRepository(private val db: MeshPigeonDatabase) : ContactRepository {
    override fun observe(identityId: Long): Flow<List<Contact>> =
        db.contactDao().observe(identityId).map { l -> l.map { it.toDomain() } }

    override fun observeBlocked(identityId: Long): Flow<List<Contact>> =
        db.contactDao().observeBlocked(identityId).map { l -> l.map { it.toDomain() } }

    override fun observePending(identityId: Long): Flow<List<Contact>> =
        db.contactDao().observePending(identityId).map { l -> l.map { it.toDomain() } }

    override suspend fun byId(id: Long): Contact? = db.contactDao().byId(id)?.toDomain()

    override suspend fun byPublicKey(identityId: Long, publicKey: ByteArray): Contact? =
        db.contactDao().byPubkey(identityId, publicKey)?.toDomain()

    override suspend fun upsert(contact: Contact): Long = db.contactDao().upsert(contact.toRow())
    override suspend fun block(id: Long) = db.contactDao().block(id, System.currentTimeMillis())
    override suspend fun unblock(id: Long) = db.contactDao().unblock(id)
    override suspend fun rename(id: Long, name: String) = db.contactDao().rename(id, name)
    override suspend fun setAccepted(id: Long, accepted: Boolean) = db.contactDao().setAccepted(id, accepted)
    override suspend fun delete(id: Long) = db.contactDao().delete(id)
    override suspend fun clearPending(identityId: Long) = db.contactDao().clearPending(identityId)
}

class RoomChannelRepository(private val db: MeshPigeonDatabase, private val sealer: SecretSealer) : ChannelRepository {
    override fun observe(identityId: Long): Flow<List<Channel>> =
        db.channelDao().observe(identityId).map { l -> l.map { it.toDomain(sealer) } }

    override suspend fun byId(id: Long): Channel? = db.channelDao().byId(id)?.toDomain(sealer)
    override suspend fun upsert(channel: Channel): Long = db.channelDao().upsert(channel.toRow(sealer))
    override suspend fun delete(id: Long) = db.channelDao().delete(id)
}

class RoomConversationRepository(private val db: MeshPigeonDatabase) : ConversationRepository {
    override fun observeAll(identityId: Long): Flow<List<Conversation>> =
        db.conversationDao().observeAll(identityId).map { l -> l.map { it.toDomain() } }

    override fun observe(conversationId: Long): Flow<Conversation?> =
        db.conversationDao().observe(conversationId).map { it?.toDomain() }

    override suspend fun byId(conversationId: Long): Conversation? =
        db.conversationDao().byId(conversationId)?.toDomain()

    override suspend fun byKind(identityId: Long, kind: ConversationKind): Conversation? =
        db.conversationDao().byKind(identityId, kind.name)?.toDomain()

    override suspend fun ensure(identityId: Long, kind: ConversationKind, refId: Long?): Long {
        db.conversationDao().byKindAndRef(identityId, kind.name, refId)?.let { return it.id }
        return db.conversationDao().upsert(
            ConversationRow(
                identity_id = identityId, kind = kind.name, ref_id = refId,
                unread_count = 0, pinned = false, muted = false,
                notify_mode = NotifyMode.DEFAULT.name, last_message_at = null,
                is_request = false,
            ),
        )
    }

    override suspend fun update(conversation: Conversation) {
        db.conversationDao().upsert(conversation.toRow())
    }
    override suspend fun bumpUnread(conversationId: Long, delta: Int) = db.conversationDao().bumpUnread(conversationId, delta)
    override suspend fun markRead(conversationId: Long) = db.conversationDao().markRead(conversationId)
    override suspend fun markAllRead(identityId: Long) = db.conversationDao().markAllRead(identityId)
}

class RoomMessageRepository(private val db: MeshPigeonDatabase) : MessageRepository {
    override fun observe(conversationId: Long, limit: Int): Flow<List<Message>> =
        db.messageDao().observe(conversationId, limit).map { l -> l.map { it.toDomain() } }

    override fun observeLastPerConversation(identityId: Long): Flow<List<Message>> =
        db.messageDao().observeLastPerConversation(identityId).map { l -> l.map { it.toDomain() } }

    override suspend fun insert(message: Message): Long = db.messageDao().insert(message.toRow())
    override suspend fun update(message: Message) = db.messageDao().update(message.toRow())
    override suspend fun byId(messageId: Long): Message? = db.messageDao().byId(messageId)?.toDomain()
    override suspend fun updateState(id: Long, state: DeliveryState, rttMs: Long?) =
        db.messageDao().updateState(id, state.name, rttMs)

    override suspend fun byAckKey(identityId: Long, ackKey: ByteArray): Message? =
        db.messageDao().byAckKey(identityId, ackKey)?.toDomain()

    override suspend fun markUnreadFrom(conversationId: Long, messageId: Long) {
        // recorded on the conversation row by the caller (M1 scope)
    }

    override suspend fun delete(id: Long) = db.messageDao().delete(id)
}

class RoomOutboxRepository(private val db: MeshPigeonDatabase) : OutboxRepository {
    override suspend fun enqueue(entry: OutboxEntry): Long = db.outboxDao().insert(entry.toRow())
    override fun due(now: Long, limit: Int): Flow<List<OutboxEntry>> =
        db.outboxDao().due(now, limit).map { l -> l.map { it.toDomain() } }

    override suspend fun update(entry: OutboxEntry) = db.outboxDao().update(entry.toRow())
    override suspend fun remove(id: Long) = db.outboxDao().delete(id)
    override suspend fun markInFlight(conversationId: Long): OutboxEntry? =
        db.outboxDao().firstQueuedFor(conversationId)?.toDomain()
}

class RoomRadioTargetRepository(private val db: MeshPigeonDatabase) : RadioTargetRepository {
    override fun observeAll(): Flow<List<RadioTarget>> =
        db.radioTargetDao().observeAll().map { l -> l.map { it.toDomain() } }

    override suspend fun upsert(target: RadioTarget): Long = db.radioTargetDao().upsert(target.toRow())
    override suspend fun delete(id: Long) = db.radioTargetDao().delete(id)
    override suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { i, id -> db.radioTargetDao().setOrder(id, i) }
    }

    override suspend fun setPreferred(id: Long, preferred: Boolean) = db.radioTargetDao().setPreferred(id, preferred)
}
