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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch

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
    // la data di installazione si legge solo se serve (ordinamento "più recenti") e fuori dal thread principale
    val installed by androidx.compose.runtime.produceState(emptyMap<String, Long>(), apps, sortMode) {
        if (sortMode == 2) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                apps.associate { a ->
                    a.pkg to try {
                        ctx.packageManager.getPackageInfo(a.pkg, 0).firstInstallTime
                    } catch (e: Exception) {
                        0L
                    }
                }
            }
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(searching) {
        searching = false
        query = ""
    }
    val shown = remember(apps, query, sortMode, installed) {
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
            // ingresso a cascata (turnstile) delle prime app quando si apre la lista
            val idx = remember(app.key) { shown.indexOfFirst { it.key == app.key } }
            val animIn = remember(app.key) { idx in 0..13 && !listState.isScrollInProgress && listState.firstVisibleItemIndex < 2 }
            val ent = remember(app.key) { androidx.compose.animation.core.Animatable(if (animIn) 0f else 1f) }
            androidx.compose.runtime.LaunchedEffect(app.key) {
                if (animIn) {
                    kotlinx.coroutines.delay(idx * 26L)
                    ent.animateTo(1f, androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                }
            }
            Box(
                Modifier.graphicsLayer {
                    val e = ent.value
                    alpha = e
                    cameraDistance = 14f * this.density
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    rotationY = (1f - e) * 55f
                }
            ) {
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
            val letterOf = { a: AppInfo ->
                val c = a.label.trim().firstOrNull()?.uppercaseChar()
                if (c != null && c in 'A'..'Z') c else '#'
            }
            val groups = remember(shown, sortMode) {
                if (sortMode == 2) null else shown.groupBy(letterOf)
            }
            // posizione di ogni lettera nella lista, per la "jump list"
            val letterIndex = remember(groups) {
                val m = HashMap<Char, Int>()
                var idx = 0
                groups?.forEach { (ch, l) -> m[ch] = idx; idx += 1 + l.size }
                m
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp, end = if (groups != null) 30.dp else 0.dp)
            ) {
                if (groups == null) {
                    items(shown, key = { it.key }) { app -> cell(app) }
                } else {
                    groups.forEach { (ch, list) ->
                        item(key = "h$ch") {
                            Box(
                                Modifier
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                                    .size(46.dp)
                                    .border(2.dp, accent)
                                    .clickable { jump = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ch.toString(), color = accent, fontSize = 26.sp)
                            }
                        }
                        items(list, key = { it.key }) { app -> cell(app) }
                    }
                }
            }
            if (groups != null) {
                val allLetters = remember { ('A'..'Z').toList() + '#' }
                val current by androidx.compose.runtime.remember(letterIndex) {
                    androidx.compose.runtime.derivedStateOf {
                        val fv = listState.firstVisibleItemIndex
                        letterIndex.entries.filter { it.value <= fv }.maxByOrNull { it.value }?.key
                    }
                }
                var bubble by remember { mutableStateOf<Char?>(null) }
                var lastBubble by remember { mutableStateOf('A') }
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp, top = 8.dp, bottom = 96.dp)
                        .width(30.dp)
                        .fillMaxHeight()
                        .pointerInput(letterIndex) {
                            fun pick(y: Float) {
                                val i = (y / size.height * allLetters.size).toInt().coerceIn(0, allLetters.lastIndex)
                                val target = allLetters.drop(i).firstOrNull { it in letterIndex }
                                    ?: allLetters.take(i).lastOrNull { it in letterIndex }
                                if (target != null) {
                                    bubble = target
                                    lastBubble = target
                                    scope.launch { listState.scrollToItem(letterIndex[target] ?: 0) }
                                }
                            }
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    pick(down.position.y)
                                    down.consume()
                                    do {
                                        val ev = awaitPointerEvent()
                                        ev.changes.forEach { if (it.pressed) { pick(it.position.y); it.consume() } }
                                    } while (ev.changes.any { it.pressed })
                                    bubble = null
                                }
                            }
                        },
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    allLetters.forEach { ch ->
                        val on = ch == current
                        val sc by androidx.compose.animation.core.animateFloatAsState(
                            if (on) 1.5f else 1f,
                            androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f),
                            label = "letter"
                        )
                        Text(
                            ch.toString(),
                            color = if (on) accent else if (ch in letterIndex) fg.copy(alpha = 0.85f) else fg.copy(alpha = 0.25f),
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc }
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = bubble != null,
                    modifier = Modifier.align(Alignment.Center),
                    enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.55f)) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                ) {
                    Box(Modifier.size(110.dp).background(accent), contentAlignment = Alignment.Center) {
                        Text(lastBubble.toString(), color = Color.White, fontSize = 64.sp)
                    }
                }
            }
            if (jump) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { jump = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.92f))
                            .clickable { jump = false },
                        contentAlignment = Alignment.Center
                    ) {
                        val letters = ('A'..'Z').toList() + '#'
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            letters.chunked(4).forEach { row ->
                                Row {
                                    row.forEach { ch ->
                                        val idx = letterIndex[ch]
                                        Box(
                                            Modifier
                                                .padding(5.dp)
                                                .size(68.dp)
                                                .background(if (idx != null) accent else Color(0xFF2B2B2B))
                                                .then(
                                                    if (idx != null) Modifier.clickable {
                                                        jump = false
                                                        scope.launch { listState.scrollToItem(idx) }
                                                    } else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                ch.toString(),
                                                color = if (idx != null) Color.White else Color(0xFF6B6B6B),
                                                fontSize = 28.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
