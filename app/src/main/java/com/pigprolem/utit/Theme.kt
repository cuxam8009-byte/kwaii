package com.pigprolem.utit

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

class P(
    val dark: Boolean, val bg1: Color, val bg2: Color, val glass: Color, val edge: Color, val ink: Color,
    val sub: Color, val kw: Color, val str: Color, val fn: Color, val num: Color
)

val LightP = P(
    false, Color(0xFFF5F5F8), Color(0xFFE4E6EE), Color.White.copy(alpha = 0.62f), Color.White,
    Color(0xFF111114), Color(0xFF6E6E7A), Color(0xFF7A4DD8), Color(0xFF1E8E5A), Color(0xFFD6336C), Color(0xFFB86E00)
)
val DarkP = P(
    true, Color(0xFF09090B), Color(0xFF18181D), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.22f),
    Color(0xFFF5F5F7), Color(0xFF9A9AA6), Color(0xFFB79CFF), Color(0xFF7FD6A8), Color(0xFFFF8FB1), Color(0xFFFFC46B)
)
val LocalP = compositionLocalOf { LightP }
val RED = Color(0xFFE5484D)
val UNIT_COLORS = listOf(
    Color(0xFFFF8FB1), Color(0xFF7FB2FF), Color(0xFF6CCB9B), Color(0xFFFFB45E), Color(0xFFB79CFF)
)

@Composable
fun Modifier.glass(r: Dp = 24.dp): Modifier {
    val p = LocalP.current
    val sh = RoundedCornerShape(r)
    return this
        .shadow(if (p.dark) 0.dp else 10.dp, sh, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
        .clip(sh).background(p.glass)
        .border(1.dp, Brush.verticalGradient(listOf(p.edge, p.edge.copy(alpha = 0.08f))), sh)
}

@Composable
fun T(
    s: String, size: Int = 16, c: Color = LocalP.current.ink, w: FontWeight = FontWeight.Medium,
    mono: Boolean = false, align: TextAlign? = null, mod: Modifier = Modifier
) = Text(
    s, modifier = mod, color = c, fontSize = size.sp, fontWeight = w, textAlign = align,
    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
    lineHeight = (size * 1.4f).sp
)

@Composable
fun Btn(text: String, enabled: Boolean = true, filled: Boolean = true, mod: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalP.current
    val bg = if (filled) p.ink.copy(alpha = if (enabled) 1f else 0.2f) else Color.Transparent
    Box(
        mod.heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .then(if (!filled) Modifier.glass(18.dp) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, 16, if (filled) p.bg1 else p.ink, FontWeight.Bold)
    }
}

@Composable
fun Pill(dot: Color, text: String) {
    Row(Modifier.glass(20.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        T(text, 15, w = FontWeight.Bold)
    }
}

@Composable
fun Bar(frac: Float) {
    val p = LocalP.current
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.ink.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(p.str))
    }
}

@Composable
fun Seg(opts: List<String>, sel: Int, onSel: (Int) -> Unit) {
    val p = LocalP.current
    Row(Modifier.fillMaxWidth().glass(16.dp).padding(4.dp)) {
        opts.forEachIndexed { i, o ->
            val on = i == sel
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (on) p.ink else Color.Transparent)
                    .clickable { onSel(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                T(o, 14, if (on) p.bg1 else p.ink, FontWeight.Bold)
            }
        }
    }
}

@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalP.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(label, 16, mod = Modifier.weight(1f))
        Box(
            Modifier.width(50.dp).height(30.dp).clip(RoundedCornerShape(15.dp))
                .background(if (on) p.ink else p.ink.copy(alpha = 0.18f)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(if (on) p.bg1 else Color.White))
        }
    }
}

private val KW = setOf("if", "elif", "else", "for", "while", "in", "def", "return", "and", "or", "not", "True", "False")
private val FN = setOf("print", "len", "range", "append")
private val HL = Regex("\"[^\"]*\"?|#.*|\\b[A-Za-z_]+\\b|\\b\\d+(\\.\\d+)?\\b")

fun AnnotatedString.Builder.hlTo(t: String, p: P) {
    var i = 0
    for (m in HL.findAll(t)) {
        append(t.substring(i, m.range.first))
        val v = m.value
        val c: Color? = when {
            v.startsWith("\"") -> p.str
            v.startsWith("#") -> p.sub
            v[0].isDigit() -> p.num
            v in KW -> p.kw
            v in FN -> p.fn
            else -> null
        }
        if (c != null) withStyle(SpanStyle(color = c)) { append(v) } else append(v)
        i = m.range.last + 1
    }
    append(t.substring(i))
}

fun codeText(t: String, fill: String?, p: P) = buildAnnotatedString {
    val parts = t.split("___")
    hlTo(parts[0], p)
    if (parts.size > 1) {
        if (fill == null) withStyle(SpanStyle(color = p.num, background = p.num.copy(alpha = 0.18f))) { append(" ▢ ") }
        else withStyle(SpanStyle(color = p.ink, background = p.fn.copy(alpha = 0.3f))) { append(" $fill ") }
        hlTo(parts[1], p)
    }
}

@Composable
fun Blobs() {
    val p = LocalP.current
    Canvas(Modifier.fillMaxSize()) {
        val a = if (p.dark) 0.30f else 0.45f
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFF8FB1).copy(alpha = a), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.12f), size.width * 0.8f),
            size.width * 0.8f, Offset(size.width * 0.9f, size.height * 0.12f)
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF9DB4FF).copy(alpha = a), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.7f), size.width * 0.9f),
            size.width * 0.9f, Offset(size.width * 0.05f, size.height * 0.7f)
        )
    }
}
