package com.geofilho.dominogjieshu.game

import kotlin.random.Random

enum class EndReason { BATIDA, TRANCADO }

data class RoundResult(
    /** Índice do jogador vencedor (com 4 jogadores, a dupla dele vence) ou -1 em empate. */
    val winner: Int,
    val reason: EndReason,
    val points: Int,
)

data class Move(val player: Int, val tile: Tile, val side: Side)

/**
 * Uma rodada de dominó para 2, 3 ou 4 jogadores (regra brasileira comum):
 *  - 2 jogadores: 7 pedras cada, 14 no monte.
 *  - 3 jogadores: 7 pedras cada, 7 no monte (cada um por si).
 *  - 4 jogadores: 7 pedras cada, sem monte; duplas alternadas (0 e 2 contra 1 e 3).
 * Quem não tem jogada compra do monte; monte vazio → passa. Bate quem zerar a mão; se ninguém
 * puder jogar, tranca e vence quem tiver menos pontos na mão (com 4, a dupla dele).
 */
class Round private constructor(
    /** Número de jogadores (2 a 4). */
    val n: Int,
    val hands: List<MutableList<Tile>>,
    val board: ArrayDeque<Placed>,
    val boneyard: MutableList<Tile>,
    current: Int,
    /** Pedra obrigatória na primeira jogada, ou null (hoje não é usada: a saída é livre). */
    var openingTile: Tile?,
    passes: Int,
    result: RoundResult?,
) {
    var current: Int = current; private set
    var consecutivePasses: Int = passes; private set
    var result: RoundResult? = result; private set
    var lastMove: Move? = null; private set

    /** Números que cada jogador mostrou não ter (comprou ou passou com essas pontas). Usado pela IA difícil. */
    val lacks: List<MutableSet<Int>> = List(n) { HashSet() }

    /** Com 4 jogadores há duplas: o lado (dupla) de cada jogador. Com 2 ou 3, cada um é um lado. */
    fun sideOf(p: Int) = if (n == 4) p % 2 else p
    val sides: Int get() = if (n == 4) 2 else n

    fun next(p: Int) = (p + 1) % n

    private fun noteLacking(player: Int) {
        leftEnd?.let { lacks[player].add(it) }
        rightEnd?.let { lacks[player].add(it) }
    }

    val isOver get() = result != null
    val leftEnd get() = board.firstOrNull()?.left
    val rightEnd get() = board.lastOrNull()?.right

    fun sidesFor(tile: Tile): List<Side> {
        if (board.isEmpty()) return if (openingTile == null || openingTile === tile) listOf(Side.RIGHT) else emptyList()
        val l = leftEnd!!; val r = rightEnd!!
        val out = ArrayList<Side>(2)
        if (tile.has(l)) out += Side.LEFT
        if (tile.has(r) && (l != r || !tile.has(l))) out += Side.RIGHT
        // Pontas iguais: jogar em qualquer lado dá no mesmo, oferece só um.
        return out
    }

    fun playable(player: Int): List<Tile> = hands[player].filter { sidesFor(it).isNotEmpty() }
    fun canDraw(player: Int) = !isOver && player == current && playable(player).isEmpty() && boneyard.isNotEmpty()
    fun mustPass(player: Int) = !isOver && player == current && playable(player).isEmpty() && boneyard.isEmpty()

    fun play(player: Int, tile: Tile, side: Side): Boolean {
        if (isOver || player != current) return false
        val hand = hands[player]
        if (!hand.contains(tile)) return false
        if (side !in sidesFor(tile) && !(board.isEmpty() && sidesFor(tile).isNotEmpty())) return false
        if (board.isEmpty()) {
            board.addLast(Placed(tile, tile.a, tile.b))
            openingTile = null
        } else if (side == Side.LEFT) {
            val l = leftEnd!!
            board.addFirst(Placed(tile, tile.other(l), l))
        } else {
            val r = rightEnd!!
            board.addLast(Placed(tile, r, tile.other(r)))
        }
        hand.remove(tile)
        lacks[player].remove(tile.a); lacks[player].remove(tile.b)
        lastMove = Move(player, tile, side)
        consecutivePasses = 0
        if (hand.isEmpty()) {
            result = RoundResult(player, EndReason.BATIDA, pointsAgainst(sideOf(player)))
        } else {
            current = next(current)
        }
        return true
    }

    fun draw(player: Int): Tile? {
        if (!canDraw(player)) return null
        noteLacking(player)
        val t = boneyard.removeAt(boneyard.lastIndex)
        hands[player].add(t)
        return t
    }

    fun pass(player: Int): Boolean {
        if (!mustPass(player)) return false
        noteLacking(player)
        consecutivePasses++
        lastMove = null
        if (consecutivePasses >= n) {
            // Trancado: vence quem tiver menos pontos na mão (com 4, a dupla dele).
            val min = (0 until n).minOf { handPips(it) }
            val best = (0 until n).filter { handPips(it) == min }
            result = if (best.map(::sideOf).distinct().size == 1) {
                RoundResult(best.first(), EndReason.TRANCADO, pointsAgainst(sideOf(best.first())))
            } else {
                RoundResult(-1, EndReason.TRANCADO, 0)
            }
        } else {
            current = next(current)
        }
        return true
    }

    fun handPips(player: Int) = hands[player].sumOf { it.pips }

    /** Pontos das mãos dos jogadores que não são do lado [side]. */
    private fun pointsAgainst(side: Int) = (0 until n).filter { sideOf(it) != side }.sumOf { handPips(it) }

    // ---------- Serialização compacta (usada no Bluetooth) ----------
    fun encode(): String = buildString {
        append(n).append(';')
        for (p in 0 until n) append(hands[p].joinToString(",") { it.id.toString() }).append(';')
        append(board.joinToString(",") { "${it.tile.id}:${it.left}:${it.right}" }).append(';')
        append(boneyard.joinToString(",") { it.id.toString() }).append(';')
        append(current).append(';')
        append(openingTile?.id ?: -1).append(';')
        append(consecutivePasses).append(';')
        val r = result
        append(if (r == null) "-" else "${r.winner}:${r.reason.ordinal}:${r.points}").append(';')
        val m = lastMove
        append(if (m == null) "-" else "${m.player}:${m.tile.id}:${m.side.ordinal}")
    }

    companion object {
        /** Distribui as pedras para [n] jogadores. [starterOverride] = quem começa (null = sorteio). */
        fun deal(starterOverride: Int?, random: Random = Random.Default, n: Int = 2): Round {
            require(n in 2..4)
            val deck = Tile.ALL.shuffled(random).toMutableList()
            val hands = List(n) { p -> deck.subList(p * 7, p * 7 + 7).toMutableList() }
            val bone = deck.subList(n * 7, 28).toMutableList()
            // Quem começa é sorteado e joga a pedra que quiser.
            val starter = starterOverride ?: random.nextInt(n)
            return Round(n, hands, ArrayDeque(), bone, starter, null, 0, null)
        }

        private fun ids(s: String) = if (s.isEmpty()) mutableListOf() else s.split(',').map { Tile.byId(it.toInt()) }.toMutableList()

        fun decode(s: String): Round {
            val p = s.split(';')
            val n = p[0].toInt()
            var i = 1
            val hands = List(n) { ids(p[i++]) }
            val board = ArrayDeque<Placed>()
            if (p[i].isNotEmpty()) p[i].split(',').forEach {
                val (id, l, r) = it.split(':').map(String::toInt)
                board.addLast(Placed(Tile.byId(id), l, r))
            }
            i++
            val bone = ids(p[i++])
            val current = p[i++].toInt()
            val opening = p[i++].toInt().takeIf { it >= 0 }?.let(Tile::byId)
            val passes = p[i++].toInt()
            val result = if (p[i] == "-") null else p[i].split(':').map(String::toInt)
                .let { RoundResult(it[0], EndReason.entries[it[1]], it[2]) }
            i++
            val round = Round(n, hands, board, bone, current, opening, passes, result)
            if (p[i] != "-") {
                val (pl, id, sd) = p[i].split(':').map(String::toInt)
                round.lastMove = Move(pl, Tile.byId(id), Side.entries[sd])
            }
            return round
        }
    }
}
