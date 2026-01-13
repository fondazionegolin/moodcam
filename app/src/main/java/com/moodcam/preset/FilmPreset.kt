package com.moodcam.preset

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Represents a film preset with all parameters for the vintage effect.
 */
@Entity(tableName = "presets")
@TypeConverters(PresetConverters::class)
data class FilmPreset(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: PresetType = PresetType.CUSTOM,
    val version: Int = 1,
    val params: PresetParams,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class PresetType {
    @SerializedName("builtin") BUILTIN,
    @SerializedName("custom") CUSTOM
}

/**
 * Complete set of parameters for film effect rendering.
 */
data class PresetParams(
    // Base adjustments
    @SerializedName("exposureEV")
    val exposureEV: Float = 0f,
    
    @SerializedName("contrast")
    val contrast: Float = 1f,
    
    @SerializedName("fade")
    val fade: Float = 0f,
    
    @SerializedName("saturation")
    val saturation: Float = 1f,
    
    @SerializedName("vibrance")
    val vibrance: Float = 0f,
    
    // Tone adjustments (highlights, midtones, shadows)
    @SerializedName("highlights")
    val highlights: Float = 0f,  // -1.0 to +1.0
    
    @SerializedName("midtones")
    val midtones: Float = 0f,    // -1.0 to +1.0
    
    @SerializedName("shadows")
    val shadows: Float = 0f,     // -1.0 to +1.0
    
    // White balance
    @SerializedName("temperatureK")
    val temperatureK: Int = 6500,
    
    @SerializedName("tint")
    val tint: Float = 0f,
    
    // Clarity (local contrast)
    @SerializedName("clarity")
    val clarity: Float = 0f,
    
    // Curves
    @SerializedName("curves")
    val curves: CurveParams = CurveParams(),
    
    // Grain
    @SerializedName("grain")
    val grain: GrainParams = GrainParams(),
    
    // Effects
    @SerializedName("effects")
    val effects: EffectsParams = EffectsParams()
)

/**
 * Curve parameters with control points for each channel.
 */
data class CurveParams(
    @SerializedName("lutResolution")
    val lutResolution: Int = 256,
    
    @SerializedName("lumaPoints")
    val lumaPoints: List<List<Float>> = listOf(
        listOf(0f, 0f), listOf(0.25f, 0.25f), listOf(0.5f, 0.5f),
        listOf(0.75f, 0.75f), listOf(1f, 1f)
    ),
    
    @SerializedName("rPoints")
    val rPoints: List<List<Float>> = listOf(
        listOf(0f, 0f), listOf(0.5f, 0.5f), listOf(1f, 1f)
    ),
    
    @SerializedName("gPoints")
    val gPoints: List<List<Float>> = listOf(
        listOf(0f, 0f), listOf(0.5f, 0.5f), listOf(1f, 1f)
    ),
    
    @SerializedName("bPoints")
    val bPoints: List<List<Float>> = listOf(
        listOf(0f, 0f), listOf(0.5f, 0.5f), listOf(1f, 1f)
    )
)

/**
 * Film grain parameters.
 */
data class GrainParams(
    @SerializedName("strength")
    val strength: Float = 0f,
    
    @SerializedName("size")
    val size: Float = 1f,
    
    @SerializedName("clumping")
    val clumping: Float = 0f,
    
    @SerializedName("toneMode")
    val toneMode: GrainToneMode = GrainToneMode.MID
)

enum class GrainToneMode {
    @SerializedName("SHADOW") SHADOW,
    @SerializedName("MID") MID,
    @SerializedName("FLAT") FLAT
}

/**
 * Optional effects parameters (for Phase 2).
 */
data class EffectsParams(
    @SerializedName("vignette")
    val vignette: Float = 0f,
    
    @SerializedName("bloom")
    val bloom: Float = 0f,
    
    @SerializedName("halation")
    val halation: Float = 0f,
    
    // Synthetic bokeh aperture (1.2, 1.4, 1.8, 2.8) - lower = stronger blur
    @SerializedName("bokehAperture")
    val bokehAperture: Float = 2.8f
)

/**
 * Room type converters for complex types.
 */
class PresetConverters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromPresetParams(params: PresetParams): String {
        return gson.toJson(params)
    }
    
    @TypeConverter
    fun toPresetParams(json: String): PresetParams {
        return gson.fromJson(json, PresetParams::class.java)
    }
    
    @TypeConverter
    fun fromPresetType(type: PresetType): String {
        return type.name
    }
    
    @TypeConverter
    fun toPresetType(name: String): PresetType {
        return PresetType.valueOf(name)
    }
}
