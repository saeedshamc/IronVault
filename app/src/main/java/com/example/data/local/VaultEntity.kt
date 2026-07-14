package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, // Kept plaintext or cipher based on preference (plaintext allows instant localized DB search)
    val usernameEncrypted: String,
    val passwordEncrypted: String,
    val urlEncrypted: String,
    val notesEncrypted: String,
    val categoriesString: String, // Comma-separated list of categories
    val isFavorite: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val passwordHistoryEncrypted: String // JSON list of old passwords
)
