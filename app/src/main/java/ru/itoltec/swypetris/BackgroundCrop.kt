package ru.itoltec.swypetris

import kotlin.math.max

internal const val MAX_BACKGROUND_ZOOM = 5f
private const val IMAGE_CENTER = .5f

/** Normalized center survives screen-size changes; zoom is relative to aspect-preserving cover. */
internal data class BackgroundCrop(val zoom: Float = 1f, val centerX: Float = .5f, val centerY: Float = .5f) {
    fun normalized(): BackgroundCrop = BackgroundCrop(
        if (zoom.isFinite()) zoom.coerceIn(1f, MAX_BACKGROUND_ZOOM) else 1f,
        if (centerX.isFinite()) centerX.coerceIn(0f, 1f) else IMAGE_CENTER,
        if (centerY.isFinite()) centerY.coerceIn(0f, 1f) else IMAGE_CENTER)
}

/** Bitmap destination fills the real viewport without stretching or revealing empty edges. */
internal data class BackgroundPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

internal data class BackgroundTransform(val focusX: Float, val focusY: Float,
    val panX: Float, val panY: Float, val zoomChange: Float)

internal fun backgroundPlacement(imageWidth: Float, imageHeight: Float, width: Float, height: Float,
    crop: BackgroundCrop): BackgroundPlacement {
    if (minOf(imageWidth, imageHeight, width, height) <= 0f)
        return BackgroundPlacement(0f, 0f, 0f, 0f)
    val normalized = crop.normalized()
    val scale = max(width / imageWidth, height / imageHeight) * normalized.zoom
    val drawnWidth = imageWidth * scale
    val drawnHeight = imageHeight * scale
    return BackgroundPlacement((width / 2f - normalized.centerX * drawnWidth)
        .coerceIn(minOf(width - drawnWidth, 0f), 0f),
        (height / 2f - normalized.centerY * drawnHeight).coerceIn(minOf(height - drawnHeight, 0f), 0f),
        drawnWidth, drawnHeight)
}

/** Keep the image point under the pinch centroid fixed, then apply drag and clamp to the field. */
internal fun transformBackground(crop: BackgroundCrop, imageWidth: Float, imageHeight: Float,
    width: Float, height: Float, gesture: BackgroundTransform): BackgroundCrop {
    val before = backgroundPlacement(imageWidth, imageHeight, width, height, crop)
    if (before.width <= 0f || before.height <= 0f) return crop.normalized()
    val normalized = crop.normalized()
    val next = normalized.copy(zoom = (normalized.zoom * gesture.zoomChange).coerceIn(1f, MAX_BACKGROUND_ZOOM))
    val after = backgroundPlacement(imageWidth, imageHeight, width, height, next)
    val left = (gesture.focusX - (gesture.focusX - before.left) * after.width / before.width + gesture.panX)
        .coerceIn(minOf(width - after.width, 0f), 0f)
    val top = (gesture.focusY - (gesture.focusY - before.top) * after.height / before.height + gesture.panY)
        .coerceIn(minOf(height - after.height, 0f), 0f)
    return next.copy(centerX = (width / 2f - left) / after.width,
        centerY = (height / 2f - top) / after.height).normalized()
}
