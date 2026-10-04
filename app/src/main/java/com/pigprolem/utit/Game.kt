package com.pigprolem.utit

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

fun dayIndex(): Long {
    val now = System.currentTimeMillis()
    return (now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L
}

class Game(private val sp: SharedPreferences) {
    var stars by mutableStateOf(loadStars())
        private set
    var corn by mutableStateOf(sp.getInt("corn", sp.getInt("c", 0)))
        private set
    var streak by mutableStateOf(sp.getInt("streak", 0))
        private set
    var lastDay by mutableStateOf(sp.getLong("lastDay", -1L))
        private set
    var doneDay by mutableStateOf(sp.getLong("doneDay", -1L))
        private set
    var doneCount by mutableStateOf(sp.getInt("doneCount", 0))
        private set
    var goal by mutableStateOf(sp.getInt("goal", 1).coerceIn(1, 3))
        private set
    var sound by mutableStateOf(sp.getBoolean("sound", true))
        private set
    var haptic by mutableStateOf(sp.getBoolean("haptic", true))
        private set
    var theme by mutableStateOf(sp.getInt("theme", 0).coerceIn(0, 2))
        private set

    private fun loadStars(): List<Int> =
        parseStars(sp.getString("stars", null) ?: sp.getString("d", null), LEVELS.size)

    private fun save() {
        sp.edit()
            .putString("stars", stars.joinToString(","))
            .putInt("corn", corn)
            .putInt("streak", streak)
            .putLong("lastDay", lastDay)
            .putLong("doneDay", doneDay)
            .putInt("doneCount", doneCount)
            .putInt("goal", goal)
            .putBoolean("sound", sound)
            .putBoolean("haptic", haptic)
            .putInt("theme", theme)
            .apply()
    }

    fun streakNow(): Int = if (lastDay >= dayIndex() - 1) streak else 0

    fun doneTodayCount(): Int = if (doneDay == dayIndex()) doneCount else 0

    fun nextLevel(): Int = stars.indexOfFirst { it == 0 }

    fun isOpen(i: Int): Boolean = i == 0 || stars.getOrElse(i - 1) { 0 } > 0

    fun finish(idx: Int, s: Int, correct: Int): Int {
        val today = dayIndex()
        var gain = correct
        if (idx >= 0 && idx < stars.size) {
            if (stars[idx] == 0) gain = correct * 5
            if (s > stars[idx]) {
                val ns = stars.toMutableList()
                ns[idx] = s
                stars = ns
            }
        }
        corn += gain
        if (lastDay != today) {
            streak = if (lastDay == today - 1) streak + 1 else 1
            lastDay = today
        }
        if (doneDay != today) {
            doneDay = today
            doneCount = 0
        }
        doneCount += 1
        save()
        return gain
    }

    fun updateGoal(v: Int) { goal = v.coerceIn(1, 3); save() }
    fun updateSound(v: Boolean) { sound = v; save() }
    fun updateHaptic(v: Boolean) { haptic = v; save() }
    fun updateTheme(v: Int) { theme = v.coerceIn(0, 2); save() }

    fun reset() {
        stars = List(LEVELS.size) { 0 }
        corn = 0
        streak = 0
        lastDay = -1L
        doneDay = -1L
        doneCount = 0
        save()
    }
}
