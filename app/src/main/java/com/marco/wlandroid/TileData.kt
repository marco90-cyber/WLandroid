package com.marco.wlandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Dati extra delle tile speciali (collegamenti, contatti, cubi, gruppi) e relative immagini. */
class TileData(private val ctx: Context, private val store: Store) {
    private val cache = mutableMapOf<String, ImageBitmap>()

    fun extra(key: String): JSONObject? = store.extra(key)

    fun bitmap(file: String?): ImageBitmap? {
        if (file.isNullOrEmpty()) return null
        cache[file]?.let { return it }
        val b = try {
            BitmapFactory.decodeFile(File(ctx.filesDir, file).path)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        if (b != null) cache[file] = b
        return b
    }

    fun saveBitmap(file: String, bmp: Bitmap) {
        try {
            FileOutputStream(File(ctx.filesDir, file)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cache.remove(file)
        } catch (e: Exception) {
        }
    }

    fun removeFile(file: String) = deleteFile(file)

    private fun deleteFile(file: String) {
        try {
            File(ctx.filesDir, file).delete()
        } catch (e: Exception) {
        }
        cache.remove(file)
    }

    fun cleanup(key: String) {
        val e = store.extra(key) ?: return
        for (f in listOf("icon", "photo")) {
            val name = e.optString(f)
            if (name.isNotEmpty()) deleteFile(name)
        }
        store.removeExtra(key)
    }
}
