package com.geofilho.dominogjieshu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.geofilho.dominogjieshu.ui.AppViewModel
import com.geofilho.dominogjieshu.ui.BluetoothScreen
import com.geofilho.dominogjieshu.ui.DiagnosticScreen
import com.geofilho.dominogjieshu.ui.DominoTheme
import com.geofilho.dominogjieshu.ui.GameScreen
import com.geofilho.dominogjieshu.ui.HelpScreen
import com.geofilho.dominogjieshu.ui.MenuScreen
import com.geofilho.dominogjieshu.ui.Screen
import com.geofilho.dominogjieshu.ui.rememberScreenReaderOn

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.geofilho.dominogjieshu.diag.DiagLog.init(this)
        enableEdgeToEdge()
        setContent {
            DominoTheme {
                // Sem leitor de tela, a voz do app fica em silêncio (exceto avisos do modo parceiro).
                vm.screenReaderOn = rememberScreenReaderOn()
                when (vm.screen) {
                    Screen.MENU -> MenuScreen(vm)
                    Screen.BLUETOOTH -> BluetoothScreen(vm)
                    Screen.GAME -> GameScreen(vm)
                    Screen.HELP -> HelpScreen(vm)
                    Screen.DIAGNOSTIC -> DiagnosticScreen(vm)
                }
            }
        }
        // Diagnóstico: registra para onde vai o foco do TalkBack (só quando a chave está ligada).
        window.decorView.accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
            override fun onRequestSendAccessibilityEvent(
                host: android.view.ViewGroup,
                child: android.view.View,
                event: android.view.accessibility.AccessibilityEvent,
            ): Boolean {
                com.geofilho.dominogjieshu.diag.DiagLog.a11y(event)
                return super.onRequestSendAccessibilityEvent(host, child, event)
            }
        }
    }
}
