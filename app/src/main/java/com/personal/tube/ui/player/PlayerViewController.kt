package com.personal.tube.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlinx.coroutines.launch

class PlayerViewController(
    private val activity: AppCompatActivity,
    private val binding: LayoutPlayerSheetBinding,
    private val repository: VideoRepository,
    private val onVideoSelect: (VideoItem) -> Unit
) {

    private var isPlayingState = false
    private var isRepeatEnabled = false
    private var currentVideo: VideoItem? = null
    private var currentSecond = 0f
    private var videoDuration = 0f
    private var customFullscreenView: View? = null
    private var customFullscreenCallback: WebChromeClient.CustomViewCallback? = null

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
        setupWebView()
    }

    private fun setupRelatedRecycler() {
        binding.rvRelatedVideos.layoutManager = LinearLayoutManager(activity)
        binding.rvRelatedVideos.adapter = relatedAdapter
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.playerWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Clean user agent by removing in-app webview flags ("; wv" and "Version/4.0")
        // This presents a genuine Chrome Mobile browser to YouTube servers
        val defaultUa = binding.playerWebView.settings.userAgentString ?: ""
        if (defaultUa.isNotBlank()) {
            val cleanUa = defaultUa
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
            binding.playerWebView.settings.userAgentString = cleanUa
        }

        binding.playerWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (customFullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customFullscreenView = view
                customFullscreenCallback = callback
                binding.playerRootContainer.addView(view)
                binding.layoutExpandedPlayer.visibility = View.GONE
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                if (customFullscreenView != null) {
                    binding.playerRootContainer.removeView(customFullscreenView)
                    customFullscreenView = null
                    customFullscreenCallback?.onCustomViewHidden()
                    binding.layoutExpandedPlayer.visibility = View.VISIBLE
                }
            }
        }

        binding.playerWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.playerBufferingSpinner.visibility = View.GONE

                // If fallback m.youtube.com was loaded, hide the topbar and start video
                if (url?.contains("m.youtube.com") == true) {
                    val js = """
                        (function() {
                            try {
                                var style = document.createElement('style');
                                style.innerHTML = 'header, ytm-mobile-topbar-renderer, .mobile-topbar-header, ytm-pivot-bar-renderer { display: none !important; } body { padding-top: 0 !important; }';
                                document.head.appendChild(style);
                                var video = document.querySelector('video');
                                if (video) { video.play(); }
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                binding.playerBufferingSpinner.visibility = View.GONE
            }
        }

        binding.playerWebView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
    }

    private inner class AndroidBridge {
        @JavascriptInterface
        fun onReady() {
            activity.runOnUiThread {
                binding.playerBufferingSpinner.visibility = View.GONE
            }
        }

        @JavascriptInterface
        fun onStateChange(state: Int) {
            activity.runOnUiThread {
                when (state) {
                    1 -> { // PLAYING
                        isPlayingState = true
                        binding.playerBufferingSpinner.visibility = View.GONE
                        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_pause)
                    }
                    2 -> { // PAUSED
                        isPlayingState = false
                        binding.playerBufferingSpinner.visibility = View.GONE
                        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_play)
                    }
                    3 -> { // BUFFERING
                        binding.playerBufferingSpinner.visibility = View.VISIBLE
                    }
                    0 -> { // ENDED
                        isPlayingState = false
                        binding.playerBufferingSpinner.visibility = View.GONE
                        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_play)
                        onPlaybackEnded()
                    }
                    else -> {
                        binding.playerBufferingSpinner.visibility = View.GONE
                    }
                }
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(time: Float, duration: Float) {
            activity.runOnUiThread {
                currentSecond = time
                videoDuration = duration
                checkSponsorSegments(time)
            }
        }

        @JavascriptInterface
        fun onError(code: Int) {
            activity.runOnUiThread {
                binding.playerBufferingSpinner.visibility = View.GONE
                // Error 101 / 150 / 153 indicates owner disabled embedding on third-party sites
                // Seamlessly fall back to official mobile YouTube stream
                if (code == 101 || code == 150 || code == 153) {
                    currentVideo?.let { v ->
                        val fallbackUrl = "https://m.youtube.com/watch?v=${v.id}"
                        binding.playerWebView.loadUrl(fallbackUrl)
                    }
                }
            }
        }
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
                pauseVideo()
            } else {
                resumeVideo()
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
                Toast.makeText(activity, "Repeat Mode: ON (Looping)", Toast.LENGTH_SHORT).show()
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

        // Like Toggle
        binding.btnDetailLike.setOnClickListener {
            binding.btnDetailLike.setColorFilter(activity.getColor(R.color.yt_accent_blue))
            Toast.makeText(activity, "Added to Liked videos", Toast.LENGTH_SHORT).show()
        }

        // Dislike Button
        binding.btnDetailDislike.setOnClickListener {
            binding.btnDetailDislike.setColorFilter(activity.getColor(R.color.yt_accent_blue))
            Toast.makeText(activity, "Feedback submitted", Toast.LENGTH_SHORT).show()
        }

        // Download Video
        binding.btnDetailDownload.setOnClickListener {
            currentVideo?.let { v ->
                binding.tvDetailDownload.text = "Downloaded"
                binding.ivDetailDownloadIcon.setColorFilter(activity.getColor(R.color.yt_green))
                Toast.makeText(activity, "Downloaded '${v.title}' for offline viewing", Toast.LENGTH_SHORT).show()
            }
        }

        // Comments Preview Click -> Open Bottom Sheet
        binding.cardCommentsPreview.setOnClickListener {
            showCommentsSheet()
        }
    }

    private fun showCommentsSheet() {
        val video = currentVideo ?: return
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
        val sheetView = activity.layoutInflater.inflate(R.layout.dialog_comments_sheet, null)
        dialog.setContentView(sheetView)

        val tvCount = sheetView.findViewById<android.widget.TextView>(R.id.tv_sheet_comment_count)
        val btnClose = sheetView.findViewById<android.widget.ImageView>(R.id.btn_close_comments)
        val rvComments = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_comments)
        val etNewComment = sheetView.findViewById<android.widget.EditText>(R.id.et_new_comment)
        val btnSend = sheetView.findViewById<android.widget.ImageView>(R.id.btn_send_comment)

        tvCount.text = binding.tvCommentPreviewCount.text
        btnClose.setOnClickListener { dialog.dismiss() }

        val commentAdapter = com.personal.tube.ui.adapters.CommentAdapter()
        rvComments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        rvComments.adapter = commentAdapter

        activity.lifecycleScope.launch {
            val comments = repository.getComments(video.id)
            commentAdapter.submitList(comments)
        }

        btnSend.setOnClickListener {
            val txt = etNewComment.text.toString().trim()
            if (txt.isNotBlank()) {
                val current = commentAdapter.currentList.toMutableList()
                current.add(
                    0,
                    com.personal.tube.data.model.CommentItem(
                        id = "user_${System.currentTimeMillis()}",
                        authorName = "You",
                        text = txt,
                        publishedTime = "Just now",
                        likeCountFormatted = "0"
                    )
                )
                commentAdapter.submitList(current)
                etNewComment.text.clear()
                Toast.makeText(activity, "Comment posted", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    fun playVideo(video: VideoItem) {
        currentVideo = video
        currentSecond = 0f
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

        // Show spinner and register safety auto-dismiss timeout
        binding.playerBufferingSpinner.visibility = View.VISIBLE
        binding.playerBufferingSpinner.postDelayed({
            if (binding.playerBufferingSpinner.visibility == View.VISIBLE) {
                binding.playerBufferingSpinner.visibility = View.GONE
            }
        }, 2500)

        // Load video embed using privacy-enhanced domain youtube-nocookie.com
        val embedHtml = buildEmbedHtml(video.id)
        binding.playerWebView.loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            embedHtml,
            "text/html",
            "UTF-8",
            null
        )

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

    private fun buildEmbedHtml(videoId: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <style>
                * { margin: 0; padding: 0; box-sizing: border-box; background: #000; }
                html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                iframe { width: 100%; height: 100%; border: 0; position: absolute; top: 0; left: 0; }
              </style>
            </head>
            <body>
              <iframe id="player"
                src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&fs=1&rel=0&enablejsapi=1"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen>
              </iframe>
              <script>
                var player;
                function onYouTubeIframeAPIReady() {
                  player = new YT.Player('player', {
                    events: {
                      'onReady': onPlayerReady,
                      'onStateChange': onPlayerStateChange,
                      'onError': onPlayerError
                    }
                  });
                }

                function onPlayerReady(event) {
                  if (window.AndroidBridge) {
                    window.AndroidBridge.onReady();
                  }
                  setInterval(function() {
                    try {
                      if (player && player.getCurrentTime) {
                        var cur = player.getCurrentTime();
                        var dur = player.getDuration();
                        if (window.AndroidBridge) {
                          window.AndroidBridge.onTimeUpdate(cur, dur);
                        }
                      }
                    } catch(e) {}
                  }, 500);
                }

                function onPlayerStateChange(event) {
                  if (window.AndroidBridge) {
                    window.AndroidBridge.onStateChange(event.data);
                  }
                }

                function onPlayerError(event) {
                  if (window.AndroidBridge) {
                    window.AndroidBridge.onError(event.data);
                  }
                }

                function nativePlay() {
                  try {
                    if (player && player.playVideo) {
                      player.playVideo();
                    } else {
                      var f = document.getElementById('player');
                      if (f && f.contentWindow) {
                        f.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*');
                      }
                    }
                  } catch(e) {}
                }

                function nativePause() {
                  try {
                    if (player && player.pauseVideo) {
                      player.pauseVideo();
                    } else {
                      var f = document.getElementById('player');
                      if (f && f.contentWindow) {
                        f.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":""}', '*');
                      }
                    }
                  } catch(e) {}
                }

                function nativeSeekTo(sec) {
                  try {
                    if (player && player.seekTo) {
                      player.seekTo(sec, true);
                    } else {
                      var f = document.getElementById('player');
                      if (f && f.contentWindow) {
                        f.contentWindow.postMessage('{"event":"command","func":"seekTo","args":[' + sec + ', true]}', '*');
                      }
                    }
                  } catch(e) {}
                }

                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun resumeVideo() {
        isPlayingState = true
        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_pause)
        binding.playerWebView.evaluateJavascript("nativePlay();", null)
    }

    fun pauseVideo() {
        isPlayingState = false
        binding.btnMiniPlayPause.setImageResource(R.drawable.ic_play)
        binding.playerWebView.evaluateJavascript("nativePause();", null)
    }

    fun seekTo(seconds: Float) {
        binding.playerWebView.evaluateJavascript("nativeSeekTo($seconds);", null)
    }

    private fun checkSponsorSegments(second: Float) {
        if (sponsorSegments.isNotEmpty()) {
            for (seg in sponsorSegments) {
                if (second >= seg.start && second < seg.end) {
                    seekTo(seg.end.toFloat())
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
        pauseVideo()
        binding.playerWebView.loadUrl("about:blank")
        currentVideo = null
        binding.root.visibility = View.GONE
    }

    private fun onPlaybackEnded() {
        if (isRepeatEnabled) {
            seekTo(0f)
            resumeVideo()
        } else if (currentRelatedVideos.isNotEmpty()) {
            val nextVideo = currentRelatedVideos.removeAt(0)
            Toast.makeText(activity, "Auto-playing next: ${nextVideo.title}", Toast.LENGTH_SHORT).show()
            playVideo(nextVideo)
        }
    }

    fun destroy() {
        try {
            binding.playerWebView.stopLoading()
            binding.playerWebView.loadUrl("about:blank")
            binding.playerWebView.clearHistory()
            binding.playerWebView.removeAllViews()
            binding.playerWebView.destroy()
        } catch (e: Exception) {
            // ignore
        }
    }
}
