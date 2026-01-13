package com.moodcam.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles photo export to device storage with EXIF metadata.
 */
class PhotoExporter(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    
    /**
     * Export a processed bitmap to MediaStore.
     * 
     * @param bitmap The processed image to save
     * @param presetName Name of the preset used (stored in EXIF)
     * @param quality JPEG quality (0-100)
     * @return The URI of the saved image, or null if failed
     */
    suspend fun exportPhoto(
        bitmap: Bitmap,
        presetName: String,
        quality: Int = 95,
        rotation: Int = 0
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // Rotate bitmap if needed
            val finalBitmap = if (rotation != 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotation.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val timestamp = dateFormat.format(Date())
            val filename = "MOODCAM_$timestamp.jpg"
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - use MediaStore
                saveToMediaStore(finalBitmap, filename, presetName, quality)
            } else {
                // Legacy - save to Pictures directory
                saveToLegacyStorage(finalBitmap, filename, presetName, quality)
            }
            
            Log.d(TAG, "Photo saved: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export photo", e)
            null
        }
    }
    
    private suspend fun saveToMediaStore(
        bitmap: Bitmap,
        filename: String,
        presetName: String,
        quality: Int
    ): Uri? = withContext(Dispatchers.IO) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MoodCam")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext null
        
        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        
        // Write EXIF data
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                writeCommonExif(exif, presetName)
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write EXIF", e)
        }
        
        // Mark as complete
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        
        uri
    }
    
    @Suppress("DEPRECATION")
    private suspend fun saveToLegacyStorage(
        bitmap: Bitmap,
        filename: String,
        presetName: String,
        quality: Int
    ): Uri? = withContext(Dispatchers.IO) {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val moodcamDir = File(picturesDir, "MoodCam")
        if (!moodcamDir.exists()) {
            moodcamDir.mkdirs()
        }
        
        val file = File(moodcamDir, filename)
        
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        
        // Write EXIF data
        try {
            val exif = ExifInterface(file.absolutePath)
            writeCommonExif(exif, presetName)
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write EXIF", e)
        }
        
        // Trigger media scanner
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }
    
    private fun writeCommonExif(exif: ExifInterface, presetName: String) {
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "MoodCam")
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Preset: $presetName")
        exif.setAttribute(ExifInterface.TAG_DATETIME, 
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()))
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        
        // Store metadata in UserComment for backend processing
        val username = com.moodcam.gallery.UserSettings.getUsername(context)
        val metadata = "filter=$presetName;username=$username;device=${Build.MANUFACTURER} ${Build.MODEL}"
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, metadata)
    }
    
    companion object {
        private const val TAG = "PhotoExporter"
    }
}
