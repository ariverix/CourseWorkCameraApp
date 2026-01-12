package com.example.coursework.ui.gallery

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.coursework.databinding.ActivityGalleryBinding
import com.example.coursework.ui.base.BaseActivity
import com.example.coursework.ui.viewer.MediaViewerActivity
import com.example.coursework.util.MediaFileHelper
import java.io.File

// Галерея
class GalleryActivity : BaseActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupRecyclerView()
        setupButtons()
        loadFiles()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.toolbar.setPadding(
                systemBars.left + 16,
                systemBars.top + 16,
                systemBars.right + 16,
                16
            )

            binding.bottomBar.setPadding(
                systemBars.left + 16,
                16,
                systemBars.right + 16,
                systemBars.bottom + 16
            )

            insets
        }
    }

    // сетка 3 колонки
    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)

        adapter = GalleryAdapter(emptyList()) { file ->
            openViewer(file)
        }

        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.backBtn.setOnClickListener { finish() }
        binding.cameraFab.setOnClickListener { finish() }
    }

    // загрузка файлов из папки приложения
    private fun loadFiles() {
        val files = MediaFileHelper.getMediaFiles(this)

        if (files.isEmpty()) {
            showEmptyState()
        } else {
            showFiles(files)
        }
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showFiles(files: List<File>) {
        binding.emptyState.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        adapter.updateFiles(files)
    }

    // открытие просмотра на весь экран
    private fun openViewer(file: File) {
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
        }
        startActivity(intent)
    }

    // обновление списка при возврате
    override fun onResume() {
        super.onResume()
        loadFiles()
    }
}
