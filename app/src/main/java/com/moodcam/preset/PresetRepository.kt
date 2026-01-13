package com.moodcam.preset

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Repository that combines built-in presets (from assets) with custom presets (from Room).
 */
class PresetRepository(
    private val context: Context,
    private val presetDao: PresetDao
) {
    private val gson = Gson()
    
    // Cache for built-in presets
    private var builtInPresets: List<FilmPreset>? = null
    
    /**
     * Get all presets (built-in + custom) as a Flow.
     */
    fun getAllPresetsFlow(): Flow<List<FilmPreset>> {
        val builtInFlow = flowOf(getBuiltInPresets())
        val customFlow = presetDao.getAllPresetsFlow()
        
        return combine(builtInFlow, customFlow) { builtIn, custom ->
            builtIn + custom
        }
    }
    
    /**
     * Get all presets (built-in + custom).
     */
    suspend fun getAllPresets(): List<FilmPreset> = withContext(Dispatchers.IO) {
        getBuiltInPresets() + presetDao.getAllPresets()
    }
    
    /**
     * Get built-in presets from assets.
     */
    fun getBuiltInPresets(): List<FilmPreset> {
        builtInPresets?.let { return it }
        
        return try {
            val json = context.assets.open("presets/builtin_presets.json")
                .bufferedReader()
                .use { it.readText() }
            
            val type = object : TypeToken<List<FilmPreset>>() {}.type
            val presets: List<FilmPreset> = gson.fromJson(json, type)
            builtInPresets = presets
            presets
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Get a preset by ID (checks both built-in and custom).
     */
    suspend fun getPresetById(id: String): FilmPreset? = withContext(Dispatchers.IO) {
        // Check built-in first
        getBuiltInPresets().find { it.id == id }?.let { return@withContext it }
        // Then check custom
        presetDao.getPresetById(id)
    }
    
    /**
     * Save a custom preset.
     */
    suspend fun savePreset(preset: FilmPreset) = withContext(Dispatchers.IO) {
        val updatedPreset = preset.copy(
            type = PresetType.CUSTOM,
            updatedAt = System.currentTimeMillis()
        )
        presetDao.insertPreset(updatedPreset)
    }
    
    /**
     * Update an existing custom preset.
     */
    suspend fun updatePreset(preset: FilmPreset) = withContext(Dispatchers.IO) {
        if (preset.type == PresetType.BUILTIN) {
            throw IllegalArgumentException("Cannot modify built-in presets")
        }
        val updatedPreset = preset.copy(updatedAt = System.currentTimeMillis())
        presetDao.updatePreset(updatedPreset)
    }
    
    /**
     * Duplicate a preset (creates a custom copy).
     */
    suspend fun duplicatePreset(preset: FilmPreset, newName: String): FilmPreset = withContext(Dispatchers.IO) {
        val duplicate = preset.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName,
            type = PresetType.CUSTOM,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        presetDao.insertPreset(duplicate)
        duplicate
    }
    
    /**
     * Delete a custom preset.
     */
    suspend fun deletePreset(preset: FilmPreset) = withContext(Dispatchers.IO) {
        if (preset.type == PresetType.BUILTIN) {
            throw IllegalArgumentException("Cannot delete built-in presets")
        }
        presetDao.deletePreset(preset)
    }
    
    /**
     * Get the default preset (first built-in).
     */
    fun getDefaultPreset(): FilmPreset {
        return getBuiltInPresets().firstOrNull() ?: createDefaultPreset()
    }
    
    private fun createDefaultPreset(): FilmPreset {
        return FilmPreset(
            id = "default",
            name = "Natural",
            type = PresetType.BUILTIN,
            params = PresetParams()
        )
    }
}
