package com.marco.wlandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AccentPalette = listOf(
    0xFFA4C400, 0xFF60A917, 0xFF00ABA9, 0xFF1BA1E2,
    0xFF0050EF, 0xFF6A00FF, 0xFFAA00FF, 0xFFD80073,
    0xFFE51400, 0xFFFA6800, 0xFFF0A30A, 0xFF647687
).map { Color(it) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(
    apps: List<AppInfo>,
    accent: Color,
    fg: Color,
    onOpen: (AppInfo) -> Unit,
    onPin: (AppInfo) -> Unit,
    onSettings: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    val groups = remember(apps) {
        apps.groupBy { a ->
            val c = a.label.firstOrNull()?.uppercaseChar()
            if (c != null && c.isLetter()) c else '#'
        }.toSortedMap()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Apps", color = fg, fontSize = 34.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "⋮",
                        color = fg,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .clickable { onSettings() }
                            .padding(horizontal = 12.dp)
                    )
                }
            }
            groups.forEach { (letter, list) ->
                item(key = "h$letter") {
                    Box(
                        Modifier
                            .padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                            .size(44.dp)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(letter.toString(), color = Color.White, fontSize = 22.sp)
                    }
                }
                items(list, key = { it.key }) { app ->
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpen(app) },
                                    onLongClick = { menuFor = app.key }
                                )
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(app.icon, app.label, Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(app.label, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Light)
                        }
                        DropdownMenu(expanded = menuFor == app.key, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(
                                text = { Text("Aggiungi alla Start") },
                                onClick = { menuFor = null; onPin(app) }
                            )
                            DropdownMenuItem(
                                text = { Text("Info app") },
                                onClick = { menuFor = null; openAppInfo(ctx, app.pkg) }
                            )
                            DropdownMenuItem(
                                text = { Text("Disinstalla") },
                                onClick = { menuFor = null; uninstall(ctx, app.pkg) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    accent: Color,
    dark: Boolean,
    onAccent: (Color) -> Unit,
    onDark: (Boolean) -> Unit,
    onNotifAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Impostazioni") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Colore tile")
                Spacer(Modifier.size(8.dp))
                AccentPalette.chunked(6).forEach { row ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier
                                    .padding(end = 6.dp)
                                    .size(34.dp)
                                    .background(c)
                                    .then(if (c == accent) Modifier.border(3.dp, Color.White) else Modifier)
                                    .clickable { onAccent(c) }
                            )
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tema scuro", Modifier.weight(1f))
                    Switch(checked = dark, onCheckedChange = onDark)
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onNotifAccess) { Text("Accesso alle notifiche") }
            }
        }
    )
}

private fun openAppInfo(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

private fun uninstall(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

@Composable
fun AppPickerDialog(apps: List<AppInfo>, onPick: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        title = { Text("Aggiungi app") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.key }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(app.icon, app.label, Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, fontSize = 16.sp)
                    }
                }
            }
        }
    )
}
