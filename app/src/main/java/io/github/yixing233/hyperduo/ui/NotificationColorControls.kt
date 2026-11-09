// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.yixing233.hyperduo.NotificationColorPolicy
import io.github.yixing233.hyperduo.SettingsColorChannels
import io.github.yixing233.hyperduo.TrioSettings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun NotificationColorControls(s: TrioSettings, update: ((SettingsRepository) -> Unit) -> Unit) {
    var picker by remember { mutableStateOf("") }
    SwitchPreference(checked = s.notificationBgEnabled, title = "Custom card background",
        summary = "Colors notification and music player backgrounds without changing their icons.",
        onCheckedChange = { value -> update { it.setNotificationBgEnabled(value) } })
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Background color", modifier = Modifier.weight(1f))
        Swatch(s.notificationBgColor); Spacer(Modifier.width(8.dp))
        Button(onClick = { picker = "background" }, enabled = s.notificationBgEnabled) { Text("Choose") }
    }
    IntSlider(value = SettingsColorChannels.opacity(s.notificationBgColor), min = 0, max = 100,
        title = "Background color opacity", summary = "Lower values show more of the original card.",
        enabled = s.notificationBgEnabled, valueTextSuffix = "%",
        onValueChange = { value -> update { it.setNotificationBgColor(SettingsColorChannels.withOpacity(s.notificationBgColor, value)) } })
    SwitchPreference(checked = s.notificationTextEnabled, title = "Custom notification text",
        summary = "Colors standard notification text; some apps use their own styling.",
        onCheckedChange = { value -> update { it.setNotificationTextEnabled(value) } })
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Text color", modifier = Modifier.weight(1f))
        Swatch(s.notificationTextColor); Spacer(Modifier.width(8.dp))
        Button(onClick = { picker = "text" }, enabled = s.notificationTextEnabled) { Text("Choose") }
    }
    if (picker.isNotEmpty()) {
        val background = picker == "background"
        val selected = if (background) s.notificationBgColor else s.notificationTextColor
        OverlayDialog(show = true, title = if (background) "Notification background" else "Notification text", onDismissRequest = { picker = "" }) {
            ColorPalette(color = Color(selected or 0xff000000.toInt()), onColorChanged = { color ->
                val next = NotificationColorPolicy.keepAlpha(selected, color.toArgb())
                if (background) update { it.setNotificationBgColor(next) } else update { it.setNotificationTextColor(next) }
            })
        }
    }
}
