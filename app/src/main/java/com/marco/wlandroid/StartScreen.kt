package com.marco.wlandroid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Pallino bianco con icona, usato per le azioni della tile selezionata. */
@Composable
private fun RoundBtn(
    icon: ImageVector,
    tint: Color,
    x: Float,
    y: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .zIndex(5f)
            .size(32.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .then(modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

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
    data: TileData,
    onLiveClick: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenContact: (String) -> Unit,
    onOptions: (Tile) -> Unit,
    onAddMenu: () -> Unit
) {
    val ctx = LocalContext.current
    val visible = tiles.filter { isSpecialKey(it.key) || apps.containsKey(it.key) }
    val packed = remember(visible) { packTiles(visible) }
    val placed = packed.first
    val rows = packed.second
    val hdrCount = packed.third
    val tilesNow by rememberUpdatedState(tiles)
    val placedNow by rememberUpdatedState(placed)
    val appsByPkg = remember(apps) { apps.values.associateBy { it.pkg } }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    val cubeIndex = remember { mutableStateMapOf<String, Int>() }
    fun appsOf(key: String): List<AppInfo> =
        data.extra(key)?.optJSONArray("apps")?.let { a ->
            (0 until a.length()).mapNotNull { apps[a.optString(it)] }
        } ?: emptyList()
    var resizeKey by remember { mutableStateOf<String?>(null) }
    var resizeW by remember { mutableIntStateOf(1) }
    var resizeH by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current

    // tile selezionata (pressione lunga): le altre si oscurano e compaiono i 4 pallini
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(editing) { if (!editing) selected = null }
    fun select(k: String) {
        selected = k
        onEdit(true)
    }
    fun deselect() {
        selected = null
        onEdit(false)
    }
    val selectedTile = placed.firstOrNull { it.tile.key == selected }?.tile
    val selTileNow by rememberUpdatedState(selectedTile)

    openGroup?.let { gk ->
        GroupDialog(
            name = data.extra(gk)?.optString("name").orEmpty(),
            apps = appsOf(gk),
            onOpen = { openGroup = null; onOpen(it) },
            onDismiss = { openGroup = null }
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(selected) {
                detectTapGestures(onTap = { if (selected != null) deselect() })
            }
    ) {
        val padPx = with(density) { 12.dp.toPx() }
        val gapPx = with(density) { 6.dp.toPx() }
        val screenPx = with(density) { maxWidth.toPx() }
        val cellPx = (screenPx - 2 * padPx - (COLUMNS - 1) * gapPx) / COLUMNS
        val hdrPx = with(density) { 26.dp.toPx() }
        val btnPx = with(density) { 32.dp.toPx() }
        val totalHeight = with(density) {
            (2 * padPx + rows * (cellPx + gapPx) + hdrCount * (hdrPx + gapPx) - gapPx)
                .coerceAtLeast(0f).toDp()
        }

        fun rectOf(p: Placed): Rect {
            val x = padPx + p.col * (cellPx + gapPx)
            val y = padPx + p.row * (cellPx + gapPx) + p.hdrBefore * (hdrPx + gapPx)
            if (p.tile.isHeader) {
                val full = COLUMNS * cellPx + (COLUMNS - 1) * gapPx
                return Rect(padPx, y, padPx + full, y + hdrPx)
            }
            val w = p.tile.w * cellPx + (p.tile.w - 1) * gapPx
            val h = p.tile.h * cellPx + (p.tile.h - 1) * gapPx
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
                        val shownApp = if (app != null && t.label != null) app.copy(label = t.label) else app
                        val widgetId = if (t.key.startsWith("widget:")) t.key.removePrefix("widget:").toIntOrNull() else null
                        val r = rectOf(p)
                        val dragging = dragKey == t.key
                        val off = if (dragging) dragOffset else Offset.Zero
                        val sel = selected
                        val isSel = sel == t.key
                        val dimmed = sel != null && !isSel

                        val interaction = when {
                            isSel -> Modifier.pointerInput(t.key, cellPx) {
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
                            sel != null -> Modifier.combinedClickable(
                                onClick = { deselect() },
                                onLongClick = { select(t.key) }
                            )
                            else -> Modifier.combinedClickable(
                                onClick = {
                                    val k = t.key
                                    when {
                                        k == "sys:add" -> onAddMenu()
                                        app != null -> onOpen(app)
                                        k.startsWith("link:") -> onOpenLink(k)
                                        k.startsWith("contact:") -> onOpenContact(k)
                                        k.startsWith("cube:") ->
                                            appsOf(k).getOrNull(cubeIndex[k] ?: 0)?.let { onOpen(it) }
                                        k.startsWith("group:") -> openGroup = k
                                        else -> onLiveClick(k)
                                    }
                                },
                                onLongClick = { select(t.key) }
                            )
                        }

                        val bg = t.color?.let { Color(it) } ?: if (t.isHeader) Color(0xFF2E2E2E) else accent

                        Box(
                            Modifier
                                .offset { IntOffset((r.left + off.x).roundToInt(), (r.top + off.y).roundToInt()) }
                                .size(
                                    with(density) { r.width.toDp() },
                                    with(density) { r.height.toDp() }
                                )
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    val s = if (dragging) 1.06f else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                                .background(bg)
                                .then(interaction)
                        ) {
                            when {
                                t.isHeader -> HeaderLabel(headerName(t.key))
                                t.key == "sys:add" -> AddTile(t.size)
                                t.key.startsWith("sys:apps") -> AppsTile(t.size, t.label)
                                t.key.startsWith("act:") -> ActionTile(
                                    actionById(t.key.removePrefix("act:").substringBefore('#')),
                                    t.size,
                                    t.label
                                )
                                t.key.startsWith("link:") -> LinkTile(
                                    t.label ?: data.extra(t.key)?.optString("name").orEmpty(),
                                    data.bitmap(data.extra(t.key)?.optString("icon")),
                                    t.size
                                )
                                t.key.startsWith("contact:") -> ContactTile(
                                    t.label ?: data.extra(t.key)?.optString("name").orEmpty(),
                                    data.bitmap(data.extra(t.key)?.optString("photo")),
                                    t.size
                                )
                                t.key.startsWith("cube:") ->
                                    CubeTile(appsOf(t.key), cubeIndex[t.key] ?: 0, { cubeIndex[t.key] = it }, t.size)
                                t.key.startsWith("group:") -> GroupTile(
                                    t.label ?: data.extra(t.key)?.optString("name").orEmpty(),
                                    appsOf(t.key),
                                    t.size
                                )
                                t.key == "live:clock" -> ClockTile(t.size)
                                t.key == "live:weather" -> WeatherTile(weather, weatherStatus, t.size)
                                t.key == "live:photos" -> PhotoTile(photos, t.size)
                                t.key == "live:notif" -> NotifSummaryTile(notifConnected, notifs, appsByPkg, t.size)
                                widgetId != null -> WidgetTile(
                                    widgets,
                                    widgetId,
                                    with(density) { r.width.toDp().value.toInt() },
                                    with(density) { r.height.toDp().value.toInt() },
                                    onLongPress = { select(t.key) }
                                )
                                shownApp != null -> AppTile(shownApp, t.size, notifs[shownApp.pkg])
                            }
                            if (sel != null && widgetId != null) {
                                // intercetta i tocchi, così il widget non li "ruba" a selezione e trascinamento
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
                            if (dimmed) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f))
                                )
                            }
                        }
                    }
                }

                // anteprima della nuova dimensione mentre si trascina il pallino di ridimensionamento
                val rk = resizeKey
                val me = if (rk != null) placed.firstOrNull { it.tile.key == rk } else null
                if (me != null) {
                    val r0 = rectOf(me)
                    val pw = resizeW * cellPx + (resizeW - 1) * gapPx
                    val ph = resizeH * cellPx + (resizeH - 1) * gapPx
                    Box(
                        Modifier
                            .offset { IntOffset(r0.left.roundToInt(), r0.top.roundToInt()) }
                            .size(with(density) { pw.toDp() }, with(density) { ph.toDp() })
                            .zIndex(2f)
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(2.dp, Color.White)
                    )
                }

                // i 4 pallini agli angoli della tile selezionata
                val selP = placed.firstOrNull { it.tile.key == selected }
                if (selP != null && dragKey == null) {
                    val tile = selP.tile
                    val rs = rectOf(selP)
                    fun bx(cx: Float) = (cx - btnPx / 2).coerceIn(0f, (screenPx - btnPx).coerceAtLeast(0f))
                    fun by(cy: Float) = (cy - btnPx / 2).coerceAtLeast(0f)
                    val selApp = apps[tile.key]

                    if (selApp != null) {
                        RoundBtn(Icons.Outlined.Info, Color.Black, bx(rs.left), by(rs.top)) {
                            openAppInfo(ctx, selApp.pkg)
                        }
                    }
                    if (tile.key != "sys:add") {
                        RoundBtn(Icons.Outlined.Delete, Color(0xFFD32F2F), bx(rs.right), by(rs.top)) {
                            onRemove(tile)
                            deselect()
                        }
                    }
                    RoundBtn(Icons.Outlined.Settings, Color.Black, bx(rs.left), by(rs.bottom)) {
                        onOptions(tile)
                    }
                    if (!tile.isHeader) {
                        RoundBtn(
                            Icons.Outlined.Fullscreen,
                            Color.Black,
                            bx(rs.right),
                            by(rs.bottom),
                            modifier = Modifier.pointerInput(tile.key, cellPx) {
                                var acc = Offset.Zero
                                val step = cellPx + gapPx
                                detectDragGestures(
                                    onDragStart = {
                                        acc = Offset.Zero
                                        resizeKey = selTileNow?.key
                                        resizeW = selTileNow?.w ?: 1
                                        resizeH = selTileNow?.h ?: 1
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        acc += amount
                                        val st = selTileNow
                                        if (st != null) {
                                            resizeW = ((st.w * step + acc.x) / step).roundToInt().coerceIn(1, COLUMNS)
                                            resizeH = ((st.h * step + acc.y) / step).roundToInt().coerceIn(1, 12)
                                        }
                                    },
                                    onDragEnd = {
                                        val k = resizeKey
                                        val nw = resizeW
                                        val nh = resizeH
                                        if (k != null) {
                                            onTiles(
                                                tilesNow.map {
                                                    if (it.key == k) it.copy(w = nw, h = nh) else it
                                                }
                                            )
                                        }
                                        resizeKey = null
                                    },
                                    onDragCancel = { resizeKey = null }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
