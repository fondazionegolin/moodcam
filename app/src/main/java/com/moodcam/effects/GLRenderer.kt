package com.moodcam.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.moodcam.preset.GrainToneMode
import com.moodcam.preset.PresetParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3.0 renderer for film effects.
 * Handles camera texture input and applies film-like processing.
 */
class GLRenderer(
    private val context: Context
) : GLSurfaceView.Renderer {
    
    // Callbacks
    var onSurfaceTextureAvailable: ((SurfaceTexture) -> Unit)? = null
    
    // Main thread handler for CameraX operations
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Shader program
    private var shaderProgram: ShaderProgram? = null
    
    // Textures
    private var cameraTextureId: Int = 0
    private var lutTextureId: Int = 0
    private var noiseTextureId: Int = 0
    private var maskTextureId: Int = 0  // Segmentation mask texture
    private var surfaceTexture: SurfaceTexture? = null
    
    // Mask update state
    @Volatile
    private var pendingMaskBuffer: ByteBuffer? = null
    @Volatile
    private var pendingMaskWidth: Int = 256
    @Volatile
    private var pendingMaskHeight: Int = 256
    private val maskLock = Object()
    
    // Geometry
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    
    // Dimensions
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    
    // Current parameters
    @Volatile
    private var currentParams: PresetParams = PresetParams()
    
    @Volatile
    private var lutNeedsUpdate: Boolean = true
    
    // Frame time for grain animation
    private var frameTime: Float = 0f
    private val startTime = System.nanoTime()
    
    // Front camera flag for mirror flip
    @Volatile
    var isFrontCamera: Boolean = false
    
    // Portrait mode flag for synthetic bokeh
    @Volatile
    var isPortraitMode: Boolean = false
    
    // Fullscreen quad vertices
    private val vertices = floatArrayOf(
        -1f, -1f, 0f,  // Bottom left
         1f, -1f, 0f,  // Bottom right
        -1f,  1f, 0f,  // Top left
         1f,  1f, 0f   // Top right
    )
    
    // Texture coordinates for back camera (rotated 90° clockwise for portrait)
    private val texCoordsBack = floatArrayOf(
        1f, 1f,  // Bottom left
        1f, 0f,  // Bottom right
        0f, 1f,  // Top left
        0f, 0f   // Top right
    )
    
    // Texture coordinates for front camera (mirrored horizontally)
    private val texCoordsFront = floatArrayOf(
        0f, 1f,  // Bottom left  (flipped X)
        0f, 0f,  // Bottom right (flipped X)
        1f, 1f,  // Top left     (flipped X)
        1f, 0f   // Top right    (flipped X)
    )
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        
        // Initialize geometry buffers
        initGeometry()
        
        // Create camera texture
        cameraTextureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(cameraTextureId)
        
        // Create LUT texture
        lutTextureId = createLutTexture()
        
        // Create noise texture for grain
        noiseTextureId = createNoiseTexture()
        
        // Create mask texture for segmentation
        maskTextureId = createMaskTexture()
        
        // Load and compile shaders
        shaderProgram = ShaderProgram(
            context,
            "shaders/film_shader.vert",
            "shaders/film_combined.frag"
        )
        
        // Notify that surface texture is ready (on main thread for CameraX)
        surfaceTexture?.let { st ->
            mainHandler.post {
                onSurfaceTextureAvailable?.invoke(st)
            }
        }
        
        Log.d(TAG, "Surface created, shaders compiled")
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES30.glViewport(0, 0, width, height)
        Log.d(TAG, "Surface changed: ${width}x${height}")
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Update surface texture
        surfaceTexture?.updateTexImage()
        
        // Update texture coordinates for current camera
        updateTexCoords()
        
        // Update frame time for grain animation
        frameTime = ((System.nanoTime() - startTime) / 1_000_000_000f) % 1000f
        
        // Update LUT if needed
        if (lutNeedsUpdate) {
            updateLutTexture()
            lutNeedsUpdate = false
        }
        
        // Update mask texture if new data available
        updateMaskTextureIfNeeded()
        
        // Clear
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        // Use shader
        shaderProgram?.use()
        
        // Bind camera texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        shaderProgram?.setInt("uCameraTexture", 0)
        
        // Bind LUT texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTextureId)
        shaderProgram?.setInt("uLutTexture", 1)
        
        // Bind noise texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, noiseTextureId)
        shaderProgram?.setInt("uNoiseTexture", 2)
        
        // Bind mask texture (unit 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
        shaderProgram?.setInt("uMaskTexture", 3)
        
        // Set uniforms from current params
        val params = currentParams
        shaderProgram?.apply {
            setFloat("uExposureEV", params.exposureEV)
            setFloat("uTemperatureK", params.temperatureK.toFloat())
            setFloat("uTint", params.tint)
            setFloat("uSaturation", params.saturation)
            setFloat("uVibrance", params.vibrance)
            setFloat("uFade", params.fade)
            setFloat("uContrast", params.contrast)
            
            // Tone controls
            setFloat("uHighlights", params.highlights)
            setFloat("uMidtones", params.midtones)
            setFloat("uShadows", params.shadows)
            
            setFloat("uGrainStrength", params.grain.strength)
            setFloat("uGrainSize", params.grain.size)
            setInt("uGrainToneMode", params.grain.toneMode.ordinal)
            
            setFloat("uVignette", params.effects.vignette)
            setFloat("uBloom", params.effects.bloom)
            setFloat("uHalation", params.effects.halation)
            setFloat("uClarity", params.clarity)
            
            setVec2("uResolution", viewportWidth.toFloat(), viewportHeight.toFloat())
            setFloat("uTime", frameTime)
            
            // Portrait mode (synthetic bokeh)
            setInt("uPortraitMode", if (isPortraitMode) 1 else 0)
        }
        
        // Set up vertex attributes
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)
        
        // Draw
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        
        // Cleanup
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }
    
    /**
     * Update current preset parameters. Thread-safe.
     */
    fun updateParams(params: PresetParams) {
        currentParams = params
        lutNeedsUpdate = true
    }
    
    /**
     * Get the surface texture for camera binding.
     */
    fun getSurfaceTexture(): SurfaceTexture? = surfaceTexture
    
    private fun initGeometry() {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)
        
        // Start with back camera coordinates
        texCoordBuffer = ByteBuffer.allocateDirect(texCoordsBack.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoordsBack)
        texCoordBuffer.position(0)
    }
    
    /**
     * Update texture coordinates based on camera direction.
     */
    private fun updateTexCoords() {
        val coords = if (isFrontCamera) texCoordsFront else texCoordsBack
        texCoordBuffer.clear()
        texCoordBuffer.put(coords)
        texCoordBuffer.position(0)
    }
    
    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        
        return textures[0]
    }
    
    private fun createLutTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        
        // Initialize with identity LUT
        updateLutTexture()
        
        return textures[0]
    }
    
    private fun updateLutTexture() {
        val curves = currentParams.curves
        val lutData = LUTGenerator.generateLUT(
            curves.lumaPoints,
            curves.rPoints,
            curves.gPoints,
            curves.bPoints,
            curves.lutResolution
        )
        
        val buffer = ByteBuffer.allocateDirect(lutData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(lutData)
        buffer.position(0)
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTextureId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA16F,
            curves.lutResolution,
            4,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_FLOAT,
            buffer
        )
    }
    
    private fun createNoiseTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        
        // Generate professional RGBA packed grain texture
        // R: fine grain A, G: fine grain B, B: clumps, A: macro-field
        val size = 256 // Smaller size but RGBA
        val noiseData = NoiseGenerator.generateFilmGrainTexture(size)
        
        val buffer = ByteBuffer.allocateDirect(noiseData.size)
            .order(ByteOrder.nativeOrder())
            .put(noiseData)
        buffer.position(0)
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        
        // RGBA texture for packed grain data
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            size,
            size,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )
        
        Log.d(TAG, "Noise texture created: RGBA ${size}x${size}")
        return textures[0]
    }
    
    /**
     * Create the segmentation mask texture.
     * Initialized with a default white texture (no blur).
     */
    private fun createMaskTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        
        // Initialize with a white texture (255 = keep sharp everywhere)
        val size = 256
        val defaultMask = ByteBuffer.allocateDirect(size * size)
        for (i in 0 until size * size) {
            defaultMask.put(255.toByte())  // White = subject = keep sharp
        }
        defaultMask.position(0)
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_R8,
            size,
            size,
            0,
            GLES30.GL_RED,
            GLES30.GL_UNSIGNED_BYTE,
            defaultMask
        )
        
        Log.d(TAG, "Mask texture created: ${textures[0]}")
        return textures[0]
    }
    
    /**
     * Update the segmentation mask texture with new data.
     * Called from the main thread with data from SegmentationProcessor.
     * Thread-safe: the actual GPU update happens on the GL thread.
     */
    fun updateMask(maskBuffer: ByteBuffer, width: Int, height: Int) {
        synchronized(maskLock) {
            // Make a copy of the buffer for the GL thread
            val copy = ByteBuffer.allocateDirect(width * height)
            maskBuffer.rewind()
            copy.put(maskBuffer)
            copy.rewind()
            
            pendingMaskBuffer = copy
            pendingMaskWidth = width
            pendingMaskHeight = height
        }
    }
    
    /**
     * Upload pending mask data to GPU texture.
     * Must be called on the GL thread (from onDrawFrame).
     */
    private fun updateMaskTextureIfNeeded() {
        val buffer: ByteBuffer?
        val width: Int
        val height: Int
        
        synchronized(maskLock) {
            buffer = pendingMaskBuffer
            width = pendingMaskWidth
            height = pendingMaskHeight
            pendingMaskBuffer = null
        }
        
        if (buffer != null) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_R8,
                width,
                height,
                0,
                GLES30.GL_RED,
                GLES30.GL_UNSIGNED_BYTE,
                buffer
            )
        }
    }
    
    // Capture state
    @Volatile
    private var captureRequested = false
    private var capturedBitmap: Bitmap? = null
    private val captureLock = Object()
    
    /**
     * Request a frame capture from the GL thread.
     * This MUST be called from the GL thread (e.g., via queueEvent).
     */
    fun requestCapture() {
        captureRequested = true
    }
    
    /**
     * Get the captured bitmap (call after requestCapture on next frame).
     */
    fun getCapturedBitmap(): Bitmap? {
        synchronized(captureLock) {
            val bitmap = capturedBitmap
            capturedBitmap = null
            return bitmap
        }
    }
    
    /**
     * Capture the current frame to a bitmap.
     * Reads pixels from the framebuffer after rendering.
     */
    fun captureFrame(width: Int, height: Int): Bitmap? {
        return try {
            val buffer = IntBuffer.allocate(width * height)
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            
            val pixels = buffer.array()
            
            // Convert ABGR to ARGB and flip vertically
            val flippedPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val srcIdx = (height - 1 - y) * width + x
                    val dstIdx = y * width + x
                    val pixel = pixels[srcIdx]
                    
                    // RGBA -> ARGB
                    val r = (pixel and 0xFF)
                    val g = (pixel shr 8) and 0xFF
                    val b = (pixel shr 16) and 0xFF
                    val a = (pixel shr 24) and 0xFF
                    
                    flippedPixels[dstIdx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            Bitmap.createBitmap(flippedPixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture frame", e)
            null
        }
    }
    
    fun release() {
        surfaceTexture?.release()
        shaderProgram?.release()
        
        val textures = intArrayOf(cameraTextureId, lutTextureId, noiseTextureId)
        GLES30.glDeleteTextures(3, textures, 0)
    }
    
    companion object {
        private const val TAG = "GLRenderer"
    }
}
