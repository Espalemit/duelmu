package id.eduduel.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.cos
import kotlinx.coroutines.flow.collect

/** One-shot events owned above navigation so a locked answer cannot clip or remove an effect. */
class GameVisualEffects {
    var celebration by mutableIntStateOf(0)
        private set
    var damage by mutableIntStateOf(0)
        private set
    fun celebrate() { celebration++ }
    fun loseLife() { damage++ }
}

val LocalGameVisualEffects = staticCompositionLocalOf<GameVisualEffects?> { null }

@Composable
fun GameEffectsHost(effects: GameVisualEffects = remember { GameVisualEffects() }, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGameVisualEffects provides effects) {
        Box(Modifier.fillMaxSize()) {
            content()
            GameEffectsOverlay(effects, Modifier.matchParentSize().zIndex(10f))
        }
    }
}

/** Drawing only: this layer never consumes touch input, including during a transition. */
@Composable
private fun GameEffectsOverlay(effects: GameVisualEffects, modifier: Modifier) {
    val confetti = remember { Animatable(1f) }
    val damage = remember { Animatable(1f) }
    LaunchedEffect(effects.celebration) {
        if (effects.celebration > 0) {
            confetti.snapTo(0f)
            confetti.animateTo(1f, tween(2_000, easing = LinearEasing))
        }
    }
    LaunchedEffect(effects) {
        var consumed = 0
        snapshotFlow { effects.damage }.collect { total ->
            // Preserve each -1, including two collisions in the same frame.
            while (consumed < total) {
                damage.snapTo(0f)
                damage.animateTo(1f, tween(500, easing = LinearEasing))
                consumed++
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        if (confetti.value < 1f) {
            Canvas(Modifier.fillMaxSize().testTag("mini_game_confetti")) {
                val colors = listOf(Color(0xFF8B5CF6), Color(0xFFFFC857), Color(0xFF29C7D4),
                    Color(0xFFFF6388), Color(0xFF65D886), Color(0xFFFF964F))
                val seconds = confetti.value * 2f
                repeat(160) { i ->
                    val side = if (i % 2 == 0) 1f else -1f
                    val age = seconds - (i % 13) * 0.027f
                    if (age < 0f) return@repeat
                    val fraction = ((i * 37 % 101) / 100f)
                    val x = (if (side > 0f) 0f else size.width) + side * size.width * (0.36f + fraction * 0.4f) * age
                    val y = size.height * 0.72f - size.height * (0.65f + (i * 17 % 79) / 100f) * age + size.height * 0.64f * age * age
                    val alpha = ((1.8f - age) / 0.55f).coerceIn(0f, 1f)
                    val center = Offset(x, y)
                    rotate(i * 23f + age * (180f + fraction * 360f), center) {
                        drawRect(colors[i % colors.size].copy(alpha = alpha), center, Size(5.dp.toPx(), 9.dp.toPx()))
                    }
                }
            }
        }
        if (damage.value < 1f) {
            val p = damage.value
            val fade = (1f - p) * (1f - p)
            Canvas(Modifier.fillMaxSize().testTag("mini_game_damage")) {
                val red = Color(0xFFED173A).copy(alpha = fade * (0.4f + 0.5f * abs(cos(p * Math.PI.toFloat() * 3f))))
                val edge = size.minDimension * 0.15f
                drawRect(Brush.horizontalGradient(listOf(red, Color.Transparent), 0f, edge), size = Size(edge, size.height))
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, red), size.width - edge, size.width),
                    Offset(size.width - edge, 0f), Size(edge, size.height))
                drawRect(Brush.verticalGradient(listOf(red, Color.Transparent), 0f, edge), size = Size(size.width, edge))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, red), size.height - edge, size.height),
                    Offset(0f, size.height - edge), Size(size.width, edge))
            }
            Text("-1", color = Color(0xFFED173A), fontSize = 72.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center).testTag("mini_game_life_lost").graphicsLayer {
                    alpha = fade
                    scaleX = 1f + p * 0.2f
                    scaleY = scaleX
                    translationY = -24.dp.toPx() * p
                })
        }
    }
}
