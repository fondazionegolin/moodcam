package com.moodcam.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import coil.compose.AsyncImage
import com.moodcam.R
import com.moodcam.camera.CameraManager
import com.moodcam.camera.CameraViewModel
import com.moodcam.effects.GLRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Main camera screen with preview, controls, and bottom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val uiState by viewModel.uiState.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val activeParams by viewModel.activeParams.collectAsState()
    val allPresets by viewModel.allPresets.collectAsState()
    
    // Camera and renderer
    val cameraManager = remember { CameraManager(context) }
    val glRenderer = remember { GLRenderer(context) }
    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }
    // PreviewView for hardware bokeh
    var previewView by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    
    // Zoom levels for multi-lens
    val zoomLevels = listOf("0.5x" to 0.5f, "1x" to 1f, "3x" to 3f, "5x" to 5f)
    var selectedZoomIndex by remember { mutableIntStateOf(1) }
    
    // Coroutine scope for capture
    val scope = rememberCoroutineScope()
    
    // Focus indicator state
    var focusIndicatorPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusIndicator by remember { mutableStateOf(false) }
    
    // Photo thumbnail blur animation
    var thumbnailBlur by remember { mutableStateOf(16.dp) }
    
    // Shutter iris animation
    var showShutterAnimation by remember { mutableStateOf(false) }
    val shutterProgress = remember { Animatable(0f) }
    
    // Exposure compensation slider state
    var showExposureSlider by remember { mutableStateOf(false) }
    var exposureCompensation by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    // Haptic vibrator
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }
    }
    
    fun hapticClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(30)
        }
    }
    
    // State for portrait mode
    var isPortraitModeActive by remember { mutableStateOf(false) }
    
    // JSON file picker for preset import
    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Read JSON content from selected file
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val jsonContent = inputStream.bufferedReader().readText()
                        viewModel.handleImportedPreset(jsonContent)
                    }
                } catch (e: Exception) {
                    viewModel.cancelImport()
                }
            }
        } ?: viewModel.cancelImport()
    }
    
    // Launch file picker when import is requested
    LaunchedEffect(uiState.showImportPicker) {
        if (uiState.showImportPicker) {
            // Filter for JSON files only
            jsonPickerLauncher.launch(arrayOf("application/json"))
        }
    }

    // Initialize camera
    LaunchedEffect(Unit) {
        cameraManager.initialize()
    }
    
    // Sync zoom level on lifecycle resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Reapply zoom when returning to app
                val currentZoom = zoomLevels[selectedZoomIndex].second
                cameraManager.setZoom(currentZoom)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Bind camera when surfaces are ready
    LaunchedEffect(glSurfaceView, previewView, lifecycleOwner) {
        if (glSurfaceView != null || previewView != null) {
            val surfaceTexture = glSurfaceView?.holder?.surface?.let { 
                // We need the SurfaceTexture, but GLSurfaceView hides it.
                // Actually GLRenderer creates the SurfaceTexture.
                // We need to wait for GLRenderer to create it.
                null 
            }
            
            // We rely on the GLRenderer callback for binding
        }
    }
    
    // Handle renderer surface creation callback
    LaunchedEffect(glRenderer) {
        glRenderer.onSurfaceTextureAvailable = { surfaceTexture ->
            cameraManager.bindToLifecycle(lifecycleOwner, surfaceTexture, previewView)
        }
    }
    
    // Rebind when previewView becomes available
    LaunchedEffect(previewView) {
        if (previewView != null) {
            cameraManager.bindToLifecycle(lifecycleOwner, null, previewView)
        }
    }
    
    // Update renderer params
    LaunchedEffect(activeParams, uiState.previewExposure) {
        val combinedParams = activeParams.copy(
            exposureEV = activeParams.exposureEV + uiState.previewExposure
        )
        glRenderer.updateParams(combinedParams)
        glSurfaceView?.requestRender()
    }
    
    // Hide focus indicator after delay
    LaunchedEffect(showFocusIndicator) {
        if (showFocusIndicator) {
            delay(1500)
            showFocusIndicator = false
        }
    }
    
    // Animate thumbnail blur when new photo is captured
    LaunchedEffect(uiState.lastPhotoUri) {
        if (uiState.lastPhotoUri != null) {
            thumbnailBlur = 16.dp
            delay(100)
            thumbnailBlur = 0.dp
        }
    }
    
    // Snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top spacer with preset name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                selectedPreset?.let { preset ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Yellow,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            
            // 3:4 aspect ratio camera preview frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            ) {
                // Camera Preview (Hybrid)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    hapticClick()
                                    focusIndicatorPosition = offset
                                    showFocusIndicator = true
                                    
                                    // Lock AE on tap point
                                    cameraManager.focusAt(
                                        offset.x,
                                        offset.y,
                                        size.width,
                                        size.height
                                    )
                                    
                                    // Show exposure slider and get range
                                    exposureRange = cameraManager.getExposureRange()
                                    exposureCompensation = 0
                                    cameraManager.setExposureCompensation(0)
                                    showExposureSlider = true
                                }
                            )
                        }
                ) {
                    // 1. GLSurfaceView for Standard Mode (Film Effects)
                    if (!isPortraitModeActive) {
                        AndroidView(
                            factory = { ctx ->
                                GLSurfaceView(ctx).apply {
                                    setEGLContextClientVersion(3)
                                    setRenderer(glRenderer)
                                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                    glSurfaceView = this
                                    
                                    // Handle renderer callback manually if needed, but we do it in LaunchedEffect
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // 2. PreviewView for Portrait Mode (Hardware Bokeh)
                    if (isPortraitModeActive) {
                        AndroidView(
                            factory = { ctx ->
                                androidx.camera.view.PreviewView(ctx).apply {
                                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                                    previewView = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Focus indicator
                if (showFocusIndicator && focusIndicatorPosition != null) {
                    FocusIndicator(
                        position = focusIndicatorPosition!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Horizontal exposure slider at focus point
                if (showExposureSlider && exposureRange != null && focusIndicatorPosition != null) {
                    val range = exposureRange!!
                    val focusPos = focusIndicatorPosition!!
                    val density = LocalDensity.current.density
                    
                    // Position slider below focus circle
                    val sliderWidth = 160.dp
                    val sliderHeight = 70.dp
                    
                    // Track previous value for haptic tick feedback
                    var lastHapticEv by remember { mutableIntStateOf(exposureCompensation) }
                    
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (focusPos.x / density).dp - sliderWidth / 2,
                                y = (focusPos.y / density).dp + 35.dp
                            )
                            .width(sliderWidth)
                            .height(sliderHeight)
                            .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // EV VALUE DISPLAY ON TOP (so finger doesn't cover)
                            val evStep = cameraManager.getExposureStep()
                            val evValue = exposureCompensation * evStep
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Exposure,
                                    contentDescription = "Exposure",
                                    tint = Color.Yellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format("%+.1f EV", evValue),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.Yellow
                                )
                            }
                            
                            // SLIDER AT BOTTOM
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Sun icon (darker)
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = "Darker",
                                    tint = Color.Yellow.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                                
                                val normalizedValue = (exposureCompensation - range.first).toFloat() / 
                                    (range.second - range.first).toFloat().coerceAtLeast(1f)
                                
                                Slider(
                                    value = normalizedValue,
                                    onValueChange = { newValue ->
                                        val newEv = (range.first + newValue * (range.second - range.first)).toInt()
                                        val clampedEv = newEv.coerceIn(range.first, range.second)
                                        
                                        // Quantized haptic feedback - tick every EV step change
                                        if (clampedEv != lastHapticEv) {
                                            lastHapticEv = clampedEv
                                            // Haptic tick feedback
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator?.vibrate(10)
                                            }
                                        }
                                        
                                        exposureCompensation = clampedEv
                                        cameraManager.setExposureCompensation(exposureCompensation)
                                    },
                                    onValueChangeFinished = {
                                        // Start auto-hide timer only when slider is released
                                        scope.launch {
                                            delay(3000)
                                            showExposureSlider = false
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Yellow,
                                        activeTrackColor = Color.Yellow,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                
                                // Sun icon (brighter)
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = "Brighter",
                                    tint = Color.Yellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                
                // Rule of thirds grid
                RuleOfThirdsGrid(modifier = Modifier.fillMaxSize())
                
                // Iris shutter animation overlay
                if (showShutterAnimation) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val maxRadius = maxOf(size.width, size.height)
                        // Iris closes from maxRadius to 0 as progress goes from 0 to 1
                        val currentRadius = maxRadius * (1f - shutterProgress.value)
                        
                        // Draw black with circular cutout
                        drawRect(Color.Black)
                        drawCircle(
                            color = Color.Transparent,
                            radius = currentRadius,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Zoom selector row with camera switch button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera switch button (front/back)
                IconButton(
                    onClick = {
                        hapticClick()
                        cameraManager.switchCamera()
                        glRenderer.isFrontCamera = cameraManager.isFrontCamera()
                        selectedZoomIndex = 1
                        cameraManager.setZoom(1f)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Zoom buttons
                zoomLevels.forEachIndexed { index, (label, zoomRatio) ->
                    val isSelected = index == selectedZoomIndex
                    ZoomButton(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            hapticClick()
                            selectedZoomIndex = index
                            cameraManager.setZoom(zoomRatio)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Portrait mode button (Hardware Bokeh)
                IconButton(
                    onClick = {
                        hapticClick()
                        isPortraitModeActive = cameraManager.togglePortraitMode()
                        // No need to set isPortraitMode on GLRenderer anymore
                        // as we switch views completely
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isPortraitModeActive) Color.White else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Portrait,
                        contentDescription = "Portrait Mode",
                        tint = if (isPortraitModeActive) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Exposure slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Exposure",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                
                // Track for haptic ticks
                var lastExpHaptic by remember { mutableFloatStateOf(uiState.previewExposure) }
                
                Slider(
                    value = uiState.previewExposure,
                    onValueChange = { newValue ->
                        // Haptic tick every 0.2 EV
                        if (kotlin.math.abs(newValue - lastExpHaptic) >= 0.2f) {
                            lastExpHaptic = newValue
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(10)
                            }
                        }
                        viewModel.setPreviewExposure(newValue)
                    },
                    valueRange = -2f..2f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    text = String.format("%+.1f", uiState.previewExposure),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(36.dp)
                )
            }
            
            // Frame slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Frame",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                
                // Track for haptic ticks
                var lastFrameHaptic by remember { mutableFloatStateOf(uiState.frameWidth) }
                
                Slider(
                    value = uiState.frameWidth,
                    onValueChange = { newValue -> 
                        // Haptic tick every 0.3 units
                        if (kotlin.math.abs(newValue - lastFrameHaptic) >= 0.3f) {
                            lastFrameHaptic = newValue
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(10)
                            }
                        }
                        viewModel.setFrameWidth(newValue)
                    },
                    valueRange = -3f..3f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    text = when {
                        uiState.frameWidth > 0.1f -> "W"
                        uiState.frameWidth < -0.1f -> "B"
                        else -> "—"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Bottom control bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Film Library button
                IconButton(
                    onClick = { 
                        hapticClick()
                        viewModel.showLibrary() 
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_effects),
                        contentDescription = "Film Library",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Shutter button
                ShutterButton(
                    isCapturing = uiState.isCapturing,
                    onClick = {
                        hapticClick()
                        // Trigger iris shutter animation
                        scope.launch {
                            showShutterAnimation = true
                            // Close iris (faster)
                            shutterProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(80, easing = FastOutSlowInEasing)
                            )
                            // Small pause at closed position
                            delay(30)
                            // Open iris back (faster)
                            shutterProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(100, easing = FastOutLinearInEasing)
                            )
                            showShutterAnimation = false
                        }
                        
                        // Use unified high-res capture flow
                        scope.launch {
                            val result = cameraManager.capturePhoto()
                            if (result != null) {
                                val (photoBytes, rotation) = result
                                var rawBitmap = android.graphics.BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
                                
                                // Rotate if needed
                                if (rotation != 0) {
                                    val matrix = android.graphics.Matrix()
                                    matrix.postRotate(rotation.toFloat())
                                    val rotated = android.graphics.Bitmap.createBitmap(
                                        rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                    )
                                    if (rotated != rawBitmap) {
                                        rawBitmap.recycle()
                                        rawBitmap = rotated
                                    }
                                }
                            
                                // Apply Film Effect if preset selected
                                val processedBitmap = if (selectedPreset != null) {
                                    val tempPreset = selectedPreset!!.copy(params = activeParams)
                                    com.moodcam.effects.FilmPostProcessor(context).process(rawBitmap, tempPreset)
                                } else {
                                    rawBitmap
                                }
                                
                                // Save via ViewModel
                                viewModel.capturePhoto(processedBitmap, uiState.frameWidth)
                            }
                        }
                    }
                )
                // Last photo thumbnail (bottom right)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable {
                            hapticClick()
                            // Open gallery to view the last photo
                            uiState.lastPhotoUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.lastPhotoUri != null) {
                        AsyncImage(
                            model = uiState.lastPhotoUri,
                            contentDescription = "Last Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(thumbnailBlur)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "No Photo",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        
        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
        
        // Film Library Bottom Sheet
        if (uiState.showLibrary) {
            FilmLibrarySheet(
                presets = allPresets,
                selectedPresetId = selectedPreset?.id,
                onPresetSelected = { viewModel.selectPreset(it) },
                onPresetEdit = { viewModel.startEditing(it) },
                onPresetDuplicate = { viewModel.duplicatePreset(it) },
                onPresetDelete = { viewModel.deletePreset(it) },
                onPresetExport = { preset ->
                    // TODO: Export preset to JSON file
                    viewModel.exportPreset(preset)
                },
                onImportPreset = {
                    // TODO: Import preset from JSON file
                    viewModel.importPreset()
                },
                onCreateNew = { viewModel.createNewPreset() },
                onDismiss = { viewModel.hideLibrary() }
            )
        }
        
        // Editor Bottom Sheet
        if (uiState.showEditor) {
            FilmEditorSheet(
                params = activeParams,
                presetName = viewModel.editingPreset.collectAsState().value?.name ?: "",
                isBuiltIn = viewModel.editingPreset.collectAsState().value?.type == com.moodcam.preset.PresetType.BUILTIN,
                onParamsChanged = { viewModel.updateActiveParams(it) },
                onSave = { viewModel.saveCurrentPreset() },
                onSaveAsNew = { name -> viewModel.saveAsNewPreset(name) },
                onExport = {
                    // Export current editing preset
                    viewModel.editingPreset.value?.let { viewModel.exportPreset(it) }
                },
                onReset = { viewModel.resetActiveParams() },
                onDismiss = { viewModel.cancelEditing() }
            )
        }
    }
}

/**
 * Zoom level selector button.
 */
@Composable
private fun ZoomButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color.White else Color.Transparent,
        shape = CircleShape,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(40.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Focus indicator with animation.
 */
@Composable
private fun FocusIndicator(
    position: Offset,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = 0.8f,
        animationSpec = tween(200),
        label = "focus_alpha"
    )
    
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (position.x - 12.dp.toPx()).toInt(),
                        (position.y - 12.dp.toPx()).toInt()
                    )
                }
                .size(24.dp)
                .alpha(alpha)
                .border(2.dp, Color.Yellow, CircleShape)
        )
    }
}

/**
 * Shutter button component.
 */
@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = !isCapturing,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    if (isCapturing) Color.Gray else Color.White
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            )
        }
    }
}

/**
 * Rule of thirds grid overlay.
 */
@Composable
private fun RuleOfThirdsGrid(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 1.dp.toPx()
        val gridColor = Color.White.copy(alpha = 0.3f)
        
        // Vertical lines (1/3 and 2/3)
        drawLine(
            color = gridColor,
            start = Offset(size.width / 3, 0f),
            end = Offset(size.width / 3, size.height),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = gridColor,
            start = Offset(size.width * 2 / 3, 0f),
            end = Offset(size.width * 2 / 3, size.height),
            strokeWidth = strokeWidth
        )
        
        // Horizontal lines (1/3 and 2/3)
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height / 3),
            end = Offset(size.width, size.height / 3),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height * 2 / 3),
            end = Offset(size.width, size.height * 2 / 3),
            strokeWidth = strokeWidth
        )
    }
}
