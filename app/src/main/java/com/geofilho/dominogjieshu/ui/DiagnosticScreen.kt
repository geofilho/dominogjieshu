package com.geofilho.dominogjieshu.ui

import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun DiagnosticScreen(vm: AppViewModel) {
    BackHandler { vm.backToMenu() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .semantics { isTraversalGroup = true }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Diagnóstico", fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp).semantics { heading() },
        )
        MenuButton("Verificar atualização (versão ${vm.updater.installedVersionName})", Palette.Start) { vm.checkUpdate() }
        MenuButton("Compartilhar jogo", Palette.Action) {
            scope.launch {
                // Usa o número da última versão publicada (sem internet, o da instalada).
                val latest = withTimeoutOrNull(4000) { vm.updater.latestVersion() }
                val version = latest?.let { "1.0.$it" } ?: vm.updater.installedVersionName
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Dominó Jieshuo G")
                    .putExtra(Intent.EXTRA_TEXT, helpPlainText(version))
                ctx.startActivity(Intent.createChooser(send, "Compartilhar o Dominó Jieshuo G"))
            }
        }
        // Registro do foco do TalkBack (vem desligado para não pesar).
        var diagOn by remember { mutableStateOf(com.geofilho.dominogjieshu.diag.DiagLog.enabled) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 56.dp)
                .toggleable(value = diagOn, role = Role.Switch) {
                    diagOn = it
                    com.geofilho.dominogjieshu.diag.DiagLog.setEnabled(it)
                    if (vm.screenReaderOn) vm.voice.speak(if (it) "Registro ligado" else "Registro desligado", flush = true)
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Registrar o foco do Jieshuo", fontSize = 19.sp)
                Text(
                    "Anota para onde o foco vai, os toques e os momentos do jogo. Depois toque em Ver relatório e compartilhe.",
                    fontSize = 14.sp, color = Palette.Text.copy(alpha = 0.75f),
                )
            }
            Switch(checked = diagOn, onCheckedChange = null)
        }
        MenuButton("Ver relatório", Palette.AreaBorder) { report = buildReport(ctx, vm) }
        MenuButton("Limpar relatório", Palette.AreaBorder) {
            com.geofilho.dominogjieshu.diag.DiagLog.clear()
            if (vm.screenReaderOn) vm.voice.speak("Relatório limpo", flush = true)
        }
        // Chave: lida pelo TalkBack como "Silenciar sons do jogo, chave, ativada/desativada".
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .heightIn(min = 56.dp)
                .toggleable(value = vm.soundsMuted, role = Role.Switch) { vm.changeSoundsMuted(it) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Silenciar sons do jogo", fontSize = 19.sp)
                Text(
                    "Cala embaralhar, pedras, bipes, porta, aplausos e os avisos falados do modo parceiro. A fala do leitor de tela continua.",
                    fontSize = 14.sp, color = Palette.Text.copy(alpha = 0.75f),
                )
            }
            Switch(checked = vm.soundsMuted, onCheckedChange = null)
        }
        UpdateDialog(vm)
    }

    report?.let { text ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("Relatório") },
            text = {
                Text(text, fontSize = 15.sp, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Relatório Dominó Jieshuo G", text))
                        if (vm.screenReaderOn) vm.voice.speak("Relatório copiado", flush = true)
                    }) { Text("Copiar") }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Relatório do Dominó Jieshuo G")
                            .putExtra(Intent.EXTRA_TEXT, text)
                        ctx.startActivity(Intent.createChooser(send, "Compartilhar relatório"))
                    }) { Text("Compartilhar") }
                }
            },
            dismissButton = { TextButton(onClick = { report = null }) { Text("Fechar") } },
        )
    }
}

/** Dados do aparelho, do sistema e do app, para ajudar a resolver problemas. */
private fun buildReport(ctx: Context, vm: AppViewModel): String {
    val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val bt = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    val readers = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN)
        .joinToString { it.resolveInfo.serviceInfo.packageName }.ifEmpty { "nenhum" }
    val tts = runCatching {
        android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.TTS_DEFAULT_SYNTH)
    }.getOrNull() ?: "?"
    return buildString {
        appendLine("Dominó Jieshuo G versão ${vm.updater.installedVersionName} (${vm.updater.installedVersion})")
        appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), compilação ${Build.DISPLAY}")
        // Tela
        val dm = ctx.resources.displayMetrics
        val cfg = ctx.resources.configuration
        val inches = runCatching {
            val wIn = dm.widthPixels / dm.xdpi; val hIn = dm.heightPixels / dm.ydpi
            String.format(java.util.Locale.US, "%.1f", kotlin.math.sqrt(wIn * wIn + hIn * hIn))
        }.getOrDefault("?")
        appendLine("Tela: ${dm.widthPixels} x ${dm.heightPixels} pixels, ${cfg.screenWidthDp} x ${cfg.screenHeightDp} dp, densidade ${dm.densityDpi} dpi (${dm.density}x), cerca de $inches polegadas")
        appendLine("Tamanho da fonte do sistema: ${cfg.fontScale}x")
        appendLine("Tamanho de exibição (zoom): ${cfg.densityDpi} dpi")
        appendLine("Modo escuro: ${if ((cfg.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) "ligado" else "desligado"}")
        appendLine("Orientação: ${if (cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "deitada" else "em pé"}")
        // Memória e espaço
        runCatching {
            val am2 = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo().also { am2.getMemoryInfo(it) }
            val st = android.os.StatFs(ctx.filesDir.path)
            appendLine("Memória: ${mi.totalMem / 1_048_576} MB no total, ${mi.availMem / 1_048_576} MB livres${if (mi.lowMemory) " (pouca memória)" else ""}")
            appendLine("Espaço livre no aparelho: ${st.availableBytes / 1_048_576} MB")
        }
        appendLine("Leitor de tela ligado: ${if (am.isTouchExplorationEnabled) "sim" else "não"}")
        appendLine("Serviços com fala: $readers")
        fun ver(pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
        appendLine("Versão do Jieshuo (解说): ${ver("com.nirenr.talkman") ?: "não instalado"}")
        appendLine("Versão do TalkBack: ${ver("com.google.android.marvin.talkback") ?: "não instalado"}")
        appendLine("Motor de voz padrão: $tts")
        appendLine("Voz do app: ${if (vm.voice.isReady) "pronta" else "não iniciou"}")
        appendLine("Silenciar sons do jogo: ${if (vm.soundsMuted) "ligado" else "desligado"}")
        appendLine("Bluetooth: ${when { bt == null -> "não existe"; bt.isEnabled -> "ligado"; else -> "desligado" }}")
        appendLine("Estado da conexão: ${vm.link.state.value}")
        appendLine("Nome salvo: ${vm.playerName.ifBlank { "(nenhum)" }}")
        appendLine("Foto salva: ${if (vm.myPhoto != null) "sim" else "não"}")
        append("Idioma do aparelho: ${java.util.Locale.getDefault()}")
        append("\nRegistro do foco: ${if (com.geofilho.dominogjieshu.diag.DiagLog.enabled) "ligado" else "desligado"}")
        append("\n\n--- REGISTRO ---\n")
        append(com.geofilho.dominogjieshu.diag.DiagLog.text())
    }
}
