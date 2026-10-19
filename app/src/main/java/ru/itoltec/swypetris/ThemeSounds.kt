package ru.itoltec.swypetris

import androidx.annotation.RawRes

private const val MAX_EFFECT_VOLUME = .55f

/** Stable palette IDs share one resource mapping; Classic retains its original recordings. */
internal object ThemeSounds {
    private val classic = ThemeSoundPair(R.raw.drop_soft, R.raw.clear_soft, .4294f, .1699f)
    private val pairs = mapOf(
        "monokai" to ThemeSoundPair(R.raw.monokai_drop, R.raw.monokai_clear, .5103f, .55f),
        "gruvbox" to ThemeSoundPair(R.raw.gruvbox_drop, R.raw.gruvbox_clear, .55f, .5103f),
        "vscode" to ThemeSoundPair(R.raw.vscode_drop, R.raw.vscode_clear, .5222f, .5313f),
        "dracula" to ThemeSoundPair(R.raw.dracula_drop, R.raw.dracula_clear),
        "nord" to ThemeSoundPair(R.raw.nord_drop, R.raw.nord_clear, .5222f, .55f),
        "solarized_light" to ThemeSoundPair(R.raw.solarized_light_drop, R.raw.solarized_light_clear, .5103f, .4901f),
        "solarized_dark" to ThemeSoundPair(R.raw.solarized_dark_drop, R.raw.solarized_dark_clear, .55f, .5468f),
        "github_light" to ThemeSoundPair(R.raw.github_light_drop, R.raw.github_light_clear),
        "tokyo_night" to ThemeSoundPair(R.raw.tokyo_night_drop, R.raw.tokyo_night_clear, .55f, .5103f),
        "catppuccin" to ThemeSoundPair(R.raw.catppuccin_drop, R.raw.catppuccin_clear, .55f, .4901f),
        "synthwave_84" to ThemeSoundPair(R.raw.synthwave_84_drop, R.raw.synthwave_84_clear, .4681f, .422f)
    )

    fun forPalette(id: String): ThemeSoundPair = pairs[id] ?: classic
}

internal data class ThemeSoundPair(@param:RawRes val drop: Int, @param:RawRes val clear: Int,
    private val dropVolume: Float = MAX_EFFECT_VOLUME, private val clearVolume: Float = MAX_EFFECT_VOLUME) {
    init {
        require(dropVolume.isFinite() && dropVolume > 0f && dropVolume <= MAX_EFFECT_VOLUME)
        require(clearVolume.isFinite() && clearVolume > 0f && clearVolume <= MAX_EFFECT_VOLUME)
    }

    fun volume(event: FeedbackEvent): Float = if (event == FeedbackEvent.DROP) dropVolume else clearVolume
}
