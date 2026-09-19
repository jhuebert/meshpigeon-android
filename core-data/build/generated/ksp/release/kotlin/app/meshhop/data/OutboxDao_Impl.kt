package app.meshhop.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
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
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class OutboxDao_Impl(
  __db: RoomDatabase,
) : OutboxDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfOutboxRow: EntityInsertAdapter<OutboxRow>

  private val __updateAdapterOfOutboxRow: EntityDeleteOrUpdateAdapter<OutboxRow>
  init {
    this.__db = __db
    this.__insertAdapterOfOutboxRow = object : EntityInsertAdapter<OutboxRow>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `outbox` (`id`,`conversation_id`,`packet`,`ack_key`,`attempts`,`next_retry_at`,`state`,`airtime_ms`,`hops`,`direct`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: OutboxRow) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.conversation_id)
        statement.bindBlob(3, entity.packet)
        val _tmpAck_key: ByteArray? = entity.ack_key
        if (_tmpAck_key == null) {
          statement.bindNull(4)
        } else {
          statement.bindBlob(4, _tmpAck_key)
        }
        statement.bindLong(5, entity.attempts.toLong())
        statement.bindLong(6, entity.next_retry_at)
        statement.bindText(7, entity.state)
        statement.bindDouble(8, entity.airtime_ms)
        statement.bindLong(9, entity.hops.toLong())
        val _tmp: Int = if (entity.direct) 1 else 0
        statement.bindLong(10, _tmp.toLong())
      }
    }
    this.__updateAdapterOfOutboxRow = object : EntityDeleteOrUpdateAdapter<OutboxRow>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `outbox` SET `id` = ?,`conversation_id` = ?,`packet` = ?,`ack_key` = ?,`attempts` = ?,`next_retry_at` = ?,`state` = ?,`airtime_ms` = ?,`hops` = ?,`direct` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: OutboxRow) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.conversation_id)
        statement.bindBlob(3, entity.packet)
        val _tmpAck_key: ByteArray? = entity.ack_key
        if (_tmpAck_key == null) {
          statement.bindNull(4)
        } else {
          statement.bindBlob(4, _tmpAck_key)
        }
        statement.bindLong(5, entity.attempts.toLong())
        statement.bindLong(6, entity.next_retry_at)
        statement.bindText(7, entity.state)
        statement.bindDouble(8, entity.airtime_ms)
        statement.bindLong(9, entity.hops.toLong())
        val _tmp: Int = if (entity.direct) 1 else 0
        statement.bindLong(10, _tmp.toLong())
        statement.bindLong(11, entity.id)
      }
    }
  }

  public override suspend fun insert(row: OutboxRow): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfOutboxRow.insertAndReturnId(_connection, row)
    _result
  }

  public override suspend fun update(row: OutboxRow): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfOutboxRow.handle(_connection, row)
  }

  public override fun due(now: Long, limit: Int): Flow<List<OutboxRow>> {
    val _sql: String = "SELECT * FROM outbox WHERE next_retry_at <= ? ORDER BY id LIMIT ?"
    return createFlow(__db, false, arrayOf("outbox")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, now)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversation_id")
        val _columnIndexOfPacket: Int = getColumnIndexOrThrow(_stmt, "packet")
        val _columnIndexOfAckKey: Int = getColumnIndexOrThrow(_stmt, "ack_key")
        val _columnIndexOfAttempts: Int = getColumnIndexOrThrow(_stmt, "attempts")
        val _columnIndexOfNextRetryAt: Int = getColumnIndexOrThrow(_stmt, "next_retry_at")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfAirtimeMs: Int = getColumnIndexOrThrow(_stmt, "airtime_ms")
        val _columnIndexOfHops: Int = getColumnIndexOrThrow(_stmt, "hops")
        val _columnIndexOfDirect: Int = getColumnIndexOrThrow(_stmt, "direct")
        val _result: MutableList<OutboxRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: OutboxRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpConversation_id: Long
          _tmpConversation_id = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpPacket: ByteArray
          _tmpPacket = _stmt.getBlob(_columnIndexOfPacket)
          val _tmpAck_key: ByteArray?
          if (_stmt.isNull(_columnIndexOfAckKey)) {
            _tmpAck_key = null
          } else {
            _tmpAck_key = _stmt.getBlob(_columnIndexOfAckKey)
          }
          val _tmpAttempts: Int
          _tmpAttempts = _stmt.getLong(_columnIndexOfAttempts).toInt()
          val _tmpNext_retry_at: Long
          _tmpNext_retry_at = _stmt.getLong(_columnIndexOfNextRetryAt)
          val _tmpState: String
          _tmpState = _stmt.getText(_columnIndexOfState)
          val _tmpAirtime_ms: Double
          _tmpAirtime_ms = _stmt.getDouble(_columnIndexOfAirtimeMs)
          val _tmpHops: Int
          _tmpHops = _stmt.getLong(_columnIndexOfHops).toInt()
          val _tmpDirect: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfDirect).toInt()
          _tmpDirect = _tmp != 0
          _item = OutboxRow(_tmpId,_tmpConversation_id,_tmpPacket,_tmpAck_key,_tmpAttempts,_tmpNext_retry_at,_tmpState,_tmpAirtime_ms,_tmpHops,_tmpDirect)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun firstFor(conversationId: Long): OutboxRow? {
    val _sql: String = "SELECT * FROM outbox WHERE conversation_id = ? ORDER BY id LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, conversationId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversation_id")
        val _columnIndexOfPacket: Int = getColumnIndexOrThrow(_stmt, "packet")
        val _columnIndexOfAckKey: Int = getColumnIndexOrThrow(_stmt, "ack_key")
        val _columnIndexOfAttempts: Int = getColumnIndexOrThrow(_stmt, "attempts")
        val _columnIndexOfNextRetryAt: Int = getColumnIndexOrThrow(_stmt, "next_retry_at")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfAirtimeMs: Int = getColumnIndexOrThrow(_stmt, "airtime_ms")
        val _columnIndexOfHops: Int = getColumnIndexOrThrow(_stmt, "hops")
        val _columnIndexOfDirect: Int = getColumnIndexOrThrow(_stmt, "direct")
        val _result: OutboxRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpConversation_id: Long
          _tmpConversation_id = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpPacket: ByteArray
          _tmpPacket = _stmt.getBlob(_columnIndexOfPacket)
          val _tmpAck_key: ByteArray?
          if (_stmt.isNull(_columnIndexOfAckKey)) {
            _tmpAck_key = null
          } else {
            _tmpAck_key = _stmt.getBlob(_columnIndexOfAckKey)
          }
          val _tmpAttempts: Int
          _tmpAttempts = _stmt.getLong(_columnIndexOfAttempts).toInt()
          val _tmpNext_retry_at: Long
          _tmpNext_retry_at = _stmt.getLong(_columnIndexOfNextRetryAt)
          val _tmpState: String
          _tmpState = _stmt.getText(_columnIndexOfState)
          val _tmpAirtime_ms: Double
          _tmpAirtime_ms = _stmt.getDouble(_columnIndexOfAirtimeMs)
          val _tmpHops: Int
          _tmpHops = _stmt.getLong(_columnIndexOfHops).toInt()
          val _tmpDirect: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfDirect).toInt()
          _tmpDirect = _tmp != 0
          _result = OutboxRow(_tmpId,_tmpConversation_id,_tmpPacket,_tmpAck_key,_tmpAttempts,_tmpNext_retry_at,_tmpState,_tmpAirtime_ms,_tmpHops,_tmpDirect)
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
    val _sql: String = "DELETE FROM outbox WHERE id = ?"
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
