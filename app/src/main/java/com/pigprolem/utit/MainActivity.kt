package com.pigprolem.utit

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class P(val dark: Boolean, val bg1: Color, val bg2: Color, val glass: Color, val edge: Color, val ink: Color,
        val sub: Color, val kw: Color, val str: Color, val fn: Color, val num: Color)

val LightP = P(false, Color(0xFFF5F5F8), Color(0xFFE4E6EE), Color.White.copy(alpha = 0.62f), Color.White,
    Color(0xFF111114), Color(0xFF6E6E7A), Color(0xFF7A4DD8), Color(0xFF1E8E5A), Color(0xFFD6336C), Color(0xFFB86E00))
val DarkP = P(true, Color(0xFF09090B), Color(0xFF18181D), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.22f),
    Color(0xFFF5F5F7), Color(0xFF9A9AA6), Color(0xFFB79CFF), Color(0xFF7FD6A8), Color(0xFFFF8FB1), Color(0xFFFFC46B))
val LocalP = compositionLocalOf { LightP }
val RED = Color(0xFFE5484D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sp = getSharedPreferences("pu", 0)
        setContent { App(sp) }
    }
}

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
fun T(s: String, size: Int = 16, c: Color = LocalP.current.ink, w: FontWeight = FontWeight.Medium,
      mono: Boolean = false, align: TextAlign? = null, mod: Modifier = Modifier) =
    Text(s, modifier = mod, color = c, fontSize = size.sp, fontWeight = w, textAlign = align,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        lineHeight = (size * 1.4f).sp)

@Composable
fun Btn(text: String, enabled: Boolean = true, filled: Boolean = true, mod: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalP.current
    val bg = if (filled) p.ink.copy(alpha = if (enabled) 1f else 0.2f) else Color.Transparent
    Box(mod.clip(RoundedCornerShape(18.dp)).background(bg)
        .then(if (!filled) Modifier.glass(18.dp) else Modifier)
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 16.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center) {
        T(text, 16, if (filled) p.bg1 else p.ink, FontWeight.Bold)
    }
}

fun AnnotatedString.Builder.hlTo(t: String, p: P) {
    val re = Regex("\"[^\"]*\"?|\\b(if|else|for|in|range)\\b|\\bprint\\b|\\b\\d+\\b")
    var i = 0
    for (m in re.findAll(t)) {
        append(t.substring(i, m.range.first))
        val c = when {
            m.value.startsWith("\"") -> p.str
            m.value == "print" -> p.fn
            m.value[0].isDigit() -> p.num
            else -> p.kw
        }
        withStyle(SpanStyle(color = c)) { append(m.value) }
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
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8FB1).copy(alpha = a), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.12f), size.width * 0.8f), size.width * 0.8f, Offset(size.width * 0.9f, size.height * 0.12f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF9DB4FF).copy(alpha = a), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.7f), size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.05f, size.height * 0.7f))
    }
}

@Composable
fun App(sp: SharedPreferences) {
    val p = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkP else LightP
    var stars by remember { mutableStateOf(sp.getString("d", "0,0,0")!!.split(",").map { it.toInt() }) }
    var corn by remember { mutableStateOf(sp.getInt("c", 0)) }
    var cur by remember { mutableStateOf(-1) }
    var result by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    CompositionLocalProvider(LocalP provides p) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg1, p.bg2)))) {
            Blobs()
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val r = result
                when {
                    r != null -> ResultScreen(r.first, r.second) { result = null; cur = -1 }
                    cur >= 0 -> LessonScreen(LEVELS[cur], { cur = -1 }) { s, c ->
                        val ns = stars.toMutableList(); ns[cur] = maxOf(ns[cur], s); stars = ns; corn += c
                        sp.edit().putString("d", ns.joinToString(",")).putInt("c", corn).apply()
                        result = s to c
                    }
                    else -> Home(stars, corn) { cur = it }
                }
            }
        }
    }
}

@Composable
fun Home(stars: List<Int>, corn: Int, onOpen: (Int) -> Unit) {
    val p = LocalP.current
    var face by remember { mutableStateOf(F.IDLE) }
    var bump by remember { mutableStateOf(0) }
    var idx by remember { mutableStateOf(0) }
    var say by remember { mutableStateOf("Chạm vào tớ đi, tớ có 9 biểu cảm đó!") }
    val order = listOf(F.HAPPY, F.LOVE, F.WOW, F.COOL, F.THINK, F.SLEEPY, F.SAD, F.EAT)
    val lines = mapOf(F.HAPPY to "Hihi, vui quá!", F.LOVE to "Yêu cậu nhiều!", F.WOW to "Ủa gì dợ?!", F.COOL to "Tớ ngầu chưa?",
        F.THINK to "Hmm, để tớ nghĩ...", F.SLEEPY to "Zzz... cho tớ ngủ xíu", F.SAD to "Huhu...", F.EAT to "Nhóp nhép, ngon!")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Pigprolem Utit", 28, w = FontWeight.Bold)
                T("Học Python mỗi ngày vài phút", 14, p.sub)
            }
            Row(Modifier.glass(20.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFC46B)))
                Spacer(Modifier.width(8.dp)); T("$corn", 16, w = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.pointerInput(Unit) {
                detectTapGestures {
                    val f = order[idx++ % order.size]; face = f; bump++; say = lines[f]!!
                    when (f) { F.SAD -> Sfx.whine(); F.EAT -> Sfx.crunch(); F.SLEEPY -> {}; else -> Sfx.oink() }
                }
            }) { Pig(face, 230.dp, bump) }
        }
        Box(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp)) { T(say, 15, align = TextAlign.Center, mod = Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(22.dp))
        T("Lộ trình", 20, w = FontWeight.Bold)
        LEVELS.forEachIndexed { i, lv ->
            val open = i == 0 || stars[i - 1] > 0
            Row(Modifier.padding(top = 14.dp).alpha(if (open) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).glass(23.dp), contentAlignment = Alignment.Center) {
                    T(if (open) "${i + 1}" else "–", 16, w = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).glass(22.dp).clickable(enabled = open) { onOpen(i) }.padding(16.dp)) {
                    T(lv.kind.uppercase(), 11, p.sub, FontWeight.Bold)
                    T(lv.title, 18, w = FontWeight.Bold)
                    Row {
                        T(lv.sub, 13, p.sub, mod = Modifier.weight(1f))
                        T("★".repeat(stars[i]) + "☆".repeat(3 - stars[i]), 14)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

fun outOf(s: Step) = when (s) { is Learn -> s.out; is Ex -> s.out; else -> emptyList() }

@Composable
fun CodeCard(file: String, content: @Composable () -> Unit) {
    val p = LocalP.current
    Column(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(p.ink.copy(alpha = 0.2f))); Spacer(Modifier.width(5.dp)) }
            Spacer(Modifier.width(4.dp)); T(file, 12, p.sub, mono = true)
        }
        Spacer(Modifier.height(10.dp)); content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(lv: Level, onExit: () -> Unit, onDone: (Int, Int) -> Unit) {
    val p = LocalP.current
    val scope = rememberCoroutineScope()
    var si by remember { mutableStateOf(0) }
    var wrong by remember { mutableStateOf(0) }
    var face by remember { mutableStateOf(F.IDLE) }
    var bump by remember { mutableStateOf(0) }
    var pick by remember(si) { mutableStateOf(-1) }
    var shown by remember(si) { mutableStateOf(0) }
    var err by remember(si) { mutableStateOf(false) }
    var busy by remember(si) { mutableStateOf(false) }
    var fb by remember(si) { mutableStateOf<Triple<Boolean, String, String>?>(null) }
    val st = lv.steps[si]
    val nEx = lv.steps.count { it !is Learn }
    LaunchedEffect(si) { face = if (st is Learn) F.IDLE else F.THINK }
    fun next() {
        if (si + 1 >= lv.steps.size) onDone(if (wrong == 0) 3 else if (wrong == 1) 2 else 1, (nEx - wrong) * 5) else si++
    }
    val order = remember(si) { (if (st is Ex) st.opts.indices.shuffled() else emptyList()) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("✕", 22, p.sub, mod = Modifier.clickable { onExit() }.padding(8.dp))
                Row(Modifier.weight(1f).padding(start = 8.dp)) {
                    lv.steps.forEachIndexed { i, _ ->
                        Box(Modifier.weight(1f).padding(horizontal = 2.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (i < si) p.ink else if (i == si) p.ink.copy(alpha = 0.5f) else p.ink.copy(alpha = 0.12f)))
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pig(face, 96.dp, bump)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).glass(18.dp).padding(14.dp)) { T(st.say, 15) }
                }
                Spacer(Modifier.height(14.dp))
                when (st) {
                    is Bug -> CodeCard(lv.file) {
                        st.lines.forEachIndexed { i, ln ->
                            val done = fb != null
                            val bg = when {
                                done && i == st.bad -> p.str.copy(alpha = 0.25f)
                                done && i == pick -> RED.copy(alpha = 0.2f)
                                i == pick -> p.ink.copy(alpha = 0.1f)
                                else -> Color.Transparent
                            }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
                                .clickable(enabled = !busy) { pick = i; Sfx.crunch() }.padding(8.dp)) {
                                T("${i + 1}", 14, p.sub, mono = true, mod = Modifier.width(24.dp))
                                Text(buildAnnotatedString { hlTo(ln, p) }, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = p.ink)
                            }
                        }
                    }
                    else -> {
                        val fill = if (st is Ex && !st.pred && pick >= 0) st.opts[pick] else null
                        val code = if (st is Learn) st.code else (st as Ex).code
                        CodeCard(lv.file) { Text(codeText(code, fill, p), fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = p.ink, lineHeight = 24.sp) }
                        Spacer(Modifier.height(12.dp))
                        val out = outOf(st)
                        val guess = if (st is Ex && st.pred && pick >= 0 && shown == 0 && !err) st.opts[pick].split("\n") else emptyList()
                        Column(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp)) {
                            T("\$ python ${lv.file}", 13, p.sub, mono = true)
                            out.take(shown).forEach { T(it, 15, mono = true) }
                            guess.forEach { T(it, 15, p.sub, mono = true) }
                            if (err) T("Lỗi! Kết quả không đúng ý heo", 14, RED, mono = true)
                            if (shown == 0 && guess.isEmpty() && !err) Spacer(Modifier.height(22.dp))
                        }
                        if (st is Ex) {
                            FlowRow(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                order.forEach { i ->
                                    val sel = pick == i
                                    Box(Modifier.then(if (sel) Modifier.clip(RoundedCornerShape(14.dp)).background(p.ink) else Modifier.glass(14.dp))
                                        .clickable(enabled = !busy) { pick = i; Sfx.crunch() }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                                        T(st.opts[i].replace("\n", " ↵ "), 16, if (sel) p.bg1 else p.ink, FontWeight.Bold, mono = true)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            when (st) {
                is Learn -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("▶ Chạy thử", !busy, false, Modifier.weight(1f)) {
                        busy = true
                        scope.launch {
                            face = F.WOW; bump++; Sfx.oink()
                            for (i in 1..st.out.size) { shown = i; delay(280) }
                            face = F.HAPPY; busy = false
                        }
                    }
                    Btn("Tiếp theo", !busy, true, Modifier.weight(1f)) { next() }
                }
                is Ex -> Btn("▶ Chạy", pick >= 0 && !busy, true, Modifier.fillMaxWidth()) {
                    busy = true
                    val ok = pick == st.ans
                    scope.launch {
                        if (ok) {
                            face = F.EAT
                            for (i in 1..st.out.size) { shown = i; delay(280) }
                            face = F.HAPPY; bump++; Sfx.oink()
                            fb = Triple(true, "Chuẩn rồi!", st.why)
                        } else {
                            wrong++; err = true; face = F.SAD; bump++; Sfx.whine(); delay(600)
                            fb = Triple(false, "Chưa đúng. Đáp án: " + st.opts[st.ans].replace("\n", " ↵ "), st.why)
                        }
                    }
                }
                is Bug -> Btn("Kiểm tra", pick >= 0 && !busy, true, Modifier.fillMaxWidth()) {
                    busy = true
                    val ok = pick == st.bad
                    scope.launch {
                        if (ok) { face = F.HAPPY; bump++; Sfx.oink() } else { wrong++; face = F.SAD; bump++; Sfx.whine() }
                        delay(400)
                        fb = Triple(ok, if (ok) "Bắt được bug rồi!" else "Chưa đúng, bug nằm ở dòng ${st.bad + 1}", st.why)
                    }
                }
            }
        }
        val f = fb
        if (f != null) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(if (p.dark) Color(0xFF202026) else Color.White)
                .padding(20.dp)) {
                T(f.second, 20, if (f.first) p.str else RED, FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); T(f.third, 15)
                Spacer(Modifier.height(14.dp)); Btn("Tiếp tục", true, true, Modifier.fillMaxWidth()) { next() }
            }
        }
    }
}

@Composable
fun ResultScreen(stars: Int, corn: Int, onBack: () -> Unit) {
    var bump by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { bump = 1; Sfx.cheer(); delay(500); Sfx.oink() }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Pig(F.LOVE, 240.dp, bump)
        T("Hoàn thành!", 30, w = FontWeight.Bold)
        Spacer(Modifier.height(6.dp)); T("★".repeat(stars) + "☆".repeat(3 - stars), 32)
        Spacer(Modifier.height(14.dp)); T("+$corn bắp", 18, LocalP.current.sub)
        Spacer(Modifier.weight(1f))
        Btn("Về trang chính", true, true, Modifier.fillMaxWidth()) { onBack() }
    }
}
