package id.eduduel.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import id.eduduel.app.presentation.components.GameFilterChip as FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.eduduel.app.R
import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.QuestionMode
import id.eduduel.app.domain.Subject
import id.eduduel.app.presentation.theme.EduDuelTheme

/**
 * Pemilih aturan: mata pelajaran (atau Campuran) + jenis soal. Jenjang & kesulitan bukan pilihan;
 * keduanya tampil sebagai label di tiap soal. Setiap pertandingan = 10 soal acak.
 * [showGhostMode] = pengaturan ghost LIVE/FAIR (hanya Host room).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectPicker(
    bank: QuestionBank?,
    config: MatchConfig,
    onChange: (MatchConfig) -> Unit,
    enabled: Boolean = true,
    showGhostMode: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Subject.entries.forEach { s ->
                ChoiceChip(s.label, config.subject == s, enabled) { onChange(config.copy(subject = s)) }
            }
            ChoiceChip(stringResource(R.string.config_mixed), config.subject == null, enabled) { onChange(config.copy(subject = null)) }
        }
        Text(stringResource(R.string.config_mode), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QuestionMode.entries.forEach { m ->
                ChoiceChip(m.label, config.mode == m, enabled) { onChange(config.copy(mode = m)) }
            }
        }
        Text(modeDescription(config.mode), style = MaterialTheme.typography.bodySmall)
        if (showGhostMode && config.mode in setOf(QuestionMode.GAME, QuestionMode.MIXED)) {
            Text(stringResource(R.string.config_ghost), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.config_ghost_live), config.ghostMode == GhostMode.LIVE, enabled) { onChange(config.copy(ghostMode = GhostMode.LIVE)) }
                ChoiceChip(stringResource(R.string.config_ghost_fair), config.ghostMode == GhostMode.FAIR, enabled) { onChange(config.copy(ghostMode = GhostMode.FAIR)) }
            }
            Text(ghostDescription(config.ghostMode), style = MaterialTheme.typography.bodySmall)
        }
        ConfigNotes(bank, config)
    }
}

@Composable
fun modeDescription(mode: QuestionMode): String = stringResource(
    when (mode) {
        QuestionMode.CLASSIC -> R.string.config_mode_classic_desc
        QuestionMode.VISUAL -> R.string.config_mode_visual_desc
        QuestionMode.GAME -> R.string.config_mode_game_desc
        QuestionMode.MIXED -> R.string.config_mode_mixed_desc
    },
)

@Composable
fun ghostDescription(mode: GhostMode): String =
    stringResource(if (mode == GhostMode.LIVE) R.string.config_ghost_live_desc else R.string.config_ghost_fair_desc)

@Composable
private fun ConfigNotes(bank: QuestionBank?, config: MatchConfig) {
    val poolSize = bank?.pool(config)?.size
    val subjectName = config.subject?.label ?: stringResource(R.string.config_mixed_name)
    if (poolSize != null) {
        Text(stringResource(R.string.config_subject_hint, config.questionCount, poolSize, subjectName), style = MaterialTheme.typography.bodyMedium)
    }
    Text(stringResource(R.string.config_labels_note), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.config_time_rule), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, null) }) else null,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** Ringkasan aturan untuk Tamu (hanya baca). */
@Composable
fun ConfigSummary(bank: QuestionBank?, config: MatchConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${config.subject?.label ?: stringResource(R.string.config_mixed)} · ${config.mode.label}", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(modeDescription(config.mode), style = MaterialTheme.typography.bodySmall)
        if (config.mode in setOf(QuestionMode.GAME, QuestionMode.MIXED)) {
            Text("${stringResource(R.string.config_ghost)}: ${config.ghostMode.name}", style = MaterialTheme.typography.titleSmall)
            Text(ghostDescription(config.ghostMode), style = MaterialTheme.typography.bodySmall)
        }
        ConfigNotes(bank, config)
    }
}

@Preview(showBackground = true)
@Composable
private fun SubjectPickerPreview() {
    EduDuelTheme { Column(Modifier.padding(16.dp)) { SubjectPicker(null, MatchConfig(Subject.MATEMATIKA, QuestionMode.GAME), {}, showGhostMode = true) } }
}
