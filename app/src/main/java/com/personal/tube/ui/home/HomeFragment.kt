package com.personal.tube.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.personal.tube.R
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.FragmentHomeBinding
import com.personal.tube.ui.MainActivity
import com.personal.tube.ui.adapters.VideoAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VideoRepository
    private lateinit var videoAdapter: VideoAdapter
    private var currentCategory = "All"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = VideoRepository(requireContext())

        setupRecycler()
        setupCategories()
        setupRefresh()
        loadFeed(currentCategory)
    }

    private fun setupRecycler() {
        videoAdapter = VideoAdapter(
            onVideoClick = { video ->
                (activity as? MainActivity)?.playVideo(video)
            },
            onMoreClick = { video ->
                (activity as? MainActivity)?.showVideoQuickMenu(video)
            }
        )
        binding.rvVideos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVideos.adapter = videoAdapter
    }

    private fun setupCategories() {
        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? com.google.android.material.chip.Chip ?: continue
                if (chip.id == checkedIds.first()) {
                    chip.setChipBackgroundColorResource(R.color.yt_chip_selected)
                    chip.setTextColor(requireContext().getColor(R.color.yt_chip_selected_text))
                } else {
                    chip.setChipBackgroundColorResource(R.color.yt_chip_background)
                    chip.setTextColor(requireContext().getColor(R.color.yt_text_primary))
                }
            }

            val category = when (checkedIds.first()) {
                R.id.chip_trending -> "Trending"
                R.id.chip_music -> "Music"
                R.id.chip_gaming -> "Gaming"
                R.id.chip_tech -> "Technology"
                R.id.chip_podcasts -> "Podcasts"
                else -> "All"
            }
            currentCategory = category
            loadFeed(category)
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadFeed(currentCategory)
        }
        binding.btnRetry.setOnClickListener {
            loadFeed(currentCategory)
        }
    }

    private fun loadFeed(category: String) {
        binding.progressLoading.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val videos = repository.getFeed(category)
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (videos.isNotEmpty()) {
                    videoAdapter.submitList(ArrayList(videos)) {
                        binding.rvVideos.scrollToPosition(0)
                    }
                    binding.rvVideos.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.layoutEmpty.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
