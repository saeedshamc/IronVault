package com.example.presentation

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.backup.GoogleDriveBackupManager
import com.example.data.backup.LocalBackupManager
import com.example.data.local.PreferencesManager
import com.example.data.repository.VaultRepository
import com.example.data.security.CryptoEngine
import com.example.data.security.PasswordGenerator
import com.example.data.security.SessionManager
import com.example.domain.model.HistoryEntry
import com.example.domain.model.VaultItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class VaultViewModel(
    private val context: Context,
    private val repository: VaultRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // --- State Observables ---
    
    val themeState: StateFlow<String> = preferencesManager.themeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM"
    )

    val languageState: StateFlow<String> = preferencesManager.languageFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "en"
    )

    val biometricEnabledState: StateFlow<Boolean> = preferencesManager.biometricEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val driveBackupEnabledState: StateFlow<Boolean> = preferencesManager.driveBackupEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val autolockTimeoutState: StateFlow<Long> = preferencesManager.autolockTimeoutFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 60000L
    )

    val lastBackupTimeState: StateFlow<Long> = preferencesManager.lastBackupTimeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0L
    )

    private val _isSetupComplete = MutableStateFlow(false)
    val isSetupComplete = _isSetupComplete.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked = _isUnlocked.asStateFlow()

    // --- Brute Force Protection ---
    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts = _failedAttempts.asStateFlow()

    private val _lockoutTimeRemaining = MutableStateFlow(0) // in seconds
    val lockoutTimeRemaining = _lockoutTimeRemaining.asStateFlow()

    private var lockoutJob: Job? = null

    // --- Vault List States ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    val vaultItems: StateFlow<List<VaultItem>> = combine(
        repository.getAllItems(),
        _searchQuery,
        _selectedCategory
    ) { items, query, category ->
        items.filter { item ->
            val matchesSearch = item.title.contains(query, ignoreCase = true) ||
                    item.username.contains(query, ignoreCase = true) ||
                    item.url.contains(query, ignoreCase = true)
            
            val matchesCategory = when (category) {
                "All" -> true
                "Favorites" -> item.isFavorite
                else -> item.categories.contains(category)
            }

            matchesSearch && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Backup & Restore Status Msg ---
    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage = _backupStatusMessage.asStateFlow()

    init {
        checkSetupStatus()
    }

    private fun checkSetupStatus() {
        viewModelScope.launch {
            val block = preferencesManager.passwordVerificationBlockFlow.first()
            _isSetupComplete.value = block != null
        }
    }

    // --- Authentication Actions ---

    /**
     * Set up a new vault on first run with the provided Master Password.
     */
    fun setupVault(password: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            // 1. Generate salt and derive master key
            val salt = CryptoEngine.generateSalt(16)
            val masterKey = CryptoEngine.deriveKey(password, salt)

            // 2. Encrypt verification block
            val verificationText = "ironvault_verified_token_v1"
            val verificationBlock = CryptoEngine.encrypt(verificationText, masterKey)

            // 3. Save to Datastore
            preferencesManager.setMasterPasswordSalt(Base64.encodeToString(salt, Base64.NO_WRAP))
            preferencesManager.setPasswordVerificationBlock(verificationBlock)

            // 4. Set Session Key
            SessionManager.setKey(masterKey, password)
            _isSetupComplete.value = true
            _isUnlocked.value = true
            onComplete()
        }
    }

    /**
     * Unlocks the vault using the entered Master Password.
     */
    fun unlockWithPassword(password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (_lockoutTimeRemaining.value > 0) {
            onFailure("Rate limit active. Please wait.")
            return
        }

        viewModelScope.launch {
            val saltBase64 = preferencesManager.masterPasswordSaltFlow.first() ?: return@launch
            val savedBlock = preferencesManager.passwordVerificationBlockFlow.first() ?: return@launch

            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val derivedKey = CryptoEngine.deriveKey(password, salt)

            try {
                // Verify the key by decrypting the verification block
                val decrypted = CryptoEngine.decrypt(savedBlock, derivedKey)
                if (decrypted == "ironvault_verified_token_v1") {
                    // Success! Reset failed attempts
                    _failedAttempts.value = 0
                    SessionManager.setKey(derivedKey, password)
                    _isUnlocked.value = true
                    onSuccess()
                } else {
                    handleFailedUnlockAttempt(onFailure)
                }
            } catch (e: Exception) {
                handleFailedUnlockAttempt(onFailure)
            }
        }
    }

    private fun handleFailedUnlockAttempt(onFailure: (String) -> Unit) {
        _failedAttempts.value += 1
        val attempts = _failedAttempts.value
        if (attempts >= 5) {
            // Trigger exponential backoff lock (e.g. 30s)
            val delaySeconds = 30
            _lockoutTimeRemaining.value = delaySeconds
            startLockoutTimer()
            onFailure("Too many failed attempts. Locked for $delaySeconds seconds.")
        } else {
            onFailure("Incorrect Master Password. Attempts remaining: ${5 - attempts}")
        }
    }

    private fun startLockoutTimer() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            while (_lockoutTimeRemaining.value > 0) {
                delay(1000)
                _lockoutTimeRemaining.value -= 1
            }
        }
    }

    /**
     * Configures the biometric wrapped key when biometrics is toggled ON.
     */
    fun setupBiometricKey(authorizedCipher: Cipher) {
        viewModelScope.launch {
            val masterKey = SessionManager.getKey() ?: return@launch
            val wrappedKeyBase64 = CryptoEngine.wrapMasterKey(masterKey, authorizedCipher)
            preferencesManager.setBiometricWrappedMasterKey(wrappedKeyBase64)
            preferencesManager.setBiometricEnabled(true)
        }
    }

    /**
     * Disables biometric login and deletes wrapped keys.
     */
    fun disableBiometrics() {
        viewModelScope.launch {
            preferencesManager.setBiometricWrappedMasterKey(null)
            preferencesManager.setBiometricEnabled(false)
        }
    }

    /**
     * Unlocks the vault using the authorized biometric Cipher.
     */
    fun unlockWithBiometrics(authorizedCipher: Cipher, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val wrappedKeyBase64 = preferencesManager.biometricWrappedMasterKeyFlow.first() ?: return@launch
            try {
                val unwrappedKey = CryptoEngine.unwrapMasterKey(wrappedKeyBase64, authorizedCipher)
                
                // Set the session credentials
                SessionManager.setKey(unwrappedKey, "") // Pass password as empty since unwrapped key directly unlocked
                _isUnlocked.value = true
                _failedAttempts.value = 0
                onSuccess()
            } catch (e: Exception) {
                onFailure("Biometric decryption failed")
            }
        }
    }

    fun lockVault() {
        SessionManager.lock()
        _isUnlocked.value = false
    }

    // --- Credential Actions ---

    fun saveCredential(
        id: Long = 0,
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        categories: List<String>,
        isFavorite: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            // Detect password change for History
            var history = emptyList<HistoryEntry>()
            if (id != 0L) {
                val oldItem = repository.getItemById(id)
                if (oldItem != null) {
                    history = oldItem.passwordHistory
                    if (oldItem.password != password) {
                        history = history + HistoryEntry(oldItem.password, System.currentTimeMillis())
                    }
                }
            }

            val item = VaultItem(
                id = id,
                title = title,
                username = username,
                password = password,
                url = url,
                notes = notes,
                categories = categories,
                isFavorite = isFavorite,
                createdAt = if (id == 0L) System.currentTimeMillis() else (repository.getItemById(id)?.createdAt ?: System.currentTimeMillis()),
                lastModifiedAt = System.currentTimeMillis(),
                passwordHistory = history
            )
            repository.saveItem(item)
            onComplete()
        }
    }

    fun deleteCredential(id: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteItemById(id)
            onComplete()
        }
    }

    fun toggleFavorite(item: VaultItem) {
        viewModelScope.launch {
            repository.saveItem(item.copy(isFavorite = !item.isFavorite))
        }
    }

    // --- Settings / Preferences ---

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            preferencesManager.setTheme(theme)
        }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch {
            preferencesManager.setLanguage(language)
        }
    }

    fun updateAutolockTimeout(timeoutMs: Long) {
        viewModelScope.launch {
            preferencesManager.setAutolockTimeout(timeoutMs)
        }
    }

    fun setDriveBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDriveBackupEnabled(enabled)
        }
    }

    /**
     * Changes the master password and re-encrypts all vault entries.
     */
    fun changeMasterPassword(newPassword: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentKey = SessionManager.getKey()
        if (currentKey == null) {
            onFailure("Vault is locked")
            return
        }

        viewModelScope.launch {
            try {
                // 1. Get all current items
                val currentItems = repository.getAllItems().first()

                // 2. Generate a fresh salt and derive a new master key
                val newSalt = CryptoEngine.generateSalt(16)
                val newMasterKey = CryptoEngine.deriveKey(newPassword, newSalt)

                // 3. Temporarily set the session key to the new key so repository writes encrypt with the new key!
                SessionManager.setKey(newMasterKey, newPassword)

                // 4. Save/re-encrypt each item in the database
                currentItems.forEach { item ->
                    repository.saveItem(item)
                }

                // 5. Encrypt new password verification block
                val verificationText = "ironvault_verified_token_v1"
                val verificationBlock = CryptoEngine.encrypt(verificationText, newMasterKey)

                // 6. Save new crypto parameters to datastore
                preferencesManager.setMasterPasswordSalt(Base64.encodeToString(newSalt, Base64.NO_WRAP))
                preferencesManager.setPasswordVerificationBlock(verificationBlock)

                // 7. If biometrics are enabled, we must re-wrap the new key.
                // We'll reset biometrics so the user can easily re-authenticate on next prompt.
                disableBiometrics()

                onSuccess()
            } catch (e: Exception) {
                // Restore current key on failure
                SessionManager.setKey(currentKey, SessionManager.masterPasswordPlain ?: "")
                onFailure("Re-encryption failed: ${e.localizedMessage}")
            }
        }
    }

    // --- Backup & Restore Actions ---

    fun performLocalExport(onSuccess: (File) -> Unit, onFailure: (String) -> Unit) {
        val password = SessionManager.masterPasswordPlain
        if (password == null) {
            onFailure("Session is locked")
            return
        }

        viewModelScope.launch {
            try {
                val items = repository.getRawEntitiesFlow().first()
                val backupFile = LocalBackupManager.exportBackup(context, items, password)
                onSuccess(backupFile)
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Export failed")
            }
        }
    }

    fun performLocalImport(uri: Uri, onFailure: (String) -> Unit) {
        val password = SessionManager.masterPasswordPlain
        if (password == null) {
            onFailure("Session is locked")
            return
        }

        viewModelScope.launch {
            try {
                val importedEntities = LocalBackupManager.importBackup(context, uri, password)
                
                // Save imported items to the database
                importedEntities.forEach { entity ->
                    // Map back to item
                    val item = VaultItem(
                        title = entity.title,
                        username = CryptoEngine.decrypt(entity.usernameEncrypted, SessionManager.getKey()!!),
                        password = CryptoEngine.decrypt(entity.passwordEncrypted, SessionManager.getKey()!!),
                        url = if (entity.urlEncrypted.isNotEmpty()) CryptoEngine.decrypt(entity.urlEncrypted, SessionManager.getKey()!!) else "",
                        notes = if (entity.notesEncrypted.isNotEmpty()) CryptoEngine.decrypt(entity.notesEncrypted, SessionManager.getKey()!!) else "",
                        categories = entity.categoriesString.split(",").filter { it.isNotEmpty() },
                        isFavorite = entity.isFavorite,
                        createdAt = entity.createdAt,
                        lastModifiedAt = entity.lastModifiedAt
                    )
                    repository.saveItem(item)
                }
                _backupStatusMessage.value = "Imported ${importedEntities.size} credentials successfully!"
            } catch (e: Exception) {
                onFailure("Import failed: check password or file integrity")
            }
        }
    }

    fun performGoogleDriveBackup(accessToken: String) {
        val password = SessionManager.masterPasswordPlain
        val activeKey = SessionManager.getKey()
        if (password == null || activeKey == null) {
            _backupStatusMessage.value = "Session locked. Cannot backup."
            return
        }

        viewModelScope.launch {
            _backupStatusMessage.value = "Starting backup..."
            val items = repository.getRawEntitiesFlow().first()
            val success = GoogleDriveBackupManager.backupToDrive(context, accessToken, items, password)
            if (success) {
                preferencesManager.setLastBackupTime(System.currentTimeMillis())
                _backupStatusMessage.value = "Backup uploaded to Google Drive successfully!"
            } else {
                _backupStatusMessage.value = "Backup upload failed. Check network or credentials."
            }
        }
    }

    fun performGoogleDriveRestore(accessToken: String) {
        val password = SessionManager.masterPasswordPlain
        if (password == null) {
            _backupStatusMessage.value = "Session locked. Cannot restore."
            return
        }

        viewModelScope.launch {
            _backupStatusMessage.value = "Fetching from Google Drive..."
            val restoredItems = GoogleDriveBackupManager.restoreFromDrive(context, accessToken, password)
            if (restoredItems != null) {
                restoredItems.forEach { entity ->
                    val item = VaultItem(
                        title = entity.title,
                        username = CryptoEngine.decrypt(entity.usernameEncrypted, SessionManager.getKey()!!),
                        password = CryptoEngine.decrypt(entity.passwordEncrypted, SessionManager.getKey()!!),
                        url = if (entity.urlEncrypted.isNotEmpty()) CryptoEngine.decrypt(entity.urlEncrypted, SessionManager.getKey()!!) else "",
                        notes = if (entity.notesEncrypted.isNotEmpty()) CryptoEngine.decrypt(entity.notesEncrypted, SessionManager.getKey()!!) else "",
                        categories = entity.categoriesString.split(",").filter { it.isNotEmpty() },
                        isFavorite = entity.isFavorite,
                        createdAt = entity.createdAt,
                        lastModifiedAt = entity.lastModifiedAt
                    )
                    repository.saveItem(item)
                }
                _backupStatusMessage.value = "Restored ${restoredItems.size} items from Google Drive!"
            } else {
                _backupStatusMessage.value = "No backup found or restore failed."
            }
        }
    }

    fun clearBackupMessage() {
        _backupStatusMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }
}

// --- Factory ---

class VaultViewModelFactory(
    private val context: Context,
    private val repository: VaultRepository,
    private val preferencesManager: PreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VaultViewModel(context, repository, preferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
