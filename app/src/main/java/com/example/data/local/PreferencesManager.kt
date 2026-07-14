package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ironvault_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("app_theme") // "LIGHT", "DARK", "SYSTEM"
        private val LANGUAGE_KEY = stringPreferencesKey("app_language") // "en", "fa"
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val DRIVE_BACKUP_ENABLED_KEY = booleanPreferencesKey("drive_backup_enabled")
        private val AUTOLOCK_TIMEOUT_KEY = longPreferencesKey("autolock_timeout") // in milliseconds
        private val LAST_BACKUP_TIME_KEY = longPreferencesKey("last_backup_time")
        
        // Cryptographic metadata
        private val MASTER_PASSWORD_SALT = stringPreferencesKey("master_password_salt") // Base64 salt
        private val PASSWORD_VERIFICATION_BLOCK = stringPreferencesKey("password_verification_block") // Base64 verification
        private val BIOMETRIC_WRAPPED_MASTER_KEY = stringPreferencesKey("biometric_wrapped_master_key") // Base64 wrapped key
    }

    // --- Core UI Settings ---

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "SYSTEM"
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = theme
        }
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "en"
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = language
        }
    }

    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BIOMETRIC_ENABLED_KEY] ?: false
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    val driveBackupEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DRIVE_BACKUP_ENABLED_KEY] ?: false
    }

    suspend fun setDriveBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DRIVE_BACKUP_ENABLED_KEY] = enabled
        }
    }

    val autolockTimeoutFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[AUTOLOCK_TIMEOUT_KEY] ?: 60000L // Default 1 minute
    }

    suspend fun setAutolockTimeout(timeoutMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[AUTOLOCK_TIMEOUT_KEY] = timeoutMs
        }
    }

    val lastBackupTimeFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_BACKUP_TIME_KEY] ?: 0L
    }

    suspend fun setLastBackupTime(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_BACKUP_TIME_KEY] = timestamp
        }
    }

    // --- Security / Encryption Metadata ---

    val masterPasswordSaltFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[MASTER_PASSWORD_SALT]
    }

    suspend fun setMasterPasswordSalt(saltBase64: String) {
        context.dataStore.edit { prefs ->
            prefs[MASTER_PASSWORD_SALT] = saltBase64
        }
    }

    val passwordVerificationBlockFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PASSWORD_VERIFICATION_BLOCK]
    }

    suspend fun setPasswordVerificationBlock(blockBase64: String) {
        context.dataStore.edit { prefs ->
            prefs[PASSWORD_VERIFICATION_BLOCK] = blockBase64
        }
    }

    val biometricWrappedMasterKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[BIOMETRIC_WRAPPED_MASTER_KEY]
    }

    suspend fun setBiometricWrappedMasterKey(wrappedKeyBase64: String?) {
        context.dataStore.edit { prefs ->
            if (wrappedKeyBase64 == null) {
                prefs.remove(BIOMETRIC_WRAPPED_MASTER_KEY)
            } else {
                prefs[BIOMETRIC_WRAPPED_MASTER_KEY] = wrappedKeyBase64
            }
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
