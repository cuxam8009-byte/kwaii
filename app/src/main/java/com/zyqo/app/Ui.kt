package com.zyqo.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class Actions(val pickZip: () -> Unit, val saveZip: () -> Unit)

@Composable
fun App(vm: ChatVm) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importZip(uri)
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportZip(uri)
    }
    val actions = remember {
        Actions(
            pickZip = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            saveZip = { saver.launch(vm.exportName()) }
        )
    }
    BackHandler(enabled = vm.hasKey && (vm.menuOpen || vm.screen != Screen.CHAT)) {
        if (vm.menuOpen) vm.menuOpen = false else vm.screen = Screen.CHAT
    }
    Box(Modifier.fillMaxSize()) {
        if (!vm.hasKey) KeyScreen(vm) else MainScreen(vm, actions)
        NoticeBar(vm)
    }
}

@Composable
fun NoticeBar(vm: ChatVm) {
    val n = vm.notice
    LaunchedEffect(n) {
        if (n != null) {
            delay(2600)
            if (vm.notice == n) vm.notice = null
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = n != null, enter = fadeIn() + scaleIn(initialScale = .9f), exit = fadeOut()) {
            Text(
                n ?: "",
                color = Z.bg,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(50)).background(Z.ink)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun Mark(box: Dp) {
    Box(Modifier.size(box).clip(RoundedCornerShape(box * 0.28f)).background(Z.ink), contentAlignment = Alignment.Center) {
        Text("Z", color = Z.bg, fontSize = (box.value * 0.5f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun Dots(color: Color = Z.ink) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val r = s / 5.2f
        listOf(0.28f, 0.72f).forEach { x ->
            listOf(0.28f, 0.72f).forEach { y -> drawCircle(color, r, Offset(s * x, s * y)) }
        }
    }
}

@Composable
fun Chip(text: String, onClick: (() -> Unit)? = null) {
    var m = Modifier.clip(RoundedCornerShape(50)).background(Z.card2)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Text(
        text, color = Z.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = m.padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
fun Title(title: String, sub: String) {
    Column(Modifier.padding(top = 14.dp, bottom = 14.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = Z.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SolidButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Z.ink, contentColor = Z.bg, disabledContainerColor = Z.card2, disabledContentColor = Z.sub
        ),
        modifier = modifier.height(50.dp)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun SoftButton(text: String, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Z.card2, contentColor = if (danger) Z.danger else Z.ink),
        modifier = modifier.height(50.dp)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Field(value: String, hint: String, modifier: Modifier = Modifier, secret: Boolean = false, onChange: (String) -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Z.card2).padding(14.dp)) {
        if (value.isEmpty()) Text(hint, color = Z.sub, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = Z.ink, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(Z.ink),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun KeyInput(vm: ChatVm, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    LaunchedEffect(vm.keyBusy) { if (!vm.keyBusy && vm.keyError == null) text = "" }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Z.card2).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; vm.keyError = null; vm.askProvider = false },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TextStyle(color = Z.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Z.ink),
                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Khóa API", color = Z.sub, fontSize = 16.sp)
                        inner()
                    }
                }
            )
            TextButton(onClick = { clip.getText()?.text?.let { text = it.trim(); vm.keyError = null } }) {
                Text("Dán", color = Z.ink, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            vm.keyError ?: "",
            color = Z.danger,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(top = 10.dp)
        )
        if (vm.askProvider) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Provider.entries.forEach { p -> Chip(p.label) { vm.addKeys(text, p) } }
            }
        }
        SolidButton(
            if (vm.keyBusy) "Đang kiểm tra…" else "Thêm khóa",
            enabled = text.isNotBlank() && !vm.keyBusy,
            modifier = Modifier.fillMaxWidth()
        ) { vm.addKeys(text, null) }
    }
}

@Composable
fun KeyScreen(vm: ChatVm) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mark(72.dp)
        Text("Zyqo", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Dán khóa API là dùng được ngay.",
            color = Z.sub,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        )
        KeyInput(vm)
        Text(
            "Tự nhận diện Anthropic, OpenAI, Gemini (AQ. và AIza), Groq và OpenRouter. Dán nhiều khóa để tự chuyển khi một khóa lỗi hoặc hết hạn mức. Khóa được mã hóa trên máy, chỉ gửi thẳng tới nhà cung cấp.",
            color = Z.sub,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}

@Composable
fun MainScreen(vm: ChatVm, a: Actions) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = vm.screen, label = "screen") { s ->
            when (s) {
                Screen.CHAT -> ChatScreen(vm, a)
                Screen.PROJECT -> ProjectScreen(vm, a)
                Screen.SKILLS -> SkillsScreen(vm)
                Screen.TOOLS -> ToolsScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
        if (vm.screen != Screen.CHAT) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)) { DockPill(vm) }
        }
        MenuOverlay(vm)
    }
}

@Composable
fun DockPill(vm: ChatVm) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Z.card2).border(1.dp, Z.line, RoundedCornerShape(50))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.toggleMenu() }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dots()
        Spacer(Modifier.size(10.dp))
        Text(vm.screen.title, fontWeight = FontWeight.Bold)
    }
}

private fun iconOf(s: Screen): ImageVector = when (s) {
    Screen.CHAT -> Icons.Default.Email
    Screen.PROJECT -> Icons.Default.List
    Screen.SKILLS -> Icons.Default.Star
    Screen.TOOLS -> Icons.Default.Build
    Screen.SETTINGS -> Icons.Default.Settings
}

@Composable
fun MenuOverlay(vm: ChatVm) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = vm.menuOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { vm.menuOpen = false }
            )
        }
        Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = vm.menuOpen,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f)
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f))
            ) {
                Column(
                    Modifier.padding(horizontal = 18.dp).padding(bottom = 86.dp).widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(30.dp)).background(Z.card)
                        .border(1.dp, Z.line, RoundedCornerShape(30.dp)).padding(10.dp)
                ) {
                    Screen.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { s ->
                                val cur = vm.screen == s
                                Column(
                                    Modifier.weight(1f).padding(3.dp).clip(RoundedCornerShape(22.dp))
                                        .background(if (cur) Z.ink else Z.card2)
                                        .clickable { vm.go(s) }.padding(vertical = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(iconOf(s), null, tint = if (cur) Z.bg else Z.ink, modifier = Modifier.size(24.dp))
                                    Text(
                                        s.title, color = if (cur) Z.bg else Z.ink, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                            if (row.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        "Cuộc trò chuyện mới", color = Z.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(3.dp).clip(RoundedCornerShape(22.dp))
                            .clickable { vm.newChat() }.padding(vertical = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatVm, a: Actions) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Zyqo", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Chip(vm.skill.name) { vm.go(Screen.SKILLS) }
            val p = vm.project
            if (p != null) {
                Spacer(Modifier.size(6.dp))
                Chip(p.name.removeSuffix(".zip").take(14)) { vm.go(Screen.PROJECT) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.messages.isEmpty()) {
                EmptyChat(vm, a)
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(vm.messages.asReversed(), key = { it.id }) { m -> MsgView(vm, m, a) }
                }
            }
        }
        vm.pending?.let { ApprovalCard(vm, it) }
        InputBar(vm, a)
    }
}

@Composable
fun EmptyChat(vm: ChatVm, a: Actions) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Mark(56.dp)
        Text("Bạn muốn làm gì hôm nay?", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            if (vm.project == null) "Bấm dấu cộng để gửi file zip dự án, hoặc cứ hỏi thẳng." else "Dự án đã sẵn sàng. Chọn gợi ý hoặc tự viết.",
            color = Z.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (vm.project == null) {
            SoftButton("Chọn file zip") { a.pickZip() }
        } else {
            listOf("Phân tích dự án này", "Tìm và sửa lỗi", "Rà soát bảo mật").forEach { q ->
                Box(Modifier.padding(vertical = 4.dp)) { Chip(q) { vm.send(q) } }
            }
        }
    }
}

@Composable
fun InputBar(vm: ChatVm, a: Actions) {
    var text by rememberSaveable { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val ready = text.isNotBlank()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Z.card2)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.toggleMenu() },
            contentAlignment = Alignment.Center
        ) { Dots() }
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(26.dp)).background(Z.card2),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { a.pickZip() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "Đính kèm zip", tint = Z.sub, modifier = Modifier.size(22.dp))
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                maxLines = 6,
                textStyle = TextStyle(color = Z.ink, fontSize = 16.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(Z.ink),
                modifier = Modifier.weight(1f).padding(vertical = 13.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Nhắn cho AI…", color = Z.sub, fontSize = 16.sp)
                        inner()
                    }
                }
            )
            Box(
                Modifier.padding(4.dp).size(40.dp).clip(CircleShape)
                    .background(if (ready || vm.busy) Z.ink else Z.card)
                    .clickable {
                        if (vm.busy) vm.stop() else if (ready) { vm.send(text); text = "" }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (vm.busy) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Z.bg))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Gửi", tint = if (ready) Z.bg else Z.sub, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun MsgView(vm: ChatVm, m: Msg, a: Actions) {
    if (m.role == Role.USER) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                m.text, color = Z.bg, fontSize = 15.5.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(20.dp)).background(Z.ink)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    } else {
        AssistantView(vm, m, a)
    }
}

private class Block(val code: Boolean, val text: String, val lang: String)

private fun splitBlocks(src: String): List<Block> {
    val parts = src.split("```")
    val out = ArrayList<Block>()
    parts.forEachIndexed { i, p ->
        if (i % 2 == 0) {
            if (p.isNotBlank()) out.add(Block(false, p.trim('\n'), ""))
        } else {
            val nl = p.indexOf('\n')
            val first = if (nl >= 0) p.substring(0, nl).trim() else p.trim()
            val hasLang = nl >= 0 && first.length <= 16 && !first.contains(' ')
            val code = if (hasLang) p.substring(nl + 1) else p
            out.add(Block(true, code.trimEnd('\n'), if (hasLang) first else ""))
        }
    }
    return out
}

private fun styled(src: String): AnnotatedString = buildAnnotatedString {
    val lines = src.split("\n")
    lines.forEachIndexed { i, raw ->
        var line = raw
        var head = false
        if (line.startsWith("#")) {
            line = line.trimStart('#').trim()
            head = true
        } else if (line.startsWith("- ") || line.startsWith("* ")) {
            line = "• " + line.substring(2)
        }
        if (head) pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp))
        spans(line)
        if (head) pop()
        if (i < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.spans(s: String) {
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            val e = s.indexOf("**", i + 2)
            if (e > 0) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(s.substring(i + 2, e))
                pop()
                i = e + 2
                continue
            }
        }
        if (s[i] == '`') {
            val e = s.indexOf('`', i + 1)
            if (e > 0) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Z.card2))
                append(s.substring(i + 1, e))
                pop()
                i = e + 1
                continue
            }
        }
        append(s[i])
        i++
    }
}

@Composable
fun AssistantView(vm: ChatVm, m: Msg, a: Actions) {
    val live = vm.busy && vm.messages.lastOrNull()?.id == m.id
    val clean = if (live || hasBlocks(m.text)) stripBlocks(m.text) else m.text
    val writing = if (live) writingPath(m.text) else null
    val blocks = remember(clean) { splitBlocks(clean) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (m.text.isEmpty() && live) Typing()
        blocks.forEach { b ->
            if (b.code) CodeBlock(b.text, b.lang) else Text(
                styled(b.text), fontSize = 15.5.sp, lineHeight = 23.sp,
                color = if (m.error) Z.danger else Z.ink
            )
        }
        if (writing != null) Chip("Đang viết " + writing.ifEmpty { "file" })
        val act = vm.activity
        val steps = if (live) vm.progress.toList() else m.steps
        if (steps.isNotEmpty()) ProgressList(steps)
        if (live && act != null) Chip(act)
        if (m.changes.isNotEmpty()) ChangeCard(vm, m, a)
    }
}

@Composable
fun ProgressList(steps: List<String>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Z.card2).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.take(12).forEach {
            Text(it, color = Z.sub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (steps.size > 12) Text("và ${steps.size - 12} bước nữa", color = Z.sub, fontSize = 12.5.sp)
    }
}

@Composable
fun Typing() {
    val t = rememberInfiniteTransition(label = "typing")
    val al by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    Text("Đang suy nghĩ…", color = Z.sub, modifier = Modifier.alpha(al))
}

@Composable
fun CodeBlock(code: String, lang: String) {
    val clip = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Z.card)
            .border(1.dp, Z.line, RoundedCornerShape(14.dp))
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { "code" }, color = Z.sub, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { clip.setText(AnnotatedString(code)) }) {
                Text("Sao chép", color = Z.sub, fontSize = 12.sp)
            }
        }
        Text(
            code, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp, softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        )
    }
}

@Composable
fun ChangeCard(vm: ChatVm, m: Msg, a: Actions) {
    val latest = vm.messages.lastOrNull { it.changes.isNotEmpty() }?.id == m.id
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Z.card2).padding(14.dp)) {
        Text("${m.changes.size} thay đổi đã áp dụng", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        m.changes.take(8).forEach {
            Text(it, color = Z.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        if (m.changes.size > 8) Text("và ${m.changes.size - 8} thay đổi nữa", color = Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
        if (latest && vm.project != null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SolidButton("Tải zip", modifier = Modifier.weight(1f)) { a.saveZip() }
                if (vm.canUndo) SoftButton("Hoàn tác", modifier = Modifier.weight(1f)) { vm.undo() }
            }
        }
    }
}

@Composable
fun ProjectScreen(vm: ChatVm, a: Actions) {
    val p = vm.project
    val rev = vm.rev
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Title("Dự án", "Gửi file zip, AI đọc và sửa, rồi trả zip mới về cho bạn.")
        if (p == null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Chưa có dự án nào", fontWeight = FontWeight.Bold)
                Text("Hỗ trợ file zip đến 80 MB.", color = Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                SolidButton("Chọn file zip", modifier = Modifier.fillMaxWidth()) { a.pickZip() }
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp)) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val extra = if (p.skipped > 0) " · lược ${p.skipped}" else ""
                Text(
                    "${p.paths.size} file · ${p.files.size} đọc được$extra",
                    color = Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "Đã sửa ${p.changed.size} · đã xóa ${p.deleted.size}",
                    color = if (p.changed.isEmpty() && p.deleted.isEmpty()) Z.sub else Z.ok,
                    fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SolidButton("Tải zip", modifier = Modifier.weight(1f)) { a.saveZip() }
                    if (vm.canUndo) SoftButton("Hoàn tác", modifier = Modifier.weight(1f)) { vm.undo() }
                    SoftButton("Đóng", danger = true, modifier = Modifier.weight(1f)) { vm.closeProject() }
                }
            }
            val list = remember(rev, p) { p.paths.toList() }
            LazyColumn(Modifier.weight(1f).padding(top = 10.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                items(list, key = { it }) { path ->
                    val mod = p.changed.containsKey(path)
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (mod) "●" else " ", color = Z.ok, fontSize = 10.sp, modifier = Modifier.width(16.dp))
                        Text(
                            path, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = if (mod) Z.ink else Z.sub
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkillForm(vm: ChatVm, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Z.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Field(name, "Tên kỹ năng") { name = it }
        Field(hint, "Mô tả ngắn") { hint = it }
        Field(prompt, "Chỉ dẫn: AI cần làm gì, theo quy tắc nào", Modifier.heightIn(min = 120.dp)) { prompt = it }
        SolidButton("Lưu kỹ năng", enabled = name.isNotBlank() && prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            vm.addSkill(name, hint, prompt)
            onDone()
        }
    }
}

@Composable
fun SkillsScreen(vm: ChatVm) {
    var adding by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Title("Kỹ năng", "Kỹ năng quyết định cách AI làm việc. Chọn một rồi quay lại trò chuyện.") }
        item { SoftButton(if (adding) "Đóng" else "Thêm kỹ năng riêng", modifier = Modifier.fillMaxWidth()) { adding = !adding } }
        if (adding) item { SkillForm(vm) { adding = false } }
        items(vm.skills, key = { it.id }) { s ->
            val cur = vm.skillId == s.id
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Z.card)
                    .border(if (cur) 1.5.dp else 1.dp, if (cur) Z.ink else Z.line, RoundedCornerShape(20.dp))
                    .clickable { vm.pickSkill(s.id) }.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (s.custom) TextButton(onClick = { vm.removeSkill(s.id) }) { Text("Xóa", color = Z.danger, fontSize = 13.sp) }
                }
                if (s.hint.isNotBlank()) Text(s.hint, color = Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun KeyCard(vm: ChatVm, e: KeyEntry) {
    var model by remember(e.id, e.model) { mutableStateOf(e.model) }
    val state = vm.keyState(e)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.provider.label, fontWeight = FontWeight.Bold)
                Text(maskKey(e.key), color = Z.sub, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
            }
            Chip(state)
        }
        Text("Model", color = Z.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Z.card2).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = model,
                onValueChange = { model = it },
                singleLine = true,
                textStyle = TextStyle(color = Z.ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Z.ink),
                modifier = Modifier.weight(1f).padding(vertical = 14.dp)
            )
            TextButton(onClick = { vm.updateModel(e.id, model) }) {
                Text(if (model.isBlank()) "Tự động" else "Lưu", color = Z.ink, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton(if (e.enabled) "Tắt" else "Bật", modifier = Modifier.weight(1f)) { vm.toggleKey(e.id) }
            SoftButton("Xóa", danger = true, modifier = Modifier.weight(1f)) { vm.removeKey(e.id) }
        }
    }
}

@Composable
fun DebugPanel() {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp)) {
        Text("Nhật ký gỡ lỗi", fontWeight = FontWeight.Bold)
        Text(
            "Các lượt gọi, chuyển khóa và lỗi. Khóa API đã được ẩn.",
            color = Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        SoftButton(if (open) "Ẩn nhật ký" else "Xem nhật ký", modifier = Modifier.fillMaxWidth()) {
            open = !open
            if (open) text = DebugLog.export()
        }
        if (open) {
            Text(
                text.ifEmpty { "Chưa có gì." }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Z.sub,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            )
            SoftButton("Sao chép gói chẩn đoán", modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                clip.setText(AnnotatedString("Zyqo " + BuildInfo.VERSION + "\n" + DebugLog.export()))
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: ChatVm) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Title("Cài đặt", "Nhiều khóa, tự chuyển khi một khóa lỗi hoặc hết hạn mức.") }
        if (!vm.persistent) {
            item {
                Text(
                    "Máy này không mã hóa được kho khóa, khóa chỉ được giữ trong phiên hiện tại.",
                    color = Z.danger, fontSize = 13.sp
                )
            }
        }
        items(vm.keys, key = { it.id }) { e -> KeyCard(vm, e) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp)) {
                Text("Thêm khóa API", fontWeight = FontWeight.Bold)
                Text(
                    "Dán một hoặc nhiều khóa, cách nhau bằng dấu cách hoặc xuống dòng.",
                    color = Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                KeyInput(vm)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp)) {
                Text("Tìm kiếm web", fontWeight = FontWeight.Bold)
                Text(
                    "AI tự tìm và đọc trang khi cần. Công cụ: " + vm.searchEngine + ". Dán khóa Tavily (tvly-...) ở trên để tìm chính xác hơn.",
                    color = Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                SoftButton(if (vm.webOn) "Đang bật, bấm để tắt" else "Đang tắt, bấm để bật", modifier = Modifier.fillMaxWidth()) { vm.toggleWeb() }
                if (vm.searchEngine == "Tavily") {
                    SoftButton("Xóa khóa Tavily", danger = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { vm.clearSearchKey() }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Đặt lại trạng thái các khóa", modifier = Modifier.fillMaxWidth()) { vm.reviveKeys() }
                SoftButton("Xóa cuộc trò chuyện", modifier = Modifier.fillMaxWidth()) { vm.newChat() }
                SoftButton("Đóng dự án đang mở", danger = true, modifier = Modifier.fillMaxWidth()) { vm.closeProject() }
            }
        }
        item { DebugPanel() }
        item { Text("Zyqo " + BuildInfo.VERSION, color = Z.sub, fontSize = 12.sp) }
    }
}

@Composable
fun ApprovalCard(vm: ChatVm, p: Pending) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(Z.card)
            .border(1.dp, Z.line, RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Text("AI muốn chạy công cụ ngoài", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(p.spec.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Text(
            p.args.take(300), color = Z.sub, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Từ chối", danger = true, modifier = Modifier.weight(1f)) { vm.answer(false, false) }
            SoftButton("Luôn cho phép", modifier = Modifier.weight(1f)) { vm.answer(true, true) }
            SolidButton("Cho phép", modifier = Modifier.weight(1f)) { vm.answer(true, false) }
        }
    }
}

@Composable
fun McpCard(vm: ChatVm, s: McpServer) {
    val state = vm.mcpStatus[s.id] ?: "Chưa kết nối"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.Bold)
                Text(
                    s.url.removePrefix("https://"), color = Z.sub, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Text(state, color = if (state.startsWith("Lỗi")) Z.danger else Z.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Làm mới", modifier = Modifier.weight(1f)) { vm.refreshMcp(s.id) }
            SoftButton(if (s.trusted) "Tin cậy: bật" else "Tin cậy: tắt", modifier = Modifier.weight(1f)) { vm.toggleMcpTrust(s.id) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton(if (s.enabled) "Tắt" else "Bật", modifier = Modifier.weight(1f)) { vm.toggleMcpEnabled(s.id) }
            SoftButton("Xóa", danger = true, modifier = Modifier.weight(1f)) { vm.removeMcp(s.id) }
        }
    }
}

@Composable
fun McpForm(vm: ChatVm) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Thêm máy chủ MCP", fontWeight = FontWeight.Bold)
        Text("Hỗ trợ MCP qua HTTP (Streamable HTTP), chỉ https.", color = Z.sub, fontSize = 13.sp)
        Field(name, "Tên (ví dụ: github)") { name = it }
        Field(url, "https://máy-chủ/mcp") { url = it }
        Field(token, "Token xác thực (nếu có)", secret = true) { token = it }
        SolidButton("Kết nối", enabled = name.isNotBlank() && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            vm.addMcp(name, url, token)
            name = ""
            url = ""
            token = ""
        }
    }
}

@Composable
fun ToolsScreen(vm: ChatVm) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Title("Công cụ", "AI gọi công cụ để đọc, tìm, kiểm tra và kết nối dịch vụ ngoài.") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Z.card).padding(18.dp)) {
                Text("Công cụ trong dự án", fontWeight = FontWeight.Bold)
                Text(
                    "list_files, grep, read_file, validate. Chỉ đọc, an toàn, có giới hạn thời gian. Sau mỗi lần sửa, app tự kiểm tra cú pháp cơ bản và báo AI sửa lại.",
                    color = Z.sub, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                SoftButton("Số vòng tự động tối đa: " + vm.maxRounds, modifier = Modifier.fillMaxWidth()) { vm.cycleRounds() }
            }
        }
        items(vm.mcpServers, key = { it.id }) { s -> McpCard(vm, s) }
        item { McpForm(vm) }
        item {
            Text(
                "Công cụ MCP có thể thay đổi dữ liệu thật. Mặc định app hỏi bạn trước khi chạy công cụ không được đánh dấu chỉ đọc. Chỉ bật Tin cậy với máy chủ bạn kiểm soát.",
                color = Z.sub, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
    }
}
