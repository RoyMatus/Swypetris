package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdatesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun releaseMetadataMustMatchVersionTagAndApk() {
        val release = JSONObject().put("tag_name", "v1.2.0").put("assets", JSONArray().put(JSONObject()
            .put("name", "Swypetris.apk").put("state", "uploaded")
            .put("digest", "sha256:" + "a".repeat(64)).put("size", 1234)
            .put("browser_download_url", "https://github.com/RoyMatus/Swypetris/releases/download/v1.2.0/Swypetris.apk")))
        val metadata = JSONObject().put("versionCode", 4).put("versionName", "1.2.0").put("sha256", "a".repeat(64))
        assertEquals(4L, parseGitHubRelease(release, metadata)?.versionCode)
        assertTrue(newerVersion(4, 3))
        assertFalse(newerVersion(4, 4))
        assertNull(parseGitHubRelease(release, JSONObject(metadata.toString()).put("versionName", "1.1.0")))
        assertNull(parseGitHubRelease(release, JSONObject(metadata.toString()).put("sha256", "wrong")))
        assertNull(parseGitHubRelease(release, JSONObject().put("versionCode", 4).put("versionName", "1.2.0")))
        release.getJSONArray("assets").getJSONObject(0).put("digest", JSONObject.NULL)
        assertEquals(4L, parseGitHubRelease(release, metadata)?.versionCode)
        release.getJSONArray("assets").getJSONObject(0).put("size", MAX_UPDATE_BYTES + 1)
        assertNull(parseGitHubRelease(release, metadata))
    }

    @Test fun versionAndManualCheckAreVisible() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, null, { 1000L }, false)
        compose.setContent { SwypetrisApp(model) {} }
        compose.onNodeWithTag("versionCheck").assertIsDisplayed().assert(hasClickAction())
        compose.runOnIdle { model.settings() }
        compose.onNodeWithTag("checkUpdates").performScrollTo().assertIsDisplayed().assert(hasClickAction())
    }
}
