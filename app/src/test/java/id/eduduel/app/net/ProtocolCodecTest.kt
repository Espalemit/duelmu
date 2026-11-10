package id.eduduel.app.net

import id.eduduel.app.net.protocol.DecodeResult
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.FrameCodec
import id.eduduel.app.net.protocol.FrameDecoder
import id.eduduel.app.net.protocol.FrameTooLargeException
import id.eduduel.app.net.protocol.Hello
import id.eduduel.app.net.protocol.InvalidFrameException
import id.eduduel.app.net.protocol.MAX_FRAME_BYTES
import id.eduduel.app.net.protocol.MatchSetup
import id.eduduel.app.net.protocol.MessageCodec
import id.eduduel.app.net.protocol.PROTOCOL_VERSION
import id.eduduel.app.net.protocol.Ping
import id.eduduel.app.net.protocol.QuestionStart
import id.eduduel.app.net.protocol.RoomStateMessage
import id.eduduel.app.net.protocol.WirePlayer
import id.eduduel.app.net.protocol.WireRoomPhase
import id.eduduel.app.domain.Subject
import id.eduduel.app.net.transport.ConnectionEvent
import id.eduduel.app.net.transport.StreamDuelConnection
import id.eduduel.app.domain.MatchConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class ProtocolCodecTest {

    private fun hello(name: String = "Budi") = Envelope(
        PROTOCOL_VERSION, 0, "m-1",
        payload = Hello("0.1.0", "2026.11.07-r1", "ab".repeat(32), name, "11111111-2222-3333-4444-555555555555"),
    )

    @Test fun roundTripHello_containsNicknameAndSessionId_notAccount() {
        val bytes = MessageCodec.encode(hello("Ani 🚀"))
        val frame = FrameDecoder().feed(bytes).single()
        val json = frame.toString(Charsets.UTF_8)
        assertTrue(json.contains("\"type\":\"HELLO\""))
        assertTrue(json.contains("Ani 🚀"))
        assertTrue(json.contains("playerSessionId"))
        assertFalse(json.contains("uid", ignoreCase = true))
        assertFalse(json.contains("mac", ignoreCase = true))
        val r = MessageCodec.decode(frame) as DecodeResult.Ok
        assertEquals("Ani 🚀", (r.envelope.payload as Hello).nickname)
    }

    @Test fun truncatedPacket_isBufferedUntilComplete() {
        val bytes = MessageCodec.encode(hello())
        val d = FrameDecoder()
        assertTrue(d.feed(bytes, 0, 2).isEmpty()) // header terpotong
        assertTrue(d.feed(bytes, 2, 5).isEmpty()) // body terpotong
        val frames = d.feed(bytes, 7, bytes.size - 7)
        assertEquals(1, frames.size)
        assertEquals(0, d.pendingBytes)
    }

    @Test fun byteByByteDelivery() {
        val bytes = MessageCodec.encode(hello())
        val d = FrameDecoder()
        val frames = bytes.flatMap { b -> d.feed(byteArrayOf(b)) }
        assertEquals(1, frames.size)
    }

    @Test fun twoFramesInOneRead() {
        val a = MessageCodec.encode(hello("A1"))
        val b = MessageCodec.encode(Envelope(PROTOCOL_VERSION, 1, "m-2", payload = Ping(5)))
        val frames = FrameDecoder().feed(MessageCodec.concat(a, b))
        assertEquals(2, frames.size)
        assertTrue((MessageCodec.decode(frames[1]) as DecodeResult.Ok).envelope.payload is Ping)
    }

    @Test(expected = FrameTooLargeException::class)
    fun tooLargeFrame_isRejectedBeforeAllocation() {
        val header = byteArrayOf(0x7F, 0, 0, 0) // ~2 GB
        FrameDecoder().feed(header)
    }

    @Test fun maxFrameBoundary() {
        val payload = ByteArray(MAX_FRAME_BYTES) { 'a'.code.toByte() }
        assertEquals(1, FrameDecoder().feed(FrameCodec.encode(payload)).size)
    }

    @Test(expected = InvalidFrameException::class)
    fun zeroLengthFrame_isInvalid() {
        FrameDecoder().feed(byteArrayOf(0, 0, 0, 0))
    }

    @Test fun malformedJson_andUnknownType_andWrongVersion() {
        assertTrue(MessageCodec.decode("{bukan json".toByteArray()) is DecodeResult.Malformed)
        assertTrue(MessageCodec.decode(byteArrayOf(0xC3.toByte(), 0x28)) is DecodeResult.Malformed) // UTF-8 rusak
        val unknown = """{"v":3,"seq":1,"msgId":"x","payload":{"type":"HACK"}}"""
        assertTrue(MessageCodec.decode(unknown.toByteArray()) is DecodeResult.Malformed)
        val missingField = """{"v":3,"seq":1,"msgId":"x","payload":{"type":"HELLO"}}"""
        assertTrue(MessageCodec.decode(missingField.toByteArray()) is DecodeResult.Malformed)
        // Duelmu 0.3.x (protokol v2, tanpa soal game) ditolak dengan alasan versi yang jelas.
        val v2 = """{"v":2,"seq":1,"msgId":"x","payload":{"type":"PING","sentAtMs":1}}"""
        assertEquals(DecodeResult.VersionMismatch(2), MessageCodec.decode(v2.toByteArray()))
        assertEquals(3, PROTOCOL_VERSION)
        // Aplikasi versi lama (protokol v1, duel 1 lawan 1) ditolak dengan jelas.
        val v1 = """{"v":1,"seq":1,"msgId":"x","payload":{"type":"PING","sentAtMs":1}}"""
        assertEquals(DecodeResult.VersionMismatch(1), MessageCodec.decode(v1.toByteArray()))
    }

    @Test fun questionStart_neverContainsCorrectAnswer() {
        val env = Envelope(PROTOCOL_VERSION, 3, "m", "match", 0, QuestionStart("FIS-SMP-001", listOf("C", "A", "D", "B"), 60_000))
        val json = MessageCodec.encode(env).drop(4).toByteArray().toString(Charsets.UTF_8)
        assertFalse(json.contains("correct", ignoreCase = true))
        val setup = Envelope(PROTOCOL_VERSION, 4, "m2", "match", payload =
            MatchSetup(MatchConfig(), listOf("FIS-SMP-001"), listOf(listOf("A", "B", "C", "D")),
                listOf(WirePlayer("p1", "Budi", isHost = true, ready = true, connected = true)), "ab", "v"))
        assertFalse(MessageCodec.encode(setup).drop(4).toByteArray().toString(Charsets.UTF_8).contains("correct", true))
    }

    @Test fun streamConnection_overPipes_handlesFramingAndEof() = runBlocking {
        val toB = PipedOutputStream()
        val bIn = PipedInputStream(toB, 1 shl 16)
        val toA = PipedOutputStream()
        val aIn = PipedInputStream(toA, 1 shl 16)
        val a = StreamDuelConnection(aIn, toB, "B")
        val b = StreamDuelConnection(bIn, toA, "A")
        a.send(hello("dari A"))
        a.send(Envelope(PROTOCOL_VERSION, 1, "m-2", payload = Ping(9)))
        val got = withTimeout(5_000) { b.events.take(2).toList() }
        assertEquals("dari A", ((got[0] as ConnectionEvent.Received).envelope.payload as Hello).nickname)
        assertTrue((got[1] as ConnectionEvent.Received).envelope.payload is Ping)
        // kirim byte mentah rusak di dalam frame valid -> Malformed, koneksi tetap hidup
        toA.write(FrameCodec.encode("rusak".toByteArray())); toA.flush()
        assertTrue(withTimeout(5_000) { a.events.first() } is ConnectionEvent.Malformed)
        a.close()
        val closed = withTimeout(5_000) { b.events.first { it is ConnectionEvent.Closed } }
        assertTrue(closed is ConnectionEvent.Closed)
    }

    @Test fun matchConfig_mixedSubjectSurvivesRoundTrip_andRoomStateCarriesFourPlayers() {
        // Campuran (subject = null) tidak boleh berubah menjadi mapel lain setelah encode/decode.
        for (cfg in listOf(MatchConfig(null), MatchConfig(Subject.MATEMATIKA))) {
            val players = (1..4).map { WirePlayer("id-$it", "P$it", isHost = it == 1, ready = it % 2 == 0, connected = true) }
            val env = Envelope(PROTOCOL_VERSION, 5, "m5", payload = RoomStateMessage(players, cfg, WireRoomPhase.LOBBY))
            val back = (MessageCodec.decode(MessageCodec.encode(env).drop(4).toByteArray()) as DecodeResult.Ok).envelope.payload as RoomStateMessage
            assertEquals(cfg, back.config)
            assertEquals(players, back.players)
        }
    }

    @Test fun encodeIsStableAcrossDevices() {
        assertArrayEquals(MessageCodec.encode(hello()), MessageCodec.encode(hello()))
    }

    private fun roundTrip(p: id.eduduel.app.net.protocol.Payload): Pair<id.eduduel.app.net.protocol.Payload, Int> {
        val bytes = MessageCodec.encode(Envelope(PROTOCOL_VERSION, 7, "m7", "match", 2, p))
        return (MessageCodec.decode(bytes.drop(4).toByteArray()) as DecodeResult.Ok).envelope.payload to bytes.size
    }

    @Test fun v3_ghostAndGamePayloads_roundTrip_andStaySmall() {
        val ghosts = listOf(
            id.eduduel.app.net.protocol.AsteroidGhost(0.31f, firing = true, filled = 1, wrong = 0),
            id.eduduel.app.net.protocol.TrajectoryGhost(0.5f, 0.75f, 2),
            id.eduduel.app.net.protocol.MicroGhost(0.1f, 0.2f, 0.4f, 0.4f, 0.55f, 0.3f, 0.4f),
            id.eduduel.app.net.protocol.LabGhost(0.6f, 0.2f, holding = 1, pouring = true, fill = 0.4f, done = true),
        )
        for (g in ghosts) {
            val type = when (g) {
                is id.eduduel.app.net.protocol.AsteroidGhost -> id.eduduel.app.domain.game.GameType.ASTEROID
                is id.eduduel.app.net.protocol.TrajectoryGhost -> id.eduduel.app.domain.game.GameType.TRAJECTORY
                is id.eduduel.app.net.protocol.MicroGhost -> id.eduduel.app.domain.game.GameType.MICROSCOPE
                is id.eduduel.app.net.protocol.LabGhost -> id.eduduel.app.domain.game.GameType.LAB
            }
            val (back, size) = roundTrip(id.eduduel.app.net.protocol.GameStateUpdate("11111111-2222-3333-4444-555555555555", type, 42, 12_345, g))
            assertEquals(g, (back as id.eduduel.app.net.protocol.GameStateUpdate).ghost)
            assertTrue("snapshot ghost $size byte (harus kecil untuk 5-10 Hz di Bluetooth)", size < 400)
        }
        val subs = listOf(
            id.eduduel.app.domain.game.AsteroidSubmission(listOf(0, 3, 1)),
            id.eduduel.app.domain.game.TrajectorySubmission(listOf(id.eduduel.app.domain.game.ShotInput(30, 4))),
            id.eduduel.app.domain.game.MicroSubmission(listOf(id.eduduel.app.domain.game.MicroTap(10, 20, 150, 500)), assist = true),
            id.eduduel.app.domain.game.LabSubmission(listOf(id.eduduel.app.domain.game.LabAction(0, 20_000), id.eduduel.app.domain.game.LabAction(-1))),
        )
        for (sub in subs) {
            val (back, _) = roundTrip(id.eduduel.app.net.protocol.GameSubmit("MAT-SMA-061", 9_000, sub))
            assertEquals(sub, (back as id.eduduel.app.net.protocol.GameSubmit).submission)
        }
        val (act, _) = roundTrip(id.eduduel.app.net.protocol.GameActionEvent("p", id.eduduel.app.domain.game.GameType.TRAJECTORY, 1, 5, id.eduduel.app.net.protocol.LaunchAction(3, 4, true)))
        assertEquals(id.eduduel.app.net.protocol.LaunchAction(3, 4, true), (act as id.eduduel.app.net.protocol.GameActionEvent).action)
    }

    @Test fun v3_setupAndStart_carrySeedButNoAnswerOrSolution() {
        val bank = id.eduduel.app.TestBank.bank
        val rounds = id.eduduel.app.domain.QuestionSelector.forConfig(bank, MatchConfig(Subject.FISIKA, id.eduduel.app.domain.QuestionMode.MIXED), 3)
        val setup = MatchSetup(MatchConfig(Subject.FISIKA, id.eduduel.app.domain.QuestionMode.MIXED), rounds.map { it.question.id },
            rounds.map { r -> r.optionOrder.map { it.name } }, listOf(WirePlayer("p1", "Budi", true, true, true)), bank.bankSha256, bank.versionName,
            seeds = rounds.map { it.seed })
        val (back, size) = roundTrip(setup)
        assertEquals(setup, back)
        assertTrue(size < MAX_FRAME_BYTES)
        val text = MessageCodec.encode(Envelope(PROTOCOL_VERSION, 1, "x", payload = setup)).drop(4).toByteArray().toString(Charsets.UTF_8)
        assertFalse(text.contains("correct", true) || text.contains("solution", true) || text.contains("reference", true))
        val game = rounds.first { it.question.isGame }
        val start = QuestionStart(game.question.id, emptyList(), game.question.timeLimitMs, game.seed)
        val startText = MessageCodec.encode(Envelope(PROTOCOL_VERSION, 1, "x", payload = start)).drop(4).toByteArray().toString(Charsets.UTF_8)
        assertFalse(startText.contains("answer", true) || startText.contains("target", true))
    }
}
