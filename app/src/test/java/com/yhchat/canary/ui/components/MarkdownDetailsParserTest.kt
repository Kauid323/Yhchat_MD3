package com.yhchat.canary.ui.components

import org.junit.Assert.*
import org.junit.Test

class MarkdownDetailsParserTest {
    @Test fun securityReportKeepsFirstAndLastLines() {
        val lines = """
            <details><summary>详细信息（点击展开）</summary>· 云湖广告检测: 未开启
            · 分享链接检测: 未开启
            · AI敏感词检测: 未开启
            · 敏感图片检测: 未开启
            · 联防黑名单检测: 未开启
            · 第三方检测: 未开启
            · 刷屏检测: 0次</details>
            > 客户端不支持提示
            **综合评价:** ✔ 从未有违规出现
        """.trimIndent().lines()
        val result = requireNotNull(extractDetailsBlock(lines, 0))
        assertEquals("详细信息（点击展开）", result.summary)
        assertEquals(7, result.content.lines().size)
        assertTrue(result.content.startsWith("· 云湖广告检测: 未开启"))
        assertTrue(result.content.endsWith("· 刷屏检测: 0次"))
        assertEquals(7, result.nextIndex)
    }

    @Test fun singleLineBlockRetainsSuffix() {
        val result = requireNotNull(extractDetailsBlock(listOf("<DETAILS><SUMMARY>标题</SUMMARY>正文</DETAILS>后文"), 0))
        assertEquals("标题", result.summary)
        assertEquals("正文", result.content)
        assertEquals("后文", result.suffix)
    }

    @Test fun nestedBlockDoesNotCloseOuterBlock() {
        val result = requireNotNull(extractDetailsBlock(listOf(
            "<details><summary>外层</summary>",
            "<details><summary>内层</summary>内部</details>",
            "外层末尾</details>"
        ), 0))
        assertTrue(result.content.contains("<summary>内层</summary>"))
        assertTrue(result.content.endsWith("外层末尾"))
        assertEquals(3, result.nextIndex)
    }

    @Test fun oldMultilineSyntaxStillWorks() {
        val result = requireNotNull(extractDetailsBlock(listOf("<details>", "<summary>", "标题", "</summary>", "正文", "</details>"), 0))
        assertEquals("标题", result.summary)
        assertEquals("正文", result.content)
    }

    @Test fun unmatchedBlockAndInlineCodeAreNotConsumed() {
        assertNull(extractDetailsBlock(listOf("<details>未结束"), 0))
        assertNull(extractDetailsBlock(listOf("`<details>` 是标签"), 0))
    }
}
