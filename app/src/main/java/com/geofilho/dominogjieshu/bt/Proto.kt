package com.geofilho.dominogjieshu.bt

/** Mensagens trocadas entre os aparelhos (uma por linha, campos separados por |). */
object Proto {
    /** Aperto de mão ao conectar: LH|temPai(0/1)|tamanhoDoGrupo — define quem fica "acima" na rede. */
    const val LINK_HELLO = "LH"
    /** Recusa a conexão (ex.: sala cheia): REJECT|motivo. */
    const val REJECT = "REJECT"
    /** Membros abaixo deste aparelho (sobe até quem comanda): MEMBERS|id~nome,id~nome. */
    const val MEMBERS = "MEMBERS"
    /** Lista da sala (desce de quem comanda): ROSTER|emJogo|parceiroA|parceiroB|id~nome,... */
    const val ROSTER = "ROSTER"
    /** Foto de um jogador: PHOTO|id|jpeg em base64. */
    const val PHOTO = "PHOTO"
    /** Escolha de parceiro (4 jogadores): PARTNER|idA|idB. */
    const val PARTNER = "PARTNER"
    /** Convite para começar: INVITE|idDeQuemConvidou (sobe e depois desce para todos). */
    const val INVITE = "INVITE"
    /** Resposta ao convite: ANSWER|id|1 ou 0. */
    const val ANSWER = "ANSWER"
    /** Resultado do convite (desce): INVRES|0|nome de quem recusou. */
    const val INVRES = "INVRES"
    /** Estado do jogo (desce): STATE|embaralhando|placar,..|idsDosLugares,..|rodada codificada. */
    const val STATE = "STATE"
    const val PLAY = "PLAY"   // PLAY|lugar|idPedra|lado (sobe)
    const val DRAW = "DRAW"   // DRAW|lugar (sobe)
    const val PASS = "PASS"   // PASS|lugar (sobe)
    const val NEXT = "NEXT"   // pedir nova rodada (sobe)
    /** Fim do jogo porque alguém saiu (desce): ENDGAME|motivo. */
    const val ENDGAME = "ENDGAME"
}
