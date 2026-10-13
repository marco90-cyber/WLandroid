package com.marco.wlandroid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartScreen(
    tiles: List<Tile>,
    apps: Map<String, AppInfo>,
    accent: Color,
    editing: Boolean,
    notifs: Map<String, NotifInfo>,
    notifConnected: Boolean,
    weather: Weather?,
    weatherStatus: String?,
    photos: List<ImageBitmap>,
    widgets: WidgetController,
    onEdit: (Boolean) -> Unit,
    onTiles: (List<Tile>) -> Unit,
    onRemove: (Tile) -> Unit,
    onOpen: (AppInfo) -> Unit,
    onLiveClick: (String) -> Unit,
    onAddLive: (String) -> Unit,
    onAddWidget: () -> Unit
) {
    val visible = tiles.filter {
        it.key.startsWith("live:") || it.key.startsWith("widget:") || apps.containsKey(it.key)
    }
    val packed = remember(visible) { packTiles(visible) }
    val placed = packed.first
    val rows = packed.second
    val tilesNow by rememberUpdatedState(tiles)
    val placedNow by rememberUpdatedState(placed)
    val appsByPkg = remember(apps) { apps.values.associateBy { it.pkg } }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(editing) {
                detectTapGestures(
                    onTap = { if (editing) onEdit(false) },
                    onLongPress = { if (!editing) onEdit(true) }
                )
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

        Column(
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
                        val widgetId = if (t.key.startsWith("widget:")) t.key.removePrefix("widget:").toIntOrNull() else null
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
                                                val to = l.indexOfFirst { it.key == target.tile.key }
                                                if (from >= 0 && to >= 0) {
                                                    val item = l.removeAt(from)
                                                    l.add(to.coerceIn(0, l.size), item)
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
                                onClick = {
                                    if (app != null) onOpen(app) else onLiveClick(t.key)
                                },
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
                            when {
                                t.key == "live:clock" -> ClockTile(t.size)
                                t.key == "live:weather" -> WeatherTile(weather, weatherStatus, t.size)
                                t.key == "live:photos" -> PhotoTile(photos, t.size)
                                t.key == "live:notif" -> NotifSummaryTile(notifConnected, notifs, appsByPkg, t.size)
                                widgetId != null -> WidgetTile(
                                    widgets,
                                    widgetId,
                                    with(density) { r.width.toDp().value.toInt() },
                                    with(density) { r.height.toDp().value.toInt() }
                                )
                                app != null -> AppTile(app, t.size, notifs[app.pkg])
                            }
                            if (editing && widgetId != null) {
                                // intercetta i tocchi, così il widget non li "ruba" al trascinamento
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) awaitPointerEvent()
                                            }
                                        }
                                )
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
                                        .clickable { onRemove(t) }
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
                                            val next = nextSize(t)
                                            onTiles(tilesNow.map { if (it.key == t.key) it.copy(size = next) else it })
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (editing) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    listOf(
                        "＋ Widget" to { onAddWidget() },
                        "＋ Meteo" to { onAddLive("live:weather") },
                        "＋ Foto" to { onAddLive("live:photos") },
                        "＋ Notifiche" to { onAddLive("live:notif") }
                    ).forEach { (label, action) ->
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(accent)
                                .clickable { action() }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
