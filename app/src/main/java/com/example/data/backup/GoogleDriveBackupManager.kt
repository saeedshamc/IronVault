package com.example.data.backup

import android.content.Context
import android.util.Log
import com.example.data.local.VaultEntity
import com.example.data.local.AppDatabase
import com.example.data.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object GoogleDriveBackupManager {

    private const val TAG = "GoogleDriveBackup"
    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Uploads the encrypted database payload to the user's private Google Drive App Data folder.
     */
    suspend fun backupToDrive(
        context: Context,
        accessToken: String,
        items: List<VaultEntity>,
        masterPasswordPlain: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Generate the encrypted backup text
            val backupFile = LocalBackupManager.exportBackup(context, items, masterPasswordPlain)
            val encryptedPayloadText = backupFile.readText()
            
            // Clean up the temporary file
            try { backupFile.delete() } catch (e: Exception) {}

            // 2. Query if a previous backup file exists in the appDataFolder
            val existingFileId = findExistingBackupFileId(accessToken)

            if (existingFileId != null) {
                // Update existing file
                return@withContext updateBackupFile(accessToken, existingFileId, encryptedPayloadText)
            } else {
                // Create new file
                return@withContext createBackupFile(accessToken, encryptedPayloadText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up to Google Drive", e)
            false
        }
    }

    /**
     * Restores vault items from the private Google Drive App Data folder.
     */
    suspend fun restoreFromDrive(
        context: Context,
        accessToken: String,
        masterPasswordPlain: String
    ): List<VaultEntity>? = withContext(Dispatchers.IO) {
        try {
            val fileId = findExistingBackupFileId(accessToken) ?: return@withContext null
            
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download backup file: ${response.code}")
                    return@withContext null
                }
                
                val encryptedText = response.body?.string() ?: return@withContext null
                
                // Write payload temporarily to decrypt using our local engine
                val tempFile = java.io.File(context.cacheDir, "temp_restore.ironvault")
                tempFile.writeText(encryptedText)
                
                val entities = LocalBackupManager.importBackup(context, android.net.Uri.fromFile(tempFile), masterPasswordPlain)
                
                try { tempFile.delete() } catch (e: Exception) {}
                
                return@withContext entities
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from Google Drive", e)
            null
        }
    }

    /**
     * Queries Google Drive for an existing file named "ironvault_backup.json" inside appDataFolder.
     */
    private fun findExistingBackupFileId(accessToken: String): String? {
        val queryUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='ironvault_backup.json'&fields=files(id)"
        val request = Request.Builder()
            .url(queryUrl)
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to search AppData folder: ${response.code} - ${response.body?.string()}")
                return null
            }
            
            val json = JSONObject(response.body?.string() ?: "{}")
            val filesArray = json.optJSONArray("files")
            if (filesArray != null && filesArray.length() > 0) {
                return filesArray.getJSONObject(0).getString("id")
            }
        }
        return null
    }

    /**
     * Creates a new backup file in Google Drive AppData folder using multipart upload.
     */
    private fun createBackupFile(accessToken: String, content: String): Boolean {
        val metadata = JSONObject().apply {
            put("name", "ironvault_backup.json")
            put("parents", listOf("appDataFolder"))
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(
                metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .addPart(
                content.toRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.d(TAG, "Backup file created successfully in Google Drive.")
                return true
            } else {
                Log.e(TAG, "Failed to create Google Drive backup: ${response.code} - ${response.body?.string()}")
                return false
            }
        }
    }

    /**
     * Updates an existing backup file in Google Drive AppData folder.
     */
    private fun updateBackupFile(accessToken: String, fileId: String, content: String): Boolean {
        val requestBody = content.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $accessToken")
            .patch(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.d(TAG, "Backup file updated successfully in Google Drive.")
                return true
            } else {
                Log.e(TAG, "Failed to update Google Drive backup: ${response.code} - ${response.body?.string()}")
                return false
            }
        }
    }
}
