package id.eduduel.app.domain.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================================ Submisi (dikirim ke Host)

/**
 * Jawaban game = MASUKAN pemain (bukan klaim "benar"). Host memutar ulang masukan ini dengan
 * mesin deterministik yang sama sehingga skor tidak bisa dipalsukan dengan flag correct=true.
 * Ukuran dibatasi oleh [GameLimits].
 */
@Serializable
sealed interface GameSubmission {
    val type: GameType
}

/** Indeks token (ke [AsteroidSpec.tokens]) yang terkena tembakan, berurutan. */
@Serializable
@SerialName("asteroid")
data class AsteroidSubmission(val hits: List<Int>) : GameSubmission {
    override val type: GameType get() = GameType.ASTEROID
}

/** Indeks parameter diskrit a/b per tembakan (lihat [ParamRange]). */
@Serializable
data class ShotInput(val a: Int, val b: Int)

@Serializable
@SerialName("trajectory")
data class TrajectorySubmission(val shots: List<ShotInput>) : GameSubmission {
    override val type: GameType get() = GameType.TRAJECTORY
}

/** Tap di koordinat DUNIA (bilangan bulat), perbesaran ×100, fokus per-mil (0..1000). */
@Serializable
data class MicroTap(val x: Int, val y: Int, val zoom: Int, val focus: Int)

@Serializable
@SerialName("microscope")
data class MicroSubmission(val taps: List<MicroTap>, val assist: Boolean = false) : GameSubmission {
    override val type: GameType get() = GameType.MICROSCOPE
}

/**
 * Aksi lab: [r] = indeks reagen (atau alat untuk modul pemisahan), [amt] = jumlah ×1000 satuan
 * reagen; r = [LabAction.RESET] mengosongkan bejana.
 */
@Serializable
data class LabAction(val r: Int, val amt: Int = 0) {
    companion object {
        const val RESET = -1
    }
}

@Serializable
@SerialName("lab")
data class LabSubmission(val actions: List<LabAction>) : GameSubmission {
    override val type: GameType get() = GameType.LAB
}

object GameLimits {
    const val MAX_ASTEROID_HITS = 64
    const val MAX_SHOTS = 8
    const val MAX_TAPS = 32
    const val MAX_LAB_ACTIONS = 240
}

/** Hasil evaluasi deterministik. [detail] = ringkasan transparan untuk layar hasil. */
data class GameOutcome(val success: Boolean, val detail: String, val mistakes: Int = 0)

class InvalidSubmissionException(message: String) : IllegalArgumentException(message)

object GameEngines {
    /** Evaluasi submisi terhadap konfigurasi soal. Submisi tidak valid -> gagal (bukan crash). */
    fun evaluate(spec: GameSpec, submission: GameSubmission?): GameOutcome {
        if (submission == null) return GameOutcome(false, "Tidak ada jawaban")
        return try {
            when (spec) {
                is AsteroidSpec -> AsteroidEngine.evaluate(spec, submission as? AsteroidSubmission ?: invalid())
                is TrajectorySpec -> TrajectoryEngine.evaluate(spec, submission as? TrajectorySubmission ?: invalid())
                is MicroscopeSpec -> MicroscopeEngine.evaluate(spec, submission as? MicroSubmission ?: invalid())
                is LabSpec -> LabEngine.evaluate(spec, submission as? LabSubmission ?: invalid())
            }
        } catch (e: InvalidSubmissionException) {
            GameOutcome(false, "Masukan tidak valid: ${e.message}")
        }
    }

    /** Validasi batas ukuran & rentang sebelum evaluasi (dipakai Host untuk menolak spam). */
    fun isWellFormed(spec: GameSpec, submission: GameSubmission): Boolean = when (spec) {
        is AsteroidSpec -> submission is AsteroidSubmission && submission.hits.size <= GameLimits.MAX_ASTEROID_HITS &&
            submission.hits.all { it in spec.tokens.indices }
        is TrajectorySpec -> submission is TrajectorySubmission && submission.shots.size in 1..min(spec.maxShots, GameLimits.MAX_SHOTS) &&
            submission.shots.all { it.a in 0 until spec.a.count && it.b in 0 until spec.b.count }
        is MicroscopeSpec -> submission is MicroSubmission && submission.taps.size in 1..GameLimits.MAX_TAPS &&
            submission.taps.all { it.focus in 0..1000 && it.zoom in 1..10_000 }
        is LabSpec -> submission is LabSubmission && submission.actions.size in 1..GameLimits.MAX_LAB_ACTIONS &&
            submission.actions.all { a ->
                a.r == LabAction.RESET || (if (spec.module == LabModule.SEPARATION) a.r in spec.tools.indices else a.r in spec.reagents.indices && a.amt > 0)
            }
    }

    internal fun invalid(msg: String = "jenis submisi tidak cocok"): Nothing = throw InvalidSubmissionException(msg)
}

// ============================================================================ Asteroid Solver

object AsteroidEngine {
    data class Progress(val filled: Int, val wrong: Int, val success: Boolean, val failed: Boolean)

    fun progress(spec: AsteroidSpec, hits: List<Int>): Progress {
        var slot = 0
        var wrong = 0
        val tokens = spec.tokens
        for (h in hits) {
            if (slot == spec.answerTokens.size || wrong > spec.maxWrong) break
            val token = tokens.getOrNull(h) ?: GameEngines.invalid("token $h")
            if (token == spec.answerTokens[slot]) slot++ else wrong++
        }
        val success = slot == spec.answerTokens.size && wrong <= spec.maxWrong
        return Progress(slot, wrong, success, wrong > spec.maxWrong)
    }

    fun evaluate(spec: AsteroidSpec, s: AsteroidSubmission): GameOutcome {
        if (s.hits.size > GameLimits.MAX_ASTEROID_HITS) GameEngines.invalid("terlalu banyak tembakan")
        val p = progress(spec, s.hits)
        return GameOutcome(p.success, "Slot terisi ${p.filled}/${spec.answerTokens.size}, tembakan salah ${p.wrong} (maks ${spec.maxWrong})", p.wrong)
    }

    /** Asteroid ke-[index] (deterministik dari seed ronde): token, posisi x (0..1), waktu muncul (detik). */
    data class Spawn(val index: Int, val token: Int, val x: Double, val spawnAt: Double, val speedFactor: Double)

    /**
     * Urutan kemunculan dari seed: kantong token diacak per siklus; setiap siklus memuat setiap
     * token jawaban DUA kali dan setiap pengecoh sekali, sehingga token yang dibutuhkan selalu muncul.
     */
    fun spawns(spec: AsteroidSpec, seed: Long, count: Int): List<Spawn> {
        val rng = SplitMix(seed)
        val out = ArrayList<Spawn>(count)
        val bag = ArrayList<Int>()
        var lastX = -1.0
        for (i in 0 until count) {
            if (bag.isEmpty()) {
                spec.answerTokens.indices.forEach { bag += it; bag += it }
                spec.distractorTokens.indices.forEach { bag += spec.answerTokens.size + it }
                for (j in bag.indices.reversed()) {
                    val k = rng.nextInt(j + 1)
                    val t = bag[j]; bag[j] = bag[k]; bag[k] = t
                }
            }
            val token = bag.removeAt(bag.lastIndex)
            var x = 0.1 + rng.nextDouble() * 0.8
            if (lastX >= 0 && abs(x - lastX) < 0.18) x = if (lastX < 0.5) lastX + 0.25 else lastX - 0.25
            lastX = x
            out += Spawn(i, token, x, i * spec.spawnInterval, 0.85 + rng.nextDouble() * 0.3)
        }
        return out
    }
}

/** RNG SplitMix64 portabel (hasil identik di semua perangkat, tidak bergantung kotlin.random). */
class SplitMix(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()
}

// ============================================================================ Trajectory Lab

object TrajectoryEngine {
    /** Langkah waktu tetap (detik). Posisi dihitung analitik pada t = k·DT. */
    const val DT = 1.0 / 120.0
    const val MAX_TIME_S = 40.0

    enum class End { TARGET, GROUND, OBSTACLE, OUT_OF_WORLD, TIMEOUT }

    data class ShotResult(val hit: Boolean, val end: End, val path: List<Pt>, val closest: Double)

    fun velocity(spec: TrajectorySpec, shot: ShotInput): Pair<Double, Double> {
        val a = spec.a.value(shot.a)
        val b = spec.b.value(shot.b)
        return when (spec.control) {
            TrajectoryControl.POLAR -> {
                val rad = StrictMath.toRadians(a)
                b * StrictMath.cos(rad) to b * StrictMath.sin(rad)
            }
            TrajectoryControl.COMPONENTS -> a to b
        }
    }

    fun simulate(spec: TrajectorySpec, shot: ShotInput, keepPath: Boolean = false): ShotResult {
        val (vx, vy) = velocity(spec, shot)
        val g = spec.gravity
        val path = if (keepPath) ArrayList<Pt>(256) else null
        var px = spec.launchX
        var py = spec.launchY
        path?.add(Pt(px, py))
        var closest = hypot(px - spec.target.x, py - spec.target.y)
        var k = 1
        while (true) {
            val t = k * DT
            if (t > MAX_TIME_S) return ShotResult(false, End.TIMEOUT, path.orEmpty(), closest)
            val x = spec.launchX + vx * t
            val y = spec.launchY + vy * t - 0.5 * g * t * t
            val d = segmentDistance(px, py, x, y, spec.target.x, spec.target.y)
            closest = min(closest, d)
            if (d <= spec.target.r) {
                path?.add(Pt(x, y))
                return ShotResult(true, End.TARGET, path.orEmpty(), closest)
            }
            if (y < 0.0) {
                // titik potong dengan tanah untuk tampilan
                val f = py / (py - y)
                path?.add(Pt(px + (x - px) * f, 0.0))
                return ShotResult(false, End.GROUND, path.orEmpty(), closest)
            }
            if (spec.obstacles.any { it.contains(x, y) }) {
                path?.add(Pt(x, y))
                return ShotResult(false, End.OBSTACLE, path.orEmpty(), closest)
            }
            if (x > spec.world.x2 || x < spec.world.x1 - 1.0 || y > spec.world.y2 * 3) {
                path?.add(Pt(x, y))
                return ShotResult(false, End.OUT_OF_WORLD, path.orEmpty(), closest)
            }
            if (path != null && k % 3 == 0) path.add(Pt(x, y))
            px = x; py = y
            k++
        }
    }

    fun evaluate(spec: TrajectorySpec, s: TrajectorySubmission): GameOutcome {
        if (s.shots.isEmpty() || s.shots.size > min(spec.maxShots, GameLimits.MAX_SHOTS)) GameEngines.invalid("jumlah tembakan")
        s.shots.forEachIndexed { i, shot ->
            if (shot.a !in 0 until spec.a.count || shot.b !in 0 until spec.b.count) GameEngines.invalid("parameter di luar rentang")
            if (simulate(spec, shot).hit) {
                return GameOutcome(true, "Kena sasaran pada tembakan ${i + 1} (${describe(spec, shot)})", i)
            }
        }
        return GameOutcome(false, "Meleset ${s.shots.size}× (maks ${spec.maxShots} tembakan)", s.shots.size)
    }

    fun describe(spec: TrajectorySpec, shot: ShotInput): String = when (spec.control) {
        TrajectoryControl.POLAR -> "θ = ${fmt(spec.a.value(shot.a))}°, v = ${fmt(spec.b.value(shot.b))} m/s"
        TrajectoryControl.COMPONENTS -> "vₓ = ${fmt(spec.a.value(shot.a))} m/s, v_y = ${fmt(spec.b.value(shot.b))} m/s"
    }

    private fun segmentDistance(x1: Double, y1: Double, x2: Double, y2: Double, cx: Double, cy: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((cx - x1) * dx + (cy - y1) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(x1 + t * dx - cx, y1 + t * dy - cy)
    }
}

internal fun fmt(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
}

// ============================================================================ Micro Hunter

object MicroscopeEngine {
    enum class TapVerdict { HIT, WRONG, BLURRY, ZOOM_TOO_LOW }

    data class TapResult(val verdict: TapVerdict, val region: SceneRegion?)

    /**
     * Viewport -> DUNIA. Perbesaran 1 = lebar dunia tepat memenuhi lebar viewport. Hasilnya tidak
     * bergantung resolusi layar: titik layar relatif yang sama memberi titik dunia yang sama di HP mana pun.
     */
    fun screenToWorld(sx: Double, sy: Double, viewW: Double, viewH: Double, centerX: Double, centerY: Double, zoom: Double, worldW: Double): Pt {
        val scale = viewW / (worldW / zoom)
        return Pt((sx - viewW / 2) / scale + centerX, (sy - viewH / 2) / scale + centerY)
    }

    /** DUNIA -> viewport HP ini (dipakai menggambar ghost lawan di koordinat lokal). */
    fun worldToScreen(wx: Double, wy: Double, viewW: Double, viewH: Double, centerX: Double, centerY: Double, zoom: Double, worldW: Double): Pt {
        val scale = viewW / (worldW / zoom)
        return Pt(viewW / 2 + (wx - centerX) * scale, viewH / 2 + (wy - centerY) * scale)
    }

    fun focusOk(spec: MicroscopeSpec, focusPm: Int): Boolean = abs(focusPm / 1000.0 - spec.focusTarget) <= spec.focusTolerance + 1e-9

    fun judge(spec: MicroscopeSpec, tap: MicroTap, assist: Boolean): TapResult {
        if (tap.zoom / 100.0 + 1e-9 < spec.minTapZoom) return TapResult(TapVerdict.ZOOM_TOO_LOW, null)
        if (!focusOk(spec, tap.focus)) return TapResult(TapVerdict.BLURRY, null)
        val tol = spec.tapTolerance + if (assist) spec.assistTolerance else 0.0
        val x = tap.x.toDouble()
        val y = tap.y.toDouble()
        val target = spec.regions.filter { it.target }
        if (target.any { r -> r.polygons.any { inside(it, x, y) || distanceToEdge(it, x, y) <= tol } }) {
            return TapResult(TapVerdict.HIT, target.first())
        }
        val other = spec.regions.filter { !it.target }.firstOrNull { r -> r.polygons.any { inside(it, x, y) } }
        return TapResult(TapVerdict.WRONG, other)
    }

    fun evaluate(spec: MicroscopeSpec, s: MicroSubmission): GameOutcome {
        if (s.taps.isEmpty() || s.taps.size > GameLimits.MAX_TAPS) GameEngines.invalid("jumlah tap")
        var wrong = 0
        for (tap in s.taps) {
            when (judge(spec, tap, s.assist).verdict) {
                TapVerdict.HIT -> return GameOutcome(true, "Menunjuk ${spec.targetName} dengan benar; tap salah $wrong (maks ${spec.maxWrong})", wrong)
                TapVerdict.WRONG -> {
                    wrong++
                    if (wrong > spec.maxWrong) return GameOutcome(false, "Tap salah ${wrong}× (maks ${spec.maxWrong})", wrong)
                }
                TapVerdict.BLURRY, TapVerdict.ZOOM_TOO_LOW -> Unit // tidak dihitung, sama seperti di layar
            }
        }
        return GameOutcome(false, "Belum menunjuk ${spec.targetName}; tap salah $wrong", wrong)
    }

    fun inside(poly: List<Pt>, x: Double, y: Double): Boolean {
        var c = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

    fun distanceToEdge(poly: List<Pt>, x: Double, y: Double): Double {
        var best = Double.MAX_VALUE
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
            best = min(best, hypot(a.x + t * dx - x, a.y + t * dy - y))
        }
        return best
    }
}

// ============================================================================ Reaction Lab

object LabEngine {
    const val KW = 1e-14

    /** Keadaan bejana (model ideal: volume aditif, massa jenis air 1 g/mL, 25 °C). */
    data class LabState(
        val volumeMl: Double,
        val massG: Double,
        val species: Map<String, Double>,
        val reagentTotals: Map<String, Double>,
        val sequence: List<String>,
        val resets: Int,
    ) {
        fun amount(sp: String): Double = species[sp] ?: 0.0
    }

    fun initial(spec: LabSpec) = LabState(spec.initialVolume, spec.initialMass, spec.initialSpecies, emptyMap(), emptyList(), 0)

    /** Putar ulang aksi. Aksi sebelum RESET terakhir dibuang. */
    fun replay(spec: LabSpec, actions: List<LabAction>): LabState {
        var st = initial(spec)
        var resets = 0
        for (a in actions) {
            if (a.r == LabAction.RESET) {
                resets++
                if (resets > spec.maxResets) GameEngines.invalid("terlalu banyak ulang")
                st = initial(spec).copy(resets = resets)
                continue
            }
            st = apply(spec, st, a)
        }
        return st
    }

    fun apply(spec: LabSpec, st: LabState, a: LabAction): LabState {
        if (spec.module == LabModule.SEPARATION) {
            val tool = spec.tools.getOrNull(a.r) ?: GameEngines.invalid("alat ${a.r}")
            if (st.sequence.size >= 8) GameEngines.invalid("langkah terlalu banyak")
            return st.copy(sequence = st.sequence + tool.id)
        }
        val r = spec.reagents.getOrNull(a.r) ?: GameEngines.invalid("reagen ${a.r}")
        if (a.amt <= 0) GameEngines.invalid("jumlah harus positif")
        val stepMilli = Math.round(r.step * 1000)
        if (stepMilli > 0 && a.amt % stepMilli != 0L) GameEngines.invalid("jumlah bukan kelipatan langkah ${r.step}")
        val amount = a.amt / 1000.0
        val total = (st.reagentTotals[r.id] ?: 0.0) + amount
        if (total > r.max + 1e-9) GameEngines.invalid("${r.name} melebihi ${r.max} ${r.unit}")
        val species = st.species.toMutableMap()
        r.species.forEach { (k, perUnit) -> species[k] = (species[k] ?: 0.0) + perUnit * amount }
        val vol = st.volumeMl + r.volumePerUnit * amount
        if (vol > spec.vesselCapacity + 1e-9) GameEngines.invalid("bejana penuh")
        return st.copy(
            volumeMl = vol,
            massG = st.massG + r.massPerUnit * amount,
            species = species,
            reagentTotals = st.reagentTotals + (r.id to total),
        )
    }

    /** Terapkan reaksi sempurna dengan pereaksi pembatas. Massa total tidak berubah. */
    fun react(spec: LabSpec, st: LabState): LabState {
        val rx = spec.reaction ?: return st
        val extent = rx.reactants.minOf { (sp, coef) -> st.amount(sp) / coef }
        if (extent <= 0.0) return st
        val species = st.species.toMutableMap()
        rx.reactants.forEach { (sp, coef) -> species[sp] = max(0.0, (species[sp] ?: 0.0) - coef * extent) }
        rx.products.forEach { (sp, coef) -> species[sp] = (species[sp] ?: 0.0) + coef * extent }
        return st.copy(species = species)
    }

    /** pH larutan kuat ideal: c = ([H⁺]−[OH⁻]) bersih; [H⁺] = (c + √(c² + 4Kw)) / 2. */
    fun ph(st: LabState): Double? {
        if (st.volumeMl <= 0.0) return null
        val c = (st.amount("H+") - st.amount("OH-")) / (st.volumeMl / 1000.0)
        val h = (c + sqrt(c * c + 4 * KW)) / 2
        return -log10(h)
    }

    fun concentration(st: LabState, species: String, unit: String): Double? = when (unit) {
        "M" -> if (st.volumeMl > 0) st.amount(species) / (st.volumeMl / 1000.0) else null
        "%" -> if (st.massG > 0) 100.0 * st.amount(species) / st.massG else null
        else -> null
    }

    data class TargetCheck(val ok: Boolean, val text: String)

    fun check(spec: LabSpec, raw: LabState): List<TargetCheck> {
        val st = react(spec, raw)
        return spec.targets.map { t ->
            when (t) {
                is LabTarget.Concentration -> {
                    val c = concentration(st, t.species, t.unit)
                    var ok = c != null && abs(c - t.value) <= t.relTol * t.value + 1e-12
                    if (t.volume != null) ok = ok && abs(st.volumeMl - t.volume) <= t.volumeTol + 1e-9
                    if (t.mass != null) ok = ok && abs(st.massG - t.mass) <= t.massTol + 1e-9
                    TargetCheck(ok, "Konsentrasi ${t.species}: ${c?.let { fmt3(it) } ?: "—"} ${t.unit} (target ${fmt3(t.value)} ±${fmt3(t.relTol * 100)}%)" +
                        (t.volume?.let { ", volume ${fmt(st.volumeMl)} mL (target ${fmt(it)})" } ?: "") +
                        (t.mass?.let { ", massa ${fmt(st.massG)} g (target ${fmt(it)})" } ?: ""))
                }
                is LabTarget.Ph -> {
                    val p = ph(st)
                    TargetCheck(p != null && p in t.min..t.max, "pH akhir ${p?.let { fmt(it) } ?: "—"} (target ${fmt(t.min)}–${fmt(t.max)})")
                }
                is LabTarget.Amounts -> {
                    val ok = t.conditions.all { abs(st.amount(it.species) - it.value) <= it.tol + 1e-9 }
                    TargetCheck(ok, t.conditions.joinToString("; ") { "${it.species} ${fmt3(st.amount(it.species))} mol (target ${fmt3(it.value)})" })
                }
                is LabTarget.ReagentTotal -> {
                    val v = raw.reagentTotals[t.reagent] ?: 0.0
                    val name = spec.reagents.firstOrNull { it.id == t.reagent }?.name ?: t.reagent
                    TargetCheck(abs(v - t.value) <= t.tol + 1e-9, "$name dituang ${fmt(v)} (target ${fmt(t.value)} ±${fmt(t.tol)})")
                }
                is LabTarget.Sequence -> {
                    val ok = t.accepted.any { it == raw.sequence }
                    val names = raw.sequence.map { id -> spec.tools.firstOrNull { it.id == id }?.name ?: id }
                    TargetCheck(ok, "Urutan: ${names.ifEmpty { listOf("—") }.joinToString(" → ")}")
                }
            }
        }
    }

    fun evaluate(spec: LabSpec, s: LabSubmission): GameOutcome {
        if (s.actions.isEmpty() || s.actions.size > GameLimits.MAX_LAB_ACTIONS) GameEngines.invalid("jumlah aksi")
        val checks = check(spec, replay(spec, s.actions))
        return GameOutcome(checks.all { it.ok }, checks.joinToString("; ") { it.text }, checks.count { !it.ok })
    }

    private fun fmt3(v: Double): String {
        val r = Math.round(v * 1000) / 1000.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
    }
}
