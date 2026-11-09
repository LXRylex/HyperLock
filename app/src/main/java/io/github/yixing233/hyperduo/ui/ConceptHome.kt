// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import io.github.yixing233.hyperduo.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.yixing233.hyperduo.Prefs
import io.github.yixing233.hyperduo.TrioPreviewView
import io.github.yixing233.hyperduo.TrioSettings
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Overview only: all actions delegate to the existing settings repository and pages. */
internal fun LazyListScope.conceptHome(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onOpen: (String) -> Unit,
    onComponents: () -> Unit,
    onAbout: () -> Unit,
) {
    item {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConceptHero(settings, onOpen = { onOpen("editor") })
            Text("Battery style", style = MiuixTheme.textStyles.title3,
                modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Prefs.STYLE_HEART to "Heart", Prefs.STYLE_RING to "Ring",
                    Prefs.STYLE_CAPSULE to "Capsule", Prefs.STYLE_DEFAULT to "System").forEach { (style,label) ->
                    val selected = settings.trioStyle == style
                    val shape = RoundedCornerShape(18.dp)
                    Column(Modifier.weight(1f).clip(shape)
                        .background(if(selected) Color(0xff34385d) else MiuixTheme.colorScheme.surfaceContainerHigh)
                        .border(if(selected) 1.5.dp else 0.dp, if(selected) Color(0xff8991ff) else Color.Transparent, shape)
                        .clickable { update { it.setTrioStyle(style) } }
                        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (style == Prefs.STYLE_HEART) {
                            val preview = remember(settings) { settings.copy().apply {
                                trioStyle = Prefs.STYLE_HEART
                                heartPercentage = false
                            } }
                            ConceptPreview(preview, Modifier.fillMaxWidth().height(45.dp))
                        } else {
                            Box(Modifier.fillMaxWidth().height(45.dp), contentAlignment = Alignment.Center) {
                                StyleIllustration(style, Modifier.size(38.dp))
                            }
                        }
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
            Text("Customize", style = MiuixTheme.textStyles.title3,
                modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)) {
                SwitchPreference(checked = settings.enabled, title = "Enable custom battery",
                    summary = "Show your chosen icon in the status bar",
                    onCheckedChange = { value -> update { it.setEnabled(value) } })
                ConceptRow("♡", "Heart battery", "Colors, percentage and animation", R.drawable.home_heart_battery) { onOpen("heart") }
                ConceptRow("▦", "Control Center", "Cards, icons, text and images", R.drawable.home_notification_center) { onOpen("control") }
                ConceptRow("♫", "Charging sound", "Choose a sound for plugging in", R.drawable.home_charging_sound) { onOpen("charging_sound") }
                ConceptRow("Aa", "Fonts", "System and status bar fonts", R.drawable.home_fonts) { onOpen("fonts") }
                ConceptRow("!", "Low battery warning", "Background, buttons and battery icon", R.drawable.home_low_battery_warning) { onOpen("battery_warning") }
                ConceptRow("◧", "Settings colors", "Cards and search background", R.drawable.home_settings_colors) { onOpen("settings_colors") }
                ConceptRow("‹", "Gesture indicators", "Line, arrows and background", R.drawable.home_gesture_indicators) { onOpen("gesture") }
                ConceptRow("⚙", "Advanced settings", "Game refresh-rate controls") { onOpen("advanced") }
                ConceptRow("◇", "Component editor", "Arrange Ring and Capsule components", R.drawable.home_component_editor) { onOpen("editor") }
                ConceptRow("◈", "Component settings", "Size, placement and other controls", R.drawable.home_component_settings, onClick = onComponents)
                ConceptRow("☰", "About & theme backup", "Module info, export and import", R.drawable.home_about_backup, onClick = onAbout)
            }
        }
    }
}

/** Lightweight style illustrations, independent of the user's live battery settings. */
@Composable private fun StyleIllustration(style: Int, modifier: Modifier) {
    val color = if (isSystemInDarkTheme()) Color(0xffe5e5ee) else Color(0xff30313c)
    Canvas(modifier) {
        val sw = 2.2.dp.toPx()
        if (style == Prefs.STYLE_RING) {
            val pad = size.width * .16f
            val bounds = Size(size.width - 2 * pad, size.height - 2 * pad)
            drawArc(color.copy(alpha = .22f), 0f, 360f, false, Offset(pad, pad), bounds, style = Stroke(sw))
            drawArc(color, -90f, 260f, false, Offset(pad, pad), bounds, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        } else if (style == Prefs.STYLE_CAPSULE) {
            val left = size.width * .08f; val top = size.height * .30f
            val rect = Size(size.width * .84f, size.height * .40f)
            drawRoundRect(color = color, topLeft = Offset(left, top), size = rect,
                cornerRadius = CornerRadius(rect.height / 2f), style = Stroke(sw))
        } else {
            val left = size.width * .16f; val top = size.height * .32f
            val rect = Size(size.width * .63f, size.height * .36f)
            drawRoundRect(color = color, topLeft = Offset(left, top), size = rect,
                cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(sw))
            drawRoundRect(color = color, topLeft = Offset(size.width*.81f, size.height*.43f),
                size = Size(size.width*.06f, size.height*.14f), cornerRadius = CornerRadius(1.dp.toPx()))
        }
    }
}

@Composable private fun ConceptHero(settings: TrioSettings, onOpen: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(if(dark) listOf(Color(0xff30354e),Color(0xff1a1c26))
            else listOf(Color(0xffdce0ff),Color(0xfff0eff9))))
        .clickable(onClick = onOpen).padding(20.dp)) {
        Text("STATUS BAR PREVIEW", fontSize=10.sp, fontWeight=FontWeight.Bold,
            color=if(dark) Color(0xffc6c8db) else Color(0xff565c7a), letterSpacing=1.sp)
        Row(Modifier.fillMaxWidth().padding(vertical=16.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your style", fontSize=27.sp, fontWeight=FontWeight.Bold,
                    color=if(dark) Color.White else Color(0xff202232))
                Text("Preview · 72%", fontSize=12.sp, color=if(dark) Color(0xffb8bad0) else Color(0xff626780))
            }
            if(settings.trioStyle == Prefs.STYLE_DEFAULT || !settings.enabled)
                Text("System", color=if(dark) Color.White else Color.Black)
            else ConceptPreview(settings, Modifier.size(90.dp))
        }
        Text("Edit component layout  ›", fontSize=13.sp, fontWeight=FontWeight.SemiBold,
            color=if(dark) Color(0xffdedfff) else Color(0xff414b99))
    }
}

@Composable internal fun ConceptPreview(settings: TrioSettings, modifier: Modifier) {
    AndroidView(modifier = modifier, factory = { context -> TrioPreviewView(context).apply {
        setPreviewBackground(android.graphics.Color.TRANSPARENT)
        setForeground(android.graphics.Color.WHITE)
        setInset(0.08f)
    } }, update = { view ->
        view.setSettings(settings)
        view.setState(72, false, false, false, 3, 4, "4G")
    })
}

@Composable private fun ConceptRow(icon: String, title: String, summary: String, drawable: Int? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal=16.dp, vertical=13.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(if (drawable != null) 0.dp else 13.dp)).background(Color(0xff393d63)),
            contentAlignment=Alignment.Center) {
            if (drawable != null) Image(bitmap=ImageBitmap.imageResource(drawable), contentDescription=null,
                modifier=Modifier.size(36.dp), filterQuality=FilterQuality.None)
            else Text(icon, color=Color(0xffb8bdff), fontSize=20.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight=FontWeight.SemiBold, fontSize=14.sp)
            Text(summary, color=MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize=11.sp)
        }
        Text("›", color=MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize=24.sp)
    }
}
