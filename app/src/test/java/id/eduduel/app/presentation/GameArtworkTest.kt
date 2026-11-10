package id.eduduel.app.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import id.eduduel.app.R
import id.eduduel.app.presentation.game.drawArtwork
import id.eduduel.app.presentation.game.drawCannonArtwork
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Use Android's native renderer to catch opaque overlays, ghost alpha and articulation errors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameArtworkTest {
    private fun asset(id: Int): ImageBitmap = checkNotNull(
        BitmapFactory.decodeResource(RuntimeEnvironment.getApplication().resources, id,
            BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 }),
    ).asImageBitmap()

    private fun render(name: String, block: DrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(3f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(640f, 360f), block)
        val directory = File("build/reports/game-artwork").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test fun cannonRotatesBarrelWhileWheelPositionsStayFixed() {
        val cannon = asset(R.drawable.game_cannon)
        val images = listOf(0f, 30f, 85f).map { angle ->
            render("cannon-${angle.toInt()}") { drawCannonArtwork(cannon, Offset(200f, 140f), angle) }
        }
        // Wheels are in the static carriage; the barrel above them must change with the aim.
        for (image in images) {
            assertTrue(AndroidColor.alpha(image.getPixel(154, 196)) > 240)
            assertEquals(images.first().getPixel(154, 196), image.getPixel(154, 196))
            assertEquals(images.first().getPixel(231, 196), image.getPixel(231, 196))
        }
        val changed = (30 until 300).sumOf { x -> (0 until 140).count { y -> images[0].getPixel(x, y) != images[2].getPixel(x, y) } }
        assertTrue("Aiming must move the illustrated barrel", changed > 500)
        // Source-space clipping must include the muzzle, even when the arena is narrower than the PNG.
        assertTrue("Horizontal muzzle must stay visible", AndroidColor.alpha(images[0].getPixel(311, 140)) > 240)
        assertTrue("Near-vertical muzzle must stay visible", AndroidColor.alpha(images[2].getPixel(209, 30)) > 240)
    }

    @Test fun ghostUsesTheSameSilhouetteWithReducedAlpha() {
        val ship = asset(R.drawable.game_spaceship)
        val bounds = Rect(60f, 20f, 300f, 340f)
        val solid = render("spaceship-solid") { drawArtwork(ship, bounds) }
        val ghost = render("spaceship-ghost") { drawArtwork(ship, bounds, alpha = 0.25f, tint = Color(0xFFFF7043)) }
        fun totalAlpha(bitmap: Bitmap): Long = (0 until bitmap.width).sumOf { x ->
            (0 until bitmap.height).sumOf { y -> AndroidColor.alpha(bitmap.getPixel(x, y)).toLong() }
        }
        val ratio = totalAlpha(ghost).toDouble() / totalAlpha(solid)
        assertTrue("Ghost must remain at the configured 25% opacity: $ratio", ratio in 0.23..0.27)
        assertEquals(AndroidColor.TRANSPARENT, ghost.getPixel(0, 0))
    }

    @Test fun glassAndLensKeepTheCenterTransparent() {
        val background = Color(0xFF75C7A4)
        for ((name, id) in listOf("beaker" to R.drawable.game_beaker, "microscope" to R.drawable.game_microscope_rim)) {
            val artwork = asset(id)
            val image = render("$name-overlay") {
                drawRect(background)
                drawArtwork(artwork, Rect(140f, 0f, 500f, 360f), fillBounds = true)
            }
            assertEquals("$name must show the changing liquid/specimen beneath it", 0xFF75C7A4.toInt(), image.getPixel(320, 180))
            val changed = (140 until 500).sumOf { x -> (0 until 360).count { y -> image.getPixel(x, y) != 0xFF75C7A4.toInt() } }
            assertTrue("$name must still draw its glass/rim", changed > 2_000)
        }
    }
}
