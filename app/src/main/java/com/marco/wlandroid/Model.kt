package com.marco.wlandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject

enum class TileSize(val w: Int, val h: Int) {
    SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2), LARGE(4, 4)
}


/** key = "pkg/class" for apps, "live:clock" for the live clock tile. */
data class Tile(
    val key: String,
    val w: Int,
    val h: Int,
    val color: Int? = null,
    val label: String? = null
) {
    /** Classe di dimensione usata dai contenuti per scegliere font e layout. */
    val size: TileSize
        get() = when {
            w == 1 && h == 1 -> TileSize.SMALL
            w * h <= 4 -> TileSize.MEDIUM
            else -> TileSize.WIDE
        }

    /** Etichetta di sezione a tutta larghezza (key = "hdr:Titolo"). */
    val isHeader: Boolean get() = key.startsWith("hdr:")
}

data class AppInfo(val label: String, val pkg: String, val cls: String, val icon: ImageBitmap) {
    val key: String get() = "$pkg/$cls"
}

data class Placed(val idx: Int, val tile: Tile, val col: Int, val row: Int, val hdrBefore: Int)

const val COLUMNS = 4

fun isSpecialKey(k: String): Boolean =
    listOf("sys:", "hdr:", "live:", "widget:", "act:", "link:", "contact:", "cube:", "group:").any { k.startsWith(it) }

/** Nome visibile di un divisore: key = "hdr:<uid>|<nome>" oppure "hdr:<nome>". */
fun headerName(key: String): String {
    val r = key.removePrefix("hdr:")
    return if (r.contains('|')) r.substringAfter('|') else r
}

/** True se il tile ha un'etichetta testuale che si può rinominare. */
fun supportsLabel(key: String, isApp: Boolean): Boolean =
    isApp || key.startsWith("act:") || key.startsWith("link:") || key.startsWith("contact:") ||
        key.startsWith("group:") || key.startsWith("sys:apps") || key.startsWith("hdr:")

fun newUid(): String = java.lang.Long.toString(System.nanoTime(), 36)
const val ADD_KEY = "sys:add"

/** La tile "+" è sempre presente e non si può eliminare. */
fun withAddTile(l: List<Tile>): List<Tile> =
    if (l.any { it.key == ADD_KEY }) l else l + Tile(ADD_KEY, 1, 1)

/**
 * First-fit packing su 4 colonne. Le etichette di sezione spezzano il flusso: occupano una
 * fascia a tutta larghezza (alta quanto l'header, non una riga di celle) e le tile successive
 * ripartono sotto di essa. Restituisce (tile posizionate, righe di celle, numero di header).
 */
fun packTiles(tiles: List<Tile>): Triple<List<Placed>, Int, Int> {
    val occ = mutableListOf<BooleanArray>()
    fun ensure(r: Int) {
        while (occ.size <= r) occ.add(BooleanArray(COLUMNS))
    }
    val out = mutableListOf<Placed>()
    var hdrs = 0
    var baseRow = 0
    tiles.forEachIndexed { i, t ->
        if (t.isHeader) {
            baseRow = occ.size
            out.add(Placed(i, t, 0, baseRow, hdrs))
            hdrs++
        } else {
            var r = baseRow
            var done = false
            while (!done) {
                var c = 0
                while (c + t.w <= COLUMNS && !done) {
                    ensure(r + t.h - 1)
                    var free = true
                    for (dr in 0 until t.h) for (dc in 0 until t.w) {
                        if (occ[r + dr][c + dc]) free = false
                    }
                    if (free) {
                        for (dr in 0 until t.h) for (dc in 0 until t.w) {
                            occ[r + dr][c + dc] = true
                        }
                        out.add(Placed(i, t, c, r, hdrs))
                        done = true
                    } else {
                        c++
                    }
                }
                if (!done) r++
            }
        }
    }
    return Triple(out, occ.size, hdrs)
}

// ---------- Start predefinita ----------

private fun pickApp(ctx: Context, apps: List<AppInfo>, pkgs: List<String>, intent: Intent?): AppInfo? {
    for (p in pkgs) apps.firstOrNull { it.pkg == p }?.let { return it }
    if (intent != null) {
        val list = try {
            ctx.packageManager.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        for (ri in list) apps.firstOrNull { it.pkg == ri.activityInfo.packageName }?.let { return it }
    }
    return null
}

private fun mainCat(cat: String) = Intent(Intent.ACTION_MAIN).addCategory(cat)

/** Layout iniziale: meteo + orologio, poi le sezioni Contact, Camera & Photos e Apps. */
fun defaultTiles(ctx: Context, apps: List<AppInfo>): List<Tile> {
    val phone = pickApp(ctx, apps, listOf("com.google.android.dialer"), Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
    val messages = pickApp(ctx, apps, listOf("com.google.android.apps.messaging"), mainCat(Intent.CATEGORY_APP_MESSAGING))
    val gmail = pickApp(ctx, apps, listOf("com.google.android.gm"), mainCat(Intent.CATEGORY_APP_EMAIL))
    val contacts = pickApp(ctx, apps, listOf("com.google.android.contacts"), mainCat(Intent.CATEGORY_APP_CONTACTS))
    val camera = pickApp(ctx, apps, emptyList(), Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    val photos = pickApp(ctx, apps, listOf("com.google.android.apps.photos"), mainCat(Intent.CATEGORY_APP_GALLERY))
    val chrome = pickApp(ctx, apps, listOf("com.android.chrome"), mainCat(Intent.CATEGORY_APP_BROWSER))
    val play = pickApp(ctx, apps, listOf("com.android.vending"), null)
    val calc = pickApp(ctx, apps, listOf("com.google.android.calculator"), mainCat(Intent.CATEGORY_APP_CALCULATOR))

    val out = mutableListOf<Tile>()
    val used = mutableSetOf<String>()
    out.add(Tile("live:weather", 2, 1))
    out.add(Tile("live:clock", 2, 1))

    fun section(title: String, vararg items: Pair<AppInfo?, Int>) {
        val found = items.filter { it.first != null }
        if (found.isEmpty()) return
        out.add(Tile("hdr:$title", COLUMNS, 1))
        for ((a, w) in found) {
            if (a != null && used.add(a.key)) out.add(Tile(a.key, w, 1))
        }
    }
    section("Contact", phone to 1, messages to 1, gmail to 1, contacts to 1)
    section("Camera & Photos", camera to 2, photos to 2)

    out.add(Tile("hdr:Apps", COLUMNS, 1))
    for (a in listOf(chrome, play)) if (a != null && used.add(a.key)) out.add(Tile(a.key, 1, 1))
    out.add(Tile("sys:apps", 1, 1))
    out.add(Tile(ADD_KEY, 1, 1))
    if (calc != null && used.add(calc.key)) out.add(Tile(calc.key, 1, 1))
    return out
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("wl", Context.MODE_PRIVATE)

    /** True se la Start è già stata salvata almeno una volta (installazione esistente). */
    fun hasSavedTiles(): Boolean = p.contains("tiles")

    fun extra(key: String): JSONObject? {
        val s = p.getString("x:$key", null) ?: return null
        return try {
            JSONObject(s)
        } catch (e: Exception) {
            null
        }
    }

    fun putExtra(key: String, o: JSONObject) = p.edit().putString("x:$key", o.toString()).apply()

    fun removeExtra(key: String) = p.edit().remove("x:$key").apply()

    fun loadTiles(): List<Tile> {
        val s = p.getString("tiles", null) ?: return withAddTile(emptyList())
        return try {
            val a = JSONArray(s)
            withAddTile((0 until a.length()).map {
                val o = a.getJSONObject(it)
                if (o.has("w")) {
                    Tile(
                        o.getString("k"),
                        o.getInt("w").coerceIn(1, COLUMNS),
                        o.getInt("h").coerceIn(1, 12),
                        if (o.has("c")) o.getInt("c") else null,
                        if (o.has("l")) o.getString("l") else null
                    )
                } else {
                    val sz = TileSize.valueOf(o.getString("s"))
                    Tile(o.getString("k"), sz.w, sz.h)
                }
            })
        } catch (e: Exception) {
            withAddTile(emptyList())
        }
    }

    fun saveTiles(list: List<Tile>) {
        val a = JSONArray()
        list.forEach { t ->
            val o = JSONObject().put("k", t.key).put("w", t.w).put("h", t.h)
            t.color?.let { o.put("c", it) }
            t.label?.let { o.put("l", it) }
            a.put(o)
        }
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
