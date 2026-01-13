package com.moodcam.camera

import android.content.Context
import android.graphics.SurfaceTexture
import androidx.camera.view.PreviewView
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.extensions.ExtensionMode
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as Camera2Manager
import android.util.Log
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Camera lens info for UI display.
 */
data class CameraLensInfo(
    val id: String,
    val name: String,
    val isFront: Boolean,
    val focalLength: Float
)

/**
 * Manages CameraX camera operations.
 * Handles preview binding to SurfaceTexture for OpenGL rendering.
 */
class CameraManager(private val context: Context) {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    
    private val executor: Executor = ContextCompat.getMainExecutor(context)
    
    // Bind context
    private var lastLifecycleOwner: LifecycleOwner? = null
    private var lastSurfaceTexture: SurfaceTexture? = null
    private var lastPreviewView: PreviewView? = null
    
    // Portrait mode state (Hardware BOKEH)
    private var _isPortraitMode = false
    val isPortraitMode: Boolean get() = _isPortraitMode
    
    // Available cameras (for display only)
    private val _availableCameras = mutableListOf<CameraLensInfo>()
    val availableCameras: List<CameraLensInfo> get() = _availableCameras
    
    /**
     * Initialize camera provider and extensions manager.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        try {
            cameraProvider = suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }, executor)
            }
            
            // Initialize ExtensionsManager
            if (cameraProvider != null) {
                extensionsManager = suspendCancellableCoroutine { continuation ->
                    val future = ExtensionsManager.getInstanceAsync(context, cameraProvider!!)
                    future.addListener({
                        try {
                            continuation.resume(future.get())
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }, executor)
                }
            }
            
            // Enumerate available cameras for display
            enumerateCameras()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera", e)
            false
        }
    }
    
    /**
     * Enumerate all available cameras and their properties.
     */
    private fun enumerateCameras() {
        _availableCameras.clear()
        
        try {
            val camera2Manager = context.getSystemService(Context.CAMERA_SERVICE) as Camera2Manager
            
            for (cameraId in camera2Manager.cameraIdList) {
                val characteristics = camera2Manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: continue
                
                val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focalLength = focalLengths?.firstOrNull() ?: 4.0f
                
                val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT
                
                // Classify camera type based on focal length
                val name = when {
                    isFront -> "Front"
                    focalLength < 2.5f -> "0.5x"
                    focalLength < 4.5f -> "1x"
                    focalLength < 8f -> "3x"
                    else -> "5x"
                }
                
                _availableCameras.add(CameraLensInfo(
                    id = cameraId,
                    name = name,
                    isFront = isFront,
                    focalLength = focalLength
                ))
            }
            
            Log.d(TAG, "Found ${_availableCameras.size} cameras: ${_availableCameras.map { "${it.name}(${it.focalLength}mm)" }}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate cameras", e)
        }
    }
    
    /**
     * Bind camera to lifecycle.
     * Handles switching between GL SurfaceTexture (Standard) and PreviewView (Bokeh).
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        surfaceTexture: SurfaceTexture? = null,
        previewView: PreviewView? = null
    ) {
        lastLifecycleOwner = lifecycleOwner
        if (surfaceTexture != null) lastSurfaceTexture = surfaceTexture
        if (previewView != null) lastPreviewView = previewView
        
        val provider = cameraProvider ?: run {
            Log.e(TAG, "Camera provider not initialized")
            return
        }
        
        // Unbind all first
        provider.unbindAll()
        
        try {
            // Select camera
            val baseCameraSelector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            
            // Check for BOKEH support if requested
            var cameraSelector = baseCameraSelector
            if (_isPortraitMode && extensionsManager != null) {
                if (extensionsManager!!.isExtensionAvailable(baseCameraSelector, ExtensionMode.BOKEH)) {
                    cameraSelector = extensionsManager!!.getExtensionEnabledCameraSelector(
                        baseCameraSelector,
                        ExtensionMode.BOKEH
                    )
                } else {
                    Log.w(TAG, "Bokeh extension not available for this camera")
                    // If bokeh not available, we might want to fallback or disable portrait mode
                    // For now, continuing with standard selector
                }
            }
            
            // Configure preview
            // Use 4:3 aspect ratio for film-like look
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(Surface.ROTATION_0)
                .build()
            
            if (_isPortraitMode && lastPreviewView != null) {
                // Determine if we are using Extension or Standard
                // Extensions require PreviewView
                preview?.setSurfaceProvider(lastPreviewView!!.surfaceProvider)
                Log.d(TAG, "Binding to PreviewView for Portrait Mode")
            } else if (lastSurfaceTexture != null) {
                // GL Rendering path
                lastSurfaceTexture!!.setDefaultBufferSize(1440, 1920) // 3:4 portrait
                val surface = Surface(lastSurfaceTexture)
                
                preview?.setSurfaceProvider { request ->
                    request.provideSurface(surface, executor) { result ->
                        Log.d(TAG, "Surface provided, result: ${result.resultCode}")
                    }
                }
                Log.d(TAG, "Binding to SurfaceTexture for GL Mode")
            }
            
            // Configure image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(Surface.ROTATION_0)
                .setJpegQuality(95)
                .build()
            
            // Bind
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            
            Log.d(TAG, "Camera bound successfully, facing: $lensFacing, portrait: $_isPortraitMode")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
            
            // Fallback if extension binding fails
            if (_isPortraitMode) {
                Log.e(TAG, "Retrying without portrait mode...")
                _isPortraitMode = false
                bindToLifecycle(lifecycleOwner, lastSurfaceTexture, lastPreviewView)
            }
        }
    }
    
    /**
     * Capture a photo.
     * Returns a pair of (Image Bytes, Rotation Degrees).
     */
    suspend fun capturePhoto(): Pair<ByteArray, Int>? = withContext(Dispatchers.Main) {
        val capture = imageCapture ?: return@withContext null
        
        suspendCancellableCoroutine<Pair<ByteArray, Int>?> { continuation ->
            capture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val rotation = image.imageInfo.rotationDegrees
                        image.close()
                        continuation.resume(Pair(bytes, rotation))
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Capture failed", exception)
                        continuation.resume(null)
                    }
                }
            )
        }
    }
    
    /**
     * Switch between front and back camera.
     */
    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        
        if (lastLifecycleOwner != null) {
            bindToLifecycle(lastLifecycleOwner!!)
        }
    }
    
    /**
     * Is front camera currently selected?
     */
    fun isFrontCamera(): Boolean = lensFacing == CameraSelector.LENS_FACING_FRONT
    
    /**
     * Get current lens facing.
     */
    fun getLensFacing(): Int = lensFacing
    
    /**
     * Check if front camera is available.
     */
    fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true
    }
    
    /**
     * Check if back camera is available.
     */
    fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true
    }
    
    /**
     * Tap to focus and lock exposure at specific coordinates.
     * AE is locked until next tap or camera restart.
     */
    fun focusAt(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        val cameraControl = camera?.cameraControl ?: return
        
        val factory = SurfaceOrientedMeteringPointFactory(
            viewWidth.toFloat(),
            viewHeight.toFloat()
        )
        // Create metering point at tap location
        val point = factory.createPoint(x, y, 0.15f)
        
        // Lock AE on this point - no auto-cancel for persistent lock
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
            .disableAutoCancel() // Keep AE locked until next tap
            .build()
        
        cameraControl.startFocusAndMetering(action)
        Log.d(TAG, "AE locked at ($x, $y)")
    }
    
    /**
     * Set exposure compensation in EV steps.
     * Range depends on camera hardware.
     */
    fun setExposureCompensation(ev: Int) {
        val cameraControl = camera?.cameraControl ?: return
        cameraControl.setExposureCompensationIndex(ev)
        Log.d(TAG, "Exposure compensation set to: $ev EV")
    }
    
    /**
     * Get the camera's exposure compensation range.
     * Returns Pair(minEV, maxEV) or null if unavailable.
     */
    fun getExposureRange(): Pair<Int, Int>? {
        val cameraInfo = camera?.cameraInfo ?: return null
        val range = cameraInfo.exposureState.exposureCompensationRange
        return if (range.lower != 0 || range.upper != 0) {
            Pair(range.lower, range.upper)
        } else {
            null
        }
    }
    
    /**
     * Get the exposure compensation step size (typically 1/3 or 1/2 EV).
     */
    fun getExposureStep(): Float {
        val cameraInfo = camera?.cameraInfo ?: return 0.33f
        return cameraInfo.exposureState.exposureCompensationStep.toFloat()
    }
    
    /**
     * Set zoom ratio.
     * For 0.5x (ultra-wide), we use the camera's minimum zoom which activates the wide lens.
     */
    fun setZoom(zoomRatio: Float) {
        val cameraControl = camera?.cameraControl ?: return
        val cameraInfo = camera?.cameraInfo ?: return
        
        // Get the camera's actual zoom range
        val zoomState = cameraInfo.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1f
        val maxZoom = zoomState?.maxZoomRatio ?: 10f
        
        // Map the requested zoom to actual camera capabilities
        // For 0.5x, use min zoom (which activates ultra-wide if available)
        val actualZoom = when {
            zoomRatio <= 0.5f -> minZoom  // Use ultra-wide (min zoom)
            zoomRatio >= maxZoom -> maxZoom
            else -> zoomRatio.coerceIn(minZoom, maxZoom)
        }
        
        Log.d(TAG, "setZoom requested: $zoomRatio, min: $minZoom, max: $maxZoom, actual: $actualZoom")
        cameraControl.setZoomRatio(actualZoom)
    }

    /**
     * Toggle portrait mode (Hardware BOKEH).
     * Returns the new state.
     * Rebinds camera to switch between preview types.
     */
    fun togglePortraitMode(): Boolean {
        // Toggle state
        _isPortraitMode = !_isPortraitMode
        
        // Rebind to apply change
        if (lastLifecycleOwner != null) {
            bindToLifecycle(lastLifecycleOwner!!)
        }
        
        return _isPortraitMode
    }
    
    /**
     * Release camera resources.
     */
    fun release() {
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        lastLifecycleOwner = null
        lastSurfaceTexture = null
        lastPreviewView = null
    }
    
    companion object {
        private const val TAG = "CameraManager"
    }
}
