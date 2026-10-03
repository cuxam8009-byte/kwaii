package com.zyqo.app

import java.io.File
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    private fun project(vararg f: Pair<String, String>): ProjectData {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        f.forEach {
            files[it.first] = it.second
            paths.add(it.first)
        }
        return ProjectData("t.zip", File("t.zip"), files, paths, 0)
    }

    private fun edit(path: String, find: String, rep: String): String =
        "<edit path=\"$path\">\n<find>\n$find\n</find>\n<replace>\n$rep\n</replace>\n</edit>"

    @Test
    fun appliesEditOnce() {
        val p = project("A.kt" to "a\nfoo()\nb\n")
        val r = p.apply(edit("A.kt", "foo()", "bar()"))
        assertEquals(listOf("Sửa A.kt"), r.changes)
        assertEquals("a\nbar()\nb\n", p.files["A.kt"])
        assertTrue(p.canUndo)
    }

    @Test
    fun rejectsAmbiguousEdit() {
        val p = project("A.kt" to "x\nx\n")
        val r = p.apply(edit("A.kt", "x", "y"))
        assertTrue(r.changes.isEmpty())
        assertEquals(1, r.errors.size)
        assertEquals("x\nx\n", p.files["A.kt"])
        assertFalse(p.canUndo)
    }

    @Test
    fun rejectsMissingFind() {
        val p = project("A.kt" to "abc\n")
        val r = p.apply(edit("A.kt", "zzz", "y"))
        assertEquals(1, r.errors.size)
    }

    @Test
    fun preservesCrlf() {
        val p = project("A.kt" to "a\r\nfoo\r\n")
        p.apply(edit("A.kt", "foo", "bar"))
        assertEquals("a\r\nbar\r\n", p.files["A.kt"])
    }

    @Test
    fun rejectsPathTraversal() {
        val p = project("A.kt" to "x")
        val r = p.apply("<file path=\"../evil.txt\">\nx\n</file>")
        assertTrue(r.changes.isEmpty())
        assertEquals(1, r.errors.size)
    }

    @Test
    fun createsAndUndoesFile() {
        val p = project("A.kt" to "x")
        p.apply("<file path=\"B.kt\">\nfun b() {}\n</file>")
        assertTrue(p.paths.contains("B.kt"))
        assertTrue(p.undo())
        assertFalse(p.paths.contains("B.kt"))
    }

    @Test
    fun stripsFenceInsideFileBlock() {
        val p = project("A.kt" to "x")
        p.apply("<file path=\"B.kt\">\n```kotlin\nfun b() {}\n```\n</file>")
        assertEquals("fun b() {}", p.files["B.kt"])
    }

    @Test
    fun stripsPartialBlocksWhileStreaming() {
        val text = "Xong.\n<file path=\"a.kt\">\nfun"
        assertEquals("Xong.", stripBlocks(text))
        assertEquals("a.kt", writingPath(text))
    }

    @Test
    fun collectsReadRequests() {
        val text = "Cần đọc.\n<read path=\"a.kt\"/>\n<read path=\"b/c.kt\"/>"
        assertEquals(listOf("a.kt", "b/c.kt"), readRequests(text))
        assertEquals("Cần đọc.", stripBlocks(text))
    }

    @Test
    fun readBackReportsUnknownFiles() {
        val p = project("A.kt" to "x")
        val out = p.readBack(listOf("A.kt", "nope.kt"), 1000)
        assertTrue(out.contains("<file path=\"A.kt\">"))
        assertTrue(out.contains("nope.kt"))
    }

    @Test
    fun snapshotRespectsBudgetAndListsMissing() {
        val p = project("A.kt" to "a".repeat(500), "B.kt" to "b".repeat(50))
        val s = p.snapshot(400)
        assertTrue(s.contains("<read path"))
        assertTrue(s.contains("B.kt"))
    }
}
