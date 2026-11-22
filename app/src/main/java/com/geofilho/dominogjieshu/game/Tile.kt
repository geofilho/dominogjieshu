package com.geofilho.dominogjieshu.game

/** Pedra de dominó (duplo-seis). Sempre normalizada com [a] <= [b]. */
class Tile private constructor(val a: Int, val b: Int) {
    val id: Int get() = a * 7 + b
    val isDouble: Boolean get() = a == b
    val pips: Int get() = a + b

    fun has(n: Int) = a == n || b == n
    fun other(n: Int) = if (a == n) b else a

    override fun toString() = "[$a|$b]"

    companion object {
        /** As 28 pedras, instâncias únicas (comparação por referência é segura). */
        val ALL: List<Tile> = buildList { for (a in 0..6) for (b in a..6) add(Tile(a, b)) }
        private val BY_ID = arrayOfNulls<Tile>(49).also { arr -> ALL.forEach { arr[it.id] = it } }
        fun of(x: Int, y: Int): Tile = byId(minOf(x, y) * 7 + maxOf(x, y))
        fun byId(id: Int): Tile = BY_ID[id] ?: error("pedra inválida: $id")
    }
}

enum class Side { LEFT, RIGHT }

/** Pedra posicionada na mesa: [left] é o número voltado para a esquerda e [right] para a direita. */
data class Placed(val tile: Tile, val left: Int, val right: Int)

/** Leitura humana de números, usada na voz e no TalkBack. */
object Words {
    private val NUM = arrayOf("branco", "um", "dois", "três", "quatro", "cinco", "seis")
    fun n(v: Int) = NUM[v]
    fun tile(t: Tile) = if (t.isDouble) "carroça de ${n(t.a)}" else "${n(t.b)} e ${n(t.a)}"
}
