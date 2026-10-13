package com.marco.wlandroid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var homeTick by mutableIntStateOf(0)
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        setContent { WLApp(store, homeTick, resumeTick) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick++
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WLApp(store: Store, homeTick: Int, resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tiles by remember { mutableStateOf(store.loadTiles()) }
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var accent by remember { mutableStateOf(Color(store.accent)) }
    var dark by remember { mutableStateOf(store.dark) }
    var editing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val pager = rememberPagerState { 2 }

    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
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

    val appMap = remember(apps) { apps.associateBy { it.key } }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        androidx.compose.foundation.layout.Box(
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
                        onEdit = { editing = it },
                        onTiles = { setTiles(it) },
                        onOpen = { launchApp(ctx, it) }
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
                onDismiss = { showSettings = false }
            )
        }
    }
}
