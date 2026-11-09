// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import io.github.yixing233.hyperduo.Prefs
import io.github.yixing233.hyperduo.AnimatedAssetFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Versioned, bounded theme archives. Entry names are never used as paths. */
internal object ThemeArchive {
    private val slots = listOf("wifi", "mobile", "bluetooth", "airplane", "music", "brightness", "volume", "gesture", "battery_warning")
    private const val imageLimit = 16 * 1024 * 1024
    private const val audioLimit = 32 * 1024 * 1024
    private const val totalLimit = 64 * 1024 * 1024
    private val baseTypes = mapOf(
        Prefs.KEY_ARC_STROKE to "Int",
        Prefs.KEY_BOLT_SCALE to "Int",
        Prefs.KEY_CAPSULE_LEFT_DX to "Int",
        Prefs.KEY_CAPSULE_LEFT_DY to "Int",
        Prefs.KEY_CAPSULE_LENGTH to "Int",
        Prefs.KEY_CAPSULE_RIGHT_DX to "Int",
        Prefs.KEY_CAPSULE_RIGHT_DY to "Int",
        Prefs.KEY_CC_BLUETOOTH_PILL to "Boolean",
        Prefs.KEY_CC_ICON_ACTIVE_COLOR to "Int",
        Prefs.KEY_CC_ICON_COLORS_ENABLED to "Boolean",
        Prefs.KEY_CC_ICON_INACTIVE_COLOR to "Int",
        Prefs.KEY_CC_IMAGES_ENABLED to "Boolean",
        Prefs.KEY_CC_IMAGE_BRIGHTNESS to "Int",
        Prefs.KEY_CC_TEXT_ACTIVE_COLOR to "Int",
        Prefs.KEY_CC_TEXT_COLORS_ENABLED to "Boolean",
        Prefs.KEY_CC_TEXT_INACTIVE_COLOR to "Int",
        Prefs.KEY_CHARGING_SHOWS_VALUE to "Boolean",
        Prefs.KEY_CLOCK_SIZE to "Int",
        Prefs.KEY_CLOCK_WEIGHT to "Int",
        Prefs.KEY_COLOR_CHARGING_ON_DARK to "Int",
        Prefs.KEY_COLOR_CHARGING_ON_LIGHT to "Int",
        Prefs.KEY_COLOR_CRITICAL_ON_DARK to "Int",
        Prefs.KEY_COLOR_CRITICAL_ON_LIGHT to "Int",
        Prefs.KEY_COLOR_LOW_ON_DARK to "Int",
        Prefs.KEY_COLOR_LOW_ON_LIGHT to "Int",
        Prefs.KEY_DATA_SIM_ONLY to "Boolean",
        Prefs.KEY_DEBUG_LOG to "Boolean",
        Prefs.KEY_DOT_SCALE to "Int",
        Prefs.KEY_DUAL_SIM to "Boolean",
        Prefs.KEY_ENABLED to "Boolean",
        Prefs.KEY_GLYPH_SCALE to "Int",
        Prefs.KEY_HEART_ANIMATE_ALWAYS to "Boolean",
        Prefs.KEY_HEART_ANIMATION to "Boolean",
        Prefs.KEY_HEART_AUTO_TINT to "Boolean",
        Prefs.KEY_HEART_BRIGHTNESS to "Int",
        Prefs.KEY_HEART_CHARGING to "Int",
        Prefs.KEY_HEART_FULL to "Int",
        Prefs.KEY_HEART_LOW to "Int",
        Prefs.KEY_HEART_NORMAL to "Int",
        Prefs.KEY_HEART_BOLT to "Boolean",
        Prefs.KEY_SETTINGS_COLORS_ENABLED to "Boolean",
        Prefs.KEY_SETTINGS_CARD_COLOR to "Int",
        Prefs.KEY_HEART_PERCENTAGE to "Boolean",
        Prefs.KEY_HEART_PERCENTAGE_RIGHT to "Boolean",
        Prefs.KEY_GESTURE_ANIMATION to "Int",
        Prefs.KEY_GESTURE_SHAPE to "Int",
        Prefs.KEY_COMPACT_NAVIGATION to "Boolean",
        Prefs.KEY_SHADE_BLUR_ENABLED to "Boolean",
        Prefs.KEY_SHADE_BLUR_LITE to "Boolean",
        Prefs.KEY_SHADE_BLUR_RADIUS to "Int",
        Prefs.KEY_SHADE_TINT_ENABLED to "Boolean",
        Prefs.KEY_SHADE_TINT_COLOR to "Int",
        Prefs.KEY_NOTIFICATION_BG_ENABLED to "Boolean",
        Prefs.KEY_NOTIFICATION_BG_COLOR to "Int",
        Prefs.KEY_NOTIFICATION_TEXT_ENABLED to "Boolean",
        Prefs.KEY_NOTIFICATION_TEXT_COLOR to "Int",
        Prefs.KEY_COMPACT_KEYBOARD to "Boolean",
        Prefs.KEY_GESTURE_CUSTOM to "Boolean",
        Prefs.KEY_GESTURE_COLOR to "Int",
        Prefs.KEY_GESTURE_BACKGROUND_MODE to "Int",
        Prefs.KEY_GESTURE_BACKGROUND_START to "Int",
        Prefs.KEY_GESTURE_BACKGROUND_END to "Int",
        Prefs.KEY_GESTURE_BACK_OPACITY to "Int",
        Prefs.KEY_HEART_STATUS_TINT to "Boolean",
        Prefs.KEY_LOW_THRESHOLD to "Int",
        Prefs.KEY_MOBILE_TYPE_MODE to "Int",
        Prefs.KEY_NAV_LABELS to "Int",
        Prefs.KEY_NAV_STYLE to "Int",
        Prefs.KEY_BATTERY_WARNING_STYLE to "String",
        Prefs.KEY_OUT_ORDER to "String",
        Prefs.KEY_OUT_SIGNAL_MARGIN to "Int",
        Prefs.KEY_OUT_SIGNAL_SIZE to "Int",
        Prefs.KEY_OUT_TYPE_MARGIN_LEFT to "Int",
        Prefs.KEY_OUT_TYPE_MARGIN_RIGHT to "Int",
        Prefs.KEY_OUT_TYPE_SIZE to "Int",
        Prefs.KEY_OUT_TYPE_SIZE_DP to "Int",
        Prefs.KEY_PERF_POWER to "Boolean",
        Prefs.KEY_PERF_SCALE to "Int",
        Prefs.KEY_PERF_TEMP to "Boolean",
        Prefs.KEY_PERF_TEMP_CHARGING_ONLY to "Boolean",
        Prefs.KEY_PERF_WEIGHT to "Int",
        Prefs.KEY_RING_LAYOUT to "String",
        Prefs.KEY_RING_STROKE to "Int",
        Prefs.KEY_ROLE_COLORS to "Boolean",
        Prefs.KEY_SHOW_BOLT to "Boolean",
        Prefs.KEY_SHOW_MOBILE to "Boolean",
        Prefs.KEY_SHOW_MOBILE_TYPE to "Boolean",
        Prefs.KEY_SHOW_SIM_1 to "Boolean",
        Prefs.KEY_SHOW_SIM_2 to "Boolean",
        Prefs.KEY_SHOW_VALUE to "Boolean",
        Prefs.KEY_SHOW_WIFI to "Boolean",
        Prefs.KEY_SIGNAL_MODE to "Int",
        Prefs.KEY_STACKED_SIGNAL to "Boolean",
        Prefs.KEY_TRACK_ALPHA to "Int",
        Prefs.KEY_TRIO_STYLE to "Int",
        Prefs.KEY_TYPE_HIDE_ON_WIFI to "Boolean",
        Prefs.KEY_TYPE_SIZE to "Int",
        Prefs.KEY_TYPE_SUFFIX_SCALE to "Int",
        Prefs.KEY_TYPE_WEIGHT to "Int",
        Prefs.KEY_VALUE_CENTRED to "Boolean",
        Prefs.KEY_VALUE_SIZE to "Int",
        Prefs.KEY_VALUE_WEIGHT to "Int",
        Prefs.KEY_WIFI_SCALE to "Int"
    )
    private val types: Map<String, String> = buildMap {
        putAll(baseTypes)
        for (key in Prefs.STYLED_KEYS) baseTypes[key]?.let { put(Prefs.styledKey(key, Prefs.STYLE_CAPSULE), it) }
    }

    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val settings = JSONObject()
        for ((key, value) in prefs.all) if (types.containsKey(key)) {
            require(matches(types.getValue(key), value)) { "Invalid saved setting: $key" }
            settings.put(key, value)
        }
        val images = linkedMapOf<String, ByteArray>()
        var total = 0
        for (slot in slots) {
            val f = ControlCenterImageStore.file(context, slot)
            if (f.isFile) {
                require(f.length() <= imageLimit) { "Image is too large" }
                val bytes = f.readBytes(); total += bytes.size
                require(total <= totalLimit) { "Images are too large" }
                images[slot] = bytes
            }
        }
        val soundUri = ChargingSoundStore.audio(context)
        val soundName = ChargingSoundStore.chosen(context)
        require((soundUri == null) == (soundName == null)) { "Charging sound settings are incomplete" }
        val sound = soundUri?.let { audio ->
            val input = context.contentResolver.openInputStream(audio) ?: error("Cannot read charging sound")
            input.use {
                val out = ByteArrayOutputStream(); val buffer = ByteArray(16384)
                while (true) {
                    val n = it.read(buffer); if (n < 0) break
                    require(out.size() + n <= audioLimit && total + n <= totalLimit) { "Charging sound is too large" }
                    total += n; out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        }
        val charging = JSONObject().put("volume", ChargingSoundStore.volume(context)).put("hasSound", sound != null)
        if (sound != null) charging.put("name", soundName)
        val manifest = JSONObject().put("format", "HyperLockTheme").put("version", 3)
            .put("settings", settings).put("images", JSONArray(images.keys.toList())).put("chargingSound", charging)
            .put("mediaVolumeBoost", MediaBoostSettings.isEnabled(context))
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open destination")
        ZipOutputStream(stream).use { zip ->
            fun entry(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            entry("manifest.json", manifest.toString().toByteArray(Charsets.UTF_8))
            if (sound != null) entry("audio/charging", sound)
            for ((slot, data) in images) entry("images/$slot." + if(AnimatedAssetFormat.parse(data)!=null) "hgif" else "png", data)
        }
    }

    suspend fun import(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open theme")
        ZipInputStream(stream).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                require(!e.isDirectory && (e.name == "manifest.json" || e.name == "audio/charging" || slots.any { e.name == "images/$it.png" || e.name == "images/$it.hgif" })) { "Unknown file in theme" }
                require(!entries.containsKey(e.name)) { "Duplicate file in theme" }
                val limit = when (e.name) { "manifest.json" -> 256 * 1024; "audio/charging" -> audioLimit; else -> imageLimit }
                val out = ByteArrayOutputStream(); val buffer = ByteArray(16384)
                while (true) {
                    val n = zip.read(buffer); if (n < 0) break
                    require(out.size() + n <= limit && total + n <= totalLimit) { "Theme is too large" }
                    total += n; out.write(buffer, 0, n)
                }
                entries[e.name] = out.toByteArray()
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(String(entries.remove("manifest.json") ?: error("Missing theme manifest"), Charsets.UTF_8))
        require(manifest.getString("format") == "HyperLockTheme" && manifest.getInt("version") in 1..3) { "Unsupported theme format" }
        val listed = manifest.getJSONArray("images")
        val expected = mutableSetOf<String>()
        for (i in 0 until listed.length()) {
            val slot = listed.getString(i)
            require(slot in slots && expected.add(slot)) { "Invalid image list" }
        }
        val images=linkedMapOf<String,ByteArray>()
        for(slot in expected) {
            val png=entries["images/$slot.png"];val gif=entries["images/$slot.hgif"]
            require((png==null)!=(gif==null)) { "Missing or duplicate image" }
            val bytes=png?:gif!!
            if(gif!=null) {
                val asset=AnimatedAssetFormat.parse(bytes) ?: error("Invalid animation")
                val movie=android.graphics.Movie.decodeByteArray(asset.bytes,0,asset.bytes.size) ?: error("Invalid GIF")
                require(movie.width() in 1..2048 && movie.height() in 1..2048) { "Invalid GIF dimensions" }
            } else {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outMimeType=="image/png" && bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024) { "Invalid image" }
                val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Invalid image");bitmap.recycle()
            }
            images[slot]=bytes
        }
        val audio = entries.remove("audio/charging")
        val charging = if (manifest.getInt("version") >= 3) manifest.getJSONObject("chargingSound") else null
        val mediaBoost = if (manifest.has("mediaVolumeBoost")) manifest.getBoolean("mediaVolumeBoost") else null
        val hasSound = charging?.getBoolean("hasSound") ?: false
        val soundVolume = charging?.getInt("volume") ?: 100
        require(soundVolume in 0..300 && hasSound == (audio != null)) { "Invalid charging sound" }
        val soundName = if (hasSound) charging!!.getString("name").also { require(it.isNotBlank() && it.length <= 255) { "Invalid sound name" } } else null
        require(entries.size==images.size) { "Unexpected images" }
        val json = manifest.getJSONObject("settings")
        val values = linkedMapOf<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next(); val value = json.get(key)
            require(types.containsKey(key) && matches(types.getValue(key), value)) { "Invalid setting: $key" }
            values[key] = value
        }
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val oldSettings = prefs.all.filterKeys { types.containsKey(it) || it == Prefs.KEY_CC_IMAGE_REVISION }
        val original = slots.associateWith { slot ->
            val f = ControlCenterImageStore.file(context, slot)
            if (f.isFile) { require(f.length() <= imageLimit); f.readBytes() } else null
        }
        fun write(slot: String, data: ByteArray?) {
            val f = ControlCenterImageStore.file(context, slot)
            if (data == null) { require(!f.exists() || f.delete()) { "Cannot remove image" }; return }
            check(f.parentFile?.mkdirs() == true || f.parentFile?.isDirectory == true)
            val atomic = AtomicFile(f); val out = atomic.startWrite()
            try { out.write(data); atomic.finishWrite(out) } catch (t: Throwable) { atomic.failWrite(out); throw t }
        }
        try {
            for (slot in slots) write(slot, images[slot])
            val editor = prefs.edit()
            types.keys.forEach { editor.remove(it) }
            for ((key, value) in values) when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
            editor.putInt(Prefs.KEY_CC_IMAGE_REVISION, (prefs.getInt(Prefs.KEY_CC_IMAGE_REVISION, 0) + 1) and Int.MAX_VALUE)
            check(editor.commit()) { "Cannot save settings" }
            if (charging != null) {
                if (audio != null) {
                    val temporary = java.io.File.createTempFile("charging-import-", ".audio", context.cacheDir)
                    try {
                        temporary.writeBytes(audio)
                        ChargingSoundStore.apply(context, Uri.fromFile(temporary), soundName)
                    } finally { temporary.delete() }
                } else ChargingSoundStore.restore(context)
                ChargingSoundStore.setVolume(context, soundVolume)
            }
            if (mediaBoost != null) MediaBoostSettings.setEnabled(context, mediaBoost)
        } catch (t: Throwable) {
            for (slot in slots) runCatching { write(slot, original[slot]) }
            runCatching {
                val rollback = prefs.edit()
                types.keys.forEach { rollback.remove(it) }
                rollback.remove(Prefs.KEY_CC_IMAGE_REVISION)
                for ((key, value) in oldSettings) when (value) {
                    is Boolean -> rollback.putBoolean(key, value)
                    is Int -> rollback.putInt(key, value)
                    is String -> rollback.putString(key, value)
                }
                rollback.commit()
            }
            throw t
        }
    }
    private fun matches(type: String, value: Any?): Boolean = when (type) {
        "Boolean" -> value is Boolean
        "Int" -> value is Int
        "String" -> value is String && value.length <= 16384
        else -> false
    }
}
