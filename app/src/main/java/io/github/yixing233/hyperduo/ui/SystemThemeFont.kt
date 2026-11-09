// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in Xiaomi theme-font replacement with a guarded, root-owned backup. */
internal object SystemThemeFont {
    private const val PREFS = "system_theme_font"
    private const val CHOSEN = "selected_path"
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    fun chosen(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(CHOSEN, null)?.takeIf { File(it).isFile }

    suspend fun importAndApply(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "system-fonts").apply { check(isDirectory || mkdirs()) }
        val file = File(dir, "font-${System.currentTimeMillis()}.ttf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 32L * 1024 * 1024) { "Font is larger than 32 MB" }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("Could not open this font")
            require(file.length() > 0) { "The selected font is empty" }
            Typeface.createFromFile(file) // Validate before touching the theme.
            applyFile(file)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CHOSEN, file.absolutePath).apply()
            "System font applied. Restart apps to see it."
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
    }

    suspend fun reapply(context: Context): String = withContext(Dispatchers.IO) {
        val path = chosen(context) ?: error("Choose a system font first")
        val file = File(path)
        Typeface.createFromFile(file)
        applyFile(file)
        "System font reapplied. Restart apps to see it."
    }

    suspend fun restore(context: Context): String = withContext(Dispatchers.IO) {
        root(script("restore", ""))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(CHOSEN).apply()
        "Previous theme font restored. Restart apps to see it."
    }

    private fun applyFile(file: File) = root(script("apply", file.absolutePath))

    private fun root(command: String) {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        check(process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); "Root command timed out" }
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        check(process.exitValue() == 0) { output.ifBlank { "Root access denied or theme font unavailable" } }
    }

    private fun script(action: String, source: String): String = """
        set -eu
        target=/data/system/theme/fonts/Roboto-Regular.ttf
        state=/data/adb/hyperlock-system-font
        backup="${'$'}state/previous.ttf"
        installed="${'$'}state/installed.sha256"
        stage=/data/system/theme/fonts/.hyperlock-font-${'$'}${'$'}
        trap 'rm -f "${'$'}stage"' EXIT
        [ -f "${'$'}target" ] && [ ! -L "${'$'}target" ] || { echo 'Apply a font in Xiaomi Themes first.'; exit 1; }
        current=${'$'}(sha256sum "${'$'}target" | cut -d' ' -f1)
        mkdir -p "${'$'}state"
        chmod 700 "${'$'}state"
        if [ ${quote(action)} = apply ]; then
          source=${quote(source)}
          [ -f "${'$'}source" ] || { echo 'The selected font is missing.'; exit 1; }
          if [ ! -e "${'$'}backup" ]; then
            cp -p "${'$'}target" "${'$'}backup"
            cmp -s "${'$'}target" "${'$'}backup" || { echo 'Could not verify font backup.'; exit 1; }
          else
            [ -f "${'$'}installed" ] || { echo 'Font backup needs manual recovery.'; exit 1; }
            saved=${'$'}(cat "${'$'}installed")
            original=${'$'}(sha256sum "${'$'}backup" | cut -d' ' -f1)
            [ "${'$'}current" = "${'$'}saved" ] || [ "${'$'}current" = "${'$'}original" ] || { echo 'Themes changed this font; restore or reset it in Themes first.'; exit 1; }
          fi
          cp "${'$'}source" "${'$'}stage"
          cmp -s "${'$'}source" "${'$'}stage" || { echo 'Font copy verification failed.'; exit 1; }
        else
          [ -f "${'$'}backup" ] && [ -f "${'$'}installed" ] || { echo 'No previous font backup found.'; exit 1; }
          saved=${'$'}(cat "${'$'}installed")
          original=${'$'}(sha256sum "${'$'}backup" | cut -d' ' -f1)
          if [ "${'$'}current" = "${'$'}original" ]; then
            rm -f "${'$'}backup" "${'$'}installed"
            exit 0
          fi
          [ "${'$'}current" = "${'$'}saved" ] || { echo 'Themes changed the font; leaving it unchanged.'; exit 1; }
          cp "${'$'}backup" "${'$'}stage"
          cmp -s "${'$'}backup" "${'$'}stage" || { echo 'Restore copy verification failed.'; exit 1; }
        fi
        owner=${'$'}(stat -c '%u:%g' "${'$'}target")
        mode=${'$'}(stat -c '%a' "${'$'}target")
        context=${'$'}(ls -Zd "${'$'}target" | cut -d' ' -f1)
        chown "${'$'}owner" "${'$'}stage"
        chmod "${'$'}mode" "${'$'}stage"
        chcon "${'$'}context" "${'$'}stage"
        if [ ${quote(action)} = apply ]; then
          sha256sum "${'$'}stage" | cut -d' ' -f1 > "${'$'}installed.new"
          mv -f "${'$'}installed.new" "${'$'}installed"
        fi
        mv -f "${'$'}stage" "${'$'}target"
        if [ ${quote(action)} = restore ]; then
          rm -f "${'$'}backup" "${'$'}installed"
        fi
    """.trimIndent()
}
