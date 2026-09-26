package com.personal.tube.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.personal.tube.R
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.FragmentLibraryBinding
import com.personal.tube.ui.MainActivity
import com.personal.tube.ui.adapters.CompactVideoAdapter
import com.personal.tube.util.UserManager
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VideoRepository
    private lateinit var historyAdapter: CompactVideoAdapter
    private lateinit var downloadsAdapter: CompactVideoAdapter
    private lateinit var bookmarksAdapter: CompactVideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = VideoRepository(requireContext())

        setupAdapters()
        observeData()
        setupListeners()
    }

    private fun setupAdapters() {
        historyAdapter = CompactVideoAdapter { video ->
            (activity as? MainActivity)?.playVideo(video)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHistory.adapter = historyAdapter

        downloadsAdapter = CompactVideoAdapter { video ->
            (activity as? MainActivity)?.playVideo(video)
        }
        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvDownloads.adapter = downloadsAdapter

        bookmarksAdapter = CompactVideoAdapter { video ->
            (activity as? MainActivity)?.playVideo(video)
        }
        binding.rvBookmarks.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvBookmarks.adapter = bookmarksAdapter
    }

    private fun observeData() {
        // Watch History
        repository.watchHistory.observe(viewLifecycleOwner) { historyList ->
            if (historyList.isNullOrEmpty()) {
                binding.tvEmptyHistory.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            } else {
                binding.tvEmptyHistory.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                val videos = historyList.map {
                    VideoItem(
                        id = it.videoId,
                        title = it.title,
                        channelTitle = it.channelTitle,
                        thumbnailUrl = it.thumbnailUrl,
                        durationFormatted = it.durationFormatted,
                        durationSeconds = it.durationSeconds
                    )
                }
                historyAdapter.submitList(videos)
            }
        }

        // Offline Downloads
        repository.downloads.observe(viewLifecycleOwner) { downloadsList ->
            if (downloadsList.isNullOrEmpty()) {
                binding.tvEmptyDownloads.visibility = View.VISIBLE
                binding.rvDownloads.visibility = View.GONE
            } else {
                binding.tvEmptyDownloads.visibility = View.GONE
                binding.rvDownloads.visibility = View.VISIBLE
                val videos = downloadsList.map {
                    VideoItem(
                        id = it.videoId,
                        title = it.title,
                        channelTitle = it.channelTitle,
                        thumbnailUrl = it.thumbnailUrl,
                        durationFormatted = it.durationFormatted,
                        directStreamUrl = it.localFilePath
                    )
                }
                downloadsAdapter.submitList(videos)
            }
        }

        // Bookmarks / Watch Later
        repository.bookmarks.observe(viewLifecycleOwner) { bookmarksList ->
            val videos = bookmarksList.map {
                VideoItem(
                    id = it.videoId,
                    title = it.title,
                    channelTitle = it.channelTitle,
                    thumbnailUrl = it.thumbnailUrl,
                    durationFormatted = it.durationFormatted,
                    durationSeconds = it.durationSeconds
                )
            }
            bookmarksAdapter.submitList(videos)
        }

        // Google / Gmail Session State
        UserManager.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.layoutLibraryLoggedIn.visibility = View.VISIBLE
                binding.layoutLibraryNotLoggedIn.visibility = View.GONE
                binding.tvLibraryUserName.text = user.displayName
                binding.tvLibraryUserEmail.text = user.email

                if (!user.photoUrl.isNullOrBlank()) {
                    Glide.with(this)
                        .load(user.photoUrl)
                        .circleCrop()
                        .into(binding.ivLibraryAvatar)
                } else {
                    binding.ivLibraryAvatar.setImageResource(R.drawable.ic_account_circle)
                }

                binding.btnLibrarySignOut.setOnClickListener {
                    UserManager.signOut(requireActivity()) {
                        Toast.makeText(requireContext(), "Signed out from Google", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.layoutLibraryLoggedIn.visibility = View.GONE
                binding.layoutLibraryNotLoggedIn.visibility = View.VISIBLE

                binding.btnLibrarySignIn.setOnClickListener {
                    (activity as? MainActivity)?.startGoogleSignIn()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnClearHistory.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.clearHistory()
                Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
