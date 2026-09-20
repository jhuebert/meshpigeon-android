package app.meshpigeon.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share-code roundtrips and MeshCore QR import (07 §11): our TLV links both
 * ways, `meshpigeon://` URIs, raw TLV paste, and read-only MeshCore URIs.
 */
class ShareCodecTest {
    private val secret = Crypto.unhex("00112233445566778899aabbccddeeff")
    private val pub = Crypto.random(32)

    @Test
    fun `channel roundtrip through the link form`() {
        val link = ShareCodec.encodeChannel("Trail Talk", secret)
        assertTrue(link.startsWith("https://meshpigeon.app/c/"))
        val decoded = ShareCodec.decode(link) as ShareCodec.Decoded.Channel
        assertEquals("Trail Talk", decoded.name)
        assertTrue(secret.contentEquals(decoded.secret))
    }

    @Test
    fun `contact roundtrip through the link form`() {
        val link = ShareCodec.encodeContact("Fern", pub)
        assertTrue(link.startsWith("https://meshpigeon.app/u/"))
        val decoded = ShareCodec.decode(link) as ShareCodec.Decoded.Contact
        assertEquals("Fern", decoded.name)
        assertTrue(pub.contentEquals(decoded.publicKey))
    }

    @Test
    fun `meshpigeon scheme URI decodes`() {
        val link = ShareCodec.encodeChannel("Public", Channels.publicKey)
        val uri = link.replace("https://meshpigeon.app/c/", "meshpigeon://c/")
        val decoded = ShareCodec.decode(uri) as ShareCodec.Decoded.Channel
        assertEquals("Public", decoded.name)
        assertTrue(Channels.publicKey.contentEquals(decoded.secret))
    }

    @Test
    fun `raw tlv base64 paste decodes`() {
        val link = ShareCodec.encodeChannel("Chatter", secret)
        val payload = link.removePrefix(ShareCodec.LINK_CHANNEL_PREFIX)
        val decoded = ShareCodec.decode(payload) as ShareCodec.Decoded.Channel
        assertEquals("Chatter", decoded.name)
        assertTrue(secret.contentEquals(decoded.secret))
    }

    @Test
    fun `meshcore channel QR imports read-only`() {
        val decoded = ShareCodec.decode(
            "meshcore://channel/add?name=Public&secret=8b3387e9c5cdea6ac9e5edbaa115cd72",
        ) as ShareCodec.Decoded.Channel
        assertEquals("Public", decoded.name)
        assertTrue(Channels.publicKey.contentEquals(decoded.secret))
    }

    @Test
    fun `meshcore contact QR imports with urlencoded name and type`() {
        val decoded = ShareCodec.decode(
            "meshcore://contact/add?name=Example+Contact&" +
                "public_key=9cd8fcf22a47333b591d96a2b848b73f457b1bb1a3ea2453a885f9e5787765b1&type=1",
        ) as ShareCodec.Decoded.Contact
        assertEquals("Example Contact", decoded.name)
        assertEquals(1, decoded.meshCoreType)
        assertEquals(
            "9cd8fcf22a47333b591d96a2b848b73f457b1bb1a3ea2453a885f9e5787765b1",
            Crypto.hex(decoded.publicKey),
        )
    }

    @Test
    fun `hex key paste is tolerant of separators and case`() {
        val decoded = ShareCodec.decode(
            "meshcore://channel/add?name=Tidy&secret=8B 33-87 E9:C5 CD EA 6A C9 E5 ED BA A1 15 CD 72",
        ) as ShareCodec.Decoded.Channel
        assertTrue(Channels.publicKey.contentEquals(decoded.secret))
    }

    @Test
    fun `unknown tlv tags are skipped and later known tags still land`() {
        // hand-build: magic+ver, unknown tag 0x7F "junk", then a channel record
        val body = byteArrayOf(
            0x7F, 0x04, 'j'.code.toByte(), 'u'.code.toByte(), 'n'.code.toByte(), 'k'.code.toByte(),
        ) + tlv(0x01, "K".encodeToByteArray()) + tlv(0x02, secret)
        val bytes = "MPGS".encodeToByteArray() + byteArrayOf(0x01) + body
        val payload = Base64Urls.encode(bytes)
        val decoded = ShareCodec.decode(payload) as ShareCodec.Decoded.Channel
        assertEquals("K", decoded.name)
        assertTrue(secret.contentEquals(decoded.secret))
    }

    @Test
    fun `junk never decodes`() {
        assertNull(ShareCodec.decode(""))
        assertNull(ShareCodec.decode("hello there"))
        assertNull(ShareCodec.decode("meshcore://unknown/thing?x=1"))
        assertNull(ShareCodec.decode("meshcore://channel/add?name=X")) // no secret
        assertNull(ShareCodec.decode("meshcore://channel/add?name=X&secret=0011")) // short key
        // wrong magic
        val bad = Base64Urls.encode(byteArrayOf(1, 2, 3, 4, 1, 1, 2, 0x01, 0x02))
        assertNull(ShareCodec.decode(bad))
        // future version
        val future = "MPGS".encodeToByteArray() + byteArrayOf(0x02) + tlv(0x02, secret)
        assertNull(ShareCodec.decode(Base64Urls.encode(future)))
        // truncated record
        val truncated = "MPGS".encodeToByteArray() + byteArrayOf(0x01) + byteArrayOf(0x02, 0x10, 0x01)
        assertNull(ShareCodec.decode(Base64Urls.encode(truncated)))
    }

    private fun tlv(tag: Int, value: ByteArray): ByteArray =
        byteArrayOf(tag.toByte(), value.size.toByte()) + value
}

/** java.util.Base64 wrapper so tests read cleanly. */
private object Base64Urls {
    fun encode(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
