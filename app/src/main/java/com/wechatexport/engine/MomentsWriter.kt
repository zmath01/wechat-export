package com.wechatexport.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serialises [Moment]s back to the JSON schema used by snskit.py /
 * MomentsLoader (snsId, authorId, authorName, timestamp, content, mediaList,
 * likes, likeCount, comments, commentCount, type) so the output round-trips
 * with the desktop tooling.
 */
object MomentsWriter {

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = " " }

    fun toJson(moments: List<Moment>): String =
        pretty.encodeToString(JsonArray.serializer(), toJsonElement(moments))

    fun toJsonElement(moments: List<Moment>): JsonArray = buildJsonArray {
        for (m in moments) add(momentJson(m))
    }

    private fun momentJson(m: Moment): JsonObject = buildJsonObject {
        put("snsId", m.snsId)
        put("authorId", m.authorId)
        put("authorName", m.authorName)
        put("timestamp", m.timestamp)
        put("content", m.content)
        put("mediaList", buildJsonArray {
            for (media in m.mediaList) add(buildJsonObject {
                put("type", media.type)
                put("url", media.url)
                put("title", media.title)
                put("thumb", media.thumb?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
            })
        })
        put("likes", buildJsonArray {
            for (like in m.likes) add(buildJsonObject {
                put("authorId", like.authorId)
                put("authorName", like.authorName)
            })
        })
        put("likeCount", m.likeCount)
        put("comments", buildJsonArray {
            for (c in m.comments) add(buildJsonObject {
                put("isSelf", c.isSelf)
                put("authorId", c.authorId)
                put("authorName", c.authorName)
                put("content", c.content)
            })
        })
        put("commentCount", m.commentCount)
        put("type", m.type)
    }
}
