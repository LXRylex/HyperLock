// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yixing233.hyperduo.TrioSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GestureControls(s: TrioSettings, update: ((SettingsRepository) -> Unit) -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var picking by remember { mutableStateOf("") }
    var crop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)scope.launch { cropUri=uri; crop=ControlCenterImageStore.decode(context,uri);if(crop==null)showImageImportError(context) }
    }
    if (picking.isNotEmpty()) {
        val selected=when(picking) { "line"->s.gestureColor; "end"->s.gestureBackgroundEnd; else->s.gestureBackgroundStart }
        OverlayDialog(show = true, title = when(picking){"line"->"Line and arrow color";"end"->"Gradient end color";else->"Background color"}, onDismissRequest = { picking = "" }) {
            ColorPalette(color = Color(selected), onColorChanged = { color ->
                val value=color.toArgb()
                when(picking) { "line"->update { it.setGestureColor(value) }; "end"->update { it.setGestureBackgroundEnd(value) }; else->update { it.setGestureBackgroundStart(value) } }
            })
        }
    }
    crop?.let { bitmap ->
        ImageCropDialog(bitmap,"gesture",onCancel={crop=null},onSave={left,top,width,height ->
            crop=null
            scope.launch { if(ControlCenterImageStore.saveSelected(context,"gesture",cropUri,bitmap,left,top,width,height))update { it.refreshCcImages() } else showImageImportError(context) }
        })
    }
    ExperimentalSwitchPreference(checked = s.compactNavigation, title = "Reduce bottom gap system-wide",
        summary = "Keep the gesture line while allowing content closer to the bottom. Restart System UI and System launcher to apply.",
        onCheckedChange = { value -> update { it.setCompactNavigation(value) } })
    ExperimentalSwitchPreference(checked = s.compactKeyboard, title = "Reduce keyboard bottom gap",
        summary = "Keeps a thin gesture strip while typing. Close and reopen the keyboard to apply.",
        onCheckedChange = { value -> update { it.setCompactKeyboard(value) } })
    SwitchPreference(checked = s.gestureCustom, title = "Custom gesture indicators",
        summary = "Colors the bottom line and back arrows. Enable System Launcher scope in Vector / LSPosed for arrows.",
        onCheckedChange = { value -> update { it.setGestureCustom(value) } })
    GestureColorRow("Line and arrow color",s.gestureColor) { picking="line" }
    Text("Back swipe animation",style=MiuixTheme.textStyles.title3,modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
    val animations = listOf("Default", "Grow", "Fade", "Float", "Pop")
    Text("Selected: " + animations[s.gestureAnimation.coerceIn(0,4)],style=MiuixTheme.textStyles.footnote1,modifier=Modifier.padding(horizontal=12.dp))
    for (row in animations.withIndex().toList().chunked(2)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for ((value,label) in row) Button(onClick={update { it.setGestureAnimation(value) }},modifier=Modifier.weight(1f)) { Text(label) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text("Back indicator shape",style=MiuixTheme.textStyles.title3,modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
    val shapes = listOf("Arrow", "\uD81B\uDE6D", "\u2726", "\u2039\uD835\uDFF9", "\u2671", "\uD81A\uDD10", "\uAA04\uFE0E")
    Text("Selected: " + shapes[s.gestureShape.coerceIn(0,6)],style=MiuixTheme.textStyles.footnote1,modifier=Modifier.padding(horizontal=12.dp))
    for (row in shapes.withIndex().toList().chunked(3)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for ((value,label) in row) Button(onClick={update { it.setGestureShape(value) }},modifier=Modifier.weight(1f)) { Text(label,fontSize=20.sp) }
            repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Text("Back swipe background",style=MiuixTheme.textStyles.title3,modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
    Text("Selected: " + listOf("Default", "Solid", "Gradient", "Image")[s.gestureBackgroundMode.coerceIn(0,3)],
        style=MiuixTheme.textStyles.footnote1,modifier=Modifier.padding(horizontal=12.dp))
    for (row in listOf(listOf(0 to "Default",1 to "Solid"),listOf(2 to "Gradient",3 to "Image"))) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for ((value,label) in row) Button(onClick={update { it.setGestureBackgroundMode(value) }},modifier=Modifier.weight(1f)) { Text(label) }
        }
    }
    if(s.gestureBackgroundMode==1 || s.gestureBackgroundMode==2) GestureColorRow(if(s.gestureBackgroundMode==2) "Gradient start" else "Background color",s.gestureBackgroundStart) { picking="start" }
    if(s.gestureBackgroundMode==2) GestureColorRow("Gradient end",s.gestureBackgroundEnd) { picking="end" }
    if(s.gestureBackgroundMode==3) {
        val hasImage=remember(s.ccImageRevision){ControlCenterImageStore.file(context,"gesture").isFile}
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={picker.launch(arrayOf("image/png","image/jpeg","image/gif"))}) { Text(if(hasImage) "Replace image" else "Choose image") }
            if(hasImage) Button(onClick={scope.launch { if(ControlCenterImageStore.remove(context,"gesture"))update { it.refreshCcImages() } }}) { Text("Remove") }
        }
        if(!hasImage) Text("Choose an image; the default background is used until then.",style=MiuixTheme.textStyles.footnote1,modifier=Modifier.padding(horizontal=12.dp))
    }
    IntSlider(value = s.gestureBackOpacity.coerceIn(0, 100), min = 0, max = 100, step = 5,
        title = "Back background opacity", summary = "Adjusts the shape behind the arrows.",
        enabled = s.gestureCustom, valueTextSuffix = "%", onValueChange = { value -> update { it.setGestureBackOpacity(value) } })
}

@Composable
private fun ExperimentalSwitchPreference(checked: Boolean, title: String, summary: String, onCheckedChange: (Boolean) -> Unit) {
    Column {
        SwitchPreference(checked = checked, title = title, summary = summary, onCheckedChange = onCheckedChange)
        Box(Modifier.padding(start = 16.dp, bottom = 10.dp)
            .background(Color(0xffffd45b), RoundedCornerShape(50))) {
            Text("Experimental", color = Color(0xff302300), fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
    }
}

@Composable private fun GestureColorRow(label:String,color:Int,onChoose:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,modifier=Modifier.weight(1f),style=MiuixTheme.textStyles.body1)
        Swatch(color); Spacer(Modifier.width(8.dp))
        Button(onClick=onChoose) { Text("Choose") }
    }
}
