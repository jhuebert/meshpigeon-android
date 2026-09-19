package app.meshhop.domain

import app.meshhop.protocol.Crypto
import app.meshhop.transport.PacketEntry
import app.meshhop.transport.RadioSession
import kotlinx.coroutines.flow.first

/**
 * History catch-up (06 §4, 05 §6): on connect, `FETCH_PACKETS(since_seq)`
 * replays everything the radio retained while nobody was listening — with a
 * resumable cursor so interrupted syncs don't re-pull (05 §6).
 */
class SyncRadioHistory(
    private val identities: IdentityRepository,
    private val tagCache: PacketTagCache,
    private val receivePipeline: ReceivePipeline,
) {
    data class SyncResult(
        val fetched: Int,
        val newPackets: Int,
        val oldestSeq: Long,
        val droppedByRadio: Long,
        val historyDepth: Long,
    )

    /**
     * Pull the radio's retained history since `cursor` (stored per radio
     * target) and feed each entry through the receive pipeline.
     */
    suspend fun sync(
        session: RadioSession,
        cursor: Long,
        onProgress: ((SyncProgress) -> Unit)? = null,
    ): SyncResult {
        val info = session.getInfo()
        check(info.protoVersion == 1) { "unsupported radio protocol version ${info.protoVersion}" }

        var since = maxOf(cursor, info.oldestSeq)
        var fetched = 0
        var fresh = 0
        val seenTags = HashSet<ByteArray>()

        while (true) {
            val batch = session.fetchPackets(since, BATCH, onProgress?.let { cb ->
                { entry: PacketEntry -> cb(SyncProgress(entry.seq, fetched)) }
            } ?: { _ -> })
            if (batch == 0) break
            fetched += batch
            since = maxOf(since, info.oldestSeq) // will be raised by caller-stored cursor
            break
        }

        // Entry-level cursor loop (fetchPackets is bounded by maxCount;
        // repeat until it stops delivering).
        var lastSeq = since
        var total = 0
        while (true) {
            val batchEntries = ArrayList<PacketEntry>(BATCH)
            val delivered = session.fetchPackets(lastSeq, BATCH) { entry ->
                batchEntries.add(entry)
            }
            if (delivered == 0) break
            total += delivered
            for (entry in batchEntries) {
                lastSeq = maxOf(lastSeq, entry.seq)
                val tag = app.meshhop.protocol.PacketCodec.packetTag(entry.raw)
                if (seenTags.add(tag) && tagCache.remember(tag)) {
                    fresh++
                    receivePipeline.onPacket(
                        raw = entry.raw,
                        rssi = entry.rssi,
                        snr = entry.snr,
                        hops = null, // hop info derives on-air, not in the store
                    )
                }
            }
        }

        return SyncResult(
            fetched = fetched + total,
            newPackets = fresh,
            oldestSeq = info.oldestSeq,
            droppedByRadio = info.storeDropped,
            historyDepth = info.storeCount,
        )
    }

    companion object {
        const val BATCH = 32
    }
}

/** Progress reporting for the sync UI (banner shows depth on connect). */
data class SyncProgress(val seq: Long, val index: Int)

/**
 * App-side dedup cache (06 §2 `packet_tags`): remember(packetTag) returns
 * false for already-seen tags. Backed by the repository in production; the
 * in-memory default suits tests and the receive fast path.
 */
class PacketTagCache(private val maxEntries: Int = 4096) {
    // hex keys: ByteArray identity equality would defeat dedup
    private val seen = object : LinkedHashMap<String, Unit>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun remember(tag: ByteArray): Boolean = seen.put(Crypto.hex(tag), Unit) == null

    @Synchronized
    fun clear() = seen.clear()
}
