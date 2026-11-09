// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.yixing233.hyperduo.SettingsColorChannels
import io.github.yixing233.hyperduo.TrioSettings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun ShadeTintControls(s: TrioSettings, update: ((SettingsRepository) -> Unit) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    SwitchPreference(checked = s.shadeTintEnabled, title = "Custom panel tint",
        summary = "Change the shade's background blend color and opacity.",
        onCheckedChange = { value -> update { it.setShadeTintEnabled(value) } })
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Tint color", modifier = Modifier.weight(1f))
        Swatch(s.shadeTintColor)
        Spacer(Modifier.width(8.dp))
        Button(onClick = { showPicker = true }, enabled = s.shadeTintEnabled) { Text("Choose") }
    }
    IntSlider(value = SettingsColorChannels.opacity(s.shadeTintColor), min = 0, max = 100,
        title = "Tint opacity", summary = "0% makes the tint transparent.", enabled = s.shadeTintEnabled,
        valueTextSuffix = "%", onValueChange = { value -> update { it.setShadeTintColor(SettingsColorChannels.withOpacity(s.shadeTintColor, value)) } })
    if (showPicker) OverlayDialog(show = true, title = "Panel tint color", onDismissRequest = { showPicker = false }) {
        ColorPalette(color = Color(s.shadeTintColor or 0xff000000.toInt()), onColorChanged = { color ->
            val rgb = color.toArgb() and 0x00ffffff
            val alpha = s.shadeTintColor and 0xff000000.toInt()
            update { it.setShadeTintColor(alpha or rgb) }
        })
    }
}
