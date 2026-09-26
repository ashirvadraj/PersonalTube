package com.personal.tube.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationFormatted: String,
    val durationSeconds: Long,
    val watchedAt: Long = System.currentTimeMillis(),
    val playbackPositionMs: Long = 0L
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey
    val channelId: String,
    val channelTitle: String,
    val avatarUrl: String,
    val subscribedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationFormatted: String,
    val durationSeconds: Long,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val localFilePath: String,
    val thumbnailUrl: String,
    val durationFormatted: String,
    val fileSizeFormatted: String,
    val isAudioOnly: Boolean,
    val downloadedAt: Long = System.currentTimeMillis()
)
