package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.annotation.RequiresApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.itoltec.swypetris.ui.theme.SwypetrisTheme
import java.io.File
import kotlin.math.roundToInt

/** Checks the saved-record artwork and hierarchy through the real Results screen. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class ResultsHistoryIntegrationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun bestCardReplacesSummaryAndKeepsItsOwnEarnedFruit() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = GameStorage.preferences(application)
        val records = listOf(
            result("hard", 2_466, 2_000L, Difficulty.HARD),
            result("medium", 42_500, 3_000L, Difficulty.MEDIUM),
            result("easy-best", 999_999, 1_000L, Difficulty.EASY),
            result("unknown-history", 80_000, 4_000L, null, rulesVersion = 1)
        )
        ResultStore(preferences).write(records)
        var model by mutableStateOf(GameViewModel(application, null, { 5_000L }, false))
        model.navigation.showResults()
        val density = mutableStateOf(Density(3.375f, 1f))

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides density.value) {
                SwypetrisTheme(darkTheme = true, dynamicColor = false) { ResultsScreen(model) }
            }
        }

        assertHistoryHierarchyAndOwnAwards()
        assertPortraitMatrix(density)
    }

    private fun assertHistoryHierarchyAndOwnAwards() {
        compose.onNodeWithTag("resultsPage").assertIsDisplayed()
        compose.onNodeWithText("История рекордов").assertIsDisplayed()
        compose.onNodeWithTag("recordSummary").assertDoesNotExist()
        compose.onNodeWithTag("bestRecordCard").assertIsDisplayed()
        compose.onNodeWithTag("bestRecordEyebrow").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("bestRecordEyebrow")).assertCountEquals(1)
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordCard-medium"))
        compose.onNodeWithTag("recordCard-medium").assertIsDisplayed()
        compose.onNodeWithTag("recordArtwork-medium").assertDoesNotExist()
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordCard-hard"))
        compose.onNodeWithTag("recordCard-hard").assertIsDisplayed()

        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("bestRecordCard"))
        compose.onNodeWithTag("bestRecordCard").assertIsDisplayed()
        val expectedFruitTags = (0..7).map { "recordFruit-easy-best-$it" }
        expectedFruitTags.forEachIndexed { index, tag ->
            compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertContentDescriptionEquals(
                if (index < 3) "${Fruit.entries[index].title}, 13" else "${Fruit.entries[index].title}, 12")
                .assertIsDisplayed()
        }
        val badgeTags = (0..7).map { "recordFruitBadge-easy-best-$it" }
        badgeTags.forEach {
            compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag(it))
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordFruit-easy-best-0"))
        compose.onAllNodes(hasTestTag("recordFruit-easy-best-0")).assertCountEquals(1)
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordFruit-medium-0"))
        compose.onAllNodes(hasTestTag("recordFruit-medium-0")).assertCountEquals(1)
        compose.onNodeWithTag("recordFruit-medium-4").assertDoesNotExist()
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordCard-hard"))
        compose.onNodeWithTag("recordFruit-hard-0").assertDoesNotExist()
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordCard-unknown-history"))
        compose.onNodeWithTag("recordFruit-unknown-history-0").assertDoesNotExist()
        compose.onNodeWithText("—").assertIsDisplayed()

    }

    @RequiresApi(26)
    private fun assertPortraitMatrix(density: androidx.compose.runtime.MutableState<Density>) {
        for ((widthDp, fontScale, densityValue) in listOf(
            Triple(240, 1f, 4.5f), Triple(320, 1.5f, 3.375f), Triple(393, 2f, 1080f / 393f)
        )) {
            compose.runOnIdle { density.value = Density(densityValue, fontScale) }
            val best = compose.onNodeWithTag("bestRecordCard").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordArtwork-easy-best"))
            captureNode("recordArtwork-easy-best", "artwork-${widthDp}-font$fontScale")
            val art = compose.onNodeWithTag("recordArtwork-easy-best").assertIsDisplayed()
            val artBounds = art.fetchSemanticsNode().boundsInRoot
            val bestBounds = best.fetchSemanticsNode().boundsInRoot
            assertTrue("Artwork escaped its card at $widthDp dp", contains(bestBounds, artBounds))
            val activeBadgeTags = (0..7).map { "recordFruitBadge-easy-best-$it" }
            val badgeBounds = activeBadgeTags.map { tag ->
                compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            assertNoBadgeOverlap(badgeBounds, widthDp, fontScale, densityValue)
            assertTrue("The selected card should have enough height for its content at $widthDp dp",
                bestBounds.height > artBounds.height)
            listOf("recordName-easy-best", "recordScore-easy-best", "recordDate-easy-best")
                .forEach(::assertTextFitsAvailableWidth)
        }
    }

    @Test fun allSuppliedScoreFixturesShowOnlyTheirEarnedFruitAndQuantities() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = GameStorage.preferences(application)
        val fixtures = listOf(
            2_466 to listOf(0, 0, 0, 0, 0, 0, 0, 0),
            42_500 to listOf(1, 1, 1, 1, 0, 0, 0, 0),
            80_000 to listOf(1, 1, 1, 1, 1, 1, 1, 1),
            90_000 to listOf(2, 1, 1, 1, 1, 1, 1, 1),
            788_267 to listOf(10, 10, 10, 10, 10, 10, 9, 9),
            999_999 to listOf(13, 13, 13, 12, 12, 12, 12, 12)
        )
        var model by mutableStateOf(GameViewModel(application, null, { 5_000L }, false))
        lateinit var renderedDensity: Density
        compose.setContent {
            renderedDensity = LocalDensity.current
            SwypetrisTheme(darkTheme = true, dynamicColor = false) { ResultsScreen(model) }
        }
        fixtures.forEach { (score, expectedCounts) ->
            compose.runOnIdle {
                ResultStore(preferences).write(listOf(result("fixture", score, 1_000L, Difficulty.EASY)))
                model = GameViewModel(application, null, { 5_000L }, false)
                model.navigation.showResults()
            }
            compose.onNodeWithTag("bestRecordCard").performScrollTo().assertIsDisplayed()
            Fruit.entries.forEachIndexed { index, fruit ->
                val fruitTag = "recordFruit-fixture-$index"
                val badgeTag = "recordFruitBadge-fixture-$index"
                val count = expectedCounts[index]
                if (count == 0) {
                    compose.onNodeWithTag(fruitTag).assertDoesNotExist()
                    compose.onNodeWithTag(badgeTag).assertDoesNotExist()
                } else {
                    val description = if (count == 1) fruit.title else "${fruit.title}, $count"
                    compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag(fruitTag))
                    compose.onNodeWithTag(fruitTag).assertContentDescriptionEquals(description).assertIsDisplayed()
                    if (count > 1) {
                        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag(badgeTag))
                        val badge = compose.onNodeWithTag(badgeTag)
                        val bounds = badge.fetchSemanticsNode().boundsInRoot
                        if (renderedDensity.fontScale == 1f) assertEquals("Normal badge must be 16dp",
                            (16f * renderedDensity.density).roundToInt(), bounds.width.roundToInt())
                        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
                        val cardBounds = compose.onNodeWithTag("recordCard-fixture").fetchSemanticsNode().boundsInRoot
                        val artBounds = compose.onNodeWithTag("recordArtwork-fixture").fetchSemanticsNode().boundsInRoot
                        assertTrue(
                            "$badgeTag bounds $bounds; card $cardBounds; artwork $artBounds; " +
                                "root $rootBounds; score $score",
                            overlaps(bounds, rootBounds)
                        )
                        badge.assertIsDisplayed()
                    }
                    else compose.onNodeWithTag(badgeTag).assertDoesNotExist()
                }
            }
            captureNode("recordCard-fixture", "card-$score")
        }
    }

    @RequiresApi(26)
    private fun captureNode(tag: String, name: String) {
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag(tag))
        val image = compose.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = requireNotNull(application.getExternalFilesDir("record-history-captures"))
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun assertNoBadgeOverlap(bounds: List<Rect>, widthDp: Int, fontScale: Float, density: Float) {
        for (first in bounds.indices) for (second in first + 1 until bounds.size) {
            val dx = kotlin.math.abs(bounds[first].center.x - bounds[second].center.x)
            val dy = kotlin.math.abs(bounds[first].center.y - bounds[second].center.y)
            val centerDistance = kotlin.math.hypot(dx, dy)
            val radii = (bounds[first].width + bounds[second].width) / 2f
            assertTrue("Badge gap at ${widthDp}dp/font$fontScale: $first/$second = " +
                "${centerDistance - radii}px, minimum ${density}px; bounds $bounds",
                centerDistance - radii >= density - 0.01f)
        }
    }

    private fun contains(outer: Rect, inner: Rect): Boolean =
        inner.left >= outer.left && inner.top >= outer.top &&
            inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(first: Rect, second: Rect): Boolean =
        first.left < second.right && first.right > second.left &&
            first.top < second.bottom && first.bottom > second.top

    private fun assertTextFitsAvailableWidth(tag: String) {
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag(tag))
        val node = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        val input = layouts.single().layoutInput
        lateinit var measured: TextLayoutResult
        compose.runOnIdle {
            TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0)
                .measure(input.text, style = input.style, overflow = input.overflow, softWrap = input.softWrap,
                    maxLines = input.maxLines, placeholders = input.placeholders,
                    constraints = Constraints(maxWidth = node.size.width, maxHeight = node.size.height),
                    skipCache = true)
                .also { measured = it }
        }
        assertFalse("$tag has clipped height", measured.didOverflowHeight)
        assertFalse("$tag has visual overflow", measured.hasVisualOverflow)
        assertFalse("$tag is ellipsized", (0 until measured.lineCount).any(measured::isLineEllipsized))
    }

    private fun result(id: String, score: Int, date: Long, difficulty: Difficulty?,
        rulesVersion: Int = GameRules.VERSION) =
        GameResult(id, date, "Игрок $id", score, lines = score / 1000, level = 4, durationMillis = 60_000L,
            rulesVersion = rulesVersion, completedRounds = 2, difficulty = difficulty)
}
