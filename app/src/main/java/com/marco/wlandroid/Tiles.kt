package com.marco.wlandroid

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Tile che ogni tanto si gira (asse orizzontale, come Windows Phone) mostrando il retro. */
@Composable
fun FlipTile(
    canFlip: Boolean,
    periodMs: Long,
    seed: Int,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    var showBack by remember { mutableStateOf(false) }
    LaunchedEffect(canFlip, periodMs) {
        if (canFlip) {
            delay(1500L + (seed and 0xFF) * 40L)
            while (true) {
                showBack = !showBack
                delay(periodMs)
            }
        } else {
            showBack = false
        }
    }
    val rot by animateFloatAsState(if (showBack) 180f else 0f, tween(700), label = "flip")
    val dens = LocalDensity.current.density
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationX = rot
                cameraDistance = 14f * dens
            }
    ) {
        if (rot <= 90f) {
            front()
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationX = 180f }
            ) { back() }
        }
    }
}

// ---------- Tile app ----------

@Composable
fun AppTile(app: AppInfo, size: TileSize, notif: NotifInfo?) {
    val canFlip = notif != null && size != TileSize.SMALL
    FlipTile(
        canFlip = canFlip,
        periodMs = 6000L,
        seed = app.key.hashCode(),
        front = { AppFront(app, size, notif) },
        back = { NotifBack(app.label, notif) }
    )
}

@Composable
private fun AppFront(app: AppInfo, size: TileSize, notif: NotifInfo?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val small = size == TileSize.SMALL
        val fraction = if (small) 0.5f else 0.45f
        val iconSize = minOf(maxWidth, maxHeight) * fraction
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = if (small) (-6).dp else (-4).dp)
                .size(iconSize)
        )
        Text(
            app.label,
            color = Color.White,
            fontSize = if (small) 11.sp else 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = if (small) 4.dp else 6.dp)
        )
        if (notif != null) {
            Text(
                "${notif.count}",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun NotifBack(label: String, notif: NotifInfo?) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                notif?.title.orEmpty(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                notif?.text.orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if ((notif?.count ?: 0) > 1) "$label · ${notif?.count}" else label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

// ---------- Orologio ----------

@Composable
fun ClockTile(size: TileSize) {
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
                fontSize = if (size == TileSize.MEDIUM) 34.sp else 54.sp,
                fontWeight = FontWeight.Light
            )
            Text(date, color = Color.White, fontSize = 14.sp, maxLines = 2)
        }
    }
}

// ---------- Meteo ----------

@Composable
fun WeatherTile(weather: Weather?, status: String?, size: TileSize) {
    if (weather == null) {
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            Text(status ?: "Meteo…", color = Color.White, fontSize = 14.sp)
        }
    } else {
        FlipTile(
            canFlip = size != TileSize.SMALL,
            periodMs = 7000L,
            seed = 3,
            front = { WeatherFront(weather, size) },
            back = { WeatherBack(weather) }
        )
    }
}

@Composable
private fun WeatherFront(w: Weather, size: TileSize) {
    if (size == TileSize.SMALL) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(weatherEmoji(w.code), fontSize = 20.sp)
            Text("${w.temp}°", color = Color.White, fontSize = 14.sp)
        }
    } else {
        Row(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(weatherEmoji(w.code), fontSize = if (size == TileSize.MEDIUM) 34.sp else 48.sp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    "${w.temp}°",
                    color = Color.White,
                    fontSize = if (size == TileSize.MEDIUM) 30.sp else 44.sp,
                    fontWeight = FontWeight.Light
                )
                Text(weatherText(w.code), color = Color.White, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WeatherBack(w: Weather) {
    val names = listOf("Oggi", "Domani", "Dopodomani")
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        w.days.take(3).forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(names.getOrElse(i) { "" }, color = Color.White, fontSize = 13.sp, maxLines = 1)
                Text("${d.first}° / ${d.second}°", color = Color.White, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

// ---------- Foto ----------

@Composable
fun PhotoTile(photos: List<ImageBitmap>, size: TileSize) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                if (size == TileSize.SMALL) "🖼️" else "Tocca per scegliere le foto",
                color = Color.White,
                fontSize = if (size == TileSize.SMALL) 20.sp else 14.sp
            )
        }
    } else {
        var index by remember { mutableIntStateOf(0) }
        LaunchedEffect(photos.size) {
            index = 0
            while (photos.size > 1) {
                delay(6000L)
                index = (index + 1) % photos.size
            }
        }
        Crossfade(targetState = index, animationSpec = tween(1200), label = "photos") { i ->
            Image(
                bitmap = photos[i.coerceIn(0, photos.size - 1)],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ---------- Riepilogo notifiche ----------

@Composable
fun NotifSummaryTile(
    connected: Boolean,
    notifs: Map<String, NotifInfo>,
    appsByPkg: Map<String, AppInfo>,
    size: TileSize
) {
    if (!connected) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                if (size == TileSize.SMALL) "🔔" else "🔔 Tocca per attivare le notifiche",
                color = Color.White,
                fontSize = if (size == TileSize.SMALL) 20.sp else 14.sp
            )
        }
    } else {
        val total = notifs.values.sumOf { it.count }
        val latestEntry = notifs.entries.maxByOrNull { it.value.time }
        FlipTile(
            canFlip = latestEntry != null && size != TileSize.SMALL,
            periodMs = 6000L,
            seed = 5,
            front = {
                Column(
                    Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔔", fontSize = if (size == TileSize.SMALL) 16.sp else 26.sp)
                    Text(
                        "$total",
                        color = Color.White,
                        fontSize = if (size == TileSize.SMALL) 16.sp else 36.sp,
                        fontWeight = FontWeight.Light
                    )
                    if (size != TileSize.SMALL) {
                        Text("notifiche", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            back = {
                NotifBack(
                    label = latestEntry?.let { appsByPkg[it.key]?.label ?: it.key }.orEmpty(),
                    notif = latestEntry?.value
                )
            }
        )
    }
}

// ---------- Tile fissa "+" ----------

@Composable
fun AddTile(size: TileSize, corner: Int = 0) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = corner.dp.toPx()
            val stroke = 7.dp.toPx().coerceAtMost(this@Canvas.size.minDimension / 4f)
            val outer = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        0f, 0f, this@Canvas.size.width, this@Canvas.size.height,
                        androidx.compose.ui.geometry.CornerRadius(r, r)
                    )
                )
            }
            val ri = (r - stroke).coerceAtLeast(0f)
            val inner = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        stroke, stroke, this@Canvas.size.width - stroke, this@Canvas.size.height - stroke,
                        androidx.compose.ui.geometry.CornerRadius(ri, ri)
                    )
                )
            }
            val ring = androidx.compose.ui.graphics.Path.combine(
                androidx.compose.ui.graphics.PathOperation.Difference, outer, inner
            )
            clipPath(ring) {
                val gap = 5.dp.toPx()
                val w = this@Canvas.size.width
                val h = this@Canvas.size.height
                var x = -h
                while (x < w) {
                    drawLine(
                        Color(0xFFF0A30A),
                        androidx.compose.ui.geometry.Offset(x, h),
                        androidx.compose.ui.geometry.Offset(x + h, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                    x += gap
                }
            }
        }
        Text(
            "+",
            color = Color.White,
            fontSize = if (size == TileSize.SMALL) 34.sp else 56.sp,
            fontWeight = FontWeight.Light
        )
    }
}

// ---------- Tile "Applicazioni" (apre l'elenco app) ----------

@Composable
fun AppsTile(size: TileSize, label: String? = null) {
    val small = size == TileSize.SMALL
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.Center)
                .offset(y = if (small) (-6).dp else (-4).dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(if (small) 8.dp else 10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
        Text(
            label ?: "Applicazioni",
            color = Color.White,
            fontSize = if (small) 11.sp else 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = if (small) 4.dp else 6.dp)
        )
    }
}

// ---------- Etichetta di sezione ----------

@Composable
fun HeaderLabel(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ---------- Azione del launcher ----------

@Composable
fun ActionTile(action: LAction?, size: TileSize, label: String? = null) {
    val small = size == TileSize.SMALL
    Box(Modifier.fillMaxSize()) {
        if (action != null) {
            Icon(
                action.icon,
                contentDescription = action.label,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (small) (-6).dp else (-4).dp)
                    .size(if (small) 32.dp else 44.dp)
            )
            Text(
                label ?: action.label,
                color = Color.White,
                fontSize = if (small) 11.sp else 13.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp, vertical = if (small) 4.dp else 6.dp)
            )
        }
    }
}

// ---------- Collegamento ----------

@Composable
fun LinkTile(name: String, icon: ImageBitmap?, size: TileSize) {
    if (icon != null) {
        AppFront(AppInfo(name, "", "", icon), size, null)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                name.take(1).uppercase(),
                color = Color.White,
                fontSize = if (size == TileSize.SMALL) 28.sp else 48.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

// ---------- Contatto ----------

@Composable
fun ContactTile(name: String, photo: ImageBitmap?, size: TileSize) {
    Box(Modifier.fillMaxSize()) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.take(1).uppercase(),
                color = Color.White,
                fontSize = if (size == TileSize.SMALL) 28.sp else 56.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            name,
            color = Color.White,
            fontSize = if (size == TileSize.SMALL) 11.sp else 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

// ---------- Gruppo di tile ----------

@Composable
fun GroupTile(
    name: String,
    apps: List<AppInfo>,
    size: TileSize,
    color: Color,
    expanded: Boolean,
    icon: ImageBitmap?,
    image: ImageBitmap?,
    showLabel: Boolean
) {
    val label = showLabel && name.isNotBlank()
    @Composable
    fun content(mod: Modifier) {
        BoxWithConstraints(mod.background(color)) {
            if (image != null) {
                Image(
                    image, null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else if (icon != null) {
                Image(
                    icon, null,
                    Modifier
                        .align(Alignment.Center)
                        .size(minOf(maxWidth, maxHeight) * 0.5f)
                )
            } else {
                // cartella stile Windows Phone: griglia 2x2 di mini icone
                val side = minOf(maxWidth.value, maxHeight.value - (if (label) 14f else 0f)) * 0.68f
                val mini = (side - 4f) / 2f
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = ((maxHeight.value - (if (label) 14f else 0f) - side) / 2f).coerceAtLeast(2f).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    apps.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { a -> Image(a.icon, a.label, Modifier.size(mini.dp)) }
                        }
                    }
                }
            }
            if (label) {
                Text(
                    name,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.8f)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        content(Modifier.fillMaxSize())
        androidx.compose.material3.Icon(
            if (expanded) androidx.compose.material.icons.Icons.Outlined.KeyboardArrowUp
            else androidx.compose.material.icons.Icons.Outlined.KeyboardArrowDown,
            null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(22.dp)
        )
    }
}

// ---------- Cubo: le app ruotano in 3D una dopo l'altra ----------

@Composable
fun CubeTile(apps: List<AppInfo>, index: Int, onIndex: (Int) -> Unit, size: TileSize) {
    if (apps.isEmpty()) return
    var shown by remember { mutableIntStateOf(index.coerceIn(0, apps.size - 1)) }
    val rot = remember { Animatable(0f) }
    val dens = LocalDensity.current.density
    LaunchedEffect(apps.size) {
        delay(1500)
        while (apps.size > 1) {
            delay(3500)
            rot.animateTo(90f, tween(350))
            shown = (shown + 1) % apps.size
            onIndex(shown)
            rot.snapTo(-90f)
            rot.animateTo(0f, tween(350))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rot.value
                cameraDistance = 16f * dens
            }
    ) {
        AppFront(apps[shown.coerceIn(0, apps.size - 1)], size, null)
    }
}
