package com.tdaude.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * TDaude 0.4 - terminal thật (PTY + emulator của Termux), nhiều phiên, giao diện hiện đại:
 * thanh trên + drawer phiên (vuốt từ mép trái), phím phụ bo góc, 5 bộ màu, chạy nền bằng service.
 */
class MainActivity : Activity(), Sessions.Listener {

    /** Một phím của hàng phím phụ. mod: 1 = CTRL, 2 = ALT. */
    private class Key(
        val label: String,
        val code: Int = 0,
        val text: String? = null,
        val popup: String? = null,
        val repeat: Boolean = false,
        val mod: Int = 0
    )

    companion object {
        private const val TAG = "TDaude"
        private const val REQ_STORAGE = 21
    }

    private lateinit var root: DrawerRoot
    private lateinit var topBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var divTop: View
    private lateinit var divKeys: View
    private lateinit var terminalView: TerminalView
    private lateinit var keysBox: LinearLayout
    private lateinit var scrim: View
    private lateinit var drawer: LinearLayout
    private lateinit var drawerTitle: TextView
    private lateinit var drawerSubtitle: TextView
    private lateinit var drawerHeader: TextView
    private lateinit var sessionListBox: LinearLayout
    private lateinit var ctrlView: TextView
    private lateinit var altView: TextView

    private val keyViews = ArrayList<TextView>()
    private val topIcons = ArrayList<ImageView>()
    private val chips = ArrayList<TextView>()

    private var pal: Palette = Schemes.all[0].palette()
    private var schemeIndex = 0

    private var ctrlState = 0 // 0 = tắt, 1 = chỉ phím kế tiếp, 2 = khoá
    private var altState = 0
    private var fontPx = 0

    private var drawerWidth = 0
    private var drawerProgress = 0f
    private var drawerOpen = false
    private var drawerAnim: ValueAnimator? = null

    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("tdaude", Context.MODE_PRIVATE) }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setColor(fill)
        if (stroke != null) d.setStroke(dp(1), stroke)
        return d
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ vòng đời

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        schemeIndex = prefs.getInt("scheme", 0).coerceIn(0, Schemes.all.size - 1)
        applySchemeColors()
        buildUi()
        applyKeepScreen()

        Sessions.listener = this
        if (Sessions.list.isEmpty()) Sessions.create(this)
        TerminalService.start(this)
        if (prefs.getBoolean("wake_lock", false)) Sessions.setWakeLock(this, true)

        showSession(Sessions.current)
    }

    override fun onResume() {
        super.onResume()
        Sessions.listener = this
        refreshChrome()
        terminalView.invalidate()
        terminalView.requestFocus()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerOpen) {
            closeDrawer()
            return
        }
        // Giữ shell sống ở nền thay vì đóng app.
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        // Không giết phiên ở đây: TerminalService giữ chúng. Chỉ ngắt liên kết với giao diện.
        if (Sessions.listener === this) Sessions.listener = null
        drawerAnim?.cancel()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ dựng giao diện

    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        drawerWidth = Math.min(dp(290), (resources.displayMetrics.widthPixels * 0.86f).toInt())

        root = DrawerRoot(this)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL

        topBar = buildTopBar()
        content.addView(topBar, LinearLayout.LayoutParams(match, dp(46)))
        divTop = View(this)
        content.addView(divTop, LinearLayout.LayoutParams(match, dp(1)))

        terminalView = TerminalView(this, null)
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        terminalView.setDefaultFocusHighlightEnabled(false)
        terminalView.setTerminalViewClient(viewClient)

        var defaultPx = Math.round(14f * resources.displayMetrics.density)
        if (defaultPx % 2 == 1) defaultPx--
        fontPx = prefs.getInt("font_px", defaultPx)
        terminalView.setTextSize(fontPx)

        val tlp = LinearLayout.LayoutParams(match, 0, 1f)
        tlp.leftMargin = dp(4)
        tlp.rightMargin = dp(4)
        content.addView(terminalView, tlp)

        divKeys = View(this)
        content.addView(divKeys, LinearLayout.LayoutParams(match, dp(1)))
        keysBox = buildKeys()
        content.addView(keysBox, LinearLayout.LayoutParams(match, wrap))
        setKeysVisible(prefs.getBoolean("keys_visible", true))

        root.addView(content, FrameLayout.LayoutParams(match, match))

        scrim = View(this)
        scrim.setBackgroundColor(Color.argb(160, 0, 0, 0))
        scrim.setOnClickListener { closeDrawer() }
        root.addView(scrim, FrameLayout.LayoutParams(match, match))

        drawer = buildDrawer()
        root.addView(drawer, FrameLayout.LayoutParams(drawerWidth, match, Gravity.START))
        drawer.elevation = dp(16).toFloat()

        setContentView(root)
        setDrawerProgress(0f)
        applyPalette()
    }

    private fun buildTopBar(): LinearLayout {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        bar.addView(
            iconButton(R.drawable.ic_menu, R.string.cd_menu) { openDrawer() },
            LinearLayout.LayoutParams(dp(46), dp(46))
        )

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.gravity = Gravity.CENTER_VERTICAL

        titleView = TextView(this)
        titleView.textSize = 15f
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.setSingleLine()
        titleView.ellipsize = TextUtils.TruncateAt.END

        subtitleView = TextView(this)
        subtitleView.textSize = 11f
        subtitleView.setSingleLine()
        subtitleView.ellipsize = TextUtils.TruncateAt.END

        texts.addView(titleView)
        texts.addView(subtitleView)
        val tlp = LinearLayout.LayoutParams(0, wrap, 1f)
        tlp.leftMargin = dp(2)
        bar.addView(texts, tlp)

        bar.addView(
            iconButton(R.drawable.ic_add, R.string.cd_new) { newSession() },
            LinearLayout.LayoutParams(dp(46), dp(46))
        )
        bar.addView(
            iconButton(R.drawable.ic_more, R.string.cd_more) { showMenu() },
            LinearLayout.LayoutParams(dp(46), dp(46))
        )
        return bar
    }

    private fun iconButton(res: Int, desc: Int, onClick: () -> Unit): ImageView {
        val b = ImageView(this)
        b.setImageResource(res)
        b.scaleType = ImageView.ScaleType.CENTER_INSIDE
        b.setPadding(dp(12), dp(12), dp(12), dp(12))
        b.contentDescription = getString(desc)
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)
        b.setBackgroundResource(out.resourceId)
        b.setOnClickListener { onClick() }
        topIcons.add(b)
        return b
    }

    private fun buildDrawer(): LinearLayout {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val d = LinearLayout(this)
        d.orientation = LinearLayout.VERTICAL
        d.isClickable = true // chặn chạm xuyên xuống terminal

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(18), dp(22), dp(18), dp(10))
        drawerTitle = TextView(this)
        drawerTitle.text = getString(R.string.app_name)
        drawerTitle.textSize = 24f
        drawerTitle.typeface = Typeface.DEFAULT_BOLD
        drawerSubtitle = TextView(this)
        drawerSubtitle.text = getString(R.string.drawer_subtitle) + "  -  v" + Shell.VERSION
        drawerSubtitle.textSize = 12f
        header.addView(drawerTitle)
        header.addView(drawerSubtitle)
        d.addView(header, LinearLayout.LayoutParams(match, wrap))

        drawerHeader = TextView(this)
        drawerHeader.text = getString(R.string.sessions_header)
        drawerHeader.textSize = 11f
        drawerHeader.typeface = Typeface.DEFAULT_BOLD
        drawerHeader.letterSpacing = 0.1f
        drawerHeader.setPadding(dp(18), dp(8), dp(18), dp(6))
        d.addView(drawerHeader, LinearLayout.LayoutParams(match, wrap))

        val scroll = ScrollView(this)
        sessionListBox = LinearLayout(this)
        sessionListBox.orientation = LinearLayout.VERTICAL
        sessionListBox.setPadding(dp(10), 0, dp(10), dp(10))
        scroll.addView(sessionListBox, ViewGroup.LayoutParams(match, wrap))
        d.addView(scroll, LinearLayout.LayoutParams(match, 0, 1f))

        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(dp(8), dp(8), dp(8), dp(12))
        val chipItems = listOf(
            Pair(R.string.btn_new, { newSession(); closeDrawer() }),
            Pair(R.string.btn_keyboard, { closeDrawer(); toggleKeyboard() }),
            Pair(R.string.btn_settings, { closeDrawer(); showMenu() })
        )
        for (item in chipItems) {
            val lp = LinearLayout.LayoutParams(0, dp(42), 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            chipRow.addView(chip(item.first, item.second), lp)
        }
        d.addView(chipRow, LinearLayout.LayoutParams(match, wrap))
        return d
    }

    private fun chip(label: Int, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.setText(label)
        t.textSize = 12f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setSingleLine()
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(4), 0, dp(4), 0)
        t.setOnClickListener { onClick() }
        chips.add(t)
        return t
    }

    private fun buildSessionRow(index: Int, s: TerminalSession): View {
        val selected = index == Sessions.current
        val running = s.isRunning

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(8), dp(4), dp(8))
        val base = rounded(
            if (selected) pal.accentSoft else Color.TRANSPARENT,
            10,
            if (selected) pal.accent else null
        )
        row.background = RippleDrawable(ColorStateList.valueOf(pal.keyPressed), base, null)

        val dotBg = GradientDrawable()
        dotBg.shape = GradientDrawable.OVAL
        dotBg.setColor(if (running) pal.ok else pal.dim)
        val dot = View(this)
        dot.background = dotBg
        row.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val name = TextView(this)
        name.text = s.mSessionName ?: "shell"
        name.textSize = 14f
        name.typeface = Typeface.DEFAULT_BOLD
        name.setTextColor(pal.text)
        name.setSingleLine()
        name.ellipsize = TextUtils.TruncateAt.END
        val sub = TextView(this)
        val sessionTitle = s.title
        sub.text = if (!running) {
            getString(R.string.session_exited, s.exitStatus)
        } else if (!sessionTitle.isNullOrEmpty()) {
            sessionTitle
        } else {
            "sh"
        }
        sub.textSize = 11f
        sub.setTextColor(pal.dim)
        sub.setSingleLine()
        sub.ellipsize = TextUtils.TruncateAt.END
        texts.addView(name)
        texts.addView(sub)
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(12)
        row.addView(texts, tlp)

        val close = ImageView(this)
        close.setImageResource(R.drawable.ic_close)
        close.setColorFilter(pal.dim)
        close.setPadding(dp(10), dp(10), dp(10), dp(10))
        close.contentDescription = getString(R.string.cd_close)
        close.setOnClickListener { closeSession(index) }
        row.addView(close, LinearLayout.LayoutParams(dp(40), dp(40)))

        row.setOnClickListener {
            showSession(index)
            closeDrawer()
        }
        row.setOnLongClickListener {
            renameSession(index)
            true
        }
        return row
    }

    private fun refreshDrawer() {
        if (!::sessionListBox.isInitialized) return
        sessionListBox.removeAllViews()
        for ((i, s) in Sessions.list.withIndex()) {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            sessionListBox.addView(buildSessionRow(i, s), lp)
        }
    }

    // ------------------------------------------------------------------ màu / giao diện

    private fun applySchemeColors() {
        val sc = Schemes.all[schemeIndex]
        TerminalColors.COLOR_SCHEME.updateWith(sc.toProperties())
        for (s in Sessions.list) s.emulator?.mColors?.reset()
        pal = sc.palette()
    }

    private fun setScheme(index: Int) {
        schemeIndex = index.coerceIn(0, Schemes.all.size - 1)
        prefs.edit().putInt("scheme", schemeIndex).apply()
        applySchemeColors()
        applyPalette()
        terminalView.invalidate()
    }

    private fun applyPalette() {
        window.statusBarColor = pal.bar
        window.navigationBarColor = pal.bar
        root.setBackgroundColor(pal.bg)

        topBar.setBackgroundColor(pal.bar)
        titleView.setTextColor(pal.text)
        subtitleView.setTextColor(pal.dim)
        for (icon in topIcons) icon.setColorFilter(pal.text)
        divTop.setBackgroundColor(pal.stroke)
        divKeys.setBackgroundColor(pal.stroke)

        keysBox.setBackgroundColor(pal.bar)
        for (k in keyViews) styleKey(k)
        applyModStyle()

        drawer.setBackgroundColor(pal.bar)
        drawerTitle.setTextColor(pal.accent)
        drawerSubtitle.setTextColor(pal.dim)
        drawerHeader.setTextColor(pal.dim)
        for (c in chips) {
            c.setTextColor(pal.text)
            c.background = RippleDrawable(
                ColorStateList.valueOf(pal.keyPressed),
                rounded(pal.key, 10),
                null
            )
        }
        refreshDrawer()
    }

    private fun applyKeepScreen() {
        if (prefs.getBoolean("keep_screen", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setKeysVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        keysBox.visibility = v
        divKeys.visibility = v
        prefs.edit().putBoolean("keys_visible", visible).apply()
    }

    // ------------------------------------------------------------------ drawer

    private fun setDrawerProgress(p: Float) {
        drawerProgress = p
        drawer.translationX = -drawerWidth * (1f - p)
        scrim.alpha = p
        scrim.visibility = if (p > 0f) View.VISIBLE else View.GONE
        drawer.visibility = if (p > 0f) View.VISIBLE else View.INVISIBLE
    }

    private fun animateDrawer(open: Boolean) {
        drawerAnim?.cancel()
        drawerOpen = open
        if (open) refreshDrawer()
        val a = ValueAnimator.ofFloat(drawerProgress, if (open) 1f else 0f)
        a.duration = 180
        a.addUpdateListener { setDrawerProgress(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!open) terminalView.requestFocus()
            }
        })
        drawerAnim = a
        a.start()
    }

    private fun openDrawer() = animateDrawer(true)
    private fun closeDrawer() = animateDrawer(false)

    /** Gốc giao diện: nhận cử chỉ vuốt từ mép trái để mở drawer, vuốt sang trái để đóng. */
    private inner class DrawerRoot(ctx: Context) : FrameLayout(ctx) {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var startProgress = 0f
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private val edge = dp(24)

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        val openGesture = !drawerOpen && downX < edge && dx > 0
                        val closeGesture = drawerOpen && dx < 0
                        if (openGesture || closeGesture) {
                            dragging = true
                            startProgress = drawerProgress
                            drawerAnim?.cancel()
                            return true
                        }
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (!dragging) return super.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val p = startProgress + (ev.x - downX) / drawerWidth
                    setDrawerProgress(p.coerceIn(0f, 1f))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    if (drawerProgress > 0.4f) openDrawer() else closeDrawer()
                }
            }
            return true
        }
    }

    // ------------------------------------------------------------------ phiên

    private fun currentSession(): TerminalSession? = Sessions.list.getOrNull(Sessions.current)

    private fun showSession(index: Int) {
        if (Sessions.list.isEmpty()) return
        val i = index.coerceIn(0, Sessions.list.size - 1)
        Sessions.current = i
        terminalView.attachSession(Sessions.list[i])
        terminalView.invalidate()
        terminalView.requestFocus()
        refreshChrome()
    }

    private fun newSession() {
        Sessions.create(this)
        showSession(Sessions.current)
    }

    private fun closeSession(index: Int) {
        Sessions.remove(index)
        if (Sessions.list.isEmpty()) Sessions.create(this)
        showSession(Sessions.current)
    }

    private fun renameSession(index: Int) {
        val s = Sessions.list.getOrNull(index) ?: return
        val input = EditText(this)
        input.setText(s.mSessionName ?: "")
        input.setSingleLine()
        input.setSelectAllOnFocus(true)
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.rename_title)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    s.mSessionName = n
                    refreshChrome()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshChrome() {
        if (!::terminalView.isInitialized) return
        val s = currentSession()
        titleView.text = s?.mSessionName ?: getString(R.string.app_name)
        val counter = getString(R.string.session_counter, Sessions.current + 1, Sessions.list.size)
        val t = s?.title
        subtitleView.text = if (!t.isNullOrEmpty()) counter + "  -  " + t else counter
        if (drawerOpen || drawerProgress > 0f) refreshDrawer()
    }

    // ------------------------------------------------------------------ Sessions.Listener

    override fun onSessionsChanged() {
        refreshChrome()
    }

    override fun onTextChanged(session: TerminalSession) {
        if (session === currentSession()) terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {
        refreshChrome()
    }

    override fun onSessionFinished(session: TerminalSession) {
        refreshChrome()
    }

    override fun onCopyText(text: String?) {
        if (text == null) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TDaude", text))
    }

    override fun onPasteText(session: TerminalSession?) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val t = clip.getItemAt(0).coerceToText(this)
            if (t != null && t.isNotEmpty()) {
                (session ?: currentSession())?.emulator?.paste(t.toString())
            }
        }
    }

    override fun onColorsChanged() {
        terminalView.invalidate()
    }

    override fun onExitRequested() {
        finishAndRemoveTask()
    }

    // ------------------------------------------------------------------ menu / cài đặt

    private fun showMenu() {
        val on = getString(R.string.state_on)
        val off = getString(R.string.state_off)
        val keysOn = keysBox.visibility == View.VISIBLE
        val keepOn = prefs.getBoolean("keep_screen", false)
        val items = arrayOf(
            getString(R.string.menu_theme),
            getString(R.string.menu_font_up),
            getString(R.string.menu_font_down),
            getString(R.string.menu_keys, if (keysOn) on else off),
            getString(R.string.menu_keep_screen, if (keepOn) on else off),
            getString(R.string.menu_wakelock, if (Sessions.hasWakeLock) on else off),
            getString(R.string.menu_storage),
            getString(R.string.menu_about)
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showThemeDialog()
                    1 -> changeFontSize(true)
                    2 -> changeFontSize(false)
                    3 -> setKeysVisible(!keysOn)
                    4 -> {
                        prefs.edit().putBoolean("keep_screen", !keepOn).apply()
                        applyKeepScreen()
                    }
                    5 -> {
                        val enable = !Sessions.hasWakeLock
                        prefs.edit().putBoolean("wake_lock", enable).apply()
                        Sessions.setWakeLock(this, enable)
                    }
                    6 -> requestStorage()
                    7 -> showAbout()
                    else -> {}
                }
            }
            .show()
    }

    private fun showThemeDialog() {
        val names = Array(Schemes.all.size) { Schemes.all[it].name }
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(names, schemeIndex) { dialog, which ->
                setScheme(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_about)
            .setMessage(getString(R.string.about_text, Shell.VERSION))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun requestStorage() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            toast(R.string.storage_granted)
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            toast(if (ok) R.string.storage_granted else R.string.storage_denied)
        }
    }

    private fun showKeyboard() {
        terminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, 0)
    }

    private fun toggleKeyboard() {
        terminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun changeFontSize(increase: Boolean) {
        val step = if (increase) 2 else -2
        fontPx = (fontPx + step).coerceIn(dp(8), dp(30))
        terminalView.setTextSize(fontPx)
        prefs.edit().putInt("font_px", fontPx).apply()
    }

    // ------------------------------------------------------------------ TerminalViewClient

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                changeFontSize(scale > 1f)
                return 1.0f
            }
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent?) {
            showKeyboard()
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = true
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
            // Sau khi shell thoát, nhấn Enter sẽ mở shell mới ngay tại phiên đó.
            if (keyCode == KeyEvent.KEYCODE_ENTER && session != null && !session.isRunning) {
                val idx = Sessions.list.indexOf(session)
                if (idx >= 0) {
                    Sessions.restart(idx, this@MainActivity)
                    showSession(idx)
                }
                return true
            }
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
        override fun onLongPress(event: MotionEvent?): Boolean = false

        override fun readControlKey(): Boolean = readMod(1)
        override fun readAltKey(): Boolean = readMod(2)
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
        override fun onEmulatorSet() {}

        override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag ?: TAG, message ?: "", e)
        }
        override fun logStackTrace(tag: String?, e: Exception?) {
            Log.e(tag ?: TAG, "", e)
        }
    }

    // ------------------------------------------------------------------ hàng phím phụ

    private fun buildKeys(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(3), dp(3), dp(3), dp(3))

        val rows = listOf(
            listOf(
                Key("ESC", code = KeyEvent.KEYCODE_ESCAPE),
                Key("/", text = "/"),
                Key("\u2015", text = "-", popup = "|"),
                Key("HOME", code = KeyEvent.KEYCODE_MOVE_HOME),
                Key("\u2191", code = KeyEvent.KEYCODE_DPAD_UP, repeat = true),
                Key("END", code = KeyEvent.KEYCODE_MOVE_END),
                Key("PGUP", code = KeyEvent.KEYCODE_PAGE_UP, repeat = true)
            ),
            listOf(
                Key("TAB", code = KeyEvent.KEYCODE_TAB),
                Key("CTRL", mod = 1),
                Key("ALT", mod = 2),
                Key("\u2190", code = KeyEvent.KEYCODE_DPAD_LEFT, repeat = true),
                Key("\u2193", code = KeyEvent.KEYCODE_DPAD_DOWN, repeat = true),
                Key("\u2192", code = KeyEvent.KEYCODE_DPAD_RIGHT, repeat = true),
                Key("PGDN", code = KeyEvent.KEYCODE_PAGE_DOWN, repeat = true)
            )
        )

        for (r in rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in r) {
                val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
                lp.setMargins(dp(2), dp(2), dp(2), dp(2))
                row.addView(makeKey(k), lp)
            }
            box.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return box
    }

    private fun makeKey(k: Key): TextView {
        val tv = TextView(this)
        tv.text = k.label
        tv.textSize = 13f
        tv.gravity = Gravity.CENTER
        tv.setOnTouchListener(KeyTouch(k, tv))
        if (k.mod == 1) ctrlView = tv
        if (k.mod == 2) altView = tv
        keyViews.add(tv)
        return tv
    }

    private fun styleKey(tv: TextView) {
        tv.setTextColor(pal.text)
        tv.background = rounded(pal.key, 8)
    }

    private inner class KeyTouch(private val k: Key, private val tv: TextView) : View.OnTouchListener {
        private var downY = 0f
        private var swiped = false
        private var longFired = false

        private val repeater = object : Runnable {
            override fun run() {
                fire(k)
                ui.postDelayed(this, 80)
            }
        }
        private val longPress = Runnable {
            longFired = true
            if (k.mod != 0) setMod(k.mod, 2)
        }

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = ev.rawY
                    swiped = false
                    longFired = false
                    tv.background = rounded(pal.keyPressed, 8)
                    tv.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (k.repeat) {
                        fire(k)
                        ui.postDelayed(repeater, 400)
                    } else if (k.mod != 0) {
                        ui.postDelayed(longPress, 500)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = k.popup
                    if (p != null) {
                        val up = (downY - ev.rawY) > dp(24)
                        if (up != swiped) {
                            swiped = up
                            tv.text = if (up) p else k.label
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(repeater)
                    ui.removeCallbacks(longPress)
                    tv.text = k.label
                    styleKey(tv)
                    if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        val p = k.popup
                        if (k.mod != 0) {
                            if (!longFired) toggleMod(k.mod)
                        } else if (!k.repeat) {
                            if (swiped && p != null) typeText(p) else fire(k)
                        }
                    }
                    applyModStyle()
                }
            }
            return true
        }
    }

    private fun fire(k: Key) {
        val t = k.text
        if (k.code != 0) sendKey(k.code) else if (t != null) typeText(t)
    }

    private fun sendKey(code: Int) {
        val ev = KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, 0)
        terminalView.onKeyDown(code, ev)
    }

    private fun typeText(s: String) {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            terminalView.inputCodePoint(TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, cp, false, false)
            i += Character.charCount(cp)
        }
    }

    // CTRL / ALT: chạm = áp dụng cho phím kế tiếp, giữ = khoá, chạm lại = tắt.
    private fun toggleMod(m: Int) {
        if (m == 1) ctrlState = if (ctrlState == 0) 1 else 0
        else altState = if (altState == 0) 1 else 0
        applyModStyle()
    }

    private fun setMod(m: Int, v: Int) {
        if (m == 1) ctrlState = v else altState = v
        applyModStyle()
    }

    private fun readMod(m: Int): Boolean {
        val st = if (m == 1) ctrlState else altState
        if (st == 1) {
            if (m == 1) ctrlState = 0 else altState = 0
            ui.post { applyModStyle() }
        }
        return st != 0
    }

    private fun styleMod(tv: TextView, state: Int) {
        when (state) {
            0 -> styleKey(tv)
            1 -> {
                tv.setTextColor(pal.accent)
                tv.background = rounded(pal.accentSoft, 8, pal.accent)
            }
            else -> {
                tv.setTextColor(pal.bg)
                tv.background = rounded(pal.accent, 8)
            }
        }
    }

    private fun applyModStyle() {
        styleMod(ctrlView, ctrlState)
        styleMod(altView, altState)
    }
}
