package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runtime geometry/state assertions; optional PNGs are evidence for a separate visual review. */
class MenuThemeAcceptanceTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val export: Boolean get() =
        InstrumentationRegistry.getArguments().getString("menuThemeExport") == "true"
    private val directory get() = File(app.getExternalFilesDir(null), "menu-theme-307").apply { mkdirs() }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun allTwelveThemesKeepActionSemanticsInBothContinueStates() {
        val model = GameViewModel(app, null, { 1000L }, false)
        setMenuContent(model) { MenuViewport(360, 800, 1f) }
        for (continued in listOf(false, true)) {
            if (continued) compose.runOnIdle { model.newGame(); model.navigation.menu() }
            GamePalettes.all.forEach { palette ->
                compose.runOnIdle { model.options.setPalette(palette.id) }
                exportMenu(palette.id, MenuViewport(360, 800, 1f), continued)
                assertMenuGeometry(continued, 360)
                compose.runOnIdle { assertEquals(palette.id, model.paletteId) }
            }
        }
        if (export) exportSceneEvidence()
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun portraitWidthsRatiosAndFontScalesKeepTargetsLabelsAndRowsInsideViewport() {
        val model = GameViewModel(app, null, { 1000L }, false)
        var viewport by mutableStateOf(MenuViewport(320, 569, 1f))
        setMenuContent(model) { viewport }
        for (continued in listOf(false, true)) {
            if (continued) compose.runOnIdle { model.newGame(); model.navigation.menu() }
            for (width in listOf(320, 360, 412)) for (ratio in listOf(16f / 9f, 20f / 9f)) {
                for (font in listOf(1f, 1.3f, 2f)) {
                    val fixture = MenuViewport(width, (width * ratio).toInt(), font)
                    compose.runOnIdle { viewport = fixture; model.options.setPalette("classic") }
                    exportMenu("classic", fixture, continued)
                    assertMenuGeometry(continued, width)
                }
            }
            for (palette in GamePalettes.all.filter { it.id != "classic" }) {
                val fixture = MenuViewport(320, 569, 2f)
                compose.runOnIdle { viewport = fixture; model.options.setPalette(palette.id) }
                exportMenu(palette.id, fixture, continued)
                assertMenuGeometry(continued, fixture.width)
            }
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun selectedThemeAndExactGameSurviveMenuResultsBackgroundAndResume() {
        val initial = GameState(active = Piece(Tetromino.T, y = 8), next = Tetromino.O,
            held = Tetromino.L, score = 4321)
        val model = GameViewModel(app, initial, { 1000L }, false)
        model.options.setPalette("dracula")
        model.options.setHints(false)
        setMenuContent(model) { MenuViewport(360, 800, 1f) }
        val before = compose.onNodeWithTag("gridBackground").captureToImage().asAndroidBitmap()
        compose.runOnIdle {
            model.navigation.menu()
            assertSame(initial, model.game)
            model.navigation.showResults()
        }
        compose.onNodeWithTag("resultsPage").assertIsDisplayed()
        compose.runOnIdle {
            model.onBackground(); model.onForeground(); model.navigation.menu(); model.resume()
            assertEquals("dracula", model.paletteId)
            assertSame(initial, model.game)
        }
        val after = compose.onNodeWithTag("gridBackground").captureToImage().asAndroidBitmap()
        assertTrue("Menu styling changed the retained game field", before.sameAs(after))
        compose.runOnIdle {
            val restored = GameViewModel(app, null, { 1000L }, false)
            assertEquals("dracula", restored.paletteId)
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun standaloneResultsUsesMenuBackgroundWhileGameOverKeepsGameplayBackground() {
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.O), next = Tetromino.I,
            gameOver = true), { 1000L }, false)
        model.options.setPalette("classic")
        model.statistics.saveResult(requireNotNull(model.game), 1000L)
        model.statistics.saveRecordName("")
        assertEquals(GameScreen.GAME_OVER, model.screen)
        setMenuContent(model) { MenuViewport(360, 800, 1f) }
        assertResultsBackground(0xff0b1020.toInt())
        compose.runOnIdle { model.navigation.showResults() }
        assertResultsBackground(0xff0c1935.toInt())
    }

    private fun setMenuContent(model: GameViewModel, viewport: () -> MenuViewport) {
        compose.setContent {
            val fixture = viewport()
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                DpSize(fixture.width.dp, fixture.height.dp))) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fixture.font),
                    LocalMenuSkyAnimations provides false) {
                    Box(Modifier.requiredSize(fixture.width.dp, fixture.height.dp).testTag("menuAcceptanceViewport")) {
                        SwypetrisApp(model) {}
                    }
                }
            }
        }
    }

    private fun assertMenuGeometry(continued: Boolean, widthDp: Int) {
        val viewport = compose.onNodeWithTag("menuAcceptanceViewport").fetchSemanticsNode().boundsInRoot
        val expected = mutableListOf("newGame")
        if (continued) expected += "resumeGame"
        expected += listOf("settings", "help", "results", "contacts", "exitGame", "versionCheck")
        val clicks = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(expected, clicks.map { it.config.getOrElse(SemanticsProperties.TestTag) { "" } })
        val bounds = expected.associateWith {
            compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        // Derive density from the exact authored dp viewport, including ForcedSize downscaling.
        val density = viewport.width / widthDp
        bounds.forEach { (tag, rect) ->
            assertTrue("$tag is outside $viewport: $rect", viewport.containsRect(rect))
            assertTrue("$tag width below48dp: $rect", rect.width + 1f >= 48f * density)
            assertTrue("$tag height below48dp: $rect", rect.height + 1f >= 48f * density)
        }
        bounds.toList().forEachIndexed { index, (firstTag, first) ->
            bounds.toList().drop(index + 1).forEach { (secondTag, second) ->
                assertFalse("$firstTag overlaps $secondTag", first.overlaps(second))
            }
        }
        assertMenuText(continued, widthDp)
    }

    private fun assertMenuText(continued: Boolean, widthDp: Int) {
        val textMetrics = StringBuilder()
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) }
            layouts.forEach { layout ->
                val metrics = assertTextFitsNode(node, layout)
                if (export) textMetrics.appendLine("$widthDp\t$continued\t$metrics")
            }
        }
        if (export) appendTextMetrics(textMetrics.toString())
    }

    private fun measureNodeText(node: SemanticsNode, layout: TextLayoutResult): TextLayoutResult {
        // Compose1.7 String semantics rebuilds at parent maxWidth but reports the narrower Paragraph size.
        val input = layout.layoutInput
        lateinit var measured: TextLayoutResult
        compose.runOnIdle {
            measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection,
                cacheSize = 0).measure(input.text, style = input.style, overflow = input.overflow,
                softWrap = input.softWrap, maxLines = input.maxLines, placeholders = input.placeholders,
                constraints = Constraints(maxWidth = node.size.width, maxHeight = node.size.height),
                skipCache = true)
        }
        return measured
    }

    private fun assertTextFitsNode(node: SemanticsNode, layout: TextLayoutResult): String {
        val input = layout.layoutInput
        val measured = measureNodeText(node, layout)
        val paragraphWidth = measured.multiParagraph.width
        val rightmostLine = (0 until measured.lineCount).maxOfOrNull(measured::getLineRight) ?: 0f
        val lastCharacterRight = if (input.text.isNotEmpty())
            measured.getBoundingBox(input.text.lastIndex).right else 0f
        val exceededMaxLines = measured.multiParagraph.didExceedMaxLines
        val ellipsized = (0 until measured.lineCount).any(measured::isLineEllipsized)
        assertTrue("Text overflow: ${input.text}; fontSize=${input.style.fontSize}; " +
            "lineHeight=${input.style.lineHeight}; actualNodeSize=${node.size}; measuredSize=${measured.size}; " +
            "originalConstraints=${input.constraints}; nodeBounds=${node.boundsInRoot}; " +
            "didOverflowWidth=${measured.didOverflowWidth}; didOverflowHeight=${measured.didOverflowHeight}; " +
            "paragraphWidth=$paragraphWidth; rightmostLine=$rightmostLine; " +
            "lastCharacterRight=$lastCharacterRight; didExceedMaxLines=$exceededMaxLines; " +
            "ellipsized=$ellipsized; rawSemanticsParagraphWidth=${layout.multiParagraph.width}",
            !measured.hasVisualOverflow && !exceededMaxLines && !ellipsized &&
                rightmostLine <= node.size.width && lastCharacterRight <= node.size.width)
        return listOf(input.density.fontScale, input.text.toString().replace('\n', ' '),
            node.size.width, node.size.height, paragraphWidth, paragraphWidth - node.size.width,
            rightmostLine, lastCharacterRight, exceededMaxLines, measured.didOverflowWidth,
            measured.didOverflowHeight, layout.multiParagraph.width, layout.didOverflowWidth,
            ellipsized).joinToString("\t")
    }

    private fun appendTextMetrics(metrics: String) {
        val file = File(directory, "text-layout-metrics.tsv")
        if (!file.exists()) file.writeText("viewportWidthDp\tcontinued\tfontScale\ttext\twidthPx\theightPx\t" +
            "paragraphWidthPx\tparagraphWidthMinusIntegerPx\tlineRightPx\tlastCharacterRightPx\t" +
            "didExceedMaxLines\tdidOverflowWidth\tdidOverflowHeight\trawSemanticsParagraphWidthPx\t" +
            "rawSemanticsOverflowWidth\tellipsized\n")
        file.appendText(metrics)
    }

    @androidx.annotation.RequiresApi(26)
    private fun exportMenu(theme: String, fixture: MenuViewport, continued: Boolean) {
        if (!export) return
        val bitmap = compose.onNodeWithTag("menuAcceptanceViewport").captureToImage().asAndroidBitmap()
        val font = fixture.font.toString().replace('.', '_')
        val file = File(directory, "${theme}-${fixture.width}x${fixture.height}-font$font-continue$continued.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @androidx.annotation.RequiresApi(26)
    private fun assertResultsBackground(expected: Int) {
        val image = compose.onNodeWithTag("resultsPage").captureToImage().asAndroidBitmap()
        assertEquals(expected, image.getPixel(image.width - 2, image.height - 2))
    }

    /** Allocation counts describe retained bitmaps, not process RSS or hardware frame performance. */
    private fun exportSceneEvidence() {
        File(directory, "decoded-bitmap-bytes.csv").bufferedWriter().use { writer ->
            writer.appendLine("theme,retainedBitmapAllocationBytes")
            GamePalettes.all.forEach { palette ->
                val scene = loadMenuScene(app.resources, palette.id)
                writer.appendLine("${palette.id},${scene.decodedBitmapBytes}")
                exportSceneFrames(palette.id, scene)
            }
        }
    }

    private fun exportSceneFrames(theme: String, scene: MenuSceneArtwork) {
        if (theme !in listOf("classic", "dracula", "catppuccin", "tokyo_night")) return
        val image = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(image)
        for (seconds in listOf(0.0, 3.1, 8.0, 14.2, 26.0, 60.0)) {
            scene.draw(canvas, 360f, 640f, seconds)
            val file = File(directory, "scene-$theme-t${seconds.toString().replace('.', '_')}.png")
            file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    private fun Rect.containsRect(other: Rect): Boolean = other.left >= left - 1f && other.top >= top - 1f &&
        other.right <= right + 1f && other.bottom <= bottom + 1f
    private data class MenuViewport(val width: Int, val height: Int, val font: Float)
}
