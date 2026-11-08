package id.eduduel.app.presentation.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.R
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.domain.OptionId
import id.eduduel.app.net.room.ActiveRoom
import id.eduduel.app.net.room.ActiveRound
import id.eduduel.app.net.room.PlayerRoundResult
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.RoomPlayer
import id.eduduel.app.net.room.RoomRole
import id.eduduel.app.net.room.RoomState
import id.eduduel.app.net.room.RoundSummary
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.ExplanationCard
import id.eduduel.app.presentation.components.InfoCard
import id.eduduel.app.presentation.components.OptionButton
import id.eduduel.app.presentation.components.PreviewData
import id.eduduel.app.presentation.components.PrimaryButton
import id.eduduel.app.presentation.components.QuestionCard
import id.eduduel.app.presentation.components.ScrollColumn
import id.eduduel.app.presentation.components.TimerBar
import id.eduduel.app.presentation.components.labelOf
import id.eduduel.app.presentation.components.optionVisual
import id.eduduel.app.presentation.components.positionLabel
import id.eduduel.app.presentation.components.rememberFeedback
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme
import id.eduduel.app.presentation.theme.LocalFeedbackColors
import kotlinx.coroutines.delay

@Composable
fun MatchPlayScreen(onExit: () -> Unit, onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val coordinator = container.room
    val (room, state) = rememberRoom()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val feedback = rememberFeedback(settings)
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(room) { if (room == null) onExit() }
    LaunchedEffect(state?.phase) {
        if (state?.phase in setOf(RoomPhase.FINISHED, RoomPhase.CANCELLED, RoomPhase.FAILED)) onFinished()
    }
    val resultsCount = state?.results?.size ?: 0
    LaunchedEffect(resultsCount) {
        val me = state?.me?.sessionId ?: return@LaunchedEffect
        state.results.lastOrNull()?.of(me)?.takeIf { !it.absent }?.let { feedback.result(it.correct) }
    }
    RoomNotices(state, snackbar)
    BackHandler { confirmLeave = true }

    AppScaffold(stringResource(R.string.home_duel_title), onBack = { confirmLeave = true }, snackbarHostState = snackbar) { padding ->
        val st = state ?: return@AppScaffold
        ScrollColumn(padding) {
            MatchPlayContent(
                state = st,
                clock = container.clock,
                onAnswer = { opt ->
                    feedback.tap()
                    when (room) {
                        is ActiveRoom.Host -> room.session.answer(opt)
                        is ActiveRoom.Guest -> room.session.answer(opt)
                        null -> Unit
                    }
                },
                onNext = {
                    when (room) {
                        is ActiveRoom.Host -> room.session.requestNext()
                        is ActiveRoom.Guest -> room.session.requestNext()
                        null -> Unit
                    }
                },
            )
        }
    }
    if (confirmLeave) {
        val host = state?.role == RoomRole.HOST
        ConfirmDialog(
            title = stringResource(R.string.duel_leave_title),
            body = stringResource(if (host) R.string.duel_leave_body_host else R.string.duel_leave_body_guest),
            confirm = stringResource(R.string.leave),
            onConfirm = { confirmLeave = false; coordinator.endSession(); onExit() },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
fun MatchPlayContent(state: RoomState, clock: MonotonicClock, onAnswer: (OptionId) -> Unit, onNext: () -> Unit) {
    val roundNumber = when (state.phase) {
        RoomPhase.QUESTION -> state.activeRound?.index
        RoomPhase.REVEAL -> state.results.lastOrNull()?.index
        else -> null
    }
    ScoreBoard(state.standings, state.me.sessionId, roundNumber?.let { stringResource(R.string.round_of, it + 1, state.totalRounds) })
    when (state.phase) {
        RoomPhase.STARTING -> Waiting(stringResource(R.string.duel_starting))
        RoomPhase.PREPARING -> Waiting(stringResource(R.string.duel_preparing))
        RoomPhase.QUESTION -> state.activeRound?.let { ActiveQuestion(it, state, clock, onAnswer) }
        RoomPhase.REVEAL -> {
            val last = state.results.lastOrNull()
            if (last == null) Waiting(stringResource(R.string.duel_preparing))
            else Reveal(last, state, clock, onNext)
        }
        RoomPhase.RECONNECTING -> Reconnecting(state.reconnectDeadlineMs, clock)
        else -> Waiting(stringResource(R.string.loading))
    }
}

@Composable
private fun Waiting(text: String) {
    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun ActiveQuestion(ar: ActiveRound, state: RoomState, clock: MonotonicClock, onAnswer: (OptionId) -> Unit) {
    QuestionCard(ar.round, ar.index, state.totalRounds)
    TimerBar(ar.startedAtMs, ar.durationMs, clock, running = !ar.isLocked)
    ar.round.displayedOptions.forEachIndexed { i, opt ->
        OptionButton(
            label = positionLabel(i),
            text = opt.text,
            visual = optionVisual(opt.id, ar.myAnswer, null),
            enabled = !ar.isLocked,
            onClick = { onAnswer(opt.id) },
        )
    }
    val connected = state.participants.count { p -> state.players.firstOrNull { it.sessionId == p.sessionId }?.connected != false }
    val locked = ar.lockedPlayers.count { id -> state.participants.any { it.sessionId == id } }
    val status = when {
        ar.myTimedOut -> stringResource(R.string.timeout)
        ar.myAnswer != null -> stringResource(R.string.duel_waiting_others)
        else -> null
    }
    if (status != null) Text(status, style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.duel_answered_count, locked, connected), style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
fun playerResultText(p: PlayerRoundResult): String = when {
    p.absent -> stringResource(R.string.duel_result_absent)
    p.timedOut -> stringResource(R.string.duel_result_timeout)
    p.correct -> stringResource(R.string.duel_result_correct_pts, p.points)
    else -> stringResource(R.string.duel_result_wrong)
}

@Composable
private fun Reveal(last: RoundSummary, state: RoomState, clock: MonotonicClock, onNext: () -> Unit) {
    val fb = LocalFeedbackColors.current
    val mine = last.of(state.me.sessionId)
    QuestionCard(last.round, last.index, state.totalRounds)
    val title = when {
        mine == null || mine.absent -> stringResource(R.string.duel_reveal_title_absent)
        mine.timedOut -> stringResource(R.string.duel_reveal_title_timeout)
        mine.correct -> stringResource(R.string.duel_reveal_title_correct, mine.points)
        else -> stringResource(R.string.duel_reveal_title_wrong)
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, color = if (mine?.correct == true) fb.correct else fb.wrong,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    last.round.displayedOptions.forEachIndexed { i, opt ->
        val pickers = last.players.filter { (id, r) -> id != state.me.sessionId && r.optionId == opt.id }.keys.map(state::nameOf)
        val mark = if (pickers.isNotEmpty()) "  ◀ " + pickers.joinToString(", ") else ""
        OptionButton(positionLabel(i), opt.text + mark, optionVisual(opt.id, mine?.optionId, last.correctOptionId), enabled = false, onClick = {})
    }
    InfoCard {
        state.participants.forEach { p ->
            val r = last.of(p.sessionId) ?: return@forEach
            val ans = r.optionId?.let { " (${last.round.labelOf(it)})" }.orEmpty()
            Text("${p.nickname}: ${playerResultText(r)}$ans", style = MaterialTheme.typography.bodyMedium,
                color = if (r.correct) fb.correct else MaterialTheme.colorScheme.onSurface)
        }
    }
    ExplanationCard(last.round)
    var now by remember { mutableLongStateOf(clock.nowMs()) }
    LaunchedEffect(state.revealUntilMs) { while (true) { now = clock.nowMs(); delay(250) } }
    state.revealUntilMs?.let { until ->
        val secs = ((until - now).coerceAtLeast(0) + 999) / 1000
        Text(stringResource(R.string.duel_next_in, secs.toInt()), style = MaterialTheme.typography.bodySmall)
    }
    PrimaryButton(
        if (state.meNextReady) stringResource(R.string.duel_next_waiting) else stringResource(R.string.duel_next),
        onClick = onNext,
        enabled = !state.meNextReady,
    )
}

@Composable
private fun Reconnecting(deadline: Long?, clock: MonotonicClock) {
    var now by remember { mutableLongStateOf(clock.nowMs()) }
    LaunchedEffect(deadline) { while (true) { now = clock.nowMs(); delay(500) } }
    val secs = (((deadline ?: now) - now).coerceAtLeast(0) / 1000).toInt()
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.duel_reconnecting_title), style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(R.string.duel_reconnecting_body, secs), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

private val previewPlayers = listOf(
    RoomPlayer("a", "Rina ⚡", true), RoomPlayer("b", "Budi", false), RoomPlayer("c", "Sari", false), RoomPlayer("d", "Joko", false),
)

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun MatchQuestionPreview() {
    val st = RoomState(
        role = RoomRole.GUEST, transport = TransportKind.LAN, me = previewPlayers[1], phase = RoomPhase.QUESTION, totalRounds = 10,
        players = previewPlayers, participants = previewPlayers,
        activeRound = ActiveRound(3, PreviewData.round, 0, 120_000, myAnswer = OptionId.A, myElapsedMs = 9_000, lockedPlayers = setOf("b", "c")),
    )
    EduDuelTheme { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { MatchPlayContent(st, { 40_000 }, {}, {}) } }
}

@Preview(showBackground = true, heightDp = 1400, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MatchRevealDarkPreview() {
    val r = RoundSummary(
        0, PreviewData.round, OptionId.A,
        mapOf(
            "a" to PlayerRoundResult(OptionId.A, 9_000, true, 389, false),
            "b" to PlayerRoundResult(OptionId.C, 15_000, false, 0, false),
            "c" to PlayerRoundResult.timeout(120_000),
            "d" to PlayerRoundResult.ABSENT,
        ),
    )
    val st = RoomState(
        role = RoomRole.HOST, transport = TransportKind.LAN, me = previewPlayers[0], phase = RoomPhase.REVEAL, totalRounds = 10,
        players = previewPlayers, participants = previewPlayers, results = listOf(r), revealUntilMs = 4_000,
    )
    EduDuelTheme(darkTheme = true) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { MatchPlayContent(st, { 0 }, {}, {}) } }
}
