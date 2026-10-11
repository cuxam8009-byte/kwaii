package com.tdaude.app

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * Nơi giữ tất cả phiên terminal. Nằm ngoài Activity để phiên vẫn sống khi Activity bị đóng
 * (TerminalService giữ tiến trình app chạy nền).
 */
object Sessions {

    /** Giao diện (Activity) đăng ký để nhận sự kiện. Có thể null khi app đang ở nền. */
    interface Listener {
        fun onSessionsChanged()
        fun onTextChanged(session: TerminalSession)
        fun onTitleChanged(session: TerminalSession)
        fun onSessionFinished(session: TerminalSession)
        fun onCopyText(text: String?)
        fun onPasteText(session: TerminalSession?)
        fun onColorsChanged()
        fun onExitRequested()
    }

    private const val TAG = "TDaude"

    val list = ArrayList<TerminalSession>()
    var current = 0

    var listener: Listener? = null

    /** TerminalService đặt callback này để cập nhật thông báo khi số phiên thay đổi. */
    var onCountChanged: (() -> Unit)? = null

    private var counter = 0
    private var wakeLock: PowerManager.WakeLock? = null

    val hasWakeLock: Boolean get() = wakeLock?.isHeld == true

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            listener?.onTextChanged(changedSession)
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            listener?.onTitleChanged(changedSession)
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            listener?.onSessionFinished(finishedSession)
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            listener?.onCopyText(text)
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            listener?.onPasteText(session)
        }

        override fun onBell(session: TerminalSession) {}

        override fun onColorsChanged(session: TerminalSession) {
            listener?.onColorsChanged()
        }

        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

        override fun getTerminalCursorStyle(): Int? = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

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

    private fun notifyChanged() {
        onCountChanged?.invoke()
        listener?.onSessionsChanged()
    }

    /** Tạo phiên mới, đưa vào cuối danh sách và chọn nó. */
    fun create(ctx: Context): TerminalSession {
        val app = ctx.applicationContext
        val s = Shell.newSession(app, client)
        counter++
        s.mSessionName = app.getString(R.string.session_name, counter)
        list.add(s)
        current = list.size - 1
        notifyChanged()
        return s
    }

    /** Thay phiên đã thoát ở vị trí index bằng một phiên mới (giữ nguyên tên). */
    fun restart(index: Int, ctx: Context): TerminalSession? {
        if (index !in list.indices) return null
        val old = list[index]
        val s = Shell.newSession(ctx.applicationContext, client)
        s.mSessionName = old.mSessionName
        old.finishIfRunning()
        list[index] = s
        current = index
        notifyChanged()
        return s
    }

    fun remove(index: Int) {
        if (index !in list.indices) return
        val s = list.removeAt(index)
        s.finishIfRunning()
        if (index < current) current--
        if (current >= list.size) current = list.size - 1
        if (current < 0) current = 0
        notifyChanged()
    }

    fun killAll() {
        for (s in list) s.finishIfRunning()
        list.clear()
        current = 0
        setWakeLock(null, false)
        notifyChanged()
    }

    /** Bật/tắt giữ CPU thức khi tắt màn hình (để lệnh chạy lâu không bị Android ngủ đông). */
    fun setWakeLock(ctx: Context?, enable: Boolean) {
        try {
            if (enable) {
                if (ctx == null || hasWakeLock) return
                val pm = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TDaude:sessions")
                wl.setReferenceCounted(false)
                wl.acquire()
                wakeLock = wl
            } else {
                wakeLock?.let { if (it.isHeld) it.release() }
                wakeLock = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "wake lock lỗi", e)
        }
        onCountChanged?.invoke()
    }
}
