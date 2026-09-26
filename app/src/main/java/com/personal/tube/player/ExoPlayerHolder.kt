package com.personal.tube.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.personal.tube.data.model.SponsorSegment
import com.personal.tube.data.model.StreamInfo
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.model.VideoStreamFormat
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

object ExoPlayerHolder {

    private var exoPlayer: ExoPlayer? = null
    var currentVideo: VideoItem? = null
        private set
    var currentStreamInfo: StreamInfo? = null
        private set
    var currentFormat: VideoStreamFormat? = null
        private set
    var isBackgroundAudioEnabled: Boolean = false

    private val sponsorSegments = CopyOnWriteArrayList<SponsorSegment>()
    private var scope: CoroutineScope? = null

    interface PlayerStateListener {
        fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean)
        fun onPositionDiscontinuity(positionMs: Long, durationMs: Long)
        fun onVideoChanged(video: VideoItem)
        fun onPlaybackEnded() {}
    }

    private val listeners = CopyOnWriteArrayList<PlayerStateListener>()

    fun addListener(listener: PlayerStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: PlayerStateListener) {
        listeners.remove(listener)
    }

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val player = ExoPlayer.Builder(context.applicationContext)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build()

            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    notifyState(isPlaying, player.playbackState == Player.STATE_BUFFERING)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val isBuffering = playbackState == Player.STATE_BUFFERING
                    notifyState(player.isPlaying, isBuffering)
                    if (playbackState == Player.STATE_ENDED) {
                        listeners.forEach { it.onPlaybackEnded() }
                    }
                }
            })

            exoPlayer = player
            startPositionTracker()
        }
        return exoPlayer!!
    }

    fun playStream(
        context: Context,
        video: VideoItem,
        streamInfo: StreamInfo,
        format: VideoStreamFormat,
        sponsors: List<SponsorSegment> = emptyList()
    ) {
        val player = getPlayer(context)
        currentVideo = video
        currentStreamInfo = streamInfo
        currentFormat = format
        sponsorSegments.clear()
        sponsorSegments.addAll(sponsors)

        val mediaItem = MediaItem.fromUri(format.url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        listeners.forEach { it.onVideoChanged(video) }
    }

    fun togglePlayPause(context: Context) {
        val player = getPlayer(context)
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekRelative(context: Context, offsetMs: Long) {
        val player = getPlayer(context)
        val target = (player.currentPosition + offsetMs).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target)
    }

    fun seekTo(context: Context, positionMs: Long) {
        val player = getPlayer(context)
        player.seekTo(positionMs)
    }

    fun setPlaybackSpeed(context: Context, speed: Float) {
        val player = getPlayer(context)
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun switchQuality(context: Context, format: VideoStreamFormat) {
        val player = getPlayer(context)
        val currentPos = player.currentPosition
        val playWhenReady = player.playWhenReady
        currentFormat = format

        val mediaItem = MediaItem.fromUri(format.url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(currentPos)
        player.playWhenReady = playWhenReady
    }

    private fun startPositionTracker() {
        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Main + Job())
        scope?.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0)
                    listeners.forEach { it.onPositionDiscontinuity(pos, dur) }

                    // SponsorBlock auto-skipping
                    if (sponsorSegments.isNotEmpty()) {
                        val currentSeconds = pos / 1000.0
                        for (seg in sponsorSegments) {
                            if (currentSeconds >= seg.start && currentSeconds < seg.end) {
                                val seekTargetMs = (seg.end * 1000).toLong()
                                player.seekTo(seekTargetMs)
                                break
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun notifyState(isPlaying: Boolean, isBuffering: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying, isBuffering) }
    }

    fun release() {
        scope?.cancel()
        scope = null
        exoPlayer?.release()
        exoPlayer = null
        currentVideo = null
        currentStreamInfo = null
    }
}
