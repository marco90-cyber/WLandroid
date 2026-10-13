package com.marco.wlandroid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartScreen(
    tiles: List<Tile>,
    apps: Map<String, AppInfo>,
    accent: Color,
    editing: Boolean,
    onEdit: (Boolean) -> Unit,
    onTiles: (List<Tile>) -> Unit,
    onOpen: (AppInfo) -> Unit
) {
    val packed = remember(tiles) { packTiles(tiles) }
    val placed = packed.first
    val rows = packed.second
    val tilesNow by rememberUpdatedState(tiles)
    val placedNow by rememberUpdatedState(placed)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(editing) {
                if (editing) detectTapGestures { onEdit(false) }
            }
    ) {
        val padPx = with(density) { 12.dp.toPx() }
        val gapPx = with(density) { 6.dp.toPx() }
        val cellPx = (with(density) { maxWidth.toPx() } - 2 * padPx - (COLUMNS - 1) * gapPx) / COLUMNS
        val totalHeight = with(density) {
            (2 * padPx + rows * cellPx + (rows - 1).coerceAtLeast(0) * gapPx).toDp()
        }

        fun rectOf(p: Placed): Rect {
            val x = padPx + p.col * (cellPx + gapPx)
            val y = padPx + p.row * (cellPx + gapPx)
            val w = p.tile.size.w * cellPx + (p.tile.size.w - 1) * gapPx
            val h = p.tile.size.h * cellPx + (p.tile.size.h - 1) * gapPx
            return Rect(x, y, x + w, y + h)
        }

        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
            ) {
                for (p in placed) {
                    val t = p.tile
                    key(t.key) {
                        val app = apps[t.key]
                        val isLive = t.key.startsWith("live:")
                        if (!isLive && app == null) return@key
                        val r = rectOf(p)
                        val dragging = dragKey == t.key
                        val off = if (dragging) dragOffset else Offset.Zero

                        val interaction = if (editing) {
                            Modifier.pointerInput(t.key, cellPx) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragKey = t.key
                                        dragOffset = Offset.Zero
                                    },
                                    onDragEnd = {
                                        val me = placedNow.firstOrNull { it.tile.key == t.key }
                                        if (me != null) {
                                            val c = rectOf(me).center + dragOffset
                                            val target = placedNow.firstOrNull {
                                                it.tile.key != t.key && rectOf(it).contains(c)
                                            }
                                            if (target != null) {
                                                val l = tilesNow.toMutableList()
                                                val from = l.indexOfFirst { it.key == t.key }
                                                if (from >= 0) {
                                                    val item = l.removeAt(from)
                                                    l.add(target.idx.coerceIn(0, l.size), item)
                                                    onTiles(l)
                                                }
                                            }
                                        }
                                        dragKey = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        dragKey = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                    }
                                )
                            }
                        } else {
                            Modifier.combinedClickable(
                                onClick = { if (app != null) onOpen(app) },
                                onLongClick = { onEdit(true) }
                            )
                        }

                        Box(
                            Modifier
                                .offset { IntOffset((r.left + off.x).roundToInt(), (r.top + off.y).roundToInt()) }
                                .size(
                                    with(density) { r.width.toDp() },
                                    with(density) { r.height.toDp() }
                                )
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    val s = if (dragging) 1.06f else if (editing) 0.95f else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                                .background(accent)
                                .then(interaction)
                        ) {
                            if (isLive) {
                                ClockTile(t.size)
                            } else if (app != null) {
                                AppTile(app, t.size)
                            }
                            if (editing) {
                                Text(
                                    "✕",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable { onTiles(tilesNow.filter { it.key != t.key }) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                                Text(
                                    "⤢",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable {
                                            val next = TileSize.values()[(t.size.ordinal + 1) % TileSize.values().size]
                                            onTiles(tilesNow.map { if (it.key == t.key) it.copy(size = next) else it })
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppInfo, size: TileSize) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fraction = if (size == TileSize.SMALL) 0.6f else 0.45f
        val iconSize = minOf(maxWidth, maxHeight) * fraction
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .align(Alignment.Center)
                .size(iconSize)
        )
        if (size != TileSize.SMALL) {
            Text(
                app.label,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .fillMaxWidth(0.8f)
            )
        }
    }
}

@Composable
private fun ClockTile(size: TileSize) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(10_000)
        }
    }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
    if (size == TileSize.SMALL) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(time, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Light)
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                time,
                color = Color.White,
                fontSize = if (size == TileSize.WIDE) 54.sp else 34.sp,
                fontWeight = FontWeight.Light
            )
            Text(date, color = Color.White, fontSize = 14.sp, maxLines = 2)
        }
    }
}
