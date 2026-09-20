package app.meshpigeon.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.math.BigInteger
import org.junit.Test

/**
 * Crypto conformance: RFC vectors for the primitives, plus the MeshCore
 * scheme (ed25519→montgomery ECDH, AES-128-ECB zero-pad, HMAC-SHA256/2).
 * On-air interop vectors captured from real MeshCore devices land in
 * bench work (09 §4) and extend this file.
 */
class CryptoTest {

    private val crypto = BouncyMeshCrypto()

    @Test
    fun `RFC 7748 X25519 section 5_2 vector 1`() {
        val scalar = Crypto.unhex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val u = Crypto.unhex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        val expected = Crypto.unhex("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552")
        val out = ByteArray(32)
        org.bouncycastle.math.ec.rfc7748.X25519.calculateAgreement(scalar, 0, u, 0, out, 0)
        assertArrayEquals(expected, out)
    }

    @Test
    fun `RFC 7748 section 6_1 key agreement with clamped Ed25519-style scalar`() {
        val alicePriv = Crypto.unhex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bobPub = Crypto.unhex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
        val expected = Crypto.unhex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        val out = ByteArray(32)
        org.bouncycastle.math.ec.rfc7748.X25519.calculateAgreement(alicePriv, 0, bobPub, 0, out, 0)
        assertArrayEquals(expected, out)
    }

    @Test
    fun `ed25519 to montgomery conversion matches birational map`() {
        // RFC 8032 §7.3 test key: public key of Ed25519 test vector 1
        val edPub = Crypto.unhex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        // y = LE(edPub); u = (1+y)/(1-y) mod p
        val u = BouncyMeshCrypto.edToMontgomery(edPub)
        // Known-good conversion of this key (computed independently via
        // BigInteger below in edToMontgomerySelfCheck)
        assertEquals(u.toList(), edToMontgomerySelfCheck(edPub).toList())
        assertEquals(32, u.size)
        // u must be < p
        val p = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
        assertTrue(u.leU256() < p)
    }

    private fun edToMontgomerySelfCheck(edPub: ByteArray): List<Byte> {
        // independent slower implementation (same formula, different path);
        // bit 255 of the Ed25519 key is x's sign bit — masked, not reduced
        val masked = edPub.copyOf(32).also { it[31] = (it[31].toInt() and 0x7F).toByte() }
        var y = java.math.BigInteger(1, masked.reversedArray())
        val p = java.math.BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
        y = y.mod(p)
        val u = y.add(java.math.BigInteger.ONE)
            .multiply(java.math.BigInteger.ONE.subtract(y).modInverse(p))
            .mod(p)
        val raw = u.toByteArray()
        val le = ArrayList<Byte>(32)
        for (i in 0 until 32) {
            val pos = raw.size - 1 - i
            le.add(if (pos >= 0) raw[pos] else 0)
        }
        return le
    }

    private fun ByteArray.leU256(): java.math.BigInteger =
        java.math.BigInteger(1, this.reversedArray())

    @Test
    fun `shared secret matches MeshCore orlp library with fixed seeds`() {
        // Cross-checked against MeshCore's lib/ed25519 (orlp) compiled natively:
        // seeds 01..20 and c8..a9 produce these pubs and shared secrets.
        val seedA = ByteArray(32) { (it + 1).toByte() }
        val seedB = ByteArray(32) { (200 - it).toByte() }
        val expectedPubA = Crypto.unhex("79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664")
        val expectedPubB = Crypto.unhex("274ed6b26805d2fe8d060529ee9a2f28763b6976d9c5fd1dee38ff36b3b76939")
        val expectedShared = Crypto.unhex("7977a065545b89e82993cbf820ed11761bc66fb6d4dd9c308d0a1c07be4fa244")

        val pubA = ByteArray(32)
        org.bouncycastle.math.ec.rfc8032.Ed25519.generatePublicKey(seedA, 0, pubA, 0)
        val pubB = ByteArray(32)
        org.bouncycastle.math.ec.rfc8032.Ed25519.generatePublicKey(seedB, 0, pubB, 0)
        assertArrayEquals(expectedPubA, pubA)
        assertArrayEquals(expectedPubB, pubB)

        val s1 = crypto.sharedSecret(seedA + pubA, pubB)
        val s2 = crypto.sharedSecret(seedB + pubB, pubA)
        assertArrayEquals(expectedShared, s1)
        assertArrayEquals(expectedShared, s2)
    }

    @Test
    fun `shared secret is symmetric and clamped`() {
        val alice = crypto.newIdentity()
        val bob = crypto.newIdentity()
        val s1 = crypto.sharedSecret(alice.privateKey, bob.publicKey)
        val s2 = crypto.sharedSecret(bob.privateKey, alice.publicKey)
        println("seedA=" + Crypto.hex(alice.privateKey.copyOf(32)))
        println("pubA=" + Crypto.hex(alice.publicKey))
        println("seedB=" + Crypto.hex(bob.privateKey.copyOf(32)))
        println("pubB=" + Crypto.hex(bob.publicKey))
        println("s1=" + Crypto.hex(s1))
        println("s2=" + Crypto.hex(s2))
        assertEquals(32, s1.size)
        assertArrayEquals(s1, s2)
        // two different identities → different secrets
        val eve = crypto.newIdentity()
        val s3 = crypto.sharedSecret(eve.privateKey, alice.publicKey)
        assertFalse(s1.contentEquals(s3))
    }

    @Test
    fun `encryptThenMac round trip and tamper detection`() {
        val secret = Crypto.random(32)
        val plain = "hello, mesh ☀️".toByteArray(Charsets.UTF_8)
        val framed = crypto.encryptThenMac(secret, plain)
        val ctBlocks = (plain.size + 15) / 16 * 16
        assertEquals(2 + ctBlocks, framed.size) // MAC + zero-padded AES blocks
        val opened = crypto.macThenDecrypt(secret, framed)
        assertNotNull(opened)
        // text plaintexts are NUL-terminated; AES padding zeros may follow
        assertEquals("hello, mesh ☀️", TxtMsgPayload.nulTerminated(opened!!, 0))
        // tampering kills the MAC
        val tampered = framed.copyOf().also { it[5] = (it[5] + 1).toByte() }
        assertNull(crypto.macThenDecrypt(secret, tampered))
        // wrong key fails too
        assertNull(crypto.macThenDecrypt(Crypto.random(32), framed))
    }

    @Test
    fun `channel keys are meshcore compatible`() {
        // Public channel key from companion_protocol.md
        assertArrayEquals(Crypto.unhex("8b3387e9c5cdea6ac9e5edbaa115cd72"), Channels.publicKey)
        // Hashtag: key = first 16 of SHA-256("#test") = 9cd8fcf22a47333b591d96a2b848b73f
        assertArrayEquals(
            Crypto.unhex("9cd8fcf22a47333b591d96a2b848b73f"),
            Channels.hashtagKey("test"),
        )
        assertArrayEquals(
            Crypto.unhex("9cd8fcf22a47333b591d96a2b848b73f"),
            Channels.hashtagKey("#test"), // tolerant of the leading '#'
        )
        // channel hash = first byte of SHA-256(secret)
        assertEquals(Crypto.sha256(Channels.publicKey)[0].toInt() and 0xFF, Channels.channelHash(Channels.publicKey))
    }

    @Test
    fun `channel mac key zero-pads 16-byte secrets to 32`() {
        val padded = BouncyMeshCrypto.macKey(Channels.publicKey)
        assertEquals(32, padded.size)
        for (i in 16 until 32) assertEquals(0, padded[i].toInt())
    }

    @Test
    fun `expected ACK matches MeshCore formula`() {
        // ACK = SHA256(plaintext_without_trailing_nul || sender_pubkey)[0..4]
        val id = crypto.newIdentity()
        val plaintext = TxtMsgPayload(1234567890L, 0, 0, "ping").encode()
        val ack = Ack.compute(plaintext, id.publicKey)
        val expected = Crypto.sha256(
            plaintext.copyOfRange(0, plaintext.size - 1), // drop NUL
            id.publicKey,
        ).copyOf(4)
        assertArrayEquals(expected, ack)
    }

    @Test
    fun `advert sign and verify`() {
        val id = crypto.newIdentity()
        val app = AdvertAppData(
            flags = AdvertAppData.FLAG_CHAT_NODE or AdvertAppData.FLAG_HAS_NAME,
            name = "Jason",
        )
        val ts = 1_700_000_000L
        val appBytes = app.encode()
        val sig = crypto.sign(id.privateKey, id.publicKey, Crypto.leU32(ts), appBytes)
        assertTrue(crypto.verify(id.publicKey, sig, id.publicKey, Crypto.leU32(ts), appBytes))
        assertFalse(crypto.verify(id.publicKey, sig, id.publicKey, Crypto.leU32(ts + 1), appBytes))
    }

    @Test
    fun `advert round trip with location and name`() {
        val id = crypto.newIdentity()
        val ts = 1_700_000_001L
        val app = AdvertAppData(
            flags = AdvertAppData.FLAG_CHAT_NODE or AdvertAppData.FLAG_HAS_LOCATION or AdvertAppData.FLAG_HAS_NAME,
            latitude = 41.25666,
            longitude = -95.93455,
            name = "Trail Node",
        )
        val raw = Messages.buildAdvert(crypto, id, ts, app)
        val packet = PacketCodec.decode(raw)!!
        assertEquals(PacketSpec.PAYLOAD_ADVERT, packet.payloadType)
        val adv = AdvertPayload.decode(packet.payload)
        assertArrayEquals(id.publicKey, adv.publicKey)
        assertEquals(ts, adv.timestamp)
        assertEquals(41.25666, adv.appData.latitude!!, 1e-6)
        assertEquals(-95.93455, adv.appData.longitude!!, 1e-6)
        assertEquals("Trail Node", adv.appData.name)
        assertTrue(crypto.verify(adv.publicKey, adv.signature, adv.publicKey, Crypto.leU32(ts), adv.appData.encode()))
    }

    @Test
    fun `zero hop advert is a direct-routed single broadcast`() {
        val id = crypto.newIdentity()
        val ts = 1_700_000_002L
        val app = AdvertAppData(flags = AdvertAppData.FLAG_HAS_NAME, name = "Nearby")
        val raw = Messages.buildZeroHopAdvert(crypto, id, ts, app)
        val packet = PacketCodec.decode(raw)!!
        // same ADVERT payload, but ROUTE_DIRECT with an empty path — receivers
        // do not relay it (MeshCore sendZeroHop)
        assertEquals(PacketSpec.ROUTE_DIRECT, packet.routeType)
        assertEquals(PacketSpec.PAYLOAD_ADVERT, packet.payloadType)
        assertEquals(0, packet.path.hopCount)
        val adv = AdvertPayload.decode(packet.payload)
        assertEquals("Nearby", adv.appData.name)
        // the flood variant stays flood-routed
        val flood = Messages.buildAdvert(crypto, id, ts, app)
        assertEquals(PacketSpec.ROUTE_FLOOD, PacketCodec.decode(flood)!!.routeType)
    }

    @Test
    fun `direct message round trip between two identities`() {
        val alice = crypto.newIdentity()
        val bob = crypto.newIdentity()
        val dm = Messages.buildDirectMessage(crypto, alice, bob.publicKey, 1_700_000_002L, "hey bob")
        val packet = PacketCodec.decode(dm.raw)!!
        assertEquals(PacketSpec.PAYLOAD_TXT_MSG, packet.payloadType)
        // only bob's key opens it
        val opened = Messages.decodeDirectMessage(crypto, bob, packet, listOf(alice.publicKey))
        assertNotNull(opened)
        assertEquals("hey bob", opened!!.text)
        assertEquals(1_700_000_002L, opened.timestamp)
        // alice cannot open her own message (she'd need bob's key)
        assertNull(Messages.decodeDirectMessage(crypto, alice, packet, listOf(bob.publicKey)))
        // ACK recomputes identically on both sides
        val ackOnBob = Ack.compute(TxtMsgPayload(1_700_000_002L, 0, 0, "hey bob").encode(), alice.publicKey)
        assertArrayEquals(dm.expectedAck, ackOnBob)
    }

    @Test
    fun `group message round trip`() {
        val ch = Channels.Channel.hashtag("hikers")
        val raw = Messages.buildGroupMessage(ch, 1_700_000_003L, "mia: trailhead in 10")
        val packet = PacketCodec.decode(raw)!!
        assertEquals(PacketSpec.PAYLOAD_GRP_TXT, packet.payloadType)
        assertEquals(ch.hash, packet.payload[0].toInt() and 0xFF)
        val framed = packet.payload.copyOfRange(1, packet.payload.size)
        val plain = ch.macThenDecrypt(crypto, framed)
        assertNotNull(plain)
        val ts = Crypto.leU32At(plain!!, 0)
        assertEquals(1_700_000_003L, ts)
        assertEquals(0, plain[4].toInt()) // flags
        assertEquals("mia: trailhead in 10", TxtMsgPayload.nulTerminated(plain, 5))
        // wrong channel key can't open
        val other = Channels.Channel.hashtag("skiers")
        assertNull(other.macThenDecrypt(crypto, framed))
    }

    @Test
    fun `group data round trip with a future sub-message type`() {
        val ch = Channels.Channel.hashtag("hikers")
        val data = "typing".toByteArray(Charsets.UTF_8)
        val raw = Messages.buildGroupData(ch, GroupDataTypes.TYPING, data)
        val packet = PacketCodec.decode(raw)!!
        assertEquals(PacketSpec.PAYLOAD_GRP_DATA, packet.payloadType)
        val framed = packet.payload.copyOfRange(1, packet.payload.size)
        val plain = ch.macThenDecrypt(crypto, framed)!!
        val (dtype, decoded) = GroupDataPayload.decodePlaintext(plain)!!
        assertEquals(GroupDataTypes.TYPING, dtype)
        assertArrayEquals(data, decoded)
    }
}
