package com.moodcam.effects

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generates high-quality procedural noise textures for authentic film grain.
 * Creates RGBA packed texture:
 * - R: fine grain variant A (blue-noise like)
 * - G: fine grain variant B (offset pattern)
 * - B: clumps base (medium frequency, for aggregates)
 * - A: macro-field (low frequency for spatial non-uniformity)
 */
object NoiseGenerator {
    
    /**
     * Generate a high-quality RGBA film grain texture.
     * Uses blue-noise-like distribution for fine grain and
     * separate layers for clumps and macro variation.
     */
    fun generateFilmGrainTexture(size: Int, seed: Long = System.currentTimeMillis()): ByteArray {
        val random = Random(seed)
        val data = ByteArray(size * size * 4) // RGBA
        
        // Generate different noise layers
        val fineA = generateBlueNoiseLike(size, random)
        val fineB = generateBlueNoiseLike(size, Random(seed + 1))
        val clumps = generateClumpNoise(size, Random(seed + 2))
        val macro = generateMacroField(size, Random(seed + 3))
        
        // Pack into RGBA
        for (i in 0 until size * size) {
            data[i * 4 + 0] = ((fineA[i] * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255).toByte()
            data[i * 4 + 1] = ((fineB[i] * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255).toByte()
            data[i * 4 + 2] = ((clumps[i] * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255).toByte()
            data[i * 4 + 3] = ((macro[i] * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255).toByte()
        }
        
        return data
    }
    
    /**
     * Generate blue-noise-like pattern using void and cluster method approximation.
     * Creates high-frequency noise with minimal low-frequency energy.
     */
    private fun generateBlueNoiseLike(size: Int, random: Random): FloatArray {
        val result = FloatArray(size * size)
        
        // Start with white noise
        for (i in result.indices) {
            result[i] = random.nextFloat() * 2f - 1f
        }
        
        // Apply high-pass filter to remove low frequencies
        // This creates blue-noise-like characteristics
        val filtered = highPassFilter(result, size, 3)
        
        // Add some structured variation using dithered pattern
        val dither = generateDitherPattern(size)
        for (i in result.indices) {
            result[i] = filtered[i] * 0.7f + dither[i] * 0.3f
        }
        
        // Normalize
        return normalize(result)
    }
    
    /**
     * Generate clump noise - medium frequency with non-linear response.
     */
    private fun generateClumpNoise(size: Int, random: Random): FloatArray {
        // Generate perlin-like noise at medium scale
        val noise = generatePerlinOctaves(size, 2, 4f, random)
        
        // Apply non-linear curve to create "clumpy" distribution
        val result = FloatArray(noise.size)
        for (i in noise.indices) {
            // Threshold-like curve for clumping
            val v = noise[i]
            result[i] = if (v > 0.1f) {
                smoothstep(0.1f, 0.8f, v) * 2f - 1f
            } else {
                -0.5f + v * 2f
            }
        }
        
        return normalize(result)
    }
    
    /**
     * Generate macro-field - very low frequency for spatial non-uniformity.
     */
    private fun generateMacroField(size: Int, random: Random): FloatArray {
        // Very low frequency noise
        return normalize(generatePerlinOctaves(size, 1, 1f, random))
    }
    
    /**
     * Generate Perlin-like noise with octaves.
     */
    private fun generatePerlinOctaves(
        size: Int,
        octaves: Int,
        baseFrequency: Float,
        random: Random
    ): FloatArray {
        val result = FloatArray(size * size)
        
        var amplitude = 1f
        var frequency = baseFrequency
        var maxValue = 0f
        
        for (octave in 0 until octaves) {
            val wavelength = (size / frequency).toInt().coerceAtLeast(2)
            val grid = generateNoiseGrid(wavelength + 2, random)
            
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val sampleX = x.toFloat() / size * (wavelength - 1)
                    val sampleY = y.toFloat() / size * (wavelength - 1)
                    
                    val value = bilinearInterpolate(grid, sampleX, sampleY, wavelength + 2)
                    result[y * size + x] += value * amplitude
                }
            }
            
            maxValue += amplitude
            amplitude *= 0.5f
            frequency *= 2f
        }
        
        // Normalize by max possible value
        for (i in result.indices) {
            result[i] /= maxValue
        }
        
        return result
    }
    
    /**
     * Generate a grid of random values.
     */
    private fun generateNoiseGrid(size: Int, random: Random): FloatArray {
        return FloatArray(size * size) { random.nextFloat() * 2f - 1f }
    }
    
    /**
     * Bilinear interpolation with smoothstep.
     */
    private fun bilinearInterpolate(grid: FloatArray, x: Float, y: Float, gridWidth: Int): Float {
        val x0 = x.toInt().coerceIn(0, gridWidth - 2)
        val y0 = y.toInt().coerceIn(0, gridWidth - 2)
        val x1 = x0 + 1
        val y1 = y0 + 1
        
        val fx = smoothstep(0f, 1f, x - x0)
        val fy = smoothstep(0f, 1f, y - y0)
        
        val v00 = grid[y0 * gridWidth + x0]
        val v10 = grid[y0 * gridWidth + x1]
        val v01 = grid[y1 * gridWidth + x0]
        val v11 = grid[y1 * gridWidth + x1]
        
        val v0 = v00 + (v10 - v00) * fx
        val v1 = v01 + (v11 - v01) * fx
        
        return v0 + (v1 - v0) * fy
    }
    
    /**
     * High-pass filter to create blue-noise characteristics.
     */
    private fun highPassFilter(data: FloatArray, size: Int, kernelRadius: Int): FloatArray {
        val result = FloatArray(data.size)
        val blurred = FloatArray(data.size)
        
        // Simple box blur
        val kernelSize = kernelRadius * 2 + 1
        val kernelArea = kernelSize * kernelSize
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                var sum = 0f
                for (ky in -kernelRadius..kernelRadius) {
                    for (kx in -kernelRadius..kernelRadius) {
                        val sx = (x + kx + size) % size
                        val sy = (y + ky + size) % size
                        sum += data[sy * size + sx]
                    }
                }
                blurred[y * size + x] = sum / kernelArea
            }
        }
        
        // Subtract blur from original (high-pass)
        for (i in data.indices) {
            result[i] = data[i] - blurred[i]
        }
        
        return result
    }
    
    /**
     * Generate Bayer-like dither pattern.
     */
    private fun generateDitherPattern(size: Int): FloatArray {
        val result = FloatArray(size * size)
        val pattern = floatArrayOf(
            0f, 8f, 2f, 10f,
            12f, 4f, 14f, 6f,
            3f, 11f, 1f, 9f,
            15f, 7f, 13f, 5f
        )
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val px = x % 4
                val py = y % 4
                result[y * size + x] = (pattern[py * 4 + px] / 15f) * 2f - 1f
            }
        }
        
        return result
    }
    
    /**
     * Smoothstep interpolation.
     */
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    
    /**
     * Normalize array to -1..1 range.
     */
    private fun normalize(data: FloatArray): FloatArray {
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        
        for (v in data) {
            if (v < min) min = v
            if (v > max) max = v
        }
        
        val range = max - min
        if (range < 0.001f) return data
        
        val result = FloatArray(data.size)
        for (i in data.indices) {
            result[i] = ((data[i] - min) / range) * 2f - 1f
        }
        
        return result
    }
    
    /**
     * Legacy single-channel texture for backwards compatibility.
     */
    fun generateFilmGrainTextureSingleChannel(size: Int, seed: Long = 42L): ByteArray {
        val random = Random(seed)
        val data = ByteArray(size * size)
        
        val noise = generateBlueNoiseLike(size, random)
        
        for (i in data.indices) {
            data[i] = ((noise[i] * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255).toByte()
        }
        
        return data
    }
}
