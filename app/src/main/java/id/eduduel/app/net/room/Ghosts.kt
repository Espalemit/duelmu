package id.eduduel.app.net.room

import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.game.GameType
import id.eduduel.app.net.protocol.AsteroidGhost
import id.eduduel.app.net.protocol.FireAction
import id.eduduel.app.net.protocol.GameActionEvent
import id.eduduel.app.net.protocol.GameStateUpdate
import id.eduduel.app.net.protocol.GhostAction
import id.eduduel.app.net.protocol.GhostState
import id.eduduel.app.net.protocol.LabGhost
import id.eduduel.app.net.protocol.LabStepAction
import id.eduduel.app.net.protocol.LaunchAction
import id.eduduel.app.net.protocol.MicroGhost
import id.eduduel.app.net.protocol.TapAction
import id.eduduel.app.net.protocol.TrajectoryGhost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Privasi ghost. LIVE: apa adanya. FAIR: ghost tetap ada (posisi/gerak), tetapi label token,
 * titik sentuh, parameter eksperimen, dan indikator berhasil DIHAPUS. Dipanggil oleh pengirim
 * (privasi sejak sumber) DAN oleh Host sebelum relay (pertahanan bila klien dimodifikasi).
 */
object GhostPrivacy {
    fun redact(state: GhostState, mode: GhostMode): GhostState = if (mode == GhostMode.LIVE) state else when (state) {
        is AsteroidGhost -> state.copy(filled = null, wrong = null)
        is TrajectoryGhost -> state.copy(aim = null, power = null)
        is MicroGhost -> state.copy(focus = null, cx = null, cy = null)
        is LabGhost -> state.copy(holding = null, fill = null)
    }

    fun redact(action: GhostAction, mode: GhostMode): GhostAction = if (mode == GhostMode.LIVE) action else when (action) {
        is FireAction -> FireAction(action.x)
        is LaunchAction -> LaunchAction()
        is TapAction -> TapAction()
        is LabStepAction -> LabStepAction()
    }

    /** true bila state tidak memuat apa pun yang disembunyikan FAIR (dipakai tes "no answer leak"). */
    fun isFairSafe(state: GhostState): Boolean = redact(state, GhostMode.FAIR) == state
    fun isFairSafe(action: GhostAction): Boolean = redact(action, GhostMode.FAIR) == action
}

/** Validasi masukan ghost dari jaringan: tipe sesuai game, angka berhingga, rentang wajar. */
object GhostValidator {
    private fun ok(v: Float?) = v == null || (v.isFinite() && v in -0.25f..1.25f)

    fun valid(type: GameType, state: GhostState): Boolean = when (state) {
        is AsteroidGhost -> type == GameType.ASTEROID && ok(state.x) && (state.filled ?: 0) in 0..16 && (state.wrong ?: 0) in 0..64
        is TrajectoryGhost -> type == GameType.TRAJECTORY && ok(state.aim) && ok(state.power) && state.shots in 0..16
        is MicroGhost -> type == GameType.MICROSCOPE && listOf(state.vx, state.vy, state.vw, state.vh, state.focus, state.cx, state.cy).all(::ok)
        is LabGhost -> type == GameType.LAB && ok(state.tx) && ok(state.ty) && ok(state.fill) && (state.holding ?: 0) in 0..32
    }

    fun valid(type: GameType, action: GhostAction): Boolean = when (action) {
        is FireAction -> type == GameType.ASTEROID && ok(action.x) && (action.token?.length ?: 0) <= 24
        is LaunchAction -> type == GameType.TRAJECTORY && (action.a ?: 0) in 0..10_000 && (action.b ?: 0) in 0..10_000
        is TapAction -> type == GameType.MICROSCOPE && ok(action.x) && ok(action.y)
        is LabStepAction -> type == GameType.LAB && (action.reagent ?: 0) in 0..32 && (action.amt ?: 0) in 0..1_000_000
    }
}

/** Satu sampel ghost dengan waktu TERIMA lokal (monotonic) untuk interpolasi. */
data class GhostSample(val receivedAtMs: Long, val tMs: Long, val state: GhostState)

data class GhostEvent(val receivedAtMs: Long, val action: GhostAction)

data class GhostTrack(
    val playerId: String,
    val gameType: GameType,
    val roundIndex: Int,
    val lastSeq: Int,
    val lastActionSeq: Int,
    /** Maks. 4 sampel terbaru, urut naik. */
    val samples: List<GhostSample>,
    /** Maks. 8 event terbaru. */
    val events: List<GhostEvent>,
) {
    val latest: GhostState? get() = samples.lastOrNull()?.state
    val done: Boolean get() = latest?.done == true
}

/**
 * Penyimpan ghost semua pemain LAIN untuk satu sesi. Terpisah dari RoomState agar update 5-10 Hz
 * tidak me-recompose seluruh layar; hanya kanvas game yang mengamati [tracks].
 *
 * - Snapshot dengan seq ≤ seq terakhir (terlambat/duplikat/out-of-order) dibuang.
 * - Snapshot ronde lain (stale roundIndex) dibuang.
 * - Ghost sendiri tidak pernah disimpan (tampil solid dari state lokal).
 */
class GhostHub(private val myId: () -> String) {
    private val _tracks = MutableStateFlow<Map<String, GhostTrack>>(emptyMap())
    val tracks: StateFlow<Map<String, GhostTrack>> = _tracks.asStateFlow()

    @Volatile
    var dropped = 0
        private set

    private var round = -1

    fun reset(roundIndex: Int) {
        round = roundIndex
        _tracks.value = emptyMap()
    }

    fun remove(playerId: String) {
        _tracks.value = _tracks.value - playerId
    }

    fun clear() {
        round = -1
        _tracks.value = emptyMap()
    }

    fun accept(roundIndex: Int, u: GameStateUpdate, nowMs: Long): Boolean {
        if (u.playerSessionId == myId() || roundIndex != round) { dropped++; return false }
        val cur = _tracks.value[u.playerSessionId]
        if (cur != null && u.seq <= cur.lastSeq) { dropped++; return false }
        val samples = ((cur?.samples ?: emptyList()) + GhostSample(nowMs, u.tMs, u.state())).takeLast(MAX_SAMPLES)
        _tracks.value = _tracks.value + (u.playerSessionId to GhostTrack(
            u.playerSessionId, u.gameType, roundIndex, u.seq, cur?.lastActionSeq ?: -1, samples, cur?.events ?: emptyList(),
        ))
        return true
    }

    fun acceptAction(roundIndex: Int, e: GameActionEvent, nowMs: Long): Boolean {
        if (e.playerSessionId == myId() || roundIndex != round) { dropped++; return false }
        val cur = _tracks.value[e.playerSessionId]
        if (cur != null && e.seq <= cur.lastActionSeq) { dropped++; return false }
        val track = (cur ?: GhostTrack(e.playerSessionId, e.gameType, roundIndex, -1, -1, emptyList(), emptyList()))
        _tracks.value = _tracks.value + (e.playerSessionId to track.copy(
            lastActionSeq = e.seq, events = (track.events + GhostEvent(nowMs, e.action)).takeLast(MAX_EVENTS),
        ))
        return true
    }

    private fun GameStateUpdate.state() = ghost

    companion object {
        const val MAX_SAMPLES = 4
        const val MAX_EVENTS = 8
    }
}

/**
 * Interpolasi tampilan ghost: render pada (sekarang − [delayMs]) di antara dua sampel yang mengapit,
 * sehingga jitter jaringan 100-200 ms tidak membuat ghost tersendat. Field non-posisi diambil dari
 * sampel yang lebih baru.
 */
object GhostInterpolator {
    const val DEFAULT_DELAY_MS = 150L

    fun at(track: GhostTrack, nowMs: Long, delayMs: Long = DEFAULT_DELAY_MS): GhostState? {
        val s = track.samples
        if (s.isEmpty()) return null
        val t = nowMs - delayMs
        if (s.size == 1 || t >= s.last().receivedAtMs) return s.last().state
        if (t <= s.first().receivedAtMs) return s.first().state
        val i = s.indexOfLast { it.receivedAtMs <= t }.coerceAtLeast(0)
        val a = s[i]
        val b = s[minOf(i + 1, s.lastIndex)]
        val span = (b.receivedAtMs - a.receivedAtMs).coerceAtLeast(1)
        val f = ((t - a.receivedAtMs).toFloat() / span).coerceIn(0f, 1f)
        return lerp(a.state, b.state, f)
    }

    private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
    private fun mixN(a: Float?, b: Float?, f: Float) = if (a != null && b != null) mix(a, b, f) else b

    fun lerp(a: GhostState, b: GhostState, f: Float): GhostState = when {
        a is AsteroidGhost && b is AsteroidGhost -> b.copy(x = mix(a.x, b.x, f))
        a is TrajectoryGhost && b is TrajectoryGhost -> b.copy(aim = mixN(a.aim, b.aim, f), power = mixN(a.power, b.power, f))
        a is MicroGhost && b is MicroGhost -> b.copy(
            vx = mix(a.vx, b.vx, f), vy = mix(a.vy, b.vy, f), vw = mix(a.vw, b.vw, f), vh = mix(a.vh, b.vh, f),
            cx = mixN(a.cx, b.cx, f), cy = mixN(a.cy, b.cy, f), focus = mixN(a.focus, b.focus, f),
        )
        a is LabGhost && b is LabGhost -> b.copy(tx = mix(a.tx, b.tx, f), ty = mix(a.ty, b.ty, f), fill = mixN(a.fill, b.fill, f))
        else -> b
    }

    /** Perubahan kecil tidak perlu dikirim ulang (coalescing). */
    fun nearlyEqual(a: GhostState?, b: GhostState?): Boolean {
        if (a == null || b == null || a::class != b::class || a.done != b.done) return a == b
        fun c(x: Float?, y: Float?) = (x == null && y == null) || (x != null && y != null && abs(x - y) < 0.002f)
        return when (a) {
            is AsteroidGhost -> b is AsteroidGhost && c(a.x, b.x) && a.firing == b.firing && a.filled == b.filled && a.wrong == b.wrong
            is TrajectoryGhost -> b is TrajectoryGhost && c(a.aim, b.aim) && c(a.power, b.power) && a.shots == b.shots
            is MicroGhost -> b is MicroGhost && c(a.vx, b.vx) && c(a.vy, b.vy) && c(a.vw, b.vw) && c(a.vh, b.vh) &&
                c(a.focus, b.focus) && c(a.cx, b.cx) && c(a.cy, b.cy)
            is LabGhost -> b is LabGhost && c(a.tx, b.tx) && c(a.ty, b.ty) && a.holding == b.holding && a.pouring == b.pouring && c(a.fill, b.fill)
        }
    }
}

/** Token bucket sederhana untuk rate-limit per Tamu (anti-spam ghost/aksi). */
class RateLimiter(private val perSecond: Int, private val burst: Int = perSecond) {
    private var tokens = burst.toDouble()
    private var lastMs = Long.MIN_VALUE

    fun allow(nowMs: Long): Boolean {
        if (lastMs != Long.MIN_VALUE) tokens = minOf(burst.toDouble(), tokens + (nowMs - lastMs) * perSecond / 1000.0)
        lastMs = nowMs
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }
}
