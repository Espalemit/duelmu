package id.eduduel.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** One feedback owner for the whole activity; preferences also apply to menus and dialogs. */
val LocalInteractionFeedback = staticCompositionLocalOf<Feedback?> { null }

@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Uses the control's own interaction source, preserving scrolling, disabled states and TalkBack. */
@Composable
fun Modifier.playfulPress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "buttonPress",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun playfulClick(onClick: () -> Unit): () -> Unit {
    val feedback = LocalInteractionFeedback.current
    return { feedback?.tap(); onClick() }
}

@Composable
fun GameButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    val source = rememberPressSource()
    Button(
        onClick = playfulClick(onClick), enabled = enabled, colors = colors,
        interactionSource = source, shape = RoundedCornerShape(18.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp),
        modifier = modifier.heightIn(min = 52.dp).playfulPress(source), content = content,
    )
}

@Composable
fun GameOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = rememberPressSource()
    OutlinedButton(
        onClick = playfulClick(onClick), enabled = enabled, interactionSource = source,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.heightIn(min = 48.dp).playfulPress(source), content = content,
    )
}

@Composable
fun GameTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = rememberPressSource()
    TextButton(onClick = playfulClick(onClick), enabled = enabled, interactionSource = source,
        modifier = modifier.heightIn(min = 48.dp).playfulPress(source), content = content)
}

@Composable
fun GameIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = rememberPressSource()
    IconButton(onClick = playfulClick(onClick), enabled = enabled, interactionSource = source,
        modifier = modifier.playfulPress(source), content = content)
}

@Composable
fun GameFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val source = rememberPressSource()
    FilterChip(
        selected = selected, onClick = playfulClick(onClick), label = label, enabled = enabled,
        interactionSource = source, leadingIcon = leadingIcon, shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier.heightIn(min = 48.dp).playfulPress(source),
    )
}
