package com.moodcam.effects

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages shader program compilation and linking.
 */
class ShaderProgram(
    private val context: Context,
    vertexShaderPath: String,
    fragmentShaderPath: String
) {
    var programId: Int = 0
        private set
    
    private var vertexShaderId: Int = 0
    private var fragmentShaderId: Int = 0
    
    init {
        val vertexSource = loadShaderSource(vertexShaderPath)
        val fragmentSource = loadShaderSource(fragmentShaderPath)
        
        vertexShaderId = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        fragmentShaderId = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        
        programId = linkProgram(vertexShaderId, fragmentShaderId)
    }
    
    private fun loadShaderSource(path: String): String {
        return try {
            context.assets.open(path).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load shader: $path", e)
            throw RuntimeException("Failed to load shader: $path", e)
        }
    }
    
    private fun compileShader(type: Int, source: String): Int {
        val shaderId = GLES30.glCreateShader(type)
        if (shaderId == 0) {
            throw RuntimeException("Failed to create shader of type $type")
        }
        
        GLES30.glShaderSource(shaderId, source)
        GLES30.glCompileShader(shaderId)
        
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            val errorLog = GLES30.glGetShaderInfoLog(shaderId)
            GLES30.glDeleteShader(shaderId)
            throw RuntimeException("Shader compilation failed: $errorLog")
        }
        
        return shaderId
    }
    
    private fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
        val programId = GLES30.glCreateProgram()
        if (programId == 0) {
            throw RuntimeException("Failed to create shader program")
        }
        
        GLES30.glAttachShader(programId, vertexShaderId)
        GLES30.glAttachShader(programId, fragmentShaderId)
        GLES30.glLinkProgram(programId)
        
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        
        if (linkStatus[0] == 0) {
            val errorLog = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Program linking failed: $errorLog")
        }
        
        return programId
    }
    
    fun use() {
        GLES30.glUseProgram(programId)
    }
    
    fun getUniformLocation(name: String): Int {
        return GLES30.glGetUniformLocation(programId, name)
    }
    
    fun getAttribLocation(name: String): Int {
        return GLES30.glGetAttribLocation(programId, name)
    }
    
    fun setFloat(name: String, value: Float) {
        val location = getUniformLocation(name)
        if (location != -1) {
            GLES30.glUniform1f(location, value)
        }
    }
    
    fun setInt(name: String, value: Int) {
        val location = getUniformLocation(name)
        if (location != -1) {
            GLES30.glUniform1i(location, value)
        }
    }
    
    fun setVec2(name: String, x: Float, y: Float) {
        val location = getUniformLocation(name)
        if (location != -1) {
            GLES30.glUniform2f(location, x, y)
        }
    }
    
    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        val location = getUniformLocation(name)
        if (location != -1) {
            GLES30.glUniform3f(location, x, y, z)
        }
    }
    
    fun release() {
        if (vertexShaderId != 0) {
            GLES30.glDeleteShader(vertexShaderId)
            vertexShaderId = 0
        }
        if (fragmentShaderId != 0) {
            GLES30.glDeleteShader(fragmentShaderId)
            fragmentShaderId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
    
    companion object {
        private const val TAG = "ShaderProgram"
    }
}
