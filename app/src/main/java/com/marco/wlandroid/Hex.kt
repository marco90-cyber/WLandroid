package com.marco.wlandroid

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Esagono con la punta in alto, inscritto nel rettangolo (l, t, w, h). */
fun hexPath(l: Float, t: Float, w: Float, h: Float, grow: Float = 0f): Path = Path().apply {
    val cx = l + w / 2f
    moveTo(cx, t - grow)
    lineTo(l + w + grow, t + h * 0.25f)
    lineTo(l + w + grow, t + h * 0.75f)
    lineTo(cx, t + h + grow)
    lineTo(l - grow, t + h * 0.75f)
    lineTo(l - grow, t + h * 0.25f)
    close()
}

/**
 * Forma "a nido d'ape" di una tile di w x h celle: unione degli esagoni delle sue celle.
 * Le righe dispari sono spostate di mezzo esagono verso destra.
 */
data class HexShape(
    val w: Int,
    val h: Int,
    val rowOdd: Boolean,
    val cw: Float,
    val hh: Float,
    val rp: Float
) : Shape {
    fun path(): Path {
        val half = cw / 2f
        fun off(dr: Int) = if (((if (rowOdd) 1 else 0) + dr) % 2 == 1) half else 0f
        var minOff = Float.MAX_VALUE
        for (dr in 0 until h) minOff = minOf(minOff, off(dr))
        var res: Path? = null
        for (dr in 0 until h) {
            for (dc in 0 until w) {
                val p = hexPath(dc * cw + off(dr) - minOff, dr * rp, cw, hh, 0.6f)
                res = if (res == null) p else Path.combine(PathOperation.Union, res, p)
            }
        }
        return res ?: Path()
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(path())
}
