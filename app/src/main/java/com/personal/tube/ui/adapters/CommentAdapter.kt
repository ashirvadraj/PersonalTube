package com.personal.tube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.personal.tube.R
import com.personal.tube.data.model.CommentItem
import com.personal.tube.databinding.ItemCommentBinding

class CommentAdapter : ListAdapter<CommentItem, CommentAdapter.CommentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommentItem) {
            binding.tvCommentAuthor.text = item.authorName
            binding.tvCommentTime.text = item.publishedTime
            binding.tvCommentBody.text = item.text
            binding.tvCommentLikes.text = item.likeCountFormatted

            val likeColor = if (item.isLiked) {
                binding.root.context.getColor(R.color.yt_accent_blue)
            } else {
                binding.root.context.getColor(R.color.yt_text_secondary)
            }
            binding.btnCommentLike.setColorFilter(likeColor)

            binding.btnCommentLike.setOnClickListener {
                item.isLiked = !item.isLiked
                val newColor = if (item.isLiked) {
                    binding.root.context.getColor(R.color.yt_accent_blue)
                } else {
                    binding.root.context.getColor(R.color.yt_text_secondary)
                }
                binding.btnCommentLike.setColorFilter(newColor)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CommentItem>() {
        override fun areItemsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean = oldItem == newItem
    }
}
