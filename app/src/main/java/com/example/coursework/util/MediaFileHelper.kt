package com.example.coursework.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Вспомогательный класс для работы с файлами
object MediaFileHelper {

    private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png")
    private val VIDEO_EXTENSIONS = listOf("mp4", "3gp", "mkv")
    val SUPPORTED_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

    // папка для медиа в external storage
    @Suppress("DEPRECATION")
    fun getMediaDirectory(context: Context): File? {
        val mediaDir = context.externalMediaDirs.firstOrNull()
        return if (mediaDir != null && mediaDir.exists()) mediaDir else null
    }

    // имя файла с датой и временем
    fun createFileName(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "$timestamp.$extension"
    }

    // отсортированные файлы по дате
    fun getMediaFiles(context: Context): List<File> {
        val mediaDir = getMediaDirectory(context) ?: return emptyList()

        return mediaDir.listFiles()
            ?.filter { file -> file.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // последний файл для превью
    fun getLastMediaFile(context: Context): File? {
        return getMediaFiles(context).firstOrNull()
    }

    fun isVideoFile(file: File): Boolean {
        return file.extension.lowercase() in VIDEO_EXTENSIONS
    }

    @Suppress("unused")
    fun isImageFile(file: File): Boolean {
        return file.extension.lowercase() in IMAGE_EXTENSIONS
    }

    // форматирование длительности видео
    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).toInt()
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, secs)
    }

    // таймер записи
    fun formatRecordingTime(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, secs)
    }

    // форматирование размера файла
    fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes Б"
            sizeBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f КБ", sizeBytes / 1024.0)
            sizeBytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f МБ", sizeBytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.1f ГБ", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
