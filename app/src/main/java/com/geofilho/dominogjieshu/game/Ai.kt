package com.geofilho.dominogjieshu.game

import kotlin.random.Random

enum class Level(val label: String) { FACIL("Fácil"), MEDIO("Médio"), DIFICIL("Difícil") }

/** Jogador do computador, em três níveis. */
object Ai {
    fun choose(round: Round, player: Int, level: Level = Level.MEDIO, random: Random = Random.Default): Move? {
        val moves = round.playable(player).flatMap { t -> round.sidesFor(t).map { Move(player, t, it) } }
        if (moves.isEmpty()) return null
        return when (level) {
            Level.FACIL -> moves.random(random)
            Level.MEDIO -> moves.maxBy { medium(round, it) }
            Level.DIFICIL -> moves.maxBy { hard(round, it) }
        }
    }

    /** Pontas que ficam na mesa depois da jogada. */
    private fun endsAfter(round: Round, m: Move): Pair<Int, Int> = when {
        round.board.isEmpty() -> m.tile.a to m.tile.b
        m.side == Side.LEFT -> m.tile.other(round.leftEnd!!) to round.rightEnd!!
        else -> round.leftEnd!! to m.tile.other(round.rightEnd!!)
    }

    /** Guloso: livra-se de pedras pesadas e carroças; prefere abrir pontas que ainda tem na mão. */
    private fun medium(round: Round, m: Move): Int {
        val hand = round.hands[m.player]
        val (l, r) = endsAfter(round, m)
        val newEnd = if (m.side == Side.LEFT) l else r
        val keep = hand.count { it !== m.tile && it.has(newEnd) }
        return m.tile.pips * 2 + (if (m.tile.isDouble) 6 else 0) + keep * 3
    }

    /**
     * Difícil: além do guloso, usa o que sabe do adversário (números que ele não tem)
     * e conta pedras já vistas para deixar pontas que o adversário dificilmente tem.
     */
    private fun hard(round: Round, m: Move): Int {
        val me = m.player
        val opp = round.next(me) // quem joga logo depois (com 2 jogadores, o adversário)
        val hand = round.hands[me]
        if (hand.size == 1) return 10_000 // bate
        val (l, r) = endsAfter(round, m)
        val rest = hand.filter { it !== m.tile }
        var score = m.tile.pips * 2 + (if (m.tile.isDouble) 8 else 0)
        // Continuar tendo jogada nas pontas que ficam.
        val follow = rest.count { it.has(l) || it.has(r) }
        score += follow * 4
        if (follow == 0) score -= 10
        for (v in setOf(l, r)) {
            // Pedras com esse número que já estão visíveis (mesa + minha mão + a que vou jogar).
            val seen = round.board.count { it.tile.has(v) } + rest.count { it.has(v) } + (if (m.tile.has(v)) 1 else 0)
            score += seen * 2
            if (seen >= 7) score += 12 // o adversário com certeza não tem esse número
            if (v in round.lacks[opp]) score += 10 // ele já mostrou que não tem
        }
        // Mão variada: evita ficar com muitas pedras de um número só.
        val counts = IntArray(7)
        rest.forEach { counts[it.a]++; if (!it.isDouble) counts[it.b]++ }
        score += counts.count { it > 0 } * 2
        return score
    }
}
