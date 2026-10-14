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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
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
    grid: Boolean,
    onGrid: (Boolean) -> Unit,
    onSettings: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(0) } // 0 A-Z, 1 Z-A, 2 più recenti
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val installed = remember(apps) {
        apps.associate { a ->
            a.pkg to try {
                ctx.packageManager.getPackageInfo(a.pkg, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
        }
    }
    androidx.activity.compose.BackHandler(searching) {
        searching = false
        query = ""
    }
    val shown = remember(apps, query, sortMode) {
        val f = if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        when (sortMode) {
            1 -> f.sortedByDescending { it.label.lowercase() }
            2 -> f.sortedByDescending { installed[it.pkg] ?: 0L }
            else -> f.sortedBy { it.label.lowercase() }
        }
    }
    val barBg = Color(0xFF15171C)
    val barIcon = Color(0xFFE3E3E8)

    Box(Modifier.fillMaxSize()) {
        val cell: @Composable (AppInfo) -> Unit = { app ->
            Box {
                if (grid) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpen(app) },
                                onLongClick = { menuFor = app.key }
                            )
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(app.icon, app.label, Modifier.size(56.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label,
                            color = fg,
                            fontSize = 12.sp,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                } else {
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
                        Image(app.icon, app.label, Modifier.size(48.dp))
                        Spacer(Modifier.width(24.dp))
                        Text(app.label, color = fg, fontSize = 20.sp)
                    }
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
        if (grid) {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(84.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp
                )
            ) {
                gridItems(shown, key = { it.key }) { app -> cell(app) }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                items(shown, key = { it.key }) { app -> cell(app) }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                .background(barBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searching) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { searching = false; query = "" },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, null, tint = barIcon
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = barIcon, fontSize = 18.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF8AB4F8)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .focusRequester(focus)
                )
                androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                Spacer(Modifier.weight(1f))
                Box {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable { sortMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.Sort, "Ordina", tint = barIcon
                        )
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("Nome A → Z", "Nome Z → A", "Più recenti").forEachIndexed { i, l ->
                            DropdownMenuItem(
                                text = { Text(l, fontWeight = if (i == sortMode) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortMode = i; sortMenu = false }
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { onGrid(!grid) },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        if (grid) androidx.compose.material.icons.Icons.Outlined.ViewList
                        else androidx.compose.material.icons.Icons.Outlined.GridView,
                        if (grid) "Vista a lista" else "Vista a griglia",
                        tint = barIcon
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { onSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Settings, "Opzioni", tint = barIcon
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { searching = true },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Search, "Cerca", tint = barIcon
                    )
                }
                Spacer(Modifier.width(8.dp))
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
    onResetLayout: () -> Unit,
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
                TextButton(onClick = onResetLayout) { Text("Ripristina Start predefinita") }
            }
        }
    )
}

fun openAppInfo(ctx: Context, pkg: String) {
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
