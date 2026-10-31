package com.marco.wlandroid

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val OptBg @Composable get() = MaterialTheme.colorScheme.surface
private val OptCircle @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val OptText @Composable get() = MaterialTheme.colorScheme.onSurface
private val OptSoft @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val OptAccent @Composable get() = MaterialTheme.colorScheme.primary

@Composable
private fun OptLabel(text: String) {
    Text(text, color = OptAccent, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
private fun OptSlider(title: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = OptText, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(value, color = OptSoft, fontSize = 15.sp)
        }
        Slider(value = v, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Piccola anteprima di un effetto delle tile, su sfondo o su un gradiente. */
@Composable
private fun FxPreview(effect: Int, strength: Float, accent: Color, wallBmp: ImageBitmap?, selected: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(88.dp)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) OptAccent else MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        if (wallBmp != null) {
            androidx.compose.foundation.Image(
                wallBmp, null, Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1B2A49), Color(0xFF8E2DE2), Color(0xFFFF6A3D)))
                )
            )
        }
        val tileShape = RoundedCornerShape(8.dp)
        Box(Modifier.align(Alignment.Center).size(56.dp).clip(tileShape)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalWall provides null) {
                TileFxLayer(effect, strength, accent, tileShape, 8.dp)
            }
        }
    }
}

fun isDefaultHome(ctx: Context): Boolean = try {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == ctx.packageName
} catch (e: Exception) {
    false
}

fun chooseDefaultHome(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun OptRow(icon: ImageVector, label: String, sub: String? = null, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (GlassUi.on) 0.6f else 1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = OptText, fontSize = 20.sp)
            if (sub != null) Text(sub, color = OptSoft, fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
fun HomeOptionsScreen(
    startPage: String? = null,
    fx: FxSettings,
    wall: WallCtl,
    onPickWall: () -> Unit,
    accent: Color,
    dark: Boolean,
    locked: Boolean,
    pinch: Boolean,
    rounded: Boolean,
    radius: Int,
    orientation: Int,
    onOrientation: (Int) -> Unit,
    appGrid: Boolean,
    onAppGrid: (Boolean) -> Unit,
    landscapeFill: Boolean,
    onLandscapeFill: (Boolean) -> Unit,
    onRounded: (Boolean) -> Unit,
    onRadius: (Int) -> Unit,
    isDefault: Boolean,
    onAccent: (Color) -> Unit,
    onDark: (Boolean) -> Unit,
    onLocked: (Boolean) -> Unit,
    onPinch: (Boolean) -> Unit,
    onSetDefault: () -> Unit,
    onNotifAccess: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    var page by remember { mutableStateOf(startPage) }
    var orientDialog by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    BackHandler(true) { if (page != null) page = null else onClose() }
    val title = when (page) {
        "ui" -> "Comportamento e interfaccia"
        "live" -> "Tile dinamica"
        "style" -> "Dimensioni e stile"
        "wall" -> "Sfondo"
        "fx" -> "Effetti tile"
        "gest" -> "Tasti e gesture"
        "apps" -> "Cassetto app"
        "info" -> "Informazioni"
        else -> "Opzioni home"
    }
    if (orientDialog) {
        Sheet("ORIENTAMENTO SCHERMO", { orientDialog = false }) {
            Spacer(Modifier.height(8.dp))
            listOf("Verticale", "Orizzontale", "Predefinito di sistema").forEachIndexed { i, label ->
                val value = when (i) { 0 -> 1; 1 -> 2; else -> 0 }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOrientation(value); orientDialog = false }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = OptText, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    RadioButton(selected = orientation == value, onClick = { onOrientation(value); orientDialog = false })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "In orizzontale",
                color = OptAccent,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            listOf("Tutto schermo" to true, "Spazi neri ai lati" to false).forEach { (label, v) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onLandscapeFill(v) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = OptText, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    RadioButton(selected = landscapeFill == v, onClick = { onLandscapeFill(v) })
                }
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(if (GlassUi.on) OptBg.copy(alpha = if (wall.bitmap != null) 0.5f else 0.9f) else OptBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        Box(
            Modifier
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .clickable { if (page != null) page = null else onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = OptText)
        }
        Text(
            title,
            color = OptText,
            fontSize = 34.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            when (page) {
                null -> {
                    if (!isDefault) {
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable(onClick = onSetDefault)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                            Spacer(Modifier.width(20.dp))
                            Text("Imposta come Home predefinita", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 19.sp)
                        }
                    } else {
                        OptRow(Icons.Outlined.Warning, "Home predefinita", "WLandroid è la tua home attuale", onSetDefault)
                    }
                    OptRow(Icons.Outlined.Tune, "Comportamento e interfaccia", null, { page = "ui" })
                    OptRow(Icons.Outlined.Notifications, "Tile dinamica", null, { page = "live" })
                    OptRow(Icons.Outlined.Palette, "Dimensioni e stile", null, { page = "style" })
                    OptRow(Icons.Outlined.Wallpaper, "Sfondo", "Immagine, ritaglio, colori, parallasse", { page = "wall" })
                    OptRow(Icons.Outlined.AutoAwesome, "Effetti tile", "Trasparenza, vetro, liquid glass e altri", { page = "fx" })
                    OptRow(Icons.Outlined.Apps, "Cassetto app", null, { page = "apps" })
                    OptRow(Icons.Outlined.Gesture, "Tasti e gesture", null, { page = "gest" })
                    OptRow(Icons.Outlined.Info, "Informazioni", null, { page = "info" })
                }
                "ui" -> {
                    OptRow(Icons.Outlined.DarkMode, "Tema scuro", null, { onDark(!dark) }) {
                        Switch(checked = dark, onCheckedChange = onDark)
                    }
                    OptRow(
                        Icons.Outlined.ScreenRotation,
                        "Orientamento schermo",
                        listOf("Predefinito di sistema", "Verticale", "Orizzontale")[orientation.coerceIn(0, 2)],
                        { orientDialog = true }
                    )
                    OptRow(Icons.Outlined.Lock, "Blocca layout", "Impedisce di modificare le tile", { onLocked(!locked) }) {
                        Switch(checked = locked, onCheckedChange = onLocked)
                    }
                }
                "live" -> {
                    OptRow(
                        Icons.Outlined.Notifications,
                        "Accesso alle notifiche",
                        "Serve alle tile Notifiche e ai badge sulle app",
                        onNotifAccess
                    )
                }
                "wall" -> {
                    androidx.compose.runtime.LaunchedEffect(wall.error) {
                        if (wall.error > 0) android.widget.Toast.makeText(
                            ctx, "Immagine non disponibile. Android a volte non lascia leggere il suo sfondo: sceglilo dalla galleria",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    val bm = wall.bitmap
                    if (bm != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            bm, contentDescription = "Sfondo attuale",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp, 250.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OptLabel("Scegli l'immagine")
                    if (bm != null) OptRow(Icons.Outlined.Tune, "Modifica lo sfondo attuale", "Ritaglia, ruota, regola luce e colori, filtri", { wall.editCurrent() })
                    OptRow(Icons.Outlined.Wallpaper, if (wall.busy) "Preparo lo sfondo\u2026" else "Dalla galleria", "Una tua foto: poi la ritagli e la regoli", onPickWall)
                    OptRow(Icons.Outlined.Palette, "Importa lo sfondo di Android", "Copia quello che hai adesso, se il sistema lo permette", { wall.importSystem() })
                    if (bm != null) OptRow(Icons.Outlined.Delete, "Rimuovi sfondo", null, { wall.remove() })
                    Spacer(Modifier.height(8.dp))
                    OptLabel("Sfondi inclusi")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BUILTIN_WALLS.forEach { (k, l) ->
                            val thumb = remember(k) { builtinWall(k, 90, 180).asImageBitmap() }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { wall.builtin(k) }) {
                                androidx.compose.foundation.Image(
                                    thumb, contentDescription = l,
                                    modifier = Modifier.size(60.dp, 120.dp).clip(RoundedCornerShape(14.dp))
                                )
                                Text(l, color = OptSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    if (bm != null) {
                        Spacer(Modifier.height(12.dp))
                        OptLabel("Effetti")
                        OptSlider("Scurisci", if (fx.wallDim == 0) "No" else "${fx.wallDim}%", fx.wallDim.toFloat(), 0f..60f, 11) {
                            val v = (it / 5f).roundToInt() * 5
                            if (v != fx.wallDim) fx.updateWallDim(v)
                        }
                        OptSlider("Sfoca", if (fx.wallBlur == 0) "No" else "${fx.wallBlur} dp", fx.wallBlur.toFloat(), 0f..24f, 11) {
                            val v = (it / 2f).roundToInt() * 2
                            if (v != fx.wallBlur) fx.updateWallBlur(v)
                        }
                        OptRow(Icons.Outlined.Gesture, "Parallasse", "Lo sfondo scorre piano quando cambi pagina", { fx.updateWallParallax(!fx.wallParallax) }) {
                            Switch(checked = fx.wallParallax, onCheckedChange = { fx.updateWallParallax(it) })
                        }
                        Spacer(Modifier.height(8.dp))
                        OptLabel("Schermata di blocco")
                        OptRow(
                            Icons.Outlined.Lock, "Usa anche sulla schermata di blocco",
                            "Imposta questa immagine come sfondo di blocco di Android",
                            {
                                val ok = wall.toLockScreen()
                                android.widget.Toast.makeText(ctx, if (ok) "Sfondo di blocco impostato" else "Non riuscito", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                "fx" -> {
                    OptLabel("Effetto")
                    Fx.NAMES.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            row.forEach { (id, name) ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { fx.updateTileFx(id) }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    FxPreview(id, fx.fxStrength / 100f, accent, wall.bitmap, fx.tileFx == id)
                                    Text(
                                        name,
                                        color = if (fx.tileFx == id) OptAccent else OptText,
                                        fontSize = 13.sp,
                                        fontWeight = if (fx.tileFx == id) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (fx.tileFx != Fx.NONE) {
                        Spacer(Modifier.height(8.dp))
                        OptSlider(
                            if (Fx.glassy(fx.tileFx)) "Trasparenza e sfocatura" else "Intensità",
                            "${fx.fxStrength}%", fx.fxStrength.toFloat(), 0f..100f, 19
                        ) {
                            val v = (it / 5f).roundToInt() * 5
                            if (v != fx.fxStrength) fx.updateFxStrength(v)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "L'effetto vale per tutte le tile colorate. Trasparente, Vetro smerigliato e Liquid Glass danno il vetro anche alla barra della lista app e alle finestre, e si vedono meglio con uno sfondo.",
                        color = OptSoft, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    if (wall.bitmap == null && Fx.glassy(fx.tileFx)) {
                        Spacer(Modifier.height(8.dp))
                        OptRow(Icons.Outlined.Wallpaper, "Scegli uno sfondo", "Senza sfondo il vetro è solo una trasparenza", { page = "wall" })
                    }
                }
                "style" -> {
                    Text("Effetto tile", color = OptAccent, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    val cornerDp = if (rounded) listOf(8, 16, 28)[radius.coerceIn(0, 2)] else 0
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(150.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                                Box(
                                    Modifier
                                        .padding(start = 28.dp, top = 28.dp)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(topStart = cornerDp.dp, bottomStart = cornerDp.dp, topEnd = cornerDp.dp, bottomEnd = cornerDp.dp))
                                        .background(accent)
                                )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Anteprima", color = OptText, fontSize = 16.sp)
                        Spacer(Modifier.height(16.dp))
                        Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Arrotondato", color = OptText, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            Switch(checked = rounded, onCheckedChange = onRounded)
                        }
                        if (rounded) {
                            Spacer(Modifier.height(16.dp))
                            Text("Raggio arrotondamento", color = OptText, fontSize = 20.sp, modifier = Modifier.fillMaxWidth())
                            Slider(
                                value = radius.toFloat(),
                                onValueChange = { onRadius(it.roundToInt().coerceIn(0, 2)) },
                                valueRange = 0f..2f,
                                steps = 1
                            )
                            Row(Modifier.fillMaxWidth()) {
                                listOf("Piccola", "Media", "Grande").forEachIndexed { i, l ->
                                    Text(
                                        l,
                                        color = if (i == radius) OptAccent else OptSoft,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        textAlign = if (i == 0) androidx.compose.ui.text.style.TextAlign.Start
                                        else if (i == 1) androidx.compose.ui.text.style.TextAlign.Center
                                        else androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                        }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Colore", color = OptText, fontSize = 20.sp, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        AccentPalette.chunked(6).forEach { row ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                row.forEach { c ->
                                    Box(
                                        Modifier
                                            .padding(end = 8.dp)
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(cornerDp.coerceAtMost(14).dp))
                                            .background(c)
                                            .then(if (c == accent) Modifier.border(3.dp, OptText) else Modifier)
                                            .clickable { onAccent(c) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OptRow(Icons.Outlined.RestartAlt, "Ripristina Start predefinita", null, onReset)
                }
                "apps" -> {
                    listOf("Lista" to false, "Griglia" to true).forEach { (label, v) ->
                        OptRow(
                            if (v) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                            label,
                            if (v) "Icone in griglia con il nome sotto" else "Icone in elenco con il nome a fianco",
                            { onAppGrid(v) }
                        ) { RadioButton(selected = appGrid == v, onClick = { onAppGrid(v) }) }
                    }
                }
                "gest" -> {
                    OptRow(
                        Icons.Outlined.Gesture,
                        "Pizzico per il Layout",
                        "Avvicina due dita sulla Start",
                        { onPinch(!pinch) }
                    ) { Switch(checked = pinch, onCheckedChange = onPinch) }
                }
                "info" -> {
                    val ver = try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    Text(
                        "WLandroid $ver\nLauncher in stile Windows Phone",
                        color = OptText,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    OptRow(
                        Icons.Outlined.SystemUpdate,
                        "Cerca aggiornamenti",
                        "Scarica e installa la nuova versione",
                        { AppUpdate.open(ctx) }
                    )
                }
            }
        }
    }
}
