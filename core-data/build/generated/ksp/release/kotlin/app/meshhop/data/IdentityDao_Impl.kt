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
public class IdentityDao_Impl(
  __db: RoomDatabase,
) : IdentityDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfIdentityRow: EntityInsertAdapter<IdentityRow>
  init {
    this.__db = __db
    this.__insertAdapterOfIdentityRow = object : EntityInsertAdapter<IdentityRow>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `identities` (`id`,`name`,`pubkey`,`privkey_enc`,`flags`,`created_at`,`is_active`,`advert_policy`,`last_advert_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: IdentityRow) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindBlob(3, entity.pubkey)
        statement.bindBlob(4, entity.privkey_enc)
        statement.bindLong(5, entity.flags.toLong())
        statement.bindLong(6, entity.created_at)
        val _tmp: Int = if (entity.is_active) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        statement.bindText(8, entity.advert_policy)
        val _tmpLast_advert_at: Long? = entity.last_advert_at
        if (_tmpLast_advert_at == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpLast_advert_at)
        }
      }
    }
  }

  public override suspend fun upsert(row: IdentityRow): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfIdentityRow.insertAndReturnId(_connection, row)
    _result
  }

  public override fun observeActive(): Flow<IdentityRow?> {
    val _sql: String = "SELECT * FROM identities WHERE is_active = 1 LIMIT 1"
    return createFlow(__db, false, arrayOf("identities")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfPubkey: Int = getColumnIndexOrThrow(_stmt, "pubkey")
        val _columnIndexOfPrivkeyEnc: Int = getColumnIndexOrThrow(_stmt, "privkey_enc")
        val _columnIndexOfFlags: Int = getColumnIndexOrThrow(_stmt, "flags")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _columnIndexOfAdvertPolicy: Int = getColumnIndexOrThrow(_stmt, "advert_policy")
        val _columnIndexOfLastAdvertAt: Int = getColumnIndexOrThrow(_stmt, "last_advert_at")
        val _result: IdentityRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpPubkey: ByteArray
          _tmpPubkey = _stmt.getBlob(_columnIndexOfPubkey)
          val _tmpPrivkey_enc: ByteArray
          _tmpPrivkey_enc = _stmt.getBlob(_columnIndexOfPrivkeyEnc)
          val _tmpFlags: Int
          _tmpFlags = _stmt.getLong(_columnIndexOfFlags).toInt()
          val _tmpCreated_at: Long
          _tmpCreated_at = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIs_active: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIs_active = _tmp != 0
          val _tmpAdvert_policy: String
          _tmpAdvert_policy = _stmt.getText(_columnIndexOfAdvertPolicy)
          val _tmpLast_advert_at: Long?
          if (_stmt.isNull(_columnIndexOfLastAdvertAt)) {
            _tmpLast_advert_at = null
          } else {
            _tmpLast_advert_at = _stmt.getLong(_columnIndexOfLastAdvertAt)
          }
          _result = IdentityRow(_tmpId,_tmpName,_tmpPubkey,_tmpPrivkey_enc,_tmpFlags,_tmpCreated_at,_tmpIs_active,_tmpAdvert_policy,_tmpLast_advert_at)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeAll(): Flow<List<IdentityRow>> {
    val _sql: String = "SELECT * FROM identities ORDER BY created_at"
    return createFlow(__db, false, arrayOf("identities")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfPubkey: Int = getColumnIndexOrThrow(_stmt, "pubkey")
        val _columnIndexOfPrivkeyEnc: Int = getColumnIndexOrThrow(_stmt, "privkey_enc")
        val _columnIndexOfFlags: Int = getColumnIndexOrThrow(_stmt, "flags")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _columnIndexOfAdvertPolicy: Int = getColumnIndexOrThrow(_stmt, "advert_policy")
        val _columnIndexOfLastAdvertAt: Int = getColumnIndexOrThrow(_stmt, "last_advert_at")
        val _result: MutableList<IdentityRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: IdentityRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpPubkey: ByteArray
          _tmpPubkey = _stmt.getBlob(_columnIndexOfPubkey)
          val _tmpPrivkey_enc: ByteArray
          _tmpPrivkey_enc = _stmt.getBlob(_columnIndexOfPrivkeyEnc)
          val _tmpFlags: Int
          _tmpFlags = _stmt.getLong(_columnIndexOfFlags).toInt()
          val _tmpCreated_at: Long
          _tmpCreated_at = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIs_active: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIs_active = _tmp != 0
          val _tmpAdvert_policy: String
          _tmpAdvert_policy = _stmt.getText(_columnIndexOfAdvertPolicy)
          val _tmpLast_advert_at: Long?
          if (_stmt.isNull(_columnIndexOfLastAdvertAt)) {
            _tmpLast_advert_at = null
          } else {
            _tmpLast_advert_at = _stmt.getLong(_columnIndexOfLastAdvertAt)
          }
          _item = IdentityRow(_tmpId,_tmpName,_tmpPubkey,_tmpPrivkey_enc,_tmpFlags,_tmpCreated_at,_tmpIs_active,_tmpAdvert_policy,_tmpLast_advert_at)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setActive(id: Long) {
    val _sql: String = "UPDATE identities SET is_active = (id = ?)"
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

  public override suspend fun delete(id: Long) {
    val _sql: String = "DELETE FROM identities WHERE id = ?"
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
