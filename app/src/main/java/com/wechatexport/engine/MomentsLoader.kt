package com.wechatexport.engine

import kotlinx.serialization.json.*

/**
 * Tolerant loader: parses the Moments JSON emitted by the Go engine / snskit.py.
 * Accepts either a top-level array or {"moments": [...]}, and normalises the
 * `likes` field which may appear as bare strings or as objects.
 */
object MomentsLoader {

    fun load(json: String): List<Moment> {
        val root = Json.parseToJsonElement(json)
        val arr: JsonArray = when {
            root is JsonArray -> root
            root is JsonObject && root["moments"] is JsonArray ->
                root["moments"] as JsonArray
            else -> throw IllegalArgumentException("Moments JSON must be an array or {\"moments\": [...]}")
        }
        return arr.map { parseMoment(it.jsonObject) }
    }

    private fun parseMoment(o: JsonObject): Moment = Moment(
        snsId = o["snsId"]?.jsonPrimitive?.longOrNull ?: 0L,
        authorId = o["authorId"]?.jsonPrimitive?.contentOrNull ?: "",
        authorName = o["authorName"]?.jsonPrimitive?.contentOrNull ?: "",
        timestamp = o["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L,
        content = o["content"]?.jsonPrimitive?.contentOrNull ?: "",
        mediaList = (o["mediaList"] as? JsonArray)?.map { parseMedia(it.jsonObject) } ?: emptyList(),
        likes = parseLikes(o["likes"]),
        likeCount = o["likeCount"]?.jsonPrimitive?.intOrNull ?: 0,
        comments = (o["comments"] as? JsonArray)?.map { parseComment(it.jsonObject) } ?: emptyList(),
        commentCount = o["commentCount"]?.jsonPrimitive?.intOrNull ?: 0,
        type = o["type"]?.jsonPrimitive?.intOrNull ?: 0
    )

    private fun parseMedia(o: JsonObject) = MediaItem(
        type = o["type"]?.jsonPrimitive?.intOrNull ?: 0,
        url = o["url"]?.jsonPrimitive?.contentOrNull ?: "",
        title = o["title"]?.jsonPrimitive?.contentOrNull ?: "",
        thumb = o["thumb"]?.jsonPrimitive?.contentOrNull
    )

    private fun parseComment(o: JsonObject) = Comment(
        isSelf = o["isSelf"]?.jsonPrimitive?.booleanOrNull ?: false,
        authorId = o["authorId"]?.jsonPrimitive?.contentOrNull ?: "",
        authorName = o["authorName"]?.jsonPrimitive?.contentOrNull ?: "",
        content = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
    )

    private fun parseLikes(element: JsonElement?): List<Like> {
        if (element == null || element !is JsonArray) return emptyList()
        return element.map { e ->
            when {
                e is JsonObject -> Like(
                    authorId = e["authorId"]?.jsonPrimitive?.contentOrNull ?: "",
                    authorName = e["authorName"]?.jsonPrimitive?.contentOrNull ?: ""
                )
                e is JsonPrimitive && e.isString -> Like(authorId = "", authorName = e.content)
                else -> Like("", "")
            }
        }
    }
}
