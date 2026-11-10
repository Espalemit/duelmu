package id.eduduel.app.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import id.eduduel.app.presentation.components.GameIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import id.eduduel.app.presentation.components.GameTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.R
import id.eduduel.app.data.history.DuelStats
import id.eduduel.app.data.history.HistoryRepository
import id.eduduel.app.data.history.MatchRecordEntity
import id.eduduel.app.data.history.RecordMode
import id.eduduel.app.data.history.RecordOutcome
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.MessageState
import id.eduduel.app.presentation.components.RoleBadge
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme
import id.eduduel.app.presentation.theme.LocalFeedbackColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val records by container.history.records.collectAsStateWithLifecycle(emptyList())
    val stats by container.history.duelStats.collectAsStateWithLifecycle(DuelStats(0, 0, 0, 0))
    val scope = rememberCoroutineScope()
    var confirm by rememberSaveable { mutableStateOf(false) }
    AppScaffold(stringResource(R.string.history_title), onBack, actions = {
        if (records.isNotEmpty()) IconButton(onClick = { confirm = true }) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
        }
    }) { padding ->
        HistoryContent(records, stats, Modifier.padding(padding))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = { TextButton(onClick = { confirm = false; scope.launch { container.history.clear() } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun HistoryContent(records: List<MatchRecordEntity>, stats: DuelStats, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StatsCard(stats) }
        item { Text(stringResource(R.string.history_local_note), style = MaterialTheme.typography.bodySmall) }
        if (records.isEmpty()) {
            item { MessageState(stringResource(R.string.history_title), stringResource(R.string.history_empty)) }
        }
        items(records, key = { it.id }) { RecordRow(it) }
    }
}

@Composable
private fun StatsCard(s: DuelStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_stats_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.history_wins), s.wins, Modifier.weight(1f))
                Stat(stringResource(R.string.history_losses), s.losses, Modifier.weight(1f))
                Stat(stringResource(R.string.history_draws), s.draws, Modifier.weight(1f))
                Stat(stringResource(R.string.history_cancelled), s.cancelled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecordRow(r: MatchRecordEntity) {
    val fb = LocalFeedbackColors.current
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.playedAtEpochMs))
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (label, color) = when (r.outcome) {
                    RecordOutcome.WIN -> stringResource(R.string.history_wins) to fb.correctContainer
                    RecordOutcome.LOSE -> stringResource(R.string.history_losses) to fb.wrongContainer
                    RecordOutcome.DRAW -> stringResource(R.string.history_draws) to MaterialTheme.colorScheme.secondaryContainer
                    RecordOutcome.CANCELLED -> stringResource(R.string.history_cancelled) to MaterialTheme.colorScheme.surfaceVariant
                    RecordOutcome.PRACTICE -> stringResource(R.string.home_solo_title) to MaterialTheme.colorScheme.tertiaryContainer
                }
                RoleBadge(label, color)
                Text(date, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            if (r.mode == RecordMode.DUEL) {
                // Nickname SNAPSHOT saat pertandingan tersebut (bukan profil akun).
                val participants = HistoryRepository.decodeParticipants(r.participantsJson)
                if (participants.isNotEmpty()) {
                    participants.sortedBy { it.rank }.forEach { p ->
                        Text("#${p.rank}  ${p.nickname}${if (p.isMe) " (${stringResource(R.string.you)})" else ""} · ${p.score}",
                            style = if (p.isMe) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(stringResource(R.string.history_vs, r.myNickname ?: "—", r.opponentNickname ?: "—"), style = MaterialTheme.typography.titleMedium)
                }
                if (r.myRank != null) {
                    Text(stringResource(R.string.history_rank, r.myRank, r.playerCount, r.myScore), style = MaterialTheme.typography.titleSmall)
                } else {
                    Text(stringResource(R.string.history_score, r.myScore), style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Text(stringResource(R.string.history_solo_entry, r.correctCount, r.questionCount), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.points_short, r.myScore), style = MaterialTheme.typography.bodyLarge)
            }
            Text(r.configSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryPreview() {
    val recs = listOf(
        MatchRecordEntity("1", RecordMode.DUEL, 1_762_500_000_000, "Rina ⚡", "Budi, Sari", "HOST", 1840, 1520, RecordOutcome.WIN, 8, 10,
            "Fisika · 10 soal", "[]", "2026.11.08-r1", myRank = 1, playerCount = 3,
            participantsJson = """[{"nickname":"Rina ⚡","score":1840,"rank":1,"isMe":true},{"nickname":"Budi","score":1520,"rank":2},{"nickname":"Sari","score":900,"rank":3}]"""),
        MatchRecordEntity("2", RecordMode.SOLO, 1_762_400_000_000, null, null, null, 900, null, RecordOutcome.PRACTICE, 4, 10,
            "Campuran · 10 soal", "[]", "2026.11.08-r1"),
    )
    EduDuelTheme { HistoryContent(recs, DuelStats(3, 1, 1, 0)) }
}
