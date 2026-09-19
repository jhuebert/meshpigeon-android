package app.meshhop.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MeshHopDatabase_Impl : MeshHopDatabase() {
  private val _identityDao: Lazy<IdentityDao> = lazy {
    IdentityDao_Impl(this)
  }

  private val _contactDao: Lazy<ContactDao> = lazy {
    ContactDao_Impl(this)
  }

  private val _channelDao: Lazy<ChannelDao> = lazy {
    ChannelDao_Impl(this)
  }

  private val _conversationDao: Lazy<ConversationDao> = lazy {
    ConversationDao_Impl(this)
  }

  private val _messageDao: Lazy<MessageDao> = lazy {
    MessageDao_Impl(this)
  }

  private val _outboxDao: Lazy<OutboxDao> = lazy {
    OutboxDao_Impl(this)
  }

  private val _radioTargetDao: Lazy<RadioTargetDao> = lazy {
    RadioTargetDao_Impl(this)
  }

  private val _packetTagDao: Lazy<PacketTagDao> = lazy {
    PacketTagDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "3f837c920eb9d8f76bb7e6b974dd0eb8", "90c798c5a0d3285ccb6d65dd6f95c64e") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `identities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `pubkey` BLOB NOT NULL, `privkey_enc` BLOB NOT NULL, `flags` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `advert_policy` TEXT NOT NULL, `last_advert_at` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `identity_id` INTEGER NOT NULL, `pubkey` BLOB NOT NULL, `name` TEXT NOT NULL, `first_seen_at` INTEGER NOT NULL, `last_seen_at` INTEGER, `source` TEXT NOT NULL, `blocked_at` INTEGER, `flags` INTEGER NOT NULL, `note` TEXT, `last_lat` REAL, `last_lon` REAL, `is_repeater` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `channels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `identity_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `key_enc` BLOB NOT NULL, `kind` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `muted` INTEGER NOT NULL, `notify_mode` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `identity_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `ref_id` INTEGER, `unread_count` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `muted` INTEGER NOT NULL, `notify_mode` TEXT NOT NULL, `last_message_at` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversation_id` INTEGER NOT NULL, `identity_id` INTEGER NOT NULL, `sender_contact_id` INTEGER, `sender_name` TEXT, `body` TEXT NOT NULL, `kind` TEXT NOT NULL, `sent_at` INTEGER NOT NULL, `out` INTEGER NOT NULL, `state` TEXT, `snr` REAL, `rssi` INTEGER, `hops` INTEGER, `region` TEXT, `packet_tag` BLOB, `reply_to_id` INTEGER, `ack_key` BLOB, `rtt_ms` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversation_id` INTEGER NOT NULL, `packet` BLOB NOT NULL, `ack_key` BLOB, `attempts` INTEGER NOT NULL, `next_retry_at` INTEGER NOT NULL, `state` TEXT NOT NULL, `airtime_ms` REAL NOT NULL, `hops` INTEGER NOT NULL, `direct` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `radio_targets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `persistent_id` TEXT NOT NULL, `name` TEXT NOT NULL, `link_kind` TEXT NOT NULL, `link_addr` TEXT NOT NULL, `pref_order` INTEGER NOT NULL, `preferred` INTEGER NOT NULL, `desired_settings_json` TEXT, `last_connected_at` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `packet_tags` (`tag` BLOB NOT NULL, `seen_at` INTEGER NOT NULL, PRIMARY KEY(`tag`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3f837c920eb9d8f76bb7e6b974dd0eb8')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `identities`")
        connection.execSQL("DROP TABLE IF EXISTS `contacts`")
        connection.execSQL("DROP TABLE IF EXISTS `channels`")
        connection.execSQL("DROP TABLE IF EXISTS `conversations`")
        connection.execSQL("DROP TABLE IF EXISTS `messages`")
        connection.execSQL("DROP TABLE IF EXISTS `outbox`")
        connection.execSQL("DROP TABLE IF EXISTS `radio_targets`")
        connection.execSQL("DROP TABLE IF EXISTS `packet_tags`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsIdentities: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsIdentities.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("pubkey", TableInfo.Column("pubkey", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("privkey_enc", TableInfo.Column("privkey_enc", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("flags", TableInfo.Column("flags", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("is_active", TableInfo.Column("is_active", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("advert_policy", TableInfo.Column("advert_policy", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIdentities.put("last_advert_at", TableInfo.Column("last_advert_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysIdentities: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesIdentities: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoIdentities: TableInfo = TableInfo("identities", _columnsIdentities, _foreignKeysIdentities, _indicesIdentities)
        val _existingIdentities: TableInfo = read(connection, "identities")
        if (!_infoIdentities.equals(_existingIdentities)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |identities(app.meshhop.data.IdentityRow).
              | Expected:
              |""".trimMargin() + _infoIdentities + """
              |
              | Found:
              |""".trimMargin() + _existingIdentities)
        }
        val _columnsContacts: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsContacts.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("identity_id", TableInfo.Column("identity_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("pubkey", TableInfo.Column("pubkey", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("first_seen_at", TableInfo.Column("first_seen_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("last_seen_at", TableInfo.Column("last_seen_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("source", TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("blocked_at", TableInfo.Column("blocked_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("flags", TableInfo.Column("flags", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("note", TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("last_lat", TableInfo.Column("last_lat", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("last_lon", TableInfo.Column("last_lon", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsContacts.put("is_repeater", TableInfo.Column("is_repeater", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysContacts: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesContacts: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoContacts: TableInfo = TableInfo("contacts", _columnsContacts, _foreignKeysContacts, _indicesContacts)
        val _existingContacts: TableInfo = read(connection, "contacts")
        if (!_infoContacts.equals(_existingContacts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |contacts(app.meshhop.data.ContactRow).
              | Expected:
              |""".trimMargin() + _infoContacts + """
              |
              | Found:
              |""".trimMargin() + _existingContacts)
        }
        val _columnsChannels: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChannels.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("identity_id", TableInfo.Column("identity_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("key_enc", TableInfo.Column("key_enc", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("kind", TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("pinned", TableInfo.Column("pinned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("muted", TableInfo.Column("muted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("notify_mode", TableInfo.Column("notify_mode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChannels: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesChannels: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoChannels: TableInfo = TableInfo("channels", _columnsChannels, _foreignKeysChannels, _indicesChannels)
        val _existingChannels: TableInfo = read(connection, "channels")
        if (!_infoChannels.equals(_existingChannels)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |channels(app.meshhop.data.ChannelRow).
              | Expected:
              |""".trimMargin() + _infoChannels + """
              |
              | Found:
              |""".trimMargin() + _existingChannels)
        }
        val _columnsConversations: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsConversations.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("identity_id", TableInfo.Column("identity_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("kind", TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("ref_id", TableInfo.Column("ref_id", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("unread_count", TableInfo.Column("unread_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("pinned", TableInfo.Column("pinned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("muted", TableInfo.Column("muted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("notify_mode", TableInfo.Column("notify_mode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("last_message_at", TableInfo.Column("last_message_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysConversations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesConversations: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoConversations: TableInfo = TableInfo("conversations", _columnsConversations, _foreignKeysConversations, _indicesConversations)
        val _existingConversations: TableInfo = read(connection, "conversations")
        if (!_infoConversations.equals(_existingConversations)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |conversations(app.meshhop.data.ConversationRow).
              | Expected:
              |""".trimMargin() + _infoConversations + """
              |
              | Found:
              |""".trimMargin() + _existingConversations)
        }
        val _columnsMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMessages.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("conversation_id", TableInfo.Column("conversation_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("identity_id", TableInfo.Column("identity_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("sender_contact_id", TableInfo.Column("sender_contact_id", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("sender_name", TableInfo.Column("sender_name", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("body", TableInfo.Column("body", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("kind", TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("sent_at", TableInfo.Column("sent_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("out", TableInfo.Column("out", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("state", TableInfo.Column("state", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("snr", TableInfo.Column("snr", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("rssi", TableInfo.Column("rssi", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("hops", TableInfo.Column("hops", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("region", TableInfo.Column("region", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("packet_tag", TableInfo.Column("packet_tag", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("reply_to_id", TableInfo.Column("reply_to_id", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("ack_key", TableInfo.Column("ack_key", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("rtt_ms", TableInfo.Column("rtt_ms", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoMessages: TableInfo = TableInfo("messages", _columnsMessages, _foreignKeysMessages, _indicesMessages)
        val _existingMessages: TableInfo = read(connection, "messages")
        if (!_infoMessages.equals(_existingMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |messages(app.meshhop.data.MessageRow).
              | Expected:
              |""".trimMargin() + _infoMessages + """
              |
              | Found:
              |""".trimMargin() + _existingMessages)
        }
        val _columnsOutbox: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsOutbox.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("conversation_id", TableInfo.Column("conversation_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("packet", TableInfo.Column("packet", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("ack_key", TableInfo.Column("ack_key", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("attempts", TableInfo.Column("attempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("next_retry_at", TableInfo.Column("next_retry_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("state", TableInfo.Column("state", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("airtime_ms", TableInfo.Column("airtime_ms", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("hops", TableInfo.Column("hops", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("direct", TableInfo.Column("direct", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysOutbox: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesOutbox: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoOutbox: TableInfo = TableInfo("outbox", _columnsOutbox, _foreignKeysOutbox, _indicesOutbox)
        val _existingOutbox: TableInfo = read(connection, "outbox")
        if (!_infoOutbox.equals(_existingOutbox)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |outbox(app.meshhop.data.OutboxRow).
              | Expected:
              |""".trimMargin() + _infoOutbox + """
              |
              | Found:
              |""".trimMargin() + _existingOutbox)
        }
        val _columnsRadioTargets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsRadioTargets.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("persistent_id", TableInfo.Column("persistent_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("link_kind", TableInfo.Column("link_kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("link_addr", TableInfo.Column("link_addr", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("pref_order", TableInfo.Column("pref_order", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("preferred", TableInfo.Column("preferred", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("desired_settings_json", TableInfo.Column("desired_settings_json", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRadioTargets.put("last_connected_at", TableInfo.Column("last_connected_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysRadioTargets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesRadioTargets: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoRadioTargets: TableInfo = TableInfo("radio_targets", _columnsRadioTargets, _foreignKeysRadioTargets, _indicesRadioTargets)
        val _existingRadioTargets: TableInfo = read(connection, "radio_targets")
        if (!_infoRadioTargets.equals(_existingRadioTargets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |radio_targets(app.meshhop.data.RadioTargetRow).
              | Expected:
              |""".trimMargin() + _infoRadioTargets + """
              |
              | Found:
              |""".trimMargin() + _existingRadioTargets)
        }
        val _columnsPacketTags: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPacketTags.put("tag", TableInfo.Column("tag", "BLOB", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPacketTags.put("seen_at", TableInfo.Column("seen_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPacketTags: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPacketTags: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPacketTags: TableInfo = TableInfo("packet_tags", _columnsPacketTags, _foreignKeysPacketTags, _indicesPacketTags)
        val _existingPacketTags: TableInfo = read(connection, "packet_tags")
        if (!_infoPacketTags.equals(_existingPacketTags)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |packet_tags(app.meshhop.data.PacketTagRow).
              | Expected:
              |""".trimMargin() + _infoPacketTags + """
              |
              | Found:
              |""".trimMargin() + _existingPacketTags)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "identities", "contacts", "channels", "conversations", "messages", "outbox", "radio_targets", "packet_tags")
  }

  public override fun clearAllTables() {
    super.performClear(false, "identities", "contacts", "channels", "conversations", "messages", "outbox", "radio_targets", "packet_tags")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(IdentityDao::class, IdentityDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ContactDao::class, ContactDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ChannelDao::class, ChannelDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ConversationDao::class, ConversationDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(MessageDao::class, MessageDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(OutboxDao::class, OutboxDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(RadioTargetDao::class, RadioTargetDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PacketTagDao::class, PacketTagDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun identityDao(): IdentityDao = _identityDao.value

  public override fun contactDao(): ContactDao = _contactDao.value

  public override fun channelDao(): ChannelDao = _channelDao.value

  public override fun conversationDao(): ConversationDao = _conversationDao.value

  public override fun messageDao(): MessageDao = _messageDao.value

  public override fun outboxDao(): OutboxDao = _outboxDao.value

  public override fun radioTargetDao(): RadioTargetDao = _radioTargetDao.value

  public override fun packetTagDao(): PacketTagDao = _packetTagDao.value
}
