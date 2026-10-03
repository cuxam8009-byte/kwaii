package com.zyqo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTest {
    @Test
    fun parsesSearchAndFetchCalls() {
        val text = "<search q=\"kotlin flow\"/>\n<fetch url=\"https://a.dev/x?a=1&amp;b=2\" q=\"retry\"/>"
        val calls = toolCalls(text)
        assertEquals(2, calls.size)
        assertEquals("kotlin flow", (calls[0] as ToolCall.Search).q)
        val f = calls[1] as ToolCall.Fetch
        assertEquals("https://a.dev/x?a=1&b=2", f.url)
        assertEquals("retry", f.q)
    }

    @Test
    fun stripsToolTagsIncludingPartial() {
        assertEquals("Ok.", stripBlocks("Ok.\n<search q=\"a\"/>"))
        assertEquals("Ok.", stripBlocks("Ok.\n<fetch url=\"https://a.dev"))
        assertTrue(hasBlocks("x <search q=\"a\"/>"))
    }

    @Test
    fun convertsHtmlToCleanText() {
        val html = "<html><head><title>Tiêu &amp; đề</title><style>p{}</style></head><body><nav>menu</nav>" +
            "<script>var a=1;</script><h1>Hello</h1><p>One  two&nbsp;three</p><footer>ft</footer></body></html>"
        assertEquals("Tiêu & đề", pageTitle(html))
        assertEquals("Hello\nOne two three", htmlToText(html))
    }

    @Test
    fun picksRelevantParagraphsWithinBudget() {
        val filler = (1..40).joinToString("\n") { "Đoạn không liên quan số $it nói về chuyện khác hoàn toàn" }
        val text = filler + "\nCách cấu hình retry với backoff trong OkHttp rất đơn giản và hiệu quả\n" + filler
        val out = pickRelevant(text, "retry backoff okhttp", 300)
        assertTrue(out.length <= 300)
        assertTrue(out.contains("retry"))
    }

    @Test
    fun keepsShortTextUntouched() {
        assertEquals("abc", pickRelevant("abc", "x", 100))
    }

    @Test
    fun parsesDuckDuckGoResults() {
        val html = "<div class=\"result\"><a rel=\"nofollow\" class=\"result__a\" href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fdoc&amp;rut=abc\">Example <b>Doc</b></a>" +
            "<a class=\"result__snippet\" href=\"x\">Mô tả <b>ngắn</b></a></div>"
        val hits = parseDdg(html, 5)
        assertEquals(1, hits.size)
        assertEquals("https://example.com/doc", hits[0].url)
        assertEquals("Example Doc", hits[0].title)
        assertEquals("Mô tả ngắn", hits[0].snippet)
    }

    @Test
    fun blocksPrivateAndInsecureTargets() {
        assertFalse(urlAllowed("http", "example.com"))
        assertFalse(urlAllowed("https", "localhost"))
        assertFalse(urlAllowed("https", "192.168.1.1"))
        assertFalse(urlAllowed("https", "127.0.0.1"))
        assertFalse(urlAllowed("https", "router.local"))
        assertTrue(urlAllowed("https", "developer.android.com"))
    }

    @Test
    fun formatsEmptyHits() {
        assertTrue(formatHits("q", emptyList()).contains("Không có kết quả"))
    }
}
