// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import io.github.yixing233.hyperduo.BatteryWarningStyle
import io.github.yixing233.hyperduo.SettingsColorChannels
import io.github.yixing233.hyperduo.TrioSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BatteryWarningControls(s: TrioSettings, update: ((SettingsRepository)->Unit)->Unit) {
    val style=BatteryWarningStyle.parse(s.batteryWarningStyle)
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var role by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf(style.titleText) }
    var customMessage by remember { mutableStateOf(style.messageText) }
    var crop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch { cropUri=uri; crop=ControlCenterImageStore.decode(context,uri);if(crop==null)showImageImportError(context) }
    }
    fun apply(block:(BatteryWarningStyle)->Unit) {
        val next=BatteryWarningStyle.parse(s.batteryWarningStyle);block(next)
        update { it.setBatteryWarningStyle(next.encode()) }
    }
    fun value(name:String)=when(name) { "Background"->style.background; "Got it"->style.gotIt; "Battery saver"->style.saver; "Battery body"->style.batteryBody; else->style.batteryFill }
    fun change(t:BatteryWarningStyle,name:String,color:Int) {when(name) {"Background"->t.background=color;"Got it"->t.gotIt=color;"Battery saver"->t.saver=color;"Battery body"->t.batteryBody=color;else->t.batteryFill=color}}
    if(role.isNotEmpty()) OverlayDialog(show=true,title="$role color",onDismissRequest={role=""}) {
        ColorPalette(color=Color(value(role)),onColorChanged={color ->
            val selected=role;val old=value(selected);val next=(color.toArgb() and 0x00ffffff) or (old and 0xff000000.toInt())
            apply { change(it,selected,next) }
        })
    }
    crop?.let { bitmap ->
        ImageCropDialog(bitmap,"battery_warning",onCancel={crop=null},onSave={left,top,width,height ->
            crop=null;scope.launch { if(ControlCenterImageStore.saveSelected(context,"battery_warning",cropUri,bitmap,left,top,width,height))update { it.refreshCcImages() } else showImageImportError(context) }
        })
    }
    SwitchPreference(checked=style.enabled,title="Customize low battery warning",
        summary="Enable Security scope in Vector / LSPosed and restart the phone.",
        onCheckedChange={value->apply { it.enabled=value }})
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Custom warning text",style=MiuixTheme.textStyles.body1)
        Text("Leave a field empty to use Xiaomi’s original text.",style=MiuixTheme.textStyles.footnote1)
        WarningTextInput(customTitle,{customTitle=it},"Warning title",256)
        WarningTextInput(customMessage,{customMessage=it},"Warning message",512)
        Button(onClick={ apply { it.titleText=customTitle; it.messageText=customMessage } },modifier=Modifier.fillMaxWidth()) { Text("Save text") }
    }
    Button(onClick={
        val intent=android.content.Intent("io.github.lxrylex.hyperlock.action.PREVIEW_BATTERY_WARNING").setPackage("com.miui.securitycenter")
        context.sendOrderedBroadcast(intent,null,object:android.content.BroadcastReceiver(){
            override fun onReceive(c:android.content.Context?, i:android.content.Intent?) {
                if(resultCode!=android.app.Activity.RESULT_OK) android.widget.Toast.makeText(context,"Test warning unavailable. Enable Security scope and restart Security, then try again.",android.widget.Toast.LENGTH_LONG).show()
            }
        },null,android.app.Activity.RESULT_CANCELED,null,null)
    },modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp)) { Text("Show test warning") }
    Box(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
        Column(Modifier.fillMaxWidth().background(Color(style.background)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(50.dp).height(22.dp).clip(RoundedCornerShape(7.dp)).background(Color(style.batteryBody))) {
                    Box(Modifier.width(8.dp).fillMaxHeight().padding(3.dp).background(Color(style.batteryFill),RoundedCornerShape(3.dp)))
                }
                Text("Low battery",color=Color.White)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(style.gotIt)).padding(8.dp),contentAlignment=Alignment.Center){Text("Got it",color=Color.White)}
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(style.saver)).padding(8.dp),contentAlignment=Alignment.Center){Text("Battery saver",color=Color.White)}
            }
        }
    }
    SwitchPreference(checked=style.image,title="Use background image",summary="The background opacity also applies to your image.",onCheckedChange={value->apply { it.image=value }})
    val hasImage=remember(s.ccImageRevision){ControlCenterImageStore.file(context,"battery_warning").isFile}
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={picker.launch(arrayOf("image/png","image/jpeg","image/gif"))}) { Text(if(hasImage) "Replace image" else "Choose image") }
        if(hasImage) Button(onClick={scope.launch { if(ControlCenterImageStore.remove(context,"battery_warning"))update { it.refreshCcImages() } }}) { Text("Remove") }
    }
    listOf("Background","Got it","Battery saver","Battery body","Battery fill").forEach { name ->
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("$name color",modifier=Modifier.weight(1f),style=MiuixTheme.textStyles.body1)
            Swatch(value(name)); Spacer(Modifier.width(8.dp));Button(onClick={role=name}) { Text("Choose") }
        }
        IntSlider(value=SettingsColorChannels.opacity(value(name)),min=0,max=100,title="$name opacity",summary="",enabled=true,valueTextSuffix="%",onValueChange={v -> apply { t->change(t,name,SettingsColorChannels.withOpacity(value(name),v)) }})
    }
}

@Composable
private fun WarningTextInput(value:String,onChange:(String)->Unit,placeholder:String,maxLength:Int) {
    BasicTextField(value=value,onValueChange={ if(it.length<=maxLength)onChange(it) },
        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant).padding(14.dp),
        textStyle=TextStyle(color=MiuixTheme.colorScheme.onSurface,fontSize=16.sp),
        decorationBox={ inner -> Box { if(value.isEmpty()) Text(placeholder,color=MiuixTheme.colorScheme.onSurface.copy(alpha=.55f));inner() } })
}
