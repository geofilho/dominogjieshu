package com.geofilho.dominogjieshu.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Voz do aplicativo (TextToSpeech em português do Brasil). */
class Voice(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed = false
    private val pending = ArrayList<() -> Unit>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val ids = AtomicInteger()
    var enabled = true
    val isReady: Boolean get() = ready

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            pending.clear()
            callbacks.values.forEach { main.post(it) }; callbacks.clear()
            return
        }
        val br = Locale("pt", "BR")
        if (tts.setLanguage(br) < 0) tts.setLanguage(Locale("pt"))
        tts.setSpeechRate(1.05f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) = fire(id)
            override fun onStop(id: String, interrupted: Boolean) = fire(id)
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) = fire(id)
        })
        ready = true
        pending.forEach { it() }
        pending.clear()
    }

    private fun fire(id: String) { callbacks.remove(id)?.let { main.post(it) } }

    private fun newId(onDone: (() -> Unit)?): String {
        val id = "u" + ids.incrementAndGet()
        if (onDone != null) callbacks[id] = onDone
        return id
    }

    /** [flush] = true interrompe o que estava falando. */
    fun speak(text: String, flush: Boolean = false) {
        if (!enabled || text.isBlank()) return
        if (!ready) { if (failed) return; if (flush) pending.clear(); pending += { speak(text, flush) }; return }
        if (flush && callbacks.isNotEmpty()) {
            // Uma fala que interrompe a fila não pode apagar pedidos "depois da fala" (ex.: foco):
            // eles são executados agora.
            val waiting = callbacks.values.toList(); callbacks.clear()
            waiting.forEach { main.post(it) }
        }
        tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle.EMPTY, newId(null))
    }

    /** Executa [action] quando tudo o que está na fila de fala terminar. */
    fun afterQueue(action: () -> Unit) {
        if (!enabled || failed) { main.post(action); return }
        if (!ready) { pending += { afterQueue(action) }; return }
        tts.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, newId(action))
    }

    fun stop() { if (ready) tts.stop() }
    fun shutdown() = tts.shutdown()
}
