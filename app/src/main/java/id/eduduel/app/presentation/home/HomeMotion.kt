package id.eduduel.app.presentation.home

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One shared clock; only draw/layer properties read it, so the menu does not recompose per frame. */
internal class HomeMotion(val phase: State<Float>, val enabled: Boolean) {
    fun wave(offset: Float = 0f): Float = if (enabled) sin(phase.value * (2f * PI.toFloat()) + offset) else 0f
}

@Composable
internal fun rememberHomeMotion(motionEnabled: Boolean): HomeMotion {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var enabled by remember(preview, motionEnabled) { mutableStateOf(motionEnabled && !preview && ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(lifecycle, preview, motionEnabled) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            enabled = motionEnabled && !preview && ValueAnimator.areAnimatorsEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val phase = if (active && enabled) {
        rememberInfiniteTransition(label = "homeAmbient").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
            label = "homePhase",
        )
    } else rememberUpdatedState(0f)
    return remember(phase, enabled) { HomeMotion(phase, enabled) }
}

/** A quick staggered welcome; content keeps its final layout bounds and remains scrollable. */
@Composable
internal fun HomeEntrance(
    order: Int,
    motion: HomeMotion,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val progress = remember { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(motion.enabled) {
        if (motion.enabled) {
            delay(order * 45L)
            progress.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        } else progress.snapTo(1f)
    }
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 20.dp.toPx()
        scaleX = 0.98f + progress.value * 0.02f
        scaleY = scaleX
    }, content = content)
}

/** Subtle movement stays at the edges of the hero, keeping its text clear. */
@Composable
internal fun HomeHeroBackdrop(motion: HomeMotion, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.secondary, cs.tertiary, cs.error)
    Canvas(modifier) {
        val angle = motion.phase.value * (2f * PI.toFloat())
        val glow = Offset(size.width * 0.91f + sin(angle) * 12.dp.toPx(), size.height * 0.15f)
        drawCircle(Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.10f), Color.Transparent),
            center = glow, radius = 100.dp.toPx()), 100.dp.toPx(), glow)
        repeat(18) { i ->
            val right = i % 2 == 0
            val x = size.width * (if (right) 0.90f + (i % 4) * 0.02f else 0.015f + (i % 4) * 0.018f)
            val y = size.height * ((i * 29 + 13) % 100) / 100f
            val center = Offset(x + sin(angle + i) * 5.dp.toPx(), y + cos(angle + i * 0.7f) * 9.dp.toPx())
            val color = colors[i % colors.size].copy(alpha = 0.26f + sin(angle + i) * 0.08f)
            rotate(i * 31f + sin(angle + i) * 25f, center) {
                if (i % 3 == 0) {
                    val arm = (3.5f + sin(angle + i)) * density
                    drawLine(color, center - Offset(arm, 0f), center + Offset(arm, 0f), 1.8.dp.toPx())
                    drawLine(color, center - Offset(0f, arm), center + Offset(0f, arm), 1.8.dp.toPx())
                } else if (i % 3 == 1) {
                    drawCircle(color, 2.6.dp.toPx(), center)
                } else {
                    drawRoundRect(color, center, Size(3.dp.toPx(), 7.dp.toPx()))
                }
            }
        }
    }
}

@Composable
internal fun FloatingTrophy(motion: HomeMotion, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(cs.primary.copy(alpha = 0.18f), size.minDimension * 0.47f, style = Stroke(1.dp.toPx()))
            val angle = motion.phase.value * (2f * PI.toFloat())
            drawCircle(cs.tertiary, 2.8.dp.toPx(), Offset(
                size.width / 2 + cos(angle) * size.width * 0.47f,
                size.height / 2 + sin(angle) * size.height * 0.47f,
            ))
        }
        Box(Modifier.size(39.dp).graphicsLayer {
            translationY = motion.wave() * 3.dp.toPx()
            rotationZ = motion.wave(0.8f) * 7f
        }.background(cs.surface.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.EmojiEvents, null, Modifier.size(26.dp), tint = cs.tertiary)
        }
    }
}
