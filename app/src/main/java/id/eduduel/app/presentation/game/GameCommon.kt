package id.eduduel.app.presentation.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.RoundQuestion
import id.eduduel.app.domain.game.AsteroidSpec
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.domain.game.GameType
import id.eduduel.app.domain.game.LabSpec
import id.eduduel.app.domain.game.MicroscopeSpec
import id.eduduel.app.domain.game.TrajectorySpec
import id.eduduel.app.net.protocol.GhostAction
import id.eduduel.app.net.protocol.GhostState
import id.eduduel.app.net.room.GhostTrack
import id.eduduel.app.presentation.components.GameButton
import id.eduduel.app.presentation.components.InfoCard
import kotlinx.coroutines.isActive

/** Penanda "belum pernah terjadi" untuk stempel waktu event lokal (tembak, luncur). */
const val NEVER_MS = Long.MIN_VALUE

/**
 * Milidetik sejak [thenMs]. AMAN untuk [NEVER_MS]: `now - Long.MIN_VALUE` overflow menjadi negatif
 * (bug 0.4.0: tembakan asteroid selalu ditolak dan laser tergambar terus di tengah arena).
 */
fun msSince(nowMs: Long, thenMs: Long): Long = if (thenMs == NEVER_MS) Long.MAX_VALUE else nowMs - thenMs

/** Gaya ghost lokal (Pengaturan): ON/OFF dan opasitas 15-30%. */
data class GhostStyle(val enabled: Boolean = true, val opacity: Float = DEFAULT_OPACITY) {
    companion object {
        const val DEFAULT_OPACITY = 0.25f
        const val MIN_OPACITY = 0.15f
        const val MAX_OPACITY = 0.30f
    }
}

/** Ghost pemain lain yang siap digambar: nama, warna, track, sudah mengunci atau belum. */
data class GhostView(val id: String, val name: String, val color: Color, val track: GhostTrack?, val locked: Boolean)

/** Warna pemain (urutan peserta) — juga dipakai untuk label ghost. */
val PlayerColors = listOf(Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFFFF7043), Color(0xFF42A5F5))

/** Konteks game yang sama untuk Solo dan room. */
data class GameEnv(
    val round: RoundQuestion,
    val startedAtMs: Long,
    val durationMs: Long,
    val clock: MonotonicClock,
    val locked: Boolean,
    val myColor: Color,
    val ghosts: List<GhostView> = emptyList(),
    val ghostStyle: GhostStyle = GhostStyle(),
    val ghostMode: GhostMode = GhostMode.LIVE,
    /** Opsi aksesibilitas: target sentuh lebih besar, kontrol langkah. */
    val assist: Boolean = false,
)

data class GameCallbacks(
    val onGhost: (GhostState) -> Unit = {},
    val onAction: (GhostAction) -> Unit = {},
    val onSubmit: (GameSubmission?) -> Unit,
)

/** Waktu monotonic yang diperbarui setiap frame selama [running] (untuk animasi & ghost). */
@Composable
fun rememberFrameTime(clock: MonotonicClock, running: Boolean): Long {
    var now by remember { mutableLongStateOf(clock.nowMs()) }
    LaunchedEffect(running) {
        while (isActive) {
            withFrameMillis { now = clock.nowMs() }
            if (!running) break
        }
    }
    return now
}

fun parseColor(hex: String?, fallback: Color = Color.Transparent): Color {
    if (hex == null || !hex.startsWith("#")) return fallback
    val h = hex.substring(1)
    return try {
        when (h.length) {
            6 -> Color(0xFF000000 or h.toLong(16))
            8 -> Color(h.substring(6, 8).toLong(16).shl(24) or h.substring(0, 6).toLong(16))
            else -> fallback
        }
    } catch (e: NumberFormatException) {
        fallback
    }
}

/** Dispatcher: satu soal game -> mesin gameplay sesuai mapelnya. */
@Composable
fun GameArena(env: GameEnv, callbacks: GameCallbacks) {
    val spec = env.round.question.game?.spec ?: return
    when (spec) {
        is AsteroidSpec -> AsteroidGame(spec, env, callbacks)
        is TrajectorySpec -> TrajectoryGame(spec, env, callbacks)
        is MicroscopeSpec -> MicroscopeGame(spec, env, callbacks)
        is LabSpec -> LabGame(spec, env, callbacks)
    }
}

/** Baris status pemain lain: nama berwarna + "selesai" (tanpa membocorkan benar/salah). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GhostLegend(env: GameEnv) {
    if (env.ghosts.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                (if (env.ghostMode == GhostMode.FAIR) "Ghost FAIR" else "Ghost LIVE") + if (!env.ghostStyle.enabled) " · disembunyikan" else "",
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
            )
        }
        env.ghosts.forEach { g ->
            Surface(shape = RoundedCornerShape(50), color = g.color.copy(alpha = 0.16f)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(g.color, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(g.name + if (g.locked || g.track?.done == true) " · selesai ✓" else "", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Petunjuk & aturan singkat per jenis game (tutorial dan kartu aturan). */
fun tutorialLines(type: GameType): List<String> = when (type) {
    GameType.ASTEROID -> listOf(
        "Geser pesawat ke kiri/kanan atau ketuk langsung asteroid untuk membidik & menembak.",
        "Tembak token SESUAI URUTAN kotak kosong (kotak yang menyala).",
        "Hindari asteroid: tabrakan atau menembak token yang salah mengurangi 1 nyawa. Semua kotak terisi = berhasil.",
    )
    GameType.TRAJECTORY -> listOf(
        "Atur parameter peluncuran dengan slider atau tombol −/+ (yang terkunci tidak bisa diubah).",
        "Tekan LUNCURKAN. Kena sasaran = berhasil; jumlah tembakan terbatas.",
        "Lintasan tembakan sebelumnya tetap terlihat sebagai panduan.",
    )
    GameType.MICROSCOPE -> listOf(
        "Geser (pan) dan cubit (zoom) preparat, atau pakai tombol arah dan −/+.",
        "Putar FOKUS sampai gambar tajam. Tap hanya dihitung bila tajam dan cukup diperbesar.",
        "Ketuk struktur yang diminta. Tap salah menunjukkan nama struktur yang kamu sentuh.",
    )
    GameType.LAB -> listOf(
        "Seret botol reagen ke bejana untuk menuang, atau pakai tombol tuang (+langkah).",
        "Perhatikan pembacaan (volume, massa, warna indikator). Tombol Ulangi mengosongkan bejana.",
        "Tekan KUNCI HASIL bila sudah yakin. Semua eksperimen adalah simulasi virtual.",
    )
}

/**
 * Tutorial interaksi sebelum START. Waktu menjawab BELUM berjalan; bisa dilewati, atau selesai
 * otomatis saat hitung mundur habis.
 */
@Composable
fun GameTutorial(type: GameType, untilMs: Long, clock: MonotonicClock, firstOfType: Boolean, waiting: Boolean, onSkip: () -> Unit) {
    val now = rememberFrameTime(clock, running = true)
    val remaining = ((untilMs - now).coerceAtLeast(0) + 999) / 1000
    InfoCard {
        Text("${type.label} · ${type.subject.label}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold)
        Text(if (firstOfType) "Cara bermain (waktu soal belum berjalan):" else "Siap? Mekanisme sama seperti sebelumnya.",
            style = MaterialTheme.typography.titleSmall)
        if (firstOfType) {
            tutorialLines(type).forEachIndexed { i, line -> Text("${i + 1}. $line", style = MaterialTheme.typography.bodyMedium) }
        }
        Text("Skor: berhasil = poin dasar kesulitan + bonus cepat (maks 50%); gagal/waktu habis = 0. Sama seperti soal kuis.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (waiting) {
            Text("Menunggu pemain lain menyelesaikan tutorial…", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Text("Mulai otomatis dalam $remaining dtk", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            GameButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Saya siap — Mulai") }
        }
    }
}

/** Kartu hasil game setelah terkunci (sebelum hasil ronde). */
@Composable
fun GameLockedNote(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
