package com.moodcam.preset

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for custom film presets.
 */
@Dao
interface PresetDao {
    
    @Query("SELECT * FROM presets ORDER BY updatedAt DESC")
    fun getAllPresetsFlow(): Flow<List<FilmPreset>>
    
    @Query("SELECT * FROM presets ORDER BY updatedAt DESC")
    suspend fun getAllPresets(): List<FilmPreset>
    
    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPresetById(id: String): FilmPreset?
    
    @Query("SELECT * FROM presets WHERE id = :id")
    fun getPresetByIdFlow(id: String): Flow<FilmPreset?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: FilmPreset)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<FilmPreset>)
    
    @Update
    suspend fun updatePreset(preset: FilmPreset)
    
    @Delete
    suspend fun deletePreset(preset: FilmPreset)
    
    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePresetById(id: String)
    
    @Query("SELECT COUNT(*) FROM presets")
    suspend fun getPresetCount(): Int
}
