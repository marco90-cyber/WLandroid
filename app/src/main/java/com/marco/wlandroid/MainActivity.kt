package com.marco.wlandroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val WALL_FILE = "home_wall.png"

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
    val data = remember { TileData(ctx, store) }
    var needsSeed by remember { mutableStateOf(!store.hasSavedTiles()) }
    var tiles by remember { mutableStateOf(store.loadTiles()) }
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var accent by remember { mutableStateOf(Color(store.accent)) }
    var dark by remember { mutableStateOf(store.dark) }
    var editing by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(store.locked) }
    var orient by remember { mutableIntStateOf(store.orientation) }
    var landFill by remember { mutableStateOf(store.landscapeFill) }
    var appGrid by remember { mutableStateOf(store.appGrid) }
    var showMenu by remember { mutableStateOf(false) }
    var moveMode by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var extraTick by remember { mutableIntStateOf(0) }
    var iconTarget by remember { mutableStateOf<String?>(null) }
    var imageTarget by remember { mutableStateOf<String?>(null) }
    var extraTarget by remember { mutableStateOf<String?>(null) }
    var pendingPick by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rounded by remember { mutableStateOf(store.rounded) }
    var radius by remember { mutableIntStateOf(store.radius) }
    var pinchOn by remember { mutableStateOf(store.pinch) }
    var showLayout by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var wall by remember { mutableStateOf(data.bitmap(WALL_FILE)) }
    var showSettings by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    var showCubePicker by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }
    var dividerTarget by remember { mutableStateOf<String?>(null) }
    var optionsTarget by remember { mutableStateOf<String?>(null) }
    var styleTarget by remember { mutableStateOf<String?>(null) }
    var labelTarget by remember { mutableStateOf<String?>(null) }
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

    val wallPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { loadWallpaperBitmap(ctx, uri) }
                if (bmp != null) {
                    data.saveBitmap(WALL_FILE, bmp)
                    wall = data.bitmap(WALL_FILE)
                }
            }
        }
    }

    fun editExtra(k: String, block: (JSONObject) -> Unit) {
        val ex = store.extra(k) ?: JSONObject()
        block(ex)
        store.putExtra(k, ex)
        extraTick++
    }
    fun clearExtraImage(k: String, field: String) = editExtra(k) { ex ->
        val old = ex.optString(field)
        if (old.isNotEmpty()) data.removeFile(old)
        ex.remove(field)
    }
    val tileImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val pp = pendingPick
        pendingPick = null
        if (uri != null && pp != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { loadWallpaperBitmap(ctx, uri) }
                if (bmp != null) {
                    val fn = "g${pp.second}_${newUid()}.png"
                    data.saveBitmap(fn, bmp)
                    editExtra(pp.first) { ex ->
                        val old = ex.optString(pp.second)
                        if (old.isNotEmpty()) data.removeFile(old)
                        ex.put(pp.second, fn)
                    }
                }
            }
        }
    }

    LaunchedEffect(orient) {
        (ctx as Activity).requestedOrientation = when (orient) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
    BackHandler(moveMode) { moveMode = false }
    BackHandler(showLayout) { showLayout = false }
    BackHandler(!showLayout && (editing || pager.currentPage == 1)) {
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
        val fixed = withAddTile(l)
        tiles = fixed
        store.saveTiles(fixed)
    }

    fun addTile(t: Tile) = setTiles(placeNew(tiles, t))

    fun pin(app: AppInfo) {
        if (tiles.none { it.key == app.key }) addTile(Tile(app.key, 1, 1))
        scope.launch { pager.animateScrollToPage(0) }
    }

    fun removeTile(t: Tile) {
        if (t.key == ADD_KEY) return
        if (t.key.startsWith("widget:")) {
            t.key.removePrefix("widget:").toIntOrNull()?.let { widgets.remove(it) }
        }
        data.cleanup(t.key)
        setTiles(tiles.filter { it.key != t.key })
    }

    fun addContact(uri: Uri) {
        try {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    val lk = c.getString(1)
                    val name = c.getString(2) ?: ""
                    val lookup = ContactsContract.Contacts.getLookupUri(id, lk)
                    val uid = newUid()
                    var photoFile = ""
                    ContactsContract.Contacts.openContactPhotoInputStream(ctx.contentResolver, uri, true)?.use { st ->
                        BitmapFactory.decodeStream(st)?.let { bmp ->
                            photoFile = "ph_$uid.png"
                            data.saveBitmap(photoFile, bmp)
                        }
                    }
                    store.putExtra(
                        "contact:$uid",
                        JSONObject().put("name", name).put("uri", lookup.toString()).put("photo", photoFile)
                    )
                    addTile(Tile("contact:$uid", 1, 1))
                }
            }
        } catch (e: Exception) {
        }
    }

    fun handleShortcut(d: Intent?) {
        if (d == null) return
        val sIntent = d.parcel(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java) ?: return
        val name = d.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Collegamento"
        var bmp: Bitmap? = d.parcel(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        if (bmp == null) {
            val r = d.parcel(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
            if (r != null) {
                try {
                    val pc = ctx.createPackageContext(r.packageName, 0)
                    val rid = pc.resources.getIdentifier(r.resourceName, null, null)
                    bmp = androidx.core.content.res.ResourcesCompat.getDrawable(pc.resources, rid, null)
                        ?.toBitmap(128, 128)
                } catch (e: Exception) {
                }
            }
        }
        val uid = newUid()
        var iconFile = ""
        if (bmp != null) {
            iconFile = "ico_$uid.png"
            data.saveBitmap(iconFile, bmp)
        }
        store.putExtra(
            "link:$uid",
            JSONObject().put("name", name)
                .put("intent", sIntent.toUri(Intent.URI_INTENT_SCHEME))
                .put("icon", iconFile)
        )
        addTile(Tile("link:$uid", 1, 1))
    }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) addContact(uri)
    }
    val createShortcut = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) handleShortcut(res.data)
    }
    val pickShortcut = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val d = res.data
        if (res.resultCode == Activity.RESULT_OK && d != null) {
            try {
                createShortcut.launch(Intent(d))
            } catch (e: Exception) {
            }
        }
    }

    fun saveDivider(name: String) {
        val target = dividerTarget ?: return
        if (target.isEmpty()) {
            addTile(Tile("hdr:${newUid()}|$name", BASE_COLUMNS, 1))
        } else {
            val raw = target.removePrefix("hdr:")
            val uid = if (raw.contains('|')) raw.substringBefore('|') else newUid()
            setTiles(tiles.map { if (it.key == target) it.copy(key = "hdr:$uid|$name") else it })
        }
        dividerTarget = null
    }

    fun performAction(id: String) {
        when (id) {
            "apps" -> scope.launch { pager.animateScrollToPage(1) }
            "settings" -> showSettings = true
            "notif" -> expandNotifications(ctx)
            "torch" -> toggleTorch(ctx)
            "sys" -> openSettings(ctx, Settings.ACTION_SETTINGS)
            "wifi" -> openSettings(ctx, Settings.ACTION_WIFI_SETTINGS)
            "bt" -> openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
        }
    }

    fun onAddPick(id: String) {
        showAddMenu = false
        when (id) {
            "app" -> showAppPicker = true
            "link" -> try {
                pickShortcut.launch(
                    Intent(Intent.ACTION_PICK_ACTIVITY)
                        .putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_CREATE_SHORTCUT))
                        .putExtra(Intent.EXTRA_TITLE, "Collegamento")
                )
            } catch (e: Exception) {
            }
            "action" -> showActionPicker = true
            "widget" -> showWidgetPicker = true
            "divider" -> dividerTarget = ""
            "cube" -> showCubePicker = true
            "group" -> showGroupPicker = true
            "drawer" -> addTile(Tile("sys:apps#${newUid()}", 1, 1))
            "contact" -> try {
                contactPicker.launch(null)
            } catch (e: Exception) {
            }
        }
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
            addTile(Tile(key, 1, 1))
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
            else -> when {
                key.startsWith("sys:apps") -> scope.launch { pager.animateScrollToPage(1) }
                key.startsWith("act:") -> performAction(key.removePrefix("act:").substringBefore('#'))
            }
        }
    }

    fun resetLayout() {
        tiles.forEach { t ->
            if (t.key.startsWith("widget:")) t.key.removePrefix("widget:").toIntOrNull()?.let { widgets.remove(it) }
        }
        setTiles(defaultTiles(ctx, apps))
    }

    LaunchedEffect(apps) {
        if (needsSeed && apps.isNotEmpty()) {
            needsSeed = false
            setTiles(defaultTiles(ctx, apps))
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
                .pinchIn(pinchOn && !showLayout && pager.currentPage == 0 && !editing) { showLayout = true }
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !editing && !moveMode,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                  Box(Modifier.fillMaxSize()) {
                    wall?.let {
                        androidx.compose.foundation.Image(
                            it, null, Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    StartScreen(
                        tiles = tiles,
                        apps = appMap,
                        accent = accent,
                        editing = editing,
                        locked = locked,
                        moveMode = moveMode,
                        onPlusMenu = { showMenu = true },
                        fill = landFill,
                        tick = extraTick,
                        corner = if (rounded) listOf(8, 16, 28)[radius.coerceIn(0, 2)] else 0,
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
                        data = data,
                        onLiveClick = { onLiveClick(it) },
                        onOpenLink = { k ->
                            val u = data.extra(k)?.optString("intent").orEmpty()
                            if (u.isNotEmpty()) {
                                try {
                                    ctx.startActivity(
                                        Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: Exception) {
                                }
                            }
                        },
                        onOpenContact = { k ->
                            val u = data.extra(k)?.optString("uri").orEmpty()
                            if (u.isNotEmpty()) {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: Exception) {
                                }
                            }
                        },
                        onOptions = { optionsTarget = it.key },
                        onAddMenu = { showAddMenu = true }
                    )
                  }
                } else {
                    AppListScreen(
                        apps = apps,
                        accent = accent,
                        fg = fg,
                        onOpen = { launchApp(ctx, it) },
                        onPin = { pin(it) },
                        grid = appGrid,
                        onGrid = { appGrid = it; store.appGrid = it },
                        onSettings = { showSettings = true }
                    )
                }
            }

            if (moveMode) {
                Box(
                    Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(24.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                        .background(Color(0xFF15171C))
                        .clickable { moveMode = false }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("Fine", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
                }
            }
        }
        if (showMenu) {
            HomeMenuDialog(
                locked = locked,
                onOptions = { showMenu = false; showSettings = true },
                onSystem = { showMenu = false; openSettings(ctx, Settings.ACTION_SETTINGS) },
                onWallpaper = { showMenu = false; showWallpaper = true },
                onLock = {
                    showMenu = false
                    locked = !locked
                    store.locked = locked
                    editing = false
                },
                onMove = { showMenu = false; editing = false; moveMode = true },
                onReset = { showMenu = false; showResetConfirm = true },
                onInfo = { showMenu = false; showInfo = true },
                onDismiss = { showMenu = false }
            )
        }
        if (showInfo) {
            val ver = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("WLandroid") },
                text = { Text("Versione $ver\nLauncher in stile Windows Phone") },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } }
            )
        }
        if (showLayout) {
            LayoutOverview(
                tiles = tiles,
                accent = accent,
                wallpaper = wall,
                locked = locked,
                onClose = { showLayout = false },
                onApps = {
                    showLayout = false
                    scope.launch { pager.animateScrollToPage(1) }
                },
                onOptions = { showSettings = true },
                onWallpaper = { showWallpaper = true },
                onToggleLock = {
                    locked = !locked
                    store.locked = locked
                    editing = false
                },
                onReset = { showResetConfirm = true },
                onTheme = { showSettings = true }
            )
        }
        if (showWallpaper) {
            WallpaperDialog(
                hasWallpaper = wall != null,
                onPick = {
                    showWallpaper = false
                    try {
                        wallPicker.launch("image/*")
                    } catch (e: Exception) {
                    }
                },
                onRemove = {
                    showWallpaper = false
                    data.removeFile(WALL_FILE)
                    wall = null
                },
                onDismiss = { showWallpaper = false }
            )
        }
        if (showSettings) {
            HomeOptionsScreen(
                accent = accent,
                dark = dark,
                locked = locked,
                pinch = pinchOn,
                rounded = rounded,
                radius = radius,
                appGrid = appGrid,
                onAppGrid = { appGrid = it; store.appGrid = it },
                landscapeFill = landFill,
                onLandscapeFill = { landFill = it; store.landscapeFill = it },
                orientation = orient,
                onOrientation = { orient = it; store.orientation = it },
                onRounded = { rounded = it; store.rounded = it },
                onRadius = { radius = it; store.radius = it },
                isDefault = remember(resumeTick, showSettings) { isDefaultHome(ctx) },
                onAccent = { accent = it; store.accent = it.toArgb() },
                onDark = { dark = it; store.dark = it },
                onLocked = { locked = it; store.locked = it; editing = false },
                onPinch = { pinchOn = it; store.pinch = it },
                onSetDefault = { chooseDefaultHome(ctx) },
                onNotifAccess = { openNotifAccess() },
                onReset = { showSettings = false; showResetConfirm = true },
                onClose = { showSettings = false }
            )
        }
        optionsTarget?.let { k ->
          if (k.startsWith("group:")) {
            GroupOptionsDialog(
                onStyle = { optionsTarget = null; styleTarget = k },
                onIcon = { optionsTarget = null; iconTarget = k },
                onLabel = { optionsTarget = null; labelTarget = k },
                onImage = { optionsTarget = null; imageTarget = k },
                onExtra = { optionsTarget = null; extraTarget = k },
                onDismiss = { optionsTarget = null }
            )
          } else {
            OptionsDialog(
                showLabel = supportsLabel(k, appMap.containsKey(k)),
                onStyle = {
                    optionsTarget = null
                    styleTarget = k
                },
                onLabel = {
                    optionsTarget = null
                    if (k.startsWith("hdr:")) dividerTarget = k else labelTarget = k
                },
                onDismiss = { optionsTarget = null }
            )
          }
        }
        iconTarget?.let { k ->
            ImageChoiceDialog(
                title = "ICONA",
                hasImage = !store.extra(k)?.optString("icon").isNullOrEmpty(),
                onPick = {
                    iconTarget = null
                    pendingPick = k to "icon"
                    try { tileImagePicker.launch("image/*") } catch (e: Exception) { }
                },
                onRemove = { iconTarget = null; clearExtraImage(k, "icon") },
                onDismiss = { iconTarget = null }
            )
        }
        imageTarget?.let { k ->
            ImageChoiceDialog(
                title = "IMMAGINE INTERA",
                hasImage = !store.extra(k)?.optString("image").isNullOrEmpty(),
                onPick = {
                    imageTarget = null
                    pendingPick = k to "image"
                    try { tileImagePicker.launch("image/*") } catch (e: Exception) { }
                },
                onRemove = { imageTarget = null; clearExtraImage(k, "image") },
                onDismiss = { imageTarget = null }
            )
        }
        extraTarget?.let { k ->
            GroupExtraDialog(
                showLabel = store.extra(k)?.optBoolean("hideLabel", false) != true,
                onShowLabel = { v -> editExtra(k) { it.put("hideLabel", !v) } },
                onDismiss = { extraTarget = null }
            )
        }
        styleTarget?.let { k ->
            StyleDialog(
                current = tiles.firstOrNull { it.key == k }?.color,
                onPick = { c ->
                    styleTarget = null
                    setTiles(tiles.map { if (it.key == k) it.copy(color = c) else it })
                },
                onDismiss = { styleTarget = null }
            )
        }
        labelTarget?.let { k ->
            val defLabel = when {
                appMap.containsKey(k) -> appMap[k]?.label
                k.startsWith("act:") -> actionById(k.removePrefix("act:").substringBefore('#'))?.label
                k.startsWith("sys:apps") -> "Applicazioni"
                else -> data.extra(k)?.optString("name")
            }.orEmpty()
            TextInputDialog(
                title = "Etichetta",
                initial = tiles.firstOrNull { it.key == k }?.label ?: defLabel,
                onConfirm = { text ->
                    labelTarget = null
                    setTiles(tiles.map { if (it.key == k) it.copy(label = text) else it })
                },
                onDismiss = { labelTarget = null }
            )
        }
        if (showAddMenu) {
            AddMenuDialog(onPick = { onAddPick(it) }, onDismiss = { showAddMenu = false })
        }
        if (showActionPicker) {
            ActionPickerDialog(
                onPick = { id ->
                    showActionPicker = false
                    addTile(Tile("act:$id#${newUid()}", 1, 1))
                },
                onDismiss = { showActionPicker = false }
            )
        }
        dividerTarget?.let { tgt ->
            TextInputDialog(
                title = if (tgt.isEmpty()) "Nuovo divisore" else "Rinomina divisore",
                initial = if (tgt.isEmpty()) "" else headerName(tgt),
                onConfirm = { saveDivider(it) },
                onDismiss = { dividerTarget = null }
            )
        }
        if (showCubePicker) {
            MultiAppPicker(
                title = "Cubo: scegli le app",
                apps = apps,
                askName = false,
                minSelected = 2,
                onConfirm = { _, keys ->
                    showCubePicker = false
                    val uid = newUid()
                    store.putExtra("cube:$uid", JSONObject().put("apps", JSONArray(keys)))
                    addTile(Tile("cube:$uid", 1, 1))
                },
                onDismiss = { showCubePicker = false }
            )
        }
        if (showGroupPicker) {
            GroupPickerDialog(
                apps = apps,
                onConfirm = { keys, folder ->
                    showGroupPicker = false
                    if (folder) {
                        val uid = newUid()
                        store.putExtra("group:$uid", JSONObject().put("name", "Gruppo").put("apps", JSONArray(keys)))
                        addTile(Tile("group:$uid", 2, 1))
                    } else {
                        keys.forEach { k -> if (tiles.none { it.key == k }) addTile(Tile(k, 1, 1)) }
                    }
                },
                onDismiss = { showGroupPicker = false }
            )
        }
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Ripristinare la Start?") },
                text = { Text("Le tile attuali verranno sostituite dal layout predefinito.") },
                confirmButton = {
                    TextButton(onClick = {
                        showResetConfirm = false
                        resetLayout()
                    }) { Text("Ripristina") }
                },
                dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Annulla") } }
            )
        }
        if (showAppPicker) {
            AppPickerDialog(
                apps = apps,
                onPick = {
                    showAppPicker = false
                    pin(it)
                },
                onDismiss = { showAppPicker = false }
            )
        }
        if (showWidgetPicker) {
            val density = ctx.resources.displayMetrics.density
            WidgetPickerDialog(
                ctl = widgets,
                builtins = listOf(
                    "live:weather" to "Meteo (WLandroid)",
                    "live:photos" to "Foto (WLandroid)",
                    "live:notif" to "Notifiche (WLandroid)",
                    "live:clock" to "Orologio (WLandroid)"
                ),
                onBuiltin = { showWidgetPicker = false; addLive(it) },
                onPick = { info ->
                    showWidgetPicker = false
                    widgets.begin(info) { id ->
                        if (id != null) {
                            val (cw, ch) = sizeForProvider(info, density)
                            addTile(Tile("widget:$id", cw, ch))
                        }
                    }
                },
                onDismiss = { showWidgetPicker = false }
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun <T : android.os.Parcelable> Intent.parcel(name: String, cls: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(name, cls) else getParcelableExtra<T>(name)
