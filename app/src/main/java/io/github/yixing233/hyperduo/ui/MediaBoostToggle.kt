// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.preference.SwitchPreference

internal object MediaBoostSettings {
 private const val PREFS="media_boost_settings"
 private val uri=Uri.parse("content://io.github.lxrylex.hyperlock.controlcenterimages/volume_boost")
 fun isEnabled(context: Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("enabled",false)
 fun setEnabled(context: Context,value:Boolean) {
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("enabled",value).apply()
  context.contentResolver.notifyChange(uri,null)
 }
}
@Composable internal fun MediaBoostToggle() {
 val context=LocalContext.current
 var enabled by remember { mutableStateOf(MediaBoostSettings.isEnabled(context)) }
 SwitchPreference(checked=enabled,title="Media volume boost",
  summary="Keep dragging up at maximum volume for up to 200%. Loud sound may distort.",
  onCheckedChange={enabled=it;MediaBoostSettings.setEnabled(context,it)})
}
