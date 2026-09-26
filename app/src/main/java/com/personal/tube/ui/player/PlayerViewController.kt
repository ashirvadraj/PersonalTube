package com.personal.tube.ui.player

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.personal.tube.R
import com.personal.tube.data.model.SponsorSegment
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.LayoutPlayerSheetBinding
import com.personal.tube.ui.adapters.VideoAdapter
import com.personal.tube.util.FormatUtils
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import kotlinx.coroutines.launch

class PlayerViewController(
    private val activity: AppCompatActivity,
    private val binding: LayoutPlayerSheetBinding,
    private val repository: VideoRepository,
    private val onVideoSelect: (VideoItem) -> Unit
) {

    private var youTubePlayer: YouTubePlayer? = null
    private var isPlayerInitialized = false
    private var isPlayingState = false
    private var currentVideo: VideoItem? = null
    private var pendingVideoToPlay: VideoItem? = null
    private var isRepeatEnabled = false
    private var currentSecond = 0f
    private var videoDuration = 0f

    private val currentRelatedVideos = mutableListOf<VideoItem>()
    private val sponsorSegments = mutableListOf<SponsorSegment>()

    private val relatedAdapter = VideoAdapter(
        onVideoClick = { video ->
            onVideoSelect(video)
        }
    )

    init {
        setupListeners()
        setupRelatedRecycler()
    }

    private fun setupRelatedRecycler() {
        binding.rvRelatedVideos.layoutManager = LinearLayoutManager(activity)
        binding.rvRelatedVideos.adapter = relatedAdapter
    }

    private fun setupListeners() {
        // Collapse & Expand
        binding.btnCollapsePlayer.setOnClickListener {
            collapseToMiniPlayer()
        }

        binding.layoutMiniPlayer.setOnClickListener {
            expandToFullPlayer()
        }

        binding.btnMiniPlayPause.setOnClickListener {
            if (isPlayingState) {
                youTubePlayer?.pause()
            } else {
                youTubePlayer?.play()
            }
        }

        binding.btnMiniClose.setOnClickListener {
            closePlayer()
        }

        // PiP Button
        binding.btnEnterPip.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity.enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().build()
                )
            } else {
                Toast.makeText(activity, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
            }
        }

        // Background Audio Toggle
        binding.btnToggleBackgroundAudio.setOnClickListener {
            Toast.makeText(activity, "Background audio playback is active", Toast.LENGTH_SHORT).show()
        }

        // Loop / Repeat Toggle
        binding.btnToggleRepeat.setOnClickListener {
            isRepeatEnabled = !isRepeatEnabled
            if (isRepeatEnabled) {
                binding.btnToggleRepeat.setColorFilter(activity.getColor(R.color.yt_green))
                Toast.makeText(activity, "Repeat Mode: ON (Looping song)", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnToggleRepeat.setColorFilter(activity.getColor(R.color.yt_white))
                Toast.makeText(activity, "Repeat Mode: OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // Description Expand
        binding.cardDescription.setOnClickListener {
            val isSingle = binding.tvDetailDescription.maxLines == 3
            binding.tvDetailDescription.maxLines = if (isSingle) 100 else 3
        }

        // Share with Exact Timestamp
        binding.btnDetailShare.setOnClickListener {
            currentVideo?.let { video ->
                val currentSec = currentSecond.toInt()
                val shareUrl = if (currentSec > 5) "https://youtu.be/${video.id}?t=${currentSec}s" else "https://youtu.be/${video.id}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, video.title)
                    putExtra(Intent.EXTRA_TEXT, "${video.title}\n$shareUrl")
                }
                activity.startActivity(Intent.createChooser(intent, "Share video"))
            }
        }

        // Subscribe Toggle
        binding.btnDetailSubscribe.setOnClickListener {
            currentVideo?.let { video ->
                activity.lifecycleScope.launch {
                    val isSub = repository.toggleSubscription(
                        channelId = video.channelId.ifBlank { video.channelTitle },
                        channelTitle = video.channelTitle,
                        avatarUrl = video.channelAvatarUrl
                    )
                    updateSubscribeButton(isSub)
                }
            }
        }

        // Bookmark Toggle
        binding.btnDetailBookmark.setOnClickListener {
            currentVideo?.let { video ->
                activity.lifecycleScope.launch {
                    val isBookmarked = repository.toggleBookmark(video)
                    binding.tvDetailBookmark.text = if (isBookmarked) "Saved" else "Save"
                    Toast.makeText(
                        activity,
                        if (isBookmarked) "Saved to Watch Later" else "Removed from Watch Later",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun playVideo(video: VideoItem) {
        currentVideo = video
        pendingVideoToPlay = video
        expandToFullPlayer()

        // Populate basic metadata immediately
        binding.tvDetailTitle.text = video.title
        binding.tvMiniTitle.text = video.title
        binding.tvDetailChannelName.text = video.channelTitle
        binding.tvMiniChannel.text = video.channelTitle
        binding.tvDetailStats.text = "${video.viewCountFormatted}${if (video.publishedTime.isNotBlank()) " · ${video.publishedTime}" else ""}"
        binding.tvDetailDescription.text = video.description.ifBlank { "No description available." }

        Glide.with(activity).load(video.thumbnailUrl).into(binding.ivMiniThumbnail)
        if (video.channelAvatarUrl.isNotBlank()) {
            Glide.with(activity).load(video.channelAvatarUrl).circleCrop().into(binding.ivDetailChannelAvatar)
        }

        // Check subscription status
        activity.lifecycleScope.launch {
            val isSub = repository.isSubscribedLive(video.channelId.ifBlank { video.channelTitle }).value ?: false
            updateSubscribeButton(isSub)
        }

        binding.playerBufferingSpinner.visibility = View.VISIBLE

        // Start playback
        if (isPlayerInitialized && youTubePlayer != null) {
            youTubePlayer?.loadVideo(video.id, 0f)
            youTubePlayer?.play()
        } else {
            initYouTubePlayer(video)
        }

        // Log into Watch History Room DB
        activity.lifecycleScope.launch {
            repository.logHistory(video)
        }

        // Fetch Return YouTube Dislike, SponsorBlock, and related videos concurrently
        activity.lifecycleScope.launch {
            launch {
                val ryd = repository.getRyd(video.id)
                binding.tvDetailLikes.text = FormatUtils.formatViews(ryd.likes).replace(" views", "")
                binding.tvDetailDislikes.text = FormatUtils.formatViews(ryd.dislikes).replace(" views", "")
            }

            launch {
                val related = repository.getFeed("Trending")
                val filtered = related.filter { it.id != video.id }
                currentRelatedVideos.clear()
                currentRelatedVideos.addAll(filtered)
                relatedAdapter.submitList(filtered)
            }

            val sponsors = repository.getSponsors(video.id)
            sponsorSegments.clear()
            sponsorSegments.addAll(sponsors)
        }
    }

    private fun initYouTubePlayer(initialVideo: VideoItem) {
        if (isPlayerInitialized) return

        activity.lifecycle.addObserver(binding.youtubePlayerView)

        val options = IFramePlayerOptions.Builder()
            .controls(1)
            .rel(0)
            .ivLoadPolicy(3)
            .build()

        binding.youtubePlayerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                youTubePlayer = player
                isPlayerInitialized = true
                val target = pendingVideoToPlay ?: currentVideo ?: initialVideo
                player.loadVideo(target.id, 0f)
                player.play()
            }

            override fun onStateChange(player: YouTubePlayer, state: PlayerConstants.PlayerState) {
                when (state) {
                    PlayerConstants.PlayerState.PLAYING -> {
                        isPlayingState = true
                        binding.playerBufferingSpinner.visibility = View.GONE
                        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_pause)
                    }
                    PlayerConstants.PlayerState.PAUSED -> {
                        isPlayingState = false
                        binding.playerBufferingSpinner.visibility = View.GONE
                        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_play)
                    }
                    PlayerConstants.PlayerState.BUFFERING -> {
                        binding.playerBufferingSpinner.visibility = View.VISIBLE
                    }
                    PlayerConstants.PlayerState.ENDED -> {
                        isPlayingState = false
                        binding.playerBufferingSpinner.visibility = View.GONE
                        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_play)
                        onPlaybackEnded()
                    }
                    else -> {}
                }
            }

            override fun onCurrentSecond(player: YouTubePlayer, second: Float) {
                currentSecond = second
                checkSponsorSegments(second)
            }

            override fun onVideoDuration(player: YouTubePlayer, duration: Float) {
                videoDuration = duration
            }

            override fun onError(player: YouTubePlayer, error: PlayerConstants.PlayerError) {
                binding.playerBufferingSpinner.visibility = View.GONE
                currentVideo?.let { v ->
                    player.cueVideo(v.id, 0f)
                }
            }
        }, options)
    }

    private fun checkSponsorSegments(second: Float) {
        if (sponsorSegments.isNotEmpty()) {
            for (seg in sponsorSegments) {
                if (second >= seg.start && second < seg.end) {
                    youTubePlayer?.seekTo(seg.end.toFloat())
                    Toast.makeText(activity, "Skipped sponsor segment", Toast.LENGTH_SHORT).show()
                    break
                }
            }
        }
    }

    private fun updateSubscribeButton(isSubscribed: Boolean) {
        if (isSubscribed) {
            binding.btnDetailSubscribe.text = activity.getString(R.string.subscribed)
            binding.btnDetailSubscribe.setBackgroundColor(activity.getColor(R.color.yt_chip_background))
            binding.btnDetailSubscribe.setTextColor(activity.getColor(R.color.yt_white))
        } else {
            binding.btnDetailSubscribe.text = activity.getString(R.string.subscribe)
            binding.btnDetailSubscribe.setBackgroundColor(activity.getColor(R.color.yt_white))
            binding.btnDetailSubscribe.setTextColor(activity.getColor(R.color.yt_chip_selected_text))
        }
    }

    fun expandToFullPlayer() {
        binding.layoutExpandedPlayer.visibility = View.VISIBLE
        binding.layoutMiniPlayer.visibility = View.GONE
        binding.root.visibility = View.VISIBLE
    }

    fun collapseToMiniPlayer() {
        binding.layoutExpandedPlayer.visibility = View.GONE
        binding.layoutMiniPlayer.visibility = View.VISIBLE
        binding.root.visibility = View.VISIBLE
    }

    fun closePlayer() {
        youTubePlayer?.pause()
        binding.root.visibility = View.GONE
    }

    private fun onPlaybackEnded() {
        if (isRepeatEnabled) {
            youTubePlayer?.seekTo(0f)
            youTubePlayer?.play()
        } else if (currentRelatedVideos.isNotEmpty()) {
            val nextVideo = currentRelatedVideos.removeAt(0)
            Toast.makeText(activity, "Auto-playing next: ${nextVideo.title}", Toast.LENGTH_SHORT).show()
            playVideo(nextVideo)
        }
    }

    fun destroy() {
        binding.youtubePlayerView.release()
    }
}
