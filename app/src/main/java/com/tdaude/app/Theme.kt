package com.tdaude.app

import android.graphics.Color
import java.util.Properties

/** Một bộ màu cho terminal. Màu giao diện (thanh trên, phím, drawer) được suy ra từ bg/fg/accent. */
class Scheme(
    val name: String,
    val bg: String,
    val fg: String,
    val cursor: String,
    val accent: String,
    /** 16 màu ANSI (color0..color15). null = dùng bảng màu mặc định của xterm. */
    val ansi: List<String>?
) {
    fun toProperties(): Properties {
        val p = Properties()
        p.setProperty("foreground", fg)
        p.setProperty("background", bg)
        p.setProperty("cursor", cursor)
        ansi?.forEachIndexed { i, c -> p.setProperty("color$i", c) }
        return p
    }

    fun palette(): Palette = Palette(Color.parseColor(bg), Color.parseColor(fg), Color.parseColor(accent))
}

/** Màu dùng cho toàn bộ phần giao diện quanh terminal. */
class Palette(val bg: Int, val fg: Int, val accent: Int) {
    val bar: Int = mix(bg, Color.WHITE, 0.06f)
    val key: Int = mix(bg, Color.WHITE, 0.12f)
    val keyPressed: Int = mix(bg, Color.WHITE, 0.26f)
    val stroke: Int = mix(bg, Color.WHITE, 0.15f)
    val text: Int = fg
    val dim: Int = mix(fg, bg, 0.45f)
    val accentSoft: Int = Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent))
    val ok: Int = Color.parseColor("#3FB950")

    companion object {
        fun mix(a: Int, b: Int, t: Float): Int {
            val r = (Color.red(a) * (1f - t) + Color.red(b) * t).toInt()
            val g = (Color.green(a) * (1f - t) + Color.green(b) * t).toInt()
            val bl = (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
            return Color.rgb(r, g, bl)
        }
    }
}

object Schemes {
    val all: List<Scheme> = listOf(
        Scheme(
            name = "TDaude Night",
            bg = "#0D1117", fg = "#E6EDF3", cursor = "#2DD4BF", accent = "#2DD4BF",
            ansi = listOf(
                "#484F58", "#FF7B72", "#3FB950", "#D29922", "#58A6FF", "#BC8CFF", "#39C5CF", "#B1BAC4",
                "#6E7681", "#FFA198", "#56D364", "#E3B341", "#79C0FF", "#D2A8FF", "#56D4DD", "#F0F6FC"
            )
        ),
        Scheme(
            name = "Dracula",
            bg = "#282A36", fg = "#F8F8F2", cursor = "#F8F8F2", accent = "#FF79C6",
            ansi = listOf(
                "#21222C", "#FF5555", "#50FA7B", "#F1FA8C", "#BD93F9", "#FF79C6", "#8BE9FD", "#F8F8F2",
                "#6272A4", "#FF6E6E", "#69FF94", "#FFFFA5", "#D6ACFF", "#FF92DF", "#A4FFFF", "#FFFFFF"
            )
        ),
        Scheme(
            name = "One Dark",
            bg = "#282C34", fg = "#ABB2BF", cursor = "#528BFF", accent = "#61AFEF",
            ansi = listOf(
                "#3F4451", "#E06C75", "#98C379", "#E5C07B", "#61AFEF", "#C678DD", "#56B6C2", "#ABB2BF",
                "#5C6370", "#E06C75", "#98C379", "#E5C07B", "#61AFEF", "#C678DD", "#56B6C2", "#FFFFFF"
            )
        ),
        Scheme(
            name = "Solarized Dark",
            bg = "#002B36", fg = "#93A1A1", cursor = "#93A1A1", accent = "#2AA198",
            ansi = listOf(
                "#073642", "#DC322F", "#859900", "#B58900", "#268BD2", "#D33682", "#2AA198", "#EEE8D5",
                "#586E75", "#CB4B16", "#7D9100", "#657B83", "#839496", "#6C71C4", "#93A1A1", "#FDF6E3"
            )
        ),
        Scheme(
            name = "Termux Classic",
            bg = "#000000", fg = "#FFFFFF", cursor = "#FFFFFF", accent = "#80DEEA",
            ansi = null
        )
    )
}
