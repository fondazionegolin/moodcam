package com.moodcam.ui.screens

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.moodcam.preset.GrainToneMode
import com.moodcam.preset.PresetParams
import com.moodcam.ui.components.CurveEditor

/**
 * Bottom sheet for editing preset parameters.
 * Tabs: Base, Tones, WB, Grain
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmEditorSheet(
    params: PresetParams,
    presetName: String,
    isBuiltIn: Boolean,
    onParamsChanged: (PresetParams) -> Unit,
    onSave: () -> Unit,
    onSaveAsNew: (String) -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("$presetName (Copy)") }
    
    val tabs = listOf("Base", "Tone", "WB", "Grain", "FX")
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,  // Fully opaque
        scrimColor = Color.Black.copy(alpha = 0.3f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.60f)  // Good for curve editor
        ) {
            // Header - very compact
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Preset",
                        style = MaterialTheme.typography.labelLarge  // Even smaller
                    )
                    Text(
                        text = presetName,
                        style = MaterialTheme.typography.labelSmall,  // Even smaller
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    // Export button
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Export")
                    }
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Tab content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> BaseTab(params, onParamsChanged)
                    1 -> TonesTab(params, onParamsChanged)
                    2 -> WhiteBalanceTab(params, onParamsChanged)
                    3 -> GrainTab(params, onParamsChanged)
                    4 -> EffectsTab(params, onParamsChanged)
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showSaveAsDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save as New")
                }
                
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isBuiltIn) "Save Copy" else "Save")
                }
            }
            
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
    
    // Save as new dialog
    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Save as New Preset") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveAsDialog = false
                        onSaveAsNew(newPresetName)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BaseTab(
    params: PresetParams,
    onParamsChanged: (PresetParams) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),  // Reduced vertical padding
        verticalArrangement = Arrangement.spacedBy(8.dp)  // Reduced spacing
    ) {
        ParamSlider(
            label = "Exposure",
            value = params.exposureEV,
            valueRange = -2f..2f,
            valueDisplay = { String.format("%+.1f EV", it) }
        ) {
            onParamsChanged(params.copy(exposureEV = it))
        }
        
        ParamSlider(
            label = "Contrast",
            value = params.contrast,
            valueRange = 0.5f..2f,
            valueDisplay = { String.format("%.0f%%", it * 100) }
        ) {
            onParamsChanged(params.copy(contrast = it))
        }
        
        ParamSlider(
            label = "Fade",
            value = params.fade,
            valueRange = 0f..0.5f,
            valueDisplay = { String.format("%.0f%%", it * 200) }
        ) {
            onParamsChanged(params.copy(fade = it))
        }
        
        ParamSlider(
            label = "Saturation",
            value = params.saturation,
            valueRange = 0f..2f,
            valueDisplay = { String.format("%.0f%%", it * 100) }
        ) {
            onParamsChanged(params.copy(saturation = it))
        }
        
        ParamSlider(
            label = "Vibrance",
            value = params.vibrance,
            valueRange = -0.5f..0.5f,
            valueDisplay = { String.format("%+.0f%%", it * 100) }
        ) {
            onParamsChanged(params.copy(vibrance = it))
        }
    }
}

/**
 * Tones tab: Per-channel Highlights/Midtones/Shadows sliders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TonesTab(
    params: PresetParams,
    onParamsChanged: (PresetParams) -> Unit
) {
    var selectedChannel by remember { mutableIntStateOf(0) }
    val channels = listOf("White", "Red", "Green", "Blue")
    val channelColors = listOf(
        Color.White,
        Color(0xFFFF5252),
        Color(0xFF69F0AE),
        Color(0xFF448AFF)
    )
    
    // Get current channel values
    val currentTones = when(selectedChannel) {
        0 -> params.channelTones.white
        1 -> params.channelTones.red
        2 -> params.channelTones.green
        else -> params.channelTones.blue
    }
    
    // Helper to update channel values
    fun updateTones(newTones: com.moodcam.preset.ToneValues) {
        val newChannelTones = when(selectedChannel) {
            0 -> params.channelTones.copy(white = newTones)
            1 -> params.channelTones.copy(red = newTones)
            2 -> params.channelTones.copy(green = newTones)
            else -> params.channelTones.copy(blue = newTones)
        }
        onParamsChanged(params.copy(channelTones = newChannelTones))
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Channel selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            channels.forEachIndexed { index, name ->
                FilterChip(
                    selected = selectedChannel == index,
                    onClick = { selectedChannel = index },
                    label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = channelColors[index].copy(alpha = 0.25f),
                        selectedLabelColor = channelColors[index]
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }
        }
        
        Divider()
        
        // Current channel indicator
        Text(
            text = "Regolazione ${channels[selectedChannel]}",
            style = MaterialTheme.typography.titleSmall,
            color = channelColors[selectedChannel]
        )
        
        // Alte (Highlights)
        ParamSlider(
            label = "Alte (Highlights)",
            value = currentTones.highlights,
            valueRange = -1f..1f,
            valueDisplay = { String.format("%+.0f%%", it * 100) }
        ) {
            updateTones(currentTones.copy(highlights = it))
        }
        
        // Medie (Midtones)
        ParamSlider(
            label = "Medie (Midtones)",
            value = currentTones.midtones,
            valueRange = -1f..1f,
            valueDisplay = { String.format("%+.0f%%", it * 100) }
        ) {
            updateTones(currentTones.copy(midtones = it))
        }
        
        // Basse (Shadows)
        ParamSlider(
            label = "Basse (Shadows)",
            value = currentTones.shadows,
            valueRange = -1f..1f,
            valueDisplay = { String.format("%+.0f%%", it * 100) }
        ) {
            updateTones(currentTones.copy(shadows = it))
        }
    }
}

@Composable
private fun WhiteBalanceTab(
    params: PresetParams,
    onParamsChanged: (PresetParams) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)  // Compact spacing
    ) {
        ParamSlider(
            label = "Temperature",
            value = params.temperatureK.toFloat(),
            valueRange = 2000f..12000f,
            valueDisplay = { String.format("%.0fK", it) }
        ) {
            onParamsChanged(params.copy(temperatureK = it.toInt()))
        }
        
        ParamSlider(
            label = "Tint",
            value = params.tint,
            valueRange = -0.5f..0.5f,
            valueDisplay = {
                when {
                    it < -0.01f -> "Green ${String.format("%.0f%%", -it * 100)}"
                    it > 0.01f -> "Magenta ${String.format("%.0f%%", it * 100)}"
                    else -> "Neutral"
                }
            }
        ) {
            onParamsChanged(params.copy(tint = it))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrainTab(
    params: PresetParams,
    onParamsChanged: (PresetParams) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)  // Compact spacing
    ) {
        ParamSlider(
            label = "Strength",
            value = params.grain.strength,
            valueRange = 0f..1f,
            valueDisplay = { String.format("%.0f%%", it * 100) }
        ) {
            onParamsChanged(params.copy(grain = params.grain.copy(strength = it)))
        }
        
        ParamSlider(
            label = "Size",
            value = params.grain.size,
            valueRange = 0.5f..2f,
            valueDisplay = { String.format("%.1fx", it) }
        ) {
            onParamsChanged(params.copy(grain = params.grain.copy(size = it)))
        }
        
        ParamSlider(
            label = "Clumping",
            value = params.grain.clumping,
            valueRange = 0f..1f,
            valueDisplay = { String.format("%.0f%%", it * 100) }
        ) {
            onParamsChanged(params.copy(grain = params.grain.copy(clumping = it)))
        }
        
        // Tone mode selector
        Text(
            text = "Tone Response",
            style = MaterialTheme.typography.labelLarge
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GrainToneMode.entries.forEach { mode ->
                FilterChip(
                    selected = params.grain.toneMode == mode,
                    onClick = {
                        onParamsChanged(params.copy(grain = params.grain.copy(toneMode = mode)))
                    },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}

/**
 * Effects tab: Vignette, Bloom, Halation, Bokeh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsTab(
    params: PresetParams,
    onParamsChanged: (PresetParams) -> Unit
) {
    val bokehOptions = listOf(1.2f, 1.4f, 1.8f, 2.8f)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)  // Compact spacing
    ) {
        ParamSlider(
            label = "Vignette",
            value = params.effects.vignette,
            valueRange = 0f..2f,
            valueDisplay = { String.format("%.0f%%", it * 50) }
        ) {
            onParamsChanged(params.copy(effects = params.effects.copy(vignette = it)))
        }
        
        ParamSlider(
            label = "Bloom",
            value = params.effects.bloom,
            valueRange = 0f..1f,
            valueDisplay = { String.format("%.0f%%", it * 100) }
        ) {
            onParamsChanged(params.copy(effects = params.effects.copy(bloom = it)))
        }
        
        ParamSlider(
            label = "Halation",
            value = params.effects.halation,
            valueRange = 0f..1f,
            valueDisplay = { String.format("%.0f%%", it * 100) }
        ) {
            onParamsChanged(params.copy(effects = params.effects.copy(halation = it)))
        }
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        
        // Bokeh aperture selector
        Text(
            text = "Bokeh Aperture (Portrait Mode)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bokehOptions.forEach { aperture ->
                val isSelected = params.effects.bokehAperture == aperture
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onParamsChanged(params.copy(
                            effects = params.effects.copy(bokehAperture = aperture)
                        ))
                    },
                    label = { Text("f/$aperture") }
                )
            }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }
    
    // Track for quantized haptic feedback (vibrate every ~5% of range)
    val rangeSize = valueRange.endInclusive - valueRange.start
    val tickInterval = rangeSize / 20f  // 20 ticks across full range
    var lastHapticValue by remember { mutableFloatStateOf(value) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium  // Smaller font
            )
            Text(
                text = valueDisplay(value),
                style = MaterialTheme.typography.bodySmall,  // Smaller font
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Slider(
            value = value,
            onValueChange = { newValue ->
                // Quantized haptic feedback every tick interval
                if (kotlin.math.abs(newValue - lastHapticValue) >= tickInterval) {
                    lastHapticValue = newValue
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(10)
                    }
                }
                onValueChange(newValue)
            },
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)  // Compact slider height
        )
    }
}
