package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Проверяет офлайн-политику и экспортирует изображения магазина из настоящего интерфейса. */
class PublicationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    /** Политика читается без сети, а оба способа возврата возвращают к контактам. */
    @Test fun privacyIsOfflineAndPreservesGame() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=1234), { 1000L }, false)
        val game = model.game
        model.contacts()
        compose.setContent { SwypetrisApp(model) {} }
        compose.onNodeWithTag("contactsPage").performScrollToNode(hasTestTag("privacy"))
        compose.onNodeWithTag("privacy").performClick()
        compose.onNodeWithTag("privacyPage").assertIsDisplayed()
        compose.onNodeWithText("Политика конфиденциальности Swypetris").assertIsDisplayed()
        compose.onNodeWithTag("privacyPage").performScrollToNode(hasText("Системная", substring=true).or(hasText("Android может", substring=true)))
        Espresso.pressBack()
        compose.onNodeWithTag("contactsPage").assertIsDisplayed()
        compose.runOnIdle { model.privacy() }
        compose.onNodeWithTag("privacyBack").performClick()
        compose.runOnIdle { assertEquals(game, model.game); assertEquals(GameScreen.CONTACTS,model.screen); assertTrue(model.results.isEmpty()) }
    }

    /** Скриншоты не используют сохранения телефона; демонстрационная партия никогда не попадает в историю. */
    @Test fun exportStoreMedia() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val heights = listOf(5,4,3,4,2,0,0,2,4,3)
        val board = List(20) { y -> List(10) { x ->
            if (y >= 20 - heights[x]) Tetromino.entries[(x / 2 + y / 2) % 7] else null
        } }
        val model = GameViewModel(app, GameState(board=board,active=Piece(Tetromino.T,x=4,y=6),next=Tetromino.L,score=34620,lines=28), { 1000L }, false)
        model.setHints(true); model.menu()
        compose.setContent { SwypetrisApp(model) {} }
        compose.onNodeWithTag("newGame").assertIsDisplayed()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "01-menu.png")
        compose.runOnIdle { model.resume() }
        compose.onNodeWithTag("board").assertIsDisplayed()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "02-game-classic.png")
        compose.runOnIdle { model.settings() }
        compose.onNodeWithTag("musicPicker").assertIsDisplayed()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "03-settings.png")
        compose.runOnIdle { model.setPalette("github_light"); model.resume() }
        compose.onNodeWithTag("board").assertIsDisplayed()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "04-game-light.png")
        assertTrue(model.results.isEmpty())

        val icon = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        val iconCanvas = Canvas(icon)
        iconCanvas.drawColor(android.graphics.Color.rgb(11,16,32))
        app.getDrawable(R.drawable.swypetris_icon_foreground)!!.apply { setBounds(0,0,512,512); draw(iconCanvas) }
        save(icon,"icon-512.png")
        val banner = Bitmap.createBitmap(1024,500,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(banner)
        canvas.drawColor(android.graphics.Color.rgb(11,16,32))
        val logo = android.graphics.BitmapFactory.decodeResource(app.resources,R.drawable.swypetris_logo)
        canvas.drawBitmap(logo,null,Rect(50,25,650,425),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE; textSize=31f; typeface=android.graphics.Typeface.create("sans-serif-medium",0) }
        canvas.drawText("ИГРАЙТЕ ЖЕСТАМИ",655f,195f,paint)
        paint.textSize=25f; paint.color=android.graphics.Color.rgb(112,222,239)
        canvas.drawText("Ретро-музыка",655f,247f,paint)
        canvas.drawText("10 расцветок",655f,290f,paint)
        canvas.drawText("Без рекламы и сети",655f,333f,paint)
        save(banner,"feature-1024x500.png")
    }

    /** Экспортирует результат штатного Android-рендерера в каталог, доступный через adb. */
    private fun save(bitmap: Bitmap,name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null),"publication").apply { mkdirs() }
        File(directory,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
