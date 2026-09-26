package com.personal.tube.data.model

import java.io.Serializable

data class VideoItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val channelId: String = "",
    val channelAvatarUrl: String = "",
    val thumbnailUrl: String = "",
    val durationFormatted: String = "00:00",
    val durationSeconds: Long = 0L,
    val viewCountFormatted: String = "0 views",
    val viewCount: Long = 0L,
    val publishedTime: String = "",
    val description: String = "",
    var directStreamUrl: String? = null,
    val isLive: Boolean = false
) : Serializable

data class StreamInfo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val formats: List<VideoStreamFormat> = emptyList(),
    val hlsUrl: String? = null,
    val dashUrl: String? = null
)

data class VideoStreamFormat(
    val quality: String,
    val resolution: String,
    val url: String,
    val mimeType: String = "video/mp4",
    val bitrate: Long = 0L,
    val isAudioOnly: Boolean = false
)

data class SponsorSegment(
    val category: String,
    val start: Double,
    val end: Double
)

data class RydStats(
    val likes: Long = 0L,
    val dislikes: Long = 0L,
    val rating: Double = 5.0
)

data class ChannelInfo(
    val id: String,
    val title: String,
    val avatarUrl: String,
    val subscriberCount: String = "",
    val videoCount: Long = 0L
)

data class CommentItem(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String = "",
    val text: String,
    val publishedTime: String = "2 hours ago",
    val likeCountFormatted: String = "1.2K",
    var isLiked: Boolean = false
)

data class ShortItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val channelAvatarUrl: String = "",
    val likeCountFormatted: String = "240K",
    val commentCountFormatted: String = "1.8K",
    var isSubscribed: Boolean = false,
    var isLiked: Boolean = false
)

