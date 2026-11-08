package id.eduduel.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.eduduel.app.R
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.Subject
import id.eduduel.app.presentation.theme.EduDuelTheme

/**
 * Pemilih aturan: HANYA mata pelajaran (atau Campuran). Jenjang & kesulitan bukan pilihan;
 * keduanya tampil sebagai label di tiap soal. Setiap pertandingan = 10 soal acak.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectPicker(bank: QuestionBank?, config: MatchConfig, onChange: (MatchConfig) -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Subject.entries.forEach { s ->
                ChoiceChip(s.label, config.subject == s, enabled) { onChange(MatchConfig(s)) }
            }
            ChoiceChip(stringResource(R.string.config_mixed), config.subject == null, enabled) { onChange(MatchConfig(null)) }
        }
        ConfigNotes(bank, config)
    }
}

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
        Text(config.subject?.label ?: stringResource(R.string.config_mixed), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        ConfigNotes(bank, config)
    }
}

@Preview(showBackground = true)
@Composable
private fun SubjectPickerPreview() {
    EduDuelTheme { Column(Modifier.padding(16.dp)) { SubjectPicker(null, MatchConfig(Subject.MATEMATIKA), {}) } }
}
