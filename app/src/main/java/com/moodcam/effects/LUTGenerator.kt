package com.moodcam.effects

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Generates 1D LUT textures from curve control points using monotonic spline interpolation.
 * 
 * The LUT is packed as a 256x4 texture where each row contains:
 * - Row 0 (y=0.125): Red curve
 * - Row 1 (y=0.375): Green curve
 * - Row 2 (y=0.625): Blue curve
 * - Row 3 (y=0.875): Luma curve
 */
object LUTGenerator {
    
    /**
     * Generate a complete LUT texture data from curve params.
     * Returns an array of floats suitable for uploading to a GL texture.
     * Format: RGBA8 or RGBA16F, size 256x4
     */
    fun generateLUT(
        lumaPoints: List<List<Float>>,
        rPoints: List<List<Float>>,
        gPoints: List<List<Float>>,
        bPoints: List<List<Float>>,
        resolution: Int = 256
    ): FloatArray {
        val lumaCurve = interpolateCurve(lumaPoints, resolution)
        val rCurve = interpolateCurve(rPoints, resolution)
        val gCurve = interpolateCurve(gPoints, resolution)
        val bCurve = interpolateCurve(bPoints, resolution)
        
        // Pack into RGBA format for 4-row texture
        // Each row is 256 pixels, 4 rows = 1024 total pixels = 4096 floats
        val data = FloatArray(resolution * 4 * 4)
        
        for (i in 0 until resolution) {
            // Row 0: R channel
            val row0Base = i * 4
            data[row0Base + 0] = rCurve[i]
            data[row0Base + 1] = 0f
            data[row0Base + 2] = 0f
            data[row0Base + 3] = 1f
            
            // Row 1: G channel
            val row1Base = (resolution + i) * 4
            data[row1Base + 0] = 0f
            data[row1Base + 1] = gCurve[i]
            data[row1Base + 2] = 0f
            data[row1Base + 3] = 1f
            
            // Row 2: B channel
            val row2Base = (resolution * 2 + i) * 4
            data[row2Base + 0] = 0f
            data[row2Base + 1] = 0f
            data[row2Base + 2] = bCurve[i]
            data[row2Base + 3] = 1f
            
            // Row 3: Luma in alpha
            val row3Base = (resolution * 3 + i) * 4
            data[row3Base + 0] = 0f
            data[row3Base + 1] = 0f
            data[row3Base + 2] = 0f
            data[row3Base + 3] = lumaCurve[i]
        }
        
        return data
    }
    
    /**
     * Generate LUT as byte array for RGBA8 texture.
     */
    fun generateLUTBytes(
        lumaPoints: List<List<Float>>,
        rPoints: List<List<Float>>,
        gPoints: List<List<Float>>,
        bPoints: List<List<Float>>,
        resolution: Int = 256
    ): ByteArray {
        val floatData = generateLUT(lumaPoints, rPoints, gPoints, bPoints, resolution)
        return FloatArray(floatData.size) { i ->
            (floatData[i].coerceIn(0f, 1f) * 255f)
        }.map { it.toInt().toByte() }.toByteArray()
    }
    
    /**
     * Interpolate a curve using monotonic cubic spline.
     * Points are expected to be sorted by x value and in format [[x, y], ...]
     */
    private fun interpolateCurve(points: List<List<Float>>, resolution: Int): FloatArray {
        if (points.isEmpty()) {
            return FloatArray(resolution) { it.toFloat() / (resolution - 1) }
        }
        
        if (points.size == 1) {
            return FloatArray(resolution) { points[0][1] }
        }
        
        // Extract x and y values
        val xs = points.map { it[0] }
        val ys = points.map { it[1] }
        
        // Calculate monotonic cubic spline
        val spline = MonotonicCubicSpline(xs.toFloatArray(), ys.toFloatArray())
        
        return FloatArray(resolution) { i ->
            val x = i.toFloat() / (resolution - 1)
            spline.interpolate(x).coerceIn(0f, 1f)
        }
    }
    
    /**
     * Monotonic cubic spline implementation.
     * Ensures the interpolated curve doesn't have overshoots that would create inversions.
     */
    private class MonotonicCubicSpline(
        private val xs: FloatArray,
        private val ys: FloatArray
    ) {
        private val n = xs.size
        private val ds: FloatArray // Slopes at each point
        private val c1s: FloatArray // Coefficients
        private val c2s: FloatArray
        private val c3s: FloatArray
        
        init {
            require(n >= 2) { "Need at least 2 points for spline" }
            
            // Calculate secants
            val secants = FloatArray(n - 1) { i ->
                (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i])
            }
            
            // Initialize slopes
            ds = FloatArray(n)
            ds[0] = secants[0]
            for (i in 1 until n - 1) {
                if (secants[i - 1] * secants[i] <= 0) {
                    ds[i] = 0f
                } else {
                    ds[i] = (secants[i - 1] + secants[i]) / 2f
                }
            }
            ds[n - 1] = secants[n - 2]
            
            // Enforce monotonicity
            for (i in 0 until n - 1) {
                if (abs(secants[i]) < 1e-6f) {
                    ds[i] = 0f
                    ds[i + 1] = 0f
                } else {
                    val alpha = ds[i] / secants[i]
                    val beta = ds[i + 1] / secants[i]
                    val tau = alpha * alpha + beta * beta
                    if (tau > 9f) {
                        val s = 3f / kotlin.math.sqrt(tau)
                        ds[i] = s * alpha * secants[i]
                        ds[i + 1] = s * beta * secants[i]
                    }
                }
            }
            
            // Calculate coefficients
            c1s = ds.copyOf()
            c2s = FloatArray(n - 1)
            c3s = FloatArray(n - 1)
            
            for (i in 0 until n - 1) {
                val h = xs[i + 1] - xs[i]
                val c1 = ds[i]
                val c2 = (3f * secants[i] - 2f * ds[i] - ds[i + 1]) / h
                val c3 = (ds[i] + ds[i + 1] - 2f * secants[i]) / (h * h)
                c2s[i] = c2
                c3s[i] = c3
            }
        }
        
        fun interpolate(x: Float): Float {
            // Handle out of bounds
            if (x <= xs[0]) return ys[0]
            if (x >= xs[n - 1]) return ys[n - 1]
            
            // Binary search for interval
            var low = 0
            var high = n - 1
            while (high - low > 1) {
                val mid = (low + high) / 2
                if (xs[mid] <= x) low = mid else high = mid
            }
            
            val i = low
            val dx = x - xs[i]
            return ys[i] + c1s[i] * dx + c2s[i] * dx * dx + c3s[i] * dx * dx * dx
        }
    }
    
    /**
     * Create identity curve (no change)
     */
    fun identityCurve(): List<List<Float>> {
        return listOf(
            listOf(0f, 0f),
            listOf(0.25f, 0.25f),
            listOf(0.5f, 0.5f),
            listOf(0.75f, 0.75f),
            listOf(1f, 1f)
        )
    }
}
