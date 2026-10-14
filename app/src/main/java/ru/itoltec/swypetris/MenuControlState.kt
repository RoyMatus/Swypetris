package ru.itoltec.swypetris

internal enum class MenuControlState { NORMAL, SELECTED, FOCUSED, PRESSED, DISABLED }

internal fun menuControlState(enabled: Boolean, pressed: Boolean, focused: Boolean,
    selected: Boolean): MenuControlState = when {
    !enabled -> MenuControlState.DISABLED
    pressed -> MenuControlState.PRESSED
    focused -> MenuControlState.FOCUSED
    selected -> MenuControlState.SELECTED
    else -> MenuControlState.NORMAL
}

internal fun usesMenuSkin(screen: GameScreen, cropPreview: Boolean): Boolean = !cropPreview && when (screen) {
    GameScreen.MENU, GameScreen.SETTINGS, GameScreen.HELP, GameScreen.CONTACTS,
    GameScreen.PRIVACY, GameScreen.LEGAL, GameScreen.RESULTS -> true
    else -> false
}
