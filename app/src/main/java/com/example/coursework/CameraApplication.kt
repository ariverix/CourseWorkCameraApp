package com.example.coursework

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class CameraApplication : Application(), CameraXConfig.Provider {

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder
            .fromConfig(Camera2Config.defaultConfig())
            .build()
    }
}
