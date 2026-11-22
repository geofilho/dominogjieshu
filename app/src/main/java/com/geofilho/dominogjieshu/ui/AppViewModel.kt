package com.geofilho.dominogjieshu.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geofilho.dominogjieshu.audio.SoundFx
import com.geofilho.dominogjieshu.bt.BtLink
import com.geofilho.dominogjieshu.bt.BtSession
import com.geofilho.dominogjieshu.bt.FoundDevice
import com.geofilho.dominogjieshu.bt.LinkEvent
import com.geofilho.dominogjieshu.bt.Member
import com.geofilho.dominogjieshu.bt.Proto
import com.geofilho.dominogjieshu.bt.Transport
import com.geofilho.dominogjieshu.diag.DiagLog
import com.geofilho.dominogjieshu.game.Ai
import com.geofilho.dominogjieshu.game.EndReason
import com.geofilho.dominogjieshu.game.Level
import com.geofilho.dominogjieshu.game.Round
import com.geofilho.dominogjieshu.game.Side
import com.geofilho.dominogjieshu.game.Tile
import com.geofilho.dominogjieshu.game.Words
import com.geofilho.dominogjieshu.profile.Avatar
import com.geofilho.dominogjieshu.speech.Voice
import com.geofilho.dominogjieshu.update.UpdateState
import com.geofilho.dominogjieshu.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { MENU, BLUETOOTH, GAME, HELP, DIAGNOSTIC }

/** Rodadas vencidas para ganhar a partida (melhor de 5). */
const val WINS_TO_MATCH = 3

enum class Mode { CPU, LOCAL, BT }

class AppViewModel(app: Application) : AndroidViewModel(app), BtSession.Listener {
    private val prefs = app.getSharedPreferences("dominog", Context.MODE_PRIVATE)
    val voice = Voice(app)
    val history = History(prefs)
    val updater = Updater(app)
    val sfx = SoundFx()
    val link = BtLink(app)

    /** Chave "Silenciar sons do jogo" (Diagnóstico): cala os efeitos, mantém as falas. */
    var soundsMuted by mutableStateOf(prefs.getBoolean("sons_mudos", false)); private set

    fun changeSoundsMuted(m: Boolean) {
        soundsMuted = m
        sfx.muted = m
        prefs.edit().putBoolean("sons_mudos", m).apply()
        say(if (m) "Sons do jogo silenciados" else "Sons do jogo ativados", flush = true)
    }

    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle); private set

    var screen by mutableStateOf(Screen.MENU); private set
    var mode by mutableStateOf(Mode.CPU); private set

    // ---------- Estado do jogo ----------
    var round: Round? = null; private set
    /** Incrementa a cada mudança na rodada para a interface se redesenhar. */
    var version by mutableIntStateOf(0); private set
    /** Placar por lado: jogador (2 ou 3 jogadores) ou dupla (4 jogadores). */
    val scores = IntArray(4)
    /** Modo mesmo aparelho: as pedras do jogador da vez estão à mostra? */
    var revealed by mutableStateOf(false); private set
    /** Animação das pedras rolando na mesa. */
    var shuffling by mutableStateOf(false); private set
    private var cpuJob: Job? = null

    // ---------- Bluetooth ----------
    var playerName by mutableStateOf(prefs.getString("name", "") ?: ""); private set
    var myPhoto by mutableStateOf(Avatar.load(app)); private set

    /** Identificação deste aparelho na sala (fica salva). */
    private val myId: String = prefs.getString("bt_id", null)
        ?: java.util.UUID.randomUUID().toString().take(8).also { prefs.edit().putString("bt_id", it).apply() }

    val session = BtSession(
        myId,
        playerName.ifBlank { "Amigo" },
        object : Transport {
            override fun send(linkId: Int, line: String) = link.send(linkId, line)
            override fun close(linkId: Int) = link.close(linkId)
        },
        this,
    )

    /** Muda quando a sala muda (jogadores, duplas, fotos) para a tela se redesenhar. */
    var roomVersion by mutableIntStateOf(0); private set
    /** Fotos dos jogadores da sala, por id. */
    val photos = HashMap<String, Bitmap>()
    /** Ids dos jogadores em cada lugar da mesa (ordem das vezes) no jogo pelo Bluetooth. */
    private var seatIds: List<String> = emptyList()
    private var lastRosterIds: Set<String> = setOf(myId)

    fun isMe(m: Member) = m.id == myId

    /** Nome de quem convidou (mostra o diálogo Aceitar/Recusar). */
    var inviteFrom by mutableStateOf<String?>(null); private set
    var waitingAccept by mutableStateOf(false); private set
    var lobbyMessage by mutableStateOf<String?>(null)

    init {
        sfx.muted = soundsMuted
        myPhoto?.let { photos[myId] = it; session.setMyPhoto(Avatar.encode(it)) }
        viewModelScope.launch {
            link.events.collect { e ->
                when (e) {
                    is LinkEvent.Opened -> { link.stopScan(); session.onOpened(e.id, e.accepted) }
                    is LinkEvent.Line -> session.onLine(e.id, e.text)
                    is LinkEvent.Closed -> session.onClosed(e.id)
                }
            }
        }
        viewModelScope.launch {
            link.errors.collect { msg -> lobbyMessage = msg; say(msg, flush = true) }
        }
    }

    fun setPhoto(b: Bitmap?) {
        val app = getApplication<Application>()
        if (b == null) {
            Avatar.delete(app); myPhoto = null; photos.remove(myId); session.setMyPhoto(null)
            roomVersion++
            say("Foto removida", flush = true)
            return
        }
        val saved = Avatar.save(app, b)
        myPhoto = saved
        photos[myId] = saved
        session.setMyPhoto(Avatar.encode(saved))
        roomVersion++
        say("Foto salva", flush = true)
    }

    fun photoFromGallery(uri: android.net.Uri) {
        viewModelScope.launch {
            val b = withContext(Dispatchers.IO) { Avatar.decode(getApplication(), uri) }
            if (b != null) setPhoto(b) else say("Não foi possível abrir a foto", flush = true)
        }
    }

    // =================== Jogadores e lados ===================

    /** Índice do jogador cujas pedras este aparelho mostra. */
    val me: Int
        get() = when (mode) {
            Mode.CPU -> 0
            Mode.BT -> seatIds.indexOf(myId).coerceAtLeast(0)
            Mode.LOCAL -> round?.current ?: 0
        }

    /** Número de jogadores da rodada atual. */
    val players: Int get() = round?.n ?: 2

    fun sideOf(p: Int): Int = round?.sideOf(p) ?: p
    val sideCount: Int get() = round?.sides ?: 2

    fun nameOf(p: Int): String = when (mode) {
        Mode.CPU -> if (p == 0) "Você" else "Computador"
        Mode.LOCAL -> if (p == 0) "Jogador 1" else "Jogador 2"
        Mode.BT -> seatIds.getOrNull(p)?.let { if (it == myId) playerName.ifBlank { "Você" } else session.nameOf(it) } ?: "Amigo"
    }

    private fun spokenName(p: Int) = when (mode) {
        Mode.LOCAL -> if (p == 0) "Jogador um" else "Jogador dois"
        else -> nameOf(p)
    }

    /** Nome do lado: o jogador, ou os dois nomes da dupla. */
    fun sideName(side: Int): String =
        if (players == 4) (0 until 4).filter { sideOf(it) == side }.joinToString(" e ") { nameOf(it) }
        else nameOf(side)

    private fun spokenSide(side: Int): String =
        if (players == 4) "dupla " + (0 until 4).filter { sideOf(it) == side }.joinToString(" e ") { spokenName(it) }
        else spokenName(side)

    /** Placar para a tela: "Ana 1 × 0 Bia" (2 lados) ou separado por pontos (3 jogadores). */
    fun placarDisplay(): String =
        if (sideCount == 2) "${sideName(0)} ${scores[0]}  ×  ${scores[1]} ${sideName(1)}"
        else (0 until sideCount).joinToString("  ·  ") { "${sideName(it)} ${scores[it]}" }

    /** Placar falado: "Você 2, Computador 1". */
    fun placarText(): String = (0 until sideCount).joinToString(", ") { "${spokenSide(it)} ${scores[it]}" }

    /** Os outros jogadores, na ordem em que jogam depois deste aparelho. */
    fun others(): List<Int> {
        val r = round ?: return emptyList()
        return (1 until r.n).map { (me + it) % r.n }
    }

    /** Com 4 jogadores: este jogador é meu parceiro? */
    fun isPartner(p: Int): Boolean = players == 4 && p != me && sideOf(p) == sideOf(me)

    /** Este aparelho comanda a rodada (computador, parceiro, ou quem comanda a sala do Bluetooth)? */
    private val isAuthority: Boolean get() = mode != Mode.BT || session.isRoot

    // =================== Navegação ===================
    /** Nível do computador escolhido no menu. */
    var level by mutableStateOf(Level.MEDIO); private set

    fun startCpu(l: Level) { level = l; mode = Mode.CPU; newMatch() }
    fun startLocal() { mode = Mode.LOCAL; newMatch() }
    fun openBluetooth() { screen = Screen.BLUETOOTH; lobbyMessage = null }
    fun openHelp() { screen = Screen.HELP }
    fun openDiagnostic() { screen = Screen.DIAGNOSTIC }

    fun backToMenu() {
        cpuJob?.cancel()
        voice.stop()
        if (mode == Mode.BT || screen == Screen.BLUETOOTH) leaveRoom()
        round = null
        screen = Screen.MENU
    }

    fun saveName(name: String) {
        playerName = name.replace('|', ' ').trim().take(20)
            .split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        prefs.edit().putString("name", playerName).apply()
        session.myName = playerName
    }

    // =================== Partida ===================
    private fun newMatch() {
        scores.fill(0)
        round = null
        // Parceiro no mesmo aparelho: o Jogador 1 sempre começa (com as pedras ocultas até
        // tocar em Mostrar pedras). Nos outros modos, quem começa é sorteado.
        if (mode == Mode.LOCAL) dealRound(0) else dealRound(null)
        screen = Screen.GAME
        waitingAccept = false
    }

    private fun dealRound(starter: Int?) {
        cpuJob?.cancel()
        val n = if (mode == Mode.BT) seatIds.size.coerceIn(2, 4) else 2
        val r = Round.deal(starter, n = n)
        round = r
        revealed = false
        shuffling = true
        sfx.playShuffle()
        say("Embaralhando as pedras", flush = true)
        bump()
        broadcast()
        viewModelScope.launch {
            delay(1700)
            shuffling = false
            announceTurn(r, newRound = true)
            maybeCpu()
        }
    }

    fun nextRound() {
        val r = round ?: return
        if (!r.isOver) return
        when {
            !isAuthority -> session.sendToRoot(Proto.NEXT)
            matchOver -> newMatch() // fim da melhor de 5: começa outra partida
            else -> {
                val res = r.result!!
                // Começa quem bateu (ou venceu o trancado); em empate, o próximo da vez.
                val starter = if (res.winner >= 0) res.winner else r.next(r.current)
                dealRound(starter)
            }
        }
    }

    // ---------- Ações do jogador neste aparelho ----------
    fun reveal() {
        revealed = true
        val r = round
        voice.stop()
        sfx.playOpen()
        bump()
        val hint = if (r != null) hintFor(r, r.current) else ""
        status = "Mostrando pedras.$hint"
        // A voz entra logo depois do som de abrir.
        viewModelScope.launch {
            delay(550)
            if (partnerVoiceOn) voice.speak("Mostrando pedras.", flush = true)
            if (hint.isNotEmpty()) focusActionAfterSpeech()
        }
    }

    /**
     * Avisos do modo parceiro ("Mostrando pedras", "Passe para o jogador"): falam mesmo sem leitor de tela,
     * a não ser que os sons do jogo estejam silenciados (aí só falam para quem usa leitor de tela).
     */
    private val partnerVoiceOn get() = screenReaderOn || !soundsMuted

    /** Informado pela tela: leitor de tela (TalkBack) ligado. */
    var screenReaderOn = false

    /** Incrementa quando o foco do TalkBack deve ir para o aviso "Passe para o jogador…". */
    var curtainFocus by mutableIntStateOf(0); private set

    fun sidesFor(t: Tile): List<Side> = round?.sidesFor(t) ?: emptyList()

    fun play(t: Tile, side: Side) {
        if (!canAct()) return
        if (!isAuthority) { session.sendToRoot("${Proto.PLAY}|$me|${t.id}|${side.ordinal}"); return }
        act(me) { it.play(me, t, side) }
    }

    fun draw() {
        if (!canAct()) return
        if (!isAuthority) { session.sendToRoot("${Proto.DRAW}|$me"); return }
        act(me) { it.draw(me) != null }
    }

    fun pass() {
        if (!canAct()) return
        if (!isAuthority) { session.sendToRoot("${Proto.PASS}|$me"); return }
        act(me) { it.pass(me) }
    }

    fun canAct(): Boolean {
        val r = round ?: return false
        if (r.isOver || shuffling || r.current != me) return false
        if (mode == Mode.LOCAL && !revealed) return false
        return true
    }

    /** Aplica uma ação na rodada (aparelho com autoridade) e anuncia o resultado. */
    private inline fun act(player: Int, action: (Round) -> Boolean) {
        val r = round ?: return
        if (r.current != player) return
        val before = Snap(r)
        if (!action(r)) return
        afterChange(before, r)
        broadcast()
        maybeCpu()
    }

    private fun maybeCpu() {
        val r = round ?: return
        if (mode != Mode.CPU || r.isOver || r.current != 1 || shuffling) return
        if (cpuJob?.isActive == true) return
        cpuJob = viewModelScope.launch {
            delay(1300)
            while (!r.isOver && r.current == 1) {
                val m = Ai.choose(r, 1, level)
                when {
                    m != null -> { act(1) { it.play(1, m.tile, m.side) }; break }
                    r.canDraw(1) -> { act(1) { it.draw(1) != null }; delay(650) }
                    else -> { act(1) { it.pass(1) }; break }
                }
            }
        }
    }

    // =================== Anúncios (som + voz) ===================
    private class Snap(r: Round) {
        val board = r.board.size
        val hands = IntArray(r.n) { r.hands[it].size }
        val passes = r.consecutivePasses
        val current = r.current
        val over = r.isOver
        fun hand(p: Int) = hands.getOrElse(p) { 0 }
    }

    private fun afterChange(before: Snap, r: Round) {
        val turnChanged = before.current != r.current
        DiagLog.add(
            "JOGO",
            "vez de ${nameOf(r.current)} (eu=${nameOf(me)}); mesa ${r.board.size}; minha mão ${r.hands.getOrNull(me)?.size}; " +
                "monte ${r.boneyard.size}; encaixa=${r.playable(me).size}${if (r.isOver) "; rodada terminou" else ""}",
        )
        if (mode == Mode.LOCAL && (turnChanged || r.isOver)) revealed = false
        bump()

        val sb = StringBuilder()
        if (r.board.size > before.board) {
            sfx.playTile()
            val m = r.lastMove!!
            val who = if (m.player == me && mode != Mode.LOCAL) "Você jogou" else "${spokenName(m.player)} jogou"
            sb.append("$who ${Words.tile(m.tile)}. ")
            // Você jogou: a pedra saiu da mão e o TalkBack perderia o lugar (ia para a mesa).
            // Leva o foco para o título "Suas pedras", logo antes das suas pedras.
            if (m.player == me && mode != Mode.LOCAL && !r.isOver) {
                DiagLog.add("FOCO", "você jogou: levar foco a Suas pedras")
                startFocus++
            }
            if (!r.isOver) sb.append("Ponta esquerda ${Words.n(r.leftEnd!!)}, ponta direita ${Words.n(r.rightEnd!!)}. ")
        }
        for (p in 0 until r.n) if (r.hands[p].size > before.hand(p)) {
            sfx.playDraw()
            if (p == me && mode != Mode.LOCAL) sb.append("Você comprou ${Words.tile(r.hands[p].last())}. ")
            else sb.append("${spokenName(p)} comprou. ")
        }
        if (r.consecutivePasses > before.passes) {
            sb.append(if (before.current == me && mode != Mode.LOCAL) "Você passou. " else "${spokenName(before.current)} passou. ")
        }
        // Comprou e continua sem pedra que encaixe: avisa de novo e leva o foco ao botão.
        val myTurnNow = !r.isOver && r.current == me && (mode != Mode.LOCAL || revealed)
        val hint = if (!turnChanged && myTurnNow) hintFor(r, me) else ""
        // Comprou e a pedra comprada encaixa: depois da fala, o foco vai para ela.
        val bought = if (mode != Mode.LOCAL && !turnChanged && myTurnNow && r.hands[me].size > before.hand(me)) r.hands[me].last() else null
        val boughtFits = bought != null && hint.isEmpty() && r.sidesFor(bought).isNotEmpty()
        say(sb.toString(), flush = true)
        // (Depois do say: a fala com "flush" limparia um pedido feito antes dela.)
        if (boughtFits) {
            DiagLog.add("FOCO", "comprou ${Words.tile(bought!!)}, que encaixa: levar foco a ela depois da fala")
            voice.afterQueue { tileFocusId = bought.id; tileFocusToken++ }
        }
        if (hint.isNotEmpty()) { status = (status + hint).trim(); focusActionAfterSpeech() }

        if (r.isOver && !before.over) announceResult(r)
        else if (turnChanged) announceTurn(r, newRound = false)
    }

    private fun announceTurn(r: Round, newRound: Boolean) {
        val p = r.current
        if (mode == Mode.LOCAL) {
            val pass = if (newRound) "${spokenName(p)} começa" else "Passe para o ${spokenName(p).lowercase()}"
            status = pass
            // Depois das informações da jogada: bipes (1 = Jogador 1, 2 = Jogador 2) e o aviso.
            // Com TalkBack, o próprio leitor fala o aviso quando o foco chega nele;
            // sem TalkBack, fala a voz do app.
            voice.afterQueue {
                sfx.playBeeps(p + 1)
                viewModelScope.launch {
                    delay(if (p == 0) 250 else 480)
                    DiagLog.add("FOCO", "aviso '$pass' (leitor=${screenReaderOn})")
                    if (screenReaderOn) curtainFocus++ else if (partnerVoiceOn) voice.speak(pass)
                }
            }
            return
        }
        val text = when {
            p == me -> "Sua vez"
            mode == Mode.CPU -> if (newRound) "Computador começa" else ""
            newRound -> "${nameOf(p)} começa"
            r.n > 2 -> "Vez de ${nameOf(p)}"
            else -> ""
        }
        val mine = p == me
        val hint = if (mine) hintFor(r, p) else ""
        say(text + if (mine) "." else "")
        // Início da partida: o foco desce para "Suas pedras" quando for a sua vez, depois da fala.
        // Se outro começa, espera a jogada dele ser falada (senão o TalkBack cortaria a frase).
        if (newRound) startFocusPending = true
        if (hint.isNotEmpty()) { startFocusPending = false; status = (status + hint).trim(); focusActionAfterSpeech() }
        else if (mine && startFocusPending) {
            startFocusPending = false
            voice.afterQueue { DiagLog.add("FOCO", "início da rodada: levar foco a Suas pedras"); startFocus++ }
        }
    }

    /** O foco de início de rodada ainda não foi levado a "Suas pedras" (espera a sua vez). */
    private var startFocusPending = false

    /** Partida = melhor de 5: vence o lado (jogador ou dupla) que ganhar [WINS_TO_MATCH] rodadas primeiro. */
    val matchOver: Boolean get() = (0 until sideCount).any { scores[it] >= WINS_TO_MATCH }
    val matchWinner: Int get() = (0 until sideCount).firstOrNull { scores[it] >= WINS_TO_MATCH } ?: -1

    private fun announceResult(r: Round) {
        val res = r.result!!
        // Nos outros aparelhos do Bluetooth o placar já chega pronto de quem comanda.
        if (res.winner >= 0 && isAuthority) scores[r.sideOf(res.winner)]++
        if (matchOver) recordMatch()
        if (res.winner >= 0) sfx.playApplause()
        val youWon = res.winner >= 0 && mode != Mode.LOCAL && res.winner == me
        val how = if (res.reason == EndReason.BATIDA) "bateu" else "venceu no jogo trancado"
        val sb = StringBuilder()
        sb.append(
            when {
                res.winner < 0 -> "Jogo trancado. Empate, a rodada não conta. "
                youWon -> "Você $how! "
                else -> "${spokenName(res.winner)} $how. "
            },
        )
        if (res.winner >= 0 && r.n == 4) sb.append("Ponto para a ${spokenSide(r.sideOf(res.winner))}. ")
        if (matchOver) {
            val w = matchWinner
            val mine = mode != Mode.LOCAL && w == r.sideOf(me)
            sb.append(
                when {
                    mine && r.n == 4 -> "Sua dupla venceu a partida! "
                    mine -> "Você venceu a partida! "
                    else -> "${spokenSide(w).replaceFirstChar { it.uppercaseChar() }} venceu a partida. "
                },
            )
        }
        // Sempre termina com o placar.
        sb.append("${if (matchOver) "Placar final" else "Placar"}: ${placarText()}.")
        say(sb.toString())
        bump()
        // Depois da fala do resultado, o foco vai para a frase do resultado (ex.: "Você bateu!").
        voice.afterQueue { DiagLog.add("FOCO", "fim da rodada: levar foco ao resultado"); resultFocus++ }
    }

    /** Nomes dos outros jogadores (para o histórico do Bluetooth). */
    private fun btOpponentsLabel(): String =
        (0 until players).filter { it != me }.joinToString(" e ") { nameOf(it) }.ifBlank { "Amigo" }

    private fun recordMatch() {
        val w = matchWinner
        when (mode) {
            Mode.CPU -> history.recordCpu(level, won = w == 0)
            Mode.LOCAL -> history.recordLocal(w)
            Mode.BT -> history.recordBt(btOpponentsLabel(), won = w == sideOf(me))
        }
    }

    /** Texto do histórico do modo atual (tocar no placar). */
    fun historyText(): String = when (mode) {
        Mode.CPU -> history.cpuText()
        Mode.LOCAL -> history.localText()
        Mode.BT -> history.btText(if (seatIds.isNotEmpty()) btOpponentsLabel() else null)
    }

    fun speakHistory() = say(historyText(), flush = true)

    /** Botão "Ouvir mesa": pontas, pedras dos outros, mesa e (depois da primeira compra) o monte. */
    fun describe() {
        val r = round ?: return
        val sb = StringBuilder()
        if (r.board.isNotEmpty()) sb.append("Ponta esquerda ${Words.n(r.leftEnd!!)}, ponta direita ${Words.n(r.rightEnd!!)}. ")
        for (p in others()) sb.append("${spokenName(p)} tem ${pedras(r.hands[p].size)}. ")
        sb.append(if (r.board.isEmpty()) "Mesa vazia. " else "Mesa com ${pedras(r.board.size)}. ")
        // O monte só é informado depois que alguém comprar a primeira pedra.
        if (r.boneyard.size < 28 - 7 * r.n) sb.append("Monte tem ${pedras(r.boneyard.size)}.")
        say(sb.toString(), flush = true)
    }

    fun boardSummary(r: Round): String =
        if (r.board.isEmpty()) "Mesa vazia"
        else "Mesa com ${pedras(r.board.size)}, ponta esquerda ${Words.n(r.leftEnd!!)}, ponta direita ${Words.n(r.rightEnd!!)}"

    fun pedras(n: Int) = if (n == 1) "1 pedra" else "$n pedras"

    private fun bump() { version++ }

    // =================== Atualização ===================
    fun checkUpdate() {
        updateState = UpdateState.Checking
        say("Verificando atualização", flush = true)
        viewModelScope.launch {
            val latest = updater.latestVersion()
            updateState = when {
                latest == null -> UpdateState.Failed("Não foi possível verificar. Confira a internet e tente de novo.")
                latest > updater.installedVersion -> UpdateState.Available(latest, updater.latestNotes())
                else -> UpdateState.UpToDate
            }
            say(
                when (val s = updateState) {
                    is UpdateState.Available -> "Nova versão 1.0.${s.version} disponível."
                    UpdateState.UpToDate -> "Você já tem a versão mais recente."
                    is UpdateState.Failed -> s.message
                    else -> ""
                },
                flush = true,
            )
        }
    }

    fun downloadUpdate() {
        updateState = UpdateState.Downloading(0)
        say("Baixando atualização", flush = true)
        viewModelScope.launch {
            try {
                val file = updater.download { updateState = UpdateState.Downloading(it) }
                installUpdate(file)
            } catch (e: Exception) {
                updateState = UpdateState.Failed("Falha ao baixar. Confira a internet e tente de novo.")
                say((updateState as UpdateState.Failed).message, flush = true)
            }
        }
    }

    fun installUpdate(file: java.io.File) {
        if (!updater.canInstall()) {
            updateState = UpdateState.NeedPermission(file)
            say("Para instalar, permita que o Dominó Jieshuo G instale aplicativos. Ative a opção e volte, que a instalação continua sozinha.", flush = true)
            return
        }
        updateState = UpdateState.Idle
        say("Abrindo o instalador", flush = true)
        updater.install(file)
    }

    fun openInstallPermission() = updater.openInstallPermissionSettings()

    /** Ao voltar das configurações: se a permissão foi dada, a instalação continua sozinha. */
    fun resumeInstallIfAllowed() {
        val s = updateState
        if (s is UpdateState.NeedPermission && updater.canInstall()) installUpdate(s.file)
    }

    fun dismissUpdate() { updateState = UpdateState.Idle }

    /** Última mensagem anunciada (mostrada na linha de status, como no modelo). */
    var status by mutableStateOf(""); private set

    /**
     * Avisos falados: só com leitor de tela ligado. Sem leitor, aparecem só na linha de status.
     * (No modo parceiro, "Mostrando pedras" e "Passe para o jogador" falam sempre; eles usam voice.speak direto.)
     */
    private fun say(text: String, flush: Boolean = false) {
        if (text.isBlank()) return
        DiagLog.add("VOZ", (if (screenReaderOn) "" else "(calada, sem leitor) ") + text.trim())
        status = text.trim()
        if (screenReaderOn) voice.speak(text, flush)
    }

    /** Toque numa pedra da mão: joga se só houver um lado, avisa se não encaixa. */
    fun tapTile(t: Tile): List<Side> {
        if (!canAct()) {
            if (round?.isOver == false && round?.current != me) say("Aguarde, não é sua vez.", flush = true)
            return emptyList()
        }
        val sides = sidesFor(t)
        when (sides.size) {
            0 -> say("Essa pedra não encaixa!", flush = true)
            1 -> play(t, sides[0])
        }
        return sides
    }

    /**
     * true quando o foco do TalkBack deve ir para o botão Comprar pedra / Passar vez
     * (depois que a voz termina o aviso): número que aumenta a cada pedido.
     */
    var buyFocusToken by mutableIntStateOf(0); private set

    private fun focusActionAfterSpeech() {
        DiagLog.add("FOCO", "pedido de foco no Comprar/Passar (espera a voz terminar)")
        voice.afterQueue { DiagLog.add("FOCO", "voz terminou: levar foco ao Comprar/Passar"); buyFocusToken++ }
    }

    /** Muda quando o foco do TalkBack deve ir para "Suas pedras" (início da rodada e depois que você joga). */
    var startFocus by mutableIntStateOf(0); private set
    /** Muda quando o foco do TalkBack deve ir para a pedra [tileFocusId] da sua mão (a que você comprou). */
    var tileFocusToken by mutableIntStateOf(0); private set
    var tileFocusId = -1; private set
    /** Muda depois da fala do resultado da rodada: o foco do TalkBack vai para o resultado. */
    var resultFocus by mutableIntStateOf(0); private set

    private fun hintFor(r: Round, p: Int): String = when {
        r.isOver || r.board.isEmpty() || r.playable(p).isNotEmpty() -> ""
        r.boneyard.isNotEmpty() -> " Você não tem pedras que encaixam. Toque no botão comprar."
        r.n == 4 -> " Você não tem pedras que encaixam. Toque no botão passar vez."
        else -> " Monte vazio. Toque no botão passar vez."
    }

    // =================== Bluetooth ===================
    /** Precisa pedir ao Android para deixar o celular visível? (não pede de novo enquanto ainda vale) */
    fun needsDiscoverable(): Boolean =
        !link.isDiscoverable && System.currentTimeMillis() > prefs.getLong("visivel_ate", 0L)

    /** Resultado do pedido de visibilidade: [seconds] > 0 quando a pessoa aceitou. */
    fun discoverableResult(seconds: Int) {
        if (seconds > 0) prefs.edit().putLong("visivel_ate", System.currentTimeMillis() + seconds * 1000L - 60_000L).apply()
    }

    /** A tela do Bluetooth já é uma sala: fica aberta, aguardando amigos. */
    fun hostRoom() { mode = Mode.BT; link.openRoom() }

    /** Sai da sala: fecha as conexões e esquece os jogadores. */
    fun leaveRoom() {
        link.closeAll()
        session.reset()
        seatIds = emptyList()
        photos.keys.retainAll(setOf(myId))
        inviteFrom = null; waitingAccept = false
        lastRosterIds = setOf(myId)
        roomVersion++
    }

    /** Conecta no aparelho de um amigo (ele entra na sala, ou você entra na dele). */
    fun connectTo(d: FoundDevice) {
        mode = Mode.BT
        lobbyMessage = null
        link.join(d)
    }

    fun invite() {
        if (!session.canStart) return
        waitingAccept = true
        session.invite()
        say("Convite enviado. Aguardando ${if (session.roster.size > 2) "os outros aceitarem" else "o amigo aceitar"}.", flush = true)
    }

    fun answerInvite(accept: Boolean) {
        inviteFrom = null
        if (accept) waitingAccept = true
        session.answerInvite(accept)
    }

    fun choosePartner(m: Member) {
        session.choosePartner(m.id)
        say("Você escolheu ${m.name} como parceiro.", flush = true)
    }

    /** Quem comanda a sala: manda o estado do jogo para todos. */
    private fun broadcast() {
        if (mode != Mode.BT || !session.isRoot) return
        val r = round ?: return
        val sc = (0 until 4).joinToString(",") { scores[it].toString() }
        session.broadcast("${Proto.STATE}|${if (shuffling) 1 else 0}|$sc|${seatIds.joinToString(",")}|${r.encode()}")
    }

    // ---------- Eventos da sala (BtSession.Listener) ----------
    override fun onRoster() {
        val ids = session.roster.map { it.id }.toSet()
        val joined = session.roster.filter { it.id !in lastRosterIds && it.id != myId }
        val left = lastRosterIds - ids
        lastRosterIds = ids
        photos.keys.retainAll(ids)
        if (joined.isNotEmpty()) say("${joined.joinToString(" e ") { it.name }} entrou na sala.")
        if (left.isNotEmpty()) waitingAccept = false
        roomVersion++
    }

    override fun onPhoto(id: String, base64: String) {
        if (id == myId) return
        Avatar.decodeBase64(base64)?.let { photos[id] = it; roomVersion++ }
    }

    override fun onInvite(fromId: String) {
        val name = session.nameOf(fromId)
        inviteFrom = name
        say("$name quer começar o jogo. Aceitar?", flush = true)
    }

    override fun onInviteDeclined(byName: String) {
        waitingAccept = false
        lobbyMessage = "$byName recusou o convite"
        say("$byName recusou o convite", flush = true)
    }

    override fun onStartGame(seatIds: List<String>) {
        this.seatIds = seatIds
        mode = Mode.BT
        newMatch()
    }

    override fun onRootMessage(parts: List<String>) {
        val seat = parts.getOrNull(1)?.toIntOrNull()
        when (parts[0]) {
            Proto.PLAY -> if (seat != null) {
                val t = Tile.byId(parts[2].toInt())
                val sd = Side.entries[parts[3].toInt()]
                act(seat) { it.play(seat, t, sd) }
            }
            Proto.DRAW -> if (seat != null) act(seat) { it.draw(seat) != null }
            Proto.PASS -> if (seat != null) act(seat) { it.pass(seat) }
            Proto.NEXT -> nextRound()
        }
    }

    override fun onDownMessage(parts: List<String>) {
        when (parts[0]) {
            Proto.STATE -> onState(parts)
            Proto.ENDGAME -> {
                val reason = parts.getOrNull(1).orEmpty().ifBlank { "A partida terminou." }
                round = null
                seatIds = emptyList()
                waitingAccept = false
                lobbyMessage = reason
                say(reason, flush = true)
                if (screen == Screen.GAME) screen = Screen.BLUETOOTH
                bump()
            }
        }
    }

    override fun onRejected(reason: String) {
        lobbyMessage = reason
        say(reason, flush = true)
    }

    private fun onState(parts: List<String>) {
        // STATE|embaralhando|placar,..|lugares,..|rodada
        val shuffle = parts[1] == "1"
        parts[2].split(',').forEachIndexed { i, v -> if (i < 4) scores[i] = v.toIntOrNull() ?: 0 }
        seatIds = parts[3].split(',').filter { it.isNotEmpty() }
        mode = Mode.BT
        waitingAccept = false
        val newR = Round.decode(parts.subList(4, parts.size).joinToString("|"))
        val old = round
        round = newR
        if (screen != Screen.GAME) screen = Screen.GAME
        if (shuffle) {
            shuffling = true
            sfx.playShuffle()
            say("Embaralhando as pedras", flush = true)
            bump()
            viewModelScope.launch {
                delay(1700)
                shuffling = false
                announceTurn(newR, newRound = true)
                bump()
            }
            return
        }
        if (old == null || old.n != newR.n || old.board.size > newR.board.size) { shuffling = false; bump(); return }
        afterChange(Snap(old), newR)
    }

    override fun onCleared() {
        cpuJob?.cancel()
        link.closeAll()
        voice.shutdown()
        sfx.release()
    }
}
