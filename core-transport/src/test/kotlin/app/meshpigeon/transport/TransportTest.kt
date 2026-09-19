package app.meshpigeon.transport

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Framing must match the firmware byte-for-byte (09 §1, shared design). */
class TransportFrameCodecTest {

    @Test
    fun `crc16 ccitt-false reference values`() {
        assertEquals(0x29B1, TransportFrameCodec.crc16("123456789".toByteArray()))
        // empty input returns the init value (matches firmware crc16_ccitt)
        assertEquals(0xFFFF, TransportFrameCodec.crc16(ByteArray(0)))
        assertEquals(0x0000, TransportFrameCodec.crc16(ByteArray(0), init = 0))
    }

    @Test
    fun `cobs round trip incl zeros`() {
        val src = byteArrayOf(0x01, 0x00, 0x02, 0x03, 0x00, 0xFF.toByte(), 0x05)
        val enc = TransportFrameCodec.cobsEncode(src)
        val dec = TransportFrameCodec.cobsDecode(enc)
        assertNotNull(dec)
        assertArrayEquals(src, dec)
    }

    @Test
    fun `wire round trip`() {
        val frame = TransportFrameCodec.build(0x02, 0x42, 0, byteArrayOf(1, 2, 3))
        val wire = TransportFrameCodec.encodeWire(frame)
        assertTrue(wire.last() == 0x00.toByte())
        val decoded = TransportFrameCodec.decodeWire(wire)
        assertNotNull(decoded)
        val parsed = TransportFrameCodec.parse(decoded!!)
        assertEquals(0x02, parsed.cmd)
        assertEquals(0x42, parsed.nonce)
        assertEquals(0, parsed.status)
        assertArrayEquals(byteArrayOf(1, 2, 3), parsed.payload)
    }

    @Test
    fun `matches firmware encoder output for PING`() {
        // captured from the C firmware: PING nonce 0x42, payload "ping!"
        // wire = 03 01 42 08 70 69 6e 67 21 55 5a 00
        val frame = RadioFrame(0x01, 0x42, 0, "ping!".toByteArray())
        val wire = TransportFrameCodec.toWire(frame)
        val expected = byteArrayOf(
            0x03, 0x01, 0x42, 0x08.toByte(), 0x70, 0x69, 0x6e, 0x67, 0x21, 0x55, 0x5A.toByte(), 0x00,
        )
        assertArrayEquals(expected, wire)
    }

    @Test
    fun `bad crc dropped`() {
        val frame = TransportFrameCodec.build(0x01, 1, 0)
        val wire = TransportFrameCodec.encodeWire(frame)
        wire[0] = (wire[0] + 1).toByte()
        assertNull(TransportFrameCodec.decodeWire(wire))
    }

    @Test
    fun `frame stream reassembles fragmented chunks`() {
        val frame = TransportFrameCodec.build(0x02, 0x07, 0, byteArrayOf(1, 2, 3))
        val wire = TransportFrameCodec.encodeWire(frame)
        val stream = FrameStream()
        val got = ArrayList<RadioFrame>()
        got.addAll(stream.feed(wire.copyOfRange(0, 3)))
        assertTrue(got.isEmpty())
        got.addAll(stream.feed(wire.copyOfRange(3, wire.size)))
        assertEquals(1, got.size)
        assertEquals(0x07.toByte().toInt(), got[0].nonce)
    }

    @Test
    fun `garbage before frame self-heals`() {
        val frame = TransportFrameCodec.build(0x01, 9, 0)
        val wire = TransportFrameCodec.encodeWire(frame)
        val stream = FrameStream()
        val got = ArrayList<RadioFrame>()
        got.addAll(stream.feed(byteArrayOf(0x55, 0x2C) + wire)) // garbage glued on
        got.addAll(stream.feed(wire)) // next intact frame
        assertEquals(1, got.size) // corrupt one dropped, good one decoded
        assertEquals(9, got[0].nonce)
    }
}

class RadioSessionTest {

    @Test
    fun `ping round trip through fake adapter`() = runTest {
        val adapter = FakeRadioAdapter()
        val session = RadioSession(adapter, backgroundScope)
        session.start()
        val resp = session.ping("hello".toByteArray())
        assertEquals("hello".toByteArray().toList(), resp.payload.toList())
    }

    @Test
    fun `get info parses 49-byte payload`() = runTest {
        val adapter = FakeRadioAdapter()
        val session = RadioSession(adapter, backgroundScope)
        session.start()
        val info = session.getInfo()
        assertEquals(1, info.protoVersion)
        assertEquals("FAKE", info.boardName)
        assertEquals(42_000L, info.uptimeMs)
        assertEquals(3L, info.bootCount)
    }

    @Test
    fun `set radio bumps epoch and notifies others`() = runTest {
        val adapter = FakeRadioAdapter()
        val session = RadioSession(adapter, backgroundScope)
        session.start()
        val events = mutableListOf<RadioFrame>()
        val job = launch { adapter.frames.collect { events.add(it) } }

        val applied = session.setRadioSettings(
            RadioSession.RadioSettings(1, 4, 906_875_000, 12_500, 9, 5, 22, 99),
        )
        assertEquals(1L, applied.configEpoch) // radio bumps, ignores request epoch
        assertEquals(4, applied.region)
        job.cancel()
        // RADIO_CHANGED broadcast arrived (nonce 0)
        assertTrue(events.any { it.cmd == RadioFrame.CMD_RADIO_CHANGED && it.nonce == 0 })
    }

    @Test
    fun `send packet returns seq and tx result`() = runTest {
        val adapter = FakeRadioAdapter()
        val session = RadioSession(adapter, backgroundScope)
        session.start()
        val seq = session.sendPacket(byteArrayOf(0x45, 1, 2, 3))
        assertEquals(1L, seq)
        val entry = adapter.store.first { it.seq == seq }
        assertEquals(0x01, entry.flags)
    }

    @Test
    fun `tx failure surfaces as status error`() = runTest {
        val adapter = FakeRadioAdapter()
        adapter.dropNextTx = true
        val session = RadioSession(adapter, backgroundScope)
        session.start()
        try {
            session.sendPacket(byteArrayOf(1))
            throw AssertionError("expected Status exception")
        } catch (e: RadioSession.CommandException.Status) {
            assertEquals(RadioFrame.STATUS_ERR_TX_FAILED, e.status)
        }
    }

    @Test
    fun `fetch packets is cursor resumable`() = runTest {
        val adapter = FakeRadioAdapter()
        val session = RadioSession(adapter, backgroundScope)
        session.start()
        repeat(3) { adapter.injectPacket(byteArrayOf(0x45, it.toByte())) }
        val got = ArrayList<PacketEntry>()
        val n1 = session.fetchPackets(0, 10) { got.add(it) }
        assertEquals(3, n1)
        // resume after seq 1
        val got2 = ArrayList<PacketEntry>()
        val n2 = session.fetchPackets(got[0].seq, 10) { got2.add(it) }
        assertEquals(2, n2)
    }

    @Test
    fun `command timeout raises typed exception`() = runTest {
        val adapter = FakeRadioAdapter()
        val session = RadioSession(adapter, backgroundScope, commandTimeoutMs = 50)
        // don't start the session → no responses pending → timeout
        try {
            session.ping()
            throw AssertionError("expected timeout")
        } catch (e: RadioSession.CommandException.Timeout) {
            // expected
        }
    }
}
