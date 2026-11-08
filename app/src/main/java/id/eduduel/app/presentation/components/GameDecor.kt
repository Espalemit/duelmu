package id.eduduel.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Small, native decoration: no downloaded assets and no perpetual animation during a quiz. */
@Composable
fun ConfettiDecoration(modifier: Modifier = Modifier, progress: Float = 1f) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error)
    Canvas(modifier) {
        repeat(24) { i ->
            val x = size.width * ((i * 37 + 7) % 100) / 100f
            val y = size.height * ((i * 29 + 11) % 100) / 100f * progress
            val center = Offset(x, y)
            val color = colors[i % colors.size].copy(alpha = 0.30f + (i % 3) * 0.12f)
            if (i % 3 == 0) {
                drawCircle(color, 3.dp.toPx(), center)
            } else {
                rotate(i * 23f, center) {
                    drawRoundRect(color, center, Size(4.dp.toPx(), 9.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun ResultCelebration(title: String, subtitle: String, celebrate: Boolean = true) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(750)) }
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(cs.tertiaryContainer, cs.primaryContainer)), RoundedCornerShape(26.dp),
        ),
    ) {
        if (celebrate) ConfettiDecoration(Modifier.matchParentSize(), entrance.value)
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(80.dp).graphicsLayer {
                    scaleX = 0.7f + entrance.value * 0.3f
                    scaleY = scaleX
                    rotationZ = -12f * (1f - entrance.value)
                }.background(cs.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (celebrate) Icons.Filled.EmojiEvents else Icons.Filled.Star,
                    null, Modifier.size(46.dp), tint = cs.tertiary)
            }
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                color = cs.onBackground, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = cs.onBackground,
                textAlign = TextAlign.Center)
        }
    }
}
