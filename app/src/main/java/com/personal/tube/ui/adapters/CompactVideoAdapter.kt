package com.personal.tube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.personal.tube.data.model.VideoItem
import com.personal.tube.databinding.ItemVideoCompactBinding

class CompactVideoAdapter(
    private val onVideoClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<CompactVideoAdapter.CompactViewHolder>() {

    private val items = mutableListOf<VideoItem>()

    fun submitList(list: List<VideoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompactViewHolder {
        val binding = ItemVideoCompactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CompactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CompactViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CompactViewHolder(private val binding: ItemVideoCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoItem) {
            binding.tvCompactTitle.text = video.title
            binding.tvCompactChannel.text = video.channelTitle
            binding.tvCompactDuration.text = video.durationFormatted

            Glide.with(binding.ivCompactThumbnail.context)
                .load(video.thumbnailUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(binding.ivCompactThumbnail)

            binding.root.setOnClickListener {
                onVideoClick(video)
            }
        }
    }
}
