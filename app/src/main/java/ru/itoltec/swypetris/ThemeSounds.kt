package ru.itoltec.swypetris

import androidx.annotation.RawRes

/** Stable palette IDs share one resource mapping; Classic retains its original recordings. */
internal object ThemeSounds {
    private val classic = ThemeSoundPair(R.raw.drop_soft, R.raw.clear_soft)
    private val pairs = mapOf(
        "monokai" to ThemeSoundPair(R.raw.monokai_drop, R.raw.monokai_clear),
        "gruvbox" to ThemeSoundPair(R.raw.gruvbox_drop, R.raw.gruvbox_clear),
        "vscode" to ThemeSoundPair(R.raw.vscode_drop, R.raw.vscode_clear),
        "dracula" to ThemeSoundPair(R.raw.dracula_drop, R.raw.dracula_clear),
        "nord" to ThemeSoundPair(R.raw.nord_drop, R.raw.nord_clear),
        "solarized_light" to ThemeSoundPair(R.raw.solarized_light_drop, R.raw.solarized_light_clear),
        "solarized_dark" to ThemeSoundPair(R.raw.solarized_dark_drop, R.raw.solarized_dark_clear),
        "github_light" to ThemeSoundPair(R.raw.github_light_drop, R.raw.github_light_clear),
        "tokyo_night" to ThemeSoundPair(R.raw.tokyo_night_drop, R.raw.tokyo_night_clear),
        "catppuccin" to ThemeSoundPair(R.raw.catppuccin_drop, R.raw.catppuccin_clear),
        "synthwave_84" to ThemeSoundPair(R.raw.synthwave_84_drop, R.raw.synthwave_84_clear)
    )

    fun forPalette(id: String): ThemeSoundPair = pairs[id] ?: classic
}

internal data class ThemeSoundPair(@param:RawRes val drop: Int, @param:RawRes val clear: Int)
