package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.height
import androidx.compose.ui.test.left
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.right
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет отрисовку и игровые часы удаления без ожидания реальных 600 мс. */
@RunWith(AndroidJUnit4::class)
class LineClearUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    /** Создаёт фигуру, которая следующим шагом заполнит нижнюю строку. */
    private fun almostFull(): GameState {
        val board = List(BoardGeometry.TOTAL_ROWS) { MutableList<Tetromino?>(10) { null } }
        for (x in 0..7) board[BoardGeometry.row(19)][x] = Tetromino.J
        return GameState(board = board, active = Piece(Tetromino.O, x = 8, y = 18), next = Tetromino.T)
    }

    /** Local highlight, frozen time and exactly-once completion on the 600 ms boundary. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun clearPausesAndCompletesOnControlledClock() {
        var now = 1000L
        val model = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), almostFull(), { now },
            false)
        // Use the same 10 × 22 square-cell aspect ratio as the gameplay board including its spawn band.
        compose.setContent { Box(Modifier.size(220.dp, 484.dp)) {
            model.game?.let { Board(it, model.clearElapsedMillis) }
        } }
        compose.runOnIdle {
            model.input.command(GameCommand.HARD_DROP)
            val locked = model.game
            assertEquals(listOf(BoardGeometry.row(19)), locked!!.clearingRows)
            now += 30
            model.simulation.advanceFrame(now)
            assertEquals(30L, model.clearElapsedMillis)
            model.input.command(GameCommand.HARD_DROP)
            model.input.pointerDown(0f, 0f, now)
            model.input.pointerUp(0f, 0f, now + 1)
            assertEquals(locked, model.game)
        }
        val beforeRemoval = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        compose.runOnIdle {
            now += 30
            model.simulation.advanceFrame(now)
        }
        val white = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val bottomRowY = assertClearHighlights(beforeRemoval, white)
        compose.runOnIdle {
            model.pause()
            now += 5000
            model.simulation.advanceFrame(now)
            assertEquals(60L, model.clearElapsedMillis)
            model.navigation.menu()
            now += 5000
            model.simulation.advanceFrame(now)
            assertEquals(60L, model.clearElapsedMillis)
            model.resume()
            now += 539
            model.simulation.advanceFrame(now)
            assertEquals(0, model.game!!.generation)
            now += 1
            model.simulation.advanceFrame(now)
            assertTrue(model.game!!.clearingRows.isEmpty())
            assertEquals(1, model.game!!.generation)
            assertEquals(100L, model.game!!.score)
            assertEquals(Tetromino.T, model.game!!.active.type)
            model.simulation.advanceFrame(now)
            assertEquals(100L, model.game!!.score)
        }
        val cleared = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        assertNotEquals(white[white.width / 20, bottomRowY],
            cleared[cleared.width / 20, bottomRowY])
    }

    /** Новая партия сбрасывает ещё не завершённую очистку и не получает старые очки. */
    @Test fun newGameCancelsPendingClear() {
        var now = 1000L
        val model = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), almostFull(), { now },
            false)
        compose.runOnIdle {
            model.input.command(GameCommand.HARD_DROP)
            now += 100
            model.simulation.advanceFrame(now)
            model.newGame()
            assertEquals(0L, model.clearElapsedMillis)
            assertTrue(model.game!!.clearingRows.isEmpty())
            now += 300
            model.simulation.advanceFrame(now)
            assertEquals(0L, model.game!!.score)
            assertEquals(0, model.game!!.generation)
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun shardsStayClippedAndSettleToTheExistingClearResult() {
        var state by mutableStateOf(clearFixture(listOf(19)))
        var elapsed by mutableLongStateOf(0)
        var reduced by mutableStateOf(false)
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var width by mutableIntStateOf(260)
        var height by mutableIntStateOf(524)
        compose.setContent {
            CompositionLocalProvider(LocalGamePalette provides palette) {
                Box(Modifier.size(width.dp, height.dp).background(Color.Magenta).padding(20.dp)
                    .background(palette.background)) {
                    Board(state, elapsed, drawActive = false, reducedMotion = reduced)
                }
            }
        }
        for (theme in listOf("classic", "github_light")) {
            for (rows in listOf(listOf(19), listOf(18, 19), listOf(17, 18, 19),
                listOf(16, 17, 18, 19), listOf(3, 17, 19))) {
                val initial = clearFixture(rows)
                compose.runOnIdle { state = initial; elapsed = 0; palette = GamePalettes.find(theme); reduced = false }
                for (phase in listOf(0L, 40L, 80L, 160L, 280L, 360L, 480L, 600L)) {
                    compose.runOnIdle { elapsed = phase }
                    assertClipped("shards-$theme-${rows.joinToString("-")}-$phase")
                }
                assertSettled { state = GameEngine().finishClear(initial); elapsed = 0 }
            }
        }
        val wide = clearFixture(listOf(16, 17, 18, 19))
        compose.runOnIdle { state = wide; width = 520; height = 340; palette = GamePalettes.find("classic") }
        for (phase in listOf(40L, 160L, 480L, 600L)) {
            compose.runOnIdle { elapsed = phase }
            assertClipped("shards-wide-$phase")
        }
        assertSettled { state = GameEngine().finishClear(wide); elapsed = 0 }
        compose.runOnIdle { state = clearFixture(listOf(19)); elapsed = 0; reduced = true }
        val still = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        compose.runOnIdle { elapsed = 280 }
        val quiet = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val clearTop = (quiet.height * 21f / (BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS)).toInt() - 2
        // Compare every pixel; construct a coordinate-rich failure only if one differs.
        for (y in 0 until clearTop) for (x in 0 until quiet.width)
            if (still[x, y] != quiet[x, y])
                assertEquals("Reduced motion must not fly or shift cells at $x/$y", still[x, y], quiet[x, y])
    }

    @androidx.annotation.RequiresApi(26)
    private fun assertClipped(name: String) {
        val image = compose.onRoot().captureToImage()
        val pixels = image.toPixelMap()
        val bounds = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val left = (bounds.left - root.left).toInt()
        val top = (bounds.top - root.top).toInt()
        val right = (bounds.right - root.left).toInt()
        val bottom = (bounds.bottom - root.top).toInt()
        for (y in 0 until pixels.height step 3) for (x in 0 until pixels.width step 3) {
            val outsideHorizontal = x < left - 1 || x > right + 1
            val outsideVertical = y < top - 1 || y > bottom + 1
            if ((outsideHorizontal || outsideVertical) && pixels[x, y] != Color.Magenta)
                assertEquals("Shard outside board: $name at $x/$y", Color.Magenta, pixels[x, y])
        }
    }

    @androidx.annotation.RequiresApi(26)
    private fun assertSettled(commit: () -> Unit) {
        val settled = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        compose.runOnIdle(commit)
        val committed = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        // Next spawn hints are outside this comparison; settled board cells must match exactly.
        val top = (settled.height * 3f / (BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS)).toInt() + 2
        for (y in top until settled.height) for (x in 0 until settled.width)
            if (committed[x, y] != settled[x, y])
                assertEquals("Visual result differs from canonical removal at $x/$y", committed[x, y], settled[x, y])
    }

    private fun saveArtifact(name: String, write: (java.io.File) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(app.getExternalFilesDir(null), name)
        write(file)
        // UTP removes test-app storage after the run, so retain inspection artifacts in Downloads.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /sdcard/Download/swypetris-issue161-captures",
            "cp ${file.absolutePath} /sdcard/Download/swypetris-issue161-captures/$name")) {
            automation.executeShellCommand(command).use {
                assertEquals("Artifact command failed", "",
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().decodeToString())
            }
        }
    }

    @Test fun realClearTimerSamplesBoundedFramesWithoutAddingGameplayDelay() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val startedAt = android.os.SystemClock.uptimeMillis()
        var liveClock = false
        var recording = false
        lateinit var model: GameViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = GameViewModel(app, clearFixture(listOf(16, 17, 18, 19)),
                { if (liveClock) android.os.SystemClock.uptimeMillis() else startedAt },
                false, timer = AndroidGameTimer())
            model.pause()
        }
        val costs = mutableListOf<Long>()
        var firstDrawAt: Long? = null
        var firstDrawClearing: Boolean? = null
        try {
            compose.setContent {
                Box(Modifier.size(240.dp, 528.dp).drawWithContent {
                    val clearing = model.game!!.clearingRows.isNotEmpty()
                    // Observe the same draw-phase clock as the child Canvas: child invalidation alone
                    // does not rerun an unchanged parent's drawWithContent measurement.
                    val elapsed = model.clearElapsedMillis
                    if (firstDrawAt == null) {
                        firstDrawAt = android.os.SystemClock.uptimeMillis()
                        firstDrawClearing = clearing
                    }
                    val start = System.nanoTime()
                    drawContent()
                    if (recording && clearing && elapsed < LineClearAnimation.TOTAL_MILLIS)
                        costs += System.nanoTime() - start
                }) { Board(model.game!!, clearTime = { model.clearElapsedMillis }) }
            }
            // The normal clear occurs on an already drawn board, not during first composition/resource loading.
            compose.waitUntil(5000) {
                var drawn = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync { drawn = firstDrawAt != null }
                drawn
            }
            compose.runOnIdle {
                assertEquals(0L, model.clearElapsedMillis)
                liveClock = true
                recording = true
                model.resume()
            }
            compose.waitUntil(5000) { model.game!!.clearingRows.isEmpty() }
            val sorted = costs.sorted()
            saveArtifact("shards-frame-cost.txt") { file ->
                file.writeText("CPU draw recording only; excludes asynchronous GPU work.\nframes=${costs.size}\n" +
                    "median_ms=${sorted.getOrNull(sorted.size / 2)?.div(1_000_000.0)}\n" +
                    "max_ms=${sorted.lastOrNull()?.div(1_000_000.0)}\n" +
                    "first_draw_after_model_ms=${firstDrawAt?.minus(startedAt)}\n" +
                    "first_draw_clearing=$firstDrawClearing\n")
            }
            compose.runOnIdle {
                assertEquals(1, model.game!!.generation)
                assertEquals(800L, model.game!!.score)
                assertTrue("Must render several real animation frames", costs.size >= 5)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.pause() }
        }
    }

    @Test fun restoreKeepsEachClearPhaseAndCompletesOnlyOnce() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (phase in listOf(40L, 160L, 480L)) {
            GameStorage.preferences(app).edit().clear().commit()
            var now = 1000L
            val original = GameViewModel(app, almostFull(), { now }, false)
            original.input.command(GameCommand.HARD_DROP)
            now += phase
            original.simulation.advanceFrame(now)
            original.onBackground()
            val frozen = original.game
            val shards = LineClearAnimation.shards(frozen!!)
            now += 10000
            val restored = GameViewModel(app, null, { now }, false)
            assertEquals(frozen, restored.game)
            assertEquals(phase, restored.clearElapsedMillis)
            assertEquals(shards, LineClearAnimation.shards(restored.game!!))
            restored.onForeground()
            restored.resume()
            now += 599 - phase
            restored.simulation.advanceFrame(now)
            assertEquals(0, restored.game!!.generation)
            now++
            restored.simulation.advanceFrame(now)
            assertEquals(1, restored.game!!.generation)
            assertEquals(100L, restored.game!!.score)
            restored.simulation.advanceFrame(now)
            assertEquals(1, restored.game!!.generation)
            assertEquals(100L, restored.game!!.score)
            restored.pause()
        }
    }

    private fun clearFixture(rows: List<Int>): GameState {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        rows.forEach { row -> repeat(10) { column -> board[BoardGeometry
            .row(row)][column] = Tetromino.entries[column % 7] } }
        board[BoardGeometry.row(rows.min() - 1)][1] = Tetromino.T
        board[BoardGeometry.row(-3)][1] = Tetromino.I
        return GameState(board = board, active = Piece(Tetromino.O), next = Tetromino.T,
            clearingRows = rows.map(BoardGeometry::row), completedClears = 2)
    }

    private fun assertClearHighlights(beforeRemoval: androidx.compose.ui.graphics.PixelMap,
        white: androidx.compose.ui.graphics.PixelMap): Int {
        val side = minOf(
            white.width.toFloat() / BoardGeometry.WIDTH,
            white.height.toFloat() / (BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS)
        )
        val boardLeft = (white.width - side * BoardGeometry.WIDTH) / 2f
        val bottomRowY = ((SPAWN_DISPLAY_ROWS + 19.5f) * side).toInt()
        val leftCellX = (boardLeft + .5f * side).toInt()
        val rightCellX = (boardLeft + 9.5f * side).toInt()
        assertNotEquals(beforeRemoval[leftCellX, bottomRowY], white[leftCellX, bottomRowY])
        assertNotEquals(beforeRemoval[rightCellX, bottomRowY], white[rightCellX, bottomRowY])
        return bottomRowY
    }
}
