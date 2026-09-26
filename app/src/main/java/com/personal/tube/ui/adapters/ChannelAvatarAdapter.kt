package com.personal.tube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.personal.tube.R
import com.personal.tube.data.local.entities.SubscriptionEntity
import com.personal.tube.databinding.ItemChannelAvatarBinding

class ChannelAvatarAdapter(
    private val onChannelClick: (SubscriptionEntity) -> Unit
) : RecyclerView.Adapter<ChannelAvatarAdapter.ChannelViewHolder>() {

    private val channels = mutableListOf<SubscriptionEntity>()

    fun submitList(list: List<SubscriptionEntity>) {
        channels.clear()
        channels.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelAvatarBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(private val binding: ItemChannelAvatarBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubscriptionEntity) {
            binding.tvChannelName.text = item.channelTitle

            if (item.avatarUrl.isNotBlank()) {
                Glide.with(binding.ivChannelIcon.context)
                    .load(item.avatarUrl)
                    .circleCrop()
                    .into(binding.ivChannelIcon)
            } else {
                binding.ivChannelIcon.setImageResource(R.drawable.ic_subscriptions)
            }

            binding.root.setOnClickListener {
                onChannelClick(item)
            }
        }
    }
}
