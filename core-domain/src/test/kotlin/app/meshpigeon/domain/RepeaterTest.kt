package app.meshpigeon.domain

import app.meshpigeon.protocol.AdvertAppData
import app.meshpigeon.protocol.BouncyMeshCrypto
import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.IdentityKeyPair
import app.meshpigeon.protocol.Messages
import app.meshpigeon.protocol.PacketCodec
import app.meshpigeon.protocol.PacketSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In-app repeater policy tests (03 §4, 04 §1.3): the app dedups by tag and
 * re-sends eligible flood packets via SEND_PACKET, wire bytes untouched.
 * Mesh-scale behavior is covered by MeshSimTest against the real sims.
 */
class RepeaterTest {

    private val crypto = BouncyMeshCrypto()
    private val keys = crypto.newIdentity()
    private val myHash = (keys.publicKey[0].toInt() and 0xFF)

    private fun repeater(my: Int? = myHash): Pair<PacketRepeater, MutableList<ByteArray>> {
        val sent = mutableListOf<ByteArray>()
        val repeater = PacketRepeater(crypto, myHash = { my })
        repeater.transmit = { sent += it }
        return repeater to sent
    }

    private fun groupText(text: String = "alice: hello mesh") = Messages.buildGroupMessage(
        Channels.Channel.public(), System.currentTimeMillis() / 1000, text,
    )

    @Test
    fun `repeats a flood group text verbatim, exactly once`() = runBlocking {
        val (rep, sent) = repeater()
        val raw = groupText()
        rep.onPacket(raw)
        assertEquals(1, sent.size)
        assertArrayEquals(raw, sent[0])
    }

    @Test
    fun `a second sighting of the same packet is not repeated`() = runBlocking {
        val (rep, sent) = repeater()
        val raw = groupText()
        rep.onPacket(raw)
        rep.onPacket(raw) // mesh echo of the copy we just sent / another repeater's copy
        assertEquals(1, sent.size)
    }

    @Test
    fun `direct-routed packets are never repeated`() = runBlocking {
        val (rep, sent) = repeater()
        // flood group text with the header flipped to ROUTE_DIRECT (zero-hop shape)
        val raw = groupText()
        val direct = raw.copyOf().also { it[0] = ((it[0].toInt() and 0b11111100) or PacketSpec.ROUTE_DIRECT).toByte() }
        rep.onPacket(direct)
        assertEquals(0, sent.size)
        // sanity: the flood original DOES repeat
        rep.onPacket(raw)
        assertEquals(1, sent.size)
    }

    @Test
    fun `flood direct messages addressed to us are not repeated`() = runBlocking {
        val (rep, sent) = repeater(myHash)
        val peer = crypto.newIdentity()
        val dm = Messages.buildDirectMessage(
            crypto, IdentityKeyPair(peer.publicKey, peer.privateKey),
            keys.publicKey, System.currentTimeMillis(), "for you",
        ).raw
        rep.onPacket(dm)
        assertEquals(0, sent.size)
        // same DM addressed to someone else relays
        val other = crypto.newIdentity().publicKey
        val otherDm = Messages.buildDirectMessage(
            crypto, IdentityKeyPair(peer.publicKey, peer.privateKey), other,
            System.currentTimeMillis(), "not for us",
        ).raw
        rep.onPacket(otherDm)
        assertEquals(1, sent.size)
    }

    @Test
    fun `our own transmissions never echo back into a repeat`() = runBlocking {
        val (rep, sent) = repeater()
        val raw = groupText()
        rep.observeOutgoing(raw) // we are about to send this ourselves
        // the radio's TX loopback arrives as a "received" packet
        rep.onPacket(raw)
        assertEquals(0, sent.size)
    }

    @Test
    fun `undecodable garbage never repeats, decodable bytes relay`() = runBlocking {
        val (rep, sent) = repeater()
        val raw = groupText()
        rep.onPacket(byteArrayOf(0x55, 1, 2, 3)) // nonsense header + payload
        assertEquals(0, sent.size)
        // a truncated-but-structurally-valid packet still relays (MeshCore
        // parity — receivers drop it on the MAC); raw bytes are not our call
        rep.onPacket(raw.copyOfRange(0, raw.size / 2))
        assertEquals(1, sent.size)
        rep.onPacket(raw)
        assertEquals(2, sent.size)
    }

    @Test
    fun `forged adverts are dropped, genuine adverts relay`() = runBlocking {
        val (rep, sent) = repeater(myHash)
        val peer = crypto.newIdentity() // adverts from someone else (ours are armed as outgoing)
        val forged = Messages.buildAdvert(
            crypto, peer, System.currentTimeMillis(),
            AdvertAppData(flags = AdvertAppData.FLAG_HAS_NAME, name = "imposter"),
        ).let { bytes ->
            // flip a signature byte AFTER the signature was computed → verify fails
            bytes.copyOf().also { it[40] = (it[40].toInt() xor 0x40).toByte() }
        }
        rep.onPacket(forged)
        assertEquals(0, sent.size)

        val genuine = Messages.buildAdvert(
            crypto, peer, System.currentTimeMillis(),
            AdvertAppData(flags = AdvertAppData.FLAG_HAS_NAME, name = "real deal"),
        )
        rep.onPacket(genuine)
        assertEquals(1, sent.size)
    }

    @Test
    fun `flood ACKs relay so confirmations can propagate`() = runBlocking {
        val (rep, sent) = repeater()
        val ack = Messages.buildAck(byteArrayOf(1, 2, 3, 4))
        assertEquals(PacketSpec.PAYLOAD_ACK, PacketCodec.decode(ack)!!.payloadType)
        rep.onPacket(ack)
        assertEquals(1, sent.size)
        rep.onPacket(ack)
        assertEquals(1, sent.size) // deduped
    }

    @Test
    fun `disabled repeaters pass everything through untouched`() = runBlocking {
        val (rep, sent) = repeater()
        rep.enabled = false
        rep.onPacket(groupText())
        assertEquals(0, sent.size)
    }

    @Test
    fun `packet tag is stable across encode-decode for dedup`() {
        val raw = groupText()
        val decoded = PacketCodec.decode(raw)!!
        assertEquals(
            PacketCodec.packetTag(raw).toList(),
            PacketCodec.packetTag(PacketCodec.encode(decoded)).toList(),
        )
    }

    @Test
    fun `non-repeating payload types are left alone`() = runBlocking {
        val (rep, sent) = repeater()
        // PATH/TRACE/REQ land with later milestones — never relayed by us
        for (type in intArrayOf(PacketSpec.PAYLOAD_REQ, PacketSpec.PAYLOAD_TRACE)) {
            val header = PacketSpec.header(PacketSpec.ROUTE_FLOOD, type)
            rep.onPacket(byteArrayOf(header.toByte(), 0, 1, 2, 3))
        }
        assertEquals(0, sent.size)
    }
}
