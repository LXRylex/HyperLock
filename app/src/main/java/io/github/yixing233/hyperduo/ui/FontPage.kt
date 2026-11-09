// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FontPage(statusFontPath: String, statusBusy: Boolean, systemBusy: Boolean,
                      systemSelected: Boolean, onBack: () -> Unit, onChooseSystem: () -> Unit,
                      onReapply: () -> Unit, onRestore: () -> Unit, onChooseStatus: () -> Unit,
                      onClearStatus: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", style = MiuixTheme.textStyles.title1)
            }
            Text("Fonts", modifier = Modifier.weight(1f).padding(start = 8.dp), style = MiuixTheme.textStyles.title3)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("System font", style = MiuixTheme.textStyles.title3, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(title = "Choose system font", summary = if (systemBusy) "Applying…" else "Apply a font to HyperOS · root required", enabled = !systemBusy, onClick = onChooseSystem)
                    if (systemSelected) {
                        ArrowPreference(title = "Reapply system font", summary = "Use your selected font again", enabled = !systemBusy, onClick = onReapply)
                        ArrowPreference(title = "Restore previous system font", summary = "Restore the previous Themes font", enabled = !systemBusy, onClick = onRestore)
                    }
                }
            }
            Text("Status bar font", style = MiuixTheme.textStyles.title3, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(title = "Choose status bar font", summary = when {
                        statusBusy -> "Installing…"
                        statusFontPath.isEmpty() -> "Font for custom battery text"
                        else -> java.io.File(statusFontPath).name
                    }, enabled = !statusBusy, onClick = onChooseStatus)
                    if (statusFontPath.isNotEmpty()) {
                        ArrowPreference(title = "Reset status bar font", summary = "Use the system font for battery text", enabled = !statusBusy, onClick = onClearStatus)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
