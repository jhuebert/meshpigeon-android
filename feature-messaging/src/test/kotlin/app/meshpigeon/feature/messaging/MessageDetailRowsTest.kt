package app.meshpigeon.feature.messaging

import app.meshpigeon.domain.Message
import app.meshpigeon.protocol.DeliveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDetailRowsTest {

    private fun received(
        snr: Float? = null,
        rssi: Int? = null,
        hops: Int? = null,
        region: String? = null,
        packetTag: ByteArray? = null,
    ) = Message(
        id = 1,
        conversationId = 1,
        identityId = 1,
        senderName = "bob",
        body = "hi",
        sentAt = 1_758_000_000_000,
        out = false,
        snr = snr,
        rssi = rssi,
        hops = hops,
        region = region,
        packetTag = packetTag,
    )

    @Test
    fun `received message with full metadata shows reception rows`() {
        val rows = messageDetailRows(
            received(
                snr = 10.4f,
                rssi = -46,
                hops = 2,
                region = "USA",
                packetTag = byteArrayOf(0x0a, 0x1b.toByte(), 0x2c.toByte()),
            ),
        ).toMap()

        assertEquals("Received", rows["Direction"])
        assertEquals("2", rows["Hops"])
        assertEquals("+10 dB", rows["SNR"])
        assertEquals("-46 dBm", rows["RSSI"])
        assertEquals("USA", rows["Region"])
        assertEquals("0a1b2c", rows["Packet tag"])
        assertFalse(rows.containsKey("Delivery"))
        assertFalse(rows.containsKey("Round trip"))
        assertTrue(rows.containsKey("Time"))
    }

    @Test
    fun `negative snr keeps its sign`() {
        val rows = messageDetailRows(received(snr = -7.6f)).toMap()
        assertEquals("-7 dB", rows["SNR"])
    }

    @Test
    fun `outgoing confirmed message shows delivery and round trip`() {
        val rows = messageDetailRows(
            Message(
                id = 2,
                conversationId = 1,
                identityId = 1,
                body = "hi",
                sentAt = 1_758_000_000_000,
                out = true,
                state = DeliveryState.CONFIRMED,
                rttMs = 1_234,
            ),
        ).toMap()

        assertEquals("Sent", rows["Direction"])
        assertEquals("✓✓ Confirmed", rows["Delivery"])
        assertEquals("1234 ms", rows["Round trip"])
        assertFalse(rows.containsKey("Hops"))
        assertFalse(rows.containsKey("SNR"))
        assertFalse(rows.containsKey("RSSI"))
    }

    @Test
    fun `message with no metadata still shows direction and time only`() {
        val rows = messageDetailRows(received()).toMap()
        assertEquals(2, rows.size)
        assertEquals("Received", rows["Direction"])
        assertTrue(rows.containsKey("Time"))
    }
}
