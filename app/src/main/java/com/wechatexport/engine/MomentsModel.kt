package com.wechatexport.engine

/**
 * Data model for one WeChat 朋友圈 (Moments) post.
 * Field names mirror the JSON emitted by the Go engine / snskit.py so exports
 * round-trip cleanly.
 */
data class MediaItem(
    val type: Int,          // 2 = image(qpic), 6 = web/note link
    val url: String,
    val title: String,
    val thumb: String?
)

data class Comment(
    val isSelf: Boolean,
    val authorId: String,
    val authorName: String,
    val content: String
)

data class Like(
    val authorId: String,
    val authorName: String
)

data class Moment(
    val snsId: Long,
    val authorId: String,
    val authorName: String,
    val timestamp: Long,
    val content: String,
    val mediaList: List<MediaItem>,
    val likes: List<Like>,
    val likeCount: Int,
    val comments: List<Comment>,
    val commentCount: Int,
    val type: Int
)
