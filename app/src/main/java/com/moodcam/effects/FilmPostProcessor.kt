package com.moodcam.effects

import android.content.Context
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.moodcam.preset.FilmPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Applies film effects to a static Bitmap using OpenGL.
 * Runs on a temporary EGL context.
 */
class FilmPostProcessor(private val context: Context) {

    /**
     * Apply film preset to a bitmap.
     * Returns a new Bitmap with effects applied.
     */
    fun process(inputBitmap: Bitmap, preset: FilmPreset): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        
        // 1. Setup EGL
        val egl = EglHelper()
        egl.initialize(width, height)
        egl.makeCurrent()
        
        try {
            // 2. Compile Shader (using photo variant with sampler2D)
            val shader = ShaderProgram(
                context,
                "shaders/film_shader.vert", // Reusing vertex shader
                "shaders/film_photo.frag"   // Using photo variant
            )
            
            // 3. Setup Geometry (Full screen quad)
            val vertexBuffer = createFloatBuffer(floatArrayOf(
                -1f, -1f, 0f, 0f, 0f,
                 1f, -1f, 0f, 1f, 0f,
                -1f,  1f, 0f, 0f, 1f,
                 1f,  1f, 0f, 1f, 1f
            ))
            
            // 4. Upload Textures
            val inputTexture = loadBitmapTexture(inputBitmap)
            
            // LUT Texture
            val lutTexture = createLutTexture(preset)
            
            // Noise Texture
            val noiseTexture = createNoiseTexture()
            
            // 5. Render
            GLES30.glViewport(0, 0, width, height)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            
            shader.use()
            
            // Bind textures
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
            shader.setInt("uCameraTexture", 0)
            
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTexture)
            shader.setInt("uLutTexture", 1)
            
            GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, noiseTexture)
            shader.setInt("uNoiseTexture", 2)
            
            // Set Uniforms (Mapped correctly from PresetParams)
            shader.setVec2("uResolution", width.toFloat(), height.toFloat())
            shader.setFloat("uTime", 0f) // Static grain for photo
            
            val p = preset.params
            shader.setFloat("uExposureEV", p.exposureEV)
            shader.setFloat("uTemperatureK", p.temperatureK.toFloat())
            shader.setFloat("uTint", p.tint)
            shader.setFloat("uSaturation", p.saturation)
            shader.setFloat("uVibrance", p.vibrance)
            shader.setFloat("uFade", p.fade)
            shader.setFloat("uContrast", p.contrast)
            shader.setFloat("uHighlights", p.highlights)
            shader.setFloat("uMidtones", p.midtones)
            shader.setFloat("uShadows", p.shadows)
            
            shader.setFloat("uGrainStrength", p.grain.strength)
            shader.setFloat("uGrainSize", p.grain.size)
            shader.setInt("uGrainToneMode", p.grain.toneMode.ordinal)
            
            shader.setFloat("uVignette", p.effects.vignette)
            shader.setFloat("uBloom", p.effects.bloom)
            shader.setFloat("uHalation", p.effects.halation)
            shader.setFloat("uClarity", p.clarity)
            
            // Disable portrait processing in shader (photo already has bokeh or is flat)
            shader.setInt("uPortraitMode", 0)
            
            // Draw
            val aPosition = shader.getAttribLocation("aPosition")
            val aTexCoord = shader.getAttribLocation("aTexCoord")
            
            vertexBuffer.position(0)
            GLES30.glVertexAttribPointer(aPosition, 3, GLES30.GL_FLOAT, false, 20, vertexBuffer)
            GLES30.glEnableVertexAttribArray(aPosition)
            
            vertexBuffer.position(3)
            GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 20, vertexBuffer)
            GLES30.glEnableVertexAttribArray(aTexCoord)
            
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            
            // 6. Read Pixels
            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val buffer = ByteBuffer.allocateDirect(width * height * 4)
            buffer.order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            resultBitmap.copyPixelsFromBuffer(buffer)
            
            // Cleanup
            GLES30.glDeleteTextures(3, intArrayOf(inputTexture, lutTexture, noiseTexture), 0)
            shader.release()
            
            return resultBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Post-processing failed", e)
            return inputBitmap // Return original on failure
        } finally {
            egl.release()
        }
    }
    
    private fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(coords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.position(0)
        return fb
    }
    
    private fun loadBitmapTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        return textures[0]
    }
    
    private fun createLutTexture(preset: FilmPreset): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        
        val curves = preset.params.curves
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
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA16F,
            curves.lutResolution,
            4,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_FLOAT,
            buffer // This is a FloatBuffer, but glTexImage2D expects Buffer. FloatBuffer implements Buffer.
        )
        
        return textures[0]
    }
    
    private fun createNoiseTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        
        // Generate professional RGBA packed grain texture
        // R: fine grain A, G: fine grain B, B: clumps, A: macro-field
        val size = 256
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
        
        return textures[0]
    }
    
    // Minimal EGL Helper class
    private class EglHelper {
        private var eglDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface = EGL14.EGL_NO_SURFACE
        
        fun initialize(width: Int, height: Int) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
            
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, // ES2 bit compatible with ES3
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0)
        }
        
        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }
        
        fun release() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
    
    companion object {
        private const val TAG = "FilmPostProcessor"
    }
}
