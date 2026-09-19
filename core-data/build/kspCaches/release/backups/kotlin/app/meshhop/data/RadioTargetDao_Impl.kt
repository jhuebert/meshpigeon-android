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
public class RadioTargetDao_Impl(
  __db: RoomDatabase,
) : RadioTargetDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfRadioTargetRow: EntityInsertAdapter<RadioTargetRow>
  init {
    this.__db = __db
    this.__insertAdapterOfRadioTargetRow = object : EntityInsertAdapter<RadioTargetRow>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `radio_targets` (`id`,`persistent_id`,`name`,`link_kind`,`link_addr`,`pref_order`,`preferred`,`desired_settings_json`,`last_connected_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: RadioTargetRow) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.persistent_id)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.link_kind)
        statement.bindText(5, entity.link_addr)
        statement.bindLong(6, entity.pref_order.toLong())
        val _tmp: Int = if (entity.preferred) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        val _tmpDesired_settings_json: String? = entity.desired_settings_json
        if (_tmpDesired_settings_json == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpDesired_settings_json)
        }
        val _tmpLast_connected_at: Long? = entity.last_connected_at
        if (_tmpLast_connected_at == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpLast_connected_at)
        }
      }
    }
  }

  public override suspend fun upsert(row: RadioTargetRow): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfRadioTargetRow.insertAndReturnId(_connection, row)
    _result
  }

  public override fun observeAll(): Flow<List<RadioTargetRow>> {
    val _sql: String = "SELECT * FROM radio_targets ORDER BY pref_order"
    return createFlow(__db, false, arrayOf("radio_targets")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPersistentId: Int = getColumnIndexOrThrow(_stmt, "persistent_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfLinkKind: Int = getColumnIndexOrThrow(_stmt, "link_kind")
        val _columnIndexOfLinkAddr: Int = getColumnIndexOrThrow(_stmt, "link_addr")
        val _columnIndexOfPrefOrder: Int = getColumnIndexOrThrow(_stmt, "pref_order")
        val _columnIndexOfPreferred: Int = getColumnIndexOrThrow(_stmt, "preferred")
        val _columnIndexOfDesiredSettingsJson: Int = getColumnIndexOrThrow(_stmt, "desired_settings_json")
        val _columnIndexOfLastConnectedAt: Int = getColumnIndexOrThrow(_stmt, "last_connected_at")
        val _result: MutableList<RadioTargetRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: RadioTargetRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPersistent_id: String
          _tmpPersistent_id = _stmt.getText(_columnIndexOfPersistentId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpLink_kind: String
          _tmpLink_kind = _stmt.getText(_columnIndexOfLinkKind)
          val _tmpLink_addr: String
          _tmpLink_addr = _stmt.getText(_columnIndexOfLinkAddr)
          val _tmpPref_order: Int
          _tmpPref_order = _stmt.getLong(_columnIndexOfPrefOrder).toInt()
          val _tmpPreferred: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfPreferred).toInt()
          _tmpPreferred = _tmp != 0
          val _tmpDesired_settings_json: String?
          if (_stmt.isNull(_columnIndexOfDesiredSettingsJson)) {
            _tmpDesired_settings_json = null
          } else {
            _tmpDesired_settings_json = _stmt.getText(_columnIndexOfDesiredSettingsJson)
          }
          val _tmpLast_connected_at: Long?
          if (_stmt.isNull(_columnIndexOfLastConnectedAt)) {
            _tmpLast_connected_at = null
          } else {
            _tmpLast_connected_at = _stmt.getLong(_columnIndexOfLastConnectedAt)
          }
          _item = RadioTargetRow(_tmpId,_tmpPersistent_id,_tmpName,_tmpLink_kind,_tmpLink_addr,_tmpPref_order,_tmpPreferred,_tmpDesired_settings_json,_tmpLast_connected_at)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun delete(id: Long) {
    val _sql: String = "DELETE FROM radio_targets WHERE id = ?"
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

  public override suspend fun setOrder(id: Long, order: Int) {
    val _sql: String = "UPDATE radio_targets SET pref_order = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, order.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setPreferred(id: Long, preferred: Boolean) {
    val _sql: String = "UPDATE radio_targets SET preferred = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (preferred) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
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
