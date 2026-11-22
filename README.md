# Dominó Jieshuo G (Android)

Versão do Dominó G feita para o leitor de tela chinês **Jieshuo (解说)**. A versão para o TalkBack continua em [geofilho/dominog](https://github.com/geofilho/dominog). As duas podem ficar instaladas ao mesmo tempo (pacotes diferentes).

Jogo de dominó nativo (Kotlin + Jetpack Compose), para quem enxerga e adaptado para pessoas cegas
(voz própria do app, sons de pedra e suporte ao leitor de tela chinês Jieshuo (解说)).

## Modos
- **Contra o computador** – o celular joga contra você.
- **Bluetooth (até 4 pessoas; com 4, em duplas)** – digite seu nome (fica salvo) e, se quiser, insira uma foto (galeria ou câmera). A tela já é
  uma sala aberta: um dos dois toca em *Buscar amigo* e escolhe o aparelho. Qualquer um toca em *Iniciar jogo*
  e o outro *Aceita*.
- **Mesmo aparelho** – Jogador 1 e Jogador 2. Depois de cada jogada as pedras ficam ocultas e a voz diz
  "Passe para o jogador dois". O próximo toca em *Mostrar pedras*: som de abrir e a voz "Mostrando pedras".

## Regras (2 jogadores)
28 pedras (duplo-seis), 7 para cada, 14 no monte. Na primeira rodada quem começa é sorteado; depois começa quem venceu.
Sem jogada: compra do monte; monte vazio: passa. Bate quem zerar a mão. Partida: melhor de 5 (vence quem ganhar 3 rodadas).
Jogo trancado: vence quem tiver menos pontos.

## Estrutura
- `game/` – regras puras (testadas em `app/src/test`), IA e serialização do estado.
- `audio/SoundFx.kt` – sons de pedra sintetizados (sem arquivos de áudio no APK).
- `speech/Voice.kt` – voz em português (TextToSpeech).
- `bt/BtLink.kt` – Bluetooth clássico (RFCOMM). O anfitrião tem a autoridade do jogo e envia o estado.
- `ui/` – telas Compose e `AppViewModel` que coordena os modos.

## Build
`./gradlew assembleDebug` (ou baixe o APK gerado pelo GitHub Actions em *Actions → Android → artifacts*).
