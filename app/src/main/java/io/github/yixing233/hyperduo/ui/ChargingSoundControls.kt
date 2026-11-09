// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.log10
import kotlin.math.roundToInt
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SOUND_KEY = "charging_started_sound"
private const val SOUND_PREFS = "charging_sound_settings"

/** The system sound player needs a readable, durable MediaStore file. */
internal object ChargingSoundStore {
    private fun root(command: String) {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { output.ifBlank { "Root access was denied" } }
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private fun prefs(context: Context) = context.getSharedPreferences(SOUND_PREFS, Context.MODE_PRIVATE)
    fun volume(context: Context): Int = prefs(context).getInt("volume", 100).coerceIn(0, 300)
    fun setVolume(context: Context, value: Int) { prefs(context).edit().putInt("volume", value.coerceIn(0, 300)).apply() }
    fun chosen(context: Context): String? = prefs(context).getString("name", null)
    fun audio(context: Context): Uri? = prefs(context).getString("uri", null)?.let(Uri::parse)

    fun apply(context: Context, source: Uri, restoredName: String? = null) {
        val resolver = context.contentResolver
        val name = restoredName ?: resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Charging sound"
        val extension = name.substringAfterLast('.', "mp3").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "mp3"
        val display = "HyperLock_charging_${System.currentTimeMillis()}.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, display)
            put(MediaStore.Audio.Media.MIME_TYPE, android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: resolver.getType(source) ?: "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS + "/HyperLock")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create sound file")
        try {
            resolver.openInputStream(source)?.use { input ->
                (resolver.openOutputStream(dest) ?: error("Could not write sound file")).use { output -> input.copyTo(output) }
            } ?: error("Could not read selected file")
            val player = MediaPlayer()
            try { player.setDataSource(context, dest); player.prepare() } finally { player.release() }
            values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            check(resolver.update(dest, values, null, null) == 1) { "Could not save sound file" }
            @Suppress("DEPRECATION")
            val path = resolver.query(dest, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: error("Could not find saved sound file")
            val p = prefs(context)
            val current = Settings.Global.getString(resolver, SOUND_KEY) ?: error("Charging sound setting is unavailable")
            val previous = p.getString("original", null) ?: current
            val oldUri = audio(context)
            root("settings put global $SOUND_KEY ${quote(path)}")
            check(Settings.Global.getString(resolver, SOUND_KEY) == path) { "Could not change charging sound" }
            p.edit().putString("original", previous).putString("uri", dest.toString()).putString("name", name).apply()
            if (oldUri != null && oldUri != dest) runCatching { resolver.delete(oldUri, null, null) }
        } catch (e: Exception) {
            runCatching { resolver.delete(dest, null, null) }
            throw e
        }
    }
    fun restore(context: Context) {
        val p = prefs(context)
        val previous = p.getString("original", null) ?: return
        root("settings put global $SOUND_KEY ${quote(previous)}")
        check(Settings.Global.getString(context.contentResolver, SOUND_KEY) == previous) { "Could not restore original sound" }
        val old = audio(context)
        p.edit().clear().apply()
        if (old != null) runCatching { context.contentResolver.delete(old, null, null) }
    }
}

@Composable
internal fun ChargingSoundControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(ChargingSoundStore.chosen(context)) }
    var busy by remember { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(ChargingSoundStore.volume(context)) }
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewEffect by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    DisposableEffect(Unit) { onDispose { previewEffect?.release(); preview?.release(); preview = null } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; message = ""
            val result = withContext(Dispatchers.IO) { runCatching { ChargingSoundStore.apply(context, uri) } }
            selected = ChargingSoundStore.chosen(context); busy = false
            message = result.fold({ "Charging sound updated." }, { "Couldn't apply sound: ${it.message ?: "unknown error"}" })
        }
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Charging sound", style = MiuixTheme.textStyles.title3)
        Text(selected ?: "Using the system sound", style = MiuixTheme.textStyles.body1)
        Text("Choose an MP3 or another audio file. Enable Security scope in Vector and restart Security to replace Xiaomi’s charging sound.", style = MiuixTheme.textStyles.footnote1)
        Button(enabled = !busy, onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (selected == null) "Choose sound" else "Replace sound") }
        if (selected != null) {
            IntSlider(value=volume,min=0,max=300,step=5,title="Charging sound volume",
                summary="Above 100% boosts the sound; louder settings may distort",enabled=!busy,valueTextSuffix="%",
                onValueChange={ value -> volume=value; ChargingSoundStore.setVolume(context,value)
                    preview?.setVolume(minOf(value,100)/100f,minOf(value,100)/100f)
                    try { previewEffect?.setTargetGain(if(value>100) (2000*log10(value/100.0)).roundToInt() else 0); previewEffect?.enabled=value>100 } catch (_: Exception) {} })
            Button(enabled = !busy, onClick = {
                try {
                    previewEffect?.release(); previewEffect=null
                    preview?.release()
                    preview = MediaPlayer().apply {
                        setAudioStreamType(AudioManager.STREAM_SYSTEM)
                        setVolume(minOf(volume,100)/100f,minOf(volume,100)/100f)
                        setDataSource(context, ChargingSoundStore.audio(context) ?: error("Sound unavailable"))
                        setOnCompletionListener { previewEffect?.release(); previewEffect=null; it.release(); preview = null }
                        prepare()
                        try { previewEffect=LoudnessEnhancer(audioSessionId).apply { setTargetGain(if(volume>100) (2000*log10(volume/100.0)).roundToInt() else 0); enabled=volume>100 } } catch (_: Exception) { }
                        start()
                    }
                } catch (e: Exception) { message = "Couldn't play sound: ${e.message}" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Play preview") }
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; message = ""
                    val result = withContext(Dispatchers.IO) { runCatching { ChargingSoundStore.restore(context) } }
                    selected = ChargingSoundStore.chosen(context); busy = false
                    message = result.fold({ "Original charging sound restored." }, { "Couldn't restore sound: ${it.message ?: "unknown error"}" })
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Restore original sound") }
        }
        if (busy) Text("Applying…")
        if (message.isNotEmpty()) Text(message, style = MiuixTheme.textStyles.footnote1)
    }
}
