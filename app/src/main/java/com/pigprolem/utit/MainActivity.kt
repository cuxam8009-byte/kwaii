package com.pigprolem.utit

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sp = getSharedPreferences("pu", 0)
        setContent { App(sp) }
    }
}

@Composable
fun App(sp: SharedPreferences) {
    val game = remember { Game(sp) }
    val sys = isSystemInDarkTheme()
    val dark = when (game.theme) {
        1 -> false
        2 -> true
        else -> sys
    }
    val p = if (dark) DarkP else LightP
    val act = LocalContext.current as ComponentActivity
    SideEffect {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        act.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    LaunchedEffect(game.sound) { Sfx.enabled = game.sound }

    var cur by rememberSaveable { mutableStateOf(-1) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var review by remember { mutableStateOf<Level?>(null) }
    var result by remember { mutableStateOf<Outcome?>(null) }
    var confirmExit by remember { mutableStateOf(false) }

    val lesson: Level? = when {
        cur >= 0 -> LEVELS.getOrNull(cur)
        cur == -2 -> review
        else -> null
    }

    BackHandler(enabled = settings) { settings = false }
    BackHandler(enabled = lesson != null && result == null) { confirmExit = true }
    BackHandler(enabled = result != null) { result = null; cur = -1 }
    BackHandler(enabled = confirmExit) { confirmExit = false }

    CompositionLocalProvider(LocalP provides p) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg1, p.bg2)))) {
            Blobs()
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val r = result
                when {
                    r != null -> ResultScreen(
                        r,
                        onNext = { cur = r.next; result = null },
                        onHome = { result = null; cur = -1 }
                    )
                    lesson != null -> LessonScreen(
                        lesson, game,
                        onExit = { confirmExit = true },
                        onDone = { s, correct, total ->
                            val level = cur
                            val gain = game.finish(level, s, correct)
                            val nxt = if (level >= 0 && level + 1 < LEVELS.size) level + 1 else -1
                            result = Outcome(s, gain, correct, total, game.streakNow(), nxt)
                        }
                    )
                    else -> Home(
                        game,
                        onOpen = { cur = it },
                        onReview = {
                            val l = reviewLevel(game.stars)
                            if (l != null) {
                                review = l
                                cur = -2
                            }
                        },
                        onSettings = { settings = true }
                    )
                }
            }
            if (settings) SettingsSheet(game) { settings = false }
            if (confirmExit) ConfirmExit(
                onStay = { confirmExit = false },
                onLeave = { confirmExit = false; cur = -1 }
            )
        }
    }
}
