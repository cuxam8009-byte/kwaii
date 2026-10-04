package com.pigprolem.utit

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

private val WAVE = listOf(0, 16, 32, 16)

fun greeting(game: Game): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        game.stars.all { it > 0 } -> "Cậu học hết lộ trình rồi! Ôn lại cho nhớ lâu nhé."
        game.doneTodayCount() >= game.goal -> "Hôm nay đủ bắp rồi, giỏi quá! Học thêm cho vui nhé."
        game.lastDay >= 0 && game.streakNow() == 0 -> "Lâu quá không gặp! Học một bài cho đỡ đói nhé."
        game.stars.all { it == 0 } -> "Chào cậu! Mình cùng học Python từ bài đầu tiên nhé."
        h < 11 -> "Chào buổi sáng! Sẵn sàng học chưa?"
        h < 14 -> "Trưa rồi, học một bài trước khi ăn nhé."
        h < 18 -> "Chào buổi chiều! Hôm nay học tiếp nào."
        else -> "Buổi tối vui vẻ! Thêm một bài nữa là đủ mục tiêu."
    }
}

@Composable
fun Home(game: Game, onOpen: (Int) -> Unit, onReview: () -> Unit, onSettings: () -> Unit) {
    val p = LocalP.current
    val stars = game.stars
    var face by remember { mutableStateOf(F.IDLE) }
    var bump by remember { mutableStateOf(0) }
    var idx by remember { mutableStateOf(0) }
    var say by remember { mutableStateOf(greeting(game)) }
    val order = listOf(F.HAPPY, F.LOVE, F.WOW, F.COOL, F.THINK, F.SLEEPY, F.SAD, F.EAT)
    val lines = mapOf(
        F.HAPPY to "Hihi, vui quá!", F.LOVE to "Yêu cậu nhiều!", F.WOW to "Ủa gì dợ?!", F.COOL to "Tớ ngầu chưa?",
        F.THINK to "Hmm, để tớ nghĩ...", F.SLEEPY to "Zzz... cho tớ ngủ xíu", F.SAD to "Huhu...", F.EAT to "Nhóp nhép, ngon!"
    )
    val next = game.nextLevel()
    val doneN = stars.count { it > 0 }
    val today = game.doneTodayCount()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Pigprolem Utit", 26, w = FontWeight.Bold)
                T("Học Python mỗi ngày vài phút", 13, p.sub)
            }
            Pill(Color(0xFFFF8A3D), "${game.streakNow()}")
            Spacer(Modifier.width(8.dp))
            Pill(Color(0xFFFFC46B), "${game.corn}")
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).glass(20.dp).clickable { onSettings() }, contentAlignment = Alignment.Center) {
                T("•••", 14, w = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.pointerInput(Unit) {
                detectTapGestures {
                    val f = order[idx++ % order.size]
                    face = f; bump++; say = lines[f]!!
                    when (f) {
                        F.SAD -> Sfx.whine()
                        F.EAT -> Sfx.crunch()
                        F.SLEEPY -> {}
                        else -> Sfx.oink()
                    }
                }
            }) { Pig(face, 190.dp, bump) }
        }
        Box(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp)) {
            T(say, 15, align = TextAlign.Center, mod = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        if (next >= 0) {
            Btn("Học tiếp: ${LEVELS[next].title}", true, true, Modifier.fillMaxWidth()) { onOpen(next) }
            if (doneN >= 2) {
                Spacer(Modifier.height(10.dp))
                Btn("Ôn tập nhanh · 8 câu", true, false, Modifier.fillMaxWidth()) { onReview() }
            }
        } else {
            Btn("Ôn tập nhanh · 8 câu", true, true, Modifier.fillMaxWidth()) { onReview() }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Mục tiêu hôm nay", 14, w = FontWeight.Bold, mod = Modifier.weight(1f))
                T("${minOf(today, game.goal)}/${game.goal} bài", 14, p.sub)
            }
            Spacer(Modifier.height(8.dp))
            Bar(today.toFloat() / game.goal)
            Spacer(Modifier.height(8.dp))
            T("Đã học $doneN/${LEVELS.size} bài trong lộ trình", 12, p.sub)
        }
        Spacer(Modifier.height(22.dp))
        T("Lộ trình", 20, w = FontWeight.Bold)
        UNITS.forEachIndexed { ui, u ->
            val accent = UNIT_COLORS[ui % UNIT_COLORS.size]
            val start = UNITS.take(ui).sumOf { it.levels.size }
            val doneU = u.levels.indices.count { stars[start + it] > 0 }
            Row(
                Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = if (p.dark) 0.22f else 0.35f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    T("CHƯƠNG ${ui + 1}", 11, p.sub, FontWeight.Bold)
                    T(u.title, 18, w = FontWeight.Bold)
                    T(u.sub, 13, p.sub)
                }
                T("$doneU/${u.levels.size}", 15, w = FontWeight.Bold)
            }
            u.levels.forEachIndexed { li, lv ->
                val i = start + li
                val open = game.isOpen(i)
                val cur = i == next
                val done = stars[i] > 0
                Row(
                    Modifier.padding(top = 14.dp, start = WAVE[li % WAVE.size].dp).alpha(if (open) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp)
                            .then(if (done) Modifier.clip(CircleShape).background(accent) else Modifier.glass(26.dp))
                            .then(if (cur) Modifier.border(3.dp, accent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        T(if (!open) "–" else if (done) "✓" else "${i + 1}", 16, if (done) Color(0xFF111114) else p.ink, FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier.weight(1f).glass(22.dp).clickable {
                            if (open) {
                                onOpen(i)
                            } else {
                                face = F.SAD; bump++; Sfx.whine()
                                say = "Hoàn thành bài trước để mở bài này nhé!"
                            }
                        }.padding(16.dp)
                    ) {
                        T(lv.kind.uppercase(), 11, p.sub, FontWeight.Bold)
                        T(lv.title, 18, w = FontWeight.Bold)
                        Row {
                            T(lv.sub, 13, p.sub, mod = Modifier.weight(1f))
                            T("★".repeat(stars[i]) + "☆".repeat(3 - stars[i]), 14)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

class Outcome(val stars: Int, val gain: Int, val correct: Int, val total: Int, val streak: Int, val next: Int)

@Composable
fun ResultScreen(r: Outcome, onNext: () -> Unit, onHome: () -> Unit) {
    val p = LocalP.current
    var bump by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        bump = 1
        Sfx.cheer()
        delay(500)
        Sfx.oink()
        for (k in 1..r.stars) {
            delay(260)
            shown = k
            Sfx.pop()
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Pig(if (r.stars == 3) F.LOVE else F.HAPPY, 220.dp, bump)
        Spacer(Modifier.height(8.dp))
        T(if (r.stars == 3) "Hoàn hảo!" else "Hoàn thành!", 30, w = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row {
            for (k in 0 until 3) {
                val sc by animateFloatAsState(if (k < shown) 1f else 0.6f, spring(dampingRatio = 0.4f), label = "star")
                T(if (k < shown) "★" else "☆", 40, mod = Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
            }
        }
        Spacer(Modifier.height(16.dp))
        T("+${r.gain} bắp", 18, p.sub)
        T("Đúng ${r.correct}/${r.total} câu", 15, p.sub)
        T("Chuỗi ${r.streak} ngày", 15, p.sub)
        Spacer(Modifier.weight(1f))
        if (r.next >= 0) {
            Btn("Bài tiếp theo", true, true, Modifier.fillMaxWidth()) { onNext() }
            Spacer(Modifier.height(10.dp))
            Btn("Về trang chính", true, false, Modifier.fillMaxWidth()) { onHome() }
        } else {
            Btn("Về trang chính", true, true, Modifier.fillMaxWidth()) { onHome() }
        }
    }
}

@Composable
fun Scrim(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        content = content
    )
}

@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalP.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(if (p.dark) Color(0xFF202026) else Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(20.dp),
        content = content
    )
}

@Composable
fun SettingsSheet(game: Game, onClose: () -> Unit) {
    val p = LocalP.current
    var sure by remember { mutableStateOf(false) }
    Scrim(onClose) {
        Sheet(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) {
            T("Cài đặt", 22, w = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Toggle("Âm thanh", game.sound) { game.updateSound(it) }
            Toggle("Rung khi chạm", game.haptic) { game.updateHaptic(it) }
            Spacer(Modifier.height(8.dp))
            T("Giao diện", 13, p.sub, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Seg(listOf("Theo máy", "Sáng", "Tối"), game.theme) { game.updateTheme(it) }
            Spacer(Modifier.height(14.dp))
            T("Mục tiêu mỗi ngày", 13, p.sub, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Seg(listOf("1 bài", "2 bài", "3 bài"), game.goal - 1) { game.updateGoal(it + 1) }
            Spacer(Modifier.height(18.dp))
            Btn(if (sure) "Bấm lần nữa để xóa hết tiến trình" else "Đặt lại tiến trình", true, false, Modifier.fillMaxWidth()) {
                if (sure) {
                    game.reset()
                    sure = false
                    onClose()
                } else {
                    sure = true
                }
            }
            Spacer(Modifier.height(8.dp))
            T("Phiên bản 1.0", 12, p.sub, align = TextAlign.Center, mod = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ConfirmExit(onStay: () -> Unit, onLeave: () -> Unit) {
    Scrim(onStay) {
        Sheet(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) {
            T("Thoát bài học?", 22, w = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            T("Tiến độ của bài này sẽ không được lưu.", 15)
            Spacer(Modifier.height(16.dp))
            Btn("Ở lại học tiếp", true, true, Modifier.fillMaxWidth()) { onStay() }
            Spacer(Modifier.height(10.dp))
            Btn("Thoát bài", true, false, Modifier.fillMaxWidth()) { onLeave() }
        }
    }
}
