package com.personal.tube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.personal.tube.databinding.ItemSearchSuggestionBinding

class SuggestionAdapter(
    private val onSuggestionClick: (String) -> Unit,
    private val onFillClick: (String) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    private val suggestions = mutableListOf<String>()

    fun submitList(list: List<String>) {
        suggestions.clear()
        suggestions.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSearchSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    inner class SuggestionViewHolder(private val binding: ItemSearchSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(text: String) {
            binding.tvSuggestion.text = text
            binding.root.setOnClickListener {
                onSuggestionClick(text)
            }
            binding.btnFillQuery.setOnClickListener {
                onFillClick(text)
            }
        }
    }
}
