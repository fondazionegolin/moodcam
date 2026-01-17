package com.moodcam.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moodcam.MoodCamApplication
import com.moodcam.export.PhotoExporter
import com.moodcam.preset.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context

/**
 * ViewModel managing camera state and preset selection.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    
    private val presetRepository: PresetRepository by lazy {
        val db = (application as MoodCamApplication).database
        PresetRepository(application, db.presetDao())
    }
    
    private val photoExporter: PhotoExporter by lazy {
        PhotoExporter(application)
    }
    
    // UI State
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    // Gallery photos
    private val _galleryPhotos = MutableStateFlow<List<Uri>>(emptyList())
    val galleryPhotos: StateFlow<List<Uri>> = _galleryPhotos.asStateFlow()
    
    // All presets (built-in + custom)
    val allPresets: StateFlow<List<FilmPreset>> = presetRepository.getAllPresetsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    // Currently selected preset
    private val _selectedPreset = MutableStateFlow<FilmPreset?>(null)
    val selectedPreset: StateFlow<FilmPreset?> = _selectedPreset.asStateFlow()
    
    // Active params (can be modified in editor without saving)
    private val _activeParams = MutableStateFlow(PresetParams())
    val activeParams: StateFlow<PresetParams> = _activeParams.asStateFlow()
    
    // Editing state
    private val _editingPreset = MutableStateFlow<FilmPreset?>(null)
    val editingPreset: StateFlow<FilmPreset?> = _editingPreset.asStateFlow()
    
    init {
        // Load last used preset on init (or default if first launch)
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("moodcam_prefs", Context.MODE_PRIVATE)
            val lastPresetId = prefs.getString("last_preset_id", null)
            
            val preset = if (lastPresetId != null) {
                // Try to find the last used preset
                allPresets.first { it.isNotEmpty() }.find { it.id == lastPresetId }
                    ?: presetRepository.getDefaultPreset()
            } else {
                presetRepository.getDefaultPreset()
            }
            selectPreset(preset)
        }
    }
    
    /**
     * Select a preset and apply its params.
     */
    fun selectPreset(preset: FilmPreset) {
        _selectedPreset.value = preset
        _activeParams.value = preset.params
        _uiState.update { it.copy(showLibrary = false) }
        
        // Save last used preset ID
        viewModelScope.launch {
            getApplication<Application>().getSharedPreferences("moodcam_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_preset_id", preset.id)
                .apply()
        }
    }
    
    /**
     * Update active params (real-time preview changes).
     */
    fun updateActiveParams(params: PresetParams) {
        _activeParams.value = params
    }
    
    /**
     * Start editing a preset.
     */
    fun startEditing(preset: FilmPreset) {
        _editingPreset.value = preset
        _activeParams.value = preset.params
        _uiState.update { it.copy(showEditor = true, showLibrary = false) }
    }
    
    /**
     * Save changes to the current preset.
     */
    fun saveCurrentPreset() {
        viewModelScope.launch {
            val editing = _editingPreset.value ?: return@launch
            val params = _activeParams.value
            
            if (editing.type == PresetType.BUILTIN) {
                // Create a copy for built-in presets
                val newPreset = presetRepository.duplicatePreset(
                    editing.copy(params = params),
                    "${editing.name} (Copy)"
                )
                selectPreset(newPreset)
            } else {
                // Update custom preset
                val updated = editing.copy(params = params)
                presetRepository.updatePreset(updated)
                selectPreset(updated)
            }
            
            _editingPreset.value = null
            _uiState.update { it.copy(showEditor = false, message = "Preset saved") }
        }
    }
    
    /**
     * Save as a new preset with given name.
     */
    fun saveAsNewPreset(name: String) {
        viewModelScope.launch {
            val editing = _editingPreset.value ?: _selectedPreset.value ?: return@launch
            val params = _activeParams.value
            
            val newPreset = presetRepository.duplicatePreset(
                editing.copy(params = params),
                name
            )
            selectPreset(newPreset)
            
            _editingPreset.value = null
            _uiState.update { it.copy(showEditor = false, message = "Preset created") }
        }
    }
    
    /**
     * Reset active params to the selected preset's original params.
     */
    fun resetActiveParams() {
        _selectedPreset.value?.let {
            _activeParams.value = it.params
        }
    }
    
    /**
     * Cancel editing.
     */
    fun cancelEditing() {
        _selectedPreset.value?.let {
            _activeParams.value = it.params
        }
        _editingPreset.value = null
        _uiState.update { it.copy(showEditor = false) }
    }
    
    /**
     * Duplicate a preset.
     */
    fun duplicatePreset(preset: FilmPreset) {
        viewModelScope.launch {
            presetRepository.duplicatePreset(preset, "${preset.name} (Copy)")
            _uiState.update { it.copy(message = "Preset duplicated") }
        }
    }
    
    /**
     * Delete a custom preset.
     */
    fun deletePreset(preset: FilmPreset) {
        if (preset.type == PresetType.BUILTIN) return
        
        viewModelScope.launch {
            presetRepository.deletePreset(preset)
            
            // If deleted the selected preset, select default
            if (_selectedPreset.value?.id == preset.id) {
                selectPreset(presetRepository.getDefaultPreset())
            }
            
            _uiState.update { it.copy(message = "Preset deleted") }
        }
    }
    
    /**
     * Create a new preset from scratch with default parameters.
     */
    fun createNewPreset() {
        val newPreset = FilmPreset(
            name = "New Preset",
            type = PresetType.CUSTOM,
            params = PresetParams() // Default parameters
        )
        _editingPreset.value = newPreset
        _activeParams.value = newPreset.params
        _uiState.update { it.copy(showEditor = true, showLibrary = false) }
    }
    
    /**
     * Toggle library visibility.
     */
    fun toggleLibrary() {
        _uiState.update { it.copy(showLibrary = !it.showLibrary) }
    }
    
    /**
     * Show library.
     */
    fun showLibrary() {
        _uiState.update { it.copy(showLibrary = true) }
    }
    
    /**
     * Hide library.
     */
    fun hideLibrary() {
        _uiState.update { it.copy(showLibrary = false) }
    }
    
    /**
     * Capture and save a photo with effects applied.
     * @param bitmap GPU-rendered bitmap from GLRenderer
     * @param frameWidth Frame width (-1 to +1, negative = black, positive = white)
     */
    fun capturePhoto(bitmap: Bitmap, frameWidth: Float = 0f, rotation: Int = 0) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true) }
            
            try {
                // Apply frame/border if needed
                val finalBitmap = if (kotlin.math.abs(frameWidth) > 0.01f) {
                    addFrame(bitmap, frameWidth)
                } else {
                    bitmap
                }
                
                val presetName = _selectedPreset.value?.name ?: "Unknown"
                
                val uri = photoExporter.exportPhoto(finalBitmap, presetName, rotation = rotation)
                
                if (uri != null) {
                    _uiState.update { it.copy(
                        isCapturing = false,
                        message = "Photo saved",
                        lastPhotoUri = uri
                    )}
                } else {
                    _uiState.update { it.copy(
                        isCapturing = false,
                        message = "Failed to save photo"
                    )}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isCapturing = false,
                    message = "Error: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * Add a frame/border to the bitmap by expanding the canvas.
     * Photo stays unchanged, output is larger with frame around it.
     */
    private fun addFrame(source: Bitmap, frameWidth: Float): Bitmap {
        val frameSize = (kotlin.math.abs(frameWidth) * source.width * 0.1f).toInt()
        if (frameSize <= 0) return source
        
        // Expand canvas to fit frame
        val newWidth = source.width + frameSize * 2
        val newHeight = source.height + frameSize * 2
        
        val frameColor = if (frameWidth >= 0) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        
        val result = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        
        // Fill with frame color
        canvas.drawColor(frameColor)
        
        // Draw original bitmap centered (not scaled)
        canvas.drawBitmap(source, frameSize.toFloat(), frameSize.toFloat(), null)
        
        return result
    }
    
    /**
     * Set preview exposure adjustment.
     */
    fun setPreviewExposure(exposure: Float) {
        _uiState.update { it.copy(previewExposure = exposure) }
    }
    
    /**
     * Set frame width (positive = white, negative = black).
     */
    fun setFrameWidth(width: Float) {
        _uiState.update { it.copy(frameWidth = width) }
    }
    
    /**
     * Clear message.
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
    
    /**
     * Export preset to JSON file in Downloads folder.
     */
    fun exportPreset(preset: FilmPreset) {
        viewModelScope.launch {
            try {
                val gson = com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                
                val json = gson.toJson(preset)
                
                // Save to Downloads folder
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val fileName = "${preset.name.replace(" ", "_")}_preset.json"
                val file = java.io.File(downloadsDir, fileName)
                file.writeText(json)
                
                _uiState.update { it.copy(message = "Exported: $fileName") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Export failed: ${e.message}") }
            }
        }
    }
    
    /**
     * Import preset from JSON file. 
     * Note: This triggers a file picker intent in the UI layer.
     */
    fun importPreset() {
        // The actual file picking is handled in the UI layer
        // This sets a flag to show import is requested
        _uiState.update { it.copy(showImportPicker = true) }
    }
    
    /**
     * Process imported preset JSON content.
     */
    fun handleImportedPreset(jsonContent: String) {
        viewModelScope.launch {
            try {
                val gson = com.google.gson.Gson()
                val imported = gson.fromJson(jsonContent, FilmPreset::class.java)
                
                // Create as custom preset with new ID
                val newPreset = imported.copy(
                    id = "custom_imported_${System.currentTimeMillis()}",
                    type = PresetType.CUSTOM
                )
                
                presetRepository.savePreset(newPreset)
                _uiState.update { 
                    it.copy(
                        message = "Imported: ${newPreset.name}",
                        showImportPicker = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        message = "Import failed: ${e.message}",
                        showImportPicker = false
                    ) 
                }
            }
        }
    }
    
    /**
     * Cancel import picker.
     */
    fun cancelImport() {
        _uiState.update { it.copy(showImportPicker = false) }
    }
    
    /**
     * Show/Hide In-App Gallery
     */
    fun showGallery() {
        _uiState.update { it.copy(showGallery = true) }
    }
    
    fun hideGallery() {
        _uiState.update { it.copy(showGallery = false) }
    }
    
    /**
     * Load recent photos from MoodCam folder or MediaStore
     */
    fun loadGalleryPhotos(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val photos = mutableListOf<Uri>()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Query MediaStore for images with description "Preset: ..." or path containing MoodCam
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN
                )
                
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("MOODCAM_%")
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                
                try {
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val contentUri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            photos.add(contentUri)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // Legacy storage scan
                try {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val moodcamDir = File(picturesDir, "MoodCam")
                    
                    if (moodcamDir.exists() && moodcamDir.isDirectory) {
                        val files = moodcamDir.listFiles { file -> 
                            file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg"))
                        }
                        
                        files?.sortedByDescending { it.lastModified() }?.forEach { file ->
                            photos.add(Uri.fromFile(file))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            _galleryPhotos.value = photos
        }
    }
}

/**
 * UI state for camera screen.
 */
data class CameraUiState(
    val showLibrary: Boolean = false,
    val showEditor: Boolean = false,
    val isCapturing: Boolean = false,
    val message: String? = null,
    // Preview exposure adjustment (on top of preset)
    val previewExposure: Float = 0f,
    // Frame/border width (0 = none, positive = white, negative = black)
    val frameWidth: Float = 0f,
    // Last captured photo URI for thumbnail preview
    val lastPhotoUri: android.net.Uri? = null,
    // Import file picker visibility
    val showImportPicker: Boolean = false,
    // In-app gallery visibility
    val showGallery: Boolean = false
)

