package com.wechatexport.engine

import org.junit.Assert.*
import org.junit.Test

class MomentsConverterTest {

    private val sample = listOf(
        Moment(
            snsId = 1,
            authorId = "u1",
            authorName = "示例用户",
            timestamp = 1769657859, // 2026-01-29 11:37 +8
            content = "hello <b>world</b>\n第二行",
            mediaList = listOf(
                MediaItem(2, "http://x/img.jpg", "", null),
                MediaItem(6, "http://x/note", "一篇笔记", null)
            ),
            likes = listOf(Like("", "alice"), Like("", "bob")),
            likeCount = 2,
            comments = listOf(Comment(true, "u1", "示例用户", "我的回复")),
            commentCount = 1,
            type = 1
        ),
        Moment(
            snsId = 2, authorId = "u2", authorName = "好友", timestamp = 0,
            content = "", mediaList = emptyList(), likes = emptyList(),
            likeCount = 0, comments = emptyList(), commentCount = 0, type = 3
        )
    )

    @Test
    fun markdown_containsHeader_andMedia() {
        val md = MomentsConverter.toMarkdown(sample)
        assertTrue(md.contains("# 微信朋友圈导出（2 条）"))
        assertTrue(md.contains("![media](http://x/img.jpg)"))
        assertTrue(md.contains("[一篇笔记](http://x/note)"))
        assertTrue(md.contains("**点赞**: alice, bob"))
        assertTrue(md.contains("_（转发 / 仅图片）_"))
    }

    @Test
    fun html_escapesInjectedTags() {
        val h = MomentsConverter.toHtml(sample, "微信朋友圈导出")
        assertTrue(h.contains("<!DOCTYPE html>"))
        // raw <b> must be escaped in HTML output
        assertFalse(h.contains("<b>world</b>"))
        assertTrue(h.contains("&lt;b&gt;world&lt;/b&gt;"))
        assertTrue(h.contains("<img class=\"media\""))
        assertTrue(h.contains("class=\"link\""))
    }

    @Test
    fun formatTime_usesPlus8() {
        assertEquals("2026-01-29 11:37", MomentsConverter.formatTime(1769657859))
        assertEquals("未知时间", MomentsConverter.formatTime(0))
    }

    @Test
    fun groupByMonth_splitsCorrectly() {
        val g = MomentsConverter.groupByMonth(
            listOf(
                Moment(1, "", "", 1700000000, "", emptyList(), emptyList(), 0, emptyList(), 0, 0),
                Moment(2, "", "", 1701000000, "", emptyList(), emptyList(), 0, emptyList(), 0, 0),
                Moment(3, "", "", 1730000000, "", emptyList(), emptyList(), 0, emptyList(), 0, 0)
            )
        )
        assertEquals(2, g["2023-11"]?.size)
        assertEquals(1, g["2024-10"]?.size)
    }
}
