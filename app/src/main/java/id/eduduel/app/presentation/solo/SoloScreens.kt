package id.eduduel.app.presentation.solo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import id.eduduel.app.presentation.components.GameOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import id.eduduel.app.presentation.components.GameTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import id.eduduel.app.R
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.data.bank.BankState
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.SoloState
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.SubjectPicker
import id.eduduel.app.presentation.components.ExplanationCard
import id.eduduel.app.presentation.components.InfoCard
import id.eduduel.app.presentation.components.LoadingState
import id.eduduel.app.presentation.components.MathAwareText
import id.eduduel.app.presentation.components.MessageState
import id.eduduel.app.presentation.components.OptionButton
import id.eduduel.app.presentation.components.PrimaryButton
import id.eduduel.app.presentation.components.QuestionCard
import id.eduduel.app.presentation.components.ScrollColumn
import id.eduduel.app.presentation.components.TimerBar
import id.eduduel.app.presentation.components.labelOf
import id.eduduel.app.presentation.components.optionVisual
import id.eduduel.app.presentation.components.positionLabel
import id.eduduel.app.presentation.game.GameArena
import id.eduduel.app.presentation.game.GameCallbacks
import id.eduduel.app.presentation.game.GameEnv
import id.eduduel.app.presentation.game.GameTutorial
import id.eduduel.app.presentation.game.PlayerColors
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.navigation.configFromRouteJson
import id.eduduel.app.presentation.navigation.toRouteJson
import id.eduduel.app.presentation.theme.EduDuelTheme
import id.eduduel.app.presentation.theme.LocalFeedbackColors

@Composable
fun SoloSetupScreen(onBack: () -> Unit, onStart: (MatchConfig) -> Unit) {
    val container = LocalAppContainer.current
    val bankState by container.bankRepository.state.collectAsStateWithLifecycle()
    var configJson by rememberSaveable { mutableStateOf(MatchConfig.INITIAL.toRouteJson()) }
    val config = configFromRouteJson(configJson)
    AppScaffold(stringResource(R.string.solo_setup_title), onBack) { padding ->
        when (val st = bankState) {
            BankState.Loading -> LoadingState(stringResource(R.string.bank_loading))
            is BankState.Error -> MessageState(stringResource(R.string.bank_error_title), st.message, isError = true)
            is BankState.Ready -> ScrollColumn(padding) {
                InfoCard {
                    Text(stringResource(R.string.play_solo_intro), style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.play_solo_body))
                }
                Text(stringResource(R.string.config_subject), style = MaterialTheme.typography.titleMedium)
                SubjectPicker(st.bank, config, onChange = { configJson = it.toRouteJson() })
                PrimaryButton(
                    stringResource(R.string.solo_start),
                    onClick = { onStart(config) },
                    enabled = st.bank.pool(config).size >= config.questionCount,
                )
            }
        }
    }
}

@Composable
fun SoloPlayScreen(config: MatchConfig, onExit: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SoloViewModel = viewModel { SoloViewModel(createSavedStateHandle(), container, config) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val feedback = id.eduduel.app.presentation.components.LocalInteractionFeedback.current
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val playing = (ui as? SoloUiState.Playing)?.state
    id.eduduel.app.presentation.components.BackgroundMusicScene(
        if (playing != null && !playing.finished) id.eduduel.app.presentation.components.MusicTrack.COMPETITIVE
        else id.eduduel.app.presentation.components.MusicTrack.MENU,
    )
    id.eduduel.app.presentation.components.GameFeedbackEffects(
        sessionId = vm.sessionId,
        started = playing != null && !playing.finished && playing.index == 0 && playing.answers.isEmpty(),
        resultIndex = playing?.answers?.lastIndex?.takeIf { it >= 0 },
        correct = playing?.answers?.lastOrNull()?.correct,
        feedback = feedback,
    )
    BackHandler(enabled = playing != null && !playing.finished) { confirmExit = true }

    val title = if (playing?.finished == true) stringResource(R.string.solo_result_title) else stringResource(R.string.solo_setup_title)
    AppScaffold(title, onBack = { if (playing != null && !playing.finished) confirmExit = true else onExit() }) { padding ->
        when (val u = ui) {
            SoloUiState.Loading -> LoadingState(stringResource(R.string.bank_loading))
            is SoloUiState.Error -> MessageState(stringResource(R.string.bank_error_title), u.message, isError = true)
            is SoloUiState.Playing -> ScrollColumn(padding) {
                if (u.state.finished) {
                    SoloResult(u.state, onRetry = vm::retry, onHome = onExit)
                } else {
                    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
                    SoloQuestion(
                        u.state, container.clock, onAnswer = { vm.answer(it) }, onNext = vm::next,
                        onGame = vm::answerGame, onBegin = vm::begin, assist = settings.touchAssist,
                    )
                }
            }
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.leave_quiz_title)) },
            text = { Text(stringResource(R.string.leave_quiz_body)) },
            confirmButton = { TextButton(onClick = { confirmExit = false; onExit() }) { Text(stringResource(R.string.leave)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun SoloQuestion(
    state: SoloState,
    clock: MonotonicClock,
    onAnswer: (OptionId) -> Unit,
    onNext: () -> Unit,
    onGame: (GameSubmission?) -> Unit = {},
    onBegin: () -> Unit = {},
    assist: Boolean = false,
) {
    val round = state.current
    val answer = state.currentAnswer
    QuestionCard(round, state.index, state.rounds.size)
    val game = round.question.game
    if (game != null && state.awaitingStart) {
        // Tutorial interaksi: waktu soal BELUM berjalan; mulai otomatis setelah hitung mundur.
        val until = androidx.compose.runtime.remember(state.index) { clock.nowMs() + if (state.rounds.take(state.index).none { it.question.game?.gameType == game.gameType }) 8_000L else 3_000L }
        val first = state.rounds.take(state.index).none { it.question.game?.gameType == game.gameType }
        androidx.compose.runtime.LaunchedEffect(state.index) {
            kotlinx.coroutines.delay((until - clock.nowMs()).coerceAtLeast(0))
            onBegin()
        }
        GameTutorial(game.gameType, until, clock, first, waiting = false, onSkip = onBegin)
        return
    }
    TimerBar(state.startedAtMs, round.question.timeLimitMs, clock, running = answer == null)
    if (game != null) {
        GameArena(
            GameEnv(round, state.startedAtMs, round.question.timeLimitMs, clock, locked = answer != null, myColor = PlayerColors[0], assist = assist),
            GameCallbacks(onSubmit = onGame),
        )
    }
    round.displayedOptions.forEachIndexed { i, opt ->
        OptionButton(
            label = positionLabel(i),
            text = opt.text,
            visual = optionVisual(opt.id, answer?.optionId, if (answer != null) round.question.correctOptionId else null),
            enabled = answer == null,
            onClick = { onAnswer(opt.id) },
        )
    }
    if (answer != null) {
        val fb = LocalFeedbackColors.current
        val (text, color) = when {
            answer.timedOut -> stringResource(R.string.timeout) to fb.wrong
            answer.correct -> "${stringResource(R.string.correct)} +${answer.score.total}" to fb.correct
            else -> stringResource(R.string.wrong) to fb.wrong
        }
        Text(
            text, color = color, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        answer.detail?.let { Text(stringResource(R.string.game_result_detail, it), style = MaterialTheme.typography.bodyMedium) }
        ExplanationCard(round)
        PrimaryButton(
            if (state.isLast) stringResource(R.string.see_result) else stringResource(R.string.next_question),
            onClick = onNext,
        )
    }
}

@Composable
fun SoloResult(state: SoloState, onRetry: () -> Unit, onHome: () -> Unit) {
    id.eduduel.app.presentation.components.ResultCelebration(
        title = stringResource(R.string.play_result_title),
        subtitle = stringResource(R.string.solo_result_summary, state.correctCount, state.rounds.size, state.totalScore),
    )
    Text(stringResource(R.string.play_result_encouragement), color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryButton(stringResource(R.string.solo_retry), onRetry)
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go_home)) }
    Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
    state.rounds.forEachIndexed { i, r ->
        val a = state.answers.getOrNull(i)
        InfoCard {
            Text(stringResource(R.string.round_of, i + 1, state.rounds.size), style = MaterialTheme.typography.labelLarge)
            MathAwareText(r.question.stem)
            val fb = LocalFeedbackColors.current
            Text(
                when {
                    a?.optionId != null -> stringResource(R.string.your_answer, "${r.labelOf(a.optionId)}. ${r.question.option(a.optionId).text}")
                    a?.detail != null -> stringResource(R.string.game_result_detail, a.detail)
                    else -> stringResource(R.string.your_answer_none)
                },
                color = if (a?.correct == true) fb.correct else fb.wrong,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { ExplanationCard(r) }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SoloQuestionPreview() {
    val clock = MonotonicClock { 30_000 }
    var s = id.eduduel.app.domain.SoloEngine.start(MatchConfig.INITIAL, listOf(id.eduduel.app.presentation.components.PreviewData.round), 0)
    s = id.eduduel.app.domain.SoloEngine.answer(s, OptionId.C, 20_000)
    EduDuelTheme {
        Column(Modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) { SoloQuestion(s, clock, {}, {}) }
    }
}
