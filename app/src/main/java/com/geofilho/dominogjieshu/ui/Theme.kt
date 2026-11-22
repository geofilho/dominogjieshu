package com.geofilho.dominogjieshu.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Cores do modelo HTML "Dominó Acessível". */
object Palette {
    val Bg = Color(0xFF1A1A1A)
    val Text = Color(0xFFECF0F1)
    val AreaBorder = Color(0xFF34495E)
    val Action = Color(0xFF2980B9)
    val Start = Color(0xFF27AE60)
    val Status = Color(0xFFF1C40F)
    val Felt = Color(0xFF0E5A3A)
    val FeltDark = Color(0xFF083D27)
    val Ivory = Color(0xFFF7F1E1)
    val IvoryEdge = Color(0xFFCBBF9F)
    val Pip = Color(0xFF1B1B1B)
    val Highlight = Color(0xFFFFC94A)
    val Back = Color(0xFF7A1F1F)
}

@Composable
fun DominoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Action,
            onPrimary = Color.White,
            secondary = Palette.Start,
            onSecondary = Color.White,
            background = Palette.Bg,
            surface = Palette.Bg,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
        ),
    ) {
        // Texto claro por padrão em todo o app (sem isso, textos fora de um Surface saíam pretos no fundo escuro).
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
