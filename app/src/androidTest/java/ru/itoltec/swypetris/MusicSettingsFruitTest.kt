package ru.itoltec.swypetris

import android.app.Application
import android.media.AudioTrack
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.center
import androidx.compose.ui.test.left
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import java.io.File
import android.graphics.Bitmap

/** Проверяет прослушивание, миграцию выбора и компактную колонку наград без пользовательских данных. */
class MusicSettingsFruitTest {
    @get:Rule(order=0) val storage = IsolatedStorageRule()
    @get:Rule(order=1) val compose = createComposeRule()

    /** Запоминает музыкальные команды без обращения к динамику. */
    private class Recorder : MusicPlayback {
        val selections = mutableListOf<MusicSelection>()
        val modes = mutableListOf<MusicMode>()
        /** Сохраняет запрос выбора, чтобы обнаружить лишний перезапуск. */
        override fun select(selection: MusicSelection) { selections += selection }
        /** Переводит разрешение в запрошенный режим. */
        override fun setPlaying(enabled: Boolean) = setMode(if (enabled) MusicMode.GAME else MusicMode.SILENT)
        /** Сохраняет режим без реального звука. */
        override fun setMode(next: MusicMode) { modes += next }
        /** Тестовый проигрыватель не владеет устройствами. */
        override fun release() = Unit
    }

    /** Выбор сразу звучит, одинаковый выбор не перезапускает, фон глушит звук, а меню включает свою тему. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun previewLifecyclePersistenceAndMigration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GameStorage.preferences(app).edit().putBoolean("music",false).commit()
        val recorder = Recorder()
        val initial = GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=10000)
        val model = GameViewModel(app,initial,{1000L},false,musicPlayback=recorder)
        lateinit var hostView: android.view.View
        assertEquals(MusicSelection.Off,model.musicSelection)
        model.navigation.settings()
        compose.setContent { hostView = LocalView.current; SwypetrisApp(model) {} }
        compose.onNodeWithTag("musicPicker").performScrollTo().performClick()
        compose.onNodeWithTag("music_trepak").performScrollTo().performClick()
        val focused = GameViewModel::class.java.getDeclaredField("windowFocused").apply { isAccessible = true }
        // Finish the genuine popup-to-host focus transfer before simulating lifecycle callbacks on that host.
        compose.waitUntil(5000) {
            var ready = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ready = hostView.hasWindowFocus() && focused.getBoolean(model)
            }
            ready
        }
        compose.runOnIdle {
            assertEquals(MusicSelection.Track(Song.TREPAK),model.musicSelection)
            assertEquals(MusicMode.MENU,recorder.modes.last())
            val count=recorder.selections.size
            model.options.chooseMusic(model.musicSelection)
            assertEquals(count,recorder.selections.size)
            model.onBackground(); assertEquals(MusicMode.SILENT,recorder.modes.last())
            model.onForeground(); assertEquals(MusicMode.MENU,recorder.modes.last())
            model.navigation.menu(); assertEquals(MusicMode.MENU,recorder.modes.last())
            model.resume(); assertEquals(MusicMode.GAME,recorder.modes.last())
            assertEquals(initial,model.game)
            assertEquals(model.musicSelection,GameViewModel(app,null,{1000L},false).musicSelection)
            model.navigation.settings(); model.options.chooseMusic(MusicSelection.ShuffleAll)
            assertEquals(MusicMode.MENU,recorder.modes.last())
            model.options.chooseMusic(MusicSelection.Off)
            assertEquals(MusicMode.SILENT,recorder.modes.last())
            assertFalse(GameViewModel(app,null,{1000L},false).musicEnabled)
            assertTrue(model.results.isEmpty())
        }
    }

    /** Музыка и сетка тем доступны при 320 dp и двойном шрифте. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun musicPickerLargeFontInLightAndDarkThemes() {
        val model=GameViewModel(ApplicationProvider.getApplicationContext(),null,{1000L},false)
        model.navigation.settings(); model.options.setPalette("github_light")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
            }
        }
        for (theme in listOf("github_light","classic")) {
            compose.runOnIdle { model.options.setPalette(theme) }
            compose.onNodeWithTag("musicPicker").performScrollTo().performClick()
            compose.onNodeWithTag("music_sugar_plum").performScrollTo().performClick()
            compose.onNodeWithTag("musicPicker").assertIsDisplayed()
            compose.onNodeWithTag("paletteGrid").performScrollTo().assertIsDisplayed()
        }
    }

    /** Earned fruits stay below the right-aligned score and away from the spawn/preview area. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun earnedFruitsHaveNoPlaceholdersAndAvoidPreview() {
        var count by mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.size(320.dp,480.dp)) {
                val state=GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=count*10000)
                Board(state); GameHud(state)
            }
        }
        for (n in listOf(0,1,7,8,9,0)) {
            compose.runOnIdle { count=n }
            if (n==0) compose.onNodeWithTag("earnedFruits").assertDoesNotExist()
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            var previous: androidx.compose.ui.geometry.Rect? = null
            Fruit.entries.forEachIndexed { index,fruit ->
                val node=compose.onNodeWithTag("earnedFruit_${fruit.name}")
                if (index<n) {
                    node.assertIsDisplayed()
                    val bounds=node.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.left >= root.left)
                    assertTrue(bounds.left > root.left + root.width * .6f)
                    previous?.let { assertTrue(bounds.top >= it.bottom || bounds.left >= it.right) }
                    previous = bounds
                } else node.assertDoesNotExist()
            }
            val hold = compose.onNodeWithTag("holdPreview").fetchSemanticsNode().boundsInRoot
            if (n == 9) {
                compose.onNodeWithTag("earnedFruitCount_CHERRY").assertTextEquals("2")
                Fruit.entries.drop(1).forEach { fruit ->
                    compose.onNodeWithTag("earnedFruitCount_${fruit.name}").assertDoesNotExist()
                }
            }
            if (n>0) {
                val fruits = compose.onNodeWithTag("earnedFruits").fetchSemanticsNode().boundsInRoot
                val score = compose.onNodeWithTag("score").fetchSemanticsNode().boundsInRoot
                assertTrue(fruits.top >= score.bottom)
                assertEquals(score.left, fruits.left, 1f)
                assertEquals(score.right, fruits.right, 1f)
                assertTrue(hold.right <= score.left + 1f)
            }
        }
    }

    /** Odd rows reserve the right cell; badge growth cannot move or resize artwork. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun fruitColumnsAndBadgesStayStableAcrossCountsAndPortraitSizes() {
        var score by mutableIntStateOf(0)
        var width by mutableIntStateOf(240)
        var fontScale by mutableFloatStateOf(1f)
        var theme by mutableStateOf("classic")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalGamePalette provides GamePalettes.find(theme)) {
                Box(Modifier.size(width.dp, 640.dp)) {
                    GameHud(GameState(active = Piece(Tetromino.T), next = Tetromino.O, score = score))
                }
            }
        }
        for (w in listOf(240, 320)) for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { width = w; fontScale = scale }
            for (count in 0..8) {
                compose.runOnIdle { score = count * GameRules.FRUIT_STEP }
                assertFruitColumns(count)
            }
            for (palette in listOf("classic", "solarized_light", "github_light")) {
                compose.runOnIdle { theme = palette; score = GameRules.FRUIT_STEP }
                assertStableFruitBadges { value -> compose.runOnIdle { score = value } }
            }
        }
    }

    private fun assertFruitColumns(count: Int) {
        if (count == 0) {
            compose.onNodeWithTag("earnedFruits").assertDoesNotExist()
            return
        }
        val first = compose.onNodeWithTag("earnedFruitIcon_CHERRY").fetchSemanticsNode().boundsInRoot
        for (index in 0 until count) {
            val fruit = Fruit.entries[index]
            val icon = compose.onNodeWithTag("earnedFruitIcon_${fruit.name}")
                .fetchSemanticsNode().boundsInRoot
            if (index % 2 == 0) assertEquals(first.left, icon.left, 1f)
            else assertTrue(icon.left > first.right)
        }
    }

    private fun assertStableFruitBadges(updateScore: (Int) -> Unit) {
        var previousIcon: androidx.compose.ui.geometry.Rect? = null
        var previousDigits = 0
        for (quantity in listOf(1, 2, 3, 4, 9, 10, 13)) {
            val value = ((quantity - 1) * 8 + 1) * GameRules.FRUIT_STEP
            updateScore(value)
            val icon = compose.onNodeWithTag("earnedFruitIcon_CHERRY").fetchSemanticsNode().boundsInRoot
            // Score digit growth can widen the existing score lane; badge growth cannot.
            val digits = value.toString().length
            if (digits == previousDigits) assertEquals(previousIcon, icon)
            previousIcon = icon
            previousDigits = digits
            val badge = compose.onNodeWithTag("earnedFruitCount_CHERRY")
            if (quantity == 1) badge.assertDoesNotExist()
            else {
                badge.assertTextEquals(quantity.toString())
                    .assertContentDescriptionEquals("${Fruit.CHERRY.title}: $quantity")
                val cell = compose.onNodeWithTag("earnedFruit_CHERRY").fetchSemanticsNode().boundsInRoot
                val bounds = badge.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= cell.left && bounds.right <= cell.right + 1f)
                assertTrue(bounds.top >= cell.top && bounds.bottom <= cell.bottom + 1f)
            }
        }
    }

    /** MainActivity forwards real popup window focus; playback must survive that transfer. */
    @Test fun realMusicDropdownKeepsPreparedMenuPlaybackAndPosition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val model = realSettings(scenario)
            val track = checkNotNull(menuTrack(model))
            val audit = auditPlayback(model)
            val selection = model.musicSelection
            val playlist = field(audit.real, "playlist") as PlaylistClock
            val occurrence = playlist.occurrence
            for (dismissal in listOf("outside", "back", "same")) {
                val position = track.playbackHeadPosition
                audit.modes.clear()
                openRealMusicDropdown()
                var focused = true
                scenario.onActivity { focused = it.hasWindowFocus() }
                Log.i("MusicDropdownAudit", "dismissal=$dismissal activityFocused=$focused " +
                    "trackState=${track.playState} position=$position")
                assertFalse("Must exercise the native popup window", focused)
                assertContinuousPlayback(model, track, audit, position)
                when (dismissal) {
                    "outside" -> tapOutsideNativeMusicPopup(scenario)
                    "back" -> Espresso.pressBack()
                    else -> compose.onNodeWithTag("music_${selection.id}").performScrollTo().performClick()
                }
                compose.onNodeWithTag("music_sugar_plum").assertDoesNotExist()
                assertContinuousPlayback(model, track, audit, position)
                assertEquals(selection, model.musicSelection)
                assertEquals(occurrence, playlist.occurrence)
                assertTrue(audit.selections.isEmpty())
            }
        }
    }

    /** Uses a real Activity lifecycle and real decoded AudioTrack while the popup owns focus. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 28)
    @Test fun realMusicDropdownPreservesSelectionOffAndSystemGates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val model = realSettings(scenario)
            val audit = auditPlayback(model)
            val track = checkNotNull(menuTrack(model))
            openRealMusicDropdown()
            compose.onNodeWithTag("music_sugar_plum").performClick()
            assertEquals(listOf(MusicSelection.Track(Song.SUGAR_PLUM)), audit.selections.toList())
            openRealMusicDropdown()
            compose.onNodeWithTag("music_off").performScrollTo().performClick()
            assertEquals(MusicSelection.Off, model.musicSelection)
            assertEquals(2, audit.selections.size)
            assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
            openRealMusicDropdown()
            assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
            Espresso.pressBack()
            compose.onNodeWithTag("music_sugar_plum").assertDoesNotExist()
            assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
            assertEquals(MusicSelection.Off,
                GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false).musicSelection)
            openRealMusicDropdown()
            compose.onNodeWithTag("music_shuffle").performScrollTo().performClick()
            compose.waitUntil(5000) { track.playState == AudioTrack.PLAYSTATE_PLAYING }
            openRealMusicDropdown()
            scenario.moveToState(Lifecycle.State.CREATED)
            assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
            assertTrue(MusicMode.SILENT in audit.modes)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10000) { track.playState == AudioTrack.PLAYSTATE_PLAYING }
            compose.onNodeWithTag("music_sugar_plum").assertIsDisplayed()
            verifyScreenLock(track)
            verifyNativeMusicFocusCallbacks(scenario, audit.real, track)
        }
    }

    @androidx.annotation.RequiresApi(28)
    private fun verifyScreenLock(track: AudioTrack) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        assertTrue(automation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
        try {
            compose.waitUntil(10000) { track.playState == AudioTrack.PLAYSTATE_PAUSED }
        } finally {
            assertTrue(automation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        }
        compose.waitUntil(10000) { track.playState == AudioTrack.PLAYSTATE_PLAYING }
        try {
            assertTrue(automation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
            compose.waitUntil(10000) { track.playState == AudioTrack.PLAYSTATE_PAUSED }
        } finally {
            for (command in listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard")) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                    .use { it.readBytes() }
            }
        }
        compose.waitUntil(10000) { track.playState == AudioTrack.PLAYSTATE_PLAYING }
    }

    private fun verifyNativeMusicFocusCallbacks(scenario: ActivityScenario<MainActivity>, music: GameMusic,
        track: AudioTrack) {
        scenario.onActivity {
            val listener = field(music, "listener") as AudioManager.OnAudioFocusChangeListener
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
            val receiver = field(music, "noisyReceiver") as android.content.BroadcastReceiver
            receiver.onReceive(it, android.content.Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
        }
    }

    private fun realSettings(scenario: ActivityScenario<MainActivity>): GameViewModel {
        lateinit var model: GameViewModel
        scenario.onActivity {
            model = ViewModelProvider(it)[GameViewModel::class.java]
            model.navigation.finishLaunchIntro()
            model.navigation.settings()
        }
        compose.waitUntil(10000) { menuTrack(model)?.playState == AudioTrack.PLAYSTATE_PLAYING }
        return model
    }

    private fun openRealMusicDropdown() {
        compose.onNodeWithTag("musicPicker").performScrollTo().performClick()
        compose.onNodeWithTag("music_sugar_plum").performScrollTo().assertIsDisplayed()
    }

    private fun assertContinuousPlayback(model: GameViewModel, track: AudioTrack, audit: PlaybackAudit, position: Int) {
        assertSame(track, menuTrack(model))
        assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
        assertFalse("No brief silent mode during popup handover", MusicMode.SILENT in audit.modes)
        compose.waitUntil(5000) { track.playbackHeadPosition > position }
    }

    private class PlaybackAudit(val real: GameMusic) : MusicPlayback by real {
        val modes = java.util.concurrent.CopyOnWriteArrayList<MusicMode>()
        val selections = java.util.concurrent.CopyOnWriteArrayList<MusicSelection>()
        override fun setMode(next: MusicMode) { modes += next; real.setMode(next) }
        override fun select(selection: MusicSelection) { selections += selection; real.select(selection) }
    }

    private fun auditPlayback(model: GameViewModel): PlaybackAudit {
        val audit = PlaybackAudit(checkNotNull(realMusic(model)))
        compose.runOnIdle {
            model.javaClass.getDeclaredField("music").apply { isAccessible = true }.set(model, audit)
        }
        return audit
    }

    private fun realMusic(model: GameViewModel): GameMusic? = when (val playback = field(model, "music")) {
        is PlaybackAudit -> playback.real
        is GameMusic -> playback
        else -> null
    }

    private fun menuTrack(model: GameViewModel): AudioTrack? {
        val music = realMusic(model)
        val player = music?.let { field(it, "menuPlayer") }
        return player?.let { field(it, "track") as AudioTrack }
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    /** Captures the real portrait renderer for both approved mockup scenarios. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun approvedFruitHudScenariosExportPortraitEvidence() {
        var state by mutableStateOf(GameState(active = Piece(Tetromino.S, y = 8), next = Tetromino.T,
            held = Tetromino.J, score = 54430))
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), state, { 1000L }, false)
        compose.setContent { GameContent(model, state, 0.dp) }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "fruit-hud-259").apply { mkdirs() }
        for (value in listOf(54430, 254430)) {
            compose.runOnIdle { state = state.copy(score = value) }
            compose.onNodeWithTag("score").assertTextEquals(value.toString())
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(directory, "$value.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    /** Свайп у правого края зоны ввода по-прежнему передвигает фигуру. */
    @Test fun rightEdgeGestureAreaRemainsUsable() {
        val model=GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=10000),{1000L},false)
        compose.setContent { SwypetrisApp(model) {} }
        val initialX=model.game!!.active.x
        val fruit=compose.onNodeWithTag("earnedFruit_CHERRY").fetchSemanticsNode().boundsInRoot
        val area=compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("gameArea").performTouchInput {
            val from=Offset(area.width*.95f,fruit.center.y-area.top)
            swipe(from,from-Offset(area.width*.22f,0f),200)
        }
        compose.runOnIdle { assertTrue(model.game!!.active.x<initialX) }
    }

}

/** Inject outside input through Android's window dispatcher, not a background Compose root. */
private fun tapOutsideNativeMusicPopup(scenario: ActivityScenario<MainActivity>) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val info = automation.serviceInfo
    val flags = info.flags
    info.flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    automation.serviceInfo = info
    try {
        automation.waitForIdle(100, 5000)
        val popupBounds = android.graphics.Rect()
        val focused = checkNotNull(automation.windows.firstOrNull { it.isFocused })
        focused.getBoundsInScreen(popupBounds)
        lateinit var point: android.graphics.Point
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            assertEquals(activity.packageName, focused.root?.packageName?.toString())
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)?.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()) ?: androidx.core.graphics.Insets.NONE
            val safe = android.graphics.Rect(location[0] + insets.left, location[1] + insets.top,
                location[0] + decor.width - insets.right, location[1] + decor.height - insets.bottom)
            point = listOf(android.graphics.Point(safe.left + 1, safe.bottom - 1),
                android.graphics.Point(safe.right - 1, safe.bottom - 1),
                android.graphics.Point(safe.left + 1, safe.top + 1),
                android.graphics.Point(safe.right - 1, safe.top + 1))
                .first { !popupBounds.contains(it.x, it.y) }
        }
        val down = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action,
                point.x.toFloat(), point.y.toFloat(), 0)
            try {
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(automation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
    } finally {
        info.flags = flags
        automation.serviceInfo = info
    }
}
