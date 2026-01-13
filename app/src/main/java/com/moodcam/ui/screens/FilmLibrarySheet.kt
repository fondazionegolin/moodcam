package com.moodcam.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moodcam.R
import com.moodcam.preset.FilmPreset
import com.moodcam.preset.PresetType

/**
 * Bottom sheet showing the film preset library with horizontal scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmLibrarySheet(
    presets: List<FilmPreset>,
    selectedPresetId: String?,
    onPresetSelected: (FilmPreset) -> Unit,
    onPresetEdit: (FilmPreset) -> Unit,
    onPresetDuplicate: (FilmPreset) -> Unit,
    onPresetDelete: (FilmPreset) -> Unit,
    onPresetExport: (FilmPreset) -> Unit,
    onImportPreset: () -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black.copy(alpha = 0.85f),
        scrimColor = Color.Black.copy(alpha = 0.3f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Film Rolls",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                
                Row {
                    // Import button
                    IconButton(onClick = onImportPreset) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import", tint = Color.White)
                    }
                    IconButton(onClick = onCreateNew) {
                        Icon(Icons.Default.Add, contentDescription = "Create New", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val builtInPresets = presets.filter { it.type == PresetType.BUILTIN }
            val customPresets = presets.filter { it.type == PresetType.CUSTOM }
            
            // Built-in presets - horizontal scroll
            if (builtInPresets.isNotEmpty()) {
                Text(
                    text = "Built-in",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(builtInPresets) { preset ->
                        FilmCanisterCard(
                            preset = preset,
                            isSelected = preset.id == selectedPresetId,
                            onClick = { onPresetSelected(preset) },
                            onEdit = { onPresetEdit(preset) },
                            onDuplicate = { onPresetDuplicate(preset) },
                            onDelete = null,
                            onExport = { onPresetExport(preset) }
                        )
                    }
                }
            }
            
            // Custom presets - horizontal scroll
            if (customPresets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(customPresets) { preset ->
                        FilmCanisterCard(
                            preset = preset,
                            isSelected = preset.id == selectedPresetId,
                            onClick = { onPresetSelected(preset) },
                            onEdit = { onPresetEdit(preset) },
                            onDuplicate = { onPresetDuplicate(preset) },
                            onDelete = { onPresetDelete(preset) },
                            onExport = { onPresetExport(preset) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Film canister card with image and name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilmCanisterCard(
    preset: FilmPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?,
    onExport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // Get drawable resource for this preset
    val imageRes = getFilmImageResource(preset.id)
    
    Column(
        modifier = Modifier
            .width(100.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Film canister image
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray.copy(alpha = 0.3f))
                .then(
                    if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageRes != null) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = preset.name,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Fallback: colored circle based on preset
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(getPresetColor(preset))
                )
            }
            
            // Edit icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .combinedClickable(
                        onClick = onEdit,
                        onLongClick = { showMenu = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Preset name
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = {
                    showMenu = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = {
                    showMenu = false
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                text = { Text("Export") },
                leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                onClick = {
                    showMenu = false
                    onExport()
                }
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Map preset ID to drawable resource.
 */
private fun getFilmImageResource(presetId: String): Int? {
    return when {
        presetId.contains("acros", ignoreCase = true) -> R.drawable.film_acros
        presetId.contains("astia", ignoreCase = true) -> R.drawable.film_astia
        presetId.contains("classic-neg", ignoreCase = true) -> R.drawable.film_classic_neg
        presetId.contains("classic-chrome", ignoreCase = true) -> R.drawable.film_classic_neg // reuse
        presetId.contains("mono", ignoreCase = true) -> R.drawable.film_mono
        presetId.contains("nostalgic", ignoreCase = true) -> R.drawable.film_nostalgic_neg
        presetId.contains("pro-neg-hi", ignoreCase = true) -> R.drawable.film_pro_neg_hi
        presetId.contains("pro-neg-std", ignoreCase = true) -> R.drawable.film_pro_neg_std
        presetId.contains("provia", ignoreCase = true) -> R.drawable.film_provia
        presetId.contains("velvia", ignoreCase = true) -> R.drawable.film_provia // reuse similar
        presetId.contains("eterna", ignoreCase = true) -> R.drawable.film_mono // reuse for cinema
        presetId.contains("reala", ignoreCase = true) -> R.drawable.film_provia // reuse
        presetId.contains("sepia", ignoreCase = true) -> R.drawable.film_nostalgic_neg // reuse
        else -> null
    }
}

/**
 * Generate a color based on preset parameters.
 */
@Composable
private fun getPresetColor(preset: FilmPreset): Color {
    val params = preset.params
    
    return when {
        params.saturation < 0.3f -> Color.Gray // B&W
        params.temperatureK < 5500 -> Color(0xFFE8B067) // Warm
        params.temperatureK > 7000 -> Color(0xFF6BA3D6) // Cool
        params.saturation > 1.2f -> Color(0xFFE85D5D) // Vibrant
        else -> Color(0xFF7CB68A) // Neutral green (Fuji green)
    }
}
