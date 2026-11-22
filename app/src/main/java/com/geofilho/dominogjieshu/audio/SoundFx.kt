package com.geofilho.dominogjieshu.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sons de pedra de dominó gerados por síntese (sem arquivos de áudio no APK):
 *  - [playTile]: "clack" de uma pedra batendo na mesa.
 *  - [playShuffle]: pedras sendo embaralhadas/rolando na mesa.
 * Os PCMs são gerados uma única vez, em background, e tocados com AudioTrack estático.
 */
class SoundFx {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var clack: ShortArray? = null
    @Volatile private var shuffle: ShortArray? = null
    @Volatile private var draw: ShortArray? = null
    @Volatile private var applause: ShortArray? = null
    @Volatile private var open: ShortArray? = null
    @Volatile private var beep1: ShortArray? = null
    @Volatile private var beep2: ShortArray? = null
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    init {
        scope.launch {
            val rnd = Random(7)
            clack = synthClack(rnd, 1f, 2900f)
            draw = synthClack(rnd, 0.45f, 2300f)
            shuffle = synthShuffle(rnd)
            applause = synthApplause(rnd)
            open = synthOpen(rnd)
            beep1 = synthBeeps(1)
            beep2 = synthBeeps(2)
        }
    }

    fun playTile() = play(clack)
    fun playDraw() = play(draw)
    fun playShuffle() = play(shuffle)
    fun playApplause() = play(applause)
    /** Som de "abrir" (porta rangendo + trinco), usado em Mostrar pedras. */
    fun playOpen() = play(open)
    /** Um bipe = passe para o Jogador 1; dois bipes = passe para o Jogador 2. */
    fun playBeeps(count: Int) = play(if (count == 1) beep1 else beep2)

    /** Silencia todos os efeitos sonoros (a fala não é afetada). */
    @Volatile var muted = false

    private fun play(pcm: ShortArray?) {
        if (muted) return
        pcm ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setNotificationMarkerPosition(pcm.size - 1)
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) { t.release() }
                    override fun onPeriodicNotification(t: AudioTrack) {}
                })
                track.play()
            }
        }
    }

    fun release() { /* tracks se liberam sozinhas ao terminar */ }

    private companion object {
        const val RATE = 22050

        /** Impacto curto: ruído + duas ressonâncias amortecidas (som de osso/resina na madeira). */
        fun synthClack(rnd: Random, gain: Float, freq: Float): ShortArray {
            val n = (RATE * 0.12).toInt()
            val out = ShortArray(n)
            val f2 = freq * 1.73f
            val low = 180f
            for (i in 0 until n) {
                val t = i / RATE.toFloat()
                val noise = (rnd.nextFloat() * 2 - 1) * exp(-t * 260f)
                val ring = sin(2 * PI * freq * t).toFloat() * exp(-t * 70f) * 0.55f +
                    sin(2 * PI * f2 * t).toFloat() * exp(-t * 110f) * 0.3f
                val thump = sin(2 * PI * low * t).toFloat() * exp(-t * 40f) * 0.35f
                val v = (noise * 0.6f + ring + thump) * gain
                out[i] = (v.coerceIn(-1f, 1f) * 26000).toInt().toShort()
            }
            return out
        }

        /** Bipes de 880 Hz (120 ms cada, 110 ms de intervalo) com bordas suaves. */
        fun synthBeeps(count: Int): ShortArray {
            val beep = (RATE * 0.12).toInt()
            val gap = (RATE * 0.11).toInt()
            val n = count * beep + (count - 1) * gap
            val out = ShortArray(n)
            val fade = (RATE * 0.008).toInt()
            for (k in 0 until count) {
                val off = k * (beep + gap)
                for (j in 0 until beep) {
                    val env = minOf(1f, j / fade.toFloat(), (beep - j) / fade.toFloat())
                    val v = sin(2 * PI * 880.0 * j / RATE).toFloat() * env * 0.55f
                    out[off + j] = (v * 32767).toInt().toShort()
                }
            }
            return out
        }

        /** ~0,7 s: trinco destravando, rangido subindo de tom e um sopro de ar (porta abrindo). */
        fun synthOpen(rnd: Random): ShortArray {
            val n = (RATE * 0.7).toInt()
            val mix = FloatArray(n)
            // trinco
            val click = synthClack(rnd, 0.5f, 1800f)
            for (j in click.indices) mix[j] += click[j] / 26000f
            // rangido: dente-de-serra com tom subindo e pequenas irregularidades
            var phase = 0f
            val start = (RATE * 0.06).toInt()
            for (i in start until n) {
                val t = (i - start) / (n - start).toFloat()
                val f = 170f + 260f * t + (rnd.nextFloat() - 0.5f) * 40f
                phase += f / RATE
                if (phase > 1f) phase -= 1f
                val saw = phase * 2 - 1
                val grain = if (rnd.nextFloat() < 0.6f) 1f else 0.4f // aspereza do rangido
                val env = sin(PI * t).toFloat()
                mix[i] += saw * grain * env * 0.22f
            }
            // sopro de ar
            var lp = 0f
            for (i in 0 until n) {
                val t = i / n.toFloat()
                lp += (0.02f + 0.2f * t) * ((rnd.nextFloat() * 2 - 1) - lp)
                mix[i] += lp * sin(PI * t).toFloat() * 0.5f
            }
            return ShortArray(n) { (mix[it].coerceIn(-1f, 1f) * 26000).toInt().toShort() }
        }

        /** ~3 s de aplausos: centenas de palmas curtas (ruído filtrado) sobrepostas. */
        fun synthApplause(rnd: Random): ShortArray {
            val n = (RATE * 3.0).toInt()
            val mix = FloatArray(n)
            val clapLen = (RATE * 0.03).toInt()
            repeat(420) {
                val start = (rnd.nextFloat() * (n - clapLen)).toInt()
                val gain = 0.15f + rnd.nextFloat() * 0.25f
                // cada palma: ruído passa-banda simples com decaimento rápido
                var lp = 0f; var prev = 0f
                val cut = 0.25f + rnd.nextFloat() * 0.35f
                for (j in 0 until clapLen) {
                    val x = rnd.nextFloat() * 2 - 1
                    lp += cut * (x - lp)
                    val bp = lp - prev; prev = lp
                    val env = exp(-j / (RATE * 0.006f))
                    mix[start + j] += bp * env * gain * 3f
                }
            }
            // envelope geral: entra rápido, mantém e some no último segundo
            for (i in 0 until n) {
                val t = i / RATE.toFloat()
                val env = minOf(1f, t / 0.25f) * minOf(1f, (3.0f - t) / 1.2f)
                mix[i] *= env.coerceAtLeast(0f)
            }
            return ShortArray(n) { (mix[it].coerceIn(-1f, 1f) * 26000).toInt().toShort() }
        }

        /** ~1,6 s de vários cliques aleatórios mais um "arrastar" de fundo. */
        fun synthShuffle(rnd: Random): ShortArray {
            val n = (RATE * 1.6).toInt()
            val mix = FloatArray(n)
            // arrasto: ruído grave filtrado
            var lp = 0f
            for (i in 0 until n) {
                val env = sin(PI * i / n).toFloat()
                lp += 0.08f * ((rnd.nextFloat() * 2 - 1) - lp)
                mix[i] += lp * 0.5f * env
            }
            repeat(70) {
                val start = (rnd.nextFloat() * (n - RATE * 0.12f)).toInt()
                val c = synthClack(rnd, 0.25f + rnd.nextFloat() * 0.45f, 2000f + rnd.nextFloat() * 1800f)
                for (j in c.indices) {
                    val k = start + j
                    if (k < n) mix[k] += c[j] / 26000f
                }
            }
            return ShortArray(n) { (mix[it].coerceIn(-1f, 1f) * 24000).toInt().toShort() }
        }
    }
}
