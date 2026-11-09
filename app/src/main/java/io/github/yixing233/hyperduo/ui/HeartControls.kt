// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yixing233.hyperduo.TrioSettings
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Small Heart-specific panel; existing module master switch and style selector stay authoritative. */
@Composable
internal fun HeartControls(s: TrioSettings, update: ((SettingsRepository)->Unit)->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Text("Bottom-to-top fill. Native network icons keep their shapes and signal readings.",
            style=MiuixTheme.textStyles.footnote1)
        SwitchPreference(checked=s.heartBolt,title="Show charging bolt",
            summary="Shows a matching bolt beside the heart while charging.",
            onCheckedChange={ v->update { it.setHeartBolt(v) } })
        SwitchPreference(checked=s.heartPercentage,title="Show battery percentage",
            onCheckedChange={ v->update { it.setHeartPercentage(v) } })
        SwitchPreference(checked=s.heartPercentageRight,title="Percentage on the right",
            summary=if(s.heartPercentageRight) "Numbers beside the heart" else "Numbers inside the heart",
            enabled=s.heartPercentage,
            onCheckedChange={ v->update { it.setHeartPercentageRight(v) } })
        SwitchPreference(checked=s.heartAutoTint,title="Follow status bar outline tint",
            onCheckedChange={ v->update { it.setHeartAutoTint(v) } })
        SwitchPreference(checked=s.heartStatusTint,title="Match status bar color",
            summary="Clock, Wi-Fi, signal and status icons follow the Heart color.",
            onCheckedChange={ v->update { it.setHeartStatusTint(v) } })
        IntSlider(value=s.heartBrightness.coerceIn(20,100),min=20,max=100,step=5,
            title="Icon brightness",summary="Dims the heart and matching status bar icons.",
            enabled=true,valueTextSuffix="%",onValueChange={ v->update { it.setHeartBrightness(v) } })
        SwitchPreference(checked=s.heartAnimation,title="Heart wave + pulse",
            summary="Moving liquid edge and breathing fill while charging.",
            onCheckedChange={ v->update { it.setHeartAnimation(v) } })
        SwitchPreference(checked=s.heartAnimateAlways,title="Animate when not charging",
            summary="Keeps the heart moving while unplugged; may use more battery.",
            enabled=s.heartAnimation,onCheckedChange={ v->update { it.setHeartAnimateAlways(v) } })
        HeartColorInput("Normal",s.heartNormal) { v->update { it.setHeartNormal(v) } }
        HeartColorInput("Charging",s.heartCharging) { v->update { it.setHeartCharging(v) } }
        HeartColorInput("Low battery",s.heartLow) { v->update { it.setHeartLow(v) } }
        HeartColorInput("Full battery",s.heartFull) { v->update { it.setHeartFull(v) } }
    }
}

@Composable
private fun HeartColorInput(label:String,color:Int,onChange:(Int)->Unit) {
    var value by remember(color) { mutableStateOf("#%06X".format(color and 0xffffff)) }
    var invalid by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp)) {
        Text(label,modifier=Modifier.weight(1f))
        BasicTextField(value=value,onValueChange={ next->
            if(next.length<=7) {
                value=next
                val raw=next.removePrefix("#")
                val parsed=if(raw.length==6) raw.toLongOrNull(16) else null
                invalid=parsed==null
                if(parsed!=null) onChange((parsed or 0xff000000L).toInt())
            }
        },singleLine=true,modifier=Modifier.width(110.dp),
          textStyle=TextStyle(fontSize=16.sp,color=if(invalid) Color(0xffe95060) else MiuixTheme.colorScheme.onSurface))
    }
    if(invalid) Text("Use #RRGGBB; the last valid color stays active.",style=MiuixTheme.textStyles.footnote1)
}
