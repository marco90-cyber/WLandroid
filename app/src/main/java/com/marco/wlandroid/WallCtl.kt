package com.marco.wlandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Impostazioni di sfondo ed effetti, salvate nello Store e osservabili da Compose. */
class FxSettings(private val st: Store) {
    var wallDim by mutableIntStateOf(st.wallDim)
        private set
    var wallBlur by mutableIntStateOf(st.wallBlur)
        private set
    var wallParallax by mutableStateOf(st.wallParallax)
        private set
    var tileFx by mutableIntStateOf(st.tileFx)
        private set
    var fxStrength by mutableIntStateOf(st.fxStrength)
        private set

    fun setWallDim(v: Int) { wallDim = v; st.wallDim = v }
    fun setWallBlur(v: Int) { wallBlur = v; st.wallBlur = v }
    fun setWallParallax(v: Boolean) { wallParallax = v; st.wallParallax = v }
    fun setTileFx(v: Int) { tileFx = v; st.tileFx = v }
    fun setFxStrength(v: Int) { fxStrength = v; st.fxStrength = v }
}

/** Sfondo della Home: immagine, editor (ritaglio e colori), sfondi inclusi e importazione. */
class WallCtl(private val ctx: Context, private val scope: CoroutineScope) {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    var darkText by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableIntStateOf(0)
        private set
    /** immagine aperta nell'editor (null = editor chiuso) */
    var editSrc by mutableStateOf<Bitmap?>(null)
        private set
    var screenPx by mutableStateOf(IntSize.Zero)

    fun load() {
        val f = wallFile(ctx)
        if (!f.exists()) {
            // sfondo della versione precedente: lo porto nel nuovo formato
            val old = File(ctx.filesDir, "home_wall.png")
            if (old.exists()) {
                setWallpaperFrom { BitmapFactory.decodeFile(old.path) }
                return
            }
            bitmap = null
            return
        }
        scope.launch {
            val bm = withContext(Dispatchers.IO) { try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null } }
            if (bm == null) return@launch
            val colors = withContext(Dispatchers.Default) {
                try { android.app.WallpaperColors.fromBitmap(bm) } catch (e: Throwable) { null }
            }
            darkText = colors != null && (colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            bitmap = bm.asImageBitmap()
        }
    }

    fun setWallpaperFrom(keepSource: Boolean = true, produce: () -> Bitmap?) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bm = produce() ?: return@withContext false
                    java.io.FileOutputStream(wallFile(ctx)).use { bm.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    if (keepSource) java.io.FileOutputStream(wallSrcFile(ctx)).use { bm.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    true
                } catch (e: Throwable) {
                    false
                }
            }
            busy = false
            if (ok) this@WallCtl.load() else error += 1
        }
    }

    /** foto scelta dalla galleria: apre l'editor */
    fun picked(uri: Uri) = openEditor { decodeScaled({ ctx.contentResolver.openInputStream(uri) }, 2600) }

    fun builtin(key: String) = setWallpaperFrom {
        val (w, h) = target()
        builtinWall(key, w, h)
    }

    private fun systemBitmap(): Bitmap? {
        val d = try { android.app.WallpaperManager.getInstance(ctx).drawable } catch (e: Throwable) { null } ?: return null
        return try {
            val bm = d.toBitmap()
            val big = max(bm.width, bm.height)
            if (big <= 2600) bm else Bitmap.createScaledBitmap(bm, bm.width * 2600 / big, bm.height * 2600 / big, true)
        } catch (e: Throwable) {
            null
        }
    }

    fun importSystem() = openEditor { systemBitmap() }

    fun editCurrent() = openEditor {
        val f = wallSrcFile(ctx).takeIf { it.exists() } ?: wallFile(ctx)
        BitmapFactory.decodeFile(f.path)
    }

    private fun openEditor(load: () -> Bitmap?) {
        if (busy) return
        busy = true
        scope.launch {
            val bm = withContext(Dispatchers.IO) { try { load() } catch (e: Throwable) { null } }
            busy = false
            if (bm == null) error += 1 else editSrc = bm
        }
    }

    fun closeEditor() { editSrc = null }

    fun remove() {
        wallFile(ctx).delete()
        wallSrcFile(ctx).delete()
        File(ctx.filesDir, "home_wall.png").delete()
        bitmap = null
        darkText = false
    }

    /** misura dello sfondo salvato: alto come lo schermo, largo abbastanza per la parallasse */
    fun wallTarget(): Pair<Int, Int> = target()

    private fun target(): Pair<Int, Int> {
        val sw = if (screenPx.width > 0) screenPx.width else ctx.resources.displayMetrics.widthPixels
        val sh = if (screenPx.height > 0) screenPx.height else ctx.resources.displayMetrics.heightPixels
        return (minOf(sw, sh) * WALL_WIDE).toInt() to max(sw, sh)
    }

    fun applyWallEdit(src: Bitmap, crop: android.graphics.RectF, matrix: FloatArray?) {
        editSrc = null
        setWallpaperFrom(keepSource = false) {
            val (w, h) = target()
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            if (matrix != null) p.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            c.drawBitmap(
                src,
                android.graphics.Rect(crop.left.toInt(), crop.top.toInt(), crop.right.toInt(), crop.bottom.toInt()),
                android.graphics.Rect(0, 0, w, h),
                p
            )
            try {
                java.io.FileOutputStream(wallSrcFile(ctx)).use { src.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            } catch (e: Throwable) { }
            out
        }
    }

    fun toLockScreen(): Boolean = try {
        val bm = BitmapFactory.decodeFile(wallFile(ctx).path)
        android.app.WallpaperManager.getInstance(ctx).setBitmap(bm, null, true, android.app.WallpaperManager.FLAG_LOCK)
        true
    } catch (e: Throwable) {
        false
    }
}
