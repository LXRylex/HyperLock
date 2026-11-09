// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

private const val PACKAGES = "angle_gl_driver_selection_pkgs"
private const val VALUES = "angle_gl_driver_selection_values"
private data class AngleApp(val name: String, val packageName: String, val system: Boolean)

private object AngleStore {
    private fun read(context: Context, key: String) = Settings.Global.getString(context.contentResolver, key)
    fun selections(context: Context) = AngleSelections.parse(read(context, PACKAGES), read(context, VALUES))
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private fun command(key: String, value: String?): String = if (value.isNullOrEmpty() || value == "null")
        "settings delete global $key" else "settings put global $key ${quote(value)}"

    fun set(context: Context, name: String, enabled: Boolean): AngleSelections {
        val oldPackages = read(context, PACKAGES)
        val oldValues = read(context, VALUES)
        val updated = AngleSelections.parse(oldPackages, oldValues)
        updated.setAngle(name, enabled)
        val change = "${command(PACKAGES, updated.packages())} && ${command(VALUES, updated.values())}"
        val rollback = "${command(PACKAGES, oldPackages)}; ${command(VALUES, oldValues)}"
        val process = ProcessBuilder("su", "-c", "($change) || { $rollback; exit 1; }").redirectErrorStream(true).start()
        check(process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); "Root request timed out" }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.exitValue() == 0) { output.ifBlank { "Could not update driver settings. Allow root access for HyperLock." } }
        return selections(context)
    }
}

@Composable
internal fun AngleAppsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AngleApp>>(emptyList()) }
    var choices by remember { mutableStateOf<AngleSelections?>(null) }
    var query by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val result = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val all = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                all.map { app ->
                    val label = try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                    AngleApp(label, app.packageName, app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) to AngleStore.selections(context)
            }
            apps = result.first
            choices = result.second
        } catch (t: Exception) { error = t.message ?: "Could not load apps" }
    }
    val visible = remember(apps, query, showSystemApps) { apps.filter { (showSystemApps || !it.system) && (it.name.contains(query, true) || it.packageName.contains(query, true)) } }
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", modifier = Modifier.clickable { onBack() }.padding(end = 18.dp), fontSize = 30.sp)
            Text("ANGLE renderer", style = MiuixTheme.textStyles.title3)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Choose apps that should use ANGLE. Restart an app to apply. Some system apps may ignore this setting or display incorrectly. Root access is required to change it.", style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(8.dp))
            }
            item {
                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(16.dp),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search apps or package names", color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)); inner() } })
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    GlassDropdownPreference(items = listOf("User apps", "All apps"), selectedIndex = if (showSystemApps) 1 else 0,
                        title = "App filter", onSelectedIndexChange = { showSystemApps = it == 1 })
                }
            }
            error?.let { message -> item { Text(message, modifier = Modifier.padding(8.dp)) } }
            if (choices == null && error == null) item { Text("Loading apps…", modifier = Modifier.padding(8.dp)) }
            items(visible, key = { it.packageName }) { app ->
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(checked = choices?.usesAngle(app.packageName) == true,
                        title = app.name, summary = app.packageName + if (app.system) " · System app" else "",
                        onCheckedChange = { enabled ->
                            if (!busy && choices != null) {
                                busy = true
                                scope.launch {
                                    try { choices = withContext(Dispatchers.IO) { AngleStore.set(context, app.packageName, enabled) }; error = null }
                                    catch (t: Exception) { error = t.message ?: "Could not update ANGLE" }
                                    finally { busy = false }
                                }
                            }
                        })
                }
            }
        }
    }
}
