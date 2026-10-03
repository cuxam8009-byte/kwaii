package com.zyqo.app

import java.io.File
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundTruthTest {
    private fun sources(): ProjectData {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach {
            val rel = it.relativeTo(root).path.replace(File.separatorChar, '/')
            files[rel] = it.readText()
            paths.add(rel)
        }
        return ProjectData("zyqo", File("zyqo.zip"), files, paths, 0)
    }

    @Test
    fun detectProviderAppearsInExactlyThreeFiles() {
        val out = LocalTools.grep(sources(), "\\bdetectProvider\\b", "", 80, false)
        val hits = out.lines().filter { it.contains(".kt:") }
        val files = hits.map { it.substringBefore(':').substringAfterLast('/') }.toSet()
        assertEquals(setOf("Data.kt", "ChatVm.kt", "SecureStore.kt"), files)
        assertEquals(3, hits.size)
    }

    @Test
    fun ghostSymbolDoesNotExist() {
        val ghost = "init" + "Provider"
        val out = LocalTools.grep(sources(), "\\b$ghost\\b", "", 80, true)
        assertEquals("Không thấy kết quả.", out)
    }

    @Test
    fun detectProviderDefinitionTakesOneArgument() {
        val out = LocalTools.grep(sources(), "fun detectProvider\\(", "", 10, false)
        assertTrue(out.contains("Data.kt"))
        assertTrue(out.contains("detectProvider(key: String)"))
    }
}
