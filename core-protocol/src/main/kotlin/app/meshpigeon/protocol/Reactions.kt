package app.meshpigeon.protocol

/**
 * Channel reactions ride as ordinary GRP_TXT so every client on the mesh —
 * the official MeshCore companion and MeshCore Open included — sees a
 * readable message (03 §6, 07 §4). Wire format inside the normal
 * `"sender: "` prefix:
 *
 *     "@[<target sender>] <quoted text> <emoji>"
 *
 * MeshPigeon parses it and attaches the emoji to the newest message by
 * `<target sender>` whose body starts with the quoted text (quotes may be
 * truncated to the channel budget). Unmatched targets fall back to a normal
 * message so nothing is lost. The legacy GRP_DATA reaction
 * ([ReactionData]) stays decode-only for app-to-app compat.
 *
 * (MeshCore Open's `r:<hash>:<index>` format was considered and rejected:
 * its target hash is Dart's implementation-defined `String.hashCode` and its
 * emoji table is app-internal — replicating both would break silently.)
 */
object Reactions {
    /** The picker row; the parser recognizes exactly these (07 §4). */
    val EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢")

    /**
     * Compose the reaction text, truncating the quote at a UTF-8 character
     * boundary to fit `budgetBytes` (the group text budget after the
     * `"name: "` prefix). `…` marks a truncated quote.
     */
    fun encode(targetSender: String, targetBody: String, emoji: String, budgetBytes: Int): String {
        val prefix = "@[$targetSender] "
        val tail = " $emoji"
        var room = budgetBytes - utf8Length(prefix) - utf8Length(tail)
        val full = targetBody.trim()
        if (room <= 0 || full.isEmpty()) {
            // nothing to quote — still a valid reaction
            return "@[$targetSender]" + tail
        }
        room -= 3 // reserve bytes for the truncation marker
        val cut = truncateUtf8(full, room)
        val quote = if (cut.length < full.length) "$cut…" else full
        return prefix + quote + tail
    }

    /** Returns (target sender, quoted text, emoji), or null when not a reaction. */
    fun parse(text: String): Triple<String, String, String>? {
        if (!text.startsWith("@[")) return null
        val close = text.indexOf(']')
        if (close <= 2) return null // "@[]" or unterminated
        val target = text.substring(2, close).trim()
        if (target.isEmpty()) return null
        val rest = text.substring(close + 1)
        val emoji = EMOJIS.firstOrNull { rest.endsWith(it) } ?: return null
        val quote = rest.removeSuffix(emoji).trim()
        if (quote.isEmpty()) return null
        return Triple(target, quote, emoji)
    }

    fun utf8Length(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    /** Cut `s` at a UTF-8 character boundary (code-point aware) to at most `maxBytes` bytes. */
    private fun truncateUtf8(s: String, maxBytes: Int): String {
        var bytes = 0
        var index = 0
        while (index < s.length) {
            val cp = s.codePointAt(index)
            val len = utf8Length(String(Character.toChars(cp)))
            if (bytes + len > maxBytes) break
            bytes += len
            index += Character.charCount(cp)
        }
        return s.take(index)
    }
}
