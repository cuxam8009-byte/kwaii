package com.pigprolem.utit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTest {
    @Test
    fun courseHasLevels() {
        assertTrue(LEVELS.size >= 10)
        assertEquals(LEVELS.size, UNITS.sumOf { it.levels.size })
    }

    @Test
    fun filesAreUnique() {
        assertEquals(LEVELS.size, LEVELS.map { it.file }.toSet().size)
    }

    @Test
    fun everyLevelHasPractice() {
        for (lv in LEVELS) {
            assertTrue(lv.title, lv.steps.isNotEmpty())
            assertTrue(lv.title, lv.steps.any { it !is Learn })
            assertTrue(lv.title, lv.title.isNotBlank() && lv.sub.isNotBlank() && lv.kind.isNotBlank())
        }
    }

    @Test
    fun everyStepIsWellFormed() {
        for (lv in LEVELS) {
            for (s in lv.steps) {
                assertTrue(lv.file, s.say.isNotBlank())
                when (s) {
                    is Learn -> {
                        assertTrue(lv.file, s.code.isNotBlank())
                        assertTrue(lv.file, s.out.isNotEmpty())
                    }
                    is Ex -> {
                        assertTrue(lv.file, s.opts.size >= 2)
                        assertEquals(lv.file, s.opts.size, s.opts.toSet().size)
                        assertTrue(lv.file, s.ans in s.opts.indices)
                        assertTrue(lv.file, s.out.isNotEmpty())
                        assertTrue(lv.file, s.why.isNotBlank())
                        if (s.pred) assertEquals(lv.file, s.opts[s.ans].split("\n"), s.out)
                        else assertTrue(lv.file, s.code.contains("___"))
                    }
                    is Bug -> {
                        assertTrue(lv.file, s.lines.size >= 2)
                        assertTrue(lv.file, s.bad in s.lines.indices)
                        assertTrue(lv.file, s.why.isNotBlank())
                    }
                    is Order -> {
                        assertTrue(lv.file, s.lines.size >= 3)
                        assertEquals(lv.file, s.lines.size, s.lines.toSet().size)
                        assertTrue(lv.file, s.out.isNotEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun starsParsingPadsAndClamps() {
        assertEquals(listOf(2, 0, 0), parseStars("2", 3))
        assertEquals(listOf(3, 1), parseStars("9,1,2,3", 2))
        assertEquals(listOf(0, 0), parseStars(null, 2))
        assertEquals(listOf(0, 1, 0), parseStars("x,1", 3))
    }

    @Test
    fun starScoring() {
        assertEquals(3, starsFor(0, 8))
        assertEquals(2, starsFor(1, 8))
        assertEquals(2, starsFor(2, 8))
        assertEquals(1, starsFor(3, 8))
    }

    @Test
    fun reviewNeedsProgress() {
        assertNull(reviewLevel(List(LEVELS.size) { 0 }))
        val r = reviewLevel(List(LEVELS.size) { 3 })
        assertNotNull(r)
        assertEquals(8, r!!.steps.size)
        assertTrue(r.steps.none { it is Learn })
    }

    @Test
    fun shuffleNeverKeepsOrder() {
        repeat(50) {
            assertTrue(shuffledOrder(4) != listOf(0, 1, 2, 3))
        }
    }
}
