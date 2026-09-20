package app.meshpigeon.protocol

/**
 * Shareable "cards" for the composer plus-menu (07 §4): a contact card or a
 * channel-join link dropped into any chat as an ordinary text message.
 *
 * Interop rule (03 §6 / 07 §6, session-11 audit): rich content rides as
 * readable text — never unregistered GRP_DATA — so official MeshCore clients
 * simply see `<emoji> <name> <link>`, while MeshPigeon detects the link and
 * renders a rich card with a one-tap save/join action.
 */
object ShareCards {
    const val CONTACT_LABEL = "📇"
    const val CHANNEL_LABEL = "📻"

    /**
     * One-line card: `<emoji> <name> <link>`. Null when the composed text
     * would not fit the protocol budget (the label and link share it with
     * the group sender prefix).
     */
    fun contactCard(name: String, publicKey: ByteArray, budget: Int): String? =
        fit("$CONTACT_LABEL $name ${ShareCodec.encodeContact(name, publicKey)}", budget)

    fun channelCard(name: String, secret: ByteArray, budget: Int): String? =
        fit("$CHANNEL_LABEL $name ${ShareCodec.encodeChannel(name, secret)}", budget)

    /**
     * Detect a card in a message body: any message carrying one of our share
     * links (with optional label text around it) decodes to its object.
     * Ordinary text — even valid base64 — yields null unless it carries the
     * TLV magic, so only real cards render as cards.
     */
    fun detect(body: String): ShareCodec.Decoded? {
        val prefix = listOf(
            ShareCodec.LINK_CHANNEL_PREFIX,
            ShareCodec.LINK_CONTACT_PREFIX,
        ).firstOrNull { body.contains(it) } ?: return null
        val link = prefix + body
            .substringAfter(prefix)
            .lineSequence()
            .first()
            .substringBefore(' ')
        return ShareCodec.decode(link)
    }

    private fun fit(text: String, budget: Int): String? =
        text.takeIf { it.toByteArray(Charsets.UTF_8).size <= budget }
}
