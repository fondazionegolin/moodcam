package com.moodcam.gallery

import android.content.Context
import java.util.UUID

/**
 * Manages the unique device key for gallery uploads.
 * Generated once on first launch, stored permanently.
 */
object DeviceKeyManager {
    
    private const val PREFS_NAME = "moodcam_device"
    private const val KEY_DEVICE_KEY = "device_key"
    
    /**
     * Get or create device key.
     * If no key exists, generates a new UUID and stores it.
     */
    fun getOrCreateDeviceKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        var key = prefs.getString(KEY_DEVICE_KEY, null)
        
        if (key == null) {
            key = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_KEY, key).apply()
        }
        
        return key
    }
    
    /**
     * Check if device key exists.
     */
    fun hasDeviceKey(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_DEVICE_KEY)
    }
}
