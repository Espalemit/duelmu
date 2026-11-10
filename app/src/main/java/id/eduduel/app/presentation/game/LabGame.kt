package id.eduduel.app.presentation.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.eduduel.app.R
import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.game.InvalidSubmissionException
import id.eduduel.app.domain.game.LabAction
import id.eduduel.app.domain.game.LabEngine
import id.eduduel.app.domain.game.LabModule
import id.eduduel.app.domain.game.LabReadout
import id.eduduel.app.domain.game.LabSpec
import id.eduduel.app.domain.game.LabSubmission
import id.eduduel.app.net.protocol.LabGhost
import id.eduduel.app.net.protocol.LabStepAction
import id.eduduel.app.net.room.GhostInterpolator
import id.eduduel.app.presentation.components.GameButton
import id.eduduel.app.presentation.components.GameOutlinedButton
import id.eduduel.app.presentation.components.InfoCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class LabPlay {
    val actions = mutableStateListOf<LabAction>()
    var holding by mutableStateOf<Int?>(null)
    var toolX by mutableFloatStateOf(0.5f)
    var toolY by mutableFloatStateOf(0.2f)
    var pouring by mutableStateOf(false)
    var resets by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)
    var messageAt by mutableLongStateOf(0L)
    var submitted by mutableStateOf(false)
}

/** Warna indikator universal per pH (skema umum kertas indikator). */
fun universalColor(ph: Double): Color {
    val stops = listOf(1.0 to Color(0xFFE53935), 3.0 to Color(0xFFFB8C00), 5.0 to Color(0xFFFDD835), 7.0 to Color(0xFF43A047),
        9.0 to Color(0xFF00897B), 11.0 to Color(0xFF1E88E5), 13.0 to Color(0xFF5E35B1))
    if (ph <= stops.first().first) return stops.first().second
    if (ph >= stops.last().first) return stops.last().second
    val i = stops.indexOfLast { it.first <= ph }
    val (p0, c0) = stops[i]
    val (p1, c1) = stops[i + 1]
    val f = ((ph - p0) / (p1 - p0)).toFloat()
    return Color(c0.red + (c1.red - c0.red) * f, c0.green + (c1.green - c0.green) * f, c0.blue + (c1.blue - c0.blue) * f)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabGame(spec: LabSpec, env: GameEnv, cb: GameCallbacks) {
    val key = env.round.question.id to env.startedAtMs
    val play = remember(key) { LabPlay() }
    val now = rememberFrameTime(env.clock, running = true)
    val callbacks by rememberUpdatedState(cb)
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val active = !env.locked && !play.submitted
    val state = remember(play.actions.size, play.actions.lastOrNull()) {
        runCatching { LabEngine.replay(spec, play.actions.toList()) }.getOrElse { LabEngine.initial(spec) }
    }

    fun say(msg: String) { play.message = msg; play.messageAt = env.clock.nowMs() }

    /** Tambah aksi (digabung dengan aksi sebelumnya bila reagen sama) bila masih valid. */
    fun add(r: Int, amtMilli: Int): Boolean {
        if (!active) return false
        val last = play.actions.lastOrNull()
        val candidate = if (last != null && last.r == r && spec.module != LabModule.SEPARATION) {
            play.actions.dropLast(1) + LabAction(r, last.amt + amtMilli)
        } else play.actions + LabAction(r, amtMilli)
        return try {
            LabEngine.replay(spec, candidate)
            if (candidate.size > id.eduduel.app.domain.game.GameLimits.MAX_LAB_ACTIONS) { say("Terlalu banyak langkah"); return false }
            play.actions.clear(); play.actions.addAll(candidate)
            callbacks.onAction(LabStepAction(r, amtMilli))
            true
        } catch (e: InvalidSubmissionException) {
            say(e.message ?: "Tidak bisa")
            false
        }
    }

    fun reset() {
        if (!active || play.resets >= spec.maxResets || play.actions.isEmpty()) return
        play.actions += LabAction(LabAction.RESET)
        play.resets++
        say("Bejana dikosongkan (ulang ${play.resets}/${spec.maxResets})")
    }

    fun lock() {
        if (!active) return
        play.submitted = true
        callbacks.onSubmit(if (play.actions.none { it.r != LabAction.RESET }) null else LabSubmission(play.actions.toList()))
    }

    LaunchedEffect(key) {
        while (true) {
            val st = runCatching { LabEngine.replay(spec, play.actions.toList()) }.getOrNull()
            callbacks.onGhost(
                LabGhost(play.toolX, play.toolY, play.holding, play.pouring,
                    fill = st?.let { (it.volumeMl / spec.vesselCapacity).toFloat().coerceIn(0f, 1f) }, done = play.submitted || env.locked),
            )
            delay(60)
        }
    }

    InfoCard {
        Text("Model sains: ${spec.model}", style = MaterialTheme.typography.bodySmall)
        spec.reaction?.let { Text("Reaksi: ${it.equation}", style = MaterialTheme.typography.titleSmall) }
    }
    GhostLegend(env)

    if (spec.module == LabModule.SEPARATION) {
        Text("Urutan langkah (seret/ketuk alat):", style = MaterialTheme.typography.titleSmall)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(
                state.sequence.mapIndexed { i, id -> "${i + 1}. ${spec.tools.first { it.id == id }.name}" }.ifEmpty { listOf("— belum ada langkah —") }.joinToString("  →  "),
                Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.tools.forEachIndexed { i, tool ->
                GameOutlinedButton(onClick = { add(i, 0) }, enabled = active && state.sequence.size < 6) {
                    Column {
                        Text(tool.name, style = MaterialTheme.typography.titleSmall)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
        LabGhostStrip(env, now)
    } else {
        LabBench(spec, env, play, state, now, measurer, active, onPour = { r -> add(r, (spec.reagents[r].step * 1000).toInt()) })
        Readouts(spec, state)
        spec.reagents.forEachIndexed { i, r ->
            val total = state.reagentTotals[r.id] ?: 0.0
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${r.name}: ${fmtNum(total)} ${r.unit} (maks ${fmtNum(r.max)})", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameOutlinedButton(onClick = { add(i, (r.step * 1000).toInt()) }, enabled = active, modifier = Modifier.weight(1f)) { Text("+${fmtNum(r.step)}") }
                    GameOutlinedButton(onClick = { add(i, (r.bigStep * 1000).toInt()) }, enabled = active, modifier = Modifier.weight(1f)) { Text("+${fmtNum(r.bigStep)}") }
                    if (r.tool == "dropper" || r.tool == "burette") {
                        Surface(
                            shape = RoundedCornerShape(18.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1.3f).heightIn(min = 48.dp).pointerInput(key, active, i) {
                                if (!active) return@pointerInput
                                detectTapGestures(onPress = {
                                    play.holding = i; play.pouring = true
                                    val job = scope.launch {
                                        while (true) {
                                            if (!add(i, (r.step * 1000).toInt())) break
                                            delay(140)
                                        }
                                    }
                                    tryAwaitRelease()
                                    job.cancel()
                                    play.holding = null; play.pouring = false
                                })
                            }.semantics { contentDescription = "Tahan untuk meneteskan ${r.name}" },
                        ) { Box(contentAlignment = Alignment.Center) { Text("Tahan: tetes", Modifier.padding(12.dp)) } }
                    }
                }
            }
        }
    }
    play.message?.let { if (now - play.messageAt < 2_200) Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameOutlinedButton(onClick = ::reset, enabled = active && play.resets < spec.maxResets && play.actions.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Text("Ulangi (${spec.maxResets - play.resets})")
        }
        GameButton(onClick = ::lock, enabled = active, modifier = Modifier.weight(1.4f)) { Text("KUNCI HASIL") }
    }
    if (play.submitted || env.locked) GameLockedNote("Hasil eksperimen dikunci. Penilaian dihitung Host dari langkah-langkahmu.")
}

@Composable
private fun LabBench(
    spec: LabSpec, env: GameEnv, play: LabPlay, state: LabEngine.LabState, now: Long,
    measurer: androidx.compose.ui.text.TextMeasurer, active: Boolean, onPour: (Int) -> Unit,
) {
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    val bottleArtwork = rememberGameArtwork(R.drawable.game_reagent_bottle)
    val beakerArtwork = rememberGameArtwork(R.drawable.game_beaker)
    val pipetteArtwork = rememberGameArtwork(R.drawable.game_pipette)
    val liquidFilters = remember(spec) { spec.reagents.map { reagentArtworkFilter(parseColor(it.color, Color(0xFF90CAF9))) } }
    val n = spec.reagents.size
    fun bottleCenter(i: Int) = Offset((i + 1f) / (n + 1f), 0.18f)
    val vessel = Rect(0.36f, 0.45f, 0.64f, 0.95f)
    // Saat alat dipegang di atas bejana: tuang berkala (seret botol = menuang).
    LaunchedEffect(play.holding, play.pouring) {
        val h = play.holding ?: return@LaunchedEffect
        while (play.pouring && active) {
            onPour(h)
            delay(180)
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(18.dp)).background(Color(0xFFF5F5F0))
            .onSizeChanged { boxSize = it }
            .pointerInput(env.startedAtMs, active) {
                if (!active) return@pointerInput
                detectDragGestures(
                    onDragStart = { o ->
                        val p = Offset(o.x / boxSize.width, o.y / boxSize.height)
                        val idx = (0 until n).minByOrNull { (bottleCenter(it) - p).getDistance() }
                        if (idx != null && (bottleCenter(idx) - p).getDistance() < 0.16f) { play.holding = idx; play.toolX = p.x; play.toolY = p.y }
                    },
                    onDragEnd = { play.holding = null; play.pouring = false },
                    onDragCancel = { play.holding = null; play.pouring = false },
                ) { change, _ ->
                    play.toolX = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                    play.toolY = (change.position.y / boxSize.height).coerceIn(0f, 1f)
                    play.pouring = play.holding != null && play.toolX in vessel.left..vessel.right && play.toolY in 0.25f..vessel.top + 0.1f
                }
            }
            .semantics { contentDescription = "Meja lab virtual: seret botol ke atas bejana untuk menuang." },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            val w = size.width; val h = size.height
            // bejana + cairan
            val fill = (state.volumeMl / spec.vesselCapacity).toFloat().coerceIn(0f, 1f)
            val ph = LabEngine.ph(state)
            val liquid = when {
                LabReadout.UNIVERSAL_INDICATOR in spec.readouts && ph != null -> universalColor(ph)
                LabReadout.PHENOLPHTHALEIN in spec.readouts && ph != null -> if (ph >= 8.2) Color(0xFFF48FB1) else Color(0xFFE3F2FD)
                else -> Color(0xFFB3E5FC)
            }
            val vx = vessel.left * w; val vy = vessel.top * h; val vw = vessel.width * w; val vh = vessel.height * h
            val showFill = if (state.volumeMl > 0) fill.coerceAtLeast(0.04f) else if (state.massG > 0 || state.species.isNotEmpty()) 0.05f else 0f
            val glassInterior = Rect(vx + vw * 0.18f, vy + vh * 0.20f, vx + vw * 0.82f, vy + vh * 0.90f)
            if (showFill > 0f) {
                val surfaceY = glassInterior.bottom - glassInterior.height * showFill
                val interiorPath = Path().apply { addRoundRect(RoundRect(glassInterior, CornerRadius(vw * 0.08f))) }
                clipPath(interiorPath) {
                    drawRect(liquid.copy(alpha = 0.85f), topLeft = Offset(glassInterior.left, surfaceY),
                        size = Size(glassInterior.width, glassInterior.bottom - surfaceY))
                    drawOval(liquid, topLeft = Offset(glassInterior.left, surfaceY - 2.dp.toPx()), size = Size(glassInterior.width, 4.dp.toPx()))
                }
            }
            drawArtwork(beakerArtwork, Rect(vx, vy, vx + vw, vy + vh), fillBounds = true)
            drawText(measurer, spec.vessel, Offset(vx, vy - 14.dp.toPx()), style = TextStyle(fontSize = 10.sp, color = Color(0xFF455A64)))
            // botol reagen di rak
            spec.reagents.forEachIndexed { i, r ->
                val c = bottleCenter(i)
                val held = play.holding == i
                val pos = if (held) Offset(play.toolX * w, play.toolY * h) else Offset(c.x * w, c.y * h)
                val bw = 44.dp.toPx()
                val bh = 50.dp.toPx()
                if (held && r.tool in listOf("pipette", "dropper", "burette")) {
                    drawArtwork(pipetteArtwork, artworkBounds(pos, 28.dp.toPx(), 58.dp.toPx()))
                } else {
                    drawBottleArtwork(bottleArtwork, artworkBounds(pos, bw, bh), liquidFilters[i])
                }
                if (held && play.pouring) {
                    val phase = (now % 540L) / 540f
                    val tip = Offset(pos.x, pos.y + bh / 2 + 7.dp.toPx() + phase * 18.dp.toPx())
                    drawOval(parseColor(r.color, liquid).copy(alpha = 1f - phase),
                        topLeft = tip - Offset(2.dp.toPx(), 0f), size = Size(4.dp.toPx(), 6.dp.toPx()))
                }
                drawText(measurer, r.name.take(18), Offset(c.x * w - 40.dp.toPx(), c.y * h + 26.dp.toPx()), style = TextStyle(fontSize = 9.sp, color = Color(0xFF37474F)))
            }
            // ghost alat lawan (pipet/gelas) — tidak mengubah isi bejana lokal
            if (env.ghostStyle.enabled) {
                env.ghosts.forEach { g ->
                    val track = g.track ?: return@forEach
                    val gs = GhostInterpolator.at(track, now) as? LabGhost ?: return@forEach
                    val a = env.ghostStyle.opacity * if (gs.done || g.locked) 0.6f else 1f
                    val col = gs.holding?.let { spec.reagents.getOrNull(it)?.color }?.let { parseColor(it, g.color) } ?: g.color
                    val pos = Offset(gs.tx * w, gs.ty * h)
                    drawArtwork(pipetteArtwork, artworkBounds(pos, 28.dp.toPx(), 58.dp.toPx()), a, col)
                    drawText(measurer, g.name.take(10) + if (env.ghostMode == GhostMode.LIVE && gs.fill != null) " · ${(gs.fill * 100).toInt()}%" else "",
                        Offset(gs.tx * w + 10.dp.toPx(), gs.ty * h - 18.dp.toPx()), style = TextStyle(fontSize = 10.sp, color = g.color.copy(alpha = (a + 0.4f).coerceAtMost(1f))))
                }
            }
        }
    }
}

@Composable
private fun LabGhostStrip(env: GameEnv, now: Long) {
    if (!env.ghostStyle.enabled || env.ghosts.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val pipetteArtwork = rememberGameArtwork(R.drawable.game_pipette)
    Canvas(Modifier.fillMaxWidth().aspectRatio(5f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F0))) {
        env.ghosts.forEach { g ->
            val track = g.track ?: return@forEach
            val gs = GhostInterpolator.at(track, now) as? LabGhost ?: return@forEach
            val a = env.ghostStyle.opacity * if (gs.done || g.locked) 0.6f else 1f
            drawArtwork(pipetteArtwork, artworkBounds(Offset(gs.tx * size.width, size.height * 0.5f), 24.dp.toPx(), size.height * 0.85f), a, g.color)
            drawText(measurer, g.name.take(10), Offset(gs.tx * size.width + 8, 4f), style = TextStyle(fontSize = 10.sp, color = g.color.copy(alpha = (a + 0.4f).coerceAtMost(1f))))
        }
    }
}

@Composable
private fun Readouts(spec: LabSpec, st: LabEngine.LabState) {
    val parts = mutableListOf<String>()
    if (LabReadout.VOLUME in spec.readouts) parts += "Volume: ${fmtNum(st.volumeMl)} mL"
    if (LabReadout.MASS in spec.readouts) parts += "Massa larutan: ${fmtNum(st.massG)} g"
    if (LabReadout.MOLES in spec.readouts) spec.initialSpecies.forEach { (k, v) -> parts += "$k awal: ${fmtNum(v)} mol" }
    val ph = LabEngine.ph(st)
    if (LabReadout.PH_METER in spec.readouts && ph != null) parts += "pH meter: ${fmtNum(ph)}"
    if (LabReadout.UNIVERSAL_INDICATOR in spec.readouts) parts += "Indikator universal: lihat warna"
    if (LabReadout.PHENOLPHTHALEIN in spec.readouts) parts += "Fenolftalein: " + if (ph != null && ph >= 8.2) "merah muda" else "tidak berwarna"
    if (parts.isNotEmpty()) Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
}
