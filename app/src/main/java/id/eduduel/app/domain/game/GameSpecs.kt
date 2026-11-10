package id.eduduel.app.domain.game

import id.eduduel.app.domain.Subject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Empat mesin mini-game Duelmu. Satu gameplay khas per mata pelajaran; setiap soal game adalah
 * KONFIGURASI untuk salah satu mesin ini (bukan MCQ yang dipaksakan).
 */
@Serializable
enum class GameType(val subject: Subject, val label: String) {
    @SerialName("asteroid") ASTEROID(Subject.MATEMATIKA, "Asteroid Solver"),
    @SerialName("trajectory") TRAJECTORY(Subject.FISIKA, "Trajectory Lab"),
    @SerialName("microscope") MICROSCOPE(Subject.BIOLOGI, "Micro Hunter"),
    @SerialName("lab") LAB(Subject.KIMIA, "Reaction Lab");

    companion object {
        fun forSubject(s: Subject): GameType = entries.first { it.subject == s }
    }
}

/**
 * Konfigurasi soal game (dibaca dari bank soal, diskriminator JSON `engine`).
 * Semua angka yang memengaruhi penilaian bersifat diskrit/deterministik agar Host dan semua Tamu
 * menghitung hasil yang IDENTIK.
 */
@Serializable
sealed interface GameSpec {
    val type: GameType
    val hint: String?
}

// ============================================================================ Matematika

/**
 * Asteroid Solver: asteroid berlabel token jatuh; pemain menembak token SESUAI URUTAN slot kosong.
 * [template] memuat penanda slot `{0}`, `{1}`, ... (mis. "f'(x) = {0} + {1}").
 * Token jawaban & pengecoh sudah dinormalisasi oleh builder; pengecoh tidak boleh setara dengan
 * token jawaban (dicek validator lewat evaluasi numerik).
 */
@Serializable
@SerialName("asteroid")
data class AsteroidSpec(
    val template: String,
    val answerTokens: List<String>,
    val distractorTokens: List<String>,
    /** Tembakan salah yang masih diizinkan; salah ke-(maxWrong+1) = kalah. */
    val maxWrong: Int,
    /** Kecepatan jatuh (tinggi arena per detik). */
    val fallSpeed: Double,
    /** Jeda antar-kemunculan asteroid (detik). */
    val spawnInterval: Double,
    override val hint: String? = null,
) : GameSpec {
    override val type: GameType get() = GameType.ASTEROID
    val tokens: List<String> get() = answerTokens + distractorTokens
}

// ============================================================================ Fisika

@Serializable
enum class TrajectoryControl {
    /** a = sudut elevasi (derajat), b = kelajuan awal (m/s). */
    @SerialName("polar") POLAR,
    /** a = vₓ (m/s), b = v_y (m/s). */
    @SerialName("components") COMPONENTS,
}

/** Rentang parameter diskrit: nilai = min + indeks × step. min == max berarti terkunci. */
@Serializable
data class ParamRange(val min: Double, val max: Double, val step: Double, val initialIndex: Int = 0) {
    val count: Int get() = if (step <= 0.0) 1 else ((max - min) / step + 1e-9).toInt() + 1
    val fixed: Boolean get() = count <= 1
    fun value(index: Int): Double = min + index.coerceIn(0, count - 1) * step
}

@Serializable
data class Circle(val x: Double, val y: Double, val r: Double)

@Serializable
data class Box(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    fun contains(x: Double, y: Double) = x in minOf(x1, x2)..maxOf(x1, x2) && y in minOf(y1, y2)..maxOf(y1, y2)
}

/**
 * Trajectory Lab: atur parameter peluncuran lalu tembakkan proyektil ke sasaran.
 * Satuan SI (meter, detik). Gerak tanpa hambatan udara; posisi dihitung analitik setiap
 * [TrajectoryEngine.DT] detik sehingga tidak ada galat integrasi.
 */
@Serializable
@SerialName("trajectory")
data class TrajectorySpec(
    val gravity: Double,
    val launchX: Double = 0.0,
    val launchY: Double = 0.0,
    val control: TrajectoryControl,
    val a: ParamRange,
    val b: ParamRange,
    val target: Circle,
    val obstacles: List<Box> = emptyList(),
    /** Batas dunia untuk tampilan dan penghentian simulasi. */
    val world: Box,
    val maxShots: Int,
    /** Teks satuan/label tambahan yang ditampilkan (mis. "g = 10 m/s²"). */
    val note: String? = null,
    override val hint: String? = null,
) : GameSpec {
    override val type: GameType get() = GameType.TRAJECTORY
}

// ============================================================================ Biologi

@Serializable
data class Pt(val x: Double, val y: Double)

/**
 * Primitive gambar vektor orisinal (koordinat DUNIA).
 * k: "e" elips [cx,cy,rx,ry,rotDeg] · "p" poligon [x1,y1,...] · "l" polyline [x1,y1,...]
 *    · "c" kurva tertutup halus (Catmull-Rom) [x1,y1,...] · "o" kurva terbuka halus.
 */
@Serializable
data class SceneShape(
    val k: String,
    val p: List<Double>,
    val fill: String? = null,
    val stroke: String? = null,
    val w: Double = 0.0,
)

/** Daerah bernama di preparat (dunia). Daerah target = jawaban; lainnya pengecoh/umpan balik. */
@Serializable
data class SceneRegion(
    val id: String,
    val name: String,
    val polygons: List<List<Pt>>,
    val target: Boolean = false,
)

/**
 * Micro Hunter: pan/zoom/fokus mikroskop virtual, lalu tunjuk struktur yang diminta.
 * Tap hanya dihitung bila fokus tajam (|fokus − focusTarget| ≤ focusTolerance) dan perbesaran
 * ≥ minTapZoom (objek kecil tidak bisa ditebak dari perbesaran rendah).
 */
@Serializable
@SerialName("microscope")
data class MicroscopeSpec(
    val worldW: Double,
    val worldH: Double,
    val background: String,
    val shapes: List<SceneShape>,
    val regions: List<SceneRegion>,
    val targetName: String,
    val focusTarget: Double,
    val focusTolerance: Double,
    val minZoom: Double,
    val maxZoom: Double,
    val minTapZoom: Double,
    /** Toleransi tap (satuan dunia) di sekitar tepi daerah target. */
    val tapTolerance: Double,
    /** Toleransi tambahan saat opsi aksesibilitas "Bantuan sentuh" aktif. */
    val assistTolerance: Double,
    val maxWrong: Int,
    val stain: String? = null,
    override val hint: String? = null,
) : GameSpec {
    override val type: GameType get() = GameType.MICROSCOPE
}

// ============================================================================ Kimia

@Serializable
enum class LabModule {
    @SerialName("dilution") DILUTION,
    @SerialName("mixing") MIXING,
    @SerialName("neutralization") NEUTRALIZATION,
    @SerialName("stoichiometry") STOICHIOMETRY,
    @SerialName("separation") SEPARATION,
}

@Serializable
enum class LabReadout {
    @SerialName("volume") VOLUME,
    @SerialName("mass") MASS,
    @SerialName("universal") UNIVERSAL_INDICATOR,
    @SerialName("phenolphthalein") PHENOLPHTHALEIN,
    @SerialName("ph_meter") PH_METER,
    @SerialName("moles") MOLES,
}

/**
 * Reagen/alat tuang. Setiap satuan [unit] menambah [volumePerUnit] mL, [massPerUnit] g, dan
 * jumlah spesies [species] (mol untuk larutan/ padatan molar; gram untuk zat terlarut %-massa).
 */
@Serializable
data class LabReagent(
    val id: String,
    val name: String,
    val unit: String,
    val step: Double,
    val bigStep: Double,
    val max: Double,
    val volumePerUnit: Double = 0.0,
    val massPerUnit: Double = 0.0,
    val species: Map<String, Double> = emptyMap(),
    val color: String = "#88C0D0",
    val tool: String = "pipette",
)

@Serializable
data class LabTool(val id: String, val name: String, val description: String)

/** Reaksi ideal berjalan sempurna sampai salah satu pereaksi habis (pereaksi pembatas). */
@Serializable
data class LabReaction(val equation: String, val reactants: Map<String, Int>, val products: Map<String, Int>)

@Serializable
data class AmountCondition(val species: String, val value: Double, val tol: Double)

@Serializable
sealed interface LabTarget {
    /** Konsentrasi spesies. unit "M" (mol/L) atau "%" (persen massa). */
    @Serializable
    @SerialName("concentration")
    data class Concentration(
        val species: String,
        val unit: String,
        val value: Double,
        val relTol: Double,
        val volume: Double? = null,
        val volumeTol: Double = 0.0,
        val mass: Double? = null,
        val massTol: Double = 0.0,
    ) : LabTarget

    @Serializable
    @SerialName("ph")
    data class Ph(val min: Double, val max: Double) : LabTarget

    /** Jumlah spesies (mol) SESUDAH reaksi. */
    @Serializable
    @SerialName("amounts")
    data class Amounts(val conditions: List<AmountCondition>) : LabTarget

    /** Total reagen yang dituang (mis. volume titran pada titik ekuivalen). */
    @Serializable
    @SerialName("reagent_total")
    data class ReagentTotal(val reagent: String, val value: Double, val tol: Double) : LabTarget

    @Serializable
    @SerialName("sequence")
    data class Sequence(val accepted: List<List<String>>) : LabTarget
}

@Serializable
@SerialName("lab")
data class LabSpec(
    val module: LabModule,
    /** Model/penyederhanaan sains yang dipakai (ditampilkan ke pemain). */
    val model: String,
    val vessel: String,
    val vesselCapacity: Double,
    /** Isi awal bejana: volume (mL), massa (g), spesies. */
    val initialVolume: Double = 0.0,
    val initialMass: Double = 0.0,
    val initialSpecies: Map<String, Double> = emptyMap(),
    val reagents: List<LabReagent> = emptyList(),
    val tools: List<LabTool> = emptyList(),
    val reaction: LabReaction? = null,
    val targets: List<LabTarget>,
    val readouts: List<LabReadout>,
    val maxResets: Int = 2,
    override val hint: String? = null,
) : GameSpec {
    override val type: GameType get() = GameType.LAB
}
