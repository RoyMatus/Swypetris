package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Long scores must remain readable at narrow portrait sizes and large text. */
class EndlessScoreUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun largestStoredScoreDoesNotClipInBestRecordCard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GameStorage.migrate(GameStorage.preferences(app))
        ResultStore(GameStorage.preferences(app)).write(listOf(GameResult("huge", 100, "Roy", Long.MAX_VALUE,
            100, 11, 20, completedRounds = Long.MAX_VALUE / GameRules.ROUND_SCORE)))
        val model = GameViewModel(app, null, { 1000L }, false)
        model.navigation.showResults()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))
                then DeviceConfigurationOverride.FontScale(2f)) {
                CompositionLocalProvider(LocalVictoryAnimations provides false) { SwypetrisApp(model) {} }
            }
        }
        compose.onNodeWithTag("resultsPage").performScrollToNode(hasTestTag("recordScore-huge"))
        val score = compose.onNodeWithTag("recordScore-huge")
        score.assertIsDisplayed()
        val node = score.fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        assertTrue(results.isNotEmpty())
        assertFalse(results.single().hasVisualOverflow)
    }
}
