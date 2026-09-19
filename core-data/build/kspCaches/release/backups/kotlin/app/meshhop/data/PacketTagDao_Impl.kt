package app.meshhop.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PacketTagDao_Impl(
  __db: RoomDatabase,
) : PacketTagDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPacketTagRow: EntityInsertAdapter<PacketTagRow>
  init {
    this.__db = __db
    this.__insertAdapterOfPacketTagRow = object : EntityInsertAdapter<PacketTagRow>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `packet_tags` (`tag`,`seen_at`) VALUES (?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PacketTagRow) {
        statement.bindBlob(1, entity.tag)
        statement.bindLong(2, entity.seen_at)
      }
    }
  }

  public override suspend fun insert(row: PacketTagRow): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPacketTagRow.insert(_connection, row)
  }

  public override suspend fun prune(before: Long) {
    val _sql: String = "DELETE FROM packet_tags WHERE seen_at < ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, before)
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
