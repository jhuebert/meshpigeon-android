package app.meshhop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ACK/retry state machine over virtual time (09 §1): send, duplicate ACK,
 * ACK-after-retry, grace window, expiry → failed.
 */
class AckTrackerTest {

    private class FakeClock {
        var now = 1_000L
        fun time(): Long = now
        fun advance(ms: Long) {
            now += ms
        }
    }

    private val clock = FakeClock()

    private fun tracker(maxRetries: Int = AckTracker.DEFAULT_MAX_RETRIES) =
        AckTracker({ clock.time() }, maxRetries)

    private val ackKey = byteArrayOf(0x11, 0x22, 0x33, 0x44)

    @Test
    fun `ack confirms tracked message`() {
        val t = tracker()
        t.track(ackKey, conversationId = 1, airtimeMs = 100.0, hops = 0, direct = false)
        assertTrue(t.onAcked(ackKey))
        assertEquals(DeliveryState.CONFIRMED, t.get(ackKey)!!.state)
    }

    @Test
    fun `duplicate ack does not double-confirm`() {
        val t = tracker()
        t.track(ackKey, 1, 100.0, 0, false)
        assertTrue(t.onAcked(ackKey))
        assertFalse(t.onAcked(ackKey)) // rebroadcast — suppressed
    }

    @Test
    fun `unknown ack is ignored`() {
        val t = tracker()
        assertFalse(t.onAcked(byteArrayOf(9, 9, 9, 9)))
    }

    @Test
    fun `timeout estimate is physics based`() {
        // airtime 100ms, flood → 500 + 16*100 = 2100ms
        assertEquals(2100L, Timeout.estimateMs(100.0, hops = 0, direct = false))
        // direct with 2 hops → 500 + (600+250)*3 = 3050ms
        assertEquals(3050L, Timeout.estimateMs(100.0, hops = 2, direct = true))
        // cap at 45s
        assertEquals(Timeout.CAP_MS, Timeout.estimateMs(10_000.0, hops = 10, direct = true))
        // minimum 500ms
        assertEquals(500L, Timeout.estimateMs(0.0, hops = 0, direct = false))
    }

    @Test
    fun `retry uses exponential backoff then fails after max retries`() {
        val t = tracker(maxRetries = 3)
        t.track(ackKey, 1, airtimeMs = 100.0, hops = 0, direct = false)
        // attempt 1: timeout = 2100ms
        clock.advance(2100)
        val r1 = t.tick()
        assertEquals(1, r1.dueForRetry.size)
        assertEquals(2, t.get(ackKey)!!.attempts)
        // backoff after attempt 1 = 1s × 2^1 = 2s
        clock.advance(1999)
        assertTrue(t.tick().dueForRetry.isEmpty())
        clock.advance(1)
        val r2 = t.tick()
        assertEquals(1, r2.dueForRetry.size)
        assertEquals(3, t.get(ackKey)!!.attempts)
        // backoff 1s × 2^2 = 4s → then exhausted → FAILED
        clock.advance(3999)
        assertTrue(t.tick().dueForRetry.isEmpty())
        clock.advance(1)
        val r3 = t.tick()
        assertEquals(1, r3.newlyFailed.size)
        assertEquals(DeliveryState.FAILED, t.get(ackKey)!!.state)
    }

    @Test
    fun `late ack within grace window still confirms`() {
        val t = tracker(maxRetries = 0)
        t.track(ackKey, 1, 100.0, 0, false)
        clock.advance(2100)
        val r = t.tick()
        assertEquals(0, r.dueForRetry.size) // maxRetries=0 → straight to failed
        assertEquals(1, r.newlyFailed.size)
        // ACK arrives 10s later — within the 30s window
        clock.advance(10_000)
        assertTrue(t.onLateAck(ackKey))
        assertEquals(DeliveryState.CONFIRMED, t.get(ackKey)!!.state)
    }

    @Test
    fun `late ack outside grace window stays failed`() {
        val t = tracker(maxRetries = 0)
        t.track(ackKey, 1, 100.0, 0, false)
        clock.advance(2100)
        t.tick()
        clock.advance(31_000)
        assertFalse(t.onLateAck(ackKey))
        assertEquals(DeliveryState.FAILED, t.get(ackKey)!!.state)
    }

    @Test
    fun `ack after failed-then-confirmed is suppressed`() {
        val t = tracker(maxRetries = 0)
        t.track(ackKey, 1, 100.0, 0, false)
        clock.advance(2100)
        t.tick()
        assertTrue(t.onLateAck(ackKey))
        assertFalse(t.onAcked(ackKey)) // already confirmed
    }

    @Test
    fun `text budget fits MeshCore payload limits`() {
        // plaintext ceiling is 176 bytes (AES-framed within the 184-byte
        // payload); plaintext = ts(4) + meta(1) + [text+NUL] for DMs
        assertEquals(170, AckTracker.textBudget(isGroup = false))
        // group: "name: " prefix rides inside the same plaintext
        assertEquals(170, AckTracker.textBudget(isGroup = true))
        assertEquals(165, AckTracker.textBudget(isGroup = true, senderPrefixLen = 5))
        // budget never negative
        assertEquals(0, AckTracker.textBudget(isGroup = true, senderPrefixLen = 999))
    }
}
