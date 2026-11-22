package com.geofilho.dominogjieshu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geofilho.dominogjieshu.game.EndReason
import com.geofilho.dominogjieshu.game.Round
import com.geofilho.dominogjieshu.game.Side
import com.geofilho.dominogjieshu.game.Tile
import com.geofilho.dominogjieshu.game.Words
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(vm: AppViewModel) {
    val askExit = rememberConfirm("Deseja sair do jogo?") { vm.backToMenu() }
    val screenReader = rememberScreenReaderOn()
    // Voltar do Android (botão, gesto padrão ou gesto de voltar do TalkBack): pergunta antes de sair.
    BackHandler { askExit() }
    // A rodada é um objeto mutável: [v] muda a cada jogada e é passado às partes da tela
    // para que o Compose as redesenhe (senão ele reaproveita o desenho antigo).
    val v = vm.version
    val r = vm.round ?: return
    val me = vm.me
    var choosing by remember { mutableStateOf<Tile?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Histórico") },
            text = { Text(vm.historyText(), fontSize = 17.sp) },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Fechar") } },
        )
    }
    if (choosing != null && (!vm.canAct() || choosing !in r.hands[me])) choosing = null

    // A tela cabe inteira, sem rolar: mesa e mão ficam em áreas próprias.
    // Só a área de baixo (suas pedras / fim da rodada) rola, se precisar.
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding()
            .semantics { isTraversalGroup = true }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1) Placar (tocar mostra e fala o histórico). Sem botão de sair: use o Voltar do Android.
        Text(
            vm.placarDisplay(),
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = "ouvir o histórico") { showHistory = true; vm.speakHistory() }
                .padding(4.dp)
                .semantics {
                    contentDescription = "Placar, melhor de 5: ${vm.placarText()}"
                    role = Role.Button
                },
        )
        // Linha de status amarela (só visual: a voz do app já fala tudo, o TalkBack não repete).
        Text(
            vm.status,
            color = Palette.Status,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clearAndSetSemantics { },
        )

        // 2) Mesa: resumo das pontas  3) cada pedra da mesa (fileira que rola para o lado)
        Area("Mesa", spokenTitle = false) {
            Text(
                if (r.board.isEmpty()) "Vazia"
                else "Ponta esquerda ${r.leftEnd}  ·  Ponta direita ${r.rightEnd}",
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clearAndSetSemantics { contentDescription = vm.boardSummary(r) },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Felt),
                contentAlignment = Alignment.Center,
            ) {
                if (vm.shuffling) ShuffleAnimation()
                else Board(r, v, autoScroll = !screenReader)
            }
        }

        // 4) Os outros jogadores (na ordem em que jogam)  5) Monte
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            for (p in vm.others()) {
                val n = r.hands[p].size
                val tag = if (vm.isPartner(p)) " (parceiro)" else ""
                val turn = if (r.current == p && !r.isOver) ", é a vez dele" else ""
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clearAndSetSemantics { contentDescription = "${vm.nameOf(p)}$tag, ${vm.pedras(n)} restantes$turn" },
                ) {
                    DominoTile(0, 0, vertical = true, unit = 12.dp, faceDown = true)
                    Text(
                        "  ${vm.nameOf(p)}$tag: ${vm.pedras(n)}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (r.current == p && !r.isOver) Palette.Status else Palette.Text,
                    )
                }
            }
            Text(
                if (r.n == 4) "Sem monte" else "Monte: ${vm.pedras(r.boneyard.size)}", fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clearAndSetSemantics {
                        contentDescription = if (r.n == 4) "Sem monte" else "Monte, ${vm.pedras(r.boneyard.size)}"
                    },
            )
        }

        // 6) Suas pedras  7) cada pedra — ocupa o resto da tela e rola só aqui dentro, se precisar.
        val handTitle = when (vm.mode) {
            Mode.LOCAL -> "Pedras do ${vm.nameOf(me)}"
            else -> "Suas pedras"
        }
        Area(handTitle, showTitle = false, modifier = Modifier.weight(1f)) {
            // Título nativo: no início da partida, o foco do TalkBack desce para ele.
            NativeFocusText(handTitle, token = vm.startFocus, sizeSp = 16f, what = "título Suas pedras")
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val res = r.result
                when {
                    res != null -> RoundOver(vm, r, v)
                    vm.shuffling -> Text("Embaralhando…")
                    vm.mode == Mode.LOCAL && !vm.revealed -> Curtain(vm, v)
                    else -> Hand(vm, r, me, v, onChoose = { choosing = it })
                }
                val c = choosing
                if (c != null && res == null) SideChooser(r, c, v, onPick = { s -> choosing = null; vm.play(c, s) }, onCancel = { choosing = null })
            }
        }

        // Ouvir mesa (só com leitor de tela) e, quando você precisa, Comprar pedra / Passar vez logo depois.
        // Os dois são botões nativos do Android: o app consegue pôr o foco do TalkBack neles.
        var ouvirView by remember { mutableStateOf<android.view.View?>(null) }
        var buyView by remember { mutableStateOf<android.view.View?>(null) }
        if (screenReader) {
            NativeButton("Ouvir mesa", primary = false, onView = { ouvirView = it }) { vm.describe() }
        }
        val handVisible = vm.mode != Mode.LOCAL || vm.revealed
        val needBuy = r.result == null && !vm.shuffling && handVisible && vm.canAct() && r.canDraw(me)
        val needPass = r.result == null && !vm.shuffling && handVisible && vm.canAct() && r.mustPass(me)
        if (needBuy || needPass) {
            NativeButton(if (needBuy) "Comprar pedra" else "Passar vez", primary = true, onView = { buyView = it }) {
                if (needBuy) vm.draw() else vm.pass()
            }
        }
        // Sua vez sem pedra que encaixe: foco no Comprar/Passar; se não der, no Ouvir mesa (logo antes dele).
        // (O pedido é um número que aumenta; não é "consumido" aqui, senão a tentativa se cancelaria.)
        val handledBuy = remember { intArrayOf(vm.buyFocusToken) }
        LaunchedEffect(vm.buyFocusToken, buyView) {
            val b = buyView ?: return@LaunchedEffect
            if (vm.buyFocusToken == handledBuy[0]) return@LaunchedEffect
            handledBuy[0] = vm.buyFocusToken
            if (!focusNative(b, "botão Comprar/Passar")) {
                ouvirView?.let {
                    DiagLogBridge.add("não focou o Comprar/Passar; tentando o Ouvir mesa")
                    focusNative(it, "botão Ouvir mesa")
                }
            }
        }
    }
}

/** Caixa com borda tracejada, como as ".area" do modelo. */
@Composable
private fun Area(
    title: String,
    spokenTitle: Boolean = true,
    showTitle: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .dashedBorder()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showTitle) {
                Text(
                    title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = if (spokenTitle) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics { },
                )
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

private fun Modifier.dashedBorder(): Modifier = drawBehind {
    val stroke = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
    drawRoundRect(Palette.AreaBorder, style = stroke, cornerRadius = CornerRadius(10.dp.toPx()))
}

@Composable
private fun Board(r: Round, @Suppress("UNUSED_PARAMETER") version: Int, autoScroll: Boolean) {
    if (r.board.isEmpty()) return
    val scroll = rememberScrollState()
    // Mantém visível a ponta onde a última pedra foi jogada. Com leitor de tela não rola sozinho:
    // o movimento fazia o TalkBack tirar o foco das suas pedras e levar para a mesa.
    LaunchedEffect(r.board.size) {
        if (!autoScroll) return@LaunchedEffect
        if (r.lastMove?.side == Side.LEFT) scroll.animateScrollTo(0) else scroll.animateScrollTo(scroll.maxValue)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        r.board.forEach { p ->
            // Identidade fixa por pedra: ao entrar uma pedra na ponta esquerda, as outras não "mudam de lugar".
            androidx.compose.runtime.key(p.tile.id) {
                DominoTile(
                    p.left, p.right, vertical = p.tile.isDouble, unit = 30.dp,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = if (p.tile.isDouble) Words.tile(p.tile) else "${Words.n(p.left)} e ${Words.n(p.right)}"
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hand(vm: AppViewModel, r: Round, me: Int, @Suppress("UNUSED_PARAMETER") version: Int, onChoose: (Tile) -> Unit) {
    // Pedras nativas (por id), para o app poder pôr o foco do TalkBack numa delas.
    val views = remember { HashMap<Int, NativeTileView>() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        r.hands[me].forEach { t ->
            // Identidade fixa por pedra: ao jogar uma pedra, as outras continuam sendo os mesmos itens para o TalkBack.
            androidx.compose.runtime.key(t.id) {
                val choose by androidx.compose.runtime.rememberUpdatedState(onChoose)
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx -> NativeTileView(ctx) },
                    update = { v ->
                        v.setTile(t.b, t.a, "Pedra ${Words.tile(t)}")
                        if (v.tag != t.id) {
                            v.tag = t.id
                            v.setOnClickListener { if (vm.tapTile(t).size > 1) choose(t) }
                        }
                        views[t.id] = v
                    },
                    onRelease = { v -> if (views[t.id] === v) views.remove(t.id) },
                )
            }
        }
    }
    // Comprou uma pedra que encaixa: depois da fala, o foco vai direto para ela.
    val handled = remember { intArrayOf(vm.tileFocusToken) }
    LaunchedEffect(vm.tileFocusToken) {
        if (vm.tileFocusToken == handled[0]) return@LaunchedEffect
        handled[0] = vm.tileFocusToken
        val v = views[vm.tileFocusId]
        if (v == null) { DiagLogBridge.add("pedra comprada não encontrada na tela"); return@LaunchedEffect }
        focusNative(v, "pedra comprada")
    }
}

@Composable
private fun SideChooser(r: Round, t: Tile, @Suppress("UNUSED_PARAMETER") version: Int, onPick: (Side) -> Unit, onCancel: () -> Unit) {
    // Diálogo: o TalkBack move o foco para ele automaticamente.
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Jogar ${Words.tile(t)} em qual ponta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Ponta esquerda, ${Words.n(r.leftEnd!!)}") { onPick(Side.LEFT) }
                ActionButton("Ponta direita, ${Words.n(r.rightEnd!!)}") { onPick(Side.RIGHT) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}

/** Modo mesmo aparelho: pedras ocultas até o jogador da vez tocar em "Mostrar pedras". */
@Composable
private fun Curtain(vm: AppViewModel, @Suppress("UNUSED_PARAMETER") version: Int) {
    val r = vm.round ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics { }) {
            repeat(r.hands[r.current].size) { DominoTile(0, 0, vertical = true, unit = 22.dp, faceDown = true) }
        }
        // Depois das informações da jogada, o foco do TalkBack vem para este aviso.
        val focus = remember { FocusRequester() }
        val seen = remember { vm.curtainFocus }
        LaunchedEffect(vm.curtainFocus) {
            if (vm.curtainFocus != seen) {
                val ok = runCatching { focus.requestFocus() }.isSuccess
                com.geofilho.dominogjieshu.diag.DiagLog.add("FOCO", "pedido de foco no aviso 'Passe para': $ok")
            }
        }
        Text(
            if (r.board.isEmpty()) "${vm.nameOf(r.current)} começa" else "Passe para o ${vm.nameOf(r.current)}",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 10.dp)
                .focusRequester(focus)
                .focusable()
                .semantics { heading() },
        )
        Button(
            onClick = { vm.reveal() },
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Start, contentColor = Color.White),
            shape = RoundedCornerShape(50),
            modifier = Modifier.heightIn(min = 64.dp).fillMaxWidth(0.85f),
        ) { Text("Mostrar pedras", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun RoundOver(vm: AppViewModel, r: Round, @Suppress("UNUSED_PARAMETER") version: Int) {
    val res = r.result ?: return
    val title = when {
        res.winner < 0 -> "Jogo trancado — empate (não conta)"
        res.reason == EndReason.BATIDA -> "${vm.nameOf(res.winner)} bateu!"
        else -> "Trancado: ${vm.nameOf(res.winner)} venceu a rodada!"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Título nativo: depois da leitura do resultado, o foco do TalkBack vem para ele.
        NativeFocusText(title, token = vm.resultFocus, sizeSp = 20f, what = "resultado da rodada")
        Text(
            "${if (vm.matchOver) "Placar final" else "Placar"}: ${vm.placarText()}",
            fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
        if (vm.matchOver) {
            Text(
                "🏆 ${vm.sideName(vm.matchWinner)} venceu a partida!",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Status, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        for (p in 0 until r.n) if (r.hands[p].isNotEmpty()) {
            val hand = r.hands[p]
            val summary = "${vm.nameOf(p)} ficou com ${vm.pedras(hand.size)}, somando ${r.handPips(p)} pontos"
            // Um elemento só para o TalkBack: quantas pedras, quantos pontos e quais são.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = summary + ": " + hand.joinToString(", ") { Words.tile(it) }
                },
            ) {
                Text("$summary:", fontSize = 15.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(4.dp)) {
                    hand.forEach { DominoTile(it.b, it.a, vertical = true, unit = 20.dp) }
                }
            }
        }
        Button(
            onClick = { vm.nextRound() },
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Start, contentColor = Color.White),
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(top = 10.dp).heightIn(min = 60.dp),
        ) { Text(if (vm.matchOver) "Nova partida" else "Próxima rodada", fontSize = 20.sp) }
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Action, contentColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(top = 10.dp).heightIn(min = 56.dp),
    ) { Text(label, fontSize = 18.sp) }
}

/** Pedras viradas rolando/embaralhando na mesa enquanto toca o som. */
@Composable
private fun ShuffleAnimation() {
    val progress = remember { Animatable(0f) }
    val seeds = remember { List(14) { Random.nextFloat() to Random.nextFloat() } }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1600)) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clearAndSetSemantics { contentDescription = "Embaralhando as pedras" },
    ) {
        val p = progress.value
        val tw = 18.dp.toPx(); val th = tw * 2
        seeds.forEachIndexed { i, (a, b) ->
            val ang = (a * 6.28f) + p * 9f * (if (i % 2 == 0) 1 else -1)
            val rad = size.minDimension * 0.32f * (0.4f + b * 0.6f)
            val cx = size.width / 2 + kotlin.math.cos(ang) * rad * 1.6f
            val cy = size.height / 2 + kotlin.math.sin(ang) * rad
            rotate(ang * 57f, Offset(cx, cy)) {
                drawRoundRect(Palette.Back, Offset(cx - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(4.dp.toPx()))
            }
        }
    }
}

/** Atalho para o registro de diagnóstico. */
private object DiagLogBridge {
    fun add(msg: String) = com.geofilho.dominogjieshu.diag.DiagLog.add("FOCO", msg)
}

private fun isA11yFocused(v: android.view.View) =
    runCatching { v.createAccessibilityNodeInfo().isAccessibilityFocused }.getOrDefault(false)

/**
 * Põe o foco do TalkBack numa view nativa (ACTION_ACCESSIBILITY_FOCUS), tentando algumas vezes
 * porque o TalkBack às vezes ainda está terminando de falar. Devolve true se o foco ficou nela.
 */
private suspend fun focusNative(v: android.view.View, what: String): Boolean {
    val log = com.geofilho.dominogjieshu.diag.DiagLog
    fun now() = android.os.SystemClock.uptimeMillis()
    var got = false
    for ((i, wait) in listOf(250L, 700L, 1200L).withIndex()) {
        kotlinx.coroutines.delay(wait)
        if (isA11yFocused(v)) { DiagLogBridge.add("$what já está com o foco"); got = true; break }
        val ok = v.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        DiagLogBridge.add("tentativa ${i + 1} de focar $what: aceita=$ok, na tela=${v.isShown}")
    }
    if (!got) {
        kotlinx.coroutines.delay(300)
        got = isA11yFocused(v)
    }
    if (!got) { DiagLogBridge.add("$what NÃO ficou com o foco"); return false }
    // Vigia por ~3 s: se o foco sumir sozinho (sem ir para outro item), devolve; se a pessoa
    // deslizar para outro item, para de vigiar.
    val start = now()
    var restores = 0
    while (now() - start < 3000 && restores < 4) {
        kotlinx.coroutines.delay(150)
        if (isA11yFocused(v) || !v.isShown) continue
        val lostAt = now()
        kotlinx.coroutines.delay(350)
        if (isA11yFocused(v)) continue
        if (log.lastFocusAt >= lostAt - 200) { DiagLogBridge.add("$what: a pessoa foi para outro item, paro de vigiar"); return true }
        v.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        restores++
        DiagLogBridge.add("$what perdeu o foco sozinho; devolvido ($restores)")
    }
    DiagLogBridge.add("$what ficou com o foco")
    return true
}

/** Botão nativo (android.widget.Button) com a aparência dos outros; [onView] recebe a view (ou null ao sair). */
@Composable
private fun NativeButton(
    label: String,
    primary: Boolean,
    onView: (android.view.View?) -> Unit,
    onClick: () -> Unit,
) {
    val click by androidx.compose.runtime.rememberUpdatedState(onClick)
    val viewCb by androidx.compose.runtime.rememberUpdatedState(onView)
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.widget.Button(ctx).apply {
                isAllCaps = false
                textSize = 18f
                val dp = ctx.resources.displayMetrics.density
                minHeight = (52 * dp).toInt()
                minimumHeight = (52 * dp).toInt()
                setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
                stateListAnimator = null
                setOnClickListener { click() }
                // Aparência definida uma vez só (mudar a cada atualização fazia o TalkBack largar o foco).
                setTextColor(if (primary) android.graphics.Color.WHITE else Palette.Action.toArgb())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 26 * dp
                    if (primary) setColor(Palette.Action.toArgb())
                    else { setColor(android.graphics.Color.TRANSPARENT); setStroke((1 * dp).toInt(), Palette.AreaBorder.toArgb()) }
                }
            }
        },
        update = { b ->
            // Só troca o texto se mudou: reescrever o mesmo texto faz o TalkBack perder o foco.
            if (b.text.toString() != label) b.text = label
            viewCb(b)
        },
        onRelease = { viewCb(null) },
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * Texto nativo (TextView) usado como título. Quando [token] muda (depois da fala), o app põe o
 * foco do TalkBack nele.
 */
@Composable
private fun NativeFocusText(text: String, token: Int, sizeSp: Float, what: String) {
    var view by remember { mutableStateOf<android.widget.TextView?>(null) }
    val handled = remember { intArrayOf(token) }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Palette.Text.toArgb())
                if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
                isFocusable = true
            }
        },
        update = { tv ->
            // Só atualiza se mudou (reescrever faz o TalkBack perder o foco).
            if (tv.text.toString() != text) tv.text = text
            if (tv.tag != sizeSp) { tv.textSize = sizeSp; tv.tag = sizeSp }
            if (view !== tv) view = tv
        },
        onRelease = { view = null },
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
    LaunchedEffect(token, view) {
        val v = view ?: return@LaunchedEffect
        if (token == handled[0]) return@LaunchedEffect
        handled[0] = token
        focusNative(v, what)
    }
}
