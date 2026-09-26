package com.personal.tube.ui.shorts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.personal.tube.R
import com.personal.tube.data.model.CommentItem
import com.personal.tube.data.model.ShortItem
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.repository.VideoRepository
import com.personal.tube.databinding.FragmentShortsBinding
import com.personal.tube.ui.MainActivity
import com.personal.tube.ui.adapters.CommentAdapter
import com.personal.tube.ui.adapters.ShortsAdapter
import kotlinx.coroutines.launch

class ShortsFragment : Fragment() {

    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VideoRepository
    private lateinit var shortsAdapter: ShortsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShortsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = VideoRepository(requireContext())

        setupViewPager()
        setupHeader()
        loadShorts()
    }

    private fun setupViewPager() {
        shortsAdapter = ShortsAdapter(
            onShortClick = { short ->
                val videoItem = VideoItem(
                    id = short.id,
                    title = short.title,
                    channelTitle = short.channelTitle,
                    channelAvatarUrl = short.channelAvatarUrl,
                    thumbnailUrl = "https://i.ytimg.com/vi/${short.id}/hqdefault.jpg"
                )
                (activity as? MainActivity)?.playVideo(videoItem)
            },
            onCommentsClick = { short ->
                showCommentsSheet(short.id, short.commentCountFormatted)
            },
            onShareClick = { short ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, short.title)
                    putExtra(Intent.EXTRA_TEXT, "${short.title}\nhttps://youtube.com/shorts/${short.id}")
                }
                startActivity(Intent.createChooser(intent, "Share Short"))
            }
        )
        binding.viewPagerShorts.adapter = shortsAdapter
    }

    private fun setupHeader() {
        binding.btnShortsSearch.setOnClickListener {
            (activity as? MainActivity)?.selectNavigationTab(MainActivity.TAB_SEARCH)
        }
    }

    private fun loadShorts() {
        binding.progressShorts.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = repository.getShorts()
                binding.progressShorts.visibility = View.GONE
                if (list.isNotEmpty()) {
                    shortsAdapter.submitList(list)
                }
            } catch (e: Exception) {
                binding.progressShorts.visibility = View.GONE
            }
        }
    }

    private fun showCommentsSheet(videoId: String, countText: String) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.dialog_comments_sheet, null)
        dialog.setContentView(sheetView)

        val tvCount = sheetView.findViewById<TextView>(R.id.tv_sheet_comment_count)
        val btnClose = sheetView.findViewById<ImageView>(R.id.btn_close_comments)
        val rvComments = sheetView.findViewById<RecyclerView>(R.id.rv_comments)
        val etNewComment = sheetView.findViewById<EditText>(R.id.et_new_comment)
        val btnSend = sheetView.findViewById<ImageView>(R.id.btn_send_comment)

        tvCount.text = countText
        btnClose.setOnClickListener { dialog.dismiss() }

        val commentAdapter = CommentAdapter()
        rvComments.layoutManager = LinearLayoutManager(requireContext())
        rvComments.adapter = commentAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            val comments = repository.getComments(videoId)
            commentAdapter.submitList(comments)
        }

        btnSend.setOnClickListener {
            val txt = etNewComment.text.toString().trim()
            if (txt.isNotBlank()) {
                val current = commentAdapter.currentList.toMutableList()
                current.add(
                    0,
                    CommentItem(
                        id = "user_${System.currentTimeMillis()}",
                        authorName = "You",
                        text = txt,
                        publishedTime = "Just now",
                        likeCountFormatted = "0"
                    )
                )
                commentAdapter.submitList(current)
                etNewComment.text.clear()
                Toast.makeText(requireContext(), "Comment posted", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
