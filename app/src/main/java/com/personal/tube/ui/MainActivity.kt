package com.personal.tube.ui

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.personal.tube.R
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.ActivityMainBinding
import com.personal.tube.databinding.LayoutPlayerSheetBinding
import com.personal.tube.player.ExoPlayerHolder
import com.personal.tube.ui.home.HomeFragment
import com.personal.tube.ui.library.LibraryFragment
import com.personal.tube.ui.player.PlayerViewController
import com.personal.tube.ui.search.SearchFragment
import com.personal.tube.ui.subscriptions.SubscriptionsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playerBinding: LayoutPlayerSheetBinding
    private lateinit var playerController: PlayerViewController
    private lateinit var repository: VideoRepository

    private val homeFragment by lazy { HomeFragment() }
    private val searchFragment by lazy { SearchFragment() }
    private val subscriptionsFragment by lazy { SubscriptionsFragment() }
    private val libraryFragment by lazy { LibraryFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = VideoRepository(this)
        setupPlayerSheet()
        setupNavigation()
        setupHeader()

        if (savedInstanceState == null) {
            selectNavigationTab(TAB_HOME)
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            val videoId = extractVideoId(data)
            if (!videoId.isNullOrBlank()) {
                val video = VideoItem(
                    id = videoId,
                    title = "Loading YouTube video...",
                    channelTitle = "YouTube",
                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                )
                playVideo(video)
            }
        }
    }

    private fun extractVideoId(uri: Uri): String? {
        return when {
            uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
            uri.host?.contains("youtube.com") == true -> uri.getQueryParameter("v")
            else -> null
        }
    }

    private fun setupPlayerSheet() {
        playerBinding = LayoutPlayerSheetBinding.inflate(layoutInflater, binding.playerSheetContainer, true)
        playerController = PlayerViewController(
            activity = this,
            binding = playerBinding,
            repository = repository,
            onVideoSelect = { video ->
                playVideo(video)
            }
        )
    }

    private fun setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchFragment(homeFragment)
                R.id.nav_subscriptions -> switchFragment(subscriptionsFragment)
                R.id.nav_library -> switchFragment(libraryFragment)
            }
            true
        }
    }

    private fun setupHeader() {
        binding.btnHeaderSearch.setOnClickListener {
            switchFragment(searchFragment)
        }

        binding.btnHeaderSettings.setOnClickListener {
            showAboutDialog()
        }
    }

    fun selectNavigationTab(tabId: Int) {
        when (tabId) {
            TAB_HOME -> {
                binding.bottomNav.selectedItemId = R.id.nav_home
                switchFragment(homeFragment)
            }
            TAB_SEARCH -> {
                switchFragment(searchFragment)
            }
            TAB_SUBSCRIPTIONS -> {
                binding.bottomNav.selectedItemId = R.id.nav_subscriptions
                switchFragment(subscriptionsFragment)
            }
            TAB_LIBRARY -> {
                binding.bottomNav.selectedItemId = R.id.nav_library
                switchFragment(libraryFragment)
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun playVideo(video: VideoItem) {
        binding.playerSheetContainer.visibility = View.VISIBLE
        playerController.playVideo(video)
    }

    fun showVideoQuickMenu(video: VideoItem) {
        val options = arrayOf("Save to Watch Later", "Share", "Copy Link")
        AlertDialog.Builder(this)
            .setTitle(video.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        lifecycleScope.launch {
                            val saved = repository.toggleBookmark(video)
                            Toast.makeText(
                                this@MainActivity,
                                if (saved) "Saved to Watch Later" else "Removed from Watch Later",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    1 -> {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, video.title)
                            putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    }
                    2 -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("YouTube Link", "https://youtu.be/${video.id}")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About PersonalTube")
            .setMessage(
                "PersonalTube v1.0.0\n\n" +
                "• 100% Ad-Free Video Streaming\n" +
                "• Background Audio & Lockscreen Controls\n" +
                "• Picture-in-Picture (PiP)\n" +
                "• Return YouTube Dislike (RYD) & SponsorBlock\n" +
                "• Offline Local Vault & Zero Tracking\n\n" +
                "Crafted for private personal use."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // Auto-enter PiP mode on Home swipe/press if video is playing
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = ExoPlayerHolder.getPlayer(this)
        if (player.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.headerBar.visibility = View.GONE
            binding.bottomNav.visibility = View.GONE
            binding.fragmentContainer.visibility = View.GONE
            playerBinding.playerTopBar.visibility = View.GONE
            playerBinding.playerControlsOverlay.visibility = View.GONE
        } else {
            binding.headerBar.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.VISIBLE
            binding.fragmentContainer.visibility = View.VISIBLE
            playerBinding.playerTopBar.visibility = View.VISIBLE
            playerBinding.playerControlsOverlay.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.destroy()
        if (!ExoPlayerHolder.isBackgroundAudioEnabled) {
            ExoPlayerHolder.release()
        }
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_SEARCH = 1
        const val TAB_SUBSCRIPTIONS = 2
        const val TAB_LIBRARY = 3
    }
}
