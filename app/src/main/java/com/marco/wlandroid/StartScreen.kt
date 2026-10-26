package com.marco.wlandroid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
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
    moveMode: Boolean,
    onPlusMenu: () -> Unit,
    fill: Boolean,
    tick: Int,
    corner: Int,
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
    val visible0 = tiles.filter { isSpecialKey(it.key) || apps.containsKey(it.key) }
    val tilesNow by rememberUpdatedState(tiles)
    val appsByPkg = remember(apps) { apps.values.associateBy { it.pkg } }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    val cubeIndex = remember { mutableStateMapOf<String, Int>() }
    fun appsOf(key: String): List<AppInfo> =
        data.extra(key)?.optJSONArray("apps")?.let { a ->
            (0 until a.length()).mapNotNull { apps[a.optString(it)] }
        } ?: emptyList()
    var resizeKey by remember { mutableStateOf<String?>(null) }
    var resizeW by remember { mutableIntStateOf(1) }
    var resizeH by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val shape = if (corner > 0) RoundedCornerShape(corner.dp) else RectangleShape

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
        // le tile hanno sempre la dimensione che avrebbero in verticale (lato corto dello schermo)
        val shortSide = with(density) { minOf(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels).toFloat() }
        val baseW = minOf(screenPx, shortSide)
        val cellPx = (baseW - 2 * padPx - (BASE_COLUMNS - 1) * gapPx) / BASE_COLUMNS
        val cols = if (!fill) {
            BASE_COLUMNS
        } else {
            maxOf(BASE_COLUMNS, ((screenPx - 2 * padPx + gapPx) / (cellPx + gapPx)).toInt())
        }
        COLUMNS = cols
        val gridW = 2 * padPx + cols * cellPx + (cols - 1) * gapPx
        val offX = ((screenPx - gridW) / 2f).coerceAtLeast(0f)
        // i divisori occupano sempre tutta la larghezza; le altre tile restano dentro le colonne disponibili
        val visible = visible0.map {
            if (it.isHeader) {
                it.copy(col = 0, w = cols)
            } else {
                val nw = minOf(it.w, cols)
                it.copy(w = nw, col = it.col.coerceIn(0, cols - nw))
            }
        }
        val hdrPx = with(density) { 26.dp.toPx() }
        val btnPx = with(density) { 32.dp.toPx() }

        /** Posizione verticale del bordo alto di ogni riga: le righe dei divisori sono sottili. */
        fun topsOf(layout: List<Tile>, extraRows: Int): FloatArray {
            val maxRow = layout.maxOfOrNull { it.row + it.h } ?: 0
            val headerRows = layout.filter { it.isHeader }.map { it.row }.toSet()
            val n = maxRow + extraRows
            val top = FloatArray(n + 1)
            top[0] = padPx
            for (r in 0 until n) {
                top[r + 1] = top[r] + if (r in headerRows) {
                    hdrPx + gapPx
                } else {
                    cellPx + gapPx
                }
            }
            return top
        }

        fun rectIn(top: FloatArray, t: Tile): Rect {
            val x = offX + padPx + t.col * (cellPx + gapPx)
            val w = t.w * cellPx + (t.w - 1) * gapPx
            val y = top[t.row.coerceIn(0, top.size - 1)]
            val bottom = top[(t.row + t.h).coerceIn(0, top.size - 1)] - gapPx
            return Rect(x, y, x + w, bottom)
        }

        // geometria "ferma" (senza anteprima), usata per capire dove si trova il dito
        val expActive = expandedGroup != null && selected == null && !moveMode && dragKey == null && resizeKey == null
        fun withExpansion(l: List<Tile>): List<Tile> {
            val gk = expandedGroup
            val g = if (expActive && gk != null) l.firstOrNull { it.key == gk } else null
            if (g == null || gk == null) return l
            val list = appsOf(gk)
            val need = (list.size + cols - 1) / cols
            val below = g.row + g.h
            val shifted = l.map {
                if (it.row >= below) it.copy(row = it.row + need)
                else if (it.row + it.h > below) it.copy(h = it.h + need)
                else it
            }
            return shifted + list.mapIndexed { i, a ->
                Tile("gx|$gk|${a.key}", 1, 1, col = i % cols, row = below + i / cols)
            }
        }
        val restLayout = withExpansion(resolveAll(visible))
        val committedTop = topsOf(restLayout, 8)

        val dk = dragKey
        val dragTile = if (dk != null) restLayout.firstOrNull { it.key == dk } else null
        var target: Pair<Int, Int>? = null
        var dragOrigin: Rect? = null
        if (dragTile != null) {
            val r0 = rectIn(committedTop, dragTile)
            dragOrigin = r0
            val left = r0.left + dragOffset.x
            val topY = r0.top + dragOffset.y
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
            val col = ((left - offX - padPx) / (cellPx + gapPx)).roundToInt().coerceIn(0, COLUMNS - dragTile.w)
            target = col to best
        }

        val tgt = target
        val org = dragOrigin

        // disposizione mostrata: durante il trascinamento le altre tile si spostano per fare spazio
        val rk0 = resizeKey
        val layout = if (dragTile != null && tgt != null) {
            resolveLayout(
                visible.map { if (it.key == dragTile.key) it.copy(col = tgt.first, row = tgt.second) else it },
                dragTile.key
            )
        } else if (rk0 != null) {
            resolveLayout(
                visible.map {
                    if (it.key == rk0) it.copy(w = resizeW, h = resizeH, col = minOf(it.col, COLUMNS - resizeW)) else it
                },
                rk0
            )
        } else {
            restLayout
        }
        val layoutNow by rememberUpdatedState(layout)
        val top = topsOf(layout, 0)
        val restTop = topsOf(restLayout, 0)
        val totalHeight = with(density) {
            val bottomPx = top[top.size - 1] - gapPx
            (bottomPx + padPx + (if (dragTile != null) 2 * (cellPx + gapPx) else 0f))
                .coerceAtLeast(0f).toDp()
        }
        val selectedTile = restLayout.firstOrNull { it.key == selected }
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
                    val ghostShape: Shape = shape
                    Box(
                        Modifier
                            .offset { IntOffset(gr.left.roundToInt(), gr.top.roundToInt()) }
                            .size(with(density) { gr.width.toDp() }, with(density) { gr.height.toDp() })
                            .clip(ghostShape)
                            .background(Color(gbg.red * 0.45f, gbg.green * 0.45f, gbg.blue * 0.45f, 1f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f))
                    )
                }
                for (t in layout) {
                    key(t.key) {
                        val app = if (t.key.startsWith("gx|")) apps[t.key.substringAfterLast('|')] else apps[t.key]
                        val shownApp = if (app != null && t.label != null) app.copy(label = t.label) else app
                        val widgetId = if (t.key.startsWith("widget:")) t.key.removePrefix("widget:").toIntOrNull() else null
                        val r = rectIn(top, t)
                        val dragging = dragKey == t.key
                        val animated by animateIntOffsetAsState(
                            IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                            label = "tilePos"
                        )
                        val sel = selected
                        val isSel = sel == t.key
                        val dimmed = sel != null && !isSel

                        // effetto Windows Phone: inclinazione al tocco, ingresso "turnstile", tile ridotte in modifica
                        var pressPos by remember { mutableStateOf<Offset?>(null) }
                        var lastPress by remember { mutableStateOf(Offset.Zero) }
                        val press by animateFloatAsState(
                            if (pressPos != null) 1f else 0f,
                            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                            label = "press"
                        )
                        val enter = remember { Animatable(0f) }
                        val enterIdx = remember { layout.indexOfFirst { it.key == t.key } }
                        LaunchedEffect(Unit) {
                            delay((enterIdx.coerceAtLeast(0) * 28L).coerceAtMost(420L))
                            enter.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                        }
                        val editScale by animateFloatAsState(
                            if (dimmed) 0.93f else 1f,
                            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                            label = "editScale"
                        )
                        val tilt = Modifier.pointerInput(t.key) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    lastPress = down.position
                                    pressPos = down.position
                                    do {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    } while (ev.changes.any { it.pressed })
                                    pressPos = null
                                }
                            }
                        }

                        val interaction = when {
                            isSel || moveMode -> Modifier.pointerInput(t.key, cellPx, moveMode) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragKey = t.key
                                        dragOffset = Offset.Zero
                                    },
                                    onDragEnd = {
                                        val dkNow = dragKey
                                        if (dkNow != null) {
                                            val mt = layoutNow.firstOrNull { it.key == dkNow }
                                            if (mt != null) {
                                                onTiles(
                                                    toFront(
                                                        tilesNow.map {
                                                            if (it.key == dkNow) it.copy(col = mt.col, row = mt.row) else it
                                                        },
                                                        dkNow
                                                    )
                                                )
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
                                        k == "sys:add" -> { if (!locked) onAddMenu() }
                                        app != null -> onOpen(app)
                                        k.startsWith("link:") -> onOpenLink(k)
                                        k.startsWith("contact:") -> onOpenContact(k)
                                        k.startsWith("cube:") ->
                                            appsOf(k).getOrNull(cubeIndex[k] ?: 0)?.let { onOpen(it) }
                                        k.startsWith("group:") -> expandedGroup = if (expandedGroup == k) null else k
                                        else -> onLiveClick(k)
                                    }
                                },
                                onLongClick = {
                                    if (t.key == ADD_KEY) onPlusMenu()
                                    else if (!t.key.startsWith("gx|")) select(t.key)
                                }
                            )
                        }

                        val tshape: Shape = shape
                        val bg = t.color?.let { Color(it) } ?: if (t.isHeader) Color(0xFF2E2E2E) else if (t.key == ADD_KEY) (if (locked) Color.Transparent else Color.Black) else if (expActive && expandedGroup == t.key) Color.Transparent else accent

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
                                    val calm = sel == null && !moveMode && !dragging
                                    val pk = if (calm) press else 0f
                                    val sc = (if (dragging) 1.06f else editScale) * (1f - 0.06f * pk)
                                    scaleX = sc
                                    scaleY = sc
                                    cameraDistance = 14f * this.density
                                    if (pk > 0f && size.width > 0f) {
                                        rotationY = ((lastPress.x - size.width / 2f) / (size.width / 2f)).coerceIn(-1f, 1f) * 9f * pk
                                        rotationX = -((lastPress.y - size.height / 2f) / (size.height / 2f)).coerceIn(-1f, 1f) * 9f * pk
                                    }
                                    val e = enter.value
                                    if (e < 1f && size.width > 0f) {
                                        // le tile ruotano come una porta ancorata al bordo sinistro dello schermo
                                        transformOrigin = TransformOrigin(-r.left / size.width, 0.5f)
                                        rotationY = (1f - e) * 62f
                                    }
                                    alpha = (if (dragging) 0.92f else 1f) * e.coerceIn(0f, 1f)
                                    shadowElevation = if (dragging) 24f else 0f
                                }
                                .clip(tshape)
                                .background(bg)
                                .then(tilt)
                                .then(interaction)
                        ) {
                            when {
                                t.isHeader -> HeaderLabel(headerName(t.key))
                                t.key == "sys:add" -> if (!locked) AddTile(t.size, corner)
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
                                t.key.startsWith("group:") -> {
                                    val gex = data.extra(t.key)
                                    GroupTile(
                                        name = t.label ?: gex?.optString("name").orEmpty(),
                                        apps = appsOf(t.key),
                                        size = t.size,
                                        color = t.color?.let { Color(it) } ?: accent,
                                        expanded = expActive && expandedGroup == t.key,
                                        icon = data.bitmap(gex?.optString("icon")),
                                        image = data.bitmap(gex?.optString("image")),
                                        showLabel = gex?.optBoolean("hideLabel", false) != true
                                    )
                                }
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
                            if ((sel != null || moveMode) && widgetId != null) {
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
                    Box(
                        Modifier
                            .offset { IntOffset(r0.left.roundToInt(), r0.top.roundToInt()) }
                            .size(with(density) { r0.width.toDp() }, with(density) { r0.height.toDp() })
                            .zIndex(2f)
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(2.dp, Color.White)
                    )
                }

                // i 4 pallini agli angoli della tile selezionata
                val selP = restLayout.firstOrNull { it.key == selected }
                if (selP != null && dragKey == null) {
                    val tile = selP
                    val rs = rectIn(restTop, selP)
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
                                val stepX = cellPx + gapPx
                                val stepY = cellPx + gapPx
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
                                            resizeW = ((st.w * stepX + acc.x) / stepX).roundToInt().coerceIn(1, COLUMNS)
                                            resizeH = ((st.h * stepY + acc.y) / stepY).roundToInt().coerceIn(1, 12)
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
                                            onTiles(toFront(resized, k))
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
