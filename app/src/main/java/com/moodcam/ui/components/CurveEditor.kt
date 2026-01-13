package com.moodcam.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.moodcam.ui.theme.CurveColors
import kotlin.math.sqrt

/**
 * Interactive curve editor with smooth dragging.
 * Uses raw pointer events instead of detectDragGestures to avoid accumulation issues.
 */
@Composable
fun CurveEditor(
    points: List<List<Float>>,
    channelIndex: Int,
    modifier: Modifier = Modifier,
    histogram: IntArray? = null,
    onPointsChanged: (List<List<Float>>) -> Unit
) {
    val curveColor = when (channelIndex) {
        0 -> CurveColors.luma
        1 -> CurveColors.red
        2 -> CurveColors.green
        else -> CurveColors.blue
    }
    
    val histogramColor = when (channelIndex) {
        0 -> Color.White.copy(alpha = 0.12f)
        1 -> CurveColors.red.copy(alpha = 0.15f)
        2 -> CurveColors.green.copy(alpha = 0.15f)
        else -> CurveColors.blue.copy(alpha = 0.15f)
    }
    
    // Stable state references
    val pointsState = remember { mutableStateOf(points) }
    val draggingIndex = remember { mutableIntStateOf(-1) }
    
    // Update when external points change and not dragging
    LaunchedEffect(points) {
        if (draggingIndex.intValue < 0) {
            pointsState.value = points
        }
    }
    
    val touchRadius = 80f
    val handleSize = 11.dp
    val handleSizeActive = 15.dp
    
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .pointerInput(channelIndex) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val pts = pointsState.value
                            
                            // Find if tapping existing point
                            val idx = pts.indexOfFirst { p ->
                                if (p[0] == 0f || p[0] == 1f) false
                                else {
                                    val px = p[0] * w
                                    val py = (1f - p[1]) * h
                                    sqrt((px - offset.x) * (px - offset.x) + (py - offset.y) * (py - offset.y)) < touchRadius
                                }
                            }
                            
                            val newPts = if (idx > 0 && idx < pts.size - 1) {
                                pts.toMutableList().apply { removeAt(idx) }
                            } else {
                                val x = (offset.x / w).coerceIn(0.05f, 0.95f)
                                val y = (1f - offset.y / h).coerceIn(0f, 1f)
                                (pts + listOf(listOf(x, y))).sortedBy { it[0] }
                            }
                            pointsState.value = newPts
                            onPointsChanged(newPts)
                        }
                    )
                }
                .pointerInput(channelIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        
                        // Find touched point
                        val pts = pointsState.value
                        val idx = pts.indexOfFirst { p ->
                            val px = p[0] * w
                            val py = (1f - p[1]) * h
                            sqrt((px - down.position.x) * (px - down.position.x) + 
                                 (py - down.position.y) * (py - down.position.y)) < touchRadius
                        }
                        
                        if (idx >= 0) {
                            draggingIndex.intValue = idx
                            
                            // Consume all drag events
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                
                                if (change.pressed) {
                                    change.consume()
                                    
                                    val currentPts = pointsState.value.toMutableList()
                                    val newY = (1f - change.position.y / h).coerceIn(0f, 1f)
                                    
                                    when (idx) {
                                        0 -> currentPts[0] = listOf(0f, newY)
                                        currentPts.size - 1 -> currentPts[idx] = listOf(1f, newY)
                                        else -> {
                                            val newX = (change.position.x / w).coerceIn(0f, 1f)
                                            val prevX = currentPts.getOrNull(idx - 1)?.get(0) ?: 0f
                                            val nextX = currentPts.getOrNull(idx + 1)?.get(0) ?: 1f
                                            val clampedX = newX.coerceIn(prevX + 0.02f, nextX - 0.02f)
                                            currentPts[idx] = listOf(clampedX, newY)
                                        }
                                    }
                                    pointsState.value = currentPts
                                } else {
                                    break
                                }
                            } while (true)
                            
                            // Drag ended
                            draggingIndex.intValue = -1
                            onPointsChanged(pointsState.value)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val pts = pointsState.value
            
            // Histogram
            histogram?.let { hist ->
                if (hist.isNotEmpty()) drawHistogram(hist, histogramColor, w, h)
            }
            
            // Grid
            val gridColor = Color.White.copy(alpha = 0.06f)
            for (i in 1..3) {
                drawLine(gridColor, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h))
                drawLine(gridColor, Offset(0f, h * i / 4f), Offset(w, h * i / 4f))
            }
            
            // Diagonal
            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, h), Offset(w, 0f), 1.dp.toPx())
            
            // Curve
            if (pts.size >= 2) {
                val path = Path()
                val sorted = pts.sortedBy { it[0] }
                val screenPts = sorted.map { Offset(it[0] * w, (1f - it[1]) * h) }
                drawCatmullRom(path, screenPts)
                drawPath(path, curveColor, style = Stroke(2.5.dp.toPx()))
            }
            
            // Handles
            pts.forEachIndexed { i, p ->
                val x = p[0] * w
                val y = (1f - p[1]) * h
                val active = i == draggingIndex.intValue
                val r = if (active) handleSizeActive.toPx() else handleSize.toPx()
                
                if (active) {
                    drawCircle(curveColor.copy(alpha = 0.35f), r + 12.dp.toPx(), Offset(x, y))
                }
                drawCircle(curveColor, r, Offset(x, y))
                drawCircle(Color(0xFF1A1A1A), r - 3.dp.toPx(), Offset(x, y))
                drawCircle(curveColor, 2.dp.toPx(), Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawHistogram(hist: IntArray, color: Color, w: Float, h: Float) {
    val max = hist.maxOrNull()?.toFloat() ?: return
    if (max <= 0) return
    val path = Path()
    path.moveTo(0f, h)
    val step = w / hist.size
    hist.forEachIndexed { i, v ->
        path.lineTo(i * step, h - (v / max) * h * 0.6f)
    }
    path.lineTo(w, h)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawCatmullRom(path: Path, pts: List<Offset>) {
    if (pts.size < 2) return
    
    val ext = mutableListOf(
        Offset(pts[0].x * 2 - pts.getOrElse(1) { pts[0] }.x, 
               pts[0].y * 2 - pts.getOrElse(1) { pts[0] }.y)
    )
    ext.addAll(pts)
    val last = pts.last()
    val prev = pts.getOrElse(pts.size - 2) { last }
    ext.add(Offset(last.x * 2 - prev.x, last.y * 2 - prev.y))
    
    path.moveTo(pts[0].x, pts[0].y)
    
    for (i in 1 until ext.size - 2) {
        val p0 = ext[i - 1]; val p1 = ext[i]; val p2 = ext[i + 1]; val p3 = ext[i + 2]
        for (t in 1..20) {
            val s = t / 20f
            val s2 = s * s; val s3 = s2 * s
            path.lineTo(
                0.5f * (2*p1.x + (-p0.x+p2.x)*s + (2*p0.x-5*p1.x+4*p2.x-p3.x)*s2 + (-p0.x+3*p1.x-3*p2.x+p3.x)*s3),
                0.5f * (2*p1.y + (-p0.y+p2.y)*s + (2*p0.y-5*p1.y+4*p2.y-p3.y)*s2 + (-p0.y+3*p1.y-3*p2.y+p3.y)*s3)
            )
        }
    }
}
