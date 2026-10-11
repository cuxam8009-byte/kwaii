package com.tdaude.app

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Dựng "hệ thống nhỏ" cho shell: ~ (home), usr/bin (PATH riêng của TDaude), file rc, biến môi trường.
 * Bố cục thư mục (trong bộ nhớ riêng của app):
 *   files/home        -> $HOME
 *   files/usr/bin     -> $PREFIX/bin  (nơi sau này đặt chương trình đi kèm)
 *   files/usr/etc     -> $PREFIX/etc  (tdaude.rc nằm ở đây)
 *   cache/tmp         -> $TMPDIR
 */
object Shell {
    const val VERSION = "0.4"
    private const val TAG = "TDaude"

    private var prepared = false

    fun home(ctx: Context): File = File(ctx.filesDir, "home")
    fun prefix(ctx: Context): File = File(ctx.filesDir, "usr")

    @Synchronized
    fun prepare(ctx: Context) {
        if (prepared) return
        val home = home(ctx)
        val prefix = prefix(ctx)
        home.mkdirs()
        File(home, "bin").mkdirs()
        File(prefix, "bin").mkdirs()
        File(prefix, "etc").mkdirs()
        File(ctx.cacheDir, "tmp").mkdirs()

        // Ghi đè rc mỗi lần mở app để luôn là bản mới nhất.
        try {
            ctx.assets.open("tdaude.rc").use { input ->
                FileOutputStream(File(prefix, "etc/tdaude.rc")).use { out -> input.copyTo(out) }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Không ghi được tdaude.rc", e)
        }

        // File cấu hình riêng của người dùng: chỉ tạo nếu chưa có.
        val user = File(home, ".mkshrc")
        if (!user.exists()) {
            try {
                user.writeText(
                    "# ~/.mkshrc - cấu hình shell riêng của bạn (không bị app ghi đè).\n" +
                        "# Ví dụ:\n" +
                        "#   alias gs='git status'\n" +
                        "#   PS1='my-prompt> '\n",
                    Charsets.UTF_8
                )
            } catch (e: IOException) {
                Log.e(TAG, "Không tạo được ~/.mkshrc", e)
            }
        }
        prepared = true
    }

    private fun buildEnv(ctx: Context): Array<String> {
        val home = home(ctx)
        val prefix = prefix(ctx)
        val env = HashMap<String, String>(System.getenv())
        val sysPath = System.getenv("PATH") ?: "/system/bin"
        env["HOME"] = home.absolutePath
        env["PREFIX"] = prefix.absolutePath
        env["TMPDIR"] = File(ctx.cacheDir, "tmp").absolutePath
        env["PATH"] = prefix.absolutePath + "/bin:" + home.absolutePath + "/bin:" + sysPath
        env["ENV"] = File(prefix, "etc/tdaude.rc").absolutePath
        env["SHELL"] = "/system/bin/sh"
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["LANG"] = "en_US.UTF-8"
        env["TDAUDE_VERSION"] = VERSION
        return env.map { it.key + "=" + it.value }.toTypedArray()
    }

    fun newSession(ctx: Context, client: TerminalSessionClient): TerminalSession {
        prepare(ctx)
        return TerminalSession(
            "/system/bin/sh",
            home(ctx).absolutePath,
            arrayOf("sh"),
            buildEnv(ctx),
            2000,
            client
        )
    }
}
