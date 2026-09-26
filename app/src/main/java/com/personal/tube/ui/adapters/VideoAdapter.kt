package com.personal.tube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.personal.tube.R
import com.personal.tube.data.model.VideoItem
import com.personal.tube.databinding.ItemVideoCardBinding

class VideoAdapter(
    private val onVideoClick: (VideoItem) -> Unit,
    private val onMoreClick: (VideoItem) -> Unit = {}
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(private val binding: ItemVideoCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoItem) {
            binding.tvTitle.text = video.title
            val subtitle = "${video.channelTitle} · ${video.viewCountFormatted}${if (video.publishedTime.isNotBlank()) " · ${video.publishedTime}" else ""}"
            binding.tvSubtitle.text = subtitle
            binding.tvDuration.text = video.durationFormatted

            // Load Video Thumbnail
            Glide.with(binding.ivThumbnail.context)
                .load(video.thumbnailUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(binding.ivThumbnail)

            // Load Channel Avatar or fallback
            if (video.channelAvatarUrl.isNotBlank()) {
                Glide.with(binding.ivChannelAvatar.context)
                    .load(video.channelAvatarUrl)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivChannelAvatar)
            } else {
                binding.ivChannelAvatar.setImageResource(R.drawable.ic_subscriptions)
            }

            binding.root.setOnClickListener {
                onVideoClick(video)
            }

            binding.btnMoreOptions.setOnClickListener {
                onMoreClick(video)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem == newItem
        }
    }
}
