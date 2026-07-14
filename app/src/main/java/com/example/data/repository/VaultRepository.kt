package com.example.data.repository

import com.example.data.local.VaultDao
import com.example.data.local.VaultEntity
import com.example.data.security.CryptoEngine
import com.example.data.security.SessionManager
import com.example.domain.model.HistoryEntry
import com.example.domain.model.VaultItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.crypto.SecretKey

class VaultRepository(private val vaultDao: VaultDao) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val historyListType = Types.newParameterizedType(List::class.java, HistoryEntry::class.java)
    private val historyAdapter = moshi.adapter<List<HistoryEntry>>(historyListType)

    /**
     * Helper to retrieve active key or throw secure session exception.
     */
    private fun getActiveKey(): SecretKey {
        return SessionManager.getKey() ?: throw IllegalStateException("Vault session is locked")
    }

    /**
     * Map database Entity to decrypted Domain Model.
     */
    private fun mapToDomain(entity: VaultEntity, key: SecretKey): VaultItem {
        val username = if (entity.usernameEncrypted.isNotEmpty()) {
            CryptoEngine.decrypt(entity.usernameEncrypted, key)
        } else ""

        val password = if (entity.passwordEncrypted.isNotEmpty()) {
            CryptoEngine.decrypt(entity.passwordEncrypted, key)
        } else ""

        val url = if (entity.urlEncrypted.isNotEmpty()) {
            CryptoEngine.decrypt(entity.urlEncrypted, key)
        } else ""

        val notes = if (entity.notesEncrypted.isNotEmpty()) {
            CryptoEngine.decrypt(entity.notesEncrypted, key)
        } else ""

        val historyJson = if (entity.passwordHistoryEncrypted.isNotEmpty()) {
            CryptoEngine.decrypt(entity.passwordHistoryEncrypted, key)
        } else "[]"

        val historyList = historyAdapter.fromJson(historyJson) ?: emptyList()

        return VaultItem(
            id = entity.id,
            title = entity.title,
            username = username,
            password = password,
            url = url,
            notes = notes,
            categories = entity.categoriesString.split(",").filter { it.isNotEmpty() },
            isFavorite = entity.isFavorite,
            createdAt = entity.createdAt,
            lastModifiedAt = entity.lastModifiedAt,
            passwordHistory = historyList
        )
    }

    /**
     * Map domain model to fully encrypted database Entity.
     */
    private fun mapToEntity(item: VaultItem, key: SecretKey): VaultEntity {
        val usernameEncrypted = if (item.username.isNotEmpty()) {
            CryptoEngine.encrypt(item.username, key)
        } else ""

        val passwordEncrypted = if (item.password.isNotEmpty()) {
            CryptoEngine.encrypt(item.password, key)
        } else ""

        val urlEncrypted = if (item.url.isNotEmpty()) {
            CryptoEngine.encrypt(item.url, key)
        } else ""

        val notesEncrypted = if (item.notes.isNotEmpty()) {
            CryptoEngine.encrypt(item.notes, key)
        } else ""

        val historyJson = historyAdapter.toJson(item.passwordHistory) ?: "[]"
        val historyEncrypted = CryptoEngine.encrypt(historyJson, key)

        return VaultEntity(
            id = item.id,
            title = item.title,
            usernameEncrypted = usernameEncrypted,
            passwordEncrypted = passwordEncrypted,
            urlEncrypted = urlEncrypted,
            notesEncrypted = notesEncrypted,
            categoriesString = item.categories.joinToString(","),
            isFavorite = item.isFavorite,
            createdAt = item.createdAt,
            lastModifiedAt = item.lastModifiedAt,
            passwordHistoryEncrypted = historyEncrypted
        )
    }

    /**
     * Returns reactive Flow of raw encrypted database entities (used for backups).
     */
    fun getRawEntitiesFlow(): Flow<List<VaultEntity>> {
        return vaultDao.getAllItemsFlow()
    }

    /**
     * Returns reactive Flow of decrypted all vault items.
     */
    fun getAllItems(): Flow<List<VaultItem>> {
        return vaultDao.getAllItemsFlow().map { list ->
            val key = SessionManager.getKey()
            if (key == null) {
                emptyList()
            } else {
                list.map { mapToDomain(it, key) }
            }
        }
    }

    /**
     * Returns reactive Flow of decrypted favorite items.
     */
    fun getFavorites(): Flow<List<VaultItem>> {
        return vaultDao.getFavoritesFlow().map { list ->
            val key = SessionManager.getKey()
            if (key == null) {
                emptyList()
            } else {
                list.map { mapToDomain(it, key) }
            }
        }
    }

    /**
     * Fetches and decrypts an item by ID.
     */
    suspend fun getItemById(id: Long): VaultItem? {
        val entity = vaultDao.getItemById(id) ?: return null
        val key = getActiveKey()
        return mapToDomain(entity, key)
    }

    /**
     * Encrypts and saves/updates an item.
     */
    suspend fun saveItem(item: VaultItem): Long {
        val key = getActiveKey()
        val entity = mapToEntity(item, key)
        return vaultDao.insertItem(entity)
    }

    /**
     * Deletes an item by ID.
     */
    suspend fun deleteItemById(id: Long) {
        vaultDao.deleteItemById(id)
    }
}
