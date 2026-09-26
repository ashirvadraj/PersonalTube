package com.personal.tube.ui.subscriptions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.FragmentSubscriptionsBinding
import com.personal.tube.ui.MainActivity
import com.personal.tube.ui.adapters.ChannelAvatarAdapter
import com.personal.tube.ui.adapters.VideoAdapter
import kotlinx.coroutines.launch

class SubscriptionsFragment : Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VideoRepository
    private lateinit var channelAdapter: ChannelAvatarAdapter
    private lateinit var feedAdapter: VideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = VideoRepository(requireContext())

        setupAdapters()
        observeSubscriptions()
    }

    private fun setupAdapters() {
        channelAdapter = ChannelAvatarAdapter { channel ->
            loadChannelVideos(channel.channelTitle)
        }
        binding.rvChannels.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvChannels.adapter = channelAdapter

        feedAdapter = VideoAdapter(
            onVideoClick = { video ->
                (activity as? MainActivity)?.playVideo(video)
            },
            onMoreClick = { video ->
                (activity as? MainActivity)?.showVideoQuickMenu(video)
            }
        )
        binding.rvSubscriptionFeed.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSubscriptionFeed.adapter = feedAdapter
    }

    private fun observeSubscriptions() {
        repository.subscriptions.observe(viewLifecycleOwner) { subs ->
            if (subs.isNullOrEmpty()) {
                binding.rvChannels.visibility = View.GONE
                binding.layoutSubsEmpty.visibility = View.VISIBLE
                binding.rvSubscriptionFeed.visibility = View.GONE
            } else {
                binding.rvChannels.visibility = View.VISIBLE
                binding.layoutSubsEmpty.visibility = View.GONE
                binding.rvSubscriptionFeed.visibility = View.VISIBLE
                channelAdapter.submitList(subs)

                // Load feed for the first subscribed channel
                loadChannelVideos(subs.first().channelTitle)
            }
        }
    }

    private fun loadChannelVideos(channelName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val results = repository.search(channelName)
            feedAdapter.submitList(results)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
