package id.eduduel.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.eduduel.app.R
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.Question
import id.eduduel.app.domain.RoundQuestion
import id.eduduel.app.presentation.theme.QuestionTextStyle

/** Label posisi tampilan (A-D) untuk opsi ke-i. */
fun positionLabel(index: Int): String = OptionId.entries[index].name

/** Label posisi tampilan dari id kanonik pada urutan opsi tertentu. */
fun RoundQuestion.labelOf(id: OptionId): String = positionLabel(optionOrder.indexOf(id))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(round: RoundQuestion, index: Int, total: Int) {
    val q = round.question
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(progress = { if (total > 0) (index + 1).toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer, drawStopIndicator = {})
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RoleBadge(stringResource(R.string.round_of, index + 1, total), MaterialTheme.colorScheme.primaryContainer)
                RoleBadge("${q.subject.label} · ${q.gradeBand.name} · ${q.difficulty.label}")
                if (q.type != id.eduduel.app.domain.QuestionType.CLASSIC_MCQ) RoleBadge(typeLabel(q), MaterialTheme.colorScheme.tertiaryContainer)
            }
            Text(q.topic, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MathAwareText(q.stem, Modifier.semantics { heading() }, QuestionTextStyle)
            // Soal bergambar: gambar DI ATAS opsi, rasio dijaga, bisa diperbesar.
            q.imageAsset?.let { QuestionImage(it, q.id) }
        }
    }
}

/** Label jenis soal (klasik / bergambar / nama mini-game). */
fun typeLabel(q: Question): String = q.game?.gameType?.label ?: q.type.label

/** Visual opsi sebelum/sesudah jawaban diungkap. */
fun optionVisual(id: OptionId, selected: OptionId?, revealCorrect: OptionId?): OptionVisual = when {
    revealCorrect != null && id == revealCorrect -> OptionVisual.CORRECT
    revealCorrect != null && id == selected -> OptionVisual.WRONG
    revealCorrect != null -> OptionVisual.DIMMED
    selected != null && id == selected -> OptionVisual.SELECTED
    selected != null -> OptionVisual.DIMMED
    else -> OptionVisual.NORMAL
}

/**
 * Pembahasan asli dari bank soal; jika tidak ada, hanya jawaban benar (tanpa pembahasan karangan).
 * Soal game: solusi referensi + pembahasan, ditampilkan HANYA setelah ronde ditutup.
 */
@Composable
fun ExplanationCard(round: RoundQuestion) {
    val q: Question = round.question
    InfoCard {
        val game = q.game
        val correctId = q.correctOptionId
        if (game != null) {
            Text(stringResource(R.string.game_solution_title), style = MaterialTheme.typography.titleSmall)
            MathAwareText(game.solutionText, style = MaterialTheme.typography.bodyLarge)
        } else if (correctId != null) {
            Text(
                stringResource(R.string.correct_answer_is, round.labelOf(correctId), ""),
                style = MaterialTheme.typography.titleSmall,
            )
            MathAwareText(q.option(correctId).text, style = MaterialTheme.typography.bodyLarge)
        }
        if (q.explanation != null) {
            Text(stringResource(R.string.explanation_title), style = MaterialTheme.typography.titleSmall)
            MathAwareText(q.explanation)
        }
    }
}

/** Data contoh untuk @Preview (tidak dipakai saat runtime). */
object PreviewData {
    val question = Question.mcq(
        id = "KIM-SMP-046",
        subject = id.eduduel.app.domain.Subject.KIMIA,
        gradeBand = id.eduduel.app.domain.GradeBand.SMP,
        topic = "Asam, basa, dan pH",
        difficulty = id.eduduel.app.domain.Difficulty.SULIT,
        stem = "Pada 25 °C, suatu larutan memiliki konsentrasi ion H⁺ sebesar 10⁻² mol/L. Nilai pH larutan itu adalah ...",
        options = listOf(
            id.eduduel.app.domain.AnswerOption(OptionId.A, "2"),
            id.eduduel.app.domain.AnswerOption(OptionId.B, "12"),
            id.eduduel.app.domain.AnswerOption(OptionId.C, "7"),
            id.eduduel.app.domain.AnswerOption(OptionId.D, "-2"),
        ),
        correct = OptionId.A,
        explanation = "pH = -log[H⁺] = 2.",
    )
    val round = RoundQuestion(question, listOf(OptionId.C, OptionId.A, OptionId.D, OptionId.B))
}
