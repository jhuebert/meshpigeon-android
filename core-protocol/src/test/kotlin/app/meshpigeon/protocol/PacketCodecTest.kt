package app.meshpigeon.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet codec golden tests. Vector rule (09 §1, 11 §2.3): hop count
 * derives from `path_bytes ÷ hash_size` — the path_length byte is never
 * treated as a byte count.
 */
class PacketCodecTest {

    private fun pathOf(hashSize: PathHashSize, hops: Int): Path =
        Path(hashSize, List(hops) { h -> ByteArray(hashSize.bytes) { b -> (h + b).toByte() } })

    @Test
    fun `round trip - flood TXT_MSG with no path`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val raw = PacketCodec.encode(PacketSpec.ROUTE_FLOOD, PacketSpec.PAYLOAD_TXT_MSG, pathOf(PathHashSize.THREE, 0), payload)
        val decoded = PacketCodec.decode(raw)
        assertNotNull(decoded)
        assertEquals(PacketSpec.ROUTE_FLOOD, decoded!!.routeType)
        assertEquals(PacketSpec.PAYLOAD_TXT_MSG, decoded.payloadType)
        assertEquals(0, decoded.path.hopCount)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun `round trip - direct with 3-byte hashes`() {
        val path = pathOf(PathHashSize.THREE, 4)
        val payload = ByteArray(100) { it.toByte() }
        val raw = PacketCodec.encode(PacketSpec.ROUTE_DIRECT, PacketSpec.PAYLOAD_TXT_MSG, path, payload)
        val decoded = PacketCodec.decode(raw)
        assertNotNull(decoded)
        assertEquals(4, decoded!!.path.hopCount)
        assertEquals(PathHashSize.THREE, decoded.path.hashSize)
        assertEquals(4 * 3, decoded.path.hashes.sumOf { it.size })
        for (h in 0 until 4) {
            assertArrayEquals(path.hashes[h], decoded.path.hashes[h])
        }
        // path_length byte: 3-byte hashes => code 2 => top bits 10
        assertEquals(0b10 shl 6 or 4, (raw[1].toInt() and 0xFF)) // no transport codes
        assertTrue(decoded.routeType == PacketSpec.ROUTE_DIRECT)
    }

    @Test
    fun `golden - path_length byte encodes hash size and hops, not byte count`() {
        // From MeshCore packet_format.md: 0x45 = 5 hops, 2-byte hashes => 10 path bytes
        val path = pathOf(PathHashSize.TWO, 5)
        val raw = PacketCodec.encode(PacketSpec.ROUTE_DIRECT, PacketSpec.PAYLOAD_GRP_TXT, path, byteArrayOf(1))
        assertEquals(0x45, raw[1].toInt() and 0xFF)
        val decoded = PacketCodec.decode(raw)!!
        assertEquals(5, decoded.path.hopCount)
        assertEquals(10, decoded.path.hashes.sumOf { it.size })
    }

    @Test
    fun `golden - 0x8A = 10 hops with 3-byte hashes`() {
        val path = pathOf(PathHashSize.THREE, 10)
        val raw = PacketCodec.encode(PacketSpec.ROUTE_DIRECT, PacketSpec.PAYLOAD_TXT_MSG, path, ByteArray(0))
        assertEquals(0x8A, raw[1].toInt() and 0xFF)
        val decoded = PacketCodec.decode(raw)!!
        assertEquals(10, decoded.path.hopCount)
        assertEquals(30, decoded.path.hashes.sumOf { it.size })
    }

    @Test
    fun `round trip - transport codes preserved on decode`() {
        val packet = RawPacket(
            PacketSpec.header(PacketSpec.ROUTE_TRANSPORT_FLOOD, PacketSpec.PAYLOAD_GRP_DATA),
            intArrayOf(0x1234, 0x5678),
            pathOf(PathHashSize.THREE, 2),
            byteArrayOf(9, 9, 9),
        )
        val out = ByteArray(PacketSpec.MAX_PACKET)
        val n = PacketCodec.encodeInto(packet, out)
        val decoded = PacketCodec.decode(out.copyOf(n))
        assertNotNull(decoded)
        assertTrue(decoded!!.hasTransportCodes)
        assertEquals(0x1234, decoded.transportCodes!![0])
        assertEquals(0x5678, decoded.transportCodes!![1])
    }

    @Test
    fun `header bit packing matches MeshCore layout`() {
        // 0bVVPPPPRR
        val h = PacketSpec.header(PacketSpec.ROUTE_TRANSPORT_DIRECT, PacketSpec.PAYLOAD_ACK, PacketSpec.PAYLOAD_VERSION_1)
        assertEquals(0b00_0011_11 or 0, h) // ACK=0x03 -> PPPP=0011, route=11
        assertEquals(PacketSpec.ROUTE_TRANSPORT_DIRECT, PacketSpec.routeType(h))
        assertEquals(PacketSpec.PAYLOAD_ACK, PacketSpec.payloadType(h))
        val h2 = PacketSpec.header(PacketSpec.ROUTE_FLOOD, PacketSpec.PAYLOAD_ADVERT)
        assertEquals(0b00_0100_01, h2)
    }

    @Test
    fun `corrupt packets are dropped, never crash`() {
        assertNull(PacketCodec.decode(ByteArray(0)))
        // unknown payload version
        val badVersion = byteArrayOf(0b01_0000_01.toByte(), 0, 1)
        assertNull(PacketCodec.decode(badVersion))
        // truncated path
        val trunc = byteArrayOf(0b00_0010_01.toByte(), 0x0A) // claims 10 hops*1B, none present
        assertNull(PacketCodec.decode(trunc))
        // oversized payload claim (payload > 184 present)
        val big = byteArrayOf(0b00_0010_01.toByte(), 0x00) + ByteArray(200) { 1 }
        assertNull(PacketCodec.decode(big))
        // hash-size code 3 is reserved
        val reserved = byteArrayOf(0b00_0010_01.toByte(), 0b11000001.toByte())
        assertNull(PacketCodec.decode(reserved))
    }

    @Test
    fun `packet tag is stable across decode-encode cycles`() {
        val path = pathOf(PathHashSize.THREE, 1)
        val raw = PacketCodec.encode(PacketSpec.ROUTE_FLOOD, PacketSpec.PAYLOAD_GRP_TXT, path, byteArrayOf(7, 7))
        val tag1 = PacketCodec.packetTag(raw)
        val decoded = PacketCodec.decode(raw)!!
        val reencoded = PacketCodec.encode(decoded)
        assertArrayEquals(tag1, PacketCodec.packetTag(reencoded))
    }

    @Test
    fun `flood route with full 63-hop path at 1-byte hashes`() {
        // 63 hops is the 6-bit ceiling; 1-byte hashes → 63 path bytes
        val path = pathOf(PathHashSize.ONE, 63)
        val raw = PacketCodec.encode(PacketSpec.ROUTE_FLOOD, PacketSpec.PAYLOAD_TRACE, path, byteArrayOf(1))
        val decoded = PacketCodec.decode(raw)!!
        assertEquals(63, decoded.path.hopCount)
        assertEquals(63, decoded.path.hashes.size)
        assertEquals(0, decoded.transportCodes?.size ?: 0)
    }

    @Test
    fun `txt msg payload round trip`() {
        val p = TxtMsgPayload(1_700_000_000L, PacketSpec.TXT_TYPE_PLAIN, 2, "héllo mesh ☀")
        val decoded = TxtMsgPayload.decode(p.encode())
        assertEquals(1_700_000_000L, decoded.timestamp)
        assertEquals(PacketSpec.TXT_TYPE_PLAIN, decoded.txtType)
        assertEquals(2, decoded.attempt)
        assertEquals("héllo mesh ☀", decoded.text)
    }
}
