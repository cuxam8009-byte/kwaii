package com.pigprolem.utit

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

enum class F { IDLE, HAPPY, SAD, EAT, LOVE, WOW, SLEEPY, THINK, COOL }

private val PINK = Color(0xFFFF9EB5)
private val PINK2 = Color(0xFFF0789A)
private val SNOUT = Color(0xFFFFC2D1)
private val DK = Color(0xFF2B2230)
private val MOUTH = Color(0xFFB8325A)

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) =
    drawOval(c, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, c: Color, w: Float) =
    drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

private fun DrawScope.curve(c: Color, w: Float, fill: Boolean = false, b: Path.() -> Unit) {
    val p = Path().apply(b)
    if (fill) drawPath(p, c) else drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round))
}

private fun DrawScope.heart(cx: Float, cy: Float, k: Float, c: Color) {
    val p = Path().apply {
        moveTo(cx, cy + 10 * k); lineTo(cx - 20 * k, cy - 12 * k)
        quadraticBezierTo(cx - 28 * k, cy - 26 * k, cx - 14 * k, cy - 32 * k)
        quadraticBezierTo(cx - 2 * k, cy - 36 * k, cx, cy - 22 * k)
        quadraticBezierTo(cx + 2 * k, cy - 36 * k, cx + 14 * k, cy - 32 * k)
        quadraticBezierTo(cx + 28 * k, cy - 26 * k, cx + 20 * k, cy - 12 * k)
        close()
    }
    drawPath(p, c)
}

private fun DrawScope.eyeOpen(cx: Float, px: Float, bl: Float) {
    scale(1f, bl, Offset(cx, 138f)) {
        oval(cx, 138f, 22f, 26f, Color.White)
        drawCircle(DK, 11f, Offset(cx + px, 142f))
        drawCircle(Color.White, 4f, Offset(cx + px + 4f, 136f))
    }
}

private fun DrawScope.txt(s: String, x: Float, y: Float, size: Float, c: Color) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = size; color = c.toArgb(); isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
}

@Composable
fun Pig(f: F, size: Dp, bump: Int = 0) {
    val inf = rememberInfiniteTransition(label = "pig")
    val bob by inf.animateFloat(
        initialValue = 0f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val blink by inf.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes<Float> {
            durationMillis = 4000; 1f at 0; 1f at 3700; 0.08f at 3850; 1f at 4000
        }), label = "blink")
    val chew by inf.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(250), RepeatMode.Reverse), label = "chew")
    val arms = when (f) {
        F.HAPPY -> 110f to -110f; F.SAD -> 8f to -8f; F.EAT -> -140f to 140f
        F.LOVE -> -150f to 150f; F.WOW -> 90f to -90f; F.THINK -> 0f to 140f
        F.COOL -> 0f to -100f; else -> 0f to 0f
    }
    val al by animateFloatAsState(arms.first, spring(dampingRatio = 0.45f), label = "al")
    val ar by animateFloatAsState(arms.second, spring(dampingRatio = 0.45f), label = "ar")
    val hop = remember { Animatable(0f) }
    val rot = remember { Animatable(0f) }
    LaunchedEffect(bump) {
        if (bump > 0) {
            if (f == F.SAD) {
                for (a in listOf(-7f, 6f, -4f, 0f)) rot.animateTo(a, tween(90))
            } else {
                hop.animateTo(-60f, tween(160)); hop.animateTo(0f, spring(0.4f))
            }
        }
    }
    val d = LocalDensity.current.density
    Canvas(
        Modifier.width(size).aspectRatio(380f / 350f).graphicsLayer {
            translationY = (bob + hop.value) * d; rotationZ = rot.value
        }
    ) {
        val s = this.size.width / 380f
        scale(s, Offset.Zero) {
            oval(190f, 334f, 95f, 9f, Color.Black.copy(alpha = 0.15f))
            curve(PINK2, 7f) { moveTo(282f, 255f); quadraticBezierTo(314f, 238f, 302f, 265f); quadraticBezierTo(294f, 284f, 314f, 274f) }
            oval(190f, 250f, 95f, 78f, PINK); oval(190f, 268f, 55f, 48f, SNOUT)
            oval(148f, 322f, 26f, 12f, PINK2); oval(232f, 322f, 26f, 12f, PINK2)
            curve(PINK, 0f, true) { moveTo(100f, 112f); quadraticBezierTo(66f, 48f, 130f, 58f); quadraticBezierTo(162f, 68f, 162f, 100f); close() }
            curve(PINK2, 0f, true) { moveTo(110f, 100f); quadraticBezierTo(96f, 70f, 130f, 72f); quadraticBezierTo(146f, 78f, 146f, 98f); close() }
            curve(PINK, 0f, true) { moveTo(280f, 112f); quadraticBezierTo(314f, 48f, 250f, 58f); quadraticBezierTo(218f, 68f, 218f, 100f); close() }
            curve(PINK2, 0f, true) { moveTo(270f, 100f); quadraticBezierTo(284f, 70f, 250f, 72f); quadraticBezierTo(234f, 78f, 234f, 98f); close() }
            oval(190f, 160f, 112f, 96f, PINK)
            drawCircle(Color(0xFFFF6F91).copy(alpha = 0.5f), 17f, Offset(118f, 190f))
            drawCircle(Color(0xFFFF6F91).copy(alpha = 0.5f), 17f, Offset(262f, 190f))
            when (f) {
                F.IDLE, F.SAD, F.THINK -> { eyeOpen(150f, 4f, blink); eyeOpen(230f, -4f, blink) }
                F.HAPPY, F.EAT -> {
                    curve(DK, 7f) { moveTo(128f, 146f); quadraticBezierTo(150f, 116f, 172f, 146f) }
                    curve(DK, 7f) { moveTo(208f, 146f); quadraticBezierTo(230f, 116f, 252f, 146f) }
                }
                F.SLEEPY -> {
                    curve(DK, 7f) { moveTo(128f, 138f); quadraticBezierTo(150f, 152f, 172f, 138f) }
                    curve(DK, 7f) { moveTo(208f, 138f); quadraticBezierTo(230f, 152f, 252f, 138f) }
                }
                F.LOVE -> { heart(150f, 148f, 1f, Color(0xFFE8335F)); heart(230f, 148f, 1f, Color(0xFFE8335F)) }
                F.WOW -> {
                    oval(150f, 136f, 27f, 31f, Color.White); drawCircle(DK, 7f, Offset(150f, 138f))
                    oval(230f, 136f, 27f, 31f, Color.White); drawCircle(DK, 7f, Offset(230f, 138f))
                }
                F.COOL -> {
                    drawRoundRect(DK, Offset(114f, 116f), Size(68f, 44f), androidx.compose.ui.geometry.CornerRadius(16f))
                    drawRoundRect(DK, Offset(198f, 116f), Size(68f, 44f), androidx.compose.ui.geometry.CornerRadius(16f))
                    drawRect(DK, Offset(180f, 128f), Size(20f, 6f))
                    line(126f, 126f, 146f, 126f, Color.White.copy(alpha = 0.6f), 5f)
                    line(210f, 126f, 230f, 126f, Color.White.copy(alpha = 0.6f), 5f)
                }
            }
            if (f == F.SAD) { line(126f, 98f, 166f, 110f, MOUTH, 6f); line(254f, 98f, 214f, 110f, MOUTH, 6f) }
            if (f == F.THINK) { line(128f, 108f, 170f, 106f, MOUTH, 6f); line(212f, 94f, 252f, 100f, MOUTH, 6f) }
            oval(190f, 190f, 46f, 33f, SNOUT)
            oval(174f, 190f, 6f, 10f, Color(0xFFD9527A)); oval(206f, 190f, 6f, 10f, Color(0xFFD9527A))
            when (f) {
                F.HAPPY -> curve(MOUTH, 0f, true) { moveTo(160f, 222f); quadraticBezierTo(190f, 270f, 220f, 222f); close() }
                F.SAD -> curve(MOUTH, 5f) { moveTo(168f, 242f); quadraticBezierTo(190f, 222f, 212f, 242f) }
                F.SLEEPY, F.THINK -> line(172f, 232f, 208f, 232f, MOUTH, 5f)
                F.WOW -> oval(190f, 236f, 11f, 14f, MOUTH)
                F.EAT -> oval(190f, 234f, 20f, 13f * chew, MOUTH)
                else -> curve(MOUTH, 5f) { moveTo(166f, 230f); quadraticBezierTo(190f, 244f, 214f, 230f) }
            }
            rotate(al, Offset(118f, 245f)) {
                line(118f, 245f, 92f, 295f, PINK, 26f); drawCircle(Color(0xFFF58FAA), 15f, Offset(92f, 295f))
            }
            rotate(ar, Offset(262f, 245f)) {
                line(262f, 245f, 288f, 295f, PINK, 26f); drawCircle(Color(0xFFF58FAA), 15f, Offset(288f, 295f))
            }
            when (f) {
                F.LOVE -> { heart(320f, 100f, 0.5f, Color(0xFFE8335F)); heart(60f, 90f, 0.4f, Color(0xFFE8335F)) }
                F.SLEEPY -> { txt("Z", 290f, 80f, 34f, Color(0xFF8E8EA8)); txt("z", 322f, 48f, 24f, Color(0xFF8E8EA8)) }
                F.SAD -> curve(Color(0xFF7CC7F2), 0f, true) { moveTo(296f, 120f); quadraticBezierTo(284f, 140f, 296f, 148f); quadraticBezierTo(308f, 140f, 296f, 120f) }
                F.THINK -> txt("?", 296f, 80f, 46f, Color(0xFF8E8EA8))
                else -> {}
            }
        }
    }
}
