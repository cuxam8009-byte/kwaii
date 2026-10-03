package com.zyqo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    @Test
    fun describesStepsInVietnamese() {
        assertEquals("Tìm \"detectProvider\"", describeStep("grep", "{\"pattern\":\"detectProvider\"}"))
        assertEquals("Đọc app/Data.kt", describeStep("read_file", "{\"path\":\"app/Data.kt\"}"))
        assertEquals("Xem cây thư mục", describeStep("list_files", "{}"))
        assertEquals("Tìm web: groq tpm", describeStep("search", "{\"query\":\"groq tpm\"}"))
        assertEquals("Đọc trang: example.com/doc", describeStep("fetch", "{\"url\":\"https://example.com/doc\"}"))
        assertEquals("Gọi abc", describeStep("abc", "không phải json"))
    }

    @Test
    fun summarizesResults() {
        assertEquals("3 kết quả", summarizeResult("grep", "A.kt:1: x\nB.kt:20: y\nC.kt:3: z", false))
        assertEquals("không thấy", summarizeResult("grep", "<tool_result name=\"grep\">\nKhông thấy kết quả.\n</tool_result>", false))
        assertEquals("lỗi", summarizeResult("read_file", "Lỗi: x", true))
        assertEquals("5 ký tự", summarizeResult("read_file", "hello", false))
        assertEquals("xong", summarizeResult("search", "bất kỳ", false))
    }

    @Test
    fun nativeHintAsksForPreamble() {
        assertTrue(NATIVE_HINT.contains("Trước khi gọi công cụ lần đầu"))
    }

    @Test
    fun messageKeepsStepsByDefault() {
        val m = Msg(1, Role.ASSISTANT, "x")
        assertTrue(m.steps.isEmpty())
        assertEquals(listOf("✓ a"), m.copy(steps = listOf("✓ a")).steps)
    }
}
