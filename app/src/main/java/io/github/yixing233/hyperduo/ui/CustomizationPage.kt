// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.yixing233.hyperduo.TrioSettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CustomizationPage(page:String, settings:TrioSettings, update:((SettingsRepository)->Unit)->Unit, onOpen:(String)->Unit, onBack:()->Unit) {
    val title=when(page) {
        "notification_colors"->"Notification colors";"advanced"->"Advanced settings";"heart"->"Heart battery";"control"->"Control Center";"layout"->"Tile layout";
        "icons"->"Icon colors";"text"->"Card text colors";"images"->"Background images";"gesture"->"Gesture indicators";"settings_colors"->"Settings colors";"battery_warning"->"Low battery warning";"charging_sound"->"Charging sound";else->"Settings"
    }
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role=Role.Button,onClick=onBack),contentAlignment=Alignment.Center) {
                Text("‹",style=MiuixTheme.textStyles.title1)
            }
            Text(title,modifier=Modifier.weight(1f).padding(start=8.dp),style=MiuixTheme.textStyles.title3)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if (page == "heart") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xff24283b)).padding(18.dp),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("BATTERY PREVIEW", color=Color(0xffc8cbe2), fontSize=11.sp,
                        fontWeight=FontWeight.Bold, modifier=Modifier.fillMaxWidth())
                    ConceptPreview(settings, Modifier.fillMaxWidth().height(120.dp))
                    Text("Preview · 72%", color=Color(0xffc8cbe2), fontSize=12.sp)
                }
                Text("Heart settings", style=MiuixTheme.textStyles.title3, modifier=Modifier.padding(top=8.dp, start=4.dp))
            }
            if(page=="control") {
                Text("Customize your Control Center", style=MiuixTheme.textStyles.title3,
                    modifier=Modifier.padding(vertical=10.dp, horizontal=4.dp))
                MediaBoostToggle()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp)) {
                        SwitchPreference(checked=settings.shadeBlurEnabled, title="Custom panel blur", summary="Adjusts the Control Center and notification shade background. Restart System UI after changing it.", onCheckedChange={ v -> update { it.setShadeBlurEnabled(v) } })
                        SwitchPreference(checked=settings.shadeBlurLite, title="Softer blur", summary="Halves the selected blur radius for both panels and may reduce GPU work. Requires custom panel blur.", onCheckedChange={ v -> update { it.setShadeBlurLite(v) } })
                        IntSlider(value=settings.shadeBlurRadius.coerceIn(0,200), min=0, max=200, title="Background blur strength", summary="Gentler control at low values. 100 matches the default radius.", enabled=settings.shadeBlurEnabled, onValueChange={ v -> update { it.setShadeBlurRadius(v) } })
                        ShadeTintControls(settings, update)
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        ArrowPreference(title="Tile layout",summary="Large Bluetooth and airplane tiles",onClick={onOpen("layout")})
                        ArrowPreference(title="Icon colors",summary="Active and inactive icons",onClick={onOpen("icons")})
                        ArrowPreference(title="Card text colors",summary="Titles and status text",onClick={onOpen("text")})
                        ArrowPreference(title="Background images",summary="Photos, crops and brightness",onClick={onOpen("images")})
                        ArrowPreference(title="Notification colors",summary="Card backgrounds and text",onClick={onOpen("notification_colors")})
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp)) {
                        if(page=="heart")HeartControls(settings,update)
                        else if(page=="notification_colors") NotificationColorControls(settings,update)
                        else if(page=="advanced") {
                            SwitchPreference(checked=settings.keepGameFrameRateUnlocked, title="Disable 60 Hz game limit", summary="Keeps the Developer Options switch on. Games may still have their own limits. When turned off, the Developer Options switch stays as last set.", onCheckedChange={ value -> update { it.setKeepGameFrameRateUnlocked(value) } })
                            ArrowPreference(title="ANGLE renderer", summary="Choose ANGLE for individual apps", onClick={onOpen("angle_apps")})
                        }
                        else if(page=="gesture") GestureControls(settings,update)
                        else if(page=="settings_colors") SettingsColorsControls(settings,update)
                        else if(page=="battery_warning") BatteryWarningControls(settings,update)
                        else if(page=="charging_sound") ChargingSoundControls()
                        else ControlCenterImagesControls(settings,update,page)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
