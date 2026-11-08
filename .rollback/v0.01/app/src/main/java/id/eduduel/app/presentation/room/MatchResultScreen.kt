package id.eduduel.app.presentation.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.eduduel.app.R
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.MatchOutcome
import id.eduduel.app.domain.OptionId
import id.eduduel.app.net.protocol.EndReason
import id.eduduel.app.net.room.ActiveRoom
import id.eduduel.app.net.room.MatchSummary
import id.eduduel.app.net.room.PlayerRoundResult
import id.eduduel.app.net.room.RoomFailure
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.RoomPlayer
import id.eduduel.app.net.room.RoomRole
import id.eduduel.app.net.room.RoomState
import id.eduduel.app.net.room.RoundSummary
import id.eduduel.app.net.room.Standing
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.InfoCard
import id.eduduel.app.presentation.components.MathAwareText
import id.eduduel.app.presentation.components.PreviewData
import id.eduduel.app.presentation.components.PrimaryButton
import id.eduduel.app.presentation.components.RoleBadge
import id.eduduel.app.presentation.components.ScrollColumn
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme
import id.eduduel.app.presentation.theme.LocalFeedbackColors

@Composable
fun MatchResultScreen(onHome: () -> Unit, onLobby: () -> Unit) {
    val coordinator = LocalAppContainer.current.room
    val (room, state) = rememberRoom()
    val home = { coordinator.endSession(); onHome() }
    BackHandler(onBack = home)
    LaunchedEffect(room) { if (room == null) onHome() }
    AppScaffold(stringResource(R.string.result_title), onBack = home) { padding ->
        val st = state ?: return@AppScaffold
        ScrollColumn(padding) {
            MatchResultContent(
                state = st,
                onPlayAgain = { (room as? ActiveRoom.Host)?.session?.backToRoom(); onLobby() },
                onBackToRoom = onLobby,
                onHome = home,
            )
        }
    }
}

@Composable
fun MatchResultContent(state: RoomState, onPlayAgain: () -> Unit, onBackToRoom: () -> Unit, onHome: () -> Unit) {
    val fb = LocalFeedbackColors.current
    val summary = state.lastMatch
    if (summary == null) {
        InfoCard {
            Text(stringResource(R.string.fail_title), style = MaterialTheme.typography.titleLarge)
            Text(failureText(state.failure))
        }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go_home)) }
        return
    }
    val meId = state.me.sessionId
    val standings = summary.standings
    val mine = standings.firstOrNull { it.player.sessionId == meId }
    val (headline, color) = when (summary.outcomeFor(meId)) {
        MatchOutcome.WIN -> stringResource(R.string.result_win) to fb.correct
        MatchOutcome.DRAW -> stringResource(R.string.result_draw) to MaterialTheme.colorScheme.primary
        MatchOutcome.LOSE -> stringResource(R.string.result_rank, mine?.rank ?: 0, standings.size) to MaterialTheme.colorScheme.onSurface
        null -> stringResource(R.string.result_cancelled) to fb.wrong
    }
    Text(headline, style = MaterialTheme.typography.headlineMedium, color = color, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() })
    Text(summary.config.subject?.label ?: stringResource(R.string.config_mixed), style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

    if (!summary.completed) {
        val reason = when {
            summary.failure == RoomFailure.NOT_ENOUGH_PLAYERS -> stringResource(R.string.result_cancel_players)
            summary.endReason == EndReason.CANCELLED_DISCONNECT -> stringResource(R.string.result_cancel_disconnect)
            summary.endReason == EndReason.CANCELLED_BY_PLAYER -> stringResource(R.string.result_cancel_player)
            summary.endReason == EndReason.CANCELLED_ERROR -> stringResource(R.string.result_cancel_error) + " " + failureText(summary.failure)
            else -> failureText(summary.failure)
        }
        InfoCard { Text(reason) }
    }

    Text(stringResource(R.string.result_standings), style = MaterialTheme.typography.titleLarge)
    standings.forEach { StandingRow(it, isMe = it.player.sessionId == meId) }

    // ---- lanjut main ----
    when {
        state.role == RoomRole.HOST && state.phase in setOf(RoomPhase.FINISHED, RoomPhase.CANCELLED) -> {
            PrimaryButton(stringResource(R.string.result_play_again), onPlayAgain)
            Text(stringResource(R.string.result_play_again_hint), style = MaterialTheme.typography.bodySmall)
        }
        state.role == RoomRole.HOST && state.phase == RoomPhase.LOBBY -> PrimaryButton(stringResource(R.string.result_back_to_room), onBackToRoom)
        state.role == RoomRole.GUEST && state.phase == RoomPhase.LOBBY && state.connected -> {
            Text(stringResource(R.string.result_host_reopened), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            PrimaryButton(stringResource(R.string.result_back_to_room), onBackToRoom)
        }
        state.role == RoomRole.GUEST && state.connected && state.phase == RoomPhase.FINISHED ->
            Text(stringResource(R.string.result_waiting_host), style = MaterialTheme.typography.bodyMedium)
        state.transport != TransportKind.DEMO -> Text(stringResource(R.string.result_disconnected), style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go_home)) }

    if (summary.results.isNotEmpty()) {
        Text(stringResource(R.string.result_breakdown), style = MaterialTheme.typography.titleLarge)
        summary.results.forEach { r -> RoundRow(r, summary) }
    }
}

@Composable
private fun StandingRow(st: Standing, isMe: Boolean) {
    val fb = LocalFeedbackColors.current
    val you = stringResource(R.string.you)
    val correct = stringResource(R.string.result_correct_count, st.correct)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                st.rank == 1 -> fb.correctContainer
                isMe -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "#${st.rank} ${st.player.nickname}${if (isMe) " ($you)" else ""}, ${st.total} poin, $correct"
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.result_rank_short, st.rank), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(st.player.nickname, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (isMe) RoleBadge(you)
                    if (!st.player.connected) RoleBadge(stringResource(R.string.lobby_disconnected), MaterialTheme.colorScheme.errorContainer)
                }
                Text(correct, style = MaterialTheme.typography.bodySmall)
            }
            Text("${st.total}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundRow(r: RoundSummary, summary: MatchSummary) {
    val fb = LocalFeedbackColors.current
    InfoCard {
        Row {
            Text(stringResource(R.string.result_round, r.index + 1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${r.round.question.subject.label} · ${r.round.question.gradeBand.name} · ${r.round.question.difficulty.label}",
                style = MaterialTheme.typography.labelSmall)
        }
        MathAwareText(r.round.question.stem, style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            summary.participants.forEach { p ->
                val pr = r.of(p.sessionId) ?: return@forEach
                PlayerLine(p.nickname, pr, r, fb.correct, fb.wrong)
            }
        }
    }
}

@Composable
private fun PlayerLine(name: String, p: PlayerRoundResult, r: RoundSummary, good: androidx.compose.ui.graphics.Color, bad: androidx.compose.ui.graphics.Color) {
    val answer = p.optionId?.let { r.round.question.option(it).text }
    val time = if (!p.timedOut && !p.absent) " · " + stringResource(R.string.duel_answered_in, "%.1f".format(p.elapsedMs / 1000.0)) else ""
    Text("$name: ${playerResultText(p)}$time" + (answer?.let { " — $it" } ?: ""), color = if (p.correct) good else bad,
        style = MaterialTheme.typography.bodyMedium)
}

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun MatchResultPreview() {
    val players = listOf(RoomPlayer("a", "Rina ⚡", true), RoomPlayer("b", "Budi", false), RoomPlayer("c", "Sari", false, connected = false))
    val r = RoundSummary(
        0, PreviewData.round, OptionId.A,
        mapOf(
            "a" to PlayerRoundResult(OptionId.A, 9_000, true, 389, false),
            "b" to PlayerRoundResult(OptionId.A, 20_000, true, 375, false),
            "c" to PlayerRoundResult.ABSENT,
        ),
    )
    val summary = MatchSummary("m", MatchConfig.INITIAL, players, listOf(r), 10, EndReason.COMPLETED, null)
    val st = RoomState(
        role = RoomRole.GUEST, transport = TransportKind.LAN, me = players[1], phase = RoomPhase.LOBBY, players = players,
        lastMatch = summary, connected = true,
    )
    EduDuelTheme { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { MatchResultContent(st, {}, {}, {}) } }
}
