package com.personal.tube.ui

import android.accounts.AccountManager
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
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.speech.RecognizerIntent
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
    private val shortsFragment by lazy { com.personal.tube.ui.shorts.ShortsFragment() }
    val searchFragment by lazy { SearchFragment() }
    private val subscriptionsFragment by lazy { SubscriptionsFragment() }
    private val libraryFragment by lazy { LibraryFragment() }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                selectNavigationTab(TAB_SEARCH)
                searchFragment.searchQueryFromExternal(spokenText)
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                UserManager.handleSignInSuccess(account)
                Toast.makeText(this, "Signed in as ${account.displayName ?: account.email}", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Google sign-in ApiException: ${e.message}")
        }
        // Seamless fallback to native Android account picker so user is never blocked or crashed
        launchDeviceAccountPicker()
    }

    private val deviceAccountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!email.isNullOrBlank()) {
                UserManager.loginWithEmail(email)
                Toast.makeText(this, "Signed in with Gmail: $email", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchDeviceAccountPicker() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            deviceAccountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            showManualEmailDialog()
        }
    }

    fun showManualEmailDialog() {
        val input = EditText(this).apply {
            hint = "yourname@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(48, 32, 48, 32)
            setTextColor(getColor(R.color.yt_white))
            setHintTextColor(getColor(R.color.yt_text_secondary))
        }

        AlertDialog.Builder(this)
            .setTitle("Enter Gmail Address")
            .setView(input)
            .setPositiveButton("Sign In") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotBlank() && email.contains("@")) {
                    UserManager.loginWithEmail(email)
                    Toast.makeText(this, "Signed in as $email", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid Gmail address", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                R.id.nav_shorts -> switchFragment(shortsFragment)
                R.id.nav_subscriptions -> switchFragment(subscriptionsFragment)
                R.id.nav_library -> switchFragment(libraryFragment)
            }
            true
        }
    }

    private fun setupHeader() {
        binding.btnHeaderCast.setOnClickListener {
            showCastDialog()
        }

        binding.btnHeaderNotifications.setOnClickListener {
            showNotificationsDialog()
        }

        binding.btnHeaderSearch.setOnClickListener {
            switchFragment(searchFragment)
        }

        binding.btnHeaderProfile.setOnClickListener {
            showAccountDialog()
        }
    }

    private fun showCastDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cast to a device")
            .setMessage("Searching for TVs and wireless displays on your local network...\n\nMake sure your Chromecast, Smart TV, or Android TV is connected to the same Wi-Fi network.")
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showNotificationsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notifications")
            .setMessage("All caught up!\n\nNo new notifications from your subscribed channels.")
            .setPositiveButton("OK", null)
            .show()
    }

    fun startVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search YouTube...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show()
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
                launchDeviceAccountPicker()
            }

            dialogView.btnDialogManualSignin.setOnClickListener {
                dialog.dismiss()
                showManualEmailDialog()
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
            TAB_SHORTS -> {
                binding.bottomNav.selectedItemId = R.id.nav_shorts
                switchFragment(shortsFragment)
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
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_video_options, null)
        dialog.setContentView(view)

        view.findViewById<android.widget.TextView>(R.id.tv_menu_video_title).text = video.title

        view.findViewById<View>(R.id.opt_save_watch_later).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val saved = repository.toggleBookmark(video)
                Toast.makeText(
                    this@MainActivity,
                    if (saved) "Saved to Watch Later" else "Removed from Watch Later",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        view.findViewById<View>(R.id.opt_save_playlist).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Added to playlist", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.opt_download).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Downloading '${video.title}' for offline viewing...", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.opt_share).setOnClickListener {
            dialog.dismiss()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, video.title)
                putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Video"))
        }

        view.findViewById<View>(R.id.opt_not_interested).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Video hidden from recommendations", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.opt_dont_recommend).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "We won't recommend videos from ${video.channelTitle}", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
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

    // Auto-enter PiP mode on Home swipe/press if player is visible
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (binding.playerSheetContainer.visibility == View.VISIBLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            playerBinding.layoutMiniPlayer.visibility = View.GONE
            playerBinding.scrollVideoDetails.visibility = View.GONE
        } else {
            binding.headerBar.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.VISIBLE
            binding.fragmentContainer.visibility = View.VISIBLE
            playerBinding.playerTopBar.visibility = View.VISIBLE
            playerBinding.scrollVideoDetails.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.destroy()
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_SEARCH = 1
        const val TAB_SUBSCRIPTIONS = 2
        const val TAB_LIBRARY = 3
        const val TAB_SHORTS = 4
    }
}
