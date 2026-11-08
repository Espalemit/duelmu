package id.eduduel.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eduduel_settings")

data class AppSettings(
    val textScale: Float = 1.0f,
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** Nama terakhir, HANYA sebagai nilai awal kolom nickname (selalu bisa diedit). */
    val lastNickname: String? = null,
    val rememberLastNickname: Boolean = true,
)

/** Pengaturan lokal (DataStore). Tidak ada sinkronisasi cloud. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val LAST_NICKNAME = stringPreferencesKey("last_nickname")
        val REMEMBER_NICKNAME = booleanPreferencesKey("remember_last_nickname")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            textScale = (p[Keys.TEXT_SCALE] ?: 1.0f).coerceIn(TEXT_SCALES.first(), TEXT_SCALES.last()),
            soundEnabled = p[Keys.SOUND] ?: false,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            lastNickname = p[Keys.LAST_NICKNAME],
            rememberLastNickname = p[Keys.REMEMBER_NICKNAME] ?: true,
        )
    }

    suspend fun setTextScale(scale: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = scale }
    suspend fun setSound(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setHaptics(enabled: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = enabled }

    suspend fun setRememberNickname(enabled: Boolean) = context.dataStore.edit {
        it[Keys.REMEMBER_NICKNAME] = enabled
        if (!enabled) it.remove(Keys.LAST_NICKNAME)
    }

    suspend fun saveLastNickname(name: String) = context.dataStore.edit {
        if (it[Keys.REMEMBER_NICKNAME] != false) it[Keys.LAST_NICKNAME] = name
    }

    suspend fun clearLastNickname() = context.dataStore.edit { it.remove(Keys.LAST_NICKNAME) }

    companion object {
        val TEXT_SCALES = listOf(0.9f, 1.0f, 1.15f, 1.3f)
    }
}
