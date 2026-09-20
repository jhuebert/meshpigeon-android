package app.meshpigeon.domain

import app.meshpigeon.protocol.AdvertAppData
import app.meshpigeon.protocol.IdentityKeyPair
import app.meshpigeon.protocol.Messages
import app.meshpigeon.protocol.MeshCrypto
import kotlinx.coroutines.flow.first

/**
 * "Share my contact" / "Say hi nearby" (03 §3, 07 §5): build the on-air
 * advert packet for the active identity. The caller transmits it over the
 * connected radio session — the domain never touches the transport.
 */
class SendAdvert(
    private val identities: IdentityRepository,
    private val crypto: MeshCrypto,
    private val wallClockSec: () -> Long,
) {
    /**
     * @param zeroHop true = "Say hi nearby" (single broadcast, not relayed);
     *                false = flood "Share my contact" — the mesh carries it.
     * @return the raw packet bytes, or null when there is no active identity.
     */
    suspend fun build(zeroHop: Boolean): ByteArray? {
        val identity = identities.active().first() ?: return null
        val appData = AdvertAppData(
            flags = identity.flags or AdvertAppData.FLAG_HAS_NAME,
            name = identity.name,
        )
        val keypair = IdentityKeyPair(identity.publicKey, identity.privateKeyEnc)
        val timestamp = wallClockSec().coerceAtMost(Int.MAX_VALUE.toLong())
        return if (zeroHop) {
            Messages.buildZeroHopAdvert(crypto, keypair, timestamp, appData)
        } else {
            Messages.buildAdvert(crypto, keypair, timestamp, appData)
        }
    }
}
