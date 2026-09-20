package app.meshpigeon.domain

import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.MeshCrypto
import kotlinx.coroutines.flow.first

/**
 * Profile creation (06 §3): a new identity keypair plus its seeded Public
 * channel + conversation, activated on creation. Used by onboarding and by
 * the drawer's identity switcher ("add identity").
 */
class CreateIdentity(
    private val identities: IdentityRepository,
    private val channels: ChannelRepository,
    private val conversations: ConversationRepository,
    private val crypto: MeshCrypto,
) {
    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        val pair = crypto.newIdentity()
        val id = identities.upsert(
            Identity(
                id = 0,
                name = name.ifBlank { "Pigeon" },
                publicKey = pair.publicKey,
                // sealed at the repository boundary (Android Keystore, M2
                // security pass); the column keeps its _enc name for schema stability
                privateKeyEnc = pair.privateKey,
                flags = 0,
                createdAt = now,
                isActive = true,
                advertPolicy = AdvertPolicy.MANUAL,
            ),
        )
        identities.setActive(id)
        val channelId = channels.upsert(
            Channel(
                id = 0,
                identityId = id,
                name = Channels.PUBLIC_NAME,
                keyEnc = Channels.Channel.public().secret,
                kind = ChannelKind.PUBLIC,
                createdAt = now,
            ),
        )
        conversations.ensure(id, ConversationKind.PUBLIC, channelId)
        return id
    }
}
