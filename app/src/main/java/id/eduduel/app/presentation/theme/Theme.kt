package id.eduduel.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF7150C7)
private val TealDark = Color(0xFF7FD3EE)
private val Green = Color(0xFF16725B)
private val GreenDark = Color(0xFF7CE0B5)
private val Amber = Color(0xFF925900)
private val AmberDark = Color(0xFFFFB95C)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF432980),
    secondary = Green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F5E9),
    onSecondaryContainer = Color(0xFF165743),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDB5),
    onTertiaryContainer = Color(0xFF704400),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF302A42),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF302A42),
    surfaceVariant = Color(0xFFF1ECF7),
    onSurfaceVariant = Color(0xFF686174),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFF5F0FB),
    surfaceContainerHigh = Color(0xFFEEE8F6),
    surfaceContainerHighest = Color(0xFFE7DFEF),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8B7F9B),
    outlineVariant = Color(0xFFE3DCEA),
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

private val RoundedFont = FontFamily(android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.NORMAL))

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontFamily = RoundedFont, fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = RoundedFont, fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = RoundedFont, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = RoundedFont, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = RoundedFont, fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 26.sp),
    )
}

val QuestionTextStyle = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)

@Composable
fun EduDuelTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFeedbackColors provides if (darkTheme) DarkFeedback else LightFeedback) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(16.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
