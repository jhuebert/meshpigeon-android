package app.meshpigeon.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quoted-text reaction format (03 §6, 07 §4) — readable on every client. */
class ReactionsTest {

    @Test
    fun `encode and parse round trip`() {
        val text = Reactions.encode("bob", "I had a really good time at the fair", "❤️", 170)
        assertEquals("@[bob] \"I had a really good time at the fair\"\n❤️", text)
        val (target, quote, emoji) = Reactions.parse(text)!!
        assertEquals("bob", target)
        assertEquals("I had a really good time at the fair", quote)
        assertEquals("❤️", emoji)
    }

    @Test
    fun `quote is truncated to the budget with an ellipsis`() {
        val long = "a".repeat(300)
        val text = Reactions.encode("bob", long, "👍", 40)
        // prefix + quoted truncation + emoji line must fit the budget
        assertTrue(Reactions.utf8Length(text) <= 40)
        val (target, quote, emoji) = Reactions.parse(text)!!
        assertEquals("bob", target)
        assertEquals("👍", emoji)
        // the truncated quote is still a prefix of the target body
        assertTrue(long.startsWith(quote.removeSuffix("…")))
        assertTrue(text.endsWith("\n👍"))
    }

    @Test
    fun `tiny budget drops the quote but keeps the reaction`() {
        val text = Reactions.encode("bob", "some long text", "👍", 12)
        assertEquals("@[bob] 👍", text)
        assertNull(Reactions.parse(text)) // no quote → not a parseable reaction
    }

    @Test
    fun `multi-byte quotes truncate at a character boundary`() {
        val body = "🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉"
        val text = Reactions.encode("bob", body, "😢", 30)
        assertTrue(Reactions.utf8Length(text) <= 30)
        val quote = Reactions.parse(text)!!.second
        assertTrue(body.startsWith(quote.removeSuffix("…")))
    }

    @Test
    fun `parse rejects non-reaction text`() {
        assertNull(Reactions.parse("hello world"))
        assertNull(Reactions.parse("👍")) // bare emoji is a message, not a reaction
        assertNull(Reactions.parse("@[bob] no emoji here"))
        assertNull(Reactions.parse("@[bob] unknown emoji 🤷"))
        assertNull(Reactions.parse("@[bob] unknown emoji\n🤷"))
        assertNull(Reactions.parse("@[] \"quote\"\n👍")) // empty target
        assertNull(Reactions.parse("@[bob] \"\"\n👍")) // empty quote
        assertNull(Reactions.parse("@[bob] \"quote\" 👍")) // emoji not on its own line
        assertNull(Reactions.parse("@[bob] \"unterminated\n👍")) // no closing quote
        assertNull(Reactions.parse("@[bob] \"trailing junk\" x\n👍"))
        assertNull(Reactions.parse("@bob \"quote\"\n👍")) // no brackets
    }

    @Test
    fun `quotes and newlines inside the target text survive round trip`() {
        val body = "saying \"hi\" there"
        val text = Reactions.encode("bob", body, "👍", 170)
        val (target, quote, emoji) = Reactions.parse(text)!!
        assertEquals("bob", target)
        assertEquals(body, quote)
        assertEquals("👍", emoji)
    }
}
