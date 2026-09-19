package app.meshhop.protocol

/**
 * Radio-uptime ↔ wall-clock mapping (03 §5). The radio only knows
 * `uptime_ms`; the app anchors it to real time on connect and re-anchors
 * every 15 min while connected. Mapping is monotonic — never rewinds
 * message timestamps (05 §6).
 *
 * Wrap handling: radio uptime is a 32-bit counter (~49.7 days). Re-anchors
 * close in time are assumed to be on the same wrap side; a jump that lands
 * "before" the last wall time is treated as monotonic clamp (drift
 * correction), not rewind.
 */
class ClockMapper {
    data class Anchor(
        val appNowMs: Long, // wall clock (UTC) at anchor time
        val appUptimeMs: Long, // app-side monotonic ms at anchor time
        val radioUptimeMs: Long, // radio's uptime reading at anchor time
    )

    @Volatile
    private var anchor: Anchor? = null

    /**
     * Anchor on connect (or re-anchor). `radioUptimeMs` comes from
     * `GET_INFO`; `radioPacketUptime`-derived times then map to:
     * `packet_time = app_now - (app_uptime_anchor - radio_packet_uptime)`
     * adjusted for how much radio time elapsed since the anchor.
     */
    fun anchor(appNowMs: Long, appUptimeMs: Long, radioUptimeMs: Long) {
        anchor = Anchor(appNowMs, appUptimeMs, radioUptimeMs)
    }

    fun anchored(): Boolean = anchor != null

    /**
     * Map a radio packet's stored `uptime_ms` to wall clock. The radio
     * counter may have wrapped since the anchor; `radioWrapsSince` (the
     * radio's boot/wrap count delta, from GET_INFO boot_count) lets us add
     * full counter periods.
     */
    fun toWallClock(radioPacketUptimeMs: Long, wrapsSinceAnchor: Long = 0): Long? {
        val a = anchor ?: return null
        val radioElapsed = radioPacketUptimeMs - a.radioUptimeMs + wrapsSinceAnchor * WRAP_PERIOD_MS
        val appNow = a.appNowMs + (currentUptimeFn() - a.appUptimeMs)
        val t = appNow - radioElapsed
        return t
    }

    /** Injected monotonic uptime source for production/tests. */
    var currentUptimeFn: () -> Long = { System.nanoTime() / 1_000_000 }

    fun clear() {
        anchor = null
    }

    companion object {
        const val WRAP_PERIOD_MS = 1L shl 32 // 49.7 days
        const val REANCHOR_INTERVAL_MS = 15 * 60 * 1000L
    }
}
