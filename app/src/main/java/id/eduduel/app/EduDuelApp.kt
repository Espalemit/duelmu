package id.eduduel.app

import android.app.Application
import id.eduduel.app.net.room.RoomCoordinator
import id.eduduel.app.net.transport.BluetoothController
import id.eduduel.app.net.transport.LanController
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.core.SystemMonotonicClock
import id.eduduel.app.data.bank.QuestionBankRepository
import id.eduduel.app.data.history.EduDuelDatabase
import id.eduduel.app.data.history.HistoryRepository
import id.eduduel.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Dependency container manual (tanpa framework DI) agar build sederhana.
 * Tidak ada Firebase, akun, atau server. Jaringan hanya lokal (Wi-Fi/hotspot/Bluetooth) antar-HP.
 */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: MonotonicClock = SystemMonotonicClock
    val bankRepository = QuestionBankRepository(app, appScope)
    private val database by lazy { EduDuelDatabase.create(app) }
    val history by lazy { HistoryRepository(database.matchRecordDao()) }
    val settings = SettingsRepository(app)
    val bluetooth = BluetoothController(app)
    val lan = LanController(app)
    val room by lazy { RoomCoordinator(appScope, bluetooth, lan, history, clock, BuildConfig.VERSION_NAME) }
}

class EduDuelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
