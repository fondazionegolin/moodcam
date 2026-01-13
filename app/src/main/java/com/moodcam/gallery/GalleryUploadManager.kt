package com.moodcam.gallery

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Handles uploading photos to the MoodCam public gallery.
 */
class GalleryUploadManager(private val context: Context) {
    
    companion object {
        // TODO: Update with your server URL (Cloudflare tunnel)
        private const val API_BASE = "https://moodcam.golinelli.ai/api"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    /**
     * Upload photo to gallery.
     * 
     * @param photoUri URI of the photo to upload
     * @param filterName Name of the filter/preset used
     * @return Result with success status and optional error message
     */
    suspend fun uploadToGallery(
        photoUri: Uri,
        filterName: String
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val deviceKey = DeviceKeyManager.getOrCreateDeviceKey(context)
            val username = UserSettings.getUsername(context)
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            
            // Get file from URI
            val inputStream = context.contentResolver.openInputStream(photoUri)
                ?: return@withContext UploadResult.Error("Could not open photo")
            
            // Create temp file
            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            // Build multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("device_key", deviceKey)
                .addFormDataPart("device", deviceModel)
                .addFormDataPart("filter_name", filterName)
                .addFormDataPart("username", username)
                .addFormDataPart(
                    "photo",
                    "photo.jpg",
                    tempFile.asRequestBody("image/jpeg".toMediaType())
                )
                .build()
            
            val request = Request.Builder()
                .url("$API_BASE/upload")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            // Clean up temp file
            tempFile.delete()
            
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                UploadResult.Success(
                    photoId = json.optString("photoId", ""),
                    username = json.optString("username", username)
                )
            } else {
                val errorBody = response.body?.string()
                val errorJson = try { JSONObject(errorBody ?: "{}") } catch (e: Exception) { JSONObject() }
                UploadResult.Error(errorJson.optString("error", "Upload failed"))
            }
            
        } catch (e: IOException) {
            UploadResult.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            UploadResult.Error("Upload failed: ${e.message}")
        }
    }
}

/**
 * Result of upload operation.
 */
sealed class UploadResult {
    data class Success(val photoId: String, val username: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
}
