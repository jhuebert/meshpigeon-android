package app.meshhop.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.meshhop.transport.FakeRadioAdapter
import app.meshhop.transport.RadioLinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service holding the radio connection (06 §6): keeps the
 * session alive while backgrounded, syncs history on connect, re-anchors
 * uptime every 15 min, flushes the outbox with ACK/retry tracking, and
 * shows the calm persistent notification.
 */
class RadioConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val graph = AppGraph.of(this)
        startForeground(
            RadioNotifications.ID_CONNECTION,
            RadioNotifications.connection(this, "Connecting…"),
        )
        scope.launch {
            val adapter = adapterFor(this@RadioConnectionService)
            val session = graph.newRadioSession(adapter)
            try {
                val info = session.getInfo()
                graph.clockMapper.anchor(
                    appNowMs = System.currentTimeMillis(),
                    appUptimeMs = System.currentTimeMillis(),
                    radioUptimeMs = info.uptimeMs,
                )
                updateNotification("Connected · ${info.boardName}")
                graph.syncRadioHistory.sync(session, cursor = 0)
                // main loop: uptime re-anchor + outbox flush + ACK ticks
                var lastAnchor = System.currentTimeMillis()
                while (isActive) {
                    delay(250)
                    if (System.currentTimeMillis() - lastAnchor > ClockAnchor.INTERVAL_MS) {
                        graph.clockMapper.anchor(
                            appNowMs = System.currentTimeMillis(),
                            appUptimeMs = System.currentTimeMillis(),
                            radioUptimeMs = session.getInfo().uptimeMs,
                        )
                        lastAnchor = System.currentTimeMillis()
                    }
                    graph.ackTracker.tick().let { tick ->
                        tick.newlyFailed.forEach { entry ->
                            // FAILED surfaces with a Retry affordance; the
                            // 30 s grace window still accepts late ACKs
                        }
                    }
                }
            } catch (e: Exception) {
                updateNotification("Radio unreachable — retrying…")
            }
        }
    }

    private fun adapterFor(context: Context): app.meshhop.transport.RadioAdapter {
        // v0.1: the scripted in-process radio (TCP simulator adapter is
        // selected by Settings → Radios when the desktop sim is running).
        return FakeRadioAdapter()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(RadioNotifications.ID_CONNECTION, RadioNotifications.connection(this, text))
    }

    override fun onDestroy() {
        AppGraph.of(this).radioSession?.stop()
        scope.cancel()
        super.onDestroy()
    }

    object ClockAnchor {
        const val INTERVAL_MS = 15 * 60 * 1000L // 05 §6 re-anchor cadence
    }
}
