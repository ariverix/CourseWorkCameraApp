package com.example.coursework.ui.camera

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.common.util.concurrent.ListenableFuture
import com.example.coursework.R
import com.example.coursework.databinding.ActivityMainBinding
import com.example.coursework.ui.base.BaseActivity
import com.example.coursework.ui.gallery.GalleryActivity
import com.example.coursework.util.MediaFileHelper
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.drawable.toDrawable

// Экран камеры
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    // CameraX
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    // флаги состояния
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isVideoMode = false
    private var isRecording = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timerSeconds = 0

    // зум
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // таймеры
    private var countdownTimer: CountDownTimer? = null
    private var recordingTimer: CountDownTimer? = null
    private var recordingStartTime = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    // запрос разрешений
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(tag, "Permissions granted")
            startCamera()
        } else {
            Log.e(tag, "Permissions denied")
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        // executor работа камеры в фоне
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        setupZoomGesture()
        checkPermissions()
        setupButtons()
    }

    // отступы под системные бары
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.topControls.setPadding(
                systemBars.left + 16,
                systemBars.top + 16,
                systemBars.right + 16,
                16
            )

            binding.bottomControls.setPadding(
                systemBars.left + 32,
                16,
                systemBars.right + 32,
                systemBars.bottom + 32
            )

            insets
        }
    }

    // проверка разрешений
    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startCamera()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    // зум двумя пальцами
    private fun setupZoomGesture() {
        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    camera?.let { cam ->
                        val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        val delta = detector.scaleFactor
                        cam.cameraControl.setZoomRatio(currentZoom * delta)
                    }
                    return true
                }
            })
    }

    // все кнопки и тач
    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtons() {
        binding.captureBtn.setOnClickListener {
            if (isVideoMode) {
                toggleVideoRecording()
            } else {
                if (timerSeconds > 0) {
                    startCountdownTimer()
                } else {
                    takePhoto()
                }
            }
        }

        binding.switchBtn.setOnClickListener { switchCamera() }

        binding.galleryBtn.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        binding.flashBtn.setOnClickListener { toggleFlash() }
        binding.timerBtn.setOnClickListener { toggleTimer() }

        binding.modePhoto.setOnClickListener {
            if (isVideoMode) {
                isVideoMode = false
                updateModeUI()
                startCamera()
            }
        }

        binding.modeVideo.setOnClickListener {
            if (!isVideoMode) {
                isVideoMode = true
                updateModeUI()
                startCamera()
            }
        }

        // тач на превью, зум + фокус по тапу
        binding.preview.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                tapToFocus(event.x, event.y)
                view.performClick()
            }
            true
        }
    }

    // переключение камеры, при записи останавливается и продолжает на другой
    private fun switchCamera() {
        val wasRecording = isRecording

        if (wasRecording) {
            recording?.stop()
            recording = null
            isRecording = false
            stopRecordingTimer()
        }

        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        startCamera()

        // адержка перед продолжением записи
        if (wasRecording) {
            mainHandler.postDelayed({ toggleVideoRecording() }, 500)
        }
    }

    // фокус по тапу
    private fun tapToFocus(x: Float, y: Float) {
        camera?.let { cam ->
            val factory = binding.preview.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point).build()

            showFocusIndicator(x, y)
            cam.cameraControl.startFocusAndMetering(action)
        }
    }

    // квадрат фокуса с анимацией
    private fun showFocusIndicator(x: Float, y: Float) {
        binding.focusIndicator.apply {
            this.x = x - width / 2
            this.y = y - height / 2
            visibility = View.VISIBLE
            alpha = 1f

            animate()
                .alpha(0f)
                .setDuration(1000)
                .withEndAction { visibility = View.INVISIBLE }
                .start()
        }
    }

    // вспышка выкл - вкл - авто
    private fun toggleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        updateFlashUI()
        imageCapture?.flashMode = flashMode
    }

    private fun updateFlashUI() {
        val icon = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
            else -> R.drawable.ic_flash_off
        }
        binding.flashBtn.setImageResource(icon)
    }

    // таймер
    private fun toggleTimer() {
        timerSeconds = when (timerSeconds) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
        updateTimerUI()
    }

    private fun updateTimerUI() {
        val icon = when (timerSeconds) {
            3 -> R.drawable.ic_timer_3
            5 -> R.drawable.ic_timer_5
            10 -> R.drawable.ic_timer_10
            else -> R.drawable.ic_timer_off
        }
        binding.timerBtn.setImageResource(icon)
    }

    // переключение ui при нажатии  фото или видео
    private fun updateModeUI() {
        if (isVideoMode) {
            binding.modeVideo.setBackgroundResource(R.drawable.mode_selected_bg)
            binding.modePhoto.background = null
            binding.captureBtn.setBackgroundResource(R.drawable.capture_button_video_bg)
            binding.captureBtn.setImageResource(0)
            binding.flashBtn.visibility = View.INVISIBLE
            binding.timerBtn.visibility = View.INVISIBLE
        } else {
            binding.modePhoto.setBackgroundResource(R.drawable.mode_selected_bg)
            binding.modeVideo.background = null
            binding.captureBtn.setBackgroundResource(R.drawable.capture_button_bg)
            binding.captureBtn.setImageResource(R.drawable.ic_camera)
            binding.flashBtn.visibility = View.VISIBLE
            binding.timerBtn.visibility = View.VISIBLE
        }
    }

    // обратный отсчет перед фото
    private fun startCountdownTimer() {
        binding.timerText.visibility = View.VISIBLE
        binding.captureBtn.isEnabled = false

        countdownTimer = object : CountDownTimer(timerSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                binding.timerText.text = seconds.toString()
            }

            override fun onFinish() {
                binding.timerText.visibility = View.GONE
                binding.captureBtn.isEnabled = true
                takePhoto()
            }
        }.start()
    }

    // инициализация камеры через CameraX
    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = binding.preview.surfaceProvider }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(flashMode)
                .build()

            // рекордер для видео в HD
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            cameraProvider.unbindAll()

            // биндим разные use case в зависимости от режима
            camera = if (isVideoMode) {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } else {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            }

            updateGalleryPreview()

        }, ContextCompat.getMainExecutor(this))
    }

    // делается фотка и сохраняем
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val mediaDir = MediaFileHelper.getMediaDirectory(this) ?: run {
            Toast.makeText(this, R.string.storage_error, Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = MediaFileHelper.createFileName("jpg")
        val file = File(mediaDir, fileName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // возвращаемся в main thread для ui
                    mainHandler.post {
                        Toast.makeText(this@MainActivity, R.string.photo_saved, Toast.LENGTH_SHORT).show()
                        animateFlash()
                        updateGalleryPreview()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo error", exception)
                }
            }
        )
    }

    // старт или стоп записи видео
    private fun toggleVideoRecording() {
        val videoCapture = videoCapture ?: return

        if (isRecording) {
            recording?.stop()
            recording = null
            isRecording = false
            stopRecordingTimer()
            updateRecordingUI()
        } else {
            val mediaDir = MediaFileHelper.getMediaDirectory(this) ?: run {
                Toast.makeText(this, R.string.storage_error, Toast.LENGTH_SHORT).show()
                return
            }

            val fileName = MediaFileHelper.createFileName("mp4")
            val file = File(mediaDir, fileName)

            val outputOptions = FileOutputOptions.Builder(file).build()

            val pendingRecording = videoCapture.output
                .prepareRecording(this, outputOptions)

            // аудио только если есть разрешение
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                pendingRecording.withAudioEnabled()
            }

            recording = pendingRecording.start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        mainHandler.post {
                            isRecording = true
                            startRecordingTimer()
                            updateRecordingUI()
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            mainHandler.post {
                                Toast.makeText(this@MainActivity, R.string.video_saved, Toast.LENGTH_SHORT).show()
                                updateGalleryPreview()
                            }
                        }
                    }
                }
            }
        }
    }

    // сркытие лишнего во время записи
    private fun updateRecordingUI() {
        if (isRecording) {
            binding.captureBtn.setBackgroundResource(R.drawable.capture_button_recording_bg)
            binding.recordingIndicator.visibility = View.VISIBLE
            binding.modeSwitch.visibility = View.INVISIBLE
            binding.topControls.visibility = View.INVISIBLE
        } else {
            binding.captureBtn.setBackgroundResource(R.drawable.capture_button_video_bg)
            binding.recordingIndicator.visibility = View.GONE
            binding.modeSwitch.visibility = View.VISIBLE
            binding.topControls.visibility = View.VISIBLE
        }
    }

    // таймер записи видео
    private fun startRecordingTimer() {
        recordingStartTime = System.currentTimeMillis()
        recordingTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val seconds = (elapsed / 1000).toInt()
                binding.recordingTime.text = MediaFileHelper.formatRecordingTime(seconds)
            }
            override fun onFinish() {}
        }.start()
    }

    private fun stopRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = null
        binding.recordingTime.setText(R.string.recording_time_default)
    }

    // белая вспышка при фото
    private fun animateFlash() {
        binding.root.foreground = Color.WHITE.toDrawable()
        binding.root.postDelayed({ binding.root.foreground = null }, 50)
    }

    // превьюшка последнего файла
    private fun updateGalleryPreview() {
        val lastFile = MediaFileHelper.getLastMediaFile(this) ?: return

        Glide.with(this)
            .load(lastFile)
            .centerCrop()
            .into(binding.galleryBtn)
    }

    override fun onResume() {
        super.onResume()
        updateGalleryPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        countdownTimer?.cancel()
        recordingTimer?.cancel()
    }
}