package com.personal.tube.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.personal.tube.R
import com.personal.tube.data.model.ShortItem
import com.personal.tube.databinding.ItemShortBinding

class ShortsAdapter(
    private val onShortClick: (ShortItem) -> Unit,
    private val onCommentsClick: (ShortItem) -> Unit,
    private val onShareClick: (ShortItem) -> Unit
) : ListAdapter<ShortItem, ShortsAdapter.ShortViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortViewHolder {
        val binding = ItemShortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ShortViewHolder(private val binding: ItemShortBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ShortItem) {
            val thumbUrl = "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg"
            Glide.with(binding.root.context)
                .load(thumbUrl)
                .into(binding.ivShortThumbnail)

            if (item.channelAvatarUrl.isNotBlank()) {
                Glide.with(binding.root.context)
                    .load(item.channelAvatarUrl)
                    .circleCrop()
                    .into(binding.ivShortChannelAvatar)
            } else {
                binding.ivShortChannelAvatar.setImageResource(R.drawable.ic_subscriptions)
            }

            binding.tvShortChannelName.text = item.channelTitle
            binding.tvShortTitle.text = item.title
            binding.tvShortLikes.text = item.likeCountFormatted
            binding.tvShortComments.text = item.commentCountFormatted
            binding.tvShortSoundTitle.text = "Original Audio · ${item.channelTitle}"

            // Like Toggle
            val likeTint = if (item.isLiked) R.color.yt_red else R.color.yt_white
            binding.ivShortLikeIcon.setColorFilter(binding.root.context.getColor(likeTint))

            binding.btnShortLike.setOnClickListener {
                item.isLiked = !item.isLiked
                val newTint = if (item.isLiked) R.color.yt_red else R.color.yt_white
                binding.ivShortLikeIcon.setColorFilter(binding.root.context.getColor(newTint))
            }

            binding.btnShortDislike.setOnClickListener {
                Toast.makeText(binding.root.context, "Feedback recorded", Toast.LENGTH_SHORT).show()
            }

            // Subscribe Toggle
            updateSubscribeButton(item.isSubscribed)
            binding.btnShortSubscribe.setOnClickListener {
                item.isSubscribed = !item.isSubscribed
                updateSubscribeButton(item.isSubscribed)
            }

            binding.btnShortComments.setOnClickListener {
                onCommentsClick(item)
            }

            binding.btnShortShare.setOnClickListener {
                onShareClick(item)
            }

            binding.btnShortRemix.setOnClickListener {
                Toast.makeText(binding.root.context, "Remix audio feature", Toast.LENGTH_SHORT).show()
            }

            // Click on surface to play in full YouTube player sheet
            binding.root.setOnClickListener {
                binding.ivCenterPlayPause.visibility = View.VISIBLE
                binding.ivCenterPlayPause.postDelayed({
                    binding.ivCenterPlayPause.visibility = View.GONE
                }, 800)
                onShortClick(item)
            }
        }

        private fun updateSubscribeButton(isSubscribed: Boolean) {
            if (isSubscribed) {
                binding.btnShortSubscribe.text = "Subscribed"
                binding.btnShortSubscribe.setBackgroundColor(binding.root.context.getColor(R.color.yt_chip_background))
                binding.btnShortSubscribe.setTextColor(binding.root.context.getColor(R.color.yt_white))
            } else {
                binding.btnShortSubscribe.text = "Subscribe"
                binding.btnShortSubscribe.setBackgroundColor(binding.root.context.getColor(R.color.yt_white))
                binding.btnShortSubscribe.setTextColor(binding.root.context.getColor(R.color.yt_chip_selected_text))
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ShortItem>() {
        override fun areItemsTheSame(oldItem: ShortItem, newItem: ShortItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ShortItem, newItem: ShortItem): Boolean = oldItem == newItem
    }
}
