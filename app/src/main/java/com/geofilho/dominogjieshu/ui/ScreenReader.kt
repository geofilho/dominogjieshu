package com.geofilho.dominogjieshu.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex

/**
 * true quando um leitor de tela (TalkBack ou similar, que usa "explorar por toque") está ligado.
 * Atualiza na hora se a pessoa ligar/desligar o leitor com o app aberto.
 */
@Composable
fun rememberScreenReaderOn(): Boolean {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var on by remember { mutableStateOf(isScreenReaderOn(am)) }
    DisposableEffect(am) {
        val touch = AccessibilityManager.TouchExplorationStateChangeListener { on = isScreenReaderOn(am) }
        val state = AccessibilityManager.AccessibilityStateChangeListener { on = isScreenReaderOn(am) }
        am.addTouchExplorationStateChangeListener(touch)
        am.addAccessibilityStateChangeListener(state)
        onDispose {
            am.removeTouchExplorationStateChangeListener(touch)
            am.removeAccessibilityStateChangeListener(state)
        }
    }
    // Reconfere ao voltar para o app (o leitor pode ter sido ligado/desligado lá fora).
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) on = isScreenReaderOn(am)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return on
}

/** TalkBack (explorar por toque) ou qualquer outro leitor de tela com retorno falado ligado. */
private fun isScreenReaderOn(am: AccessibilityManager): Boolean {
    if (!am.isEnabled) return false
    if (am.isTouchExplorationEnabled) return true
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty()
}

/** Mostra um diálogo de confirmação (Sim/Não); devolve a função que o abre. */
@Composable
fun rememberConfirm(question: String, onConfirm: () -> Unit): () -> Unit {
    var show by remember { mutableStateOf(false) }
    if (show) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { show = false },
            title = { androidx.compose.material3.Text(question) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { show = false; onConfirm() }) {
                    androidx.compose.material3.Text("Sim")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { show = false }) {
                    androidx.compose.material3.Text("Não")
                }
            },
        )
    }
    return { show = true }
}

/**
 * Botão de sair/voltar do canto superior direito. Fica por último na varredura do
 * TalkBack (o foco não cai nele ao abrir a tela), mas é encontrado pela exploração por toque.
 * A tela que o usa deve marcar o contêiner com `isTraversalGroup = true`.
 */
@androidx.compose.runtime.Composable
fun CornerExitButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = androidx.compose.ui.Modifier.semantics { traversalIndex = 1f },
    ) { androidx.compose.material3.Text(label) }
}
