package app.meshhop.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ConversationDao_Impl(
  __db: RoomDatabase,
) : ConversationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfConversationRow: EntityInsertAdapter<ConversationRow>
  init {
    this.__db = __db
    this.__insertAdapterOfConversationRow = object : EntityInsertAdapter<ConversationRow>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `conversations` (`id`,`identity_id`,`kind`,`ref_id`,`unread_count`,`pinned`,`muted`,`notify_mode`,`last_message_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ConversationRow) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.identity_id)
        statement.bindText(3, entity.kind)
        val _tmpRef_id: Long? = entity.ref_id
        if (_tmpRef_id == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmpRef_id)
        }
        statement.bindLong(5, entity.unread_count.toLong())
        val _tmp: Int = if (entity.pinned) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        val _tmp_1: Int = if (entity.muted) 1 else 0
        statement.bindLong(7, _tmp_1.toLong())
        statement.bindText(8, entity.notify_mode)
        val _tmpLast_message_at: Long? = entity.last_message_at
        if (_tmpLast_message_at == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpLast_message_at)
        }
      }
    }
  }

  public override suspend fun upsert(row: ConversationRow): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfConversationRow.insertAndReturnId(_connection, row)
    _result
  }

  public override fun observeAll(identityId: Long): Flow<List<ConversationRow>> {
    val _sql: String = "SELECT * FROM conversations WHERE identity_id = ? ORDER BY last_message_at IS NULL, last_message_at DESC"
    return createFlow(__db, false, arrayOf("conversations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfRefId: Int = getColumnIndexOrThrow(_stmt, "ref_id")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unread_count")
        val _columnIndexOfPinned: Int = getColumnIndexOrThrow(_stmt, "pinned")
        val _columnIndexOfMuted: Int = getColumnIndexOrThrow(_stmt, "muted")
        val _columnIndexOfNotifyMode: Int = getColumnIndexOrThrow(_stmt, "notify_mode")
        val _columnIndexOfLastMessageAt: Int = getColumnIndexOrThrow(_stmt, "last_message_at")
        val _result: MutableList<ConversationRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConversationRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpRef_id: Long?
          if (_stmt.isNull(_columnIndexOfRefId)) {
            _tmpRef_id = null
          } else {
            _tmpRef_id = _stmt.getLong(_columnIndexOfRefId)
          }
          val _tmpUnread_count: Int
          _tmpUnread_count = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpPinned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfPinned).toInt()
          _tmpPinned = _tmp != 0
          val _tmpMuted: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfMuted).toInt()
          _tmpMuted = _tmp_1 != 0
          val _tmpNotify_mode: String
          _tmpNotify_mode = _stmt.getText(_columnIndexOfNotifyMode)
          val _tmpLast_message_at: Long?
          if (_stmt.isNull(_columnIndexOfLastMessageAt)) {
            _tmpLast_message_at = null
          } else {
            _tmpLast_message_at = _stmt.getLong(_columnIndexOfLastMessageAt)
          }
          _item = ConversationRow(_tmpId,_tmpIdentity_id,_tmpKind,_tmpRef_id,_tmpUnread_count,_tmpPinned,_tmpMuted,_tmpNotify_mode,_tmpLast_message_at)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observe(id: Long): Flow<ConversationRow?> {
    val _sql: String = "SELECT * FROM conversations WHERE id = ?"
    return createFlow(__db, false, arrayOf("conversations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfRefId: Int = getColumnIndexOrThrow(_stmt, "ref_id")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unread_count")
        val _columnIndexOfPinned: Int = getColumnIndexOrThrow(_stmt, "pinned")
        val _columnIndexOfMuted: Int = getColumnIndexOrThrow(_stmt, "muted")
        val _columnIndexOfNotifyMode: Int = getColumnIndexOrThrow(_stmt, "notify_mode")
        val _columnIndexOfLastMessageAt: Int = getColumnIndexOrThrow(_stmt, "last_message_at")
        val _result: ConversationRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpRef_id: Long?
          if (_stmt.isNull(_columnIndexOfRefId)) {
            _tmpRef_id = null
          } else {
            _tmpRef_id = _stmt.getLong(_columnIndexOfRefId)
          }
          val _tmpUnread_count: Int
          _tmpUnread_count = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpPinned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfPinned).toInt()
          _tmpPinned = _tmp != 0
          val _tmpMuted: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfMuted).toInt()
          _tmpMuted = _tmp_1 != 0
          val _tmpNotify_mode: String
          _tmpNotify_mode = _stmt.getText(_columnIndexOfNotifyMode)
          val _tmpLast_message_at: Long?
          if (_stmt.isNull(_columnIndexOfLastMessageAt)) {
            _tmpLast_message_at = null
          } else {
            _tmpLast_message_at = _stmt.getLong(_columnIndexOfLastMessageAt)
          }
          _result = ConversationRow(_tmpId,_tmpIdentity_id,_tmpKind,_tmpRef_id,_tmpUnread_count,_tmpPinned,_tmpMuted,_tmpNotify_mode,_tmpLast_message_at)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun byKind(identityId: Long, kind: String): ConversationRow? {
    val _sql: String = "SELECT * FROM conversations WHERE identity_id = ? AND kind = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        _argIndex = 2
        _stmt.bindText(_argIndex, kind)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfRefId: Int = getColumnIndexOrThrow(_stmt, "ref_id")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unread_count")
        val _columnIndexOfPinned: Int = getColumnIndexOrThrow(_stmt, "pinned")
        val _columnIndexOfMuted: Int = getColumnIndexOrThrow(_stmt, "muted")
        val _columnIndexOfNotifyMode: Int = getColumnIndexOrThrow(_stmt, "notify_mode")
        val _columnIndexOfLastMessageAt: Int = getColumnIndexOrThrow(_stmt, "last_message_at")
        val _result: ConversationRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpRef_id: Long?
          if (_stmt.isNull(_columnIndexOfRefId)) {
            _tmpRef_id = null
          } else {
            _tmpRef_id = _stmt.getLong(_columnIndexOfRefId)
          }
          val _tmpUnread_count: Int
          _tmpUnread_count = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpPinned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfPinned).toInt()
          _tmpPinned = _tmp != 0
          val _tmpMuted: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfMuted).toInt()
          _tmpMuted = _tmp_1 != 0
          val _tmpNotify_mode: String
          _tmpNotify_mode = _stmt.getText(_columnIndexOfNotifyMode)
          val _tmpLast_message_at: Long?
          if (_stmt.isNull(_columnIndexOfLastMessageAt)) {
            _tmpLast_message_at = null
          } else {
            _tmpLast_message_at = _stmt.getLong(_columnIndexOfLastMessageAt)
          }
          _result = ConversationRow(_tmpId,_tmpIdentity_id,_tmpKind,_tmpRef_id,_tmpUnread_count,_tmpPinned,_tmpMuted,_tmpNotify_mode,_tmpLast_message_at)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun byKindAndRef(
    identityId: Long,
    kind: String,
    refId: Long?,
  ): ConversationRow? {
    val _sql: String = "SELECT * FROM conversations WHERE identity_id = ? AND kind = ? AND (ref_id = ? OR (ref_id IS NULL AND ? IS NULL)) LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        _argIndex = 2
        _stmt.bindText(_argIndex, kind)
        _argIndex = 3
        if (refId == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, refId)
        }
        _argIndex = 4
        if (refId == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, refId)
        }
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfRefId: Int = getColumnIndexOrThrow(_stmt, "ref_id")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unread_count")
        val _columnIndexOfPinned: Int = getColumnIndexOrThrow(_stmt, "pinned")
        val _columnIndexOfMuted: Int = getColumnIndexOrThrow(_stmt, "muted")
        val _columnIndexOfNotifyMode: Int = getColumnIndexOrThrow(_stmt, "notify_mode")
        val _columnIndexOfLastMessageAt: Int = getColumnIndexOrThrow(_stmt, "last_message_at")
        val _result: ConversationRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpRef_id: Long?
          if (_stmt.isNull(_columnIndexOfRefId)) {
            _tmpRef_id = null
          } else {
            _tmpRef_id = _stmt.getLong(_columnIndexOfRefId)
          }
          val _tmpUnread_count: Int
          _tmpUnread_count = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpPinned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfPinned).toInt()
          _tmpPinned = _tmp != 0
          val _tmpMuted: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfMuted).toInt()
          _tmpMuted = _tmp_1 != 0
          val _tmpNotify_mode: String
          _tmpNotify_mode = _stmt.getText(_columnIndexOfNotifyMode)
          val _tmpLast_message_at: Long?
          if (_stmt.isNull(_columnIndexOfLastMessageAt)) {
            _tmpLast_message_at = null
          } else {
            _tmpLast_message_at = _stmt.getLong(_columnIndexOfLastMessageAt)
          }
          _result = ConversationRow(_tmpId,_tmpIdentity_id,_tmpKind,_tmpRef_id,_tmpUnread_count,_tmpPinned,_tmpMuted,_tmpNotify_mode,_tmpLast_message_at)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun bumpUnread(id: Long, delta: Int) {
    val _sql: String = "UPDATE conversations SET unread_count = unread_count + ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, delta.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markRead(id: Long) {
    val _sql: String = "UPDATE conversations SET unread_count = 0 WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markAllRead(identityId: Long) {
    val _sql: String = "UPDATE conversations SET unread_count = 0 WHERE identity_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun touch(id: Long, at: Long) {
    val _sql: String = "UPDATE conversations SET last_message_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, at)
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
