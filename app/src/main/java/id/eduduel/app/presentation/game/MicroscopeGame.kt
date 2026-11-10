package id.eduduel.app.presentation.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.eduduel.app.R
import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.game.MicroSubmission
import id.eduduel.app.domain.game.MicroTap
import id.eduduel.app.domain.game.MicroscopeEngine
import id.eduduel.app.domain.game.MicroscopeSpec
import id.eduduel.app.domain.game.SceneShape
import id.eduduel.app.net.protocol.MicroGhost
import id.eduduel.app.net.protocol.TapAction
import id.eduduel.app.net.room.GhostInterpolator
import id.eduduel.app.presentation.components.GameOutlinedButton
import id.eduduel.app.presentation.components.LocalGameVisualEffects
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Path dunia yang sudah dibangun sekali per soal. */
internal class ScenePath(val path: Path, val fill: Color?, val stroke: Color?, val width: Float)

internal fun buildScene(spec: MicroscopeSpec): List<ScenePath> = spec.shapes.map { s -> ScenePath(shapePath(s), s.fill?.let { parseColor(it) }, s.stroke?.let { parseColor(it) }, s.w.toFloat()) }

private fun shapePath(s: SceneShape): Path {
    val p = s.p
    val path = Path()
    when (s.k) {
        "e" -> {
            val (cx, cy, rx, ry) = listOf(p[0], p[1], p[2], p[3])
            val rot = Math.toRadians(p[4])
            val c = cos(rot); val sn = sin(rot)
            for (i in 0 until 32) {
                val t = 2 * Math.PI * i / 32
                val x = rx * cos(t); val y = ry * sin(t)
                val px = (cx + x * c - y * sn).toFloat(); val py = (cy + x * sn + y * c).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
        "p", "l" -> {
            for (i in 0 until p.size / 2) {
                val x = p[2 * i].toFloat(); val y = p[2 * i + 1].toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (s.k == "p") path.close()
        }
        "c", "o" -> {
            val pts = (0 until p.size / 2).map { Offset(p[2 * it].toFloat(), p[2 * it + 1].toFloat()) }
            val closed = s.k == "c"
            val n = pts.size
            if (n < 2) return path
            path.moveTo(pts[0].x, pts[0].y)
            val segs = if (closed) n else n - 1
            for (i in 0 until segs) {
                val p0 = if (closed || i > 0) pts[(i - 1 + n) % n] else pts[i]
                val p1 = pts[i]; val p2 = pts[(i + 1) % n]
                val p3 = if (closed || i + 2 < n) pts[(i + 2) % n] else p2
                path.cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
            }
            if (closed) path.close()
        }
    }
    return path
}

internal fun DrawScope.drawScene(scene: List<ScenePath>, alpha: Float = 1f) {
    scene.forEach { sp ->
        sp.fill?.let { drawPath(sp.path, it, alpha = alpha) }
        if (sp.stroke != null && sp.width > 0f) drawPath(sp.path, sp.stroke, alpha = alpha, style = Stroke(width = sp.width))
    }
}

private class MicroPlay(spec: MicroscopeSpec) {
    var cx by mutableFloatStateOf((spec.worldW / 2).toFloat())
    var cy by mutableFloatStateOf((spec.worldH / 2).toFloat())
    var zoom by mutableFloatStateOf(spec.minZoom.toFloat())
    var focus by mutableFloatStateOf(if (spec.focusTarget > 0.5) 0.08f else 0.92f)
    var cursorX by mutableFloatStateOf(0.5f)
    var cursorY by mutableFloatStateOf(0.5f)
    val taps = mutableStateListOf<MicroTap>()
    var wrong by mutableIntStateOf(0)
    var message by mutableStateOf<Pair<String, Boolean>?>(null)
    var messageAt by mutableLongStateOf(0L)
    var submitted by mutableStateOf(false)
    var assist by mutableStateOf(false)
}

/** One recognizer owns a gesture: a stationary release answers; a pan/pinch only moves the view. */
private suspend fun PointerInputScope.detectMicroscopeGestures(
    onTap: (Offset) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var pan = Offset.Zero
    var zoom = 1f
    var transformed = false
    var multiplePointers = false
    do {
        val event = awaitPointerEvent()
        if (event.changes.any { it.isConsumed }) break
        multiplePointers = multiplePointers || event.changes.count { it.pressed } > 1
        val panChange = event.calculatePan()
        val zoomChange = event.calculateZoom()
        if (!transformed) {
            pan += panChange
            zoom *= zoomChange
            val zoomMotion = abs(1 - zoom) * event.calculateCentroidSize(useCurrent = false)
            transformed = pan.getDistance() > viewConfiguration.touchSlop || zoomMotion > viewConfiguration.touchSlop
        }
        if (transformed) {
            onTransform(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
        if (event.changes.none { it.pressed }) {
            if (!transformed && !multiplePointers) {
                event.changes.firstOrNull { it.id == down.id }?.let {
                    onTap(it.position)
                    it.consume()
                }
            }
            break
        }
    } while (true)
}

@Composable
fun MicroscopeGame(spec: MicroscopeSpec, env: GameEnv, cb: GameCallbacks) {
    val key = env.round.question.id to env.startedAtMs
    val play = remember(key) { MicroPlay(spec).also { it.assist = env.assist } }
    val scene = remember(spec) { buildScene(spec) }
    val bg = remember(spec) { parseColor(spec.background, Color.White) }
    val now = rememberFrameTime(env.clock, running = true)
    val callbacks by rememberUpdatedState(cb)
    val visuals = LocalGameVisualEffects.current
    val measurer = rememberTextMeasurer()
    val active = !env.locked && !play.submitted
    val rimArtwork = rememberGameArtwork(R.drawable.game_microscope_rim)
    var viewSize by remember { mutableStateOf(Size(1f, 1f)) }
    val W = spec.worldW.toFloat()
    val H = spec.worldH.toFloat()

    fun scale() = viewSize.width / (W / play.zoom)
    fun clampCenter() {
        play.cx = play.cx.coerceIn(0f, W)
        play.cy = play.cy.coerceIn(0f, H)
    }
    fun toWorld(o: Offset): Offset {
        val p = MicroscopeEngine.screenToWorld(o.x.toDouble(), o.y.toDouble(), viewSize.width.toDouble(), viewSize.height.toDouble(),
            play.cx.toDouble(), play.cy.toDouble(), play.zoom.toDouble(), spec.worldW)
        return Offset(p.x.toFloat(), p.y.toFloat())
    }

    fun tap(o: Offset) {
        if (!active) return
        // The illustrated rim is decorative, outside the actual circular specimen viewport.
        if ((o - Offset(viewSize.width / 2, viewSize.height / 2)).getDistance() > viewSize.minDimension * 0.48f) return
        val wpt = toWorld(o)
        val t = MicroTap(wpt.x.toInt(), wpt.y.toInt(), (play.zoom * 100).toInt(), (play.focus * 1000).toInt())
        play.cursorX = wpt.x / W; play.cursorY = wpt.y / H
        val res = MicroscopeEngine.judge(spec, t, play.assist)
        play.messageAt = env.clock.nowMs()
        when (res.verdict) {
            MicroscopeEngine.TapVerdict.BLURRY -> { play.message = "Masih kabur — atur fokus dulu (tap tidak dihitung)." to false; return }
            MicroscopeEngine.TapVerdict.ZOOM_TOO_LOW -> { play.message = "Perbesar minimal ×${fmtNum(spec.minTapZoom)} (tap tidak dihitung)." to false; return }
            else -> Unit
        }
        play.taps += t
        val hit = res.verdict == MicroscopeEngine.TapVerdict.HIT
        callbacks.onAction(TapAction(wpt.x / W, wpt.y / H, hit))
        if (hit) {
            play.message = "Tepat: ${spec.targetName}!" to true
            play.submitted = true
            callbacks.onSubmit(MicroSubmission(play.taps.toList(), play.assist))
        } else {
            play.wrong++
            visuals?.loseLife()
            play.message = (res.region?.let { "Itu ${it.name}, bukan ${spec.targetName}." } ?: "Bukan ${spec.targetName}.") to false
            if (play.wrong > spec.maxWrong || play.taps.size >= 32) {
                play.submitted = true
                callbacks.onSubmit(MicroSubmission(play.taps.toList(), play.assist))
            }
        }
    }

    LaunchedEffect(key) {
        while (true) {
            val visW = W / play.zoom
            val visH = visW * (viewSize.height / viewSize.width)
            callbacks.onGhost(
                MicroGhost(
                    vx = ((play.cx - visW / 2) / W), vy = ((play.cy - visH / 2) / H), vw = visW / W, vh = visH / H,
                    focus = play.focus, cx = play.cursorX, cy = play.cursorY, done = play.submitted || env.locked,
                ),
            )
            delay(60)
        }
    }

    Text("Tap salah tersisa: ${(spec.maxWrong - play.wrong).coerceAtLeast(0)} · perbesaran ×${fmtNum(play.zoom.toDouble())}" +
        " (tap min ×${fmtNum(spec.minTapZoom)})", style = MaterialTheme.typography.bodyMedium)
    GhostLegend(env)

    val defocus = abs(play.focus - spec.focusTarget.toFloat())
    val sharp = defocus <= spec.focusTolerance
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Box(
            Modifier.fillMaxWidth(0.72f).aspectRatio(1f).align(Alignment.Center).clip(CircleShape).background(Color(0xFF1B1B1B))
                .onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(key, active) {
                    if (active) detectMicroscopeGestures(onTap = { tap(it) }) { centroid, pan, zoomChange ->
                        val s = scale()
                        play.cx -= pan.x / s; play.cy -= pan.y / s
                        play.zoom = (play.zoom * zoomChange).coerceIn(spec.minZoom.toFloat(), spec.maxZoom.toFloat())
                        clampCenter()
                        val w = toWorld(centroid); play.cursorX = w.x / W; play.cursorY = w.y / H
                    }
                }
                .semantics { contentDescription = "Lensa mikroskop. Cari ${spec.targetName}. ${if (sharp) "Fokus tajam" else "Gambar kabur"}." },
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val s = size.width / (W / play.zoom)
                drawCircle(bg, radius = size.minDimension / 2)
                fun worldBlock(dx: Float, dy: Float, alpha: Float) = withTransform({
                    translate(size.width / 2 - play.cx * s + dx, size.height / 2 - play.cy * s + dy)
                    scale(s, s, pivot = Offset.Zero)
                }) { drawScene(scene, alpha) }
                if (defocus <= 0.004f) {
                    worldBlock(0f, 0f, 1f)
                } else {
                    // Kabur: beberapa salinan bergeser (ringan, tanpa RenderEffect yang butuh API 31).
                    val o = (defocus * 45.dp.toPx()).coerceAtMost(16.dp.toPx())
                    worldBlock(0f, 0f, 0.35f)
                    worldBlock(o, 0f, 0.22f); worldBlock(-o, 0f, 0.22f); worldBlock(0f, o, 0.22f); worldBlock(0f, -o, 0.22f)
                }
                fun toView(wx: Float, wy: Float) = Offset(size.width / 2 + (wx - play.cx) * s, size.height / 2 + (wy - play.cy) * s)
                // ghost: bingkai area pengamatan + kursor (koordinat dunia -> viewport HP ini)
                if (env.ghostStyle.enabled) {
                    env.ghosts.forEach { g ->
                        val track = g.track ?: return@forEach
                        val gs = GhostInterpolator.at(track, now) as? MicroGhost ?: return@forEach
                        val a = env.ghostStyle.opacity * if (gs.done || g.locked) 0.6f else 1f
                        val tl = toView(gs.vx * W, gs.vy * H)
                        val br = toView((gs.vx + gs.vw) * W, (gs.vy + gs.vh) * H)
                        drawRect(g.color.copy(alpha = a), topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y), style = Stroke(width = 3.dp.toPx()))
                        val label = g.name.take(10) + if (env.ghostMode == GhostMode.LIVE && gs.focus != null) " · f ${"%.2f".format(gs.focus)}" else ""
                        drawText(measurer, label, tl + Offset(4.dp.toPx(), 2.dp.toPx()),
                            style = TextStyle(color = g.color.copy(alpha = (a + 0.4f).coerceAtMost(1f)), fontSize = 10.sp))
                        if (gs.cx != null && gs.cy != null) drawCircle(g.color.copy(alpha = a + 0.1f), 6.dp.toPx(), toView(gs.cx * W, gs.cy * H))
                        track.events.forEach { e ->
                            val ta = e.action as? TapAction ?: return@forEach
                            if (ta.x != null && ta.y != null && now - e.receivedAtMs < 1_500) {
                                drawCircle(g.color.copy(alpha = a), 12.dp.toPx(), toView(ta.x * W, ta.y * H), style = Stroke(width = 2.dp.toPx()))
                            }
                        }
                    }
                }
                // kursor sendiri
                drawCircle(env.myColor, 5.dp.toPx(), toView(play.cursorX * W, play.cursorY * H), style = Stroke(width = 2.dp.toPx()))
                drawLine(Color(0x55FFFFFF), Offset(size.width / 2 - 10.dp.toPx(), size.height / 2), Offset(size.width / 2 + 10.dp.toPx(), size.height / 2), 1.5f)
                drawLine(Color(0x55FFFFFF), Offset(size.width / 2, size.height / 2 - 10.dp.toPx()), Offset(size.width / 2, size.height / 2 + 10.dp.toPx()), 1.5f)
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawArtwork(rimArtwork, Rect(Offset.Zero, size), fillBounds = true)
        }
    }
    play.message?.let { (msg, ok) ->
        if (now - play.messageAt < 2_200) {
            Surface(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(12.dp),
                color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(msg, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
            }
        }
    }
    Text(if (sharp) "Fokus: TAJAM" else "Fokus: kabur — geser fokus", style = MaterialTheme.typography.titleSmall,
        color = if (sharp) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GameOutlinedButton(onClick = { play.focus = (play.focus - 0.01f).coerceAtLeast(0f) }, enabled = active) { Text("−") }
        Slider(play.focus, { play.focus = it }, enabled = active, modifier = Modifier.weight(1f).semantics { stateDescription = "Fokus ${"%.2f".format(play.focus)}" })
        GameOutlinedButton(onClick = { play.focus = (play.focus + 0.01f).coerceAtMost(1f) }, enabled = active) { Text("+") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val step = { W / play.zoom * 0.15f }
        GameOutlinedButton(onClick = { play.zoom = (play.zoom / 1.25f).coerceAtLeast(spec.minZoom.toFloat()) }, enabled = active, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.ZoomOut, "Perkecil")
        }
        GameOutlinedButton(onClick = { play.zoom = (play.zoom * 1.25f).coerceAtMost(spec.maxZoom.toFloat()) }, enabled = active, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.ZoomIn, "Perbesar")
        }
        GameOutlinedButton(onClick = { play.cx -= step(); clampCenter() }, enabled = active, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Geser kiri") }
        GameOutlinedButton(onClick = { play.cy -= step(); clampCenter() }, enabled = active, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.KeyboardArrowUp, "Geser atas") }
        GameOutlinedButton(onClick = { play.cy += step(); clampCenter() }, enabled = active, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.KeyboardArrowDown, "Geser bawah") }
        GameOutlinedButton(onClick = { play.cx += step(); clampCenter() }, enabled = active, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Geser kanan") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Bantuan sentuh (objek kecil)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(play.assist, { play.assist = it }, enabled = active)
    }
    when {
        play.submitted && play.wrong <= spec.maxWrong -> GameLockedNote("Struktur ditemukan! Menunggu hasil ronde…")
        play.submitted -> GameLockedNote("Kesempatan tap habis — jawaban dikunci.")
        env.locked -> GameLockedNote("Jawaban terkunci.")
    }
}
