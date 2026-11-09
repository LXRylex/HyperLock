// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yixing233.hyperduo.SettingsColorChannels
import io.github.yixing233.hyperduo.TrioSettings
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsColorsControls(s: TrioSettings, update: ((SettingsRepository) -> Unit) -> Unit) {
    SwitchPreference(checked=s.settingsColorsEnabled, title="Custom Settings colors",
        summary="Enable Settings scope in Vector / LSPosed, then restart Settings.",
        onCheckedChange={ value -> update { it.setSettingsColorsEnabled(value) } })
    Text("Background color", style=MiuixTheme.textStyles.title3, modifier=Modifier.padding(horizontal=12.dp, vertical=8.dp))
    Box(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=4.dp)
        .clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
        Box(Modifier.fillMaxWidth().height(72.dp).background(Color(if (s.settingsColorsEnabled) s.settingsCardColor else SettingsColorChannels.withChannel(0xff242424.toInt(), 24, SettingsColorChannels.channel(s.settingsCardColor, 24)))),
            contentAlignment=Alignment.CenterStart) {
            Text("Settings card preview", color=Color.White, modifier=Modifier.padding(horizontal=18.dp))
        }
    }
    listOf("Red" to 16, "Green" to 8, "Blue" to 0).forEach { (label, shift) ->
        IntSlider(value=SettingsColorChannels.channel(s.settingsCardColor, shift), min=0, max=255,
            title=label, summary="", enabled=s.settingsColorsEnabled,
            onValueChange={ value -> update { it.setSettingsCardColor(SettingsColorChannels.withChannel(s.settingsCardColor, shift, value)) } })
    }
    IntSlider(value=SettingsColorChannels.opacity(s.settingsCardColor), min=0, max=100,
        title="Opacity", summary="Works with the original or custom color.",
        enabled=true, valueTextSuffix="%",
        onValueChange={ value -> update { it.setSettingsCardColor(SettingsColorChannels.withOpacity(s.settingsCardColor, value)) } })
}
