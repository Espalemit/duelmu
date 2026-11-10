package id.eduduel.app.presentation.navigation

import androidx.compose.runtime.Composable
import id.eduduel.app.presentation.components.BackgroundMusicScene
import id.eduduel.app.presentation.components.MusicTrack
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import id.eduduel.app.AppContainer
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.presentation.room.CreateRoomScreen
import id.eduduel.app.presentation.room.JoinRoomScreen
import id.eduduel.app.presentation.room.LobbyScreen
import id.eduduel.app.presentation.room.MatchPlayScreen
import id.eduduel.app.presentation.room.MatchResultScreen
import id.eduduel.app.presentation.room.RoomEntryScreen
import id.eduduel.app.presentation.history.HistoryScreen
import id.eduduel.app.presentation.home.HomeScreen
import id.eduduel.app.presentation.settings.PrivacyScreen
import id.eduduel.app.presentation.settings.SettingsScreen
import id.eduduel.app.presentation.solo.SoloPlayScreen
import id.eduduel.app.presentation.solo.SoloSetupScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer belum disediakan") }

// Rute type-safe. TIDAK ADA rute Login/Register/Akun/Profil/Sync.
@Serializable object HomeRoute
@Serializable object SoloSetupRoute
@Serializable data class SoloPlayRoute(val configJson: String)
@Serializable object RoomEntryRoute
@Serializable object CreateRoomRoute
@Serializable object JoinRoomRoute
@Serializable object LobbyRoute
@Serializable object MatchRoute
@Serializable object ResultRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute
@Serializable object PrivacyRoute

private val routeJson = Json { ignoreUnknownKeys = true }
fun MatchConfig.toRouteJson(): String = routeJson.encodeToString(MatchConfig.serializer(), this)
fun configFromRouteJson(s: String): MatchConfig =
    runCatching { routeJson.decodeFromString(MatchConfig.serializer(), s) }.getOrDefault(MatchConfig.INITIAL)
        .copy(mode = id.eduduel.app.domain.QuestionMode.MIXED)

/** Kembali ke Beranda dan bersihkan back stack duel. */
fun NavHostController.goHome() {
    if (!popBackStack(HomeRoute, inclusive = false)) navigate(HomeRoute)
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            HomeScreen(
                onSolo = { navController.navigate(SoloSetupRoute) },
                onDuel = { navController.navigate(RoomEntryRoute) },
                onHistory = { navController.navigate(HistoryRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SoloSetupRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            SoloSetupScreen(
                onBack = { navController.popBackStack() },
                onStart = { cfg -> navController.navigate(SoloPlayRoute(cfg.toRouteJson())) },
            )
        }
        composable<SoloPlayRoute> { entry ->
            val route = entry.toRoute<SoloPlayRoute>()
            SoloPlayScreen(
                config = configFromRouteJson(route.configJson),
                onExit = { navController.goHome() },
            )
        }
        composable<RoomEntryRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            RoomEntryScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(CreateRoomRoute) },
                onJoin = { navController.navigate(JoinRoomRoute) },
                onDemo = { navController.navigate(LobbyRoute) },
            )
        }
        composable<CreateRoomRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            CreateRoomScreen(
                onBack = { navController.popBackStack() },
                onLobby = { navController.navigate(LobbyRoute) { popUpTo(RoomEntryRoute) } },
            )
        }
        composable<JoinRoomRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            JoinRoomScreen(
                onBack = { navController.popBackStack() },
                onLobby = { navController.navigate(LobbyRoute) { popUpTo(RoomEntryRoute) } },
            )
        }
        composable<LobbyRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            LobbyScreen(
                onExit = { navController.goHome() },
                onMatchStarted = { navController.navigate(MatchRoute) { popUpTo(LobbyRoute) { inclusive = true } } },
            )
        }
        composable<MatchRoute> {
            MatchPlayScreen(
                onExit = { navController.goHome() },
                onFinished = { navController.navigate(ResultRoute) { popUpTo(MatchRoute) { inclusive = true } } },
            )
        }
        composable<ResultRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            MatchResultScreen(
                onHome = { navController.goHome() },
                onLobby = { navController.navigate(LobbyRoute) { popUpTo(ResultRoute) { inclusive = true } } },
            )
        }
        composable<HistoryRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            SettingsScreen(onBack = { navController.popBackStack() }, onPrivacy = { navController.navigate(PrivacyRoute) })
        }
        composable<PrivacyRoute> {
            BackgroundMusicScene(MusicTrack.MENU)
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
