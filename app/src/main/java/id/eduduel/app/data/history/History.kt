package id.eduduel.app.data.history

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Jenis entri riwayat lokal. */
enum class RecordMode { SOLO, DUEL }

/**
 * Hasil dari sudut pandang perangkat ini. WIN = peringkat 1 sendirian, DRAW = juara bersama,
 * LOSE = peringkat 2 ke bawah, CANCELLED = pertandingan dibatalkan (tanpa pemenang).
 */
enum class RecordOutcome { WIN, LOSE, DRAW, CANCELLED, PRACTICE }

/**
 * Satu baris riwayat. Nickname disimpan sebagai SNAPSHOT saat pertandingan (bukan referensi akun).
 * Disimpan hanya di perangkat; tidak ada sinkronisasi cloud.
 */
@Entity(tableName = "match_records")
data class MatchRecordEntity(
    @PrimaryKey val id: String,
    val mode: RecordMode,
    val playedAtEpochMs: Long,
    val myNickname: String?,
    val opponentNickname: String?,
    val myRole: String?,
    val myScore: Int,
    val opponentScore: Int?,
    val outcome: RecordOutcome,
    val correctCount: Int,
    val questionCount: Int,
    val configSummary: String,
    val roundsJson: String,
    val bankVersionName: String,
    /** Peringkat saya (1 = juara); null untuk latihan/dibatalkan. */
    val myRank: Int? = null,
    @ColumnInfo(defaultValue = "1")
    val playerCount: Int = 1,
    /** Snapshot semua peserta: nickname, skor, peringkat. */
    @ColumnInfo(defaultValue = "[]")
    val participantsJson: String = "[]",
)

@Serializable
data class ParticipantRecord(val nickname: String, val score: Int, val rank: Int, val isMe: Boolean = false)

@Serializable
data class RoundRecord(
    val index: Int,
    val questionId: String,
    /** Kunci MCQ; null untuk soal game. */
    val correctOptionId: String? = null,
    val myOptionId: String? = null,
    val myPoints: Int,
    val myElapsedMs: Long,
    val opponentOptionId: String? = null,
    val opponentPoints: Int? = null,
    val opponentElapsedMs: Long? = null,
    val voided: Boolean = false,
    /** classic_mcq / visual_mcq / mini_game (null = riwayat lama sebelum 0.4 = klasik). */
    val questionType: String? = null,
    val correct: Boolean? = null,
    /** Ringkasan hasil game (mis. "Kena sasaran pada tembakan 2"). */
    val detail: String? = null,
)

data class DuelStats(val wins: Int, val losses: Int, val draws: Int, val cancelled: Int) {
    val played: Int get() = wins + losses + draws
}

data class OutcomeCount(val outcome: RecordOutcome, val total: Int)

@Dao
interface MatchRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MatchRecordEntity)

    @Query("SELECT * FROM match_records ORDER BY playedAtEpochMs DESC")
    fun observeAll(): Flow<List<MatchRecordEntity>>

    @Query("SELECT * FROM match_records WHERE id = :id")
    suspend fun byId(id: String): MatchRecordEntity?

    @Query("SELECT outcome, COUNT(*) AS total FROM match_records WHERE mode = 'DUEL' GROUP BY outcome")
    fun observeDuelOutcomeCounts(): Flow<List<OutcomeCount>>

    @Query("DELETE FROM match_records")
    suspend fun deleteAll()
}

@Database(entities = [MatchRecordEntity::class], version = 2, exportSchema = true)
abstract class EduDuelDatabase : RoomDatabase() {
    abstract fun matchRecordDao(): MatchRecordDao

    companion object {
        /** v1 (duel 1 lawan 1) -> v2 (room maks. 4 pemain): kolom peringkat & peserta. Riwayat lama dipertahankan. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE match_records ADD COLUMN myRank INTEGER")
                db.execSQL("ALTER TABLE match_records ADD COLUMN playerCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE match_records ADD COLUMN participantsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("UPDATE match_records SET playerCount = 2 WHERE mode = 'DUEL'")
            }
        }

        fun create(context: Context): EduDuelDatabase =
            Room.databaseBuilder(context, EduDuelDatabase::class.java, "eduduel.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

class HistoryRepository(private val dao: MatchRecordDao) {
    val records: Flow<List<MatchRecordEntity>> = dao.observeAll()

    val duelStats: Flow<DuelStats> = dao.observeDuelOutcomeCounts().map { rows ->
        fun n(o: RecordOutcome) = rows.firstOrNull { it.outcome == o }?.total ?: 0
        DuelStats(n(RecordOutcome.WIN), n(RecordOutcome.LOSE), n(RecordOutcome.DRAW), n(RecordOutcome.CANCELLED))
    }

    suspend fun save(record: MatchRecordEntity) = dao.upsert(record)
    suspend fun clear() = dao.deleteAll()

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun encodeRounds(rounds: List<RoundRecord>): String = json.encodeToString(rounds)
        fun decodeRounds(raw: String): List<RoundRecord> =
            runCatching { json.decodeFromString<List<RoundRecord>>(raw) }.getOrDefault(emptyList())
        fun encodeParticipants(list: List<ParticipantRecord>): String = json.encodeToString(list)
        fun decodeParticipants(raw: String): List<ParticipantRecord> =
            runCatching { json.decodeFromString<List<ParticipantRecord>>(raw) }.getOrDefault(emptyList())
    }
}
