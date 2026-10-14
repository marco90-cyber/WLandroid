package com.marco.wlandroid

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
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
import kotlin.math.abs
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
    locked: Boolean,
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
    val tilesNow by rememberUpdatedState(tiles)
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
        if (locked) return
        selected = k
        onEdit(true)
    }
    fun deselect() {
        selected = null
        onEdit(false)
    }

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

        /** Posizione verticale del bordo alto di ogni riga: le righe dei divisori sono sottili. */
        fun topsOf(layout: List<Tile>, extraRows: Int): FloatArray {
            val maxRow = layout.maxOfOrNull { it.row + it.h } ?: 0
            val headerRows = layout.filter { it.isHeader }.map { it.row }.toSet()
            val n = maxRow + extraRows
            val top = FloatArray(n + 1)
            top[0] = padPx
            for (r in 0 until n) top[r + 1] = top[r] + (if (r in headerRows) hdrPx else cellPx) + gapPx
            return top
        }

        fun rectIn(top: FloatArray, t: Tile): Rect {
            val x = padPx + t.col * (cellPx + gapPx)
            val w = t.w * cellPx + (t.w - 1) * gapPx
            val y = top[t.row.coerceIn(0, top.size - 1)]
            val bottom = top[(t.row + t.h).coerceIn(0, top.size - 1)] - gapPx
            return Rect(x, y, x + w, bottom)
        }

        // geometria "ferma" (senza anteprima), usata per capire dove si trova il dito
        val committedTop = topsOf(visible, 8)

        val dk = dragKey
        val dragTile = if (dk != null) visible.firstOrNull { it.key == dk } else null
        var target: Pair<Int, Int>? = null
        var dragOrigin: Rect? = null
        if (dragTile != null) {
            val r0 = rectIn(committedTop, dragTile)
            dragOrigin = r0
            val left = r0.left + dragOffset.x
            val topY = r0.top + dragOffset.y
            val col = ((left - padPx) / (cellPx + gapPx)).roundToInt().coerceIn(0, COLUMNS - dragTile.w)
            var best = 0
            var bd = Float.MAX_VALUE
            for (r in 0 until committedTop.size - 1) {
                val d = abs(committedTop[r] - topY)
                if (d < bd) {
                    bd = d
                    best = r
                }
            }
            best = best.coerceAtMost((committedTop.size - 1 - dragTile.h).coerceAtLeast(0))
            target = col to best
        }

        val tgt = target
        val org = dragOrigin

        // disposizione mostrata: durante il trascinamento le altre tile si spostano per fare spazio
        val layout = if (dragTile != null && tgt != null) {
            resolveLayout(
                visible.map { if (it.key == dragTile.key) it.copy(col = tgt.first, row = tgt.second) else it },
                dragTile.key
            )
        } else {
            visible
        }
        val layoutNow by rememberUpdatedState(layout)
        val top = topsOf(layout, 0)
        val totalHeight = with(density) {
            (top[top.size - 1] - gapPx + padPx + (if (dragTile != null) 2 * (cellPx + gapPx) else 0f))
                .coerceAtLeast(0f).toDp()
        }
        val selectedTile = layout.firstOrNull { it.key == selected }
        val selTileNow by rememberUpdatedState(selectedTile)

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
                // sagoma di anteprima: dove cadrà la tile trascinata
                val ghostTile = if (dragTile != null) layout.firstOrNull { it.key == dragTile.key } else null
                if (ghostTile != null) {
                    val gr = rectIn(top, ghostTile)
                    val gbg = ghostTile.color?.let { Color(it) } ?: if (ghostTile.isHeader) Color(0xFF2E2E2E) else accent
                    Box(
                        Modifier
                            .offset { IntOffset(gr.left.roundToInt(), gr.top.roundToInt()) }
                            .size(with(density) { gr.width.toDp() }, with(density) { gr.height.toDp() })
                            .background(Color(gbg.red * 0.45f, gbg.green * 0.45f, gbg.blue * 0.45f, 1f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f))
                    )
                }
                for (t in layout) {
                    key(t.key) {
                        val app = apps[t.key]
                        val shownApp = if (app != null && t.label != null) app.copy(label = t.label) else app
                        val widgetId = if (t.key.startsWith("widget:")) t.key.removePrefix("widget:").toIntOrNull() else null
                        val r = rectIn(top, t)
                        val dragging = dragKey == t.key
                        val animated by animateIntOffsetAsState(
                            IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                            tween(220),
                            label = "tilePos"
                        )
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
                                        if (dragKey != null) {
                                            val moved = layoutNow.associateBy { it.key }
                                            onTiles(tilesNow.map { moved[it.key] ?: it })
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
                                        k == "sys:add" -> { if (!locked) onAddMenu() }
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
                                .offset {
                                    val o = org
                                    if (dragging && o != null) {
                                        IntOffset((o.left + dragOffset.x).roundToInt(), (o.top + dragOffset.y).roundToInt())
                                    } else {
                                        animated
                                    }
                                }
                                .size(
                                    with(density) { r.width.toDp() },
                                    with(density) { r.height.toDp() }
                                )
                                .zIndex(if (dragging) 3f else 0f)
                                .graphicsLayer {
                                    val sc = if (dragging) 1.06f else 1f
                                    scaleX = sc
                                    scaleY = sc
                                    alpha = if (dragging) 0.92f else 1f
                                    shadowElevation = if (dragging) 24f else 0f
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
                val me = if (rk != null) layout.firstOrNull { it.key == rk } else null
                if (me != null) {
                    val r0 = rectIn(top, me)
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
                val selP = layout.firstOrNull { it.key == selected }
                if (selP != null && dragKey == null) {
                    val tile = selP
                    val rs = rectIn(top, selP)
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
                                            val resized = tilesNow.map {
                                                if (it.key == k) {
                                                    it.copy(w = nw, h = nh, col = minOf(it.col, COLUMNS - nw))
                                                } else {
                                                    it
                                                }
                                            }
                                            onTiles(resolveLayout(resized, k))
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
