package com.personal.tube.ui.player

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.personal.tube.R
import com.personal.tube.data.model.StreamInfo
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.LayoutPlayerSheetBinding
import com.personal.tube.player.ExoPlayerHolder
import com.personal.tube.player.PlaybackService
import com.personal.tube.ui.adapters.VideoAdapter
import com.personal.tube.util.DownloadHelper
import com.personal.tube.util.FormatUtils
import kotlinx.coroutines.launch

class PlayerViewController(
    private val activity: AppCompatActivity,
    private val binding: LayoutPlayerSheetBinding,
    private val repository: VideoRepository,
    private val onVideoSelect: (VideoItem) -> Unit
) : ExoPlayerHolder.PlayerStateListener {

    private var currentVideo: VideoItem? = null
    private var streamInfo: StreamInfo? = null
    private var isExpanded = true
    private var isUserTrackingSeekBar = false

    private val relatedAdapter = VideoAdapter(
        onVideoClick = { video ->
            onVideoSelect(video)
        }
    )

    init {
        setupPlayerView()
        setupListeners()
        setupRelatedRecycler()
        ExoPlayerHolder.addListener(this)
    }

    private fun setupPlayerView() {
        val player = ExoPlayerHolder.getPlayer(activity)
        binding.playerView.player = player
    }

    private fun setupRelatedRecycler() {
        binding.rvRelatedVideos.layoutManager = LinearLayoutManager(activity)
        binding.rvRelatedVideos.adapter = relatedAdapter
    }

    private fun setupListeners() {
        // Full player controls
        binding.btnPlayPause.setOnClickListener {
            ExoPlayerHolder.togglePlayPause(activity)
        }

        binding.btnRewind10.setOnClickListener {
            ExoPlayerHolder.seekRelative(activity, -10000)
        }

        binding.btnForward10.setOnClickListener {
            ExoPlayerHolder.seekRelative(activity, 10000)
        }

        // Collapse & Expand
        binding.btnCollapsePlayer.setOnClickListener {
            collapseToMiniPlayer()
        }

        binding.layoutMiniPlayer.setOnClickListener {
            expandToFullPlayer()
        }

        binding.btnMiniPlayPause.setOnClickListener {
            ExoPlayerHolder.togglePlayPause(activity)
        }

        binding.btnMiniClose.setOnClickListener {
            closePlayer()
        }

        // PiP
        binding.btnEnterPip.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity.enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().build()
                )
            } else {
                Toast.makeText(activity, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
            }
        }

        // Background Audio
        binding.btnToggleBackgroundAudio.setOnClickListener {
            ExoPlayerHolder.isBackgroundAudioEnabled = !ExoPlayerHolder.isBackgroundAudioEnabled
            if (ExoPlayerHolder.isBackgroundAudioEnabled) {
                PlaybackService.start(activity)
                binding.btnToggleBackgroundAudio.setColorFilter(activity.getColor(R.color.yt_green))
                Toast.makeText(activity, "Background playback enabled", Toast.LENGTH_SHORT).show()
            } else {
                PlaybackService.stop(activity)
                binding.btnToggleBackgroundAudio.setColorFilter(activity.getColor(R.color.yt_white))
                Toast.makeText(activity, "Background playback disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Quality Switcher
        binding.btnSelectQuality.setOnClickListener {
            showQualityDialog()
        }

        // Speed Switcher
        binding.btnSelectSpeed.setOnClickListener {
            showSpeedDialog()
        }

        // Description Expand
        binding.cardDescription.setOnClickListener {
            val isSingle = binding.tvDetailDescription.maxLines == 3
            binding.tvDetailDescription.maxLines = if (isSingle) 100 else 3
        }

        // Share
        binding.btnDetailShare.setOnClickListener {
            currentVideo?.let { video ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, video.title)
                    putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
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

        // Download
        binding.btnDetailDownload.setOnClickListener {
            currentVideo?.let { video ->
                val format = ExoPlayerHolder.currentFormat ?: streamInfo?.formats?.firstOrNull()
                if (format == null) {
                    Toast.makeText(activity, "Stream format not resolved yet", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Toast.makeText(activity, "Downloading ${video.title}...", Toast.LENGTH_LONG).show()
                binding.tvDetailDownload.text = "Saving..."
                activity.lifecycleScope.launch {
                    val success = DownloadHelper.downloadVideo(activity, video, format, repository)
                    if (success) {
                        binding.tvDetailDownload.text = "Saved"
                        binding.ivDetailDownloadIcon.setImageResource(R.drawable.ic_download)
                        binding.ivDetailDownloadIcon.setColorFilter(activity.getColor(R.color.yt_green))
                        Toast.makeText(activity, "Download completed!", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.tvDetailDownload.text = "Download"
                        Toast.makeText(activity, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Seek Bar
        binding.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvPlayerCurrentTime.text = FormatUtils.formatDurationMs(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = false
                seekBar?.let {
                    ExoPlayerHolder.seekTo(activity, it.progress.toLong())
                }
            }
        })
    }

    fun playVideo(video: VideoItem) {
        currentVideo = video
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

        // Fetch stream, RYD, SponsorBlock, and related videos concurrently
        activity.lifecycleScope.launch {
            binding.playerBufferingSpinner.visibility = View.VISIBLE

            // Fetch Return YouTube Dislike
            launch {
                val ryd = repository.getRyd(video.id)
                binding.tvDetailLikes.text = FormatUtils.formatViews(ryd.likes).replace(" views", "")
                binding.tvDetailDislikes.text = FormatUtils.formatViews(ryd.dislikes).replace(" views", "")
            }

            // Fetch related feed
            launch {
                val related = repository.getFeed("Trending")
                relatedAdapter.submitList(related.filter { it.id != video.id })
            }

            // Fetch SponsorBlock
            val sponsors = repository.getSponsors(video.id)

            // Resolve streams
            val resolvedStream = repository.resolveStream(video.id)
            streamInfo = resolvedStream

            if (resolvedStream != null && resolvedStream.formats.isNotEmpty()) {
                val defaultFormat = resolvedStream.formats.firstOrNull { it.quality.contains("720") || it.quality.contains("HD") }
                    ?: resolvedStream.formats.first()

                binding.btnSelectQuality.text = defaultFormat.quality

                ExoPlayerHolder.playStream(
                    context = activity,
                    video = video,
                    streamInfo = resolvedStream,
                    format = defaultFormat,
                    sponsors = sponsors
                )

                // Log into Watch History Room DB
                repository.logHistory(video)
            } else {
                Toast.makeText(activity, "Unable to resolve stream mirror", Toast.LENGTH_SHORT).show()
                binding.playerBufferingSpinner.visibility = View.GONE
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

    private fun showQualityDialog() {
        val formats = streamInfo?.formats ?: return
        if (formats.isEmpty()) return

        val items = formats.map { it.quality }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.quality)
            .setItems(items) { _, which ->
                val selected = formats[which]
                binding.btnSelectQuality.text = selected.quality
                ExoPlayerHolder.switchQuality(activity, selected)
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val values = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        AlertDialog.Builder(activity)
            .setTitle(R.string.speed)
            .setItems(speeds) { _, which ->
                val speed = values[which]
                binding.btnSelectSpeed.text = speeds[which]
                ExoPlayerHolder.setPlaybackSpeed(activity, speed)
            }
            .show()
    }

    fun expandToFullPlayer() {
        isExpanded = true
        binding.layoutExpandedPlayer.visibility = View.VISIBLE
        binding.layoutMiniPlayer.visibility = View.GONE
        binding.root.visibility = View.VISIBLE
    }

    fun collapseToMiniPlayer() {
        isExpanded = false
        binding.layoutExpandedPlayer.visibility = View.GONE
        binding.layoutMiniPlayer.visibility = View.VISIBLE
        binding.root.visibility = View.VISIBLE
    }

    fun closePlayer() {
        ExoPlayerHolder.getPlayer(activity).stop()
        PlaybackService.stop(activity)
        binding.root.visibility = View.GONE
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        binding.playerBufferingSpinner.visibility = if (isBuffering) View.VISIBLE else View.GONE
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(playPauseIcon)
        binding.btnMiniPlayPause.setImageResource(playPauseIcon)
    }

    override fun onPositionDiscontinuity(positionMs: Long, durationMs: Long) {
        if (!isUserTrackingSeekBar) {
            binding.playerSeekBar.max = durationMs.toInt()
            binding.playerSeekBar.progress = positionMs.toInt()
            binding.tvPlayerCurrentTime.text = FormatUtils.formatDurationMs(positionMs)
            binding.tvPlayerTotalTime.text = FormatUtils.formatDurationMs(durationMs)
        }
    }

    override fun onVideoChanged(video: VideoItem) {
        currentVideo = video
    }

    fun destroy() {
        ExoPlayerHolder.removeListener(this)
    }
}
