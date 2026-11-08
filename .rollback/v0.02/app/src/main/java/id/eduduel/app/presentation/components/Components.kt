package id.eduduel.app.presentation.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import id.eduduel.app.presentation.components.GameButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import id.eduduel.app.presentation.components.GameIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.eduduel.app.R
import id.eduduel.app.core.MathText
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.core.NicknameError
import id.eduduel.app.core.NicknameValidation
import id.eduduel.app.core.NicknameValidator
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.presentation.theme.EduDuelTheme
import id.eduduel.app.presentation.theme.LocalFeedbackColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = { actions() },
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        content = content,
    )
}

/** Kolom konten yang dapat di-scroll dengan gutter standar. */
@Composable
fun ScrollColumn(padding: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun MessageState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isError) Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Teks soal/opsi dengan fallback rendering pangkat Unicode (tanpa jaringan). */
@Composable
fun MathAwareText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(MathText.format(text), modifier = modifier, style = style)
}

enum class OptionVisual { NORMAL, SELECTED, CORRECT, WRONG, DIMMED }

@Composable
fun OptionButton(
    label: String,
    text: String,
    visual: OptionVisual,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressSource = rememberPressSource()
    val fb = LocalFeedbackColors.current
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (visual) {
            OptionVisual.CORRECT -> fb.correctContainer
            OptionVisual.WRONG -> fb.wrongContainer
            OptionVisual.SELECTED -> cs.primaryContainer
            OptionVisual.DIMMED -> cs.surfaceContainer
            OptionVisual.NORMAL -> cs.surface
        },
        label = "optionColor",
    )
    val content = when (visual) {
        OptionVisual.CORRECT -> fb.onCorrectContainer
        OptionVisual.WRONG -> fb.onWrongContainer
        OptionVisual.SELECTED -> cs.onPrimaryContainer
        OptionVisual.DIMMED -> cs.onSurface.copy(alpha = 0.6f)
        OptionVisual.NORMAL -> cs.onSurface
    }
    val border = when (visual) {
        OptionVisual.CORRECT -> fb.correct
        OptionVisual.WRONG -> fb.wrong
        OptionVisual.SELECTED -> cs.primary
        else -> cs.outlineVariant
    }
    val stateText = when (visual) {
        OptionVisual.CORRECT -> stringResource(R.string.option_state_correct)
        OptionVisual.WRONG -> stringResource(R.string.option_state_wrong)
        OptionVisual.SELECTED -> stringResource(R.string.option_state_selected)
        else -> ""
    }
    val a11y = stringResource(R.string.option_a11y, label, MathText.format(text))
    Surface(
        onClick = playfulClick(onClick),
        interactionSource = pressSource,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(if (visual == OptionVisual.NORMAL || visual == OptionVisual.DIMMED) 1.dp else 2.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .playfulPress(pressSource)
            .semantics(mergeDescendants = true) {
                contentDescription = a11y
                if (stateText.isNotEmpty()) stateDescription = stateText
            },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = border.copy(alpha = 0.15f), modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontWeight = FontWeight.Bold, color = content)
                }
            }
            Spacer(Modifier.width(12.dp))
            MathAwareText(text, Modifier.weight(1f), MaterialTheme.typography.bodyLarge)
            when (visual) {
                OptionVisual.CORRECT -> Icon(Icons.Filled.CheckCircle, null, tint = fb.correct)
                OptionVisual.WRONG -> Icon(Icons.Filled.Cancel, null, tint = fb.wrong)
                else -> Unit
            }
        }
    }
}

/**
 * Bilah waktu berbasis jam monotonic. Waktu mulai berasal dari state (ViewModel/sesi), sehingga
 * recomposition/rotasi tidak me-reset timer.
 */
@Composable
fun TimerBar(startedAtMs: Long, durationMs: Long, clock: MonotonicClock, running: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(clock.nowMs()) }
    LaunchedEffect(startedAtMs, running) {
        while (running) {
            now = clock.nowMs()
            delay(100)
        }
        now = clock.nowMs()
    }
    val remaining = (durationMs - (now - startedAtMs)).coerceIn(0, durationMs)
    val fraction = if (durationMs > 0) remaining.toFloat() / durationMs else 0f
    val seconds = ((remaining + 999) / 1000).toInt()
    val color = when {
        fraction > 0.5f -> MaterialTheme.colorScheme.secondary
        fraction > 0.2f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val desc = stringResource(R.string.time_left, seconds)
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            modifier = Modifier.weight(1f).heightIn(min = 10.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.seconds_short, seconds),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 56.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun nicknameErrorText(error: NicknameError?): String? = when (error) {
    null -> null
    NicknameError.EMPTY -> stringResource(R.string.name_error_empty)
    NicknameError.TOO_SHORT -> stringResource(R.string.name_error_short)
    NicknameError.TOO_LONG -> stringResource(R.string.name_error_long)
    NicknameError.INVALID_CHARACTERS -> stringResource(R.string.name_error_chars)
}

/** Kolom nickname dengan batas grapheme aman-emoji dan pesan validasi berbahasa Indonesia. */
@Composable
fun NicknameField(
    value: String,
    onValueChange: (String) -> Unit,
    showError: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val validation = NicknameValidator.validate(value)
    val error = (validation as? NicknameValidation.Invalid)?.error
    val count = NicknameValidator.graphemeCount(NicknameValidator.normalize(value))
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(NicknameValidator.sanitizeInput(it)) },
        label = { Text(stringResource(R.string.duel_name_label)) },
        placeholder = { Text(stringResource(R.string.duel_name_hint)) },
        singleLine = true,
        isError = showError && error != null,
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (showError) nicknameErrorText(error).orEmpty() else "",
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.duel_name_counter, count, NicknameValidator.MAX_GRAPHEMES))
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Umpan balik haptics/suara opsional sesuai Pengaturan. */
class Feedback(private val haptic: (HapticFeedbackType) -> Unit, private val tone: ToneGenerator?, private val settings: AppSettings) {
    fun tap() {
        if (settings.hapticsEnabled) haptic(HapticFeedbackType.TextHandleMove)
        if (settings.soundEnabled) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 35) }
    }

    fun result(correct: Boolean) {
        if (settings.hapticsEnabled) haptic(if (correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        if (settings.soundEnabled) runCatching {
            tone?.startTone(if (correct) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 150)
        }
    }
}

@Composable
fun rememberFeedback(settings: AppSettings): Feedback {
    val haptic = LocalHapticFeedback.current
    val tone = remember(settings.soundEnabled) {
        if (settings.soundEnabled) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull() else null
    }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    return remember(settings, haptic, tone) { Feedback({ haptic.performHapticFeedback(it) }, tone, settings) }
}

/** Badge peran koneksi. */
@Composable
fun RoleBadge(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionButtonPreview() {
    EduDuelTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionButton("A", "6,02 × 10²³ entitas", OptionVisual.CORRECT, true, {})
            OptionButton("B", "10⁻² mol/L", OptionVisual.WRONG, true, {})
            OptionButton("C", "x² + 2x", OptionVisual.NORMAL, true, {})
            OptionButton("D", "H₂O", OptionVisual.DIMMED, false, {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NicknamePreview() {
    EduDuelTheme { Box(Modifier.padding(16.dp)) { NicknameField("Rina ⚡", {}, true, {}) } }
}
