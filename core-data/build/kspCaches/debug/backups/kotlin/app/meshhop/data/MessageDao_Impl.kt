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
import kotlin.Float
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
public class MessageDao_Impl(
  __db: RoomDatabase,
) : MessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMessageRow: EntityInsertAdapter<MessageRow>
  init {
    this.__db = __db
    this.__insertAdapterOfMessageRow = object : EntityInsertAdapter<MessageRow>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `messages` (`id`,`conversation_id`,`identity_id`,`sender_contact_id`,`sender_name`,`body`,`kind`,`sent_at`,`out`,`state`,`snr`,`rssi`,`hops`,`region`,`packet_tag`,`reply_to_id`,`ack_key`,`rtt_ms`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageRow) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.conversation_id)
        statement.bindLong(3, entity.identity_id)
        val _tmpSender_contact_id: Long? = entity.sender_contact_id
        if (_tmpSender_contact_id == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmpSender_contact_id)
        }
        val _tmpSender_name: String? = entity.sender_name
        if (_tmpSender_name == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSender_name)
        }
        statement.bindText(6, entity.body)
        statement.bindText(7, entity.kind)
        statement.bindLong(8, entity.sent_at)
        val _tmp: Int = if (entity.out) 1 else 0
        statement.bindLong(9, _tmp.toLong())
        val _tmpState: String? = entity.state
        if (_tmpState == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpState)
        }
        val _tmpSnr: Float? = entity.snr
        if (_tmpSnr == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpSnr.toDouble())
        }
        val _tmpRssi: Int? = entity.rssi
        if (_tmpRssi == null) {
          statement.bindNull(12)
        } else {
          statement.bindLong(12, _tmpRssi.toLong())
        }
        val _tmpHops: Int? = entity.hops
        if (_tmpHops == null) {
          statement.bindNull(13)
        } else {
          statement.bindLong(13, _tmpHops.toLong())
        }
        val _tmpRegion: String? = entity.region
        if (_tmpRegion == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpRegion)
        }
        val _tmpPacket_tag: ByteArray? = entity.packet_tag
        if (_tmpPacket_tag == null) {
          statement.bindNull(15)
        } else {
          statement.bindBlob(15, _tmpPacket_tag)
        }
        val _tmpReply_to_id: Long? = entity.reply_to_id
        if (_tmpReply_to_id == null) {
          statement.bindNull(16)
        } else {
          statement.bindLong(16, _tmpReply_to_id)
        }
        val _tmpAck_key: ByteArray? = entity.ack_key
        if (_tmpAck_key == null) {
          statement.bindNull(17)
        } else {
          statement.bindBlob(17, _tmpAck_key)
        }
        val _tmpRtt_ms: Long? = entity.rtt_ms
        if (_tmpRtt_ms == null) {
          statement.bindNull(18)
        } else {
          statement.bindLong(18, _tmpRtt_ms)
        }
      }
    }
  }

  public override suspend fun insert(row: MessageRow): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfMessageRow.insertAndReturnId(_connection, row)
    _result
  }

  public override fun observe(conversationId: Long, limit: Int): Flow<List<MessageRow>> {
    val _sql: String = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY sent_at ASC LIMIT ?"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, conversationId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversation_id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfSenderContactId: Int = getColumnIndexOrThrow(_stmt, "sender_contact_id")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "sender_name")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfSentAt: Int = getColumnIndexOrThrow(_stmt, "sent_at")
        val _columnIndexOfOut: Int = getColumnIndexOrThrow(_stmt, "out")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfSnr: Int = getColumnIndexOrThrow(_stmt, "snr")
        val _columnIndexOfRssi: Int = getColumnIndexOrThrow(_stmt, "rssi")
        val _columnIndexOfHops: Int = getColumnIndexOrThrow(_stmt, "hops")
        val _columnIndexOfRegion: Int = getColumnIndexOrThrow(_stmt, "region")
        val _columnIndexOfPacketTag: Int = getColumnIndexOrThrow(_stmt, "packet_tag")
        val _columnIndexOfReplyToId: Int = getColumnIndexOrThrow(_stmt, "reply_to_id")
        val _columnIndexOfAckKey: Int = getColumnIndexOrThrow(_stmt, "ack_key")
        val _columnIndexOfRttMs: Int = getColumnIndexOrThrow(_stmt, "rtt_ms")
        val _result: MutableList<MessageRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageRow
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpConversation_id: Long
          _tmpConversation_id = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpSender_contact_id: Long?
          if (_stmt.isNull(_columnIndexOfSenderContactId)) {
            _tmpSender_contact_id = null
          } else {
            _tmpSender_contact_id = _stmt.getLong(_columnIndexOfSenderContactId)
          }
          val _tmpSender_name: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSender_name = null
          } else {
            _tmpSender_name = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpSent_at: Long
          _tmpSent_at = _stmt.getLong(_columnIndexOfSentAt)
          val _tmpOut: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfOut).toInt()
          _tmpOut = _tmp != 0
          val _tmpState: String?
          if (_stmt.isNull(_columnIndexOfState)) {
            _tmpState = null
          } else {
            _tmpState = _stmt.getText(_columnIndexOfState)
          }
          val _tmpSnr: Float?
          if (_stmt.isNull(_columnIndexOfSnr)) {
            _tmpSnr = null
          } else {
            _tmpSnr = _stmt.getDouble(_columnIndexOfSnr).toFloat()
          }
          val _tmpRssi: Int?
          if (_stmt.isNull(_columnIndexOfRssi)) {
            _tmpRssi = null
          } else {
            _tmpRssi = _stmt.getLong(_columnIndexOfRssi).toInt()
          }
          val _tmpHops: Int?
          if (_stmt.isNull(_columnIndexOfHops)) {
            _tmpHops = null
          } else {
            _tmpHops = _stmt.getLong(_columnIndexOfHops).toInt()
          }
          val _tmpRegion: String?
          if (_stmt.isNull(_columnIndexOfRegion)) {
            _tmpRegion = null
          } else {
            _tmpRegion = _stmt.getText(_columnIndexOfRegion)
          }
          val _tmpPacket_tag: ByteArray?
          if (_stmt.isNull(_columnIndexOfPacketTag)) {
            _tmpPacket_tag = null
          } else {
            _tmpPacket_tag = _stmt.getBlob(_columnIndexOfPacketTag)
          }
          val _tmpReply_to_id: Long?
          if (_stmt.isNull(_columnIndexOfReplyToId)) {
            _tmpReply_to_id = null
          } else {
            _tmpReply_to_id = _stmt.getLong(_columnIndexOfReplyToId)
          }
          val _tmpAck_key: ByteArray?
          if (_stmt.isNull(_columnIndexOfAckKey)) {
            _tmpAck_key = null
          } else {
            _tmpAck_key = _stmt.getBlob(_columnIndexOfAckKey)
          }
          val _tmpRtt_ms: Long?
          if (_stmt.isNull(_columnIndexOfRttMs)) {
            _tmpRtt_ms = null
          } else {
            _tmpRtt_ms = _stmt.getLong(_columnIndexOfRttMs)
          }
          _item = MessageRow(_tmpId,_tmpConversation_id,_tmpIdentity_id,_tmpSender_contact_id,_tmpSender_name,_tmpBody,_tmpKind,_tmpSent_at,_tmpOut,_tmpState,_tmpSnr,_tmpRssi,_tmpHops,_tmpRegion,_tmpPacket_tag,_tmpReply_to_id,_tmpAck_key,_tmpRtt_ms)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun byAckKey(identityId: Long, ackKey: ByteArray): MessageRow? {
    val _sql: String = "SELECT * FROM messages WHERE identity_id = ? AND ack_key = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, identityId)
        _argIndex = 2
        _stmt.bindBlob(_argIndex, ackKey)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversation_id")
        val _columnIndexOfIdentityId: Int = getColumnIndexOrThrow(_stmt, "identity_id")
        val _columnIndexOfSenderContactId: Int = getColumnIndexOrThrow(_stmt, "sender_contact_id")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "sender_name")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfSentAt: Int = getColumnIndexOrThrow(_stmt, "sent_at")
        val _columnIndexOfOut: Int = getColumnIndexOrThrow(_stmt, "out")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfSnr: Int = getColumnIndexOrThrow(_stmt, "snr")
        val _columnIndexOfRssi: Int = getColumnIndexOrThrow(_stmt, "rssi")
        val _columnIndexOfHops: Int = getColumnIndexOrThrow(_stmt, "hops")
        val _columnIndexOfRegion: Int = getColumnIndexOrThrow(_stmt, "region")
        val _columnIndexOfPacketTag: Int = getColumnIndexOrThrow(_stmt, "packet_tag")
        val _columnIndexOfReplyToId: Int = getColumnIndexOrThrow(_stmt, "reply_to_id")
        val _columnIndexOfAckKey: Int = getColumnIndexOrThrow(_stmt, "ack_key")
        val _columnIndexOfRttMs: Int = getColumnIndexOrThrow(_stmt, "rtt_ms")
        val _result: MessageRow?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpConversation_id: Long
          _tmpConversation_id = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpIdentity_id: Long
          _tmpIdentity_id = _stmt.getLong(_columnIndexOfIdentityId)
          val _tmpSender_contact_id: Long?
          if (_stmt.isNull(_columnIndexOfSenderContactId)) {
            _tmpSender_contact_id = null
          } else {
            _tmpSender_contact_id = _stmt.getLong(_columnIndexOfSenderContactId)
          }
          val _tmpSender_name: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSender_name = null
          } else {
            _tmpSender_name = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpSent_at: Long
          _tmpSent_at = _stmt.getLong(_columnIndexOfSentAt)
          val _tmpOut: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfOut).toInt()
          _tmpOut = _tmp != 0
          val _tmpState: String?
          if (_stmt.isNull(_columnIndexOfState)) {
            _tmpState = null
          } else {
            _tmpState = _stmt.getText(_columnIndexOfState)
          }
          val _tmpSnr: Float?
          if (_stmt.isNull(_columnIndexOfSnr)) {
            _tmpSnr = null
          } else {
            _tmpSnr = _stmt.getDouble(_columnIndexOfSnr).toFloat()
          }
          val _tmpRssi: Int?
          if (_stmt.isNull(_columnIndexOfRssi)) {
            _tmpRssi = null
          } else {
            _tmpRssi = _stmt.getLong(_columnIndexOfRssi).toInt()
          }
          val _tmpHops: Int?
          if (_stmt.isNull(_columnIndexOfHops)) {
            _tmpHops = null
          } else {
            _tmpHops = _stmt.getLong(_columnIndexOfHops).toInt()
          }
          val _tmpRegion: String?
          if (_stmt.isNull(_columnIndexOfRegion)) {
            _tmpRegion = null
          } else {
            _tmpRegion = _stmt.getText(_columnIndexOfRegion)
          }
          val _tmpPacket_tag: ByteArray?
          if (_stmt.isNull(_columnIndexOfPacketTag)) {
            _tmpPacket_tag = null
          } else {
            _tmpPacket_tag = _stmt.getBlob(_columnIndexOfPacketTag)
          }
          val _tmpReply_to_id: Long?
          if (_stmt.isNull(_columnIndexOfReplyToId)) {
            _tmpReply_to_id = null
          } else {
            _tmpReply_to_id = _stmt.getLong(_columnIndexOfReplyToId)
          }
          val _tmpAck_key: ByteArray?
          if (_stmt.isNull(_columnIndexOfAckKey)) {
            _tmpAck_key = null
          } else {
            _tmpAck_key = _stmt.getBlob(_columnIndexOfAckKey)
          }
          val _tmpRtt_ms: Long?
          if (_stmt.isNull(_columnIndexOfRttMs)) {
            _tmpRtt_ms = null
          } else {
            _tmpRtt_ms = _stmt.getLong(_columnIndexOfRttMs)
          }
          _result = MessageRow(_tmpId,_tmpConversation_id,_tmpIdentity_id,_tmpSender_contact_id,_tmpSender_name,_tmpBody,_tmpKind,_tmpSent_at,_tmpOut,_tmpState,_tmpSnr,_tmpRssi,_tmpHops,_tmpRegion,_tmpPacket_tag,_tmpReply_to_id,_tmpAck_key,_tmpRtt_ms)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateState(
    id: Long,
    state: String,
    rtt: Long?,
  ) {
    val _sql: String = "UPDATE messages SET state = ?, rtt_ms = COALESCE(?, rtt_ms) WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, state)
        _argIndex = 2
        if (rtt == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, rtt)
        }
        _argIndex = 3
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun delete(id: Long) {
    val _sql: String = "DELETE FROM messages WHERE id = ?"
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
