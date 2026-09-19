package app.meshpigeon.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uptime→wall-clock mapping (09 §1): anchors, re-anchors, monotonicity. */
class ClockMapperTest {

    @Test
    fun `unanchored mapper returns null`() {
        val m = ClockMapper()
        assertFalse(m.anchored())
        assertNull(m.toWallClock(1234))
    }

    @Test
    fun `packet uptime maps to wall clock`() {
        val m = ClockMapper()
        var appUptime = 10_000L
        m.currentUptimeFn = { appUptime }
        // anchor: app wall 1700000000000, app uptime 10s, radio uptime 5s
        m.anchor(appNowMs = 1_700_000_000_000, appUptimeMs = 10_000, radioUptimeMs = 5_000)
        // a packet stamped 7s of radio uptime arrives when app uptime is 12s:
        // radio elapsed since anchor = 2s; app elapsed since anchor = 2s
        // → packet time = wall_now - 2s
        appUptime = 12_000
        val t = m.toWallClock(7_000)
        assertEquals(1_700_000_000_000 + 12_000 - 2_000 - 10_000, t!!)
    }

    @Test
    fun `re anchor corrects drift without rewinding timestamps`() {
        val m = ClockMapper()
        var appUptime = 0L
        m.currentUptimeFn = { appUptime }
        m.anchor(appNowMs = 1_000, appUptimeMs = 0, radioUptimeMs = 0)
        // radio clock is 5s AHEAD of app clock; packet at radio uptime 6s
        appUptime = 1_000
        val t1 = m.toWallClock(6_000) // radio elapsed 6s, app elapsed 1s → app 1000 - 5000 = -4000
        assertEquals(-4_000L, t1)
        // re-anchor: apps agree the drift is now only 1s
        m.anchor(appNowMs = 2_000, appUptimeMs = 1_000, radioUptimeMs = 2_000)
        val t2 = m.toWallClock(3_000) // radio elapsed 1s → 2000 - 1000 = 1000
        assertEquals(1_000L, t2)
        assertTrue(t2!! > t1!!) // never rewinds
    }

    @Test
    fun `radio wrap is handled with wrap count`() {
        val m = ClockMapper()
        var appUptime = 60_000L
        m.currentUptimeFn = { appUptime }
        m.anchor(appNowMs = 1_000_000, appUptimeMs = 60_000, radioUptimeMs = 0xFFFF_F000)
        // packet stamped just after the wrap: small uptime, 1 wrap since anchor
        // radio elapsed = 0x100 − 0xFFFFF000 + 2^32 = 4096 + 256 = 4352ms
        val t = m.toWallClock(radioPacketUptimeMs = 0x0000_0100, wrapsSinceAnchor = 1)
        assertEquals(1_000_000 - 4_352, t!!)
    }
}

class PathCacheTest {

    private var now = 0L
    private val cache = PathCache({ now })

    @Test
    fun `route used when learned, cleared on exhaustion`() {
        val route = Path(PathHashSize.THREE, listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)))
        cache.learn(0xAB, route)
        assertEquals(route, cache.routeFor(0xAB))
        cache.onAck(0xAB, route)
        assertEquals(1, cache.entry(0xAB)!!.successCount)

        cache.onExhausted(0xAB)
        assertTrue(cache.routeFor(0xAB)!!.hopCount == 0) // cleared → flood next time
        assertTrue(cache.isEmptyPath(0xAB))
        assertEquals(1, cache.entry(0xAB)!!.failCount)
    }

    @Test
    fun `unknown destination floods`() {
        assertNull(cache.routeFor(0x42))
    }
}
