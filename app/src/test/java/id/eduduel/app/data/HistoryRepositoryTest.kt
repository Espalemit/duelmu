package id.eduduel.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.eduduel.app.data.history.DuelStats
import id.eduduel.app.data.history.EduDuelDatabase
import id.eduduel.app.data.history.HistoryRepository
import id.eduduel.app.data.history.MatchRecordEntity
import id.eduduel.app.data.history.ParticipantRecord
import id.eduduel.app.data.history.RecordMode
import id.eduduel.app.data.history.RecordOutcome
import id.eduduel.app.data.history.RoundRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Riwayat hanya lokal (Room) dan menyimpan SNAPSHOT nickname per pertandingan. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HistoryRepositoryTest {
    private lateinit var db: EduDuelDatabase
    private lateinit var repo: HistoryRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EduDuelDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HistoryRepository(db.matchRecordDao())
    }

    @After fun tearDown() = db.close()

    private fun duel(id: String, me: String, opp: String, outcome: RecordOutcome, at: Long) = MatchRecordEntity(
        id, RecordMode.DUEL, at, me, opp, "HOST", 500, 300, outcome, 3, 5, "Fisika · 5 soal",
        HistoryRepository.encodeRounds(listOf(RoundRecord(0, "FIS-SMP-001", "A", "A", 150, 100, "B", 0, 2000))), "v1",
    )

    @Test fun storesNicknameSnapshotPerMatch_andStats() = runBlocking {
        repo.save(duel("m1", "Rina", "Budi", RecordOutcome.WIN, 1))
        // Pertandingan berikutnya dengan nama berbeda: entri lama tetap memakai nama lamanya.
        repo.save(duel("m2", "Rina Baru", "Budi", RecordOutcome.DRAW, 2))
        repo.save(duel("m3", "Sama", "Sama", RecordOutcome.CANCELLED, 3))
        val all = repo.records.first()
        assertEquals(listOf("m3", "m2", "m1"), all.map { it.id })
        assertEquals("Rina", all.first { it.id == "m1" }.myNickname)
        assertEquals("Rina Baru", all.first { it.id == "m2" }.myNickname)
        assertEquals(DuelStats(wins = 1, losses = 0, draws = 1, cancelled = 1), repo.duelStats.first())
        val rounds = HistoryRepository.decodeRounds(all.first().roundsJson)
        assertEquals("FIS-SMP-001", rounds.single().questionId)
    }

    @Test fun roomMatch_storesRankAndAllParticipantSnapshots() = runBlocking {
        val parts = listOf(ParticipantRecord("Rina", 900, 1, isMe = true), ParticipantRecord("Budi", 900, 1), ParticipantRecord("Sari", 300, 3))
        repo.save(
            duel("m4", "Rina", "Budi, Sari", RecordOutcome.DRAW, 4).copy(
                myRank = 1, playerCount = 3, participantsJson = HistoryRepository.encodeParticipants(parts),
            ),
        )
        val r = repo.records.first().single()
        assertEquals(1, r.myRank)
        assertEquals(3, r.playerCount)
        assertEquals(parts, HistoryRepository.decodeParticipants(r.participantsJson))
    }

    @Test fun clearDeletesEverything() = runBlocking {
        repo.save(duel("m1", "Rina", "Budi", RecordOutcome.LOSE, 1))
        repo.clear()
        assertTrue(repo.records.first().isEmpty())
    }
}
