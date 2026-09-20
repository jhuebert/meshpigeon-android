package app.meshpigeon.domain

import app.meshpigeon.protocol.AckTracker
import app.meshpigeon.protocol.Crypto
import app.meshpigeon.protocol.DeliveryState
import kotlinx.coroutines.flow.first

/**
 * Outbox flush loop (11 §2.2): per-conversation FIFO — one packet in
 * flight, the rest queued. The [AckTracker] owns retry timing and the
 * receive pipeline confirms via ACK packets; this class moves rows between
 * the outbox and the air and finalizes the message state. Pure Kotlin —
 * the service calls [resume] on (re)connect and [tick] from its 250 ms loop.
 */
class FlushOutbox(
    private val outbox: OutboxRepository,
    private val messages: MessageRepository,
    private val ackTracker: AckTracker,
    private val clock: () -> Long,
) {
    /** One packet to put on the air now. */
    data class Transmission(val entry: OutboxEntry, val isRetry: Boolean)

    // ackKey hex → claimed entry (ByteArray identity ≠ content equality)
    private val inFlight = LinkedHashMap<String, OutboxEntry>()

    /**
     * Recovery after a restart: rows orphaned in-flight (claimed but the
     * process died before an ACK) go back to the queue.
     */
    suspend fun resume() {
        for (entry in outbox.due(Long.MAX_VALUE).first()) {
            if (entry.state == DeliveryState.SENT) {
                outbox.update(entry.copy(state = DeliveryState.QUEUED, nextRetryAt = 0))
            }
        }
    }

    /** Advance the state machine; returns the packets to transmit now. */
    suspend fun tick(): List<Transmission> {
        val tx = ArrayList<Transmission>(2)
        val result = ackTracker.tick()
        for (failed in result.newlyFailed) {
            finalize(failed.ackKey, DeliveryState.FAILED) // row dropped; Retry affordance re-enqueues (M2)
        }
        for (retry in result.dueForRetry) {
            inFlight[hex(retry.ackKey)]?.let { tx.add(Transmission(it, isRetry = true)) }
        }
        // fresh sends: at most one in flight per conversation
        val busy = inFlight.values.map { it.conversationId }.toHashSet()
        val queued = outbox.due(clock()).first().filter { it.state == DeliveryState.QUEUED }
        for (conversationId in queued.map { it.conversationId }.distinct()) {
            if (conversationId in busy) continue
            outbox.markInFlight(conversationId)?.let { entry ->
                if (entry.ackKey != null) {
                    inFlight[hex(entry.ackKey)] = entry
                    ackTracker.track(
                        entry.ackKey, conversationId,
                        entry.airtimeMs, entry.hops, entry.direct,
                    )
                } else {
                    // no ACKs (group/reactions): fire once, never retransmitted
                    // (03 §6) — drop the row so it cannot be claimed again
                    outbox.remove(entry.id)
                }
                messages.updateState(entry.messageId, DeliveryState.SENT)
                tx.add(Transmission(entry, isRetry = false))
            }
        }
        return tx
    }

    /**
     * An ACK confirmed `ackKey` (the receive pipeline already flipped the
     * message row through the tracker) — drop the outbox row.
     */
    suspend fun onConfirmed(ackKey: ByteArray) = finalize(ackKey, DeliveryState.CONFIRMED)

    private suspend fun finalize(ackKey: ByteArray, state: DeliveryState) {
        val entry = inFlight.remove(hex(ackKey)) ?: return
        outbox.remove(entry.id)
        messages.updateState(entry.messageId, state)
    }

    private fun hex(b: ByteArray) = Crypto.hex(b)
}
