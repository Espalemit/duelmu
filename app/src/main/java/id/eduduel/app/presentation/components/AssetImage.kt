package id.eduduel.app.presentation.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache gambar soal di memori (LRU berbasis byte, ±1/16 heap). Gambar didekode MALAS saat
 * pertama dibutuhkan, dengan inSampleSize sesuai lebar tampilan (PNG 1250x600 tidak pernah
 * didekode penuh di layar kecil). Semua dari assets/ di APK, tanpa internet.
 */
object AssetImageCache {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(4 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun decode(context: Context, path: String, targetWidthPx: Int): ImageBitmap? {
        val key = "$path@${sampleKey(targetWidthPx)}"
        cache.get(key)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            bmp.asImageBitmap().also { cache.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun sampleKey(w: Int) = when {
        w <= 400 -> 400
        w <= 800 -> 800
        else -> 1600
    }
}

private sealed interface ImgState {
    data object Loading : ImgState
    data class Ok(val bitmap: ImageBitmap) : ImgState
    data object Failed : ImgState
}

@Composable
private fun rememberAssetBitmap(path: String, widthPx: Int): ImgState {
    val context = LocalContext.current
    val state by produceState<ImgState>(ImgState.Loading, path, widthPx) {
        value = withContext(Dispatchers.IO) { AssetImageCache.decode(context.applicationContext, path, widthPx) }?.let { ImgState.Ok(it) } ?: ImgState.Failed
    }
    return state
}

/**
 * Ilustrasi soal bergambar di atas opsi. Rasio dijaga (fit), bisa diketuk untuk memperbesar.
 * Memperbesar gambar TIDAK membuka jawaban; timer tetap berjalan.
 */
@Composable
fun QuestionImage(path: String, questionId: String, modifier: Modifier = Modifier) {
    var zoomOpen by rememberSaveable(path) { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val px = with(LocalDensity.current) { maxWidth.toPx() }.toInt().coerceAtLeast(200)
        when (val st = rememberAssetBitmap(path, px)) {
            ImgState.Loading -> Box(Modifier.fillMaxWidth().aspectRatio(1250f / 600f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ImgState.Failed -> ImageError(questionId, path)
            is ImgState.Ok -> Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .clickable(role = Role.Button, onClickLabel = "Perbesar gambar") { zoomOpen = true },
            ) {
                Image(
                    st.bitmap, contentDescription = "Ilustrasi soal $questionId (ketuk untuk memperbesar)", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(st.bitmap.width.toFloat() / st.bitmap.height),
                )
                Surface(shape = RoundedCornerShape(50), color = Color(0xAA000000), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ZoomIn, null, tint = Color.White)
                        Text("Perbesar", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (zoomOpen) ZoomDialog(path, questionId) { zoomOpen = false }
}

@Composable
private fun ImageError(questionId: String, path: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text("Gambar soal $questionId tidak dapat dimuat ($path). Pasang ulang aplikasi; bank soal diverifikasi saat dibuka.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ZoomDialog(path: String, questionId: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val tState = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(Modifier.fillMaxSize().background(Color(0xF0000000))) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val px = with(LocalDensity.current) { maxWidth.toPx() }.toInt() * 2
                when (val st = rememberAssetBitmap(path, px)) {
                    is ImgState.Ok -> Image(
                        st.bitmap, contentDescription = "Ilustrasi soal $questionId diperbesar", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().transformable(tState)
                            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                    )
                    ImgState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    ImgState.Failed -> Text("Gambar tidak dapat dimuat", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameIconButton(onClick = { scale = (scale / 1.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }) {
                    Icon(Icons.Filled.ZoomOut, "Perkecil", tint = Color.White)
                }
                GameIconButton(onClick = { scale = (scale * 1.5f).coerceAtMost(5f) }) { Icon(Icons.Filled.ZoomIn, "Perbesar", tint = Color.White) }
                GameIconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Tutup" }) {
                    Icon(Icons.Filled.Close, "Tutup", tint = Color.White)
                }
            }
        }
    }
}
