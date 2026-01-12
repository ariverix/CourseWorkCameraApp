package com.example.coursework.ui.gallery

import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.coursework.databinding.ItemMediaBinding
import com.example.coursework.util.MediaFileHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Адаптер для RecyclerView в галерее
class GalleryAdapter(
    private var files: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.MediaViewHolder>() {

    class MediaViewHolder(private val binding: ItemMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File, onItemClick: (File) -> Unit) {
            // превью через glide
            Glide.with(binding.root.context)
                .load(file)
                .centerCrop()
                .into(binding.thumbnail)

            val isVideo = MediaFileHelper.isVideoFile(file)

            // дата файла
            val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            binding.date.text = dateFormat.format(Date(file.lastModified()))

            if (isVideo) {
                binding.playIcon.visibility = View.VISIBLE
                binding.duration.visibility = View.VISIBLE

                //  длительность видео
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durationMs = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                    retriever.release()

                    binding.duration.text = MediaFileHelper.formatDuration(durationMs)
                } catch (_: Exception) {
                    binding.duration.visibility = View.GONE
                }
            } else {
                binding.playIcon.visibility = View.GONE
                binding.duration.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(file) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(files[position], onItemClick)
    }

    override fun getItemCount(): Int = files.size

    // DiffUtil чтоб не дергать весь список
    fun updateFiles(newFiles: List<File>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = files.size
            override fun getNewListSize() = newFiles.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                files[oldPos].absolutePath == newFiles[newPos].absolutePath
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                files[oldPos].lastModified() == newFiles[newPos].lastModified()
        })
        files = newFiles
        diffResult.dispatchUpdatesTo(this)
    }
}
