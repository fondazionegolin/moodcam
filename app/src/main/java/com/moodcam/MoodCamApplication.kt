package com.moodcam

import android.app.Application
import com.moodcam.preset.PresetDatabase

/**
 * Application class for MoodCam.
 * Initializes singletons and provides global access to dependencies.
 */
class MoodCamApplication : Application() {
    
    val database: PresetDatabase by lazy {
        PresetDatabase.getInstance(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: MoodCamApplication
            private set
    }
}
