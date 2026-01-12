package com.example.coursework.ui.viewer

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.coursework.R
import com.example.coursework.databinding.ActivityMediaViewerBinding
import com.example.coursework.ui.base.BaseActivity
import com.example.coursework.util.MediaFileHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Просмотр фото или видео на весь экран
class MediaViewerActivity : BaseActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    private lateinit var binding: ActivityMediaViewerBinding

    private var currentFile: File? = null
    private var isVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        // путь к файлу
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath == null) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentFile = File(filePath)
        if (currentFile?.exists() != true) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        isVideo = MediaFileHelper.isVideoFile(currentFile!!)

        loadMedia()
        showFileInfo()
        setupButtons()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.topBar.setPadding(
                systemBars.left + 16,
                systemBars.top + 16,
                systemBars.right + 16,
                16
            )

            binding.bottomInfo.setPadding(
                systemBars.left + 16,
                16,
                systemBars.right + 16,
                systemBars.bottom + 16
            )

            insets
        }
    }

    private fun loadMedia() {
        if (isVideo) {
            loadVideo()
        } else {
            loadPhoto()
        }
    }

    private fun loadVideo() {
        binding.videoView.visibility = View.VISIBLE
        binding.imageView.visibility = View.GONE
        binding.playPauseBtn.visibility = View.VISIBLE

        binding.videoView.setVideoURI(Uri.fromFile(currentFile))

        // контроллер видео
        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)

        binding.playPauseBtn.setOnClickListener {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
                binding.playPauseBtn.setImageResource(R.drawable.ic_play)
            } else {
                binding.videoView.start()
                binding.playPauseBtn.visibility = View.GONE
            }
        }

        // пауза по тапу на видео
        binding.videoView.setOnClickListener {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
                binding.playPauseBtn.visibility = View.VISIBLE
                binding.playPauseBtn.setImageResource(R.drawable.ic_play)
            }
        }
    }

    private fun loadPhoto() {
        binding.imageView.visibility = View.VISIBLE
        binding.videoView.visibility = View.GONE
        binding.playPauseBtn.visibility = View.GONE

        Glide.with(this)
            .load(currentFile)
            .into(binding.imageView)
    }

    // информация о файле
    private fun showFileInfo() {
        currentFile?.let { file ->
            binding.fileName.text = file.name

            val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
            val date = Date(file.lastModified())
            binding.fileDate.text = dateFormat.format(date)

            binding.fileSize.text = MediaFileHelper.formatFileSize(file.length())
        }
    }

    private fun setupButtons() {
        binding.backBtn.setOnClickListener { finish() }
        binding.deleteBtn.setOnClickListener { showDeleteDialog() }
    }

    // диалог подтверждения удаления
    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_confirmation)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                deleteFile()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteFile() {
        currentFile?.let { file ->
            // останавливка видео если играет
            if (isVideo && binding.videoView.isPlaying) {
                binding.videoView.stopPlayback()
            }

            if (file.delete()) {
                Toast.makeText(this, R.string.file_deleted, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, R.string.delete_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isVideo && binding.videoView.isPlaying) {
            binding.videoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isVideo) {
            binding.videoView.stopPlayback()
        }
    }
}
