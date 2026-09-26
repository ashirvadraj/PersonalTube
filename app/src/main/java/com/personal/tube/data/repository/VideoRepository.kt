package com.personal.tube.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.personal.tube.data.local.AppDatabase
import com.personal.tube.data.local.entities.BookmarkEntity
import com.personal.tube.data.local.entities.DownloadEntity
import com.personal.tube.data.local.entities.SubscriptionEntity
import com.personal.tube.data.local.entities.WatchHistoryEntity
import com.personal.tube.data.model.RydStats
import com.personal.tube.data.model.SponsorSegment
import com.personal.tube.data.model.StreamInfo
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.network.YoutubeExtractorService

class VideoRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val extractor = YoutubeExtractorService()

    // Remote APIs
    suspend fun getFeed(category: String): List<VideoItem> {
        return extractor.getTrendingVideos(category)
    }

    suspend fun getShorts(): List<com.personal.tube.data.model.ShortItem> {
        return extractor.getShorts()
    }

    suspend fun getComments(videoId: String): List<com.personal.tube.data.model.CommentItem> {
        return extractor.getComments(videoId)
    }

    suspend fun search(query: String): List<VideoItem> {
        return extractor.searchVideos(query)
    }

    suspend fun getSuggestions(query: String): List<String> {
        return extractor.getSearchSuggestions(query)
    }

    suspend fun resolveStream(videoId: String): StreamInfo? {
        return extractor.resolveStreamInfo(videoId)
    }

    suspend fun getRyd(videoId: String): RydStats {
        return extractor.getReturnYoutubeDislike(videoId)
    }

    suspend fun getSponsors(videoId: String): List<SponsorSegment> {
        return extractor.getSponsorBlockSegments(videoId)
    }

    // Local Watch History
    val watchHistory: LiveData<List<WatchHistoryEntity>> = db.historyDao().getAllHistory()

    suspend fun logHistory(video: VideoItem, positionMs: Long = 0L) {
        db.historyDao().insertOrUpdate(
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title,
                channelTitle = video.channelTitle,
                thumbnailUrl = video.thumbnailUrl,
                durationFormatted = video.durationFormatted,
                durationSeconds = video.durationSeconds,
                watchedAt = System.currentTimeMillis(),
                playbackPositionMs = positionMs
            )
        )
    }

    suspend fun clearHistory() {
        db.historyDao().clearAll()
    }

    // Subscriptions
    val subscriptions: LiveData<List<SubscriptionEntity>> = db.subscriptionDao().getAllSubscriptions()

    fun isSubscribedLive(channelId: String): LiveData<Boolean> {
        return db.subscriptionDao().isSubscribedLive(channelId)
    }

    suspend fun toggleSubscription(channelId: String, channelTitle: String, avatarUrl: String): Boolean {
        val subscribed = db.subscriptionDao().isSubscribed(channelId)
        if (subscribed) {
            db.subscriptionDao().delete(channelId)
            return false
        } else {
            db.subscriptionDao().insert(
                SubscriptionEntity(
                    channelId = channelId,
                    channelTitle = channelTitle,
                    avatarUrl = avatarUrl
                )
            )
            return true
        }
    }

    // Bookmarks / Watch Later
    val bookmarks: LiveData<List<BookmarkEntity>> = db.bookmarkDao().getAllBookmarks()

    fun isBookmarkedLive(videoId: String): LiveData<Boolean> {
        return db.bookmarkDao().isBookmarkedLive(videoId)
    }

    suspend fun toggleBookmark(video: VideoItem): Boolean {
        val isSaved = db.bookmarkDao().isBookmarked(video.id)
        if (isSaved) {
            db.bookmarkDao().delete(video.id)
            return false
        } else {
            db.bookmarkDao().insert(
                BookmarkEntity(
                    videoId = video.id,
                    title = video.title,
                    channelTitle = video.channelTitle,
                    thumbnailUrl = video.thumbnailUrl,
                    durationFormatted = video.durationFormatted,
                    durationSeconds = video.durationSeconds
                )
            )
            return true
        }
    }

    // Offline Downloads
    val downloads: LiveData<List<DownloadEntity>> = db.downloadDao().getAllDownloads()

    suspend fun saveDownload(download: DownloadEntity) {
        db.downloadDao().insert(download)
    }

    suspend fun deleteDownload(videoId: String) {
        db.downloadDao().delete(videoId)
    }
}
