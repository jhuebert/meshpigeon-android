package app.meshpigeon.domain.mesh

import app.meshpigeon.protocol.Crypto
import app.meshpigeon.transport.RadioFrame
import app.meshpigeon.transport.RadioSession
import app.meshpigeon.transport.TcpRadioAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * mesh-sim harness (09-testing §2): bridges N firmware-simulator radios
 * (desktop sims over TCP) into a shared "air" with an RF loss model, so
 * app-level pipelines can be exercised at mesh scale in CI with zero
 * hardware. Each sim is one dumb radio; the harness is the only thing that
 * moves packets between them, exactly like the app-driven repeater path
 * (03 §4): fetch with a since-cursor → decide → SEND_PACKET.
 *
 * Loop safety: a packet is relayed at most once globally — the first store
 * entry seen with a given raw fingerprint (sent or received, on any radio)
 * arms the relay; every later copy (sent echo, receive echo, other radios'
 * copies) is skipped. This is the same first-seen dedup rule the app's
 * packet-tag cache applies on the receive side.
 *
 * Sims are purged on connect so a run is deterministic even against reused
 * instances; do NOT boot the sims with --traffic-ms (synthetic traffic
 * would pollute message-level assertions). Start them with
 * meshpigeon-firmware: `scripts/mesh-sim.sh 3 8801` and pass the ports in.
 */
class MeshSimHarness(
    private val ports: List<Int>,
    private val lossPercent: Int = 0,
    private val dupPercent: Int = 0,
    private val tickMs: Long = 10,
    private val maxPerFetch: Int = 32,
    /**
     * Radio range as an adjacency list (null = everyone hears everyone).
     * Only meaningful together with [air]/`relay = false` — with relaying
     * on, the harness itself plays repeater and reaches every radio.
     */
    private val adjacency: List<Set<Int>>? = null,
    /**
     * When true (default) the harness relays first-seen packets itself —
     * the pre-repeater mesh model used by the fanout/loss/dedup tests.
     * When false the harness is ONLY the RF layer: packets move between
     * radios solely via [air] (called by the test's app-driven repeaters),
     * which is exactly the app-repeater shape (03 §4).
     */
    private val relay: Boolean = true,
) {
    private val adapters = mutableListOf<TcpRadioAdapter>()
    val sessions = mutableListOf<RadioSession>()
    private val cursors = mutableListOf<Long>()

    /** Hex fingerprints of every packet this harness has ever seen "on air". */
    private val seen = HashSet<String>()

    /** Per-radio relay queues: one SEND_PACKET per radio per tick (the sims
     *  key up serially — a second SEND_PACKET while one is in flight is
     *  STATUS_ERR_BUSY, so it goes back to the front of the queue). */
    private val relayQueues = List(ports.size) { ArrayDeque<ByteArray>() }

    private val random = java.util.Random(1234)
    private var loop: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val radioCount: Int get() = sessions.size

    /** Connect one session per sim and wipe its store. */
    suspend fun connect() {
        for (port in ports) {
            val adapter = TcpRadioAdapter("127.0.0.1", port)
            val session = RadioSession(adapter, scope)
            adapter.connectBlocking()
            session.start()
            session.purgeStore()
            adapters += adapter
            sessions += session
            cursors += 0L
        }
    }

    fun start() {
        check(loop == null) { "already started" }
        loop = scope.launch {
            while (true) {
                tick()
                delay(tickMs)
            }
        }
    }

    /** Originate a packet on radio [from] (the sent entry relays from there). */
    suspend fun send(from: Int, raw: ByteArray): Long = sessions[from].sendPacket(raw)

    /**
     * Model one on-air transmission by radio [from]: its neighbors hear it
     * per the RF loss/dup model (queued — sims key up serially). Used by
     * app-driven-repeater tests, where propagation is the repeater's
     * decision, not the harness's.
     */
    fun air(from: Int, raw: ByteArray) {
        val neighbors = adjacency?.getOrNull(from) ?: (sessions.indices - from)
        for (j in neighbors) {
            if (random.nextInt(100) < lossPercent) continue
            relayQueues[j] += raw
            if (random.nextInt(100) < dupPercent) relayQueues[j] += raw
        }
    }

    /** Everything still retained in radio [i]'s store. */
    suspend fun storeEntries(i: Int): List<app.meshpigeon.transport.PacketEntry> {
        val out = mutableListOf<app.meshpigeon.transport.PacketEntry>()
        sessions[i].fetchPackets(0, 1000) { out += it }
        return out
    }

    private suspend fun tick() {
        // relay phase: inject at most one queued packet per radio
        for (i in relayQueues.indices) {
            val q = relayQueues[i]
            if (q.isEmpty()) continue
            val raw = q.removeFirst()
            try {
                sessions[i].sendPacket(raw)
            } catch (e: RadioSession.CommandException.Status) {
                if (e.status == RadioFrame.STATUS_ERR_BUSY) q.addFirst(raw) // keying up; next tick
                // other statuses (e.g. TX_FAILED under the sim's own loss
                // model) are informational only — cross-radio loss is ours
            }
        }

        // fetch phase: pull new store entries and arm relays (harness-as-
        // repeater mode only — with relay=false propagation is the app's job)
        if (!relay) return
        for (i in sessions.indices) {
            var maxSeq = cursors[i]
            sessions[i].fetchPackets(cursors[i], maxPerFetch) { entry ->
                if (entry.seq > maxSeq) maxSeq = entry.seq
                val fingerprint = Crypto.hex(Crypto.sha256(entry.raw))
                if (seen.add(fingerprint)) { // first sighting anywhere
                    for (j in sessions.indices) {
                        if (j == i) continue
                        if (random.nextInt(100) < lossPercent) continue
                        relayQueues[j] += entry.raw
                        if (random.nextInt(100) < dupPercent) relayQueues[j] += entry.raw
                    }
                }
            }
            cursors[i] = maxSeq
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        for (s in sessions) s.stop()
        for (a in adapters) a.close()
        sessions.clear()
        adapters.clear()
        scope.cancel()
    }
}
