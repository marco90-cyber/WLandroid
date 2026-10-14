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
    val label: String? = null,
    /** Posizione sulla griglia (colonna, riga). -1 = da assegnare. */
    val col: Int = -1,
    val row: Int = -1
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

/** Assegna le posizioni mancanti e garantisce che la tile "+" sia sempre presente. */
fun withAddTile(l: List<Tile>): List<Tile> {
    val a = if (l.any { it.col < 0 || it.row < 0 }) autoPlace(l) else l
    if (a.any { it.key == ADD_KEY }) return a
    val bottom = a.maxOfOrNull { it.row + it.h } ?: 0
    return a + Tile(ADD_KEY, 1, 1, col = 0, row = bottom)
}

// ---------- Griglia libera: ogni tile ha la propria posizione ----------

private fun isFree(others: List<Tile>, col: Int, row: Int, w: Int, h: Int): Boolean =
    others.none { it.col < col + w && col < it.col + it.w && it.row < row + h && row < it.row + it.h }

/** Primo spazio libero (colonna, riga), cercando per righe a partire da (fromCol, fromRow). */
fun findFree(others: List<Tile>, w: Int, h: Int, fromRow: Int, fromCol: Int): Pair<Int, Int> {
    var r = fromRow
    var c = fromCol
    while (true) {
        if (c + w > COLUMNS) {
            r++
            c = 0
            continue
        }
        if (isFree(others, c, r, w, h)) return c to r
        c++
    }
}

fun overlaps(a: Tile, b: Tile): Boolean =
    a.col < b.col + b.w && b.col < a.col + a.w && a.row < b.row + b.h && b.row < a.row + a.h

/**
 * Assegna una posizione alle tile che non ce l'hanno, nell'ordine della lista (come il vecchio
 * impilamento): un divisore va su una riga nuova e le tile successive ripartono sotto di esso.
 */
fun autoPlace(tiles: List<Tile>): List<Tile> {
    val out = ArrayList<Tile>(tiles.size)
    val occ = ArrayList<Tile>()
    var baseRow = 0
    for (t in tiles) {
        val placed = when {
            t.col >= 0 && t.row >= 0 -> t
            t.isHeader -> {
                val bottom = occ.maxOfOrNull { it.row + it.h } ?: 0
                val (c, r) = findFree(occ, t.w, t.h, maxOf(bottom, baseRow), 0)
                t.copy(col = c, row = r)
            }
            else -> {
                val (c, r) = findFree(occ, t.w, t.h, baseRow, 0)
                t.copy(col = c, row = r)
            }
        }
        if (placed.isHeader) baseRow = placed.row + 1
        out.add(placed)
        occ.add(placed)
    }
    return out
}

/**
 * La tile `movedKey` è già nella sua nuova posizione/dimensione: le tile che la sovrappongono
 * vengono spinte verso il basso (a cascata). Gli spazi lasciati vuoti restano vuoti.
 */
fun resolveLayout(tiles: List<Tile>, movedKey: String): List<Tile> {
    val moved = tiles.firstOrNull { it.key == movedKey } ?: return tiles
    val settled = arrayListOf(moved)
    val result = HashMap<String, Tile>()
    result[movedKey] = moved
    val others = tiles.filter { it.key != movedKey }.sortedWith(compareBy({ it.row }, { it.col }))
    for (x in others) {
        var cur = x
        var guard = 0
        while (guard++ < 500) {
            val hit = settled.firstOrNull { overlaps(cur, it) } ?: break
            cur = cur.copy(row = hit.row + hit.h)
        }
        settled.add(cur)
        result[cur.key] = cur
    }
    return tiles.map { result[it.key] ?: it }
}

/** Mette la tile `key` in testa alla lista: è quella con la priorità quando due tile si sovrappongono. */
fun toFront(tiles: List<Tile>, key: String): List<Tile> {
    val t = tiles.firstOrNull { it.key == key } ?: return tiles
    return listOf(t) + tiles.filter { it.key != key }
}

/**
 * Posizioni mostrate: le posizioni salvate sono quelle "volute"; le tile sovrapposte (la prima della
 * lista ha la priorità) vengono spinte in basso solo a video, e tornano al loro posto appena si libera spazio.
 */
fun resolveAll(tiles: List<Tile>): List<Tile> {
    val first = tiles.firstOrNull() ?: return tiles
    return resolveLayout(tiles, first.key)
}

/** Aggiunge una nuova tile nel primo spazio libero a partire dal "+", poi sposta il "+" dopo di essa. */
fun placeNew(tiles: List<Tile>, t: Tile): List<Tile> {
    val shown = resolveAll(tiles)
    val plus = shown.firstOrNull { it.key == ADD_KEY }
    val base = shown.filter { it.key != ADD_KEY }
    val bottom = base.maxOfOrNull { it.row + it.h } ?: 0
    val (sc, sr) = if (plus != null && plus.col >= 0) plus.col to plus.row else 0 to bottom
    val (c, r) = findFree(base, t.w, t.h, sr, sc)
    val placed = t.copy(col = c, row = r)
    val (pc, pr) = findFree(base + placed, 1, 1, r, c + t.w)
    val others = tiles.filter { it.key != ADD_KEY && it.key != placed.key }
    return listOf(placed) + others + (plus ?: Tile(ADD_KEY, 1, 1)).copy(col = pc, row = pr)
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
                        if (o.has("l")) o.getString("l") else null,
                        o.optInt("x", -1),
                        o.optInt("y", -1)
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
            val o = JSONObject().put("k", t.key).put("w", t.w).put("h", t.h).put("x", t.col).put("y", t.row)
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

    var locked: Boolean
        get() = p.getBoolean("locked", false)
        set(v) = p.edit().putBoolean("locked", v).apply()

    var pinch: Boolean
        get() = p.getBoolean("pinch", true)
        set(v) = p.edit().putBoolean("pinch", v).apply()

    var rounded: Boolean
        get() = p.getBoolean("rounded", false)
        set(v) = p.edit().putBoolean("rounded", v).apply()

    /** 0 = piccola, 1 = media, 2 = grande */
    var radius: Int
        get() = p.getInt("radius", 0)
        set(v) = p.edit().putInt("radius", v).apply()

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
