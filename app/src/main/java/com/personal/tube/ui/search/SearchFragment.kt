package com.personal.tube.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.FragmentSearchBinding
import com.personal.tube.ui.MainActivity
import com.personal.tube.ui.adapters.SuggestionAdapter
import com.personal.tube.ui.adapters.VideoAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VideoRepository
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var resultsAdapter: VideoAdapter
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = VideoRepository(requireContext())

        setupAdapters()
        setupListeners()
    }

    private fun setupAdapters() {
        suggestionAdapter = SuggestionAdapter(
            onSuggestionClick = { query ->
                binding.etSearchQuery.setText(query)
                binding.etSearchQuery.setSelection(query.length)
                performSearch(query)
            },
            onFillClick = { query ->
                binding.etSearchQuery.setText(query)
                binding.etSearchQuery.setSelection(query.length)
            }
        )
        binding.rvSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSuggestions.adapter = suggestionAdapter

        resultsAdapter = VideoAdapter(
            onVideoClick = { video ->
                (activity as? MainActivity)?.playVideo(video)
            },
            onMoreClick = { video ->
                (activity as? MainActivity)?.showVideoQuickMenu(video)
            }
        )
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = resultsAdapter
    }

    private fun setupListeners() {
        binding.btnSearchBack.setOnClickListener {
            (activity as? MainActivity)?.selectNavigationTab(MainActivity.TAB_HOME)
        }

        binding.btnSearchClear.setOnClickListener {
            binding.etSearchQuery.text.clear()
            binding.rvSuggestions.visibility = View.VISIBLE
            binding.rvSearchResults.visibility = View.GONE
        }

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchQuery.text.toString().trim()
                if (query.isNotBlank()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                binding.btnSearchClear.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                fetchSuggestions(text)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchSuggestions(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            suggestionAdapter.submitList(emptyList())
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250) // Debounce
            val suggestions = repository.getSuggestions(query)
            suggestionAdapter.submitList(suggestions)
            binding.rvSuggestions.visibility = View.VISIBLE
            binding.rvSearchResults.visibility = View.GONE
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        binding.rvSuggestions.visibility = View.GONE
        binding.rvSearchResults.visibility = View.GONE
        binding.progressSearch.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val results = repository.search(query)
            binding.progressSearch.visibility = View.GONE
            binding.rvSearchResults.visibility = View.VISIBLE
            resultsAdapter.submitList(results)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
