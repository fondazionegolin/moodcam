package com.moodcam.effects

import android.graphics.Bitmap

/**
 * Utility for calculating image histograms efficiently.
 * Used for real-time histogram display in the curve editor.
 */
object HistogramCalculator {
    
    /**
     * Calculate luminance histogram from a bitmap.
     * Returns an IntArray of 256 values representing the distribution.
     */
    fun calculateLumaHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(256)
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample pixels (skip some for performance on large images)
        val step = maxOf(1, (width * height) / 50000).coerceAtMost(8)
        
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Calculate luminance using standard coefficients
                val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                histogram[luma]++
            }
        }
        
        return histogram
    }
    
    /**
     * Calculate RGB histograms from a bitmap.
     * Returns a Triple of IntArrays (R, G, B), each with 256 values.
     */
    fun calculateRGBHistogram(bitmap: Bitmap): Triple<IntArray, IntArray, IntArray> {
        val rHistogram = IntArray(256)
        val gHistogram = IntArray(256)
        val bHistogram = IntArray(256)
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample pixels for performance
        val step = maxOf(1, (width * height) / 50000).coerceAtMost(8)
        
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                rHistogram[r]++
                gHistogram[g]++
                bHistogram[b]++
            }
        }
        
        return Triple(rHistogram, gHistogram, bHistogram)
    }
    
    /**
     * Calculate histogram for a specific channel.
     * @param channelIndex 0=Luma, 1=Red, 2=Green, 3=Blue
     */
    fun calculateHistogram(bitmap: Bitmap, channelIndex: Int): IntArray {
        return when (channelIndex) {
            0 -> calculateLumaHistogram(bitmap)
            1 -> calculateRGBHistogram(bitmap).first
            2 -> calculateRGBHistogram(bitmap).second
            3 -> calculateRGBHistogram(bitmap).third
            else -> calculateLumaHistogram(bitmap)
        }
    }
}
