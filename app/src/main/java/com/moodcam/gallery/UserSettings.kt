package com.moodcam.gallery

import android.content.Context

/**
 * Manages user settings including display username.
 */
object UserSettings {
    
    private const val PREFS_NAME = "moodcam_user"
    private const val KEY_USERNAME = "username"
    
    // Random username parts
    private val adjectives = listOf(
        "Happy", "Sunny", "Misty", "Golden", "Silver", "Velvet", 
        "Smoky", "Dusty", "Faded", "Vivid", "Mellow", "Rustic",
        "Dreamy", "Vintage", "Classic", "Analog", "Retro", "Warm"
    )
    
    private val nouns = listOf(
        "Lens", "Frame", "Grain", "Film", "Shutter", "Focus", 
        "Light", "Shadow", "Pixel", "Mood", "Tone", "Shot",
        "Flash", "Bokeh", "Chrome", "Velvia", "Portra", "Kodak"
    )
    
    /**
     * Get username. If not set, generates a random one.
     */
    fun getUsername(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, null) ?: generateRandomUsername()
    }
    
    /**
     * Set custom username.
     */
    fun setUsername(context: Context, username: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USERNAME, username.trim()).apply()
    }
    
    /**
     * Check if user has set a custom username.
     */
    fun hasCustomUsername(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_USERNAME)
    }
    
    /**
     * Clear username (revert to random).
     */
    fun clearUsername(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_USERNAME).apply()
    }
    
    /**
     * Generate a random username.
     */
    private fun generateRandomUsername(): String {
        val adj = adjectives.random()
        val noun = nouns.random()
        val num = (1..999).random()
        return "$adj$noun$num"
    }
}
