package com.example.domain.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HistoryEntry(
    val password: String,
    val timestamp: Long
)

data class VaultItem(
    val id: Long = 0,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val categories: List<String>,
    val isFavorite: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val passwordHistory: List<HistoryEntry> = emptyList()
)
