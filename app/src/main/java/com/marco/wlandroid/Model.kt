package com.marco.wlandroid

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject

enum class TileSize(val w: Int, val h: Int) {
    SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2), LARGE(4, 4)
}

fun nextSize(t: Tile): TileSize {
    val all = TileSize.values().toList().filter { !(t.key.startsWith("widget:") && it == TileSize.SMALL) }
    val i = all.indexOf(t.size)
    return all[(i + 1) % all.size]
}

/** key = "pkg/class" for apps, "live:clock" for the live clock tile. */
data class Tile(val key: String, val size: TileSize)

data class AppInfo(val label: String, val pkg: String, val cls: String, val icon: ImageBitmap) {
    val key: String get() = "$pkg/$cls"
}

data class Placed(val idx: Int, val tile: Tile, val col: Int, val row: Int)

const val COLUMNS = 4

/** First-fit packing of tiles on a 4-column grid. Returns placed tiles and row count. */
fun packTiles(tiles: List<Tile>): Pair<List<Placed>, Int> {
    val occ = mutableListOf<BooleanArray>()
    fun ensure(r: Int) {
        while (occ.size <= r) occ.add(BooleanArray(COLUMNS))
    }
    val out = mutableListOf<Placed>()
    tiles.forEachIndexed { i, t ->
        var r = 0
        var done = false
        while (!done) {
            var c = 0
            while (c + t.size.w <= COLUMNS && !done) {
                ensure(r + t.size.h - 1)
                var free = true
                for (dr in 0 until t.size.h) for (dc in 0 until t.size.w) {
                    if (occ[r + dr][c + dc]) free = false
                }
                if (free) {
                    for (dr in 0 until t.size.h) for (dc in 0 until t.size.w) {
                        occ[r + dr][c + dc] = true
                    }
                    out.add(Placed(i, t, c, r))
                    done = true
                } else {
                    c++
                }
            }
            if (!done) r++
        }
    }
    return out to occ.size
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("wl", Context.MODE_PRIVATE)

    fun loadTiles(): List<Tile> {
        val s = p.getString("tiles", null) ?: return listOf(Tile("live:clock", TileSize.WIDE))
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Tile(o.getString("k"), TileSize.valueOf(o.getString("s")))
            }
        } catch (e: Exception) {
            listOf(Tile("live:clock", TileSize.WIDE))
        }
    }

    fun saveTiles(list: List<Tile>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("k", it.key).put("s", it.size.name)) }
        p.edit().putString("tiles", a.toString()).apply()
    }

    var accent: Int
        get() = p.getInt("accent", 0xFF1BA1E2.toInt())
        set(v) = p.edit().putInt("accent", v).apply()

    var dark: Boolean
        get() = p.getBoolean("dark", true)
        set(v) = p.edit().putBoolean("dark", v).apply()

    var weather: Weather?
        get() = parseWeather(p.getString("weather", null))
        set(v) = p.edit().putString("weather", v?.toJson()).apply()

    var photos: List<String>
        get() = (p.getString("photos", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = p.edit().putString("photos", v.joinToString("\n")).apply()
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { r ->
            AppInfo(
                label = r.loadLabel(pm).toString(),
                pkg = r.activityInfo.packageName,
                cls = r.activityInfo.name,
                icon = r.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, app: AppInfo) {
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.pkg, app.cls)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        ctx.startActivity(i)
    } catch (_: Exception) {
    }
}
