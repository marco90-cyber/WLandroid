package com.marco.wlandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Riconosce il pizzico a due dita (dita che si avvicinano) senza consumare gli eventi. */
fun Modifier.pinchIn(enabled: Boolean, onPinch: () -> Unit): Modifier = pointerInput(enabled) {
    if (enabled) {
        awaitPointerEventScope {
            var start = 0f
            var fired = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val down = ev.changes.filter { it.pressed }
                if (down.size >= 2) {
                    val d = (down[0].position - down[1].position).getDistance()
                    if (start == 0f) {
                        start = d
                        fired = false
                    } else if (!fired && start > 0f && d < start * 0.72f) {
                        fired = true
                        onPinch()
                    }
                } else {
                    start = 0f
                    fired = false
                }
            }
        }
    }
}

private val OverBg = Color(0xFF0E0F13)
private val BarBg = Color(0xFF1E2026)
private val Soft = Color(0xFFA8AAB4)

@Composable
private fun CircleAction(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF2B2E36))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color(0xFF8AB4F8), modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFE3E3E8), fontSize = 12.sp)
    }
}

/** Panoramica "Layout": anteprima della Start, scorciatoie e barra con Opzioni / Sfondo / Modifica. */
@Composable
fun LayoutOverview(
    tiles: List<Tile>,
    accent: Color,
    wallpaper: ImageBitmap?,
    locked: Boolean,
    onClose: () -> Unit,
    onApps: () -> Unit,
    onOptions: () -> Unit,
    onWallpaper: () -> Unit,
    onToggleLock: () -> Unit,
    onReset: () -> Unit,
    onTheme: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OverBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Layout", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            }
            Text(
                "Applicazioni",
                color = Soft,
                fontSize = 24.sp,
                modifier = Modifier.clickable { onApps() }
            )
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val ch = maxHeight
            val cw = (ch * 0.56f).coerceAtMost(maxWidth * 0.72f)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            val a = appear.value
                            scaleX = 1.14f - 0.14f * a
                            scaleY = 1.14f - 0.14f * a
                            alpha = a.coerceIn(0f, 1f)
                        }
                        .width(cw)
                        .height((ch - 70.dp).coerceAtLeast(120.dp))
                        .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = accent, spotColor = accent)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black)
                        .border(2.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
                        .clickable { onClose() }
                ) {
                    if (wallpaper != null) {
                        Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    val shown = resolveAll(tiles)
                    Canvas(Modifier.fillMaxSize().clipToBounds()) {
                        val pad = size.width * 0.04f
                        val gap = size.width * 0.018f
                        val cell = (size.width - 2 * pad - (COLUMNS - 1) * gap) / COLUMNS
                        val hdr = cell * 0.3f
                        val headerRows = shown.filter { it.isHeader }.map { it.row }.toSet()
                        val maxRow = shown.maxOfOrNull { it.row + it.h } ?: 0
                        val top = FloatArray(maxRow + 1)
                        top[0] = pad
                        for (r in 0 until maxRow) {
                            top[r + 1] = top[r] + (if (r in headerRows) hdr else cell) + gap
                        }
                        // se il contenuto è più alto dell'anteprima, lo rimpicciolisce per mostrarlo tutto
                        val contentH = top[maxRow] + pad
                        val sc = if (contentH > size.height) size.height / contentH else 1f
                        withTransform({ scale(sc, sc, Offset(size.width / 2f, 0f)) }) {
                            val rad = CornerRadius(cell * 0.07f, cell * 0.07f)
                            for (t in shown) {
                                if (t.row < 0 || t.col < 0) continue
                                if (t.key == ADD_KEY && locked) continue
                                val x = pad + t.col * (cell + gap)
                                val w = t.w * cell + (t.w - 1) * gap
                                val y = top[t.row.coerceIn(0, maxRow)]
                                val b = top[(t.row + t.h).coerceIn(0, maxRow)] - gap
                                val hh = (b - y).coerceAtLeast(1f)
                                if (t.key == ADD_KEY) {
                                    drawRoundRect(Color.Black, Offset(x, y), Size(w, hh), rad)
                                    val st = cell * 0.05f
                                    drawRoundRect(
                                        Color(0xFFF0A30A), Offset(x + st / 2, y + st / 2), Size(w - st, hh - st), rad,
                                        style = Stroke(st)
                                    )
                                    continue
                                }
                                val c = t.color?.let { Color(it) } ?: if (t.isHeader) Color(0xFF2E2E2E) else accent
                                drawRoundRect(c, Offset(x, y), Size(w, hh), if (t.isHeader) CornerRadius(cell * 0.04f, cell * 0.04f) else rad)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircleAction(Icons.Outlined.Home) { onClose() }
                    CircleAction(Icons.Outlined.Palette) { onTheme() }
                    CircleAction(Icons.Outlined.Delete) { if (!locked) onReset() }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BarBg)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BarItem(Icons.Outlined.Settings, "Opzioni home", Modifier.weight(1f), onOptions)
            BarItem(Icons.Outlined.Wallpaper, "Sfondo", Modifier.weight(1f), onWallpaper)
            BarItem(
                if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                if (locked) "Bloccato" else "Modifica",
                Modifier.weight(1f),
                onToggleLock
            )
        }
    }
}

/** Menu che si apre con la pressione lunga sul tile "+". */
@Composable
fun HomeMenuDialog(
    locked: Boolean,
    onOptions: () -> Unit,
    onSystem: () -> Unit,
    onWallpaper: () -> Unit,
    onLock: () -> Unit,
    onMove: () -> Unit,
    onReset: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    Sheet("MENU", onDismiss) {
        SheetRow(Icons.Outlined.Tune, "Opzioni home", onOptions)
        SheetRow(Icons.Outlined.Settings, "Impostazioni di sistema", onSystem)
        SheetRow(Icons.Outlined.Wallpaper, "Sfondo", onWallpaper)
        SheetRow(if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, if (locked) "Sblocca modifiche" else "Blocca modifiche", onLock)
        if (!locked) {
            SheetRow(Icons.Outlined.OpenWith, "Sposta tile", onMove)
            SheetRow(Icons.Outlined.RestartAlt, "Reimposta", onReset)
        }
        SheetRow(Icons.Outlined.Info, "Informazioni", onInfo)
    }
}

@Composable
fun WallpaperDialog(hasWallpaper: Boolean, onPick: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    Sheet("SFONDO HOME", onDismiss) {
        SheetRow(Icons.Outlined.Image, "Scegli un'immagine", onPick)
        if (hasWallpaper) SheetRow(Icons.Outlined.Delete, "Rimuovi sfondo", onRemove)
    }
}

/** Legge un'immagine ridimensionata (max ~1600x2400) da un Uri. */
fun loadWallpaperBitmap(ctx: Context, uri: Uri): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var s = 1
        while (opts.outWidth / s > 1600 || opts.outHeight / s > 2400) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
    } catch (e: Exception) {
        null
    }
}
