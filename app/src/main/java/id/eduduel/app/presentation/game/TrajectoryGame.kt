package id.eduduel.app.presentation.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.eduduel.app.R
import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.game.ParamRange
import id.eduduel.app.domain.game.Pt
import id.eduduel.app.domain.game.ShotInput
import id.eduduel.app.domain.game.TrajectoryControl
import id.eduduel.app.domain.game.TrajectoryEngine
import id.eduduel.app.domain.game.TrajectorySpec
import id.eduduel.app.domain.game.TrajectorySubmission
import id.eduduel.app.net.protocol.LaunchAction
import id.eduduel.app.net.protocol.TrajectoryGhost
import id.eduduel.app.net.room.GhostInterpolator
import id.eduduel.app.presentation.components.GameButton
import id.eduduel.app.presentation.components.GameOutlinedButton
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private class TrajectoryPlay(spec: TrajectorySpec) {
    var a by mutableIntStateOf(spec.a.initialIndex.coerceIn(0, spec.a.count - 1))
    var b by mutableIntStateOf(spec.b.initialIndex.coerceIn(0, spec.b.count - 1))
    val shots = mutableStateListOf<ShotInput>()
    val paths = mutableStateListOf<TrajectoryEngine.ShotResult>()
    var flyingSince by mutableLongStateOf(NEVER_MS)
    var submitted by mutableStateOf(false)
}

/** Durasi animasi = waktu terbang sebenarnya (dibatasi 4 dtk agar tidak memakan waktu soal). */
private fun animDurationMs(res: TrajectoryEngine.ShotResult): Long =
    (res.path.size * 3 * TrajectoryEngine.DT * 1000).toLong().coerceIn(600, 4_000)

@Composable
fun TrajectoryGame(spec: TrajectorySpec, env: GameEnv, cb: GameCallbacks) {
    val key = env.round.question.id to env.startedAtMs
    val play = remember(key) { TrajectoryPlay(spec) }
    val now = rememberFrameTime(env.clock, running = true)
    val callbacks by rememberUpdatedState(cb)
    val measurer = rememberTextMeasurer()
    val lastResult = play.paths.lastOrNull()
    val flying = lastResult != null && msSince(now, play.flyingSince) < animDurationMs(lastResult)
    val hit = play.paths.any { it.hit }
    val active = !env.locked && !play.submitted && !flying
    val cannonArtwork = rememberGameArtwork(R.drawable.game_cannon)
    val projectileArtwork = rememberGameArtwork(R.drawable.game_projectile)
    val targetArtwork = rememberGameArtwork(R.drawable.game_target)
    val obstacleArtwork = rememberGameArtwork(R.drawable.game_obstacle)

    // Kirim hasil SETELAH animasi selesai (berhasil atau tembakan habis).
    LaunchedEffect(play.paths.size, flying) {
        if (!flying && play.paths.isNotEmpty() && !play.submitted && (hit || play.shots.size >= spec.maxShots)) {
            play.submitted = true
            callbacks.onSubmit(TrajectorySubmission(play.shots.toList()))
        }
    }
    LaunchedEffect(key) {
        while (true) {
            val (aim, power) = aimPower(spec, ShotInput(play.a, play.b))
            callbacks.onGhost(TrajectoryGhost(aim, power, play.shots.size, done = play.submitted || env.locked))
            delay(60)
        }
    }

    Text(spec.note ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Tembakan: ${play.shots.size}/${spec.maxShots} · sasaran radius ${fmtNum(spec.target.r)} m",
        style = MaterialTheme.typography.bodyMedium)
    GhostLegend(env)

    val w = spec.world
    val ratio = ((w.x2 - w.x1) / (w.y2 - w.y1)).toFloat().coerceIn(1.0f, 2.2f)
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE3F2FD))
        .semantics { contentDescription = "Lapangan lintasan. Sasaran di x ${fmtNum(spec.target.x)} m, y ${fmtNum(spec.target.y)} m." }) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            // Make room for the cannon carriage, including worlds whose launcher is at x = y = 0.
            val left = 38.dp.toPx()
            val bottom = 28.dp.toPx()
            val sx = (size.width - left - 12.dp.toPx()) / (w.x2 - w.x1).toFloat()
            val sy = (size.height - bottom - 16.dp.toPx()) / (w.y2 - w.y1).toFloat()
            val sc = min(sx, sy)
            fun P(x: Double, y: Double) = Offset(left + ((x - w.x1) * sc).toFloat(), size.height - bottom - ((y - w.y1) * sc).toFloat())
            // grid 5 m
            var gx = 0.0
            while (gx <= w.x2) {
                drawLine(Color(0x22000000), P(gx, 0.0), P(gx, w.y2), 1f); gx += 5.0
            }
            val ground = P(w.x1, 0.0).y
            drawRect(Color(0xFFE8D6BB), topLeft = Offset(0f, ground), size = Size(size.width, (size.height - ground).coerceAtLeast(0f)))
            drawLine(Color(0xFF6E9270), Offset(0f, ground), Offset(size.width, ground), 3.dp.toPx())
            spec.obstacles.forEach { o ->
                val tl = P(min(o.x1, o.x2), max(o.y1, o.y2))
                val br = P(max(o.x1, o.x2), min(o.y1, o.y2))
                drawObstacleArtwork(obstacleArtwork, Rect(tl, br))
            }
            val tc = P(spec.target.x, spec.target.y)
            val targetDiameter = (spec.target.r * sc * 2).toFloat().coerceAtLeast(10.dp.toPx())
            drawArtwork(targetArtwork, artworkBounds(tc, targetDiameter), fillBounds = true)
            val launch = P(spec.launchX, spec.launchY)
            if (spec.launchY > 0) drawRect(Color(0xFFA1887F), topLeft = Offset(0f, launch.y), size = Size(launch.x + 14.dp.toPx(), size.height - launch.y))

            // ghost: meriam + arah bidik + proyektil semitransparan (tidak memengaruhi proyektil lokal)
            if (env.ghostStyle.enabled) {
                env.ghosts.forEach { g ->
                    val track = g.track ?: return@forEach
                    val gs = GhostInterpolator.at(track, now) as? TrajectoryGhost
                    val alpha = env.ghostStyle.opacity * if (gs?.done == true || g.locked) 0.6f else 1f
                    val aimDeg = (gs?.aim ?: 0.5f) * 90f
                    drawCannonArtwork(cannonArtwork, launch, aimDeg, alpha, g.color)
                    track.events.forEach { e ->
                        val l = e.action as? LaunchAction ?: return@forEach
                        val age = now - e.receivedAtMs
                        if (l.a != null && l.b != null && env.ghostMode == GhostMode.LIVE && age < 4_000) {
                            val shot = ShotInput(l.a.coerceIn(0, spec.a.count - 1), l.b.coerceIn(0, spec.b.count - 1))
                            val res = TrajectoryEngine.simulate(spec, shot, keepPath = true)
                            drawPathPts(res.path, ::P, g.color.copy(alpha = alpha), dashed = true, upTo = 1f)
                            val f = (age.toFloat() / animDurationMs(res)).coerceIn(0f, 1f)
                            pointAt(res.path, f)?.let { drawArtwork(projectileArtwork, artworkBounds(P(it.x, it.y), 12.dp.toPx()), alpha, g.color) }
                        } else if (age < 600) {
                            drawCircle(g.color.copy(alpha = alpha * (1 - age / 600f)), 18.dp.toPx() * (age / 600f + 0.3f), launch)
                        }
                    }
                    drawText(measurer, g.name.take(10), launch + Offset(10.dp.toPx(), -30.dp.toPx() - 12.dp.toPx() * env.ghosts.indexOf(g)),
                        style = TextStyle(color = g.color.copy(alpha = (alpha + 0.4f).coerceAtMost(1f)), fontSize = 10.sp))
                }
            }

            // lintasan sendiri
            play.paths.forEachIndexed { i, res ->
                val isLast = i == play.paths.lastIndex
                val f = if (isLast) (msSince(now, play.flyingSince).toFloat() / animDurationMs(res)).coerceIn(0f, 1f) else 1f
                drawPathPts(res.path, ::P, if (isLast) env.myColor else env.myColor.copy(alpha = 0.35f), dashed = !isLast, upTo = f)
                if (isLast) pointAt(res.path, f)?.let { drawArtwork(projectileArtwork, artworkBounds(P(it.x, it.y), 12.dp.toPx())) }
            }
            val (aimN, _) = aimPower(spec, ShotInput(play.a, play.b))
            drawCannonArtwork(cannonArtwork, launch, aimN * 90f)
        }
        if (!flying && lastResult != null) {
            Text(
                if (lastResult.hit) "KENA! 🎯" else "Meleset (${endText(lastResult.end)}; jarak terdekat ${fmtNum(lastResult.closest)} m)",
                color = if (lastResult.hit) Color(0xFF2E7D32) else Color(0xFFC62828), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    val (labelA, unitA, labelB, unitB) = when (spec.control) {
        TrajectoryControl.POLAR -> listOf("Sudut θ", "°", "Kelajuan v", "m/s")
        TrajectoryControl.COMPONENTS -> listOf("vₓ", "m/s", "v_y", "m/s")
    }
    ParamControl(labelA, unitA, spec.a, play.a, active) { play.a = it }
    ParamControl(labelB, unitB, spec.b, play.b, active) { play.b = it }
    GameButton(
        onClick = {
            if (!active || play.shots.size >= spec.maxShots) return@GameButton
            val shot = ShotInput(play.a, play.b)
            val res = TrajectoryEngine.simulate(spec, shot, keepPath = true)
            play.shots += shot
            play.paths += res
            play.flyingSince = env.clock.nowMs()
            callbacks.onAction(LaunchAction(shot.a, shot.b, res.hit))
        },
        enabled = active && play.shots.size < spec.maxShots,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("LUNCURKAN (${spec.maxShots - play.shots.size} tersisa)") }
    when {
        play.submitted && hit -> GameLockedNote("Sasaran kena! Menunggu hasil ronde…")
        play.submitted -> GameLockedNote("Tembakan habis — jawaban dikunci.")
        env.locked -> GameLockedNote("Jawaban terkunci.")
    }
}

@Composable
private fun ParamControl(label: String, unit: String, range: ParamRange, index: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val value = range.value(index)
    if (range.fixed) {
        Text("$label = ${fmtNum(value)} $unit (terkunci)", style = MaterialTheme.typography.titleSmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label = ${fmtNum(value)} $unit", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameOutlinedButton(onClick = { onChange((index - 1).coerceAtLeast(0)) }, enabled = enabled && index > 0, modifier = Modifier.width(56.dp)) { Text("−") }
            Slider(
                value = index.toFloat(), onValueChange = { onChange(it.toInt().coerceIn(0, range.count - 1)) },
                valueRange = 0f..(range.count - 1).toFloat(), enabled = enabled, modifier = Modifier.weight(1f).semantics {
                    stateDescription = "${fmtNum(value)} $unit"
                },
            )
            GameOutlinedButton(onClick = { onChange((index + 1).coerceAtMost(range.count - 1)) }, enabled = enabled && index < range.count - 1, modifier = Modifier.width(56.dp)) { Text("+") }
        }
    }
}

/** Arah bidik ternormalisasi 0..1 (0..90°) dan kekuatan relatif 0..1 untuk ghost. */
internal fun aimPower(spec: TrajectorySpec, shot: ShotInput): Pair<Float, Float> {
    val (vx, vy) = TrajectoryEngine.velocity(spec, shot)
    val maxV = when (spec.control) {
        TrajectoryControl.POLAR -> spec.b.max
        TrajectoryControl.COMPONENTS -> hypot(spec.a.max, spec.b.max)
    }.coerceAtLeast(1.0)
    val deg = Math.toDegrees(atan2(vy, vx)).coerceIn(0.0, 90.0)
    return (deg / 90.0).toFloat() to (hypot(vx, vy) / maxV).toFloat().coerceIn(0f, 1f)
}

private fun pointAt(path: List<Pt>, f: Float): Pt? {
    if (path.isEmpty()) return null
    val i = ((path.size - 1) * f).toInt().coerceIn(0, path.lastIndex)
    return path[i]
}

private fun DrawScope.drawPathPts(path: List<Pt>, toPx: (Double, Double) -> Offset, color: Color, dashed: Boolean, upTo: Float) {
    if (path.size < 2) return
    val n = ((path.size - 1) * upTo).toInt().coerceIn(1, path.lastIndex)
    val p = Path()
    path.take(n + 1).forEachIndexed { i, pt ->
        val o = toPx(pt.x, pt.y)
        if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
    }
    drawPath(p, color, style = Stroke(width = 3.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null))
}

private fun endText(e: TrajectoryEngine.End) = when (e) {
    TrajectoryEngine.End.GROUND -> "jatuh ke tanah"
    TrajectoryEngine.End.OBSTACLE -> "menabrak rintangan"
    TrajectoryEngine.End.OUT_OF_WORLD -> "keluar lapangan"
    TrajectoryEngine.End.TIMEOUT -> "terlalu lama"
    TrajectoryEngine.End.TARGET -> "kena"
}

internal fun fmtNum(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
}
