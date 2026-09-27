package ru.itoltec.swypetris

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LegalScreenTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun contactsOpenOfflineNoticesAndBundledLicense() {
        val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        compose.runOnIdle { model.finishLaunchIntro(); model.contacts() }
        compose.onNodeWithTag("contactsPage").performScrollToNode(hasTestTag("legal"))
        compose.onNodeWithTag("legal").performClick()
        compose.onNodeWithTag("legalPage").assertIsDisplayed()
        compose.onNodeWithText("Коробейники").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("legalPage").performScrollToNode(hasTestTag("license-vscode-monokai.txt"))
        compose.onNodeWithTag("license-vscode-monokai.txt").performClick()
        compose.onNodeWithText("MIT License", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Закрыть").performClick()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(GameScreen.CONTACTS, model.screen) }
        compose.onNodeWithTag("contactsPage").assertIsDisplayed()
    }
}
