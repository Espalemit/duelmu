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
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Nama terakhir, HANYA sebagai nilai awal kolom nickname (selalu bisa diedit). */
    val lastNickname: String? = null,
    val rememberLastNickname: Boolean = true,
    val musicEnabled: Boolean = true,
    /** Tampilkan ghost pemain lain di mini-game (lokal per HP). */
    val ghostEnabled: Boolean = true,
    /** Opasitas ghost 15-30% (default 25%). */
    val ghostOpacity: Float = DEFAULT_GHOST_OPACITY,
    /** Aksesibilitas: target sentuh lebih besar untuk objek kecil. */
    val touchAssist: Boolean = false,
)

const val DEFAULT_GHOST_OPACITY = 0.25f
const val MIN_GHOST_OPACITY = 0.15f
const val MAX_GHOST_OPACITY = 0.30f

/** Pengaturan lokal (DataStore). Tidak ada sinkronisasi cloud. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val MUSIC = booleanPreferencesKey("music_enabled")
        val LAST_NICKNAME = stringPreferencesKey("last_nickname")
        val REMEMBER_NICKNAME = booleanPreferencesKey("remember_last_nickname")
        val GHOST = booleanPreferencesKey("ghost_enabled")
        val GHOST_OPACITY = floatPreferencesKey("ghost_opacity")
        val TOUCH_ASSIST = booleanPreferencesKey("touch_assist")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            textScale = (p[Keys.TEXT_SCALE] ?: 1.0f).coerceIn(TEXT_SCALES.first(), TEXT_SCALES.last()),
            soundEnabled = p[Keys.SOUND] ?: true,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            musicEnabled = p[Keys.MUSIC] ?: true,
            lastNickname = p[Keys.LAST_NICKNAME],
            rememberLastNickname = p[Keys.REMEMBER_NICKNAME] ?: true,
            ghostEnabled = p[Keys.GHOST] ?: true,
            ghostOpacity = (p[Keys.GHOST_OPACITY] ?: DEFAULT_GHOST_OPACITY).coerceIn(MIN_GHOST_OPACITY, MAX_GHOST_OPACITY),
            touchAssist = p[Keys.TOUCH_ASSIST] ?: false,
        )
    }

    suspend fun setTextScale(scale: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = scale }
    suspend fun setSound(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setHaptics(enabled: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setMusic(enabled: Boolean) = context.dataStore.edit { it[Keys.MUSIC] = enabled }
    suspend fun setGhost(enabled: Boolean) = context.dataStore.edit { it[Keys.GHOST] = enabled }
    suspend fun setGhostOpacity(value: Float) = context.dataStore.edit { it[Keys.GHOST_OPACITY] = value.coerceIn(MIN_GHOST_OPACITY, MAX_GHOST_OPACITY) }
    suspend fun setTouchAssist(enabled: Boolean) = context.dataStore.edit { it[Keys.TOUCH_ASSIST] = enabled }

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
