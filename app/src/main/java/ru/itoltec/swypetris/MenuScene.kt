package ru.itoltec.swypetris

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject

internal const val MENU_SCENE_WIDTH = 941f
internal const val MENU_SCENE_HEIGHT = 1672f
private const val MENU_HEADER_TOP = 40f
private const val MENU_HEADER_BOTTOM = 660f
private const val MENU_ALPHA_MAX = 255
private const val MENU_ALPHA_THRESHOLD = .0001f
private const val MENU_BEVEL_ALPHA = 0x99
private const val MENU_BEVEL_COLOR = 0x99ffffff
private const val MENU_TRANSPARENT_RGB_MASK = 0x00ffffff
private const val FRAME_PROGRESS = 0
private const val FRAME_X = 1
private const val FRAME_Y = 2
private const val FRAME_SCALE_X = 3
private const val FRAME_SCALE_Y = 4
private const val FRAME_ROTATION = 5
private const val FRAME_ALPHA = 6
private const val FRAME_ATLAS = 7
private const val ATLAS_LEFT = 0
private const val ATLAS_TOP = 1
private const val ATLAS_WIDTH = 2
private const val ATLAS_HEIGHT = 3
private const val ANCHOR_X = 0
private const val ANCHOR_Y = 1

private val menuSceneResources = mapOf(
    "classic" to (R.drawable.menu_scene_classic to R.raw.menu_motion_classic),
    "monokai" to (R.drawable.menu_scene_monokai to R.raw.menu_motion_monokai),
    "gruvbox" to (R.drawable.menu_scene_gruvbox to R.raw.menu_motion_gruvbox),
    "vscode" to (R.drawable.menu_scene_vscode to R.raw.menu_motion_vscode),
    "dracula" to (R.drawable.menu_scene_dracula to R.raw.menu_motion_dracula),
    "nord" to (R.drawable.menu_scene_nord to R.raw.menu_motion_nord),
    "solarized_light" to (R.drawable.menu_scene_solarized_light to R.raw.menu_motion_solarized_light),
    "solarized_dark" to (R.drawable.menu_scene_solarized_dark to R.raw.menu_motion_solarized_dark),
    "github_light" to (R.drawable.menu_scene_github_light to R.raw.menu_motion_github_light),
    "tokyo_night" to (R.drawable.menu_scene_tokyo_night to R.raw.menu_motion_tokyo_night),
    "catppuccin" to (R.drawable.menu_scene_catppuccin to R.raw.menu_motion_catppuccin),
    "synthwave_84" to (R.drawable.menu_scene_synthwave_84 to R.raw.menu_motion_synthwave_84))
private val menuSpriteResources = mapOf("bat" to R.drawable.menu_sprite_bat,
    "cat" to R.drawable.menu_sprite_cat, "leaf" to R.drawable.menu_sprite_leaf,
    "cloud" to R.drawable.menu_sprite_cloud)

/** Retains only small parsed data, never decoded plates from previous selections. */
private object MenuSceneData {
    private val themes = mutableMapOf<String, List<MenuMotionTrack>>()
    private var sprites: Map<String, List<MenuSpriteFrame>>? = null

    @Synchronized fun tracks(resources: Resources, id: String): List<MenuMotionTrack> =
        themes.getOrPut(id) {
            val json = resources.readMenuJson(menuSceneResources.getValue(id).second)
            require(json.getInt("schema") == 1 && json.getString("theme") == id)
            require(json.getInt("width") == MENU_SCENE_WIDTH.toInt() &&
                json.getInt("height") == MENU_SCENE_HEIGHT.toInt() && json.getDouble("staticTime") == 0.0)
            json.getJSONArray("tracks").objects().map(::parseMenuTrack)
        }

    @Synchronized fun registration(resources: Resources): Map<String, List<MenuSpriteFrame>> {
        sprites?.let { return it }
        val json = resources.readMenuJson(R.raw.menu_sprite_registration)
        require(json.getInt("schema") == 1)
        val definitions = json.getJSONObject("sprites")
        return menuSpriteResources.keys.associateWith { name ->
            definitions.getJSONObject(name).getJSONArray("frames").objects().map {
                val rect = it.getJSONArray("rect")
                val anchor = it.getJSONArray("anchor")
                MenuSpriteFrame(Rect(rect.getInt(ATLAS_LEFT), rect.getInt(ATLAS_TOP),
                    rect.getInt(ATLAS_LEFT) + rect.getInt(ATLAS_WIDTH),
                    rect.getInt(ATLAS_TOP) + rect.getInt(ATLAS_HEIGHT)),
                    anchor.getDouble(ANCHOR_X).toFloat(), anchor.getDouble(ANCHOR_Y).toFloat())
            }
        }.also { sprites = it }
    }
}

private fun Resources.readMenuJson(id: Int): JSONObject = openRawResource(id).bufferedReader().use {
    JSONObject(it.readText())
}
private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
private fun JSONObject.optionalFloat(name: String): Float? =
    if (has(name)) getDouble(name).toFloat() else null

private fun parseMenuTrack(json: JSONObject): MenuMotionTrack {
    val frames = json.getJSONArray("frames")
    val events = json.optJSONArray("frameEvents")
    return MenuMotionTrack(json.getString("name"), json.getString("kind"),
        Color.parseColor(json.getString("color")).toLong(), json.getDouble("width").toFloat(),
        json.getDouble("height").toFloat(), json.getDouble("period"), json.getDouble("phase"),
        List(frames.length()) { i -> frames.getJSONArray(i).let { frame ->
            MenuMotionFrame(frame.getDouble(FRAME_PROGRESS), frame.getDouble(FRAME_X).toFloat(),
                frame.getDouble(FRAME_Y).toFloat(), frame.getDouble(FRAME_SCALE_X).toFloat(),
                frame.getDouble(FRAME_SCALE_Y).toFloat(), frame.getDouble(FRAME_ROTATION).toFloat(),
                frame.getDouble(FRAME_ALPHA).toFloat(), frame.getInt(FRAME_ATLAS))
        } }, json.optionalFloat("stroke"), json.optBoolean("glow"), json.optBoolean("bevel"),
        if (json.has("sprite")) json.getString("sprite") else null, json.optBoolean("tint"),
        if (json.has("framePeriod")) json.getDouble("framePeriod") else null,
        events?.objects()?.map { MenuFrameEvent(it.getDouble("period"), it.getDouble("start"),
            it.getDouble("duration"), it.getInt("index")) }.orEmpty())
}

internal data class MenuSpriteFrame(val source: Rect, val anchorX: Float, val anchorY: Float)

internal fun loadMenuPlate(resources: Resources, id: String): Bitmap =
    requireNotNull(BitmapFactory.decodeResource(resources, menuSceneResources.getValue(id).first))

internal fun loadMenuScene(resources: Resources, id: String): MenuSceneArtwork {
    val tracks = MenuSceneData.tracks(resources, id)
    val registration = MenuSceneData.registration(resources)
    val sprites = tracks.mapNotNull { it.sprite }.distinct().associateWith {
        requireNotNull(BitmapFactory.decodeResource(resources, menuSpriteResources.getValue(it)))
    }
    val tinted = mutableMapOf<Pair<String, Long>, Bitmap>()
    val prepared = tracks.map { track ->
        val sprite = track.sprite?.let { name ->
            if (track.tint) tinted.getOrPut(name to track.color) {
                tintMenuSprite(sprites.getValue(name), track.color.toInt())
            } else sprites.getValue(name)
        }
        MenuTrackArtwork(track, sprite, track.sprite?.let(registration::getValue).orEmpty())
    }
    return MenuSceneArtwork(loadMenuPlate(resources, id), prepared)
}

private fun tintMenuSprite(source: Bitmap, color: Int): Bitmap =
    Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        canvas.drawBitmap(source, null, bounds, null)
        val paint = Paint().apply {
            this.color = color
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), paint)
    }

internal class MenuSceneArtwork(private val plate: Bitmap, internal val tracks: List<MenuTrackArtwork>) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val plateBounds = RectF(0f, 0f, MENU_SCENE_WIDTH, MENU_SCENE_HEIGHT)
    /** Actual allocation of uniquely retained decoded images; diagnostic access outside drawing. */
    internal val decodedBitmapBytes: Long get() {
        val images = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Bitmap, Boolean>())
        images.add(plate)
        tracks.forEach { artwork -> artwork.decodedBitmap?.let { images.add(it) } }
        return images.sumOf { it.allocationByteCount.toLong() }
    }
    fun draw(canvas: Canvas, width: Float, height: Float, time: Double) {
        val checkpoint = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        val scale = maxOf(width / MENU_SCENE_WIDTH, height / MENU_SCENE_HEIGHT)
        canvas.translate((width - MENU_SCENE_WIDTH * scale) / 2f, (height - MENU_SCENE_HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(plate, null, plateBounds, paint)
        tracks.forEach { it.draw(canvas, time) }
        canvas.restoreToCount(checkpoint)
    }
}

internal class MenuTrackArtwork(internal val track: MenuMotionTrack, private val sprite: Bitmap?,
    internal val spriteFrames: List<MenuSpriteFrame>) {
    internal val decodedBitmap: Bitmap? get() = sprite
    private val bounds = RectF(-track.width / 2f, -track.height / 2f, track.width / 2f, track.height / 2f)
    private val destination = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = track.color.toInt()
        strokeWidth = track.stroke ?: 1f
        style = if (track.stroke != null) Paint.Style.STROKE else Paint.Style.FILL
        if (track.glow) shader = RadialGradient(0f, 0f, maxOf(track.width, track.height) / 2f,
            track.color.toInt(), track.color.toInt() and MENU_TRANSPARENT_RGB_MASK, Shader.TileMode.CLAMP)
    }
    private val glowClip = if (track.glow) Path().apply { addOval(bounds, Path.Direction.CW) } else null
    private val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MENU_BEVEL_COLOR.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val bevelBounds = RectF(bounds.left + 1f, bounds.top + 1f, bounds.right - 1f, bounds.bottom - 1f)

    fun draw(canvas: Canvas, time: Double) {
        val pose = sampleMenuMotion(track, time)
        if (pose.alpha <= MENU_ALPHA_THRESHOLD) return
        val checkpoint = canvas.save()
        canvas.translate(pose.x, pose.y)
        canvas.rotate(pose.rotation)
        canvas.scale(pose.scaleX, pose.scaleY)
        paint.alpha = (pose.alpha * MENU_ALPHA_MAX).toInt()
        when (track.kind) {
            "sprite" -> drawSprite(canvas, pose.index)
            "line" -> canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint)
            "rect" -> {
                canvas.drawRect(bounds, paint)
                if (track.bevel && track.stroke == null) {
                    bevel.alpha = (pose.alpha * MENU_BEVEL_ALPHA).toInt()
                    canvas.drawRect(bevelBounds, bevel)
                }
            }
            else -> {
                glowClip?.let { canvas.clipPath(it) }
                canvas.drawOval(bounds, paint)
            }
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun drawSprite(canvas: Canvas, index: Int) {
        val frame = spriteFrames[index.coerceIn(0, spriteFrames.lastIndex)]
        val left = -frame.anchorX / frame.source.width() * track.width
        val top = -frame.anchorY / frame.source.height() * track.height
        destination.set(left, top, left + track.width, top + track.height)
        canvas.drawBitmap(requireNotNull(sprite), frame.source, destination, paint)
    }
}

internal class MenuHeaderArtwork(private val plate: Bitmap) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val plateBounds = RectF(0f, 0f, plate.width.toFloat(), plate.height.toFloat())
    fun draw(canvas: Canvas, width: Float, height: Float) {
        val checkpoint = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        val sourceHeight = MENU_HEADER_BOTTOM - MENU_HEADER_TOP
        val scale = maxOf(width / MENU_SCENE_WIDTH, height / sourceHeight)
        canvas.translate((width - MENU_SCENE_WIDTH * scale) / 2f,
            (height - sourceHeight * scale) / 2f - MENU_HEADER_TOP * scale)
        canvas.scale(scale * MENU_SCENE_WIDTH / plate.width, scale * MENU_SCENE_HEIGHT / plate.height)
        canvas.drawBitmap(plate, null, plateBounds, paint)
        canvas.restoreToCount(checkpoint)
    }
}
