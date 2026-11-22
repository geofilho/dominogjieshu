package com.geofilho.dominogjieshu.diag

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico (Diagnóstico → chave "Registrar o foco do Jieshuo").
 * Anota para onde o foco do TalkBack vai, toques e momentos do jogo, para ajudar a resolver
 * problemas de foco. Fica só no aparelho; a pessoa compartilha se quiser.
 */
object DiagLog {
    private const val MAX = 1500
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    @Volatile var enabled = false; private set
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("dominog", Context.MODE_PRIVATE)
        enabled = prefs!!.getBoolean("diag_on", false)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("diag_on", on)?.apply()
        add("DIAG", if (on) "registro ligado" else "registro desligado", force = true)
    }

    @Synchronized
    fun add(tag: String, msg: String, force: Boolean = false) {
        if (!enabled && !force) return
        lines.addLast("${time.format(Date())} [$tag] $msg")
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized
    fun text(): String = if (lines.isEmpty()) "(registro vazio)" else lines.joinToString("\n")

    @Synchronized
    fun clear() { lines.clear() }

    val count: Int @Synchronized get() = lines.size

    /** Eventos de acessibilidade do app (vêm da tela, via delegate na janela). */
    /** Hora (ms) do último foco do TalkBack em qualquer item do app (para saber se a pessoa deslizou). */
    @Volatile var lastFocusAt = 0L; private set
    /** Hora (ms) da última vez que o foco saiu de um item. */
    @Volatile var lastClearAt = 0L; private set

    fun a11y(e: AccessibilityEvent) {
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> lastFocusAt = android.os.SystemClock.uptimeMillis()
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> lastClearAt = android.os.SystemClock.uptimeMillis()
        }
        if (!enabled) return
        val type = when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> "FOCO Jieshuo em"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> "foco saiu de"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TOQUE em"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "foco de entrada em"
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> "anúncio"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "janela mudou"
            else -> return // ignora as mudanças de conteúdo (são muitas)
        }
        var text = e.text.joinToString(" ").ifBlank { e.contentDescription?.toString().orEmpty() }
        if (text.isBlank()) {
            // Itens desenhados pelo Compose: o nome vem do próprio item que recebeu o foco.
            text = runCatching {
                val n = e.source
                (n?.contentDescription ?: n?.text)?.toString().orEmpty()
            }.getOrDefault("")
        }
        val cls = e.className?.toString()?.substringAfterLast('.').orEmpty()
        // Item sem nome recebendo foco: anota detalhes para descobrir qual é (id, posição, filhos).
        var extra = ""
        if (text.isBlank() && e.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            extra = runCatching {
                val n = e.source ?: return@runCatching " [sem detalhes]"
                val r = android.graphics.Rect().also { n.getBoundsInScreen(it) }
                val id = n.viewIdResourceName?.substringAfterLast('/') ?: "-"
                " [id=$id, posição=${r.left},${r.top}-${r.right},${r.bottom}, filhos=${n.childCount}, clicável=${n.isClickable}]"
            }.getOrDefault("")
        }
        add("A11Y", "$type \"${text.take(120)}\" ($cls)$extra")
    }
}
