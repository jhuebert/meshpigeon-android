package app.meshpigeon.domain

import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.Crypto
import kotlinx.coroutines.flow.first

/**
 * Channel creation & join (07 §6): a private channel gets a random 16-byte
 * key (or a typed one, pasted tolerantly by the UI), a "shared" channel
 * derives its key from the name (hashtag), and an imported channel (QR/link)
 * reuses the key it arrived with. Idempotent per identity: joining a channel
 * whose key we already hold reopens its conversation instead of duplicating
 * the channel row.
 */
class CreateChannel(
    private val channels: ChannelRepository,
    private val conversations: ConversationRepository,
) {
    /** Private channel; `secretHex` blank → generate a random key. Null on a blank name/bad key. */
    suspend fun createPrivate(identityId: Long, name: String, secretHex: String?): Pair<Long, Long>? {
        val secret = if (secretHex.isNullOrBlank()) {
            Channels.privateKey()
        } else {
            parseHex16(secretHex) ?: return null
        }
        return join(identityId, name.trim(), secret)
    }

    /** Shared ("hashtag") channel: the name alone determines the key. Null on a blank name. */
    suspend fun createShared(identityId: Long, name: String): Pair<Long, Long>? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        return join(identityId, clean, Channels.hashtagKey(clean))
    }

    /** Join by (name, secret) — e.g. decoded from a QR/link. Null on a blank name. */
    suspend fun join(identityId: Long, name: String, secret: ByteArray): Pair<Long, Long>? {
        val clean = name.trim()
        if (clean.isEmpty() || secret.size != 16) return null
        return upsert(identityId, clean, secret)
    }

    private suspend fun upsert(identityId: Long, name: String, secret: ByteArray): Pair<Long, Long> {
        val hex = Crypto.hex(secret)
        val existing = channels.observe(identityId).first().firstOrNull {
            Crypto.hex(it.keyEnc) == hex
        }
        val channelId = if (existing != null) {
            existing.id
        } else {
            channels.upsert(
                Channel(
                    id = 0,
                    identityId = identityId,
                    name = name,
                    keyEnc = secret,
                    // name-derived key → HASHTAG; anything else is private
                    kind = if (Channels.hashtagKey(name).contentEquals(secret)) ChannelKind.HASHTAG else ChannelKind.PRIVATE,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        val conversationId = conversations.ensure(identityId, ConversationKind.GROUP, channelId)
        return channelId to conversationId
    }

    private fun parseHex16(raw: String): ByteArray? {
        val hex = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (hex.length != 32) return null
        return runCatching { Crypto.unhex(hex) }.getOrNull()
    }
}
