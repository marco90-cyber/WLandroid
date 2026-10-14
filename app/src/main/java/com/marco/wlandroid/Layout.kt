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
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Layout", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Applicazioni",
                color = Soft,
                fontSize = 22.sp,
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
                        .width(cw)
                        .height((ch - 70.dp).coerceAtLeast(120.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(2.dp, Color(0xFF8AB4F8), RoundedCornerShape(14.dp))
                        .clickable { onClose() }
                ) {
                    if (wallpaper != null) {
                        Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Canvas(Modifier.fillMaxSize().clipToBounds()) {
                        val pad = size.width * 0.03f
                        val gap = size.width * 0.015f
                        val cell = (size.width - 2 * pad - (COLUMNS - 1) * gap) / COLUMNS
                        val hdr = cell * 0.3f
                        val headerRows = tiles.filter { it.isHeader }.map { it.row }.toSet()
                        val maxRow = tiles.maxOfOrNull { it.row + it.h } ?: 0
                        val top = FloatArray(maxRow + 1)
                        top[0] = pad
                        for (r in 0 until maxRow) {
                            top[r + 1] = top[r] + (if (r in headerRows) hdr else cell) + gap
                        }
                        for (t in tiles) {
                            if (t.row < 0 || t.col < 0) continue
                            val x = pad + t.col * (cell + gap)
                            val w = t.w * cell + (t.w - 1) * gap
                            val y = top[t.row.coerceIn(0, maxRow)]
                            val b = top[(t.row + t.h).coerceIn(0, maxRow)] - gap
                            val c = t.color?.let { Color(it) } ?: if (t.isHeader) Color(0xFF2E2E2E) else accent
                            drawRect(c, Offset(x, y), Size(w, (b - y).coerceAtLeast(1f)))
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
