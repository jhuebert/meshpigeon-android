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
import kotlin.Double
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
public class ContactDao_Impl(
  __db: RoomDatabase,
) : ContactDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfContactRow: EntityInsertAdapter<ContactRow>
  init {
    this.__db = __db
    this.__insertAdapterOfContactRow = object : EntityInsertAdapter<ContactRow>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `contacts` (`id`,`identity_id`,`pubkey`,`name`,`first_seen_at`,`last_seen_at`,`source`,`blocked_at`,`flags`,`note`,`last_lat`,`last_lon`,`is_repeater`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ContactRow) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.identity_id)
        statement.bindBlob(3, entity.pubkey)
        statement.bindText(4, entity.name)
        statement.bindLong(5, entity.first_seen_at)
        val _tmpLast_seen_at: Long? = entity.last_seen_at
        if (_tmpLast_seen_at == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpLast_seen_at)
        }
        statement.bindText(7, entity.source)
        val _tmpBlocked_at: Long? = entity.blocked_at
        if (_tmpBlocked_at == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpBlocked_at)
        }
        statement.bindLong(9, entity.flags.toLong())
        val _tmpNote: String? = entity.note
        if (_tmpNote == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpNote)
        }
        val _tmpLast_lat: Double? = entity.last_lat
        if (_tmpLast_lat == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpLast_lat)
        }
        val _tmpLast_lon: Double? = entity.last_lon
        if (_tmpLast_lon == null) {
          statement.bindNull(12)
        } else {
          statement.bindDouble(12, _tmpLast_lon)
        }
        val _tmp: Int = if (entity.is_repeater) 1 else 0
        statement.bindLong(13, _tmp.toLong())
      }
    }
  }

  public override suspend fun upsert(row: ContactRow): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfContactRow.insertAndReturnId(_connection, row)
    _result
  }

  public override fun observe(identityId: Long): Flow<List<ContactRow>> {
    val _sql: String = "SELECT * FROM contacts WHERE identity_id = ? ORDER BY last_seen_at DESC"
    return createFlow(__db, false, arrayOf("contacts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfPubkey: Int = getColumnIndexOrThrow(_stmt, "pubkey")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFirstSeenAt: Int = getColumnIndexOrThrow(_stmt, "first_seen_at")
        val _columnIndexOfLastSeenAt: Int = getColumnIndexOrThrow(_stmt, "last_seen_at")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfBlockedAt: Int = getColumnIndexOrThrow(_stmt, "blocked_at")
        val _columnIndexOfFlags: Int = getColumnIndexOrThrow(_stmt, "flags")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfLastLat: Int = getColumnIndexOrThrow(_stmt, "last_lat")
        val _columnIndexOfLastLon: Int = getColumnIndexOrThrow(_stmt, "last_lon")
        val _columnIndexOfIsRepeater: Int = getColumnIndexOrThrow(_stmt, "is_repeater")
        val _result: MutableList<ContactRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: ContactRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpPubkey: ByteArray
          _tmpPubkey = _stmt.getBlob(_columnIndexOfPubkey)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFirst_seen_at: Long
          _tmpFirst_seen_at = _stmt.getLong(_columnIndexOfFirstSeenAt)
          val _tmpLast_seen_at: Long?
          if (_stmt.isNull(_columnIndexOfLastSeenAt)) {
            _tmpLast_seen_at = null
          } else {
            _tmpLast_seen_at = _stmt.getLong(_columnIndexOfLastSeenAt)
          }
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpBlocked_at: Long?
          if (_stmt.isNull(_columnIndexOfBlockedAt)) {
            _tmpBlocked_at = null
          } else {
            _tmpBlocked_at = _stmt.getLong(_columnIndexOfBlockedAt)
          }
          val _tmpFlags: Int
          _tmpFlags = _stmt.getLong(_columnIndexOfFlags).toInt()
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          val _tmpLast_lat: Double?
          if (_stmt.isNull(_columnIndexOfLastLat)) {
            _tmpLast_lat = null
          } else {
            _tmpLast_lat = _stmt.getDouble(_columnIndexOfLastLat)
          }
          val _tmpLast_lon: Double?
          if (_stmt.isNull(_columnIndexOfLastLon)) {
            _tmpLast_lon = null
          } else {
            _tmpLast_lon = _stmt.getDouble(_columnIndexOfLastLon)
          }
          val _tmpIs_repeater: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRepeater).toInt()
          _tmpIs_repeater = _tmp != 0
          _item = ContactRow(_tmpId,_tmpIdentity_id,_tmpPubkey,_tmpName,_tmpFirst_seen_at,_tmpLast_seen_at,_tmpSource,_tmpBlocked_at,_tmpFlags,_tmpNote,_tmpLast_lat,_tmpLast_lon,_tmpIs_repeater)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeBlocked(identityId: Long): Flow<List<ContactRow>> {
    val _sql: String = "SELECT * FROM contacts WHERE identity_id = ? AND blocked_at IS NOT NULL"
    return createFlow(__db, false, arrayOf("contacts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfPubkey: Int = getColumnIndexOrThrow(_stmt, "pubkey")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFirstSeenAt: Int = getColumnIndexOrThrow(_stmt, "first_seen_at")
        val _columnIndexOfLastSeenAt: Int = getColumnIndexOrThrow(_stmt, "last_seen_at")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfBlockedAt: Int = getColumnIndexOrThrow(_stmt, "blocked_at")
        val _columnIndexOfFlags: Int = getColumnIndexOrThrow(_stmt, "flags")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfLastLat: Int = getColumnIndexOrThrow(_stmt, "last_lat")
        val _columnIndexOfLastLon: Int = getColumnIndexOrThrow(_stmt, "last_lon")
        val _columnIndexOfIsRepeater: Int = getColumnIndexOrThrow(_stmt, "is_repeater")
        val _result: MutableList<ContactRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: ContactRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpPubkey: ByteArray
          _tmpPubkey = _stmt.getBlob(_columnIndexOfPubkey)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFirst_seen_at: Long
          _tmpFirst_seen_at = _stmt.getLong(_columnIndexOfFirstSeenAt)
          val _tmpLast_seen_at: Long?
          if (_stmt.isNull(_columnIndexOfLastSeenAt)) {
            _tmpLast_seen_at = null
          } else {
            _tmpLast_seen_at = _stmt.getLong(_columnIndexOfLastSeenAt)
          }
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpBlocked_at: Long?
          if (_stmt.isNull(_columnIndexOfBlockedAt)) {
            _tmpBlocked_at = null
          } else {
            _tmpBlocked_at = _stmt.getLong(_columnIndexOfBlockedAt)
          }
          val _tmpFlags: Int
          _tmpFlags = _stmt.getLong(_columnIndexOfFlags).toInt()
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          val _tmpLast_lat: Double?
          if (_stmt.isNull(_columnIndexOfLastLat)) {
            _tmpLast_lat = null
          } else {
            _tmpLast_lat = _stmt.getDouble(_columnIndexOfLastLat)
          }
          val _tmpLast_lon: Double?
          if (_stmt.isNull(_columnIndexOfLastLon)) {
            _tmpLast_lon = null
          } else {
            _tmpLast_lon = _stmt.getDouble(_columnIndexOfLastLon)
          }
          val _tmpIs_repeater: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRepeater).toInt()
          _tmpIs_repeater = _tmp != 0
          _item = ContactRow(_tmpId,_tmpIdentity_id,_tmpPubkey,_tmpName,_tmpFirst_seen_at,_tmpLast_seen_at,_tmpSource,_tmpBlocked_at,_tmpFlags,_tmpNote,_tmpLast_lat,_tmpLast_lon,_tmpIs_repeater)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observePending(identityId: Long): Flow<List<ContactRow>> {
    val _sql: String = "SELECT * FROM contacts WHERE identity_id = ? AND source = 'ADVERT' AND blocked_at IS NULL"
    return createFlow(__db, false, arrayOf("contacts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfPubkey: Int = getColumnIndexOrThrow(_stmt, "pubkey")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFirstSeenAt: Int = getColumnIndexOrThrow(_stmt, "first_seen_at")
        val _columnIndexOfLastSeenAt: Int = getColumnIndexOrThrow(_stmt, "last_seen_at")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfBlockedAt: Int = getColumnIndexOrThrow(_stmt, "blocked_at")
        val _columnIndexOfFlags: Int = getColumnIndexOrThrow(_stmt, "flags")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfLastLat: Int = getColumnIndexOrThrow(_stmt, "last_lat")
        val _columnIndexOfLastLon: Int = getColumnIndexOrThrow(_stmt, "last_lon")
        val _columnIndexOfIsRepeater: Int = getColumnIndexOrThrow(_stmt, "is_repeater")
        val _result: MutableList<ContactRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: ContactRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpPubkey: ByteArray
          _tmpPubkey = _stmt.getBlob(_columnIndexOfPubkey)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFirst_seen_at: Long
          _tmpFirst_seen_at = _stmt.getLong(_columnIndexOfFirstSeenAt)
          val _tmpLast_seen_at: Long?
          if (_stmt.isNull(_columnIndexOfLastSeenAt)) {
            _tmpLast_seen_at = null
          } else {
            _tmpLast_seen_at = _stmt.getLong(_columnIndexOfLastSeenAt)
          }
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpBlocked_at: Long?
          if (_stmt.isNull(_columnIndexOfBlockedAt)) {
            _tmpBlocked_at = null
          } else {
            _tmpBlocked_at = _stmt.getLong(_columnIndexOfBlockedAt)
          }
          val _tmpFlags: Int
          _tmpFlags = _stmt.getLong(_columnIndexOfFlags).toInt()
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          val _tmpLast_lat: Double?
          if (_stmt.isNull(_columnIndexOfLastLat)) {
            _tmpLast_lat = null
          } else {
            _tmpLast_lat = _stmt.getDouble(_columnIndexOfLastLat)
          }
          val _tmpLast_lon: Double?
          if (_stmt.isNull(_columnIndexOfLastLon)) {
            _tmpLast_lon = null
          } else {
            _tmpLast_lon = _stmt.getDouble(_columnIndexOfLastLon)
          }
          val _tmpIs_repeater: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRepeater).toInt()
          _tmpIs_repeater = _tmp != 0
          _item = ContactRow(_tmpId,_tmpIdentity_id,_tmpPubkey,_tmpName,_tmpFirst_seen_at,_tmpLast_seen_at,_tmpSource,_tmpBlocked_at,_tmpFlags,_tmpNote,_tmpLast_lat,_tmpLast_lon,_tmpIs_repeater)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun byPubkey(identityId: Long, pubkey: ByteArray): ContactRow? {
    val _sql: String = "SELECT * FROM contacts WHERE identity_id = ? AND pubkey = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        _argIndex = 2
        _stmt.bindBlob(_argIndex, pubkey)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfPubkey: Int = getColumnIndexOrThrow(_stmt, "pubkey")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFirstSeenAt: Int = getColumnIndexOrThrow(_stmt, "first_seen_at")
        val _columnIndexOfLastSeenAt: Int = getColumnIndexOrThrow(_stmt, "last_seen_at")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfBlockedAt: Int = getColumnIndexOrThrow(_stmt, "blocked_at")
        val _columnIndexOfFlags: Int = getColumnIndexOrThrow(_stmt, "flags")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfLastLat: Int = getColumnIndexOrThrow(_stmt, "last_lat")
        val _columnIndexOfLastLon: Int = getColumnIndexOrThrow(_stmt, "last_lon")
        val _columnIndexOfIsRepeater: Int = getColumnIndexOrThrow(_stmt, "is_repeater")
        val _result: ContactRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpPubkey: ByteArray
          _tmpPubkey = _stmt.getBlob(_columnIndexOfPubkey)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFirst_seen_at: Long
          _tmpFirst_seen_at = _stmt.getLong(_columnIndexOfFirstSeenAt)
          val _tmpLast_seen_at: Long?
          if (_stmt.isNull(_columnIndexOfLastSeenAt)) {
            _tmpLast_seen_at = null
          } else {
            _tmpLast_seen_at = _stmt.getLong(_columnIndexOfLastSeenAt)
          }
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpBlocked_at: Long?
          if (_stmt.isNull(_columnIndexOfBlockedAt)) {
            _tmpBlocked_at = null
          } else {
            _tmpBlocked_at = _stmt.getLong(_columnIndexOfBlockedAt)
          }
          val _tmpFlags: Int
          _tmpFlags = _stmt.getLong(_columnIndexOfFlags).toInt()
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          val _tmpLast_lat: Double?
          if (_stmt.isNull(_columnIndexOfLastLat)) {
            _tmpLast_lat = null
          } else {
            _tmpLast_lat = _stmt.getDouble(_columnIndexOfLastLat)
          }
          val _tmpLast_lon: Double?
          if (_stmt.isNull(_columnIndexOfLastLon)) {
            _tmpLast_lon = null
          } else {
            _tmpLast_lon = _stmt.getDouble(_columnIndexOfLastLon)
          }
          val _tmpIs_repeater: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRepeater).toInt()
          _tmpIs_repeater = _tmp != 0
          _result = ContactRow(_tmpId,_tmpIdentity_id,_tmpPubkey,_tmpName,_tmpFirst_seen_at,_tmpLast_seen_at,_tmpSource,_tmpBlocked_at,_tmpFlags,_tmpNote,_tmpLast_lat,_tmpLast_lon,_tmpIs_repeater)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun block(id: Long, at: Long) {
    val _sql: String = "UPDATE contacts SET blocked_at = ? WHERE id = ?"
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

  public override suspend fun unblock(id: Long) {
    val _sql: String = "UPDATE contacts SET blocked_at = NULL WHERE id = ?"
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

  public override suspend fun rename(id: Long, name: String) {
    val _sql: String = "UPDATE contacts SET name = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, name)
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearPending(identityId: Long) {
    val _sql: String = "DELETE FROM contacts WHERE identity_id = ? AND source = 'ADVERT' AND blocked_at IS NULL"
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

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
