package app.meshhop.android

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.meshhop.data.MeshHopDatabase
import app.meshhop.data.RoomChannelRepository
import app.meshhop.data.RoomContactRepository
import app.meshhop.data.RoomConversationRepository
import app.meshhop.data.RoomIdentityRepository
import app.meshhop.data.RoomMessageRepository
import app.meshhop.data.RoomOutboxRepository
import app.meshhop.data.RoomRadioTargetRepository
import app.meshhop.domain.InMemoryPathCache
import app.meshhop.domain.ReceivePipeline
import app.meshhop.domain.SendMessage
import app.meshhop.domain.SyncRadioHistory
import app.meshhop.protocol.AckTracker
import app.meshhop.protocol.BouncyMeshCrypto
import app.meshhop.protocol.ClockMapper
import app.meshhop.protocol.MeshCrypto
import app.meshhop.transport.FakeRadioAdapter
import app.meshhop.transport.RadioAdapter
import app.meshhop.transport.RadioSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph (M0). Hilt lands when the feature surface grows
 * past what one graph can hold (plan 02 §app; noted as a pragmatic
 * deviation for a smaller reviewable foundation).
 */
class MeshHopApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

class AppGraph(context: Context) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val crypto: MeshCrypto = BouncyMeshCrypto()

    val db: MeshHopDatabase = Room.databaseBuilder(context, MeshHopDatabase::class.java, MeshHopDatabase.NAME).build()

    val identities = RoomIdentityRepository(db)
    val contacts = RoomContactRepository(db)
    val channels = RoomChannelRepository(db)
    val conversations = RoomConversationRepository(db)
    val messages = RoomMessageRepository(db)
    val outbox = RoomOutboxRepository(db)
    val radioTargets = RoomRadioTargetRepository(db)

    val clockMapper = ClockMapper()
    val ackTracker = AckTracker({ System.currentTimeMillis() })
    val pathCache = InMemoryPathCache { System.currentTimeMillis() }
    val tagCache = app.meshhop.domain.PacketTagCache()

    val receivePipeline = ReceivePipeline(
        identities, contacts, channels, conversations, messages,
        tagCache, ackTracker, pathCache, clockMapper, crypto,
        object : ReceivePipeline.Notifier {
            override suspend fun notify(notification: ReceivePipeline.Notification) {
                RadioNotifications.post(context, notification)
            }
        },
    )

    val syncRadioHistory = SyncRadioHistory(identities, tagCache, receivePipeline)

    val sendMessage = SendMessage(
        identities, contacts, conversations, messages, outbox,
        ackTracker, pathCache, crypto,
        airtimeEstimator = object : app.meshhop.domain.AirtimeEstimator {
            override fun estimate(packetLen: Int): Double =
                app.meshhop.protocol.Airtime.estimateMs(packetLen, sf = 9, bandwidthKhz = 125.0, codingRateDenominator = 5)
        },
        wallClockSec = { System.currentTimeMillis() / 1000 },
        wallClockMs = { System.currentTimeMillis() },
    )

    val connectToRadio = app.meshhop.domain.ConnectToRadio(radioTargets)
    val settingsGuard = app.meshhop.domain.RadioSettingsGuard()

    /**
     * The connected radio session. v0.1 ships the TCP/sim adapter so the
     * whole pipeline is exercisable on the bench without BLE hardware;
     * :transport-android's BLE/USB adapters slot in behind the same SPI.
     */
    @Volatile
    var radioSession: RadioSession? = null

    fun newRadioSession(adapter: RadioAdapter): RadioSession =
        RadioSession(adapter, appScope).also { session ->
            radioSession = session
            session.start()
        }

    companion object {
        fun of(context: Context): AppGraph = (context.applicationContext as MeshHopApp).graph
    }
}
