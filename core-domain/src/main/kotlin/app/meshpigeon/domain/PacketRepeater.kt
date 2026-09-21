package app.meshpigeon.domain

import app.meshpigeon.protocol.AdvertPayload
import app.meshpigeon.protocol.Crypto
import app.meshpigeon.protocol.MeshCrypto
import app.meshpigeon.protocol.PacketCodec
import app.meshpigeon.protocol.PacketSpec
import app.meshpigeon.protocol.RawPacket

/**
 * The in-app repeater (03 §4): repeating is a pure app feature — the app
 * dedups incoming packets by tag and re-sends eligible ones via
 * `SEND_PACKET`, wire bytes untouched (transport codes preserved). The
 * firmware has no repeat logic at all (04 §1.3); the caller supplies the
 * transmit side, so this class stays transport-free.
 *
 * Eligibility mirrors MeshCore's `routeRecvPacket` semantics so the two
 * repeater populations behave identically on air:
 *  - only flood-routed packets are repeated (direct-routed packets travel
 *    hop-by-hop along their own path and are none of our business);
 *  - flood TXT_MSGs addressed to us are NOT repeated — they arrived, and
 *    the receive pipeline ACKs them (MeshCore marks them
 *    do-not-retransmit); zero-hop adverts are direct-routed, so they
 *    never repeat either;
 *  - adverts are repeated only after their signature verifies (MeshCore
 *    drops forged adverts without forwarding them);
 *  - everything else decodable and flood-routed (GRP_TXT, GRP_DATA, ACK)
 *    repeats — group traffic is re-flooded by everyone on the channel,
 *    and ACK propagation is what turns ✓ heard into ✓✓ confirmed.
 *
 * Dedup uses its OWN tag cache — deliberately separate from the receive
 * pipeline's (a packet we decoded for display must still be repeated).
 * Outgoing transmissions are pre-armed via [observeOutgoing] so the radio's
 * self-echo of our own TX never gets repeated back onto the air.
 *
 * Caller contract: invoke [onPacket] for every live packet (never for
 * history replay — the store replay predates us and the mesh has moved on).
 * Bytes are re-sent verbatim, so MeshPigeon and MeshCore repeaters dedup
 * identical copies by the same raw-packet fingerprints.
 */
class PacketRepeater(
    private val crypto: MeshCrypto,
    /** First byte of the active identity's public key, or null if none. */
    private val myHash: suspend () -> Int?,
    private val tagCache: PacketTagCache = PacketTagCache(),
) {
    /** Set by the owner; packets judged eligible are handed here. */
    var transmit: (suspend (ByteArray) -> Unit)? = null

    /** On/off for the session (v1 has no UI toggle yet — on while connected). */
    @Volatile
    var enabled: Boolean = true

    /**
     * Arm the dedup cache with a packet WE are about to transmit, so the
     * radio's TX loopback echo is never repeated. Call for every local
     * send (outbox flush, ACK fast path, adverts).
     */
    fun observeOutgoing(raw: ByteArray) {
        tagCache.remember(PacketCodec.packetTag(raw))
    }

    /** Judge one received packet; eligible ones go to [transmit]. */
    suspend fun onPacket(raw: ByteArray) {
        if (!enabled) return
        if (!repeats(raw)) return
        if (!tagCache.remember(PacketCodec.packetTag(raw))) return
        transmit?.invoke(raw)
    }

    private suspend fun repeats(raw: ByteArray): Boolean {
        val packet = PacketCodec.decode(raw) ?: return false // corrupt — drop (09 §1)
        if (!packet.isFlood) return false
        return when (packet.payloadType) {
            PacketSpec.PAYLOAD_GRP_TXT, PacketSpec.PAYLOAD_GRP_DATA -> true
            PacketSpec.PAYLOAD_ACK -> true
            PacketSpec.PAYLOAD_ADVERT -> advertIsGenuine(packet)
            PacketSpec.PAYLOAD_TXT_MSG -> {
                val dest = packet.payload.getOrNull(0)?.toInt()?.and(0xFF) ?: return false
                dest != (myHash() ?: -1)
            }
            else -> false // REQ/RESPONSE/PATH/TRACE/RAW_CUSTOM are not ours to relay
        }
    }

    /** Forged adverts are dropped, never forwarded (MeshCore parity). */
    private suspend fun advertIsGenuine(packet: RawPacket): Boolean {
        val adv = try {
            AdvertPayload.decode(packet.payload)
        } catch (_: Exception) {
            return false
        }
        if (adv.publicKey[0].toInt().and(0xFF) == (myHash() ?: -1)) return false // self advert echo
        return crypto.verify(
            adv.publicKey, adv.signature,
            adv.publicKey, Crypto.leU32(adv.timestamp), adv.appData.encode(),
        )
    }
}
