package app.meshpigeon.domain

import app.meshpigeon.protocol.Channels
import app.meshpigeon.protocol.Crypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Channel create/join semantics (07 §6) for the start-chat sheet and QR import. */
class CreateChannelTest {

    private val channels = FakeChannelRepository()
    private val conversations = FakeConversationRepository()
    private val createChannel = CreateChannel(channels, conversations)
    private val identityId = 7L

    @Test
    fun `shared channel derives its key from the name`() = runTest {
        val (channelId, conversationId) = createChannel.createShared(identityId, "Trail Talk")!!
        val channel = channels.store.value.single()
        assertEquals(channelId, channel.id)
        assertEquals("Trail Talk", channel.name)
        assertEquals(ChannelKind.HASHTAG, channel.kind)
        assertTrue(Channels.hashtagKey("Trail Talk").contentEquals(channel.keyEnc))
        assertEquals(conversationId, conversations.store.value.single().id)
        assertEquals(ConversationKind.GROUP, conversations.store.value.single().kind)
        assertEquals(channelId, conversations.store.value.single().refId)
    }

    @Test
    fun `private channel without a key generates one`() = runTest {
        val a = createChannel.createPrivate(identityId, "Family", null)!!
        val b = createChannel.createPrivate(identityId, "Squad", null)!!
        val stored = channels.store.value.sortedBy { it.id }
        assertEquals(2, stored.size)
        assertTrue(!stored[0].keyEnc.contentEquals(stored[1].keyEnc))
        assertEquals(ChannelKind.PRIVATE, stored[0].kind)
        assertEquals(ChannelKind.PRIVATE, stored[1].kind)
        assertEquals(a.second, conversations.store.value.first { it.refId == a.first }.id)
    }

    @Test
    fun `private channel accepts a typed pasted key`() = runTest {
        createChannel.createPrivate(identityId, "Keyed", " 00-11:22 33445566778899AABBCCDDEEFF ")!!
        val channel = channels.store.value.single()
        assertEquals(ChannelKind.PRIVATE, channel.kind)
        assertTrue(
            Crypto.unhex("00112233445566778899aabbccddeeff").contentEquals(channel.keyEnc),
        )
    }

    @Test
    fun `typed hashtag key still lands as a shared channel`() = runTest {
        createChannel.createPrivate(identityId, "Trail Talk", Crypto.hex(Channels.hashtagKey("Trail Talk")))!!
        assertEquals(ChannelKind.HASHTAG, channels.store.value.single().kind)
    }

    @Test
    fun `joining an existing channel reopens it instead of duplicating`() = runTest {
        val first = createChannel.createShared(identityId, "Trail Talk")!!
        val again = createChannel.join(identityId, " Trail Talk ", Channels.hashtagKey("Trail Talk"))!!
        assertEquals(first, again)
        assertEquals(1, channels.store.value.size)
        assertEquals(1, conversations.store.value.size)
    }

    @Test
    fun `same key different label stays one channel row`() = runTest {
        // channel identity is the key, not the label — a renamed re-join
        // must not fork a second channel row
        val first = createChannel.join(identityId, "Trail Talk", Channels.hashtagKey("Trail Talk"))!!
        val second = createChannel.join(identityId, "trail talk", Channels.hashtagKey("Trail Talk"))!!
        assertEquals(first, second)
        assertEquals(1, channels.store.value.size)
    }

    @Test
    fun `bad input returns null`() = runTest {
        assertNull(createChannel.createShared(identityId, "   "))
        assertNull(createChannel.createPrivate(identityId, "X", "zz")) // not hex
        assertNull(createChannel.createPrivate(identityId, "X", "00 11")) // wrong length
        assertNull(createChannel.join(identityId, "X", ByteArray(15)))
    }
}
