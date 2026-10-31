package com.marco.wlandroid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Effetti grafici delle tile (impostazione globale: vale per tutte le tile colorate). */
object Fx {
    const val NONE = 0
    const val TRANSPARENT = 1
    const val FROSTED = 2
    const val LIQUID = 3
    const val GRADIENT = 4
    const val NEON = 5
    const val OUTLINE = 6
    const val METAL = 7
    const val BEVEL = 8

    val NAMES = listOf(
        NONE to "Normale",
        TRANSPARENT to "Trasparente",
        FROSTED to "Vetro smerigliato",
        LIQUID to "Liquid Glass",
        GRADIENT to "Sfumatura",
        NEON to "Neon",
        OUTLINE to "Contorno",
        METAL to "Metallo",
        BEVEL to "Rilievo 3D"
    )

    /** effetti "di vetro": usano lo sfondo e danno il vetro anche a barre e finestre */
    fun glassy(e: Int) = e == TRANSPARENT || e == FROSTED || e == LIQUID
}

/** Stato globale dell'interfaccia: pannelli di vetro (barra lista app, finestre) e testo scuro sopra lo sfondo. */
object GlassUi {
    var on by androidx.compose.runtime.mutableStateOf(false)
    var darkText by androidx.compose.runtime.mutableStateOf(false)
}

private fun lighten(c: Color, f: Float) = lerp(c, Color.White, f)
private fun darken(c: Color, f: Float) = lerp(c, Color.Black, f)

/**
 * Sfondo di una tile con l'effetto scelto. Va messo come primo figlio del Box della tile
 * (che ha già la forma ritagliata); [strength] va da 0 a 1.
 */
@Composable
fun TileFxLayer(effect: Int, strength: Float, color: Color, shape: Shape, corner: Dp) {
    val s = strength.coerceIn(0f, 1f)
    when (effect) {
        Fx.TRANSPARENT -> Box(Modifier.fillMaxSize().background(color.copy(alpha = (0.92f - 0.8f * s).coerceIn(0.08f, 1f))))
        Fx.FROSTED -> GlassLayer(
            corner, shape, tint = color, tintAlpha = 0.55f - 0.35f * s,
            blurDp = 8f + 24f * s, lens = false, veil = 0.05f
        )
        Fx.LIQUID -> GlassLayer(
            corner, shape, tint = color, tintAlpha = 0.45f - 0.33f * s,
            blurDp = 10f + 6f * s, lens = true, veil = 0.06f
        )
        Fx.GRADIENT -> Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    listOf(lighten(color, 0.10f + 0.30f * s), color, darken(color, 0.15f + 0.45f * s)),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                )
            )
        }
        Fx.NEON -> Canvas(Modifier.fillMaxSize()) {
            val r = corner.toPx()
            drawRect(Color(0xFF08090E))
            drawRect(color.copy(alpha = 0.10f + 0.10f * s))
            val g = 1.dp.toPx() + 2.dp.toPx() * s
            for ((w, a) in listOf(10f to 0.10f, 6f to 0.18f, 3f to 0.35f)) {
                val sw = w.dp.toPx() * (0.6f + s)
                drawRoundRect(color.copy(alpha = a), Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), CornerRadius(r), style = Stroke(sw))
            }
            val core = 2.dp.toPx() + g / 2
            drawRoundRect(lighten(color, 0.35f), Offset(core / 2, core / 2), Size(size.width - core, size.height - core), CornerRadius(r), style = Stroke(core))
        }
        Fx.OUTLINE -> Canvas(Modifier.fillMaxSize()) {
            val r = corner.toPx()
            drawRect(color.copy(alpha = 0.06f + 0.22f * (1f - s)))
            val sw = 3.dp.toPx()
            drawRoundRect(color, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), CornerRadius(r), style = Stroke(sw))
        }
        Fx.METAL -> Canvas(Modifier.fillMaxSize()) {
            val hi = 0.25f + 0.5f * s
            drawRect(
                Brush.linearGradient(
                    0f to lighten(color, hi * 0.6f),
                    0.28f to color,
                    0.46f to lighten(color, hi),
                    0.62f to darken(color, 0.30f * (0.5f + s)),
                    0.82f to color,
                    1f to lighten(color, hi * 0.5f),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                )
            )
            drawRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.35f to Color.Transparent)
            )
        }
        Fx.BEVEL -> Canvas(Modifier.fillMaxSize()) {
            val r = corner.toPx()
            drawRect(color)
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.16f + 0.2f * s),
                    0.5f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.18f + 0.22f * s),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                )
            )
            val sw = 2.5.dp.toPx()
            drawRoundRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.8f),
                    0.5f to Color.White.copy(alpha = 0.05f),
                    1f to Color.Black.copy(alpha = 0.55f),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                ),
                Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), CornerRadius(r), style = Stroke(sw)
            )
        }
        else -> Box(Modifier.fillMaxSize().background(color))
    }
}
