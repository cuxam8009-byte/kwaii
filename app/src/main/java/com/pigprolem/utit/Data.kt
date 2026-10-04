package com.pigprolem.utit

sealed class Step { abstract val say: String }
data class Learn(override val say: String, val code: String, val out: List<String>) : Step()
data class Ex(
    override val say: String, val code: String, val opts: List<String>, val ans: Int,
    val out: List<String>, val why: String, val pred: Boolean = false
) : Step()
data class Bug(override val say: String, val lines: List<String>, val bad: Int, val why: String) : Step()
data class Order(override val say: String, val lines: List<String>, val out: List<String>, val why: String) : Step()
data class Level(val file: String, val title: String, val sub: String, val kind: String, val steps: List<Step>)
data class CUnit(val title: String, val sub: String, val levels: List<Level>)

val LEVELS: List<Level> by lazy { UNITS.flatMap { it.levels } }

fun parseStars(raw: String?, n: Int): List<Int> {
    val parsed = (raw ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }
    return List(n) { parsed.getOrElse(it) { 0 }.coerceIn(0, 3) }
}

fun reviewLevel(stars: List<Int>): Level? {
    val pool = ArrayList<Step>()
    LEVELS.forEachIndexed { i, lv ->
        if (stars.getOrElse(i) { 0 } > 0) lv.steps.filter { it !is Learn }.forEach { pool.add(it) }
    }
    if (pool.size < 4) return null
    return Level("on_tap.py", "Ôn tập", "Luyện lại những gì đã học", "Ôn tập", pool.shuffled().take(8))
}

fun shuffledOrder(n: Int): List<Int> {
    val id = (0 until n).toList()
    var l = id.shuffled()
    var guard = 0
    while (n > 1 && l == id && guard < 10) {
        l = id.shuffled()
        guard++
    }
    return l
}

fun starsFor(wrong: Int, total: Int): Int = when {
    wrong == 0 -> 3
    wrong <= maxOf(1, total / 4) -> 2
    else -> 1
}
