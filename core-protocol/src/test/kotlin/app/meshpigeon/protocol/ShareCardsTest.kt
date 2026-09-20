package app.meshpigeon.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Composer plus-menu cards (07 §4): label + share link as plain text,
 * within the protocol budget, detectable back into a decoded object.
 */
class ShareCardsTest {
    private val pubKey = ByteArray(32) { (it * 7).toByte() }
    private val secret = ByteArray(16) { (it + 1).toByte() }

    @Test
    fun `contact card carries the name and link and fits the budget`() {
        val text = ShareCards.contactCard("Bob", pubKey, 160)!!
        assertNotNull(text)
        assertTrue(text.startsWith("📇 Bob https://meshpigeon.app/u/"))
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= 160)
    }

    @Test
    fun `channel card carries the name and link`() {
        val text = ShareCards.channelCard("Trail Talk", secret, 160)!!
        assertNotNull(text)
        assertTrue(text.startsWith("📻 Trail Talk https://meshpigeon.app/c/"))
    }

    @Test
    fun `over-budget card is null, not truncated`() {
        val longName = "A very long display name that surely does not fit"
        assertNull(ShareCards.contactCard(longName, pubKey, 160))
    }

    @Test
    fun `cards detect back into their objects`() {
        val contact = ShareCards.detect(
            ShareCards.contactCard("Bob", pubKey, 160)!!,
        ) as ShareCodec.Decoded.Contact
        assertTrue(pubKey.contentEquals(contact.publicKey))
        assertEquals("Bob", contact.name)

        val channel = ShareCards.detect(
            ShareCards.channelCard("Trail Talk", secret, 160)!!,
        ) as ShareCodec.Decoded.Channel
        assertTrue(secret.contentEquals(channel.secret))
        assertEquals("Trail Talk", channel.name)
    }

    @Test
    fun `plain text and foreign links are not cards`() {
        assertNull(ShareCards.detect("See https://example.com for details"))
        assertNull(ShareCards.detect("hello"))
        assertNull(ShareCards.detect("migrated data MPGS magic somewhere"))
    }

    @Test
    fun `group budget includes the sender prefix`() {
        // 160 total; "Alicia: " eats 8, leaving 152 for the card
        val fits = ShareCards.contactCard("Bob", pubKey, AckTracker.textBudget(isGroup = true, senderPrefixLen = 8))!!
        assertNotNull(fits)
        assertTrue(fits.toByteArray(Charsets.UTF_8).size <= 152)
    }
}
