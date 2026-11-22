package com.geofilho.dominogjieshu.ui

import android.content.SharedPreferences
import com.geofilho.dominogjieshu.game.Level

/** Histórico de partidas (melhor de 5) salvo no aparelho. */
class History(private val prefs: SharedPreferences) {

    private fun get(key: String) = prefs.getInt(key, 0)
    private fun inc(key: String) = prefs.edit().putInt(key, get(key) + 1).apply()
    private fun friendKey(name: String) = name.trim().lowercase()

    fun recordCpu(level: Level, won: Boolean) = inc("h_cpu_${level.name}_${if (won) "w" else "l"}")
    fun recordLocal(winner: Int) = inc("h_local_p${winner + 1}")
    fun recordBt(friend: String, won: Boolean) {
        val k = friendKey(friend)
        inc("h_bt_${k}_${if (won) "w" else "l"}")
        // Guarda o nome como foi digitado, para poder listar os amigos.
        val names = prefs.getStringSet("h_bt_names", emptySet())!!.toMutableSet()
        names.removeAll { friendKey(it) == k }
        names += friend.trim()
        prefs.edit().putStringSet("h_bt_names", names).apply()
    }

    private fun partidas(n: Int) = if (n == 1) "1 partida" else "$n partidas"

    fun cpuText(): String = buildString {
        append("Histórico contra o computador. ")
        var any = false
        for (l in Level.entries) {
            val w = get("h_cpu_${l.name}_w"); val lo = get("h_cpu_${l.name}_l")
            if (w + lo == 0) continue
            any = true
            append("Nível ${l.label}: você ganhou ${partidas(w)} e perdeu ${partidas(lo)}. ")
        }
        if (!any) append("Nenhuma partida terminada ainda.")
    }

    fun localText(): String {
        val p1 = get("h_local_p1"); val p2 = get("h_local_p2")
        return if (p1 + p2 == 0) "Histórico no mesmo aparelho: nenhuma partida terminada ainda."
        else "Histórico no mesmo aparelho: Jogador 1 ganhou ${partidas(p1)}, Jogador 2 ganhou ${partidas(p2)}."
    }

    fun btText(currentFriend: String?): String = buildString {
        append("Histórico pelo Bluetooth. ")
        val names = prefs.getStringSet("h_bt_names", emptySet())!!
            .sortedBy { if (currentFriend != null && friendKey(it) == friendKey(currentFriend)) 0 else 1 }
        if (names.isEmpty()) { append("Nenhuma partida terminada ainda."); return@buildString }
        for (n in names) {
            val k = friendKey(n)
            append("Contra $n: você ganhou ${partidas(get("h_bt_${k}_w"))} e perdeu ${partidas(get("h_bt_${k}_l"))}. ")
        }
    }
}
