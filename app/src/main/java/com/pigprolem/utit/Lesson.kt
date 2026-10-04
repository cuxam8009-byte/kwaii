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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class Item(val step: Step, val tries: Int)

fun outOf(s: Step): List<String> = when (s) {
    is Learn -> s.out
    is Ex -> s.out
    is Order -> s.out
    else -> emptyList()
}

@Composable
fun CodeCard(file: String, content: @Composable () -> Unit) {
    val p = LocalP.current
    Column(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(p.ink.copy(alpha = 0.2f)))
                Spacer(Modifier.width(5.dp))
            }
            Spacer(Modifier.width(4.dp))
            T(file, 12, p.sub, mono = true)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(lv: Level, game: Game, onExit: () -> Unit, onDone: (Int, Int, Int) -> Unit) {
    val p = LocalP.current
    val scope = rememberCoroutineScope()
    val hap = LocalHapticFeedback.current
    val queue = remember(lv) { mutableStateListOf<Item>().also { q -> lv.steps.forEach { q.add(Item(it, 0)) } } }
    val nEx = remember(lv) { lv.steps.count { it !is Learn } }
    var si by remember(lv) { mutableStateOf(0) }
    var wrong by remember(lv) { mutableStateOf(0) }
    var face by remember { mutableStateOf(F.IDLE) }
    var bump by remember { mutableStateOf(0) }
    var pick by remember(si) { mutableStateOf(-1) }
    var shown by remember(si) { mutableStateOf(0) }
    var err by remember(si) { mutableStateOf(false) }
    var busy by remember(si) { mutableStateOf(false) }
    var fb by remember(si) { mutableStateOf<Triple<Boolean, String, String>?>(null) }
    val item = queue[si]
    val st = item.step
    val picked = remember(si) { mutableStateListOf<Int>() }
    val shuf = remember(si) { if (st is Order) shuffledOrder(st.lines.size) else emptyList() }
    val order = remember(si) { if (st is Ex) st.opts.indices.shuffled() else emptyList() }
    val say = (if (item.tries > 0) "Làm lại nhé! " else "") + st.say

    LaunchedEffect(si) { face = if (st is Learn) F.IDLE else F.THINK }

    fun buzz(strong: Boolean) {
        if (game.haptic) {
            hap.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
    }

    fun next() {
        if (si + 1 >= queue.size) onDone(starsFor(wrong, nEx), nEx - wrong, nEx) else si++
    }

    fun missed() {
        if (item.tries == 0) {
            wrong++
            queue.add(Item(st, 1))
        }
    }

    fun note(why: String): String =
        if (item.tries == 0) why + "\n\nCâu này sẽ quay lại ở cuối bài để bạn làm lại." else why

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("✕", 22, p.sub, mod = Modifier.clip(CircleShape).clickable { onExit() }.padding(12.dp))
                Row(Modifier.weight(1f).padding(start = 4.dp)) {
                    queue.forEachIndexed { i, _ ->
                        Box(
                            Modifier.weight(1f).padding(horizontal = 2.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (i < si) p.ink else if (i == si) p.ink.copy(alpha = 0.5f) else p.ink.copy(alpha = 0.12f))
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pig(face, 96.dp, bump)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).glass(18.dp).padding(14.dp)) { T(say, 15) }
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
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
                                    .clickable(enabled = !busy) { pick = i; Sfx.crunch(); buzz(false) }.padding(8.dp)
                            ) {
                                T("${i + 1}", 14, p.sub, mono = true, mod = Modifier.width(24.dp))
                                Text(
                                    buildAnnotatedString { hlTo(ln, p) },
                                    fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = p.ink
                                )
                            }
                        }
                    }
                    is Order -> {
                        CodeCard(lv.file) {
                            if (picked.isEmpty()) T("Chạm các dòng bên dưới theo đúng thứ tự", 14, p.sub)
                            picked.forEachIndexed { k, li ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !busy) { picked.removeAt(k); Sfx.crunch() }.padding(8.dp)
                                ) {
                                    T("${k + 1}", 14, p.sub, mono = true, mod = Modifier.width(24.dp))
                                    Text(
                                        buildAnnotatedString { hlTo(st.lines[li], p) },
                                        fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = p.ink
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp)) {
                            T("Kết quả mong muốn", 12, p.sub, FontWeight.Bold)
                            st.out.forEach { T(it, 15, mono = true) }
                        }
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            shuf.forEach { li ->
                                if (li !in picked) {
                                    Box(
                                        Modifier.glass(14.dp).clickable(enabled = !busy) { picked.add(li); Sfx.crunch(); buzz(false) }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) { T(st.lines[li].trimEnd(), 15, mono = true) }
                                }
                            }
                        }
                    }
                    else -> {
                        val fill = if (st is Ex && !st.pred && pick >= 0) st.opts[pick] else null
                        val code = if (st is Learn) st.code else (st as Ex).code
                        CodeCard(lv.file) {
                            Text(codeText(code, fill, p), fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = p.ink, lineHeight = 24.sp)
                        }
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
                            FlowRow(
                                Modifier.fillMaxWidth().padding(top = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                order.forEach { i ->
                                    val sel = pick == i
                                    Box(
                                        Modifier.then(if (sel) Modifier.clip(RoundedCornerShape(14.dp)).background(p.ink) else Modifier.glass(14.dp))
                                            .clickable(enabled = !busy) { pick = i; Sfx.crunch(); buzz(false) }
                                            .padding(horizontal = 18.dp, vertical = 12.dp)
                                    ) {
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
                            face = F.HAPPY; bump++; Sfx.oink(); buzz(false)
                            fb = Triple(true, "Chuẩn rồi!", st.why)
                        } else {
                            missed()
                            err = true; face = F.SAD; bump++; Sfx.whine(); buzz(true); delay(600)
                            fb = Triple(false, "Chưa đúng. Đáp án: " + st.opts[st.ans].replace("\n", " ↵ "), note(st.why))
                        }
                    }
                }
                is Bug -> Btn("Kiểm tra", pick >= 0 && !busy, true, Modifier.fillMaxWidth()) {
                    busy = true
                    val ok = pick == st.bad
                    scope.launch {
                        if (ok) {
                            face = F.HAPPY; bump++; Sfx.oink(); buzz(false)
                        } else {
                            missed()
                            face = F.SAD; bump++; Sfx.whine(); buzz(true)
                        }
                        delay(400)
                        fb = Triple(ok, if (ok) "Bắt được bug rồi!" else "Chưa đúng, bug nằm ở dòng ${st.bad + 1}", if (ok) st.why else note(st.why))
                    }
                }
                is Order -> Btn("Kiểm tra", picked.size == st.lines.size && !busy, true, Modifier.fillMaxWidth()) {
                    busy = true
                    val ok = picked.toList() == st.lines.indices.toList()
                    scope.launch {
                        if (ok) {
                            face = F.HAPPY; bump++; Sfx.oink(); buzz(false)
                        } else {
                            missed()
                            face = F.SAD; bump++; Sfx.whine(); buzz(true)
                        }
                        delay(350)
                        fb = Triple(ok, if (ok) "Chuẩn rồi!" else "Chưa đúng thứ tự", if (ok) st.why else note(st.why))
                    }
                }
            }
        }
        val f = fb
        if (f != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(if (p.dark) Color(0xFF202026) else Color.White).padding(20.dp)
            ) {
                T(f.second, 20, if (f.first) p.str else RED, FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                T(f.third, 15)
                Spacer(Modifier.height(14.dp))
                Btn("Tiếp tục", true, true, Modifier.fillMaxWidth()) { next() }
            }
        }
    }
}
