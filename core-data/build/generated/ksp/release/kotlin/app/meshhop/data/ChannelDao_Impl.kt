package app.meshhop.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.ByteArray
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
public class ChannelDao_Impl(
  __db: RoomDatabase,
) : ChannelDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChannelRow: EntityInsertAdapter<ChannelRow>
  init {
    this.__db = __db
    this.__insertAdapterOfChannelRow = object : EntityInsertAdapter<ChannelRow>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `channels` (`id`,`identity_id`,`name`,`key_enc`,`kind`,`created_at`,`pinned`,`muted`,`notify_mode`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChannelRow) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.identity_id)
        statement.bindText(3, entity.name)
        statement.bindBlob(4, entity.key_enc)
        statement.bindText(5, entity.kind)
        statement.bindLong(6, entity.created_at)
        val _tmp: Int = if (entity.pinned) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        val _tmp_1: Int = if (entity.muted) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        statement.bindText(9, entity.notify_mode)
      }
    }
  }

  public override suspend fun upsert(row: ChannelRow): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfChannelRow.insertAndReturnId(_connection, row)
    _result
  }

  public override fun observe(identityId: Long): Flow<List<ChannelRow>> {
    val _sql: String = "SELECT * FROM channels WHERE identity_id = ? ORDER BY pinned DESC, created_at DESC"
    return createFlow(__db, false, arrayOf("channels")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfKeyEnc: Int = getColumnIndexOrThrow(_stmt, "key_enc")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfPinned: Int = getColumnIndexOrThrow(_stmt, "pinned")
        val _columnIndexOfMuted: Int = getColumnIndexOrThrow(_stmt, "muted")
        val _columnIndexOfNotifyMode: Int = getColumnIndexOrThrow(_stmt, "notify_mode")
        val _result: MutableList<ChannelRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChannelRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpKey_enc: ByteArray
          _tmpKey_enc = _stmt.getBlob(_columnIndexOfKeyEnc)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpCreated_at: Long
          _tmpCreated_at = _stmt.getLong(_columnIndexOfCreatedAt)
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
          _item = ChannelRow(_tmpId,_tmpIdentity_id,_tmpName,_tmpKey_enc,_tmpKind,_tmpCreated_at,_tmpPinned,_tmpMuted,_tmpNotify_mode)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun byId(id: Long): ChannelRow? {
    val _sql: String = "SELECT * FROM channels WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfKeyEnc: Int = getColumnIndexOrThrow(_stmt, "key_enc")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfPinned: Int = getColumnIndexOrThrow(_stmt, "pinned")
        val _columnIndexOfMuted: Int = getColumnIndexOrThrow(_stmt, "muted")
        val _columnIndexOfNotifyMode: Int = getColumnIndexOrThrow(_stmt, "notify_mode")
        val _result: ChannelRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpKey_enc: ByteArray
          _tmpKey_enc = _stmt.getBlob(_columnIndexOfKeyEnc)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpCreated_at: Long
          _tmpCreated_at = _stmt.getLong(_columnIndexOfCreatedAt)
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
          _result = ChannelRow(_tmpId,_tmpIdentity_id,_tmpName,_tmpKey_enc,_tmpKind,_tmpCreated_at,_tmpPinned,_tmpMuted,_tmpNotify_mode)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun delete(id: Long) {
    val _sql: String = "DELETE FROM channels WHERE id = ?"
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

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
