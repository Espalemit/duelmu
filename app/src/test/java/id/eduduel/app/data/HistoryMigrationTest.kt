package id.eduduel.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.eduduel.app.data.history.EduDuelDatabase
import id.eduduel.app.data.history.HistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Riwayat duel 1 lawan 1 dari versi 0.1.0 (skema v1) tetap terbaca setelah update ke skema v2 (room).
 * Database v1 dibuat persis dari skema yang diekspor Room (app/schemas/.../1.json).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HistoryMigrationTest {

    @Test fun migrate1To2_keepsOldDuelRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schemaDir = listOf(File("schemas"), File("app/schemas")).first { it.exists() }
        val v1 = Json.parseToJsonElement(File(schemaDir, "id.eduduel.app.data.history.EduDuelDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        val entity = v1.getValue("entities").jsonArray.single().jsonObject
        val createSql = entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "match_records")

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).also { it.parentFile?.mkdirs() }, null).use { db ->
            db.execSQL(createSql)
            v1.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL(
                "INSERT INTO match_records VALUES ('old1','DUEL',1,'Rina','Budi','HOST',500,300,'WIN',3,5,'Fisika · 5 soal','[]','2026.11.07-r1')",
            )
            db.execSQL(
                "INSERT INTO match_records VALUES ('old2','SOLO',2,NULL,NULL,NULL,400,NULL,'PRACTICE',2,5,'Campuran · 5 soal','[]','2026.11.07-r1')",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, EduDuelDatabase::class.java, name)
            .addMigrations(EduDuelDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val records = HistoryRepository(room.matchRecordDao()).records.first().associateBy { it.id }
            val duel = records.getValue("old1")
            assertEquals("Budi", duel.opponentNickname)
            assertEquals(2, duel.playerCount)
            assertNull(duel.myRank)
            assertEquals("[]", duel.participantsJson)
            assertEquals(1, records.getValue("old2").playerCount)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
