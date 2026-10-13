package ru.itoltec.swypetris

import android.app.Application
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Optional real-time audio export workload; ordinary correctness tests do not wait for clips. */
internal object ThemeSoundAudition {
    fun recordIfRequested(feedback: AndroidGameFeedback) {
        if (InstrumentationRegistry.getArguments().getString("themeSoundCapture") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val markers = File(app.getExternalFilesDir(null), "theme-sound-markers.csv")
        markers.writeText("epochMillis,palette,music,event,repetition\n")
        lateinit var music: GameMusic
        lateinit var model: GameViewModel
        instrumentation.runOnMainSync {
            music = GameMusic(app)
            model = GameViewModel(app, GameState(active = Piece(Tetromino.O), next = Tetromino.T),
                SystemClock::uptimeMillis, false, feedback, music)
            model.options.setSound(true)
            model.options.setVibration(false)
            model.onForeground()
            model.onWindowFocusChanged(true)
        }
        try {
            for (withMusic in listOf(false, true)) for (palette in GamePalettes.all) {
                instrumentation.runOnMainSync {
                    model.options.setPalette(palette.id)
                    model.options.chooseMusic(if (withMusic) MusicSelection.Track(Song.KOROBEINIKI)
                        else MusicSelection.Off)
                }
                SystemClock.sleep(650)
                repeat(3) { repetition ->
                    for (event in FeedbackEvent.entries) {
                        instrumentation.runOnMainSync {
                            replaceFixture(model, event)
                            markers.appendText("${System.currentTimeMillis()},${palette.id},$withMusic," +
                                "$event,$repetition\n")
                            model.input.command(GameCommand.HARD_DROP)
                            check((model.game!!.clearingRows.isNotEmpty()) == (event == FeedbackEvent.CLEAR))
                        }
                        SystemClock.sleep(650)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { model.onBackground(); music.release(); feedback.stop() }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun replaceFixture(model: GameViewModel, event: FeedbackEvent) {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        if (event == FeedbackEvent.CLEAR) for (column in 0..9) {
            if (column !in 4..5) board[BoardGeometry.row(19)][column] = Tetromino.J
        }
        val field = GameViewModel::class.java.getDeclaredField("game\$delegate").apply { isAccessible = true }
        val state = field.get(model) as androidx.compose.runtime.MutableState<GameState?>
        state.value = GameState(board = board, active = Piece(Tetromino.O, y = 18), next = Tetromino.T)
    }
}
