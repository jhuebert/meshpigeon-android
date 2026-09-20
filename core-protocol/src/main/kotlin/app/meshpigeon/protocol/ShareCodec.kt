package app.meshpigeon.protocol

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Base64

/**
 * Share codes for channels and contacts (07 §11 "QR & links everywhere"):
 *
 * **Emit** — our own versioned TLV, base64url-encoded inside a short link:
 * `https://meshpigeon.app/c/<payload>` (channel join) and
 * `https://meshpigeon.app/u/<payload>` (contact card). The TLV payload is
 * `["MPGS":4][ver:1]` followed by `[tag:1][len:1][value]` records — unknown
 * tags are skipped on decode so future fields stay forward-compatible.
 *
 * **Import (read-only)** — MeshCore's documented QR URI formats
 * (MeshCore `docs/qr_codes.md`) also decode here, so a MeshCore channel or
 * contact code can be joined without ever emitting one:
 *
 * - `meshcore://channel/add?name=<urlencoded>&secret=<32 hex>`
 * - `meshcore://contact/add?name=<urlencoded>&public_key=<64 hex>&type=<n>`
 *
 * Key paste is tolerant (07 §6): spaces/dashes in hex keys are stripped and
 * case is ignored.
 */
object ShareCodec {
    private val MAGIC = "MPGS".encodeToByteArray()
    private const val VERSION = 1

    private const val TAG_CHANNEL_NAME = 0x01
    private const val TAG_CHANNEL_SECRET = 0x02
    private const val TAG_CONTACT_NAME = 0x03
    private const val TAG_CONTACT_PUBLIC_KEY = 0x04

    const val LINK_CHANNEL_PREFIX = "https://meshpigeon.app/c/"
    const val LINK_CONTACT_PREFIX = "https://meshpigeon.app/u/"
    private const val URI_CHANNEL_PREFIX = "meshpigeon://c/"
    private const val URI_CONTACT_PREFIX = "meshpigeon://u/"
    private const val MESHCORE_SCHEME = "meshcore://"

    /** One decoded share object, ready to preview before committing (07 §6). */
    sealed class Decoded {
        /** A channel join: name plus its 16-byte secret. */
        data class Channel(val name: String, val secret: ByteArray) : Decoded()

        /** A contact card: display name plus the 32-byte Ed25519 public key. */
        data class Contact(
            val name: String,
            val publicKey: ByteArray,
            /** MeshCore contact `type` on import (1 companion, 2 repeater, …). */
            val meshCoreType: Int? = null,
        ) : Decoded()
    }

    fun encodeChannel(name: String, secret: ByteArray): String =
        LINK_CHANNEL_PREFIX + encodeTlv(
            listOf(TAG_CHANNEL_NAME to name.encodeToByteArray(), TAG_CHANNEL_SECRET to secret),
        )

    fun encodeContact(name: String, publicKey: ByteArray): String =
        LINK_CONTACT_PREFIX + encodeTlv(
            listOf(TAG_CONTACT_NAME to name.encodeToByteArray(), TAG_CONTACT_PUBLIC_KEY to publicKey),
        )

    private fun encodeTlv(records: List<Pair<Int, ByteArray>>): String {
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION)
        for ((tag, value) in records) {
            out.write(tag)
            out.write(value.size)
            out.write(value)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /**
     * Decode anything the user can paste: our links, `meshpigeon://` URIs,
     * raw TLV base64, or a MeshCore QR URI. Returns null for junk.
     */
    fun decode(text: String): Decoded? {
        val s = text.trim()
        if (s.startsWith(MESHCORE_SCHEME)) return decodeMeshCore(s)
        val payload = when {
            s.startsWith(LINK_CHANNEL_PREFIX) -> s.removePrefix(LINK_CHANNEL_PREFIX)
            s.startsWith(LINK_CONTACT_PREFIX) -> s.removePrefix(LINK_CONTACT_PREFIX)
            s.startsWith(URI_CHANNEL_PREFIX) -> s.removePrefix(URI_CHANNEL_PREFIX)
            s.startsWith(URI_CONTACT_PREFIX) -> s.removePrefix(URI_CONTACT_PREFIX)
            else -> s // raw TLV base64
        }
        val bytes = runCatching { Base64.getUrlDecoder().decode(payload) }.getOrNull() ?: return null
        return decodeTlv(bytes)
    }

    private fun decodeTlv(bytes: ByteArray): Decoded? {
        if (bytes.size < 5) return null
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC)) return null
        if (bytes[4].toInt() != VERSION) return null // future versions: not decodable here
        var name: String? = null
        var secret: ByteArray? = null
        var publicKey: ByteArray? = null
        var meshCoreType: Int? = null
        var i = 5
        while (i + 2 <= bytes.size) {
            val tag = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (i + 2 + len > bytes.size) return null
            val value = bytes.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
            when (tag) {
                TAG_CHANNEL_NAME, TAG_CONTACT_NAME -> name = String(value, Charsets.UTF_8)
                TAG_CHANNEL_SECRET -> secret = value
                TAG_CONTACT_PUBLIC_KEY -> publicKey = value
                else -> Unit // unknown tag — skip (forward compatible)
            }
        }
        return when {
            secret != null && secret.size == 16 ->
                Decoded.Channel(name ?: "", secret)
            publicKey != null && publicKey.size == 32 ->
                Decoded.Contact(name ?: "", publicKey, meshCoreType)
            else -> null
        }
    }

    /** MeshCore QR URIs, read-only import (never emitted by MeshPigeon). */
    private fun decodeMeshCore(uri: String): Decoded? {
        val path = uri.removePrefix(MESHCORE_SCHEME).substringBefore('?')
        val params = parseQuery(uri.substringAfter('?', ""))
        return when (path) {
            "channel/add" -> params["secret"]?.let { secret ->
                parseHex(secret, 16)?.let { Decoded.Channel(params["name"] ?: "", it) }
            }
            "contact/add" -> params["public_key"]?.let { key ->
                parseHex(key, 32)?.let {
                    Decoded.Contact(params["name"] ?: "", it, params["type"]?.toIntOrNull())
                }
            }
            else -> null
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key.isBlank()) return@mapNotNull null
            runCatching { URLDecoder.decode(value, Charsets.UTF_8) }
                .map { key to it }
                .getOrNull()
        }.toMap()
    }

    /** Tolerant hex (07 §6): separators stripped, case ignored, exact size required. */
    private fun parseHex(raw: String, size: Int): ByteArray? {
        val hex = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (hex.length != size * 2) return null
        return runCatching { Crypto.unhex(hex) }.getOrNull()
    }
}
