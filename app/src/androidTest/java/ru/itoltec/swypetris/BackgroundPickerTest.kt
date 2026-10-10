package ru.itoltec.swypetris

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import android.app.UiAutomation
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundPickerTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    /** Select a real scoped-storage image through DocumentsUI, including the result callback. */
    @SdkSuppress(minSdkVersion = 29)
    @Test fun systemPickerReturnsImageAndOpensCropPreview() {
        val resolver = compose.activity.contentResolver
        val name = "Swypetris-background-${UUID.randomUUID()}"
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SwypetrisTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        val bitmap = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.YELLOW)
        resolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        check(resolver.update(uri, ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }, null, null) == 1)
        val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            compose.runOnIdle { model.navigation.finishLaunchIntro(); model.navigation.settings() }
            compose.waitUntil(10000) { !model.background.busy }
            compose.onNodeWithTag("chooseBackground").performScrollTo().performClick()
            automation.waitForIdle(500, 5000)
            selectDocument(awaitPickerDocument(automation, name))
            compose.waitUntil(10000) { model.background.draft != null && !model.background.busy }
            compose.onNodeWithTag("backgroundCropPreview").assertExists()
            assertNotNull(model.background.draft)
            compose.runOnIdle { model.background.cancel() }
        } catch (error: ComposeTimeoutException) {
            pickerDiagnostics(automation, name)
            throw error
        } catch (error: AssertionError) {
            pickerDiagnostics(automation, name)
            throw error
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    private fun awaitPickerDocument(automation: UiAutomation, name: String): AccessibilityNodeInfo {
        var document: AccessibilityNodeInfo? = null
        var rootsOpened = false
        var imagesOpened = false
        var folderOpened = false
        compose.waitUntil(10000) {
            if (android.os.Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val root = automation.rootInActiveWindow
            // Package visibility may hide DocumentsUI from resolveActivity even though it launches.
            if (root != null && root.packageName?.toString() != compose.activity.packageName) {
                document = root.findAccessibilityNodeInfosByText(name).firstOrNull()
                // Browse the fixture folder from either Recent or the previously visited image root.
                if (document == null) {
                    when {
                        folderOpened -> Unit
                        clickPickerItem(root, "SwypetrisTest") -> folderOpened = true
                        !rootsOpened -> rootsOpened = clickPickerItem(root, "Show roots")
                        !imagesOpened -> imagesOpened = clickPickerItem(root, "Images", "Изображения")
                    }
                }
            }
            document != null
        }
        return checkNotNull(document)
    }

    private fun clickPickerItem(root: AccessibilityNodeInfo, vararg labels: String): Boolean {
        // A drawer root can repeat the current toolbar title; its item follows the toolbar in the tree.
        val node = labels.firstNotNullOfOrNull { label ->
            root.findAccessibilityNodeInfosByText(label).lastOrNull { it.isVisibleToUser }
        }
            ?: return false
        // Drawer labels belong to clickable rows. Invoke their advertised action
        // instead of mistaking an injected touch during drawer animation for a click.
        val target = generateSequence(node) { it.parent }.firstOrNull { candidate ->
            candidate.isEnabled && candidate.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
        } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** GridView item delegates advertise ACTION_CLICK even when isClickable is false. */
    private fun selectDocument(document: AccessibilityNodeInfo) {
        val directory = File(compose.activity.getExternalFilesDir(null), "background-picker-diagnostics")
            .apply { mkdirs() }
        File(directory, "selected-node.txt").writeText("Document action target: $document")
        assertTrue(document.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        assertTrue(document.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun pickerDiagnostics(automation: UiAutomation, name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "background-picker-diagnostics")
            .apply { mkdirs() }
        val root = automation.rootInActiveWindow
        File(directory, "active-window.txt").writeText("Expected document: $name\n" + tree(root))
        val info = automation.serviceInfo
        val originalFlags = info.flags
        try {
            info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            File(directory, "windows.txt").writeText(automation.windows.joinToString("\n") { tree(it.root) })
            if (android.os.Build.VERSION.SDK_INT >= 34) automation.clearCache()
            File(directory, "fresh-window.txt").writeText(tree(automation.rootInActiveWindow))
            val screenshot = automation.takeScreenshot()
            File(directory, "picker.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }

    private fun tree(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 12) return ""
        return node.toString() + "\n" + (0 until node.childCount).joinToString("\n") {
            tree(node.getChild(it), depth + 1)
        }
    }
}
