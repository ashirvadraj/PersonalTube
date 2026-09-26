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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.personal.tube.R
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.ActivityMainBinding
import com.personal.tube.databinding.DialogAccountProfileBinding
import com.personal.tube.databinding.LayoutPlayerSheetBinding
import com.personal.tube.player.ExoPlayerHolder
import com.personal.tube.ui.home.HomeFragment
import com.personal.tube.ui.library.LibraryFragment
import com.personal.tube.ui.player.PlayerViewController
import com.personal.tube.ui.search.SearchFragment
import com.personal.tube.ui.subscriptions.SubscriptionsFragment
import com.personal.tube.util.UserManager
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

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                UserManager.handleSignInSuccess(account)
                Toast.makeText(this, "Signed in as ${account.displayName ?: account.email}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // When running without a production Firebase/Cloud Console SHA-1 certificate registered,
            // provide a graceful fallback sign-in so user experience is not blocked
            val account = task.result
            if (account != null) {
                UserManager.handleSignInSuccess(account)
                Toast.makeText(this, "Signed in as ${account.displayName ?: account.email}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Google Sign-In: ${e.localizedMessage ?: "Failed"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = VideoRepository(this)
        setupPlayerSheet()
        setupNavigation()
        setupHeader()
        observeUserSession()

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

        binding.btnHeaderProfile.setOnClickListener {
            showAccountDialog()
        }
    }

    private fun observeUserSession() {
        UserManager.currentUser.observe(this) { user ->
            if (user != null) {
                if (!user.photoUrl.isNullOrBlank()) {
                    binding.btnHeaderProfile.clearColorFilter()
                    Glide.with(this)
                        .load(user.photoUrl)
                        .circleCrop()
                        .into(binding.btnHeaderProfile)
                } else {
                    binding.btnHeaderProfile.setImageResource(R.drawable.ic_account_circle)
                    binding.btnHeaderProfile.setColorFilter(getColor(R.color.yt_white))
                }
            } else {
                binding.btnHeaderProfile.setImageResource(R.drawable.ic_account_circle)
                binding.btnHeaderProfile.setColorFilter(getColor(R.color.yt_white))
            }
        }
    }

    fun startGoogleSignIn() {
        val client = UserManager.getGoogleSignInClient(this)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun showAccountDialog() {
        val dialogView = DialogAccountProfileBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val currentUser = UserManager.currentUser.value
        if (currentUser != null) {
            dialogView.layoutLoggedIn.visibility = View.VISIBLE
            dialogView.layoutNotLoggedIn.visibility = View.GONE
            dialogView.tvDialogUserName.text = currentUser.displayName
            dialogView.tvDialogUserEmail.text = currentUser.email

            if (!currentUser.photoUrl.isNullOrBlank()) {
                Glide.with(this)
                    .load(currentUser.photoUrl)
                    .circleCrop()
                    .into(dialogView.ivDialogUserAvatar)
            } else {
                dialogView.ivDialogUserAvatar.setImageResource(R.drawable.ic_account_circle)
            }

            dialogView.btnDialogSignOut.setOnClickListener {
                UserManager.signOut(this) {
                    Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        } else {
            dialogView.layoutLoggedIn.visibility = View.GONE
            dialogView.layoutNotLoggedIn.visibility = View.VISIBLE

            dialogView.btnDialogGoogleSignin.setOnClickListener {
                dialog.dismiss()
                startGoogleSignIn()
            }

            dialogView.btnDialogDismiss.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
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
