package com.wechatexport.engine

import java.io.File

/**
 * Writes the full export set into the destination directory, mirroring the
 * snskit.py / combine_export.py layout of the wechat-re tooling:
 *
 *   exported_sns.json        all moments, newest first (combined)
 *   exported_sns.md / .html  all moments, newest first (readable combined)
 *   YYYY-MM.md / YYYY-MM.html one pair per month, newest month first,
 *                             newest moment first inside each file
 */
object MonthlyExporter {

    data class Summary(
        val total: Int,
        val months: List<Pair<String, Int>>,   // (yyyy-MM, count), newest first
        val json: File,
        val md: File,
        val html: File
    )

    fun exportAll(
        moments: List<Moment>,
        dir: File,
        combinedTitle: String = "微信朋友圈导出"
    ): Summary {
        dir.mkdirs()
        // Newest first everywhere (倒序).
        val sorted = moments.sortedByDescending { it.timestamp }

        val json = File(dir, "exported_sns.json")
        json.writeText(MomentsWriter.toJson(sorted), Charsets.UTF_8)

        val md = File(dir, "exported_sns.md")
        md.writeText(MomentsConverter.toMarkdown(sorted), Charsets.UTF_8)

        val html = File(dir, "exported_sns.html")
        html.writeText(MomentsConverter.toHtml(sorted, combinedTitle), Charsets.UTF_8)

        // Group by month, newest month first.
        val byMonth = MomentsConverter.groupByMonth(sorted)
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Moment>>> { it.key })
            .map { it.key to it.value }

        for ((month, list) in byMonth) {
            val monthSorted = list.sortedByDescending { it.timestamp }
            File(dir, "$month.md").writeText(
                MomentsConverter.toMarkdown(monthSorted), Charsets.UTF_8
            )
            File(dir, "$month.html").writeText(
                MomentsConverter.toHtml(monthSorted, "$combinedTitle $month"), Charsets.UTF_8
            )
        }

        return Summary(
            total = sorted.size,
            months = byMonth.map { (k, v) -> k to v.size },
            json = json,
            md = md,
            html = html
        )
    }
}
