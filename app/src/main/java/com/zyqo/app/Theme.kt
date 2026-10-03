package com.zyqo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Z {
    val bg = Color(0xFF0B0B0D)
    val card = Color(0xFF151518)
    val card2 = Color(0xFF1E1E22)
    val ink = Color(0xFFF5F5F7)
    val sub = Color(0xFF9A9AA3)
    val line = Color(0x1FFFFFFF)
    val danger = Color(0xFFFF5A6E)
    val ok = Color(0xFF5BE3A0)
}

@Composable
fun ZyqoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Z.bg,
            surface = Z.bg,
            onSurface = Z.ink,
            onBackground = Z.ink,
            primary = Z.ink,
            onPrimary = Z.bg
        )
    ) {
        Surface(color = Z.bg, contentColor = Z.ink, content = content)
    }
}
