package id.eduduel.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF0B6E8C)
private val TealDark = Color(0xFF7FD3EE)
private val Green = Color(0xFF1E9E7A)
private val GreenDark = Color(0xFF7CE0B5)
private val Amber = Color(0xFFB86E00)
private val AmberDark = Color(0xFFFFB95C)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4EBF8),
    onPrimaryContainer = Color(0xFF002634),
    secondary = Green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F2DA),
    onSecondaryContainer = Color(0xFF002117),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB5),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF6FAFB),
    onBackground = Color(0xFF161C1E),
    surface = Color(0xFFF6FAFB),
    onSurface = Color(0xFF161C1E),
    surfaceVariant = Color(0xFFDCE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    surfaceContainer = Color(0xFFEAF1F3),
    surfaceContainerHigh = Color(0xFFE2EBEE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF70787D),
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF003546),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFC4EBF8),
    secondary = GreenDark,
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF00513D),
    onSecondaryContainer = Color(0xFFB9F2DA),
    tertiary = AmberDark,
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF653E00),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF0E1416),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0E1416),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFBFC8CC),
    surfaceContainer = Color(0xFF1A2123),
    surfaceContainerHigh = Color(0xFF252B2E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A9296),
)

/** Warna semantik untuk umpan balik jawaban (kontras cukup di terang & gelap). */
@Immutable
data class FeedbackColors(
    val correct: Color,
    val correctContainer: Color,
    val onCorrectContainer: Color,
    val wrong: Color,
    val wrongContainer: Color,
    val onWrongContainer: Color,
)

private val LightFeedback = FeedbackColors(
    Color(0xFF1B7A3D), Color(0xFFC8F2D2), Color(0xFF00210C),
    Color(0xFFB3261E), Color(0xFFFFDAD6), Color(0xFF410002),
)
private val DarkFeedback = FeedbackColors(
    Color(0xFF8FDCA4), Color(0xFF0E5229), Color(0xFFC8F2D2),
    Color(0xFFFFB4AB), Color(0xFF8C1D18), Color(0xFFFFDAD6),
)

val LocalFeedbackColors = staticCompositionLocalOf { LightFeedback }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 26.sp),
    )
}

val QuestionTextStyle = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)

@Composable
fun EduDuelTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFeedbackColors provides if (darkTheme) DarkFeedback else LightFeedback) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
