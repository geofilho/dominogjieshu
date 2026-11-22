package com.geofilho.dominogjieshu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Posições das pintas numa grade 3x3 (coluna, linha) para os valores 0..6. */
private val PIPS: Array<IntArray> = arrayOf(
    intArrayOf(),
    intArrayOf(1, 1),
    intArrayOf(0, 0, 2, 2),
    intArrayOf(0, 0, 1, 1, 2, 2),
    intArrayOf(0, 0, 2, 0, 0, 2, 2, 2),
    intArrayOf(0, 0, 2, 0, 1, 1, 0, 2, 2, 2),
    intArrayOf(0, 0, 0, 1, 0, 2, 2, 0, 2, 1, 2, 2),
)

/**
 * Desenha uma pedra. [first] fica em cima (vertical) ou à esquerda (horizontal).
 * [unit] é a largura da face menor.
 */
@Composable
fun DominoTile(
    first: Int,
    second: Int,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    unit: Dp = 34.dp,
    highlight: Boolean = false,
    faceDown: Boolean = false,
) {
    val w = if (vertical) unit else unit * 2
    val h = if (vertical) unit * 2 else unit
    Canvas(modifier.size(w, h)) {
        val r = CornerRadius(size.minDimension * 0.16f)
        val inset = 1.5f
        val body = Size(size.width - inset * 2, size.height - inset * 2)
        drawRoundRect(Color(0x55000000), Offset(inset + 2f, inset + 3f), body, r)
        drawRoundRect(if (faceDown) Palette.Back else Palette.Ivory, Offset(inset, inset), body, r)
        drawRoundRect(
            if (highlight) Palette.Highlight else Palette.IvoryEdge, Offset(inset, inset), body, r,
            style = Stroke(if (highlight) size.minDimension * 0.09f else 2f),
        )
        if (faceDown) return@Canvas
        val half = size.minDimension
        if (vertical) {
            drawLine(Palette.IvoryEdge, Offset(half * 0.18f, half), Offset(size.width - half * 0.18f, half), 2.5f)
            pips(first, Offset(0f, 0f), half)
            pips(second, Offset(0f, half), half)
        } else {
            drawLine(Palette.IvoryEdge, Offset(half, half * 0.18f), Offset(half, size.height - half * 0.18f), 2.5f)
            pips(first, Offset(0f, 0f), half)
            pips(second, Offset(half, 0f), half)
        }
    }
}

private fun DrawScope.pips(v: Int, origin: Offset, cell: Float) {
    val p = PIPS[v]
    val step = cell * 0.27f
    val start = cell * 0.23f
    val radius = cell * 0.085f
    var i = 0
    while (i < p.size) {
        drawCircle(Palette.Pip, radius, Offset(origin.x + start + p[i] * step, origin.y + start + p[i + 1] * step))
        i += 2
    }
}
