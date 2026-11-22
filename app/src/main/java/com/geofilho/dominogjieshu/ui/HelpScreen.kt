package com.geofilho.dominogjieshu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Blocos do texto "Como usar": cada um vira um elemento na varredura do TalkBack. */
private sealed interface Block {
    val text: String
    data class Title(override val text: String) : Block
    data class Heading(override val text: String) : Block
    data class Para(override val text: String) : Block
}

private const val DOWNLOAD_LINK = "https://github.com/geofilho/dominogjieshu/releases/download/ultima-versao/DominoJieshuoG.apk"

private fun helpBlocks(version: String): List<Block> {
    val t = Block::Title; val h = Block::Heading; val p = Block::Para
    return listOf(
        t("Dominó Jieshuo G (versão $version)"),
        p(
            "O Dominó Jieshuo G é um jogo de dominó para Android, leve, feito para ser jogado tanto por quem enxerga " +
                "quanto por quem usa leitor de tela. Dá para jogar contra o computador, em três níveis, com até 4 " +
                "pessoas pelo Bluetooth (com 4, em duplas) ou com um parceiro no mesmo aparelho. O jogo fala cada jogada e as pontas da " +
                "mesa, tem sons de pedra, bipes e aplausos, e guarda o histórico de partidas. Não tem propagandas e " +
                "funciona do Android 8 em diante.",
        ),
        p(
            "Esta é a versão feita para o leitor de tela chinês Jieshuo (解说). Quem usa o TalkBack deve instalar o " +
                "Dominó G normal. As duas versões podem ficar instaladas no mesmo celular.",
        ),

        h("LINK PARA BAIXAR"),
        p(DOWNLOAD_LINK),
        p("Este link sempre baixa a versão mais recente do aplicativo."),

        h("TELA PRINCIPAL, NA ORDEM DA VARREDURA"),
        p(
            "1. Jogar contra o computador: abre a escolha do nível.\n" +
                "- Fácil: o computador joga qualquer pedra que encaixe.\n" +
                "- Médio: o computador se livra primeiro das pedras pesadas e das carroças.\n" +
                "- Difícil: o computador lembra os números que você não tem, quando você compra ou passa, conta as " +
                "pedras que já saíram e tenta fechar as pontas para você.",
        ),
        p("2. Jogar pelo Bluetooth: abre a sala de jogo. Veja abaixo, em JOGAR PELO BLUETOOTH."),
        p(
            "3. Jogar com um parceiro no mesmo aparelho: Jogador 1 e Jogador 2 usam o mesmo celular. Veja abaixo, " +
                "em JOGAR COM UM PARCEIRO NO MESMO APARELHO.",
        ),
        p("4. Como usar: abre este texto."),
        p(
            "5. Diagnóstico: abre uma página com estas opções.\n" +
                "- Verificar atualização: procura versão nova. Se houver, mostra as novidades e pergunta se quer " +
                "baixar e instalar. O botão também informa a versão instalada.\n" +
                "- Compartilhar jogo: abre o compartilhamento do Android com este texto, já com o número da versão " +
                "mais recente e o link para baixar, para enviar aos amigos.\n" +
                "- Ver relatório: mostra os dados do aparelho, do sistema, da tela, da memória, do Jieshuo, do Bluetooth e da voz, e pode ser copiado " +
                "ou compartilhado, para ajudar a resolver problemas.\n" +
                "- Registrar o foco do Jieshuo: chave que vem desligada. Ligada, o aplicativo anota para onde o foco do " +
                "Jieshuo vai, os toques e os momentos do jogo. Depois de jogar, toque em Ver relatório e compartilhe, " +
                "para ajudar a resolver problemas. O botão Limpar relatório apaga o que foi anotado.\n" +
                "- Silenciar sons do jogo: chave que cala os efeitos sonoros, como embaralhar, pedras, bipes, porta e " +
                "aplausos, e também os avisos falados do modo parceiro para quem não usa leitor de tela. Com o leitor de tela " +
                "ligado, as falas continuam. A escolha fica salva.",
        ),

        p(
            "Nenhuma tela tem botão de voltar, para não ser tocado sem querer. Para voltar ao menu, use o voltar do " +
                "Android: o botão voltar, o gesto de voltar ou o gesto de voltar do Jieshuo. No jogo e na sala do " +
                "Bluetooth, o aplicativo pergunta antes se você quer mesmo sair.",
        ),

        h("TELA DO JOGO, NA ORDEM DA VARREDURA"),
        p(
            "1. Placar: mostra as rodadas vencidas por cada um na partida, que é melhor de 5. Tocando no placar, a voz " +
                "fala o histórico de partidas ganhas e perdidas.",
        ),
        p("2. Resumo da mesa: por exemplo, \"Mesa com 2 pedras, ponta esquerda dois, ponta direita quatro\"."),
        p("3. Pedras da mesa: cada pedra é um elemento, da esquerda para a direita."),
        p("4. Adversário: quantas pedras restam na mão do computador ou do outro jogador."),
        p("5. Monte: quantas pedras restam para comprar."),
        p(
            "6. Suas pedras: o título, seguido de cada pedra sua, uma por elemento.\n" +
                "- Tocando numa pedra, ela é jogada. Se encaixar nas duas pontas, abre uma janela perguntando ponta " +
                "esquerda ou ponta direita, e o foco do Jieshuo vai direto para ela.\n" +
                "- Se a pedra não encaixar, o jogo avisa \"Essa pedra não encaixa!\". O jogo não sugere qual pedra jogar.",
        ),
        p(
            "7. Ouvir mesa: só aparece com o leitor de tela ligado. Fala as pontas, quantas pedras tem cada " +
                "adversário, quantas pedras estão na mesa e, depois que alguém comprar a primeira pedra, quantas restam no monte.",
        ),
        p(
            "8. Comprar pedra: aparece só quando você não tem pedra que encaixe, logo depois de Ouvir mesa. Com o " +
                "monte vazio (ou com 4 jogadores), aparece Passar vez. Quando ele aparece, o foco do Jieshuo vai para " +
                "ele; se não conseguir, vai para Ouvir mesa, e um toque para a direita chega nele.",
        ),
        p(
            "9. Sair do jogo: não há botão de sair na tela, para não ser tocado sem querer durante a partida. " +
                "Use o botão Voltar do Android, o gesto de voltar do Android ou o gesto de voltar do Jieshuo. " +
                "O jogo pergunta \"Deseja sair do jogo?\": toque em Sim para voltar ao menu ou em Não para continuar jogando.",
        ),
        p(
            "O que o jogo fala e toca:\n" +
                "- No começo de cada rodada, o som das pedras sendo embaralhadas na mesa.\n" +
                "- A cada jogada, o som da pedra batendo na mesa, e a voz diz quem jogou, qual pedra e as pontas.\n" +
                "- Ao comprar, um som de pedra e o aviso de quem comprou.\n" +
                "- Quando alguém vence a rodada, aplausos.\n" +
                "- Para quem enxerga, a linha amarela no alto da tela mostra o último aviso.",
        ),

        h("COMO O FOCO DO JIESHUO FUNCIONA NO JOGO"),
        p(
            "Com o Jieshuo ligado, o jogo leva o foco para o lugar certo em cada momento, para você não precisar " +
                "procurar na tela. O foco só muda depois que a voz do jogo termina de falar.",
        ),
        p(
            "Diferença desta versão: o Jieshuo às vezes move o foco sozinho quando a tela muda. Para o foco não " +
                "ficar pulando de um lado para o outro, o jogo pede o foco uma vez só e não insiste. Se o foco " +
                "parar em outro lugar, deslize para achar o item, ou toque em Ouvir mesa.",
        ),
        p(
            "1. Começo da rodada: a voz avisa quem começa e o foco desce para o título Suas pedras. Basta deslizar " +
                "da esquerda para a direita para ir lendo as suas pedras, uma por uma.",
        ),
        p(
            "2. Depois que você joga: a pedra jogada sai da sua mão e o foco volta para o título Suas pedras. " +
                "Deslize para a direita e você já está nas pedras que sobraram. Enquanto o adversário joga, o foco " +
                "fica ali, sem pular para a mesa.",
        ),
        p(
            "3. Quando nenhuma pedra sua encaixa: depois do aviso, o foco vai direto para o botão Comprar pedra, que " +
                "fica no fim da tela, depois de Ouvir mesa. Toque duas vezes para comprar. Se a pedra comprada também " +
                "não servir, o foco continua no botão, e é só tocar de novo. Com o monte vazio, o botão é Passar vez.",
        ),
        p(
            "4. Quando a pedra comprada encaixa: a voz diz qual pedra você comprou e o foco vai direto para ela. " +
                "Toque duas vezes para jogar.",
        ),
        p(
            "5. Quando a pedra encaixa nas duas pontas: abre uma janela perguntando ponta esquerda ou ponta direita, " +
                "já com o foco nela.",
        ),
        p(
            "6. Fim da rodada: a voz fala o resultado e o placar, e o foco vai para a frase do resultado, como Você " +
                "bateu. Logo abaixo ficam as pedras que sobraram com cada um e o botão Próxima rodada.",
        ),
        p(
            "7. Para saber como está o jogo a qualquer momento, deslize até Ouvir mesa, no fim da tela, e toque duas vezes.",
        ),

        h("JOGAR PELO BLUETOOTH"),
        p("1. Na primeira vez, digite seu nome. Ele fica salvo e pode ser alterado depois, no botão Alterar nome."),
        p(
            "2. Inserir foto: fica ao lado do nome e abre as opções Escolher da galeria e Tirar foto com a câmera. " +
                "A foto aparece para o amigo quando vocês conectam. Depois o botão vira Trocar foto, com a opção de remover.",
        ),
        p(
            "3. Ao entrar, a sala já fica aberta, esperando os amigos. Na primeira vez, o Android pede permissão de " +
                "Bluetooth e para deixar o celular visível para outros aparelhos.",
        ),
        p(
            "4. Buscar amigo: toque nesse botão e aparece a lista de aparelhos. Toque no aparelho do amigo. " +
                "Todos precisam estar com o Dominó Jieshuo G aberto nessa tela.",
        ),
        p(
            "5. A sala aceita até 4 jogadores. Depois de conectados, qualquer um pode tocar em Buscar mais um amigo " +
                "e trazer outra pessoa, até completar 4. A lista da sala mostra a foto e o nome de cada um.",
        ),
        p(
            "6. Com 2 jogadores, é um contra o outro. Com 3, cada um joga por si. Com 4, o jogo é em duplas: ao lado " +
                "de cada pessoa aparece o botão Escolher fulano como meu parceiro. Quando alguém escolhe, as duas " +
                "duplas ficam definidas e aparecem na tela.",
        ),
        p(
            "7. Qualquer um toca em Iniciar jogo e os outros tocam em Aceitar. A partida começa quando todos aceitam. " +
                "Se alguém recusar, todos voltam para a sala.",
        ),
        p(
            "8. Na tela do jogo, cada outro jogador aparece com quantas pedras tem, na ordem em que jogam. " +
                "Com 4, o seu parceiro aparece marcado como parceiro, e quem está na vez aparece em amarelo.",
        ),
        p("9. Desconectar: sai da sala e deixa a sua sala aberta de novo. Se alguém sair durante a partida, ela termina para todos."),
        p(
            "Para sair da sala, use o voltar do Android: o botão voltar, o gesto de voltar ou o gesto de voltar " +
                "do Jieshuo. O jogo pergunta se você quer voltar ao menu, com as opções Sim e Não.",
        ),

        h("JOGAR COM UM PARCEIRO NO MESMO APARELHO"),
        p("1. O Jogador 1 começa a partida. As pedras começam ocultas: toque em Mostrar pedras para ver as suas."),
        p(
            "2. Ao tocar em Mostrar pedras, toca um som de porta abrindo e a voz diz \"Mostrando pedras\", para o " +
                "outro jogador saber que as pedras estão à mostra.",
        ),
        p(
            "3. Depois da jogada, as pedras ficam ocultas de novo. A voz fala a jogada, tocam os bipes e aparece o " +
                "aviso \"Passe para o jogador\". Um bipe é para o Jogador 1, dois bipes para o Jogador 2. Com o " +
                "Jieshuo, o foco vai para esse aviso depois das outras informações.",
        ),
        p("4. Nas rodadas seguintes, começa quem venceu a rodada anterior, também com as pedras ocultas."),

        h("REGRAS"),
        p(
            "- São 28 pedras, 7 para cada jogador. Com 2 jogadores sobram 14 no monte; com 3, sobram 7; com 4, " +
                "não há monte.\n" +
                "- Contra o computador e pelo Bluetooth, quem começa a primeira rodada é sorteado e pode jogar qualquer " +
                "pedra. Nas rodadas seguintes, começa quem venceu a anterior.\n" +
                "- Sem pedra que encaixe, compra do monte; com o monte vazio (ou com 4 jogadores), passa a vez.\n" +
                "- Com 4 jogadores, as duplas jogam alternadas: você, um adversário, seu parceiro, o outro adversário.\n" +
                "- Bate quem jogar a última pedra. Se ninguém puder jogar, o jogo tranca e vence quem tiver menos pontos " +
                "na mão; com 4 jogadores, vence a dupla desse jogador. Empate no jogo trancado não conta.\n" +
                "- A partida é melhor de 5: vence quem (ou a dupla que) ganhar 3 rodadas primeiro.",
        ),

        h("HISTÓRICO"),
        p(
            "O histórico de partidas fica salvo no aparelho. Para ouvir, toque no placar durante o jogo.\n" +
                "- Contra o computador: partidas ganhas e perdidas em cada nível.\n" +
                "- Pelo Bluetooth: partidas ganhas e perdidas contra cada amigo ou grupo de amigos.\n" +
                "- No mesmo aparelho: quantas partidas o Jogador 1 e o Jogador 2 ganharam.",
        ),

        h("COMO INSTALAR"),
        p("1. Abra o link para baixar, que está no começo deste texto. O navegador baixa o arquivo DominoJieshuoG.apk."),
        p("2. Quando o download terminar, toque no arquivo, pela notificação ou pela pasta Downloads."),
        p(
            "3. O Android vai pedir permissão para instalar aplicativos daquele lugar, por exemplo do Chrome ou do " +
                "gerenciador de arquivos. Toque em Configurações, ative \"Permitir desta fonte\" e volte.",
        ),
        p("4. Toque em Instalar."),

        h("O AVISO DO PLAY PROTECT"),
        p(
            "Como o aplicativo não vem da Play Store, o Play Protect, a proteção do Google, pode mostrar um aviso de " +
                "aplicativo desconhecido. Isso é normal para aplicativos instalados fora da loja.\n" +
                "- O melhor caminho é, no próprio aviso, tocar em \"Mais detalhes\" e depois em \"Instalar mesmo " +
                "assim\". O nome dessas opções pode variar.\n" +
                "- Se o celular não mostrar essa opção e bloquear a instalação, desative o Play Protect só durante a " +
                "instalação: abra a Play Store, toque na sua foto de perfil, entre em Play Protect, toque em " +
                "Configurações e desative \"Verificar apps com o Play Protect\". Instale o aplicativo e depois volte e " +
                "ative de novo. É importante reativar, porque o Play Protect protege o celular contra aplicativos perigosos.",
        ),

        h("COMO ATUALIZAR"),
        p("1. No aplicativo, toque em Diagnóstico, na tela principal, e depois em \"Verificar atualização\"."),
        p(
            "2. Se houver versão nova, aparecem as novidades e os botões \"OK, baixar\" e \"Cancelar\". Toque em " +
                "\"OK, baixar\".",
        ),
        p(
            "3. Na primeira atualização, o Android pede uma autorização: permitir que o Dominó Jieshuo G instale aplicativos. " +
                "O aplicativo explica e abre a configuração. Ative a opção e volte, que a instalação continua sozinha. " +
                "Essa autorização só é pedida uma vez.",
        ),
        p("4. Confirme a instalação. Se aparecer o aviso do Play Protect, siga as orientações acima."),
        p("A atualização é instalada por cima e mantém o seu nome, a sua foto e o histórico de partidas."),

        p("Esta é uma versão de teste. Toda sugestão é bem-vinda!"),
    )
}

/** Texto completo (para compartilhar com amigos). */
fun helpPlainText(version: String): String = helpBlocks(version).joinToString("\n\n") { it.text }

@Composable
fun HelpScreen(vm: AppViewModel) {
    BackHandler { vm.backToMenu() }
    val version = vm.updater.installedVersionName
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .semantics { isTraversalGroup = true }
            .padding(20.dp),
    ) {
        helpBlocks(version).forEach { b ->
            when (b) {
                is Block.Title -> Text(
                    b.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 10.dp).semantics { heading() },
                )
                is Block.Heading -> Text(
                    b.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Status,
                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp).semantics { heading() },
                )
                is Block.Para -> Text(
                    b.text, fontSize = 17.sp, lineHeight = 24.sp,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
            }
        }
    }
}
