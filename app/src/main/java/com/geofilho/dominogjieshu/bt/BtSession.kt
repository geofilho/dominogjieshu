package com.geofilho.dominogjieshu.bt

/** Jogador na sala. */
data class Member(val id: String, val name: String)

/** Envia linhas pelas conexões (no app é o [BtLink]; nos testes, uma rede falsa). */
interface Transport {
    fun send(linkId: Int, line: String)
    fun close(linkId: Int)
}

/**
 * Sala do Bluetooth para até 4 jogadores.
 *
 * As conexões formam uma árvore: cada aparelho tem no máximo um "pai" (o aparelho acima dele) e
 * pode ter vários "filhos". Quem não tem pai é a **raiz**: ela comanda a sala e o jogo.
 * Assim, um amigo pode entrar conectando em qualquer pessoa da sala; as mensagens sobem até a
 * raiz e descem dela para todos.
 *
 * Ao conectar, os dois aparelhos trocam LH (tem pai? tamanho do grupo?) e decidem quem fica acima:
 *  - quem já tem pai fica acima (o outro, sozinho ou raiz do seu grupo, entra embaixo);
 *  - se nenhum tem pai, o grupo maior fica acima; empate: quem recebeu a conexão fica acima;
 *  - se os dois já têm pai, ou o total passaria de 4, ou o jogo já começou, a conexão é recusada.
 *
 * Esta classe não usa Android: é testada com uma rede falsa em RoomTest.
 */
class BtSession(
    val myId: String,
    myName: String,
    private val net: Transport,
    private val listener: Listener,
) {
    interface Listener {
        /** Lista da sala, duplas ou "em jogo" mudaram. */
        fun onRoster() {}
        fun onPhoto(id: String, base64: String) {}
        /** Alguém convidou para começar (mostrar Aceitar/Recusar). */
        fun onInvite(fromId: String) {}
        /** Convite recusado por [byName]. */
        fun onInviteDeclined(byName: String) {}
        /** Só na raiz: todos aceitaram; distribuir as pedras para os lugares [seatIds]. */
        fun onStartGame(seatIds: List<String>) {}
        /** Só na raiz: jogada vinda de outro aparelho (PLAY/DRAW/PASS/NEXT). */
        fun onRootMessage(parts: List<String>) {}
        /** Nos outros: mensagem do jogo vinda da raiz (STATE/ENDGAME). */
        fun onDownMessage(parts: List<String>) {}
        /** Conexão recusada (sala cheia, jogo em andamento...). */
        fun onRejected(reason: String) {}
    }

    private enum class Role { PENDING, PARENT, CHILD }

    private val roles = HashMap<Int, Role>()
    private val accepted = HashMap<Int, Boolean>()
    /** Membros abaixo de cada filho. */
    private val subtrees = HashMap<Int, List<Member>>()

    var myName: String = clean(myName)
        set(v) { field = clean(v); membersChanged() }

    /** Fotos (base64) por id de jogador. */
    val photos = HashMap<String, String>()

    var roster: List<Member> = listOf(Member(myId, this.myName)); private set
    /** Dupla escolhida (com 4 jogadores): ids dos dois parceiros. */
    var partners: Pair<String, String>? = null; private set
    var inGame = false; private set

    private var pendingInviteFrom: String? = null
    private val inviteAccepted = HashSet<String>()

    private val parentLink: Int? get() = roles.entries.firstOrNull { it.value == Role.PARENT }?.key
    private val childLinks: List<Int> get() = roles.filterValues { it == Role.CHILD }.keys.toList()

    /** Este aparelho comanda a sala e o jogo? */
    val isRoot: Boolean get() = parentLink == null
    val hasConnections: Boolean get() = roles.values.any { it != Role.PENDING }

    fun nameOf(id: String) = roster.firstOrNull { it.id == id }?.name ?: "Amigo"

    // ================= Eventos das conexões =================

    fun onOpened(linkId: Int, wasAccepted: Boolean) {
        roles[linkId] = Role.PENDING
        accepted[linkId] = wasAccepted
        net.send(linkId, "${Proto.LINK_HELLO}|${if (parentLink != null) 1 else 0}|${roster.size}|${if (inGame) 1 else 0}")
    }

    fun onClosed(linkId: Int) {
        val role = roles.remove(linkId) ?: return
        accepted.remove(linkId)
        when (role) {
            Role.PENDING -> {}
            Role.CHILD -> {
                subtrees.remove(linkId)
                membersChanged()
            }
            Role.PARENT -> {
                // Perdi a conexão com quem estava acima: agora comando o meu pedaço do grupo.
                partners = null
                if (inGame) {
                    inGame = false
                    val reason = "A conexão com a sala caiu."
                    sendDown("${Proto.ENDGAME}|$reason")
                    listener.onDownMessage(listOf(Proto.ENDGAME, reason))
                }
                recomputeRoster()
            }
        }
    }

    fun onLine(linkId: Int, text: String) {
        val role = roles[linkId] ?: return
        val parts = text.split('|')
        val type = parts[0]
        if (role == Role.PENDING) {
            when (type) {
                Proto.LINK_HELLO -> handshake(linkId, parts)
                Proto.REJECT -> { listener.onRejected(parts.getOrNull(1).orEmpty()); net.close(linkId) }
            }
            return
        }
        if (role == Role.CHILD) fromChild(linkId, text, parts) else fromParent(text, parts)
    }

    private fun handshake(linkId: Int, parts: List<String>) {
        val peerHasParent = parts.getOrNull(1) == "1"
        val peerSize = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val peerInGame = parts.getOrNull(3) == "1"
        val iHaveParent = parentLink != null
        val reason = when {
            inGame || peerInGame -> "O jogo já começou. Espere a partida terminar."
            iHaveParent && peerHasParent -> "Vocês dois já estão em salas diferentes. Um de vocês precisa sair da sala dele."
            roster.size + peerSize > MAX_PLAYERS -> "A sala ficaria com mais de $MAX_PLAYERS jogadores."
            else -> null
        }
        if (reason != null) {
            // Os dois lados chegam à mesma conclusão; cada um avisa a sua pessoa.
            listener.onRejected(reason)
            net.close(linkId)
            roles.remove(linkId)
            return
        }
        val iAmAbove = when {
            iHaveParent -> true
            peerHasParent -> false
            roster.size != peerSize -> roster.size > peerSize
            else -> accepted[linkId] == true
        }
        if (iAmAbove) {
            roles[linkId] = Role.CHILD
            subtrees[linkId] = emptyList() // chega com MEMBERS
        } else {
            roles[linkId] = Role.PARENT
            partners = null
            // Conto ao pai quem está comigo e mando as fotos que conheço.
            sendUp(membersLine())
            photos.forEach { (id, b64) -> sendUp("${Proto.PHOTO}|$id|$b64") }
        }
    }

    private fun fromChild(linkId: Int, text: String, parts: List<String>) {
        when (parts[0]) {
            Proto.MEMBERS -> {
                subtrees[linkId] = parseMembers(parts.getOrNull(1).orEmpty())
                if (isRoot) {
                    // Quem acabou de chegar recebe as fotos de todos.
                    photos.forEach { (id, b64) -> net.send(linkId, "${Proto.PHOTO}|$id|$b64") }
                }
                membersChanged()
            }
            Proto.PHOTO -> {
                storePhoto(parts)
                if (isRoot) sendDown(text, except = linkId) else sendUp(text)
            }
            else -> if (isRoot) atRoot(parts) else sendUp(text)
        }
    }

    private fun fromParent(text: String, parts: List<String>) {
        when (parts[0]) {
            Proto.ROSTER -> {
                inGame = parts.getOrNull(1) == "1"
                val a = parts.getOrNull(2).orEmpty(); val b = parts.getOrNull(3).orEmpty()
                partners = if (a.isNotEmpty() && b.isNotEmpty()) a to b else null
                roster = parseMembers(parts.getOrNull(4).orEmpty())
                listener.onRoster()
            }
            Proto.PHOTO -> storePhoto(parts)
            Proto.INVITE -> parts.getOrNull(1)?.let { if (it != myId) listener.onInvite(it) }
            Proto.INVRES -> listener.onInviteDeclined(parts.getOrNull(2).orEmpty())
            Proto.STATE, Proto.ENDGAME -> listener.onDownMessage(parts)
        }
        sendDown(text)
    }

    // ================= Raiz: comanda a sala =================

    private fun atRoot(parts: List<String>) {
        when (parts[0]) {
            Proto.PARTNER -> {
                val a = parts.getOrNull(1).orEmpty(); val b = parts.getOrNull(2).orEmpty()
                val ids = roster.map { it.id }
                if (roster.size == 4 && a in ids && b in ids && a != b && !inGame) {
                    partners = a to b
                    broadcastRoster()
                }
            }
            Proto.INVITE -> startInvite(parts.getOrNull(1).orEmpty())
            Proto.ANSWER -> answer(parts.getOrNull(1).orEmpty(), parts.getOrNull(2) == "1")
            else -> listener.onRootMessage(parts)
        }
    }

    private fun startInvite(from: String) {
        if (inGame || !canStart) return
        pendingInviteFrom = from
        inviteAccepted.clear(); inviteAccepted += from
        sendDown("${Proto.INVITE}|$from")
        if (from != myId) listener.onInvite(from)
    }

    private fun answer(id: String, ok: Boolean) {
        if (pendingInviteFrom == null) return
        if (!ok) {
            pendingInviteFrom = null
            val name = nameOf(id)
            sendDown("${Proto.INVRES}|0|$name")
            listener.onInviteDeclined(name)
            return
        }
        inviteAccepted += id
        if (inviteAccepted.containsAll(roster.map { it.id })) {
            pendingInviteFrom = null
            inGame = true
            broadcastRoster()
            listener.onStartGame(seatOrder())
        }
    }

    /** Ordem das vezes: com 4, alternando as duplas (A, B, parceiro de A, parceiro de B). */
    fun seatOrder(): List<String> {
        val ids = roster.map { it.id }
        val p = partners
        if (ids.size == 4 && p != null) {
            val others = ids.filter { it != p.first && it != p.second }
            return listOf(p.first, others[0], p.second, others[1])
        }
        return ids
    }

    /** Dá para começar? 2 ou 3 jogadores, ou 4 com as duplas escolhidas. */
    val canStart: Boolean get() = roster.size in 2..3 || (roster.size == 4 && partners != null)

    private fun membersChanged() {
        if (isRoot) recomputeRoster() else sendUp(membersLine())
    }

    private fun recomputeRoster() {
        val now = subtree()
        val old = roster
        // Mantém a ordem de entrada; quem chegou vai para o fim.
        val merged = old.filter { o -> now.any { it.id == o.id } }.map { o -> now.first { it.id == o.id } } +
            now.filter { n -> old.none { it.id == n.id } }
        val left = old.filter { o -> merged.none { it.id == o.id } }
        roster = merged
        val p = partners
        if (p != null && (roster.size != 4 || roster.none { it.id == p.first } || roster.none { it.id == p.second })) partners = null
        if (left.isNotEmpty() && inGame) {
            inGame = false
            val reason = "${left.joinToString(" e ") { it.name }} saiu da sala. A partida terminou."
            sendDown("${Proto.ENDGAME}|$reason")
            listener.onDownMessage(listOf(Proto.ENDGAME, reason))
        }
        if (left.isNotEmpty() && pendingInviteFrom != null) pendingInviteFrom = null
        broadcastRoster()
    }

    private fun broadcastRoster() {
        val p = partners
        sendDown("${Proto.ROSTER}|${if (inGame) 1 else 0}|${p?.first.orEmpty()}|${p?.second.orEmpty()}|${encodeMembers(roster)}")
        listener.onRoster()
    }

    // ================= Ações deste aparelho =================

    fun invite() {
        if (isRoot) startInvite(myId) else sendUp("${Proto.INVITE}|$myId")
    }

    fun answerInvite(ok: Boolean) {
        if (isRoot) answer(myId, ok) else sendUp("${Proto.ANSWER}|$myId|${if (ok) 1 else 0}")
    }

    fun choosePartner(otherId: String) {
        val line = "${Proto.PARTNER}|$myId|$otherId"
        if (isRoot) atRoot(line.split('|')) else sendUp(line)
    }

    /** Jogada deste aparelho (não raiz) para a raiz. */
    fun sendToRoot(line: String) {
        if (isRoot) atRoot(line.split('|')) else sendUp(line)
    }

    /** Raiz: manda para todos os aparelhos (ex.: STATE). */
    fun broadcast(line: String) { if (isRoot) sendDown(line) }

    /** Raiz: fim do jogo (volta todos para a sala). */
    fun endGame(reason: String) {
        if (!isRoot) return
        inGame = false
        sendDown("${Proto.ENDGAME}|$reason")
        broadcastRoster()
    }

    fun setMyPhoto(base64: String?) {
        if (base64 == null) photos.remove(myId) else photos[myId] = base64
        if (base64 != null && hasConnections) {
            val line = "${Proto.PHOTO}|$myId|$base64"
            if (isRoot) sendDown(line) else sendUp(line)
        }
    }

    /** Esquece tudo (ao sair da sala). As conexões são fechadas por quem chama. */
    fun reset() {
        roles.clear(); accepted.clear(); subtrees.clear()
        val mine = photos[myId]
        photos.clear(); if (mine != null) photos[myId] = mine
        roster = listOf(Member(myId, myName)); partners = null; inGame = false
        pendingInviteFrom = null; inviteAccepted.clear()
        listener.onRoster()
    }

    // ================= Auxiliares =================

    private fun subtree(): List<Member> = listOf(Member(myId, myName)) + subtrees.values.flatten()
    private fun membersLine() = "${Proto.MEMBERS}|${encodeMembers(subtree())}"
    private fun sendUp(line: String) { parentLink?.let { net.send(it, line) } }
    private fun sendDown(line: String, except: Int? = null) { childLinks.filter { it != except }.forEach { net.send(it, line) } }

    private fun storePhoto(parts: List<String>) {
        val id = parts.getOrNull(1) ?: return
        val b64 = parts.getOrNull(2) ?: return
        photos[id] = b64
        listener.onPhoto(id, b64)
    }

    companion object {
        const val MAX_PLAYERS = 4
        private fun clean(s: String) = s.replace(Regex("[~,|]"), " ").trim().ifEmpty { "Amigo" }
        fun encodeMembers(m: List<Member>) = m.joinToString(",") { "${it.id}~${clean(it.name)}" }
        fun parseMembers(s: String): List<Member> = if (s.isEmpty()) emptyList() else s.split(',').mapNotNull {
            val i = it.indexOf('~'); if (i <= 0) null else Member(it.substring(0, i), it.substring(i + 1))
        }
    }
}
