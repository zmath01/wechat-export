package com.wechatexport.engine

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure renderer: turns parsed [Moment]s into readable Markdown / HTML.
 * No Android, file, or network dependencies — unit-testable on plain JVM.
 *
 * This is a faithful Kotlin port of the Go engine's `converter` package so the
 * desktop CLI and the Android app produce identical output.
 */
object MomentsConverter {

    private val ZONE = ZoneId.of("Asia/Shanghai") // +8, matches source device locale
    private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun formatTime(ts: Long): String =
        if (ts <= 0) "未知时间" else TIME_FMT.format(Instant.ofEpochSecond(ts).atZone(ZONE))

    fun monthKey(ts: Long): String =
        if (ts <= 0) "0000-00" else Instant.ofEpochSecond(ts).atZone(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM"))

    fun groupByMonth(moments: List<Moment>): Map<String, List<Moment>> =
        moments.groupBy { monthKey(it.timestamp) }

    // ── Markdown ──────────────────────────────────────────────────────────

    fun toMarkdown(moments: List<Moment>): String = buildString {
        append("# 微信朋友圈导出（").append(moments.size).append(" 条）\n\n")
        for (m in moments) {
            append(renderMomentMarkdown(m)).append("\n---\n\n")
        }
    }

    private fun renderMomentMarkdown(m: Moment): String = buildString {
        append("## ").append(m.authorName).append(" · ").append(formatTime(m.timestamp)).append("\n\n")
        val body = if (m.content.isBlank()) "_（转发 / 仅图片）_" else m.content
        append(body).append("\n")
        for (media in m.mediaList) append("\n").append(renderMediaMarkdown(media))
        if (m.likes.isNotEmpty()) {
            append("\n\n**点赞**: ").append(m.likes.joinToString(", ") { it.authorName })
        }
        if (m.comments.isNotEmpty()) {
            append("\n\n**评论:**\n")
            for (c in m.comments) {
                val prefix = if (c.isSelf) "（我）" else ""
                append("> ").append(prefix).append(c.authorName).append(": ")
                    .append(c.content).append("\n>\n")
            }
        }
    }

    private fun renderMediaMarkdown(media: MediaItem): String = when (media.type) {
        2 -> "![media](${media.url})"               // image
        else -> "[${media.title.ifBlank { media.url }}](${media.url})" // link / note
    }

    // ── HTML ───────────────────────────────────────────────────────────────

    fun toHtml(moments: List<Moment>, title: String): String = buildString {
        append(HTML_HEAD.replace("%TITLE%", esc(title)))
        append("<h2 style=\"text-align:center;color:#576b95\">")
            .append(esc(title)).append("（").append(moments.size).append(" 条）</h2>\n")
        for (m in moments) append(renderMomentHtml(m))
        append(HTML_END)
    }

    private fun renderMomentHtml(m: Moment): String = buildString {
        append("<article class=\"post\">")
        append("<div class=\"head\"><span class=\"author\">").append(esc(m.authorName))
            .append("</span><span class=\"time\">").append(esc(formatTime(m.timestamp))).append("</span></div>")
        append("<div class=\"content\">")
        if (m.content.isBlank()) append("<span class=\"empty\">(转发 / 仅图片)</span>")
        else append(esc(m.content))
        append("</div>")
        if (m.mediaList.isNotEmpty()) {
            append("<div class=\"media-row\">")
            for (media in m.mediaList) append(renderMediaHtml(media))
            append("</div>")
        }
        if (m.likes.isNotEmpty()) {
            append("<div class=\"likes\">&#10084; ")
                .append(m.likes.joinToString(", ") { esc(it.authorName) })
                .append("</div>")
        }
        if (m.comments.isNotEmpty()) {
            append("<div class=\"comments\">")
            for (c in m.comments) {
                val cls = if (c.isSelf) "comment self" else "comment"
                append("<div class=\"$cls\"><span class=\"cname\">").append(esc(c.authorName))
                    .append("</span><span class=\"ctext\">").append(esc(c.content)).append("</span></div>")
            }
            append("</div>")
        }
        append("</article>")
    }

    private fun renderMediaHtml(media: MediaItem): String = when (media.type) {
        2 -> "<a href=\"${esc(media.url)}\" target=\"_blank\" rel=\"noopener\">" +
             "<img class=\"media\" loading=\"lazy\" src=\"${esc(media.url)}\" alt=\"media\"></a>"
        else -> "<a class=\"link\" href=\"${esc(media.url)}\" target=\"_blank\" rel=\"noopener\">" +
                "${esc(media.title.ifBlank { media.url })}</a>"
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private const val HTML_HEAD = """<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>%TITLE%</title>
<style>
 body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;max-width:720px;margin:0 auto;padding:16px;background:#f5f5f5;color:#222}
 .post{background:#fff;border-radius:8px;padding:12px 14px;margin-bottom:14px;box-shadow:0 1px 3px rgba(0,0,0,.08)}
 .head{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:6px}
 .author{font-weight:600;color:#576b95}
 .time{font-size:12px;color:#999}
 .content{white-space:pre-wrap;line-height:1.6;word-break:break-word}
 .empty{color:#bbb;font-style:italic}
 .media-row{display:flex;flex-wrap:wrap;gap:4px;margin-top:8px}
 .media{width:110px;height:110px;object-fit:cover;border-radius:4px}
 .link{display:inline-block;margin:2px 4px 2px 0;color:#576b95}
 .likes{margin-top:8px;font-size:13px;color:#e63946}
 .comments{margin-top:8px;background:#f7f7f7;border-radius:6px;padding:8px}
 .comment{font-size:14px;line-height:1.5;margin-bottom:4px}
 .comment.self .cname{color:#576b95}
 .cname{font-weight:600;color:#576b95;margin-right:4px}
 .ctext{word-break:break-word}
</style>
</head>
<body>
"""

    private const val HTML_END = "</body>\n</html>"
}
