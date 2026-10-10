package ru.itoltec.swypetris

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val REGULAR_HEIGHT_DP = 700
private const val COMPACT_ARTWORK_TOP = .07f
private const val COMPACT_ARTWORK_HEIGHT = .34f
private const val COMPACT_CONTENT_TOP = .42f
private const val REGULAR_ARTWORK_TOP = .115f
private const val REGULAR_ARTWORK_HEIGHT = .39f
private const val REGULAR_CONTENT_TOP = .50f

/** One geometry owner keeps the fixed foreground above content on short portrait viewports. */
internal data class VictoryLayout(val artworkTop: Float, val artworkHeight: Float,
    val contentTop: Float, val compact: Boolean) {
    companion object {
        fun forHeight(height: Dp): VictoryLayout = if (height < REGULAR_HEIGHT_DP.dp)
            VictoryLayout(COMPACT_ARTWORK_TOP, COMPACT_ARTWORK_HEIGHT, COMPACT_CONTENT_TOP, true)
        else VictoryLayout(REGULAR_ARTWORK_TOP, REGULAR_ARTWORK_HEIGHT, REGULAR_CONTENT_TOP, false)
    }
}
