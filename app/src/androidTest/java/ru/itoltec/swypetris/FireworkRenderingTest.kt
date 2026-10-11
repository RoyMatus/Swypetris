package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Deterministic software-rendered frames; baseline artifacts stay outside source control. */
class FireworkRenderingTest {
    private data class Fixture(val name: String, val width: Int, val height: Int, val density: Float,
        val celebration: VictoryCelebration)

    private val fixtures = listOf(
        Fixture("round-small", 360, 800, 1f, VictoryCelebration.ROUND),
        Fixture("round-large", 1080, 1920, 3f, VictoryCelebration.ROUND),
        Fixture("absolute-small", 360, 800, 1f, VictoryCelebration.ABSOLUTE),
        Fixture("absolute-large", 1080, 1920, 3f, VictoryCelebration.ABSOLUTE),
        Fixture("postcard", 1080, 1440, 3f, VictoryCelebration.POSTCARD)
    )
    private val times = listOf(0L, 300L, 755L, 756L, 757L, 1405L, 1406L, 1407L,
        2055L, 2056L, 2057L, 2400L, 4000L, 4199L, 4200L)

    @Test fun exportsDeterministicBoundaryFrames() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Frame export is an opt-in audit", args.containsKey("fireworkPhase"))
        val phase = args.getString("fireworkPhase", "current")
        require(phase.matches(Regex("[a-zA-Z0-9_-]+")))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "firework-audit/$phase")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val manifest = mutableListOf("frame\twidth\theight\targb_sha256")
        fixtures.forEach { fixture ->
            val bitmap = Bitmap.createBitmap(fixture.width, fixture.height, Bitmap.Config.ARGB_8888)
            try {
                var painted = false
                times.forEach { elapsed ->
                    render(bitmap, fixture, elapsed)
                    val pixels = IntArray(fixture.width * fixture.height)
                    bitmap.getPixels(pixels, 0, fixture.width, 0, 0, fixture.width, fixture.height)
                    painted = painted || pixels.any { it ushr 24 != 0 }
                    val bytes = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
                    pixels.forEach(bytes::putInt)
                    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array())
                        .joinToString("") { "%02x".format(it) }
                    val name = "${fixture.name}-$elapsed.png"
                    File(directory, name).outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    manifest += "$name\t${fixture.width}\t${fixture.height}\t$digest"
                }
                assertTrue("Empty celebration: ${fixture.name}", painted)
                if (args.getString("fireworkBenchmark") == "true") benchmark(bitmap, fixture)
            } finally { bitmap.recycle() }
        }
        File(directory, "manifest.tsv").writeText(manifest.joinToString("\n", postfix = "\n"))
        Log.i("FireworkAudit", "Exported ${manifest.size - 1} frames to $directory")
    }

    private fun render(bitmap: Bitmap, fixture: Fixture, elapsed: Long) {
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val centers = if (fixture.celebration == VictoryCelebration.POSTCARD) listOf(
            Offset(.15f * fixture.width, .51f * fixture.height),
            Offset(.84f * fixture.width, .51f * fixture.height),
            Offset(.85f * fixture.width, .69f * fixture.height)) else null
        CanvasDrawScope().draw(Density(fixture.density), LayoutDirection.Ltr,
            Canvas(android.graphics.Canvas(bitmap)), Size(fixture.width.toFloat(), fixture.height.toFloat())) {
            drawVictoryFireworks(elapsed, fixture.celebration, centers)
        }
    }

    /** Includes bitmap clearing and software drawing; it is not a GPU/frame-presentation benchmark. */
    private fun benchmark(bitmap: Bitmap, fixture: Fixture) {
        repeat(30) { render(bitmap, fixture, 2400L) }
        repeat(5) { sample ->
            val start = SystemClock.elapsedRealtimeNanos()
            repeat(60) { render(bitmap, fixture, 2400L) }
            val elapsed = SystemClock.elapsedRealtimeNanos() - start
            Log.i("FireworkAudit", "${fixture.name} sample=$sample frames=60 totalNanos=$elapsed")
        }
    }
}
