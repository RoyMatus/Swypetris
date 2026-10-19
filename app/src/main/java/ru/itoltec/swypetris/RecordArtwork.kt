package ru.itoltec.swypetris

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max

private const val RECORD_LAYOUT_ASSET = "record_artwork_layout_v1.json"
private const val BADGE_GAP_DP = 1f
private const val RECT_HEIGHT_INDEX = 3
private const val BADGE_TEXT_HORIZONTAL_PADDING_DP = 6f
private const val BADGE_TEXT_VERTICAL_PADDING_DP = 4f
private const val BADGE_EDGE_RESERVE_DP = 4f
// Two independently rounded offsets and circle sizes can consume part of the ideal gap.
private const val BADGE_RASTER_ROUNDING_PIXELS = 3f

internal data class ArtworkRect(val x: Float, val y: Float, val width: Float, val height: Float)

internal data class RecordFruitArtwork(
    val fruit: Fruit,
    val label: String,
    val rect: ArtworkRect,
    val opacity: Float,
    val zIndex: Float,
    val badgeCenterX: Float,
    val badgeCenterY: Float,
)

internal data class RecordArtworkContract(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val trophyRect: ArtworkRect,
    val trophyOpacity: Float,
    val fruits: List<RecordFruitArtwork>,
    val badgeDiameterDp: Float,
    val badgeFontSp: Float,
    val badgeTextColor: Color,
    val badgeZIndex: Float,
    val artworkFraction: Float,
    val artworkMinWidthDp: Float,
    val artworkMaxWidthDp: Float,
    val minimumTextWidthDp: Float,
    val stackedArtworkWidthDp: Float,
)

@Composable
internal fun rememberRecordArtworkContract(): RecordArtworkContract {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        val json = context.assets.open(RECORD_LAYOUT_ASSET).bufferedReader().use { JSONObject(it.readText()) }
        fun rect(array: JSONArray) = ArtworkRect(array.getDouble(0).toFloat(), array.getDouble(1).toFloat(),
            array.getDouble(2).toFloat(), array.getDouble(RECT_HEIGHT_INDEX).toFloat())
        val canvas = json.getJSONObject("canvas")
        val trophy = json.getJSONObject("trophy")
        val card = json.getJSONObject("card")
        val badge = json.getJSONObject("badge")
        val fruits = json.getJSONArray("fruits")
        val parsedFruits = buildList {
            repeat(fruits.length()) { index ->
                val fruit = fruits.getJSONObject(index)
                val center = fruit.getJSONArray("badgeCenter")
                add(RecordFruitArtwork(Fruit.valueOf(fruit.getString("id")), fruit.getString("label"),
                    rect(fruit.getJSONArray("rect")), fruit.getDouble("opacity").toFloat(),
                    fruit.getInt("zIndex").toFloat(), center.getDouble(0).toFloat(), center.getDouble(1).toFloat()))
            }
        }
        val color = android.graphics.Color.parseColor(badge.getString("textColor"))
        RecordArtworkContract(canvas.getDouble("width").toFloat(), canvas.getDouble("height").toFloat(),
            rect(trophy.getJSONArray("rect")), trophy.getDouble("opacity").toFloat(), parsedFruits,
            badge.getDouble("diameterDp").toFloat(), badge.getDouble("fontSp").toFloat(), Color(color),
            badge.getDouble("zIndex").toFloat(),
            card.getDouble("artworkFractionOfInnerWidth").toFloat(),
            card.getDouble("artworkMinSideWidthDp").toFloat(), card.getDouble("artworkMaxSideWidthDp").toFloat(),
            card.getDouble("minimumTextColumnDp").toFloat(), card.getDouble("stackedArtworkWidthDp").toFloat())
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RecordFruitRow(quantities: List<Long>, recordId: String, modifier: Modifier = Modifier) {
    val contract = rememberRecordArtworkContract()
    val density = LocalDensity.current.density
    FlowRow(modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        contract.fruits.forEach { artwork ->
            val count = quantities.getOrElse(artwork.fruit.ordinal) { 0L }
            if (count > 0) {
                val badgeDiameter = if (count > 1) rememberBadgeSize(count.toString(), contract, density) * 2f else 0f
                val itemSize = max(28f, badgeDiameter)
                Box(Modifier.size(itemSize.dp).clearAndSetSemantics {
                    contentDescription = if (count == 1L) artwork.label else "${artwork.label}, $count"
                    testTag = "recordFruit-$recordId-${artwork.fruit.ordinal}"
                }) {
                    Image(ImageBitmap.imageResource(artwork.fruit.drawableResource()), contentDescription = null,
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
                        modifier = Modifier.align(Alignment.BottomStart).size(24.dp))
                    if (count > 1) {
                        Box(Modifier.align(Alignment.TopEnd).size(badgeDiameter.dp).clearAndSetSemantics {
                            testTag = "recordFruitBadge-$recordId-${artwork.fruit.ordinal}"
                        },
                            contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.record_quantity_badge), contentDescription = null,
                                contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                            BasicText(count.toString(), style = badgeTextStyle(contract),
                                maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun recordArtworkMinimumWidth(quantities: List<Long>, contract: RecordArtworkContract): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val visibleBadges = contract.fruits.mapNotNull { fruit ->
        val count = quantities.getOrElse(fruit.fruit.ordinal) { 0L }
        if (count <= 1) return@mapNotNull null
        val diameter = measuredBadgeDiameter(count.toString(), contract, measurer, density,
            LocalDensity.current.fontScale)
        fruit to diameter
    }
    var requiredWidth = 0f
    for (first in visibleBadges.indices) {
        val (a, diameterA) = visibleBadges[first]
        val edge = minOf(a.badgeCenterX, contract.canvasWidth - a.badgeCenterX,
            a.badgeCenterY, contract.canvasHeight - a.badgeCenterY) / contract.canvasWidth
        val neededBeyondOuterReserve = diameterA / 2f - BADGE_EDGE_RESERVE_DP
        if (edge > 0f && neededBeyondOuterReserve > 0f) requiredWidth = max(requiredWidth,
            neededBeyondOuterReserve / edge)
        for (second in first + 1 until visibleBadges.size) {
            val (b, diameterB) = visibleBadges[second]
            val normalizedDistance = hypot(a.badgeCenterX - b.badgeCenterX,
                a.badgeCenterY - b.badgeCenterY) / contract.canvasWidth
            if (normalizedDistance > 0f) requiredWidth = max(requiredWidth,
                (diameterA / 2f + diameterB / 2f + BADGE_GAP_DP +
                    BADGE_RASTER_ROUNDING_PIXELS / density) / normalizedDistance)
        }
    }
    return requiredWidth
}

@Composable
internal fun RecordArtwork(
    quantities: List<Long>,
    recordId: String,
    widthDp: Float,
    modifier: Modifier = Modifier,
) {
    val contract = rememberRecordArtworkContract()
    val density = LocalDensity.current.density
    val trophy = contract.trophyRect
    Box(modifier.width(widthDp.dp).aspectRatio(contract.canvasWidth / contract.canvasHeight)
        .testTag("recordArtwork-$recordId")) {
        Image(ImageBitmap.imageResource(R.drawable.record_trophy), contentDescription = null,
            contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
            modifier = Modifier.offset((trophy.x / contract.canvasWidth * widthDp).dp,
                (trophy.y / contract.canvasWidth * widthDp).dp)
                .size((trophy.width / contract.canvasWidth * widthDp).dp,
                    (trophy.height / contract.canvasWidth * widthDp).dp)
                .alpha(contract.trophyOpacity))
        contract.fruits.sortedBy { it.zIndex }.forEach { artwork ->
            val count = quantities.getOrElse(artwork.fruit.ordinal) { 0L }
            if (count > 0) {
                Box(Modifier.offset((artwork.rect.x / contract.canvasWidth * widthDp).dp,
                    (artwork.rect.y / contract.canvasWidth * widthDp).dp)
                    .size((artwork.rect.width / contract.canvasWidth * widthDp).dp,
                        (artwork.rect.height / contract.canvasWidth * widthDp).dp)
                    .alpha(artwork.opacity).zIndex(artwork.zIndex)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (count == 1L) artwork.label else "${artwork.label}, $count"
                    }.testTag("recordFruit-$recordId-${artwork.fruit.ordinal}")) {
                    Image(ImageBitmap.imageResource(artwork.fruit.drawableResource()), contentDescription = null,
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
                        modifier = Modifier.matchParentSize())
                }
            }
        }
        contract.fruits.forEach { artwork ->
            val count = quantities.getOrElse(artwork.fruit.ordinal) { 0L }
            if (count > 1) {
                val text = count.toString()
                val measured = rememberBadgeSize(text, contract, density)
                val centerX = artwork.badgeCenterX / contract.canvasWidth * widthDp
                val centerY = artwork.badgeCenterY / contract.canvasWidth * widthDp
                Box(Modifier.offset((centerX - measured).dp, (centerY - measured).dp)
                    .size((measured * 2).dp).zIndex(contract.badgeZIndex)
                    .clearAndSetSemantics {
                        testTag = "recordFruitBadge-$recordId-${artwork.fruit.ordinal}"
                    },
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.record_quantity_badge), contentDescription = null,
                        contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                    BasicText(text, style = badgeTextStyle(contract), maxLines = 1, softWrap = false,
                        modifier = Modifier.testTag("recordBadgeText-$recordId-${artwork.fruit.ordinal}"))
                }
            }
        }
    }
}

@Composable
private fun rememberBadgeSize(text: String, contract: RecordArtworkContract, density: Float): Float =
    measuredBadgeDiameter(text, contract, rememberTextMeasurer(), density, LocalDensity.current.fontScale) / 2f

private fun badgeTextStyle(contract: RecordArtworkContract) = TextStyle(
    color = contract.badgeTextColor, fontSize = contract.badgeFontSp.sp, fontWeight = FontWeight.SemiBold,
    platformStyle = PlatformTextStyle(includeFontPadding = false))

private fun measuredBadgeDiameter(
    text: String,
    contract: RecordArtworkContract,
    measurer: TextMeasurer,
    density: Float,
    fontScale: Float,
): Float {
    val measured = measurer.measure(text, style = badgeTextStyle(contract), maxLines = 1, softWrap = false)
    val width = measured.size.width / density
    val height = measured.size.height / density
    if (fontScale <= 1f && width <= contract.badgeDiameterDp && height <= contract.badgeDiameterDp) {
        return contract.badgeDiameterDp
    }
    return max(contract.badgeDiameterDp, max(width + BADGE_TEXT_HORIZONTAL_PADDING_DP,
        height + BADGE_TEXT_VERTICAL_PADDING_DP))
}

private fun Fruit.drawableResource(): Int = when (this) {
    Fruit.CHERRY -> R.drawable.record_fruit_cherry
    Fruit.BANANA -> R.drawable.record_fruit_banana
    Fruit.GRAPES -> R.drawable.record_fruit_grapes
    Fruit.STRAWBERRY -> R.drawable.record_fruit_strawberry
    Fruit.APPLE -> R.drawable.record_fruit_apple
    Fruit.PEAR -> R.drawable.record_fruit_pear
    Fruit.PINEAPPLE -> R.drawable.record_fruit_pineapple
    Fruit.WATERMELON -> R.drawable.record_fruit_watermelon
}
