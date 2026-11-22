package com.geofilho.dominogjieshu.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.toArgb

/**
 * Pedra da sua mão como item nativo do Android (mesmo desenho da versão do Compose).
 * Sendo nativa, o app consegue pôr o foco do TalkBack direto nela — por exemplo, na pedra
 * que você acabou de comprar e que encaixa.
 */
class NativeTileView(context: Context) : View(context) {
    /** Número de cima e de baixo (a pedra fica em pé). */
    var topValue = 0; private set
    var bottomValue = 0; private set

    private val density = resources.displayMetrics.density
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Ivory.toArgb() }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.IvoryEdge.toArgb(); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val pip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Pip.toArgb() }
    private val rect = RectF()

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // Lida como botão pelo TalkBack.
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        }
    }

    fun setTile(top: Int, bottom: Int, description: String) {
        if (topValue != top || bottomValue != bottom) { topValue = top; bottomValue = bottom; invalidate() }
        if (contentDescription?.toString() != description) contentDescription = description
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (UNIT_DP * density).toInt()
        setMeasuredDimension(w, w * 2)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val inset = 1.5f
        val r = w * 0.16f
        rect.set(inset + 2f, inset + 3f, w - inset + 2f, h - inset + 3f)
        canvas.drawRoundRect(rect, r, r, shadow)
        rect.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rect, r, r, body)
        canvas.drawRoundRect(rect, r, r, edge)
        val half = w
        edge.strokeWidth = 2.5f
        canvas.drawLine(half * 0.18f, half, w - half * 0.18f, half, edge)
        edge.strokeWidth = 2f
        drawPips(canvas, topValue, 0f, half)
        drawPips(canvas, bottomValue, half, half)
    }

    private fun drawPips(canvas: Canvas, v: Int, originY: Float, cell: Float) {
        val p = PIPS[v]
        val step = cell * 0.27f
        val start = cell * 0.23f
        val radius = cell * 0.085f
        var i = 0
        while (i < p.size) {
            canvas.drawCircle(start + p[i] * step, originY + start + p[i + 1] * step, radius, pip)
            i += 2
        }
    }

    companion object {
        const val UNIT_DP = 34f

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
    }
}
