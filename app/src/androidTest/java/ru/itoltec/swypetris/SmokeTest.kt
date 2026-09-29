package ru.itoltec.swypetris

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Basic launch, game, pause and resume path used by focused CI and release regression. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchGamePauseAndContinue() {
        compose.onNodeWithTag("newGame").performClick()
        compose.onNodeWithTag("board").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("mainMenu").assertIsDisplayed()
        compose.onNodeWithTag("resumeGame").performClick()
        compose.onNodeWithTag("board").assertIsDisplayed()
    }
}
