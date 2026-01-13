package com.moodcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moodcam.camera.CameraViewModel
import com.moodcam.ui.screens.CameraScreen
import com.moodcam.ui.screens.PermissionScreen
import com.moodcam.ui.theme.MoodCamTheme

class MainActivity : ComponentActivity() {
    
    private var hasPermission by mutableStateOf(false)
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Enable 120Hz for smoother UI navigation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            display?.let { display ->
                display.supportedModes.maxByOrNull { it.refreshRate }?.let { mode ->
                    window.attributes = window.attributes.also { 
                        it.preferredDisplayModeId = mode.modeId 
                    }
                }
            }
        }
        
        // Hide system bars for immersive camera experience
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Check permission
        hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        
        // Handle deep link if present
        handleDeepLink(intent)
        
        setContent {
            MoodCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasPermission) {
                        val viewModel: CameraViewModel = viewModel()
                        
                        // Handle deep link intent updates
                        DisposableEffect(Unit) {
                            val consumer = androidx.core.util.Consumer<android.content.Intent> { intent ->
                                handleDeepLink(intent, viewModel)
                            }
                            addOnNewIntentListener(consumer)
                            onDispose { removeOnNewIntentListener(consumer) }
                        }
                        
                        // Check for pending profile import from onCreate
                        LaunchedEffect(pendingProfileId) {
                            pendingProfileId?.let { id ->
                                importProfile(id, viewModel)
                                pendingProfileId = null
                            }
                        }
                        
                        CameraScreen(viewModel = viewModel)
                    } else {
                        PermissionScreen(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }
            }
        }
    }
    
    private var pendingProfileId: String? = null
    
    // TODO: Update with your server URL
    private val API_BASE = "https://moodcam.golinelli.ai/api"
    
    private fun handleDeepLink(intent: android.content.Intent, viewModel: CameraViewModel? = null) {
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "moodcam" && data.host == "profile") {
                val profileId = data.lastPathSegment
                if (!profileId.isNullOrEmpty()) {
                    if (viewModel != null) {
                        importProfile(profileId, viewModel)
                    } else {
                        pendingProfileId = profileId
                    }
                }
            }
        }
    }
    
    private fun importProfile(profileId: String, viewModel: CameraViewModel) {
        // Fetch profile JSON from server
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("$API_BASE/profiles/$profileId")
            .build()
            
        // Run in background
        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonContent = response.body?.string()
                    if (jsonContent != null) {
                        // Import specifically from deep link
                        runOnUiThread {
                            viewModel.handleImportedPreset(jsonContent)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
