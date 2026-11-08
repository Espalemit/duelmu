package id.eduduel.app.presentation.solo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.eduduel.app.AppContainer
import id.eduduel.app.core.Ids
import id.eduduel.app.data.bank.BankState
import id.eduduel.app.data.history.HistoryRepository
import id.eduduel.app.data.history.MatchRecordEntity
import id.eduduel.app.data.history.RecordMode
import id.eduduel.app.data.history.RecordOutcome
import id.eduduel.app.data.history.RoundRecord
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.QuestionSelector
import id.eduduel.app.domain.SoloEngine
import id.eduduel.app.domain.SoloState
import id.eduduel.app.domain.describe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface SoloUiState {
    data object Loading : SoloUiState
    data class Error(val message: String) : SoloUiState
    data class Playing(val state: SoloState) : SoloUiState
}

/**
 * State latihan disimpan di ViewModel (bertahan saat rotasi) DAN di SavedStateHandle (bertahan saat
 * process death). Waktu mulai soal memakai elapsedRealtime, jadi timer tidak di-reset.
 */
class SoloViewModel(
    private val handle: SavedStateHandle,
    private val container: AppContainer,
    private val config: MatchConfig,
) : ViewModel() {
    private val clock = container.clock
    private val _ui = MutableStateFlow<SoloUiState>(SoloUiState.Loading)
    val ui: StateFlow<SoloUiState> = _ui.asStateFlow()
    private var bank: QuestionBank? = null
    private val usedIds = linkedSetOf<String>()
    private var savedSessionId: String? = null

    init {
        viewModelScope.launch {
            val st = container.bankRepository.state.first { it !is BankState.Loading }
            if (st is BankState.Error) {
                _ui.value = SoloUiState.Error(st.message); return@launch
            }
            val b = (st as BankState.Ready).bank
            bank = b
            val restored = handle.get<String>(KEY_SNAPSHOT)?.let { raw ->
                runCatching { SoloEngine.restore(json.decodeFromString(SoloEngine.Snapshot.serializer(), raw), b) }.getOrNull()
            }
            savedSessionId = handle.get<String>(KEY_SAVED)
            set(restored ?: newSession(b))
            while (isActive) {
                delay(200)
                val s = (ui.value as? SoloUiState.Playing)?.state ?: continue
                if (!s.finished && !s.isLocked) set(SoloEngine.timeout(s, clock.nowMs()))
            }
        }
    }

    private fun newSession(b: QuestionBank): SoloState {
        val pool = b.pool(config)
        val count = config.questionCount.coerceAtMost(pool.size)
        val rounds = QuestionSelector.select(pool, count, Ids.randomSeed(), avoidIds = usedIds)
        usedIds += rounds.map { it.question.id }
        handle[KEY_SESSION] = Ids.random()
        return SoloEngine.start(config, rounds, clock.nowMs())
    }

    fun answer(option: OptionId) = update { SoloEngine.answer(it, option, clock.nowMs()) }
    fun next() = update { SoloEngine.next(it, clock.nowMs()) }

    /** Ulangi latihan dengan soal lain (menghindari soal yang sudah muncul bila memungkinkan). */
    fun retry() {
        val b = bank ?: return
        (ui.value as? SoloUiState.Playing)?.state?.rounds?.forEach { usedIds += it.question.id }
        set(newSession(b))
    }

    private inline fun update(f: (SoloState) -> SoloState) {
        val s = (ui.value as? SoloUiState.Playing)?.state ?: return
        set(f(s))
    }

    private fun set(s: SoloState) {
        _ui.value = SoloUiState.Playing(s)
        handle[KEY_SNAPSHOT] = json.encodeToString(SoloEngine.Snapshot.serializer(), SoloEngine.snapshot(s))
        val sessionId = handle.get<String>(KEY_SESSION) ?: return
        if (s.finished && savedSessionId != sessionId) {
            savedSessionId = sessionId
            handle[KEY_SAVED] = sessionId
            viewModelScope.launch { saveHistory(sessionId, s) }
        }
    }

    private suspend fun saveHistory(id: String, s: SoloState) {
        val b = bank ?: return
        container.history.save(
            MatchRecordEntity(
                id = id,
                mode = RecordMode.SOLO,
                playedAtEpochMs = System.currentTimeMillis(),
                myNickname = null,
                opponentNickname = null,
                myRole = null,
                myScore = s.totalScore,
                opponentScore = null,
                outcome = RecordOutcome.PRACTICE,
                correctCount = s.correctCount,
                questionCount = s.rounds.size,
                configSummary = s.config.describe(),
                roundsJson = HistoryRepository.encodeRounds(
                    s.answers.mapIndexed { i, a ->
                        RoundRecord(i, a.questionId, s.rounds[i].question.correctOptionId.name, a.optionId?.name, a.score.total, a.elapsedMs)
                    },
                ),
                bankVersionName = b.versionName,
            ),
        )
    }

    companion object {
        private const val KEY_SNAPSHOT = "solo_snapshot"
        private const val KEY_SESSION = "solo_session_id"
        private const val KEY_SAVED = "solo_saved_session_id"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
