package id.eduduel.app.presentation.game

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/** Decode once per arena, with bounded memory; the approved originals remain in drawable-nodpi. */
@Composable
internal fun rememberGameArtwork(@DrawableRes id: Int): ImageBitmap {
    val resources = LocalResources.current
    return remember(resources, id) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, options)
        // At most 768 px per edge, including the long pipette/laser sprites.
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 768) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        options.inScaled = false
        checkNotNull(BitmapFactory.decodeResource(resources, id, options)).asImageBitmap()
    }
}

/** Aspect-fit by default; fillBounds is reserved for geometry-aligned overlays and targets. */
internal fun DrawScope.drawArtwork(
    image: ImageBitmap,
    bounds: Rect,
    alpha: Float = 1f,
    tint: Color? = null,
    fillBounds: Boolean = false,
    colorFilter: ColorFilter? = null,
) {
    if (bounds.width <= 0f || bounds.height <= 0f || alpha <= 0f) return
    val fit = min(bounds.width / image.width, bounds.height / image.height)
    val width = if (fillBounds) bounds.width else image.width * fit
    val height = if (fillBounds) bounds.height else image.height * fit
    drawImage(
        image = image,
        dstOffset = IntOffset((bounds.center.x - width / 2).roundToInt(), (bounds.center.y - height / 2).roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
        alpha = alpha.coerceIn(0f, 1f),
        colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcAtop) } ?: colorFilter,
        filterQuality = FilterQuality.Medium,
    )
}

internal fun artworkBounds(center: Offset, width: Float, height: Float = width): Rect =
    Rect(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2)

// Source-space masks let the approved, single cannon PNG articulate without generating new art.
private val cannonSource = Rect(0f, 0f, 1448f, 1086f)
private val cannonPivot = Offset(640f, 578f)
private val cannonCarriage = Path().apply {
    moveTo(150f, 1086f)
    lineTo(150f, 740f)
    lineTo(320f, 740f)
    cubicTo(422f, 726f, 471f, 656f, 507f, 586f)
    cubicTo(511f, 494f, 567f, 437f, 629f, 435f)
    cubicTo(723f, 421f, 790f, 485f, 790f, 576f)
    cubicTo(789f, 626f, 759f, 674f, 720f, 702f)
    lineTo(734f, 738f)
    lineTo(1010f, 738f)
    lineTo(1010f, 1086f)
    close()
}

internal fun DrawScope.drawCannonArtwork(image: ImageBitmap, at: Offset, degrees: Float, alpha: Float = 1f, tint: Color? = null) {
    val scale = 76.dp.toPx() / cannonSource.width
    withTransform({
        translate(at.x - cannonPivot.x * scale, at.y - cannonPivot.y * scale)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        rotate(30f - degrees, pivot = cannonPivot) {
            clipRect(left = 0f, top = 0f, right = cannonSource.width, bottom = 740f) {
                clipPath(cannonCarriage, clipOp = ClipOp.Difference) {
                    drawArtwork(image, cannonSource, alpha, tint, fillBounds = true)
                }
            }
        }
        clipPath(cannonCarriage) {
            drawArtwork(image, cannonSource, alpha, tint, fillBounds = true)
        }
    }
}

/** Tile the stonework inside the existing obstacle rectangle, including narrow/tall obstacles. */
internal fun DrawScope.drawObstacleArtwork(image: ImageBitmap, bounds: Rect) {
    val visible = bounds.intersect(Rect(Offset.Zero, size))
    if (visible.width <= 0 || visible.height <= 0) return
    val tileW = 80.dp.toPx()
    val tileH = tileW * image.height / image.width
    clipRect(visible.left, visible.top, visible.right, visible.bottom) {
        var y = visible.top
        while (y < visible.bottom) {
            var x = visible.left
            while (x < visible.right) {
                drawArtwork(image, Rect(x, y, x + tileW, y + tileH), fillBounds = true)
                x += tileW
            }
            y += tileH
        }
    }
}

/** Preserve highlights while coloring only the bottle's liquid (the label stays blank/cream). */
internal fun reagentArtworkFilter(color: Color): ColorFilter {
    fun row(component: Float) = floatArrayOf(0.2126f * component, 0.7152f * component, 0.0722f * component, 0f, 0f)
    return ColorFilter.colorMatrix(ColorMatrix(
        row(color.red) + row(color.green) + row(color.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f),
    ))
}

internal fun DrawScope.drawBottleArtwork(image: ImageBitmap, bounds: Rect, liquidFilter: ColorFilter) {
    drawArtwork(image, bounds, fillBounds = true)
    val w = bounds.width
    val h = bounds.height
    clipRect(bounds.left + w * 0.15f, bounds.top + h * 0.54f, bounds.left + w * 0.85f, bounds.top + h * 0.97f) {
        clipRect(bounds.left + w * 0.24f, bounds.top + h * 0.57f, bounds.left + w * 0.80f, bounds.top + h * 0.82f, clipOp = ClipOp.Difference) {
            drawArtwork(image, bounds, fillBounds = true, colorFilter = liquidFilter)
        }
    }
}
