// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.yixing233.hyperduo.ControlCenterImagePolicy
import io.github.yixing233.hyperduo.AnimatedAssetFormat
import io.github.yixing233.hyperduo.TrioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun showImageImportError(context: Context) {
    android.widget.Toast.makeText(context, "Could not use this image. GIFs must be at most 16 MB and 2048 × 2048 pixels.", android.widget.Toast.LENGTH_LONG).show()
}

internal object ControlCenterImageStore {
    private const val LIMIT=16*1024*1024
    fun file(context:Context,slot:String):File {
        require(ControlCenterImagePolicy.validSlot(slot))
        return File(File(context.filesDir,"cc-images"),"$slot.png")
    }
    suspend fun decode(context:Context,uri:Uri):Bitmap?=withContext(Dispatchers.IO) {
        try {
            val data=context.contentResolver.openInputStream(uri)?.use { input ->
                val out=ByteArrayOutputStream();val buf=ByteArray(16384);var count=0
                while(true){val n=input.read(buf);if(n<0)break;count+=n;if(count>LIMIT)throw IllegalArgumentException("Image too large");out.write(buf,0,n)}
                out.toByteArray()
            } ?: return@withContext null
            if(data.size>=6 && data[0]==71.toByte() && data[1]==73.toByte() && data[2]==70.toByte()) {
                if(data.size>16*1024*1024-24)return@withContext null
                val movie=android.graphics.Movie.decodeByteArray(data,0,data.size) ?: return@withContext null
                if(movie.width() !in 1..2048 || movie.height() !in 1..2048)return@withContext null
                val scale=min(1f,1024f/max(movie.width(),movie.height()))
                val frame=Bitmap.createBitmap(max(1,(movie.width()*scale).roundToInt()),max(1,(movie.height()*scale).roundToInt()),Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(frame).apply { scale(scale,scale);movie.setTime(0);movie.draw(this,0f,0f) }
                return@withContext frame
            }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(data,0,data.size,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>32768 || bounds.outHeight>32768)return@withContext null
            var sample=1
            while(bounds.outWidth/sample>1024 || bounds.outHeight/sample>1024)sample*=2
            BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply { inSampleSize=sample })
        } catch(t:Throwable){android.util.Log.w("HyperDuo","Control Center image import failed",t);null}
    }
    suspend fun save(context:Context,slot:String,bitmap:Bitmap,left:Int,top:Int,width:Int,height:Int):Boolean=withContext(Dispatchers.IO) {
        try {
            val cropped=Bitmap.createBitmap(bitmap,left,top,width,height)
            val target=file(context,slot);target.parentFile?.mkdirs();val atomic=AtomicFile(target);val stream=atomic.startWrite()
            try { if(!cropped.compress(Bitmap.CompressFormat.PNG,100,stream))throw IllegalStateException("Cannot encode image");atomic.finishWrite(stream) }
            catch(t:Throwable){atomic.failWrite(stream);throw t}
            finally {if(cropped !== bitmap)cropped.recycle()}
            true
        }catch(t:Throwable){android.util.Log.w("HyperDuo","Control Center crop save failed",t);false}
    }
    suspend fun saveSelected(context:Context,slot:String,uri:Uri?,bitmap:Bitmap,left:Int,top:Int,width:Int,height:Int):Boolean {
        if(uri!=null) {
            val bytes=withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(16384)
                    while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=LIMIT);out.write(buffer,0,n)}
                    out.toByteArray()
                }
            }.getOrNull() }
            if(bytes!=null && bytes.size>=6 && bytes[0]==71.toByte() && bytes[1]==73.toByte() && bytes[2]==70.toByte()) {
                return withContext(Dispatchers.IO) { runCatching {
                    val movie=android.graphics.Movie.decodeByteArray(bytes,0,bytes.size) ?: error("Invalid GIF")
                    require(movie.width() in 1..2048 && movie.height() in 1..2048)
                    val data=AnimatedAssetFormat.pack(bytes,bitmap.width,bitmap.height,left,top,width,height)
                    val target=file(context,slot);target.parentFile?.mkdirs();val atomic=AtomicFile(target);val stream=atomic.startWrite()
                    try {stream.write(data);atomic.finishWrite(stream)} catch(t:Throwable){atomic.failWrite(stream);throw t}
                    true
                }.getOrElse { android.util.Log.w("HyperDuo","GIF import failed",it);false } }
            }
            if(bytes==null)return false
        }
        return save(context,slot,bitmap,left,top,width,height)
    }
    suspend fun remove(context:Context,slot:String):Boolean=withContext(Dispatchers.IO){try {val f=file(context,slot);!f.exists() || f.delete()}catch(t:Throwable){false}}
}

@Composable internal fun ControlCenterImagesControls(s:TrioSettings,update:((SettingsRepository)->Unit)->Unit,section:String) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var slot by rememberSaveable { mutableStateOf("") }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val picked=slot
        if(uri!=null && ControlCenterImagePolicy.validSlot(picked))scope.launch {
            cropUri=uri; cropBitmap=ControlCenterImageStore.decode(context,uri)
            if(cropBitmap==null)showImageImportError(context)
        }
    }
    var colorRole by remember { mutableStateOf("") }
    if(colorRole.isNotEmpty()) {
        val active=colorRole.endsWith("active") && !colorRole.endsWith("inactive")
        val textRole=colorRole.startsWith("text")
        val selected=if(textRole) { if(active)s.ccTextActiveColor else s.ccTextInactiveColor } else { if(active)s.ccIconActiveColor else s.ccIconInactiveColor }
        OverlayDialog(show=true,title=(if(active) "Active " else "Inactive ")+(if(textRole) "card text color" else "icon color"),onDismissRequest={colorRole=""}) {
            ColorPalette(color=Color(selected),onColorChanged={ c ->
                val value=c.toArgb()
                if(textRole) { if(active)update { it.setCcTextActiveColor(value) } else update { it.setCcTextInactiveColor(value) } }
                else { if(active)update { it.setCcIconActiveColor(value) } else update { it.setCcIconInactiveColor(value) } }
            })
        }
    }
    cropBitmap?.let { bitmap ->
        ImageCropDialog(bitmap,slot,onCancel={cropBitmap=null},onSave={left,top,width,height ->
            val picked=slot;cropBitmap=null
            scope.launch {
                if(ControlCenterImageStore.saveSelected(context,picked,cropUri,bitmap,left,top,width,height))update { it.refreshCcImages() } else showImageImportError(context)
            }
        })
    }
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        if(section=="layout") {
        SwitchPreference(checked=s.ccBluetoothPill,title="Large Bluetooth and airplane tiles",
            summary="Adds Bluetooth and airplane pills below Wi-Fi and mobile data.",onCheckedChange={ v->update { it.setCcBluetoothPill(v) } })
        }
        if(section=="icons") {
        SwitchPreference(checked=s.ccIconColorsEnabled,title="Custom Control Center icon colors",
            summary="Colors icons, sliders and music controls. Light active icons use a subtle active backdrop.",onCheckedChange={ v->update { it.setCcIconColorsEnabled(v) } })
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Active",modifier=Modifier.weight(1f));Swatch(s.ccIconActiveColor)
            CompactImageAction("Choose") { colorRole="icon_active" }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Inactive",modifier=Modifier.weight(1f));Swatch(s.ccIconInactiveColor)
            CompactImageAction("Choose") { colorRole="icon_inactive" }
        }
        }
        if(section=="text") {
        SwitchPreference(checked=s.ccTextColorsEnabled,title="Custom card text colors",
            summary="Colors the title and status on Wi-Fi, mobile data, Bluetooth and airplane cards.",onCheckedChange={ v->update { it.setCcTextColorsEnabled(v) } })
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Active text",modifier=Modifier.weight(1f));Swatch(s.ccTextActiveColor)
            CompactImageAction("Choose") { colorRole="text_active" }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Inactive text",modifier=Modifier.weight(1f));Swatch(s.ccTextInactiveColor)
            CompactImageAction("Choose") { colorRole="text_inactive" }
        }
        }
        if(section=="images") {
        Text("Choose a photo for each item.",style=MiuixTheme.textStyles.footnote1)
        SwitchPreference(checked=s.ccImagesEnabled,title="Custom backgrounds",onCheckedChange={ v->update { it.setCcImagesEnabled(v) } })
        IntSlider(value=s.ccImageBrightness.coerceIn(0,100),min=0,max=100,step=5,title="Image brightness",
            summary="Adjusts all Control Center images.",enabled=s.ccImagesEnabled,valueTextSuffix="%",
            onValueChange={ v->update { it.setCcImageBrightness(v) } })
        for((key,label) in listOf("wifi" to "Wi-Fi", "mobile" to "Mobile data", "bluetooth" to "Bluetooth", "airplane" to "Airplane mode", "music" to "Music", "brightness" to "Brightness slider", "volume" to "Volume slider")) {
            val hasImage=remember(s.ccImageRevision,key){ControlCenterImageStore.file(context,key).isFile}
            Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=3.dp)) {
                Text(label,style=MiuixTheme.textStyles.body1)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    CompactImageAction(if(hasImage) "Replace" else "Choose") {slot=key;picker.launch(arrayOf("image/png","image/jpeg","image/gif"))}
                    if(hasImage)CompactImageAction("Remove") {scope.launch { if(ControlCenterImageStore.remove(context,key))update { it.refreshCcImages() } }}
                }
            }
        }
        }
    }
}

@Composable private fun CompactImageAction(label:String,onClick:()->Unit) {
    Box(Modifier.height(48.dp).clickable(onClick=onClick).padding(vertical=4.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxHeight().background(MiuixTheme.colorScheme.onSurface.copy(alpha=0.18f),RoundedCornerShape(12.dp)).padding(horizontal=14.dp),contentAlignment=Alignment.Center) {
            Text(label,color=MiuixTheme.colorScheme.onSurface,style=MiuixTheme.textStyles.body1)
        }
    }
}

/** The saved bitmap is the selected crop, so SystemUI needs no extra image permissions. */
@Composable internal fun ImageCropDialog(bitmap:Bitmap,slot:String,onCancel:()->Unit,onSave:(Int,Int,Int,Int)->Unit) {
    val ratio=when(slot){"wifi","mobile","bluetooth","airplane"->2.2f;"brightness","volume"->0.46f;"gesture"->0.30f;"battery_warning"->1.17f;else->1f}
    var size by remember(bitmap){mutableStateOf(IntSize.Zero)}
    var zoom by remember(bitmap){mutableFloatStateOf(1f)}
    var panX by remember(bitmap){mutableFloatStateOf(0f)}
    var panY by remember(bitmap){mutableFloatStateOf(0f)}
    fun clampPan(w:Float,h:Float,z:Float,x:Float,y:Float):Pair<Float,Float>{
        val scale=max(w/bitmap.width,h/bitmap.height)*z
        return x.coerceIn(-max(0f,(bitmap.width*scale-w)/2),max(0f,(bitmap.width*scale-w)/2)) to
            y.coerceIn(-max(0f,(bitmap.height*scale-h)/2),max(0f,(bitmap.height*scale-h)/2))
    }
    Dialog(onDismissRequest=onCancel) {
        Column(Modifier.fillMaxWidth().background(Color(0xff202126),RoundedCornerShape(20.dp)).padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Adjust image",color=Color.White)
            Text("Drag to move, pinch to zoom",color=Color.White,style=MiuixTheme.textStyles.footnote1)
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                val h=min(maxWidth.value/ratio,320f).dp
                Canvas(Modifier.width(h*ratio).height(h).clipToBounds().background(Color.Black).onSizeChanged{size=it}.pointerInput(bitmap){
                    detectTransformGestures { _,pan,gestureZoom,_ ->
                        val z=(zoom*gestureZoom).coerceIn(1f,5f)
                        val p=clampPan(size.width.toFloat(),size.height.toFloat(),z,panX+pan.x,panY+pan.y)
                        zoom=z;panX=p.first;panY=p.second
                    }
                }) {
                    val scale=max(size.width.toFloat()/bitmap.width,size.height.toFloat()/bitmap.height)*zoom
                    val x=(size.width-bitmap.width*scale)/2f+panX;val y=(size.height-bitmap.height*scale)/2f+panY
                    drawContext.canvas.nativeCanvas.drawBitmap(bitmap,null,android.graphics.RectF(x,y,x+bitmap.width*scale,y+bitmap.height*scale),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                TextButton(text="Cancel",onClick=onCancel)
                TextButton(text="Apply",onClick={
                    if(size.width>0 && size.height>0){
                        val scale=max(size.width.toFloat()/bitmap.width,size.height.toFloat()/bitmap.height)*zoom
                        val width=(size.width/scale).roundToInt().coerceIn(1,bitmap.width)
                        val height=(size.height/scale).roundToInt().coerceIn(1,bitmap.height)
                        val left=((bitmap.width-width)/2f-panX/scale).roundToInt().coerceIn(0,bitmap.width-width)
                        val top=((bitmap.height-height)/2f-panY/scale).roundToInt().coerceIn(0,bitmap.height-height)
                        onSave(left,top,width,height)
                    }
                })
            }
        }
    }
}

