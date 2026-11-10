package id.eduduel.app.presentation.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.eduduel.app.R
import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.game.AsteroidEngine
import id.eduduel.app.domain.game.AsteroidSpec
import id.eduduel.app.domain.game.AsteroidSubmission
import id.eduduel.app.net.protocol.AsteroidGhost
import id.eduduel.app.net.protocol.FireAction
import id.eduduel.app.net.room.GhostInterpolator
import id.eduduel.app.presentation.components.GameButton
import id.eduduel.app.presentation.components.GameOutlinedButton
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val SHIP_Y = 0.9f
private const val AST_R = 0.085f
private const val ARENA_RATIO = 0.82f // lebar : tinggi

private class AsteroidPlay {
    var shipX by mutableFloatStateOf(0.5f)
    val hits = mutableStateListOf<Int>()
    var destroyed by mutableStateOf(setOf<Int>())
    var lastShotAt by mutableLongStateOf(NEVER_MS)
    var lastShotX by mutableFloatStateOf(0.5f)
    var lastShotY by mutableFloatStateOf(0f)
    var impactX by mutableFloatStateOf(0.5f)
    var impactY by mutableFloatStateOf(0f)
    var impactAt by mutableLongStateOf(NEVER_MS)
    var feedback by mutableStateOf<Pair<String, Boolean>?>(null)
    var feedbackAt by mutableLongStateOf(0L)
    var submitted by mutableStateOf(false)
}

@Composable
fun AsteroidGame(spec: AsteroidSpec, env: GameEnv, cb: GameCallbacks) {
    val key = env.round.question.id to env.startedAtMs
    val play = remember(key) { AsteroidPlay() }
    val spawns = remember(key) {
        val count = ((env.durationMs / 1000.0) / spec.spawnInterval).toInt() + 12
        AsteroidEngine.spawns(spec, env.round.seed, count)
    }
    val now = rememberFrameTime(env.clock, running = true)
    val t = (now - env.startedAtMs) / 1000.0
    val progress = AsteroidEngine.progress(spec, play.hits)
    val active = !env.locked && !play.submitted
    val measurer = rememberTextMeasurer()
    val callbacks by rememberUpdatedState(cb)
    val shipArtwork = rememberGameArtwork(R.drawable.game_spaceship)
    val rockArtwork = rememberGameArtwork(R.drawable.game_asteroid)
    val laserArtwork = rememberGameArtwork(R.drawable.game_laser)
    val impactArtwork = rememberGameArtwork(R.drawable.game_impact)

    fun alive(time: Double) = spawns.mapNotNull { s ->
        if (s.index in play.destroyed || time < s.spawnAt) return@mapNotNull null
        val y = (-0.08 + (time - s.spawnAt) * spec.fallSpeed * s.speedFactor).toFloat()
        if (y > 1.08f) null else Triple(s, s.x.toFloat(), y)
    }

    fun fire(x: Float) {
        if (!active) return
        val nowMs = env.clock.nowMs()
        if (msSince(nowMs, play.lastShotAt) < 300) return
        play.shipX = x.coerceIn(0.05f, 0.95f)
        val time = (nowMs - env.startedAtMs) / 1000.0
        val target = alive(time).filter { (_, ax, ay) -> abs(ax - play.shipX) <= AST_R * 1.05f && ay < SHIP_Y - 0.03f }.maxByOrNull { it.third }
        play.lastShotAt = nowMs
        play.lastShotX = play.shipX
        play.lastShotY = target?.third ?: -0.1f
        if (target == null) {
            callbacks.onAction(FireAction(play.shipX))
            return
        }
        val (s, _, _) = target
        play.impactX = target.second
        play.impactY = target.third
        play.impactAt = nowMs
        play.destroyed = play.destroyed + s.index
        val before = AsteroidEngine.progress(spec, play.hits)
        play.hits += s.token
        val after = AsteroidEngine.progress(spec, play.hits)
        val correct = after.filled > before.filled
        val token = spec.tokens[s.token]
        play.feedback = (if (correct) "✓ $token" else "✗ $token bukan isi kotak berikutnya") to correct
        play.feedbackAt = nowMs
        callbacks.onAction(FireAction(play.shipX, token, correct))
        if (after.success || after.failed) {
            play.submitted = true
            callbacks.onSubmit(AsteroidSubmission(play.hits.toList()))
        }
    }

    // Publikasi ghost lokal (di-coalesce oleh sesi sesuai interval transport).
    LaunchedEffect(key) {
        while (true) {
            val p = AsteroidEngine.progress(spec, play.hits)
            callbacks.onGhost(
                AsteroidGhost(play.shipX, firing = msSince(env.clock.nowMs(), play.lastShotAt) < 250, filled = p.filled, wrong = p.wrong,
                    done = play.submitted || env.locked),
            )
            delay(60)
        }
    }

    // ---- template dengan kotak kosong ----
    val template = buildAnnotatedString {
        val regex = Regex("\\{(\\d+)\\}")
        var last = 0
        regex.findAll(spec.template).forEach { m ->
            append(spec.template.substring(last, m.range.first))
            val slot = m.groupValues[1].toInt()
            when {
                slot < progress.filled -> withStyle(SpanStyle(color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)) { append(spec.answerTokens[slot]) }
                slot == progress.filled && active -> withStyle(SpanStyle(background = Color(0xFFFFF59D), fontWeight = FontWeight.Bold)) { append(" ? ") }
                else -> withStyle(SpanStyle(color = Color.Gray)) { append(" ▢ ") }
            }
            last = m.range.last + 1
        }
        append(spec.template.substring(last))
    }
    Text(template, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics {
        contentDescription = "Kotak terisi ${progress.filled} dari ${spec.answerTokens.size}"
    })
    Text("Nyawa: ${"♥".repeat((spec.maxWrong - progress.wrong + 1).coerceAtLeast(0))}${"♡".repeat(progress.wrong.coerceAtMost(spec.maxWrong + 1))}" +
        "  ·  salah ${progress.wrong}/${spec.maxWrong} diizinkan", style = MaterialTheme.typography.bodyMedium)
    GhostLegend(env)

    Box(
        Modifier.fillMaxWidth().aspectRatio(ARENA_RATIO).clip(RoundedCornerShape(18.dp)).background(Color(0xFF101634))
            .pointerInput(key, active) {
                if (!active) return@pointerInput
                detectDragGestures { change, _ -> play.shipX = (change.position.x / size.width).coerceIn(0.05f, 0.95f) }
            }
            .pointerInput(key, active) {
                if (!active) return@pointerInput
                // Ketuk asteroid = bidik + tembak (alternatif aksesibilitas tanpa menggeser).
                detectTapGestures { pos -> fire(pos.x / size.width) }
            }
            .semantics { contentDescription = "Arena asteroid. Ketuk asteroid untuk menembak." },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(ARENA_RATIO)) {
            drawStars(env.round.seed)
            val w = size.width
            val h = size.height
            val r = AST_R * w * if (env.assist) 1.1f else 1f
            alive(t).forEach { (s, ax, ay) ->
                val c = Offset(ax * w, ay * h)
                rotate((s.index % 5 - 2) * 12f, pivot = c) {
                    drawArtwork(rockArtwork, artworkBounds(c, r * 2.1f))
                }
                drawLabel(measurer, spec.tokens[s.token], c, r)
            }
            // ghost pemain lain: pesawat semitransparan + laser, TIDAK menghancurkan asteroid lokal
            if (env.ghostStyle.enabled) {
                env.ghosts.forEach { g ->
                    val track = g.track ?: return@forEach
                    val gs = GhostInterpolator.at(track, now) as? AsteroidGhost ?: return@forEach
                    val a = env.ghostStyle.opacity * if (gs.done || g.locked) 0.6f else 1f
                    drawArtwork(shipArtwork, artworkBounds(Offset(gs.x * w, SHIP_Y * h), w * 0.13f, w * 0.15f), a, g.color)
                    drawText(measurer, g.name.take(10), Offset(gs.x * w - 24.dp.toPx(), SHIP_Y * h + 14.dp.toPx()),
                        style = TextStyle(color = g.color.copy(alpha = (a + 0.35f).coerceAtMost(1f)), fontSize = 10.sp))
                    track.events.lastOrNull()?.let { e ->
                        val fire = e.action as? FireAction ?: return@let
                        if (now - e.receivedAtMs < 300) {
                            drawArtwork(laserArtwork, Rect(fire.x * w - 5.dp.toPx(), 0f, fire.x * w + 5.dp.toPx(), SHIP_Y * h), a, g.color, fillBounds = true)
                            if (env.ghostMode == GhostMode.LIVE && fire.token != null) {
                                drawText(measurer, fire.token, Offset(fire.x * w + 6, h * 0.15f),
                                    style = TextStyle(color = g.color.copy(alpha = (a + 0.3f).coerceAtMost(1f)), fontSize = 12.sp))
                            }
                        }
                    }
                }
            }
            // laser & pesawat sendiri (solid)
            if (msSince(now, play.lastShotAt) < 220) {
                drawArtwork(laserArtwork, Rect(play.lastShotX * w - 5.dp.toPx(), play.lastShotY.coerceAtLeast(0f) * h,
                    play.lastShotX * w + 5.dp.toPx(), SHIP_Y * h - w * 0.045f), fillBounds = true)
            }
            val impactAge = msSince(now, play.impactAt)
            if (impactAge < 320) {
                val f = impactAge / 320f
                drawArtwork(impactArtwork, artworkBounds(Offset(play.impactX * w, play.impactY * h), r * (1.2f + f)), alpha = 1f - f)
            }
            drawArtwork(shipArtwork, artworkBounds(Offset(play.shipX * w, SHIP_Y * h), w * 0.13f, w * 0.15f))
            // Player identity stays visible alongside the original multicolored artwork.
            drawCircle(env.myColor, 3.dp.toPx(), Offset(play.shipX * w, SHIP_Y * h + w * 0.085f))
        }
        play.feedback?.let { (msg, ok) ->
            if (now - play.feedbackAt < 1_400) {
                Text(msg, color = if (ok) Color(0xFFA5D6A7) else Color(0xFFFF8A80), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter))
            }
        }
    }
    // Kontrol langkah (aksesibilitas): kiri/kanan/tembak.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameOutlinedButton(onClick = { play.shipX = (play.shipX - 0.08f).coerceAtLeast(0.05f) }, enabled = active, modifier = Modifier.weight(1f)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Geser kiri")
        }
        GameButton(onClick = { fire(play.shipX) }, enabled = active, modifier = Modifier.weight(2f)) { Text("TEMBAK") }
        GameOutlinedButton(onClick = { play.shipX = (play.shipX + 0.08f).coerceAtMost(0.95f) }, enabled = active, modifier = Modifier.weight(1f)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Geser kanan")
        }
    }
    when {
        progress.success -> GameLockedNote("Semua kotak terisi! Menunggu hasil ronde…")
        progress.failed -> GameLockedNote("Nyawa habis — jawaban dikunci.")
        env.locked -> GameLockedNote("Jawaban terkunci.")
    }
}

private fun DrawScope.drawStars(seed: Long) {
    val rng = id.eduduel.app.domain.game.SplitMix(seed xor 0x5EEDL)
    repeat(40) {
        drawCircle(Color.White.copy(alpha = 0.25f + rng.nextDouble().toFloat() * 0.4f), radius = 1.5f + rng.nextDouble().toFloat() * 1.5f,
            center = Offset(rng.nextDouble().toFloat() * size.width, rng.nextDouble().toFloat() * size.height))
    }
}

private fun DrawScope.drawLabel(m: TextMeasurer, token: String, c: Offset, r: Float) {
    val size = when {
        token.length <= 3 -> 16.sp
        token.length <= 5 -> 13.sp
        else -> 11.sp
    }
    val layout = m.measure(token, TextStyle(color = Color(0xFF2C173E), fontSize = size, fontWeight = FontWeight.ExtraBold))
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
}
