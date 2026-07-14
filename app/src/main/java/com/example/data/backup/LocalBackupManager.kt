package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.VaultEntity
import com.example.data.security.CryptoEngine
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import javax.crypto.spec.SecretKeySpec

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val saltBase64: String,
    val encryptedData: String
)

@JsonClass(generateAdapter = true)
data class BackupItem(
    val title: String,
    val usernameEncrypted: String,
    val passwordEncrypted: String,
    val urlEncrypted: String,
    val notesEncrypted: String,
    val categoriesString: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val lastModifiedAt: Long,
    val passwordHistoryEncrypted: String
)

object LocalBackupManager {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(BackupPayload::class.java)
    
    private val listType = Types.newParameterizedType(List::class.java, BackupItem::class.java)
    private val itemsAdapter = moshi.adapter<List<BackupItem>>(listType)

    /**
     * Exports all vault items into a single encrypted JSON file.
     * Returns the File containing the encrypted backup.
     */
    fun exportBackup(context: Context, items: List<VaultEntity>, password: String): File {
        // 1. Map to BackupItem representation
        val backupItems = items.map {
            BackupItem(
                title = it.title,
                usernameEncrypted = it.usernameEncrypted,
                passwordEncrypted = it.passwordEncrypted,
                urlEncrypted = it.urlEncrypted,
                notesEncrypted = it.notesEncrypted,
                categoriesString = it.categoriesString,
                isFavorite = it.isFavorite,
                createdAt = it.createdAt,
                lastModifiedAt = it.lastModifiedAt,
                passwordHistoryEncrypted = it.passwordHistoryEncrypted
            )
        }

        // 2. Serialize items list to JSON
        val plainJson = itemsAdapter.toJson(backupItems) ?: throw Exception("Failed to serialize items")

        // 3. Derive key for this backup using a fresh salt
        val salt = CryptoEngine.generateSalt(16)
        val backupKey = CryptoEngine.deriveKey(password, salt)

        // 4. Encrypt the JSON data
        val encryptedData = CryptoEngine.encrypt(plainJson, backupKey)

        // 5. Wrap in BackupPayload container
        val payload = BackupPayload(
            saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP),
            encryptedData = encryptedData
        )

        val finalJson = payloadAdapter.toJson(payload) ?: throw Exception("Failed to package payload")

        // 6. Write to local cache file for sharing
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "ironvault_backup_${System.currentTimeMillis()}.ironvault")
        backupFile.writeText(finalJson)
        
        return backupFile
    }

    /**
     * Imports and restores items from an encrypted file URI.
     * Returns the list of parsed VaultEntity items.
     */
    fun importBackup(context: Context, uri: Uri, password: String): List<VaultEntity> {
        // 1. Read string content from URI
        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: throw Exception("Could not open backup file")

        // 2. Parse backup payload
        val payload = payloadAdapter.fromJson(content) ?: throw Exception("Invalid backup file format")

        // 3. Extract salt and derive decryption key
        val salt = android.util.Base64.decode(payload.saltBase64, android.util.Base64.NO_WRAP)
        val backupKey = CryptoEngine.deriveKey(password, salt)

        // 4. Decrypt plain JSON
        val plainJson = CryptoEngine.decrypt(payload.encryptedData, backupKey)

        // 5. Parse items list
        val backupItems = itemsAdapter.fromJson(plainJson) ?: throw Exception("Corrupted backup content")

        // 6. Map back to database Entities
        return backupItems.map {
            VaultEntity(
                title = it.title,
                usernameEncrypted = it.usernameEncrypted,
                passwordEncrypted = it.passwordEncrypted,
                urlEncrypted = it.urlEncrypted,
                notesEncrypted = it.notesEncrypted,
                categoriesString = it.categoriesString,
                isFavorite = it.isFavorite,
                createdAt = it.createdAt,
                lastModifiedAt = it.lastModifiedAt,
                passwordHistoryEncrypted = it.passwordHistoryEncrypted
            )
        }
    }
}
