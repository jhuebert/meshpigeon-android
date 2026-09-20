package app.meshpigeon.protocol

/**
 * Delivery-state machine for outgoing messages (03 §4 + meshcore-open §2.1
 * adoptions). The radio is dumb — ALL retry logic lives here.
 *
 * - Physics-based timeout estimate computed from SF/BW/CR airtime and hops.
 * - Exponential backoff 1s × 2^n, bounded retries.
 * - 30 s grace window after "failed": a late ACK still confirms.
 * - Duplicate-ACK suppression (a rebroadcast ACK must not double-confirm).
 *
 * State transitions the UI renders (07 §4):
 *   ⏳ queued → ✓ sent → ✓ heard → ✓✓ confirmed (failed shows Retry)
 */
enum class DeliveryState { QUEUED, SENT, HEARD, CONFIRMED, FAILED }

/**
 * Estimated on-air time (ms) for a packet of `packetLen` bytes at the given
 * LoRa parameters — the standard LoRa airtime formula (preamble + payload),
 * good enough for timeout estimation (exactness is not required; the cap
 * bounds us).
 */
object Airtime {
    fun estimateMs(
        packetLen: Int,
        sf: Int,
        bandwidthKhz: Double,
        codingRateDenominator: Int,
        preambleSymbols: Int = 8,
    ): Double {
        val tSym = (1L shl sf) * 1000.0 / (bandwidthKhz * 1000.0)
        // low-data-rate optimization kicks in for slow settings
        val ldro = (sf >= 11) || (sf == 10 && bandwidthKhz <= 125.0)
        val de = if (ldro) 1 else 0
        val crcLen = 4
        val numerator = 8.0 * packetLen - 4.0 * sf + 28 + 16 * crcLen - 20 * de
        val symbols = Math.ceil(numerator / (4.0 * (sf - 2 * de))).toLong() *
            (codingRateDenominator - 4).toLong()
        val payloadSymbNb = 8 + maxOf(0L, symbols)
        val preambleMs = (preambleSymbols + 4.25) * tSym
        return preambleMs + payloadSymbNb * tSym
    }
}

/**
 * Timeout budget for one send attempt, computed per meshcore-open's matured
 * answers but WITHOUT ML prediction (rejected in 11 §4):
 * - direct: `500 + (airtime × 6 + 250) × (hops + 1)` ms
 * - flood:  `500 + 16 × airtime` ms
 * capped at 45 s.
 */
object Timeout {
    const val CAP_MS = 45_000L

    fun estimateMs(airtimeMs: Double, hops: Int, direct: Boolean): Long {
        val t = if (direct) {
            500 + (airtimeMs * 6 + 250) * (hops + 1)
        } else {
            500 + 16 * airtimeMs
        }
        return minOf(CAP_MS, t.toLong().coerceAtLeast(500))
    }
}

/**
 * Tracks every outgoing message and drives its state machine in virtual or
 * real time. The caller feeds `onAcked`/`onSent`/`tick` events; `due`
 * reports messages needing a retransmit. Pure Kotlin, fully testable.
 */
class AckTracker(
    private val clock: () -> Long, // monotonic ms
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
) {
    data class Entry(
        val ackKey: ByteArray, // expected ACK checksum (4 bytes)
        val conversationId: Long,
        val airtimeMs: Double,
        val hops: Int,
        val direct: Boolean,
        val state: DeliveryState,
        val attempts: Int,
        val nextRetryAt: Long,
        val failedAt: Long? = null, // grace window start
    )

    // keyed by hex (ByteArray identity != content equality)
    private val entries = LinkedHashMap<String, Entry>(16)

    fun track(
        ackKey: ByteArray,
        conversationId: Long,
        airtimeMs: Double,
        hops: Int,
        direct: Boolean,
    ): Entry {
        val e = Entry(
            ackKey,
            conversationId,
            airtimeMs,
            hops,
            direct,
            DeliveryState.SENT,
            attempts = 1,
            nextRetryAt = clock() + Timeout.estimateMs(airtimeMs, hops, direct),
        )
        entries[hex(ackKey)] = e
        return e
    }

    /**
     * An ACK arrived for `ackKey`. Returns true the FIRST time this key
     * confirms a tracked message (duplicate-ACK suppression); false when
     * unknown (not ours) or already confirmed (rebroadcast).
     */
    fun onAcked(ackKey: ByteArray): Boolean {
        val entry = entries[hex(ackKey)] ?: return false
        if (entry.state == DeliveryState.CONFIRMED) return false
        entries[hex(ackKey)] = entry.copy(state = DeliveryState.CONFIRMED)
        return true
    }

    /**
     * Advance the state machine. Returns messages whose attempt timed out
     * (caller retransmits, bumping the attempt) or that exhausted retries
     * (state FAILED; grace window keeps them confirmable).
     */
    fun tick(): TickResult {
        val now = clock()
        val due = ArrayList<Entry>(2)
        val newlyFailed = ArrayList<Entry>(2)
        for ((key, e) in entries) {
            when {
                e.state == DeliveryState.CONFIRMED -> {}
                e.failedAt != null -> {} // in grace window
                now >= e.nextRetryAt -> {
                    if (e.attempts >= maxRetries) {
                        val failed = e.copy(state = DeliveryState.FAILED, failedAt = now)
                        entries[key] = failed
                        newlyFailed.add(failed)
                    } else {
                        val backoff = BACKOFF_BASE_MS shl e.attempts.coerceAtMost(5)
                        val retried = e.copy(
                            attempts = e.attempts + 1,
                            nextRetryAt = now + backoff,
                        )
                        entries[key] = retried
                        due.add(retried)
                    }
                }
            }
        }
        return TickResult(due, newlyFailed)
    }

    /** A late ACK after failure still confirms, inside the grace window. */
    fun onLateAck(ackKey: ByteArray): Boolean {
        val e = entries[hex(ackKey)] ?: return false
        if (e.state != DeliveryState.FAILED) return false
        val failedAt = e.failedAt ?: return false
        if (clock() - failedAt <= GRACE_WINDOW_MS) {
            entries[hex(ackKey)] = e.copy(state = DeliveryState.CONFIRMED)
            return true
        }
        return false
    }

    fun get(ackKey: ByteArray): Entry? = entries[hex(ackKey)]
    fun all(): Collection<Entry> = entries.values

    private fun hex(b: ByteArray) = Crypto.hex(b)

    data class TickResult(val dueForRetry: List<Entry>, val newlyFailed: List<Entry>)

    companion object {
        const val DEFAULT_MAX_RETRIES = 5
        const val BACKOFF_BASE_MS = 1_000L // 1s × 2^n
        const val GRACE_WINDOW_MS = 30_000L

        /**
         * Character budget for a message body (07 §4), derived from the
         * MeshCore payload math: AES-framed plaintext must fit the 184-byte
         * payload → plaintext ceiling 176; TXT plaintext = ts(4) + meta(1)
         * + text(+NUL). Group messages carry a "name: " prefix inside the
         * same plaintext. (The routing path lives in the packet header, not
         * the payload, so it doesn't affect the text budget.)
         *
         * COMPOSE cap matches MeshCore exactly: MAX_TEXT_LEN =
         * 10*CIPHER_BLOCK_SIZE = 160 bytes of text, shared with the
         * "name: " prefix for group messages (BaseChatMesh::sendGroupMessage).
         * MeshCore clients can DISPLAY up to 184-byte frames, but nothing
         * they run can ever COMPOSE more — so MeshPigeon must not either.
         */
        fun textBudget(isGroup: Boolean, senderPrefixLen: Int = 0): Int {
            return (160 - senderPrefixLen).coerceAtLeast(0)
        }
    }
}
