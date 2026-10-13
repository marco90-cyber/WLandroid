package com.marco.wlandroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var homeTick by mutableIntStateOf(0)
    private var resumeTick by mutableIntStateOf(0)
    private lateinit var widgets: WidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgets = WidgetController(this)
        val store = Store(this)
        setContent { WLApp(store, widgets, homeTick, resumeTick) }
    }

    override fun onStart() {
        super.onStart()
        widgets.start()
    }

    override fun onStop() {
        widgets.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick++
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    @Deprecated("Needed for AppWidgetHost callbacks")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.handleResult(requestCode, resultCode)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WLApp(store: Store, widgets: WidgetController, homeTick: Int, resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tiles by remember { mutableStateOf(store.loadTiles()) }
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var accent by remember { mutableStateOf(Color(store.accent)) }
    var dark by remember { mutableStateOf(store.dark) }
    var editing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf(store.weather) }
    var weatherStatus by remember { mutableStateOf<String?>(null) }
    var weatherTick by remember { mutableIntStateOf(0) }
    var hasLoc by remember { mutableStateOf(hasLocationPermission(ctx)) }
    var photoUris by remember { mutableStateOf(store.photos) }
    val pager = rememberPagerState { 2 }

    val notifs by NotificationHub.state.collectAsState()
    val notifConnected by NotificationHub.connected.collectAsState()

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasLoc = ok
        weatherTick++
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach {
                try {
                    ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                }
            }
            val list = uris.map { it.toString() }
            store.photos = list
            photoUris = list
        }
    }

    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
        hasLoc = hasLocationPermission(ctx)
    }
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            editing = false
            pager.animateScrollToPage(0)
        }
    }
    BackHandler(editing || pager.currentPage == 1) {
        if (editing) editing = false else scope.launch { pager.animateScrollToPage(0) }
    }

    val hasWeatherTile = tiles.any { it.key == "live:weather" }
    LaunchedEffect(hasWeatherTile, hasLoc, weatherTick, resumeTick) {
        if (hasWeatherTile && !hasLoc) {
            weatherStatus = "Tocca per consentire la posizione"
        } else if (hasWeatherTile) {
            while (true) {
                val loc = lastLocation(ctx)
                if (loc == null) {
                    weatherStatus = "Posizione non disponibile"
                } else {
                    val w = fetchWeather(loc.first, loc.second)
                    if (w != null) {
                        weather = w
                        store.weather = w
                        weatherStatus = null
                    } else if (weather == null) {
                        weatherStatus = "Meteo non disponibile"
                    }
                }
                delay(30 * 60 * 1000L)
            }
        }
    }

    val photos by produceState(emptyList<ImageBitmap>(), photoUris) {
        value = withContext(Dispatchers.IO) {
            photoUris.take(12).mapNotNull { loadThumb(ctx, Uri.parse(it)) }
        }
    }

    val bg = if (dark) Color.Black else Color.White
    val fg = if (dark) Color.White else Color.Black
    val view = LocalView.current
    SideEffect {
        val w = (ctx as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }

    fun setTiles(l: List<Tile>) {
        tiles = l
        store.saveTiles(l)
    }

    fun pin(app: AppInfo) {
        if (tiles.none { it.key == app.key }) setTiles(tiles + Tile(app.key, TileSize.MEDIUM))
        scope.launch { pager.animateScrollToPage(0) }
    }

    fun removeTile(t: Tile) {
        if (t.key.startsWith("widget:")) {
            t.key.removePrefix("widget:").toIntOrNull()?.let { widgets.remove(it) }
        }
        setTiles(tiles.filter { it.key != t.key })
    }

    fun openNotifAccess() {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    fun addLive(key: String) {
        if (tiles.none { it.key == key }) {
            setTiles(tiles + Tile(key, if (key == "live:weather") TileSize.WIDE else TileSize.MEDIUM))
        }
        if (key == "live:weather" && !hasLoc) locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (key == "live:photos" && photoUris.isEmpty()) photoLauncher.launch(arrayOf("image/*"))
        if (key == "live:notif" && !notifConnected) openNotifAccess()
    }

    fun onLiveClick(key: String) {
        when (key) {
            "live:weather" -> if (!hasLoc) {
                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                weatherTick++
            }
            "live:photos" -> photoLauncher.launch(arrayOf("image/*"))
            "live:notif" -> if (!notifConnected) openNotifAccess()
        }
    }

    val appMap = remember(apps) { apps.associateBy { it.key } }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !editing,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                    StartScreen(
                        tiles = tiles,
                        apps = appMap,
                        accent = accent,
                        editing = editing,
                        notifs = notifs,
                        notifConnected = notifConnected,
                        weather = weather,
                        weatherStatus = weatherStatus,
                        photos = photos,
                        widgets = widgets,
                        onEdit = { editing = it },
                        onTiles = { setTiles(it) },
                        onRemove = { removeTile(it) },
                        onOpen = { launchApp(ctx, it) },
                        onLiveClick = { onLiveClick(it) },
                        onAddLive = { addLive(it) },
                        onAddWidget = { showWidgetPicker = true }
                    )
                } else {
                    AppListScreen(
                        apps = apps,
                        accent = accent,
                        fg = fg,
                        onOpen = { launchApp(ctx, it) },
                        onPin = { pin(it) },
                        onSettings = { showSettings = true }
                    )
                }
            }
        }
        if (showSettings) {
            SettingsDialog(
                accent = accent,
                dark = dark,
                onAccent = { accent = it; store.accent = it.toArgb() },
                onDark = { dark = it; store.dark = it },
                onNotifAccess = { openNotifAccess() },
                onDismiss = { showSettings = false }
            )
        }
        if (showWidgetPicker) {
            val density = ctx.resources.displayMetrics.density
            WidgetPickerDialog(
                ctl = widgets,
                onPick = { info ->
                    showWidgetPicker = false
                    widgets.begin(info) { id ->
                        if (id != null) {
                            setTiles(tiles + Tile("widget:$id", sizeForProvider(info, density)))
                        }
                    }
                },
                onDismiss = { showWidgetPicker = false }
            )
        }
    }
}
