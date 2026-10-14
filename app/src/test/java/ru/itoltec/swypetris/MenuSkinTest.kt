package ru.itoltec.swypetris

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MenuSkinTest {
    @Test fun menuBindingRetainsOriginalPieceColorsAndMaterialsForEveryTheme() {
        val roles = listOf("primary", "resume", "settings", "help", "results", "contacts",
            "destructive", "back", "link").associateWith { Color.White }
        GamePalettes.all.forEach { original ->
            val skin = MenuSkin(original.id, !original.light, Color.Black, Color.Gray,
                Color.White, Color.LightGray, Color.White, Color.Cyan, Color.Black,
                Color.Blue, Color.White, Color.White, Color.Black, roles,
                12f, 2f, .46f, .28f, 2f, .12f, .14f)
            val bound = skin.palette(original)
            assertEquals(original.id, bound.id)
            assertEquals(original.title, bound.title)
            assertSame(original.pieces, bound.pieces)
            assertEquals(original.finish, bound.finish)
            assertEquals(original.texture, bound.texture)
            assertEquals(original.grid, bound.grid)
            Tetromino.entries.forEach { assertEquals(original.piece(it), bound.piece(it)) }
            assertEquals(skin.background, bound.background)
            assertEquals(skin.text, bound.text)
            assertEquals(skin.light, bound.light)
        }
    }
}
