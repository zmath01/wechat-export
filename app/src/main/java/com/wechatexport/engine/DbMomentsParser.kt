package com.wechatexport.engine

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.Html
import java.io.File
import java.util.Locale

/**
 * Reads the copied WeChat Moments SQLite database directly on Android.
 *
 * The exact schema varies between WeChat releases, so table/column discovery
 * is performed at runtime. This parser covers common plaintext SnsInfo,
 * SnsComment and SnsLike layouts and leaves unsupported serialized formats
 * visible through warnings rather than silently claiming success.
 */
object DbMomentsParser {

    data class Result(val moments: List<Moment>, val warnings: List<String>)

    fun parse(dbFile: File): Result {
        require(dbFile.isFile && dbFile.length() > 0) {
            "数据库文件不存在或为空: ${dbFile.absolutePath}"
        }
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        try {
            val tableNames = tables(db)
            val snsTable = findTable(tableNames, "snsinfo", "sns_info", "sns")
                ?: throw IllegalArgumentException(
                    "未找到朋友圈主表。数据库表: ${tableNames.joinToString(", ")}"
                )
            val commentsTable = findTable(tableNames, "snscomment", "sns_comment")
            val likesTable = findTable(tableNames, "snslike", "sns_like")
            val warnings = mutableListOf<String>()

            val moments = readMoments(db, snsTable, warnings)
            if (moments.isEmpty()) {
                warnings += "$snsTable 中没有可导出的记录，可能需要针对当前微信版本的序列化/protobuf 解码"
            }
            val comments = commentsTable?.let { readComments(db, it, warnings) }.orEmpty()
            val likes = likesTable?.let { readLikes(db, it, warnings) }.orEmpty()

            val commentMap: Map<Long, List<Comment>> = comments
                .groupBy(RowComment::snsId)
                .mapValues { (_, rows) -> rows.map(RowComment::comment) }
            val likeMap: Map<Long, List<Like>> = likes
                .groupBy(RowLike::snsId)
                .mapValues { (_, rows) -> rows.map(RowLike::like) }

            val merged = moments.map { moment ->
                // Proto-path moments already carry comments/likes; only fill
                // gaps from the SnsComment / SnsLike tables (old XML DBs).
                val cs = commentMap[moment.snsId].orEmpty()
                val ls = likeMap[moment.snsId].orEmpty()
                moment.copy(
                    comments = if (moment.comments.isEmpty()) cs else moment.comments,
                    commentCount = if (moment.comments.isEmpty()) cs.size else moment.commentCount,
                    likes = if (moment.likes.isEmpty()) ls else moment.likes,
                    likeCount = if (moment.likes.isEmpty()) ls.size else moment.likeCount
                )
            }
            return Result(merged, warnings)
        } finally {
            db.close()
        }
    }

    private fun readMoments(
        db: SQLiteDatabase,
        table: String,
        warnings: MutableList<String>
    ): List<Moment> {
        val cols = columns(db, table)
        val id = column(cols, "snsId", "snsid", "sns_id", "id")
        val author = column(cols, "userName", "username", "user", "authorId", "author")
        val time = column(cols, "createTime", "createtime", "create_time", "timestamp", "time")
        val content = column(cols, "content", "contentDesc", "contentdesc", "text")
        val attrBuf = column(cols, "attrBuf", "attrbuf")
        val type = column(cols, "type", "snsType")
        if (id == null || time == null) {
            warnings += "$table 缺少 snsId/createTime，无法可靠导出；列: ${cols.keys.joinToString(", ")}"
            return emptyList()
        }
        if (content == null) warnings += "$table 没有 content 列，正文可能为空"

        val projection = listOfNotNull(id, author, time, content, attrBuf, type).distinct()
        val sql = "SELECT ${projection.joinToString(", ")} FROM ${quoteIdent(table)} ORDER BY ${quoteIdent(time)} DESC"
        val out = ArrayList<Moment>()
        db.rawQuery(sql, null).use { cursor ->
            val idIx = cursor.getColumnIndex(id)
            val authorIx = author?.let(cursor::getColumnIndex) ?: -1
            val timeIx = cursor.getColumnIndex(time)
            val contentIx = content?.let(cursor::getColumnIndex) ?: -1
            val attrBufIx = attrBuf?.let(cursor::getColumnIndex) ?: -1
            val typeIx = type?.let(cursor::getColumnIndex) ?: -1
            while (cursor.moveToNext()) {
                val snsId = cursor.longOrNull(idIx) ?: continue
                val ts = normaliseTimestamp(cursor.longOrNull(timeIx) ?: 0L)
                val authorValue = cursor.stringOrEmpty(authorIx)

                // WeChat 8.0.x stores protobuf in content/attrBuf BLOBs; older
                // versions store XML text. Decode whichever this row carries.
                val rawContent = cursor.bytesOrNull(contentIx)
                val xmlText = rawContent?.let { SnsProto.strictUtf8(it) }?.takeIf { it.contains('<') }
                var text: String
                var media: List<MediaItem>
                var comments: List<Comment> = emptyList()
                var likes: List<Like> = emptyList()
                if (xmlText != null) {
                    text = decodeContent(xmlText)
                    media = decodeMedia(xmlText)
                } else {
                    text = SnsProto.parseTextFromContent(rawContent)
                    media = SnsProto.parseMediaFromContent(rawContent)
                    comments = SnsProto.parseCommentsFromAttrBuf(cursor.bytesOrNull(attrBufIx))
                    likes = SnsProto.parseLikesFromContent(rawContent)
                }

                out += Moment(
                    snsId = snsId,
                    authorId = authorValue,
                    authorName = authorValue,
                    timestamp = ts,
                    content = text,
                    mediaList = media,
                    likes = likes, likeCount = likes.size,
                    comments = comments, commentCount = comments.size,
                    type = cursor.intOrZero(typeIx)
                )
            }
        }
        return out
    }

    private data class RowComment(val snsId: Long, val comment: Comment)
    private data class RowLike(val snsId: Long, val like: Like)

    private fun readComments(db: SQLiteDatabase, table: String, warnings: MutableList<String>): List<RowComment> {
        val cols = columns(db, table)
        val sns = column(cols, "snsId", "snsid", "sns_id")
        val name = column(cols, "nickname", "nickName", "authorName", "username", "userName", "talker")
        val content = column(cols, "content", "comment")
        val self = column(cols, "isSelf", "is_self")
        val authorId = column(cols, "userName", "username", "talker", "authorId")
        if (sns == null || content == null) return emptyList()
        if (name == null && authorId == null) warnings += "$table 没有评论者字段"
        val projection = listOfNotNull(sns, name, authorId, content, self).distinct()
        val sql = "SELECT ${projection.joinToString(", ")} FROM ${quoteIdent(table)}"
        val out = ArrayList<RowComment>()
        db.rawQuery(sql, null).use { cursor ->
            val si = cursor.getColumnIndex(sns)
            val ni = name?.let(cursor::getColumnIndex) ?: -1
            val ai = authorId?.let(cursor::getColumnIndex) ?: -1
            val ci = cursor.getColumnIndex(content)
            val selfIx = self?.let(cursor::getColumnIndex) ?: -1
            while (cursor.moveToNext()) {
                val snsId = cursor.longOrNull(si) ?: continue
                val authorName = cursor.stringOrEmpty(ni).ifBlank { cursor.stringOrEmpty(ai) }
                val text = decodeContent(cursor.stringOrEmpty(ci))
                if (text.isBlank()) continue
                out += RowComment(snsId, Comment(
                    isSelf = cursor.intOrZero(selfIx) != 0,
                    authorId = cursor.stringOrEmpty(ai),
                    authorName = authorName,
                    content = text
                ))
            }
        }
        return out
    }

    private fun readLikes(db: SQLiteDatabase, table: String, warnings: MutableList<String>): List<RowLike> {
        val cols = columns(db, table)
        val sns = column(cols, "snsId", "snsid", "sns_id")
        val name = column(cols, "nickname", "nickName", "authorName", "username", "userName", "talker")
        val authorId = column(cols, "userName", "username", "talker", "authorId")
        if (sns == null || name == null) return emptyList()
        val projection = listOfNotNull(sns, name, authorId).distinct()
        val sql = "SELECT ${projection.joinToString(", ")} FROM ${quoteIdent(table)}"
        val out = ArrayList<RowLike>()
        db.rawQuery(sql, null).use { cursor ->
            val si = cursor.getColumnIndex(sns)
            val ni = cursor.getColumnIndex(name)
            val ai = authorId?.let(cursor::getColumnIndex) ?: -1
            while (cursor.moveToNext()) {
                val snsId = cursor.longOrNull(si) ?: continue
                val n = cursor.stringOrEmpty(ni)
                if (n.isNotBlank()) out += RowLike(snsId, Like(cursor.stringOrEmpty(ai), n))
            }
        }
        return out
    }

    private fun decodeContent(raw: String): String {
        if (raw.isBlank()) return ""
        val s = raw.trim()
        if (!s.contains('<')) return s
        val flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val desc = Regex("<contentDesc>(.*?)</contentDesc>", flags)
            .find(s)?.groupValues?.getOrNull(1)
        if (!desc.isNullOrBlank()) return Html.fromHtml(desc, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun decodeMedia(raw: String): List<MediaItem> {
        if (raw.isBlank() || !raw.contains('<')) return emptyList()
        val flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val out = ArrayList<MediaItem>()
        val mediaBlocks = Regex(
            "<(?:media|mediaItem)\\b([^>]*)>(.*?)</(?:media|mediaItem)>", flags
        )
        for (match in mediaBlocks.findAll(raw)) {
            val attrs = match.groupValues.getOrNull(1).orEmpty()
            val body = match.groupValues.getOrNull(2).orEmpty()
            val mediaType = Regex("(?:type|mediaType)\\s*=\\s*[\\\"'](\\d+)[\\\"']", flags)
                .find(attrs)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("<(?:type|mediaType)>(\\d+)</(?:type|mediaType)>", flags)
                    .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 2
            val url = Regex("<(?:url|urlList|cdnUrl)>(.*?)</(?:url|urlList|cdnUrl)>", flags)
                .find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val title = Regex("<title>(.*?)</title>", flags)
                .find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val thumb = Regex("<(?:thumb|thumbUrl)>(.*?)</(?:thumb|thumbUrl)>", flags)
                .find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { null }
            if (url.isNotBlank()) {
                out += MediaItem(
                    type = mediaType,
                    url = Html.fromHtml(url, Html.FROM_HTML_MODE_LEGACY).toString(),
                    title = Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY).toString(),
                    thumb = thumb?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                )
            }
        }
        return out
    }

    private fun normaliseTimestamp(value: Long): Long = when {
        value <= 0 -> 0L
        value > 10_000_000_000L -> value / 1000L
        else -> value
    }

    private fun tables(db: SQLiteDatabase): List<String> {
        val out = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null
        ).use { cursor ->
            while (cursor.moveToNext()) out += cursor.getString(0)
        }
        return out
    }

    private fun columns(db: SQLiteDatabase, table: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        db.rawQuery("PRAGMA table_info(${quoteIdent(table)})", null).use { cursor ->
            while (cursor.moveToNext()) {
                val actual = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                out[actual.lowercase(Locale.ROOT)] = actual
            }
        }
        return out
    }

    private fun column(cols: Map<String, String>, vararg names: String): String? =
        names.firstNotNullOfOrNull { cols[it.lowercase(Locale.ROOT)] }

    private fun findTable(tables: List<String>, vararg names: String): String? {
        val normalized = tables.associateBy { it.lowercase(Locale.ROOT) }
        return names.firstNotNullOfOrNull { normalized[it.lowercase(Locale.ROOT)] }
            ?: tables.firstOrNull { t -> names.any { n -> t.equals(n, ignoreCase = true) } }
    }

    private fun quoteIdent(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    /** Blob-safe: BLOB columns are strict-UTF-8 decoded, "" if binary. */
    private fun Cursor.stringOrEmpty(index: Int): String {
        if (index < 0 || isNull(index)) return ""
        return try {
            when (getType(index)) {
                Cursor.FIELD_TYPE_BLOB -> SnsProto.strictUtf8(getBlob(index)) ?: ""
                else -> getString(index) ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun Cursor.bytesOrNull(index: Int): ByteArray? {
        if (index < 0 || isNull(index)) return null
        return try {
            when (getType(index)) {
                Cursor.FIELD_TYPE_BLOB -> getBlob(index)
                Cursor.FIELD_TYPE_STRING -> getString(index)?.toByteArray(Charsets.UTF_8)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun Cursor.longOrNull(index: Int): Long? =
        if (index >= 0 && !isNull(index)) getLong(index) else null

    private fun Cursor.intOrZero(index: Int): Int =
        if (index >= 0 && !isNull(index)) getInt(index) else 0
}
