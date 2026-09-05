package com.wechatexport.engine

import java.nio.ByteBuffer

/**
 * Generic protobuf wire-format decoder + SnsMicroMsg SnsInfo parsing for
 * WeChat 8.0.x, where `SnsInfo.content` / `SnsInfo.attrBuf` are BLOBs holding
 * protobuf instead of the old XML strings.
 *
 * Faithful Kotlin port of wechat-re `snskit.py`:
 *   CONTENT_TEXT_FIELD = 5, CONTENT_MEDIA_FIELD = 8, CONTENT_LIKES_FIELD = 9,
 *   CONTENT_CMTS_FIELD = 10, ATTRBUF_COMMENT_FIELD = 12.
 */
object SnsProto {

    private const val CONTENT_TEXT_FIELD = 5
    private const val CONTENT_MEDIA_FIELD = 8
    private const val CONTENT_LIKES_FIELD = 9
    private const val ATTRBUF_COMMENT_FIELD = 12

    private class Field(val f: Int, val wt: Int, val num: Long, val bytes: ByteArray?) {
        val isBytes: Boolean get() = wt == 2
    }

    private fun decode(buf: ByteArray): List<Field> {        val out = ArrayList<Field>()
        var i = 0
        val n = buf.size
        fun readVarint(i0: Int): Pair<Long, Int> {
            var shift = 0
            var v = 0L
            var i = i0
            while (true) {
                if (i >= n) return v to i
                val x = buf[i].toInt() and 0xff
                i++
                v = v or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return v to i
                shift += 7
            }
        }
        while (i < n) {
            val (key, ni) = readVarint(i)
            i = ni
            val f = (key shr 3).toInt()
            val wt = (key and 7).toInt()
            when (wt) {
                0 -> { val (v, i2) = readVarint(i); i = i2; out += Field(f, 0, v, null) }
                2 -> {
                    val (ln, i2) = readVarint(i); i = i2
                    val len = ln.toInt()
                    if (len < 0 || i + len > n) return out
                    out += Field(f, 2, 0, buf.copyOfRange(i, i + len)); i += len
                }
                5 -> { if (i + 4 > n) return out; out += Field(f, 5, 0, buf.copyOfRange(i, i + 4)); i += 4 }
                1 -> { if (i + 8 > n) return out; out += Field(f, 1, 0, buf.copyOfRange(i, i + 8)); i += 8 }
                else -> return out
            }
        }
        return out
    }

    fun strictUtf8(b: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b)).toString()
    } catch (_: Exception) {
        null
    }

    private fun tryText(b: ByteArray): String? {
        val s = strictUtf8(b) ?: return null
        return if (s.all { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' || it.code > 127 }) s else null
    }

    /** Comments embedded in SnsInfo.attrBuf (field 12). isSelf always false —
     *  the app has no --me argument; renderer shows "（我）" only if set later. */
    fun parseCommentsFromAttrBuf(ab: ByteArray?): List<Comment> {
        if (ab == null || ab.isEmpty()) return emptyList()
        val out = ArrayList<Comment>()
        for (f in decode(ab)) {
            if (f.f != ATTRBUF_COMMENT_FIELD || !f.isBytes) continue
            val flds = HashMap<Int, Field>()
            for (cf in decode(f.bytes!!)) if (!flds.containsKey(cf.f)) flds[cf.f] = cf
            val aid = flds[1]?.bytes?.let(::strictUtf8) ?: continue
            val anm = flds[2]?.bytes?.let(::strictUtf8).orEmpty()
            val ctt = flds[5]?.bytes?.let(::strictUtf8).orEmpty()
            if (ctt.isBlank()) continue
            out += Comment(isSelf = false, authorId = aid, authorName = anm, content = ctt)
        }
        return out
    }

    /** LikeInfo from SnsObject (field 9): field1=count, field2=repeated{1=username,2=nickname}. */
    fun parseLikesFromContent(content: ByteArray?): List<Like> {
        if (content == null || content.isEmpty()) return emptyList()
        val out = ArrayList<Like>()
        for (f in decode(content)) {
            if (f.f != CONTENT_LIKES_FIELD || !f.isBytes) continue
            for (lf in decode(f.bytes!!)) {
                if (lf.f != 2 || !lf.isBytes) continue
                var un: String? = null
                var nm: String? = null
                for (sf in decode(lf.bytes!!)) {
                    if (sf.f == 1 && sf.isBytes) un = strictUtf8(sf.bytes!!)
                    if (sf.f == 2 && sf.isBytes) nm = strictUtf8(sf.bytes!!)
                }
                val u = un.orEmpty()
                val n = nm.orEmpty()
                if (u.isNotBlank() || n.isNotBlank()) out += Like(authorId = u, authorName = n.ifBlank { u })
            }
        }
        return out
    }

    /** Decode SnsObject media (field 8) into the app's MediaItem model.
     *  snskit type 1/2 → image (type 2), 6 → link, 7(video) → thumbnail image. */
    fun parseMediaFromContent(content: ByteArray?): List<MediaItem> {
        if (content == null || content.isEmpty()) return emptyList()
        val out = ArrayList<MediaItem>()
        for (f in decode(content)) {
            if (f.f != CONTENT_MEDIA_FIELD || !f.isBytes) continue
            var mtitle: String? = null
            var mlink: String? = null
            val f5thumbs = ArrayList<List<String>>()
            val f5links = ArrayList<String?>()
            val f5titles = ArrayList<String?>()
            val f5videos = ArrayList<String?>()
            for (sf in decode(f.bytes!!)) {
                when {
                    sf.wt == 0 && sf.f == 2 -> {} // media type varint, unused
                    sf.wt == 2 && sf.f == 3 -> mtitle = tryText(sf.bytes!!)
                    sf.wt == 2 && sf.f == 4 -> {
                        val s = tryText(sf.bytes!!)
                        if (s != null && (s.startsWith("http://") || s.startsWith("https://"))) mlink = s
                    }
                    sf.f == 5 && sf.isBytes -> {
                        val q = ArrayList<String>()
                        var dl: String? = null
                        var dt: String? = null
                        var vu: String? = null
                        for (mf in decode(sf.bytes!!)) {
                            if (!mf.isBytes) continue
                            val s = tryText(mf.bytes!!) ?: continue
                            if ("snsvideodownload" in s && "vweixinthumb" !in s) vu = s
                            else if ("qpic.cn" in s || "vweixinthumb" in s) q += s
                            else if (s.startsWith("http://") || s.startsWith("https://")) dl = s
                            if (mf.f == 9) dt = s
                            if (mf.f == 11 && (s.startsWith("http://") || s.startsWith("https://"))) dl = s
                        }
                        f5thumbs += q; f5links += dl; f5titles += dt; f5videos += vu
                    }
                }
            }
            val link = mlink ?: f5links.firstOrNull { !it.isNullOrBlank() }
            val videoUrl = f5videos.firstOrNull { !it.isNullOrBlank() }
            val allThumbs = f5thumbs.flatten()
            fun pickThumb(): String? =
                allThumbs.firstOrNull { "vweixinthumb" in it }
                    ?: allThumbs.firstOrNull { it.trimEnd('/').endsWith("/150") }
                    ?: allThumbs.maxByOrNull { it.length }
            if (videoUrl != null) {
                val title = f5titles.firstOrNull { !it.isNullOrBlank() } ?: mtitle.orEmpty()
                val thumb = pickThumb()
                // Render as image (thumbnail) — MediaItem has no video field.
                out += MediaItem(type = 2, url = thumb ?: videoUrl, title = title, thumb = thumb)
            } else if (link != null) {
                val title = mtitle ?: f5titles.firstOrNull { !it.isNullOrBlank() }.orEmpty()
                out += MediaItem(type = 6, url = link, title = title, thumb = pickThumb())
            } else {
                for ((idx, q) in f5thumbs.withIndex()) {
                    if (q.isEmpty()) continue
                    val full = q.maxByOrNull { it.length } ?: continue
                    val isVideo = "mmsns.qpic.cn" in full && "szmmsns.qpic.cn" !in full
                    val url = if (isVideo) full else (full.substringBeforeLast('/') + "/0")
                    out += MediaItem(type = 2, url = url, title = "", thumb = null)
                }
            }
        }
        return out
    }

    fun parseTextFromContent(content: ByteArray?): String {
        if (content == null || content.isEmpty()) return ""
        for (f in decode(content)) {
            if (f.f == CONTENT_TEXT_FIELD && f.isBytes) {
                tryText(f.bytes!!)?.let { return it }
            }
        }
        return ""
    }
}
