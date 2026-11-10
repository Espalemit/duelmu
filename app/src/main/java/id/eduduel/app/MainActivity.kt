package id.eduduel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.presentation.navigation.AppNavHost
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme
import id.eduduel.app.presentation.components.LocalInteractionFeedback
import id.eduduel.app.presentation.components.rememberFeedback
import id.eduduel.app.presentation.components.LocalBackgroundMusic
import id.eduduel.app.presentation.components.rememberBackgroundMusic

/** Single-activity. Splash singkat lalu LANGSUNG Beranda: tanpa login dan tanpa form nama awal. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as EduDuelApp).container
        setContent {
            val savedSettings by container.settings.settings.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            val settings = savedSettings ?: AppSettings()
            val base = LocalDensity.current
            val feedback = rememberFeedback(settings)
            val music = rememberBackgroundMusic(savedSettings != null && settings.musicEnabled)
            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalDensity provides Density(base.density, base.fontScale * settings.textScale),
                LocalInteractionFeedback provides feedback,
                LocalBackgroundMusic provides music,
            ) {
                EduDuelTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        id.eduduel.app.presentation.components.GameEffectsHost { AppNavHost() }
                    }
                }
            }
        }
    }
}
