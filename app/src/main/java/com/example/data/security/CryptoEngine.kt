package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "IronVaultBiometricKey"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    
    // Security guidelines: 310,000 iterations for PBKDF2-HMAC-SHA256
    private const val PBKDF2_ITERATIONS = 310000
    private const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure random byte array.
     */
    fun generateSalt(size: Int = 16): ByteArray {
        val salt = ByteArray(size)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit AES key from a Master Password using PBKDF2-HMAC-SHA256.
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derived = factory.generateSecret(spec).encoded
        return SecretKeySpec(derived, "AES")
    }

    /**
     * Encrypts plaintext using AES-256-GCM with the provided key.
     * Returns a base64 string containing: Base64(IV + EncryptedData)
     */
    fun encrypt(plaintext: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val iv = ByteArray(12) // GCM standard IV size
        secureRandom.nextBytes(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Combine IV and Ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts AES-256-GCM encrypted base64 payload.
     */
    fun decrypt(encryptedBase64: String, key: SecretKey): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < 12) throw IllegalArgumentException("Invalid cipher size")
        
        val iv = ByteArray(12)
        val ciphertext = ByteArray(combined.size - 12)
        
        System.arraycopy(combined, 0, iv, 0, 12)
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)
        
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plaintextBytes = cipher.doFinal(ciphertext)
        
        return String(plaintextBytes, Charsets.UTF_8)
    }

    // --- Android Keystore Biometric Key Operations ---

    /**
     * Creates or retrieves an AES key in the Android Keystore that is protected by biometrics.
     */
    fun getOrCreateBiometricWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val entry = keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            // Works for API 24-36. PIN, Pattern, or Biometric.
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Prepares an authorized Cipher object for BiometricPrompt.CryptoObject.
     */
    fun getBiometricCipher(mode: Int, iv: ByteArray? = null): Cipher {
        val secretKey = getOrCreateBiometricWrappingKey()
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(mode, secretKey)
        } else {
            cipher.init(mode, secretKey, GCMParameterSpec(128, iv))
        }
        return cipher
    }

    /**
     * Wraps (encrypts) the Master Key using a biometric-authenticated Cipher.
     */
    fun wrapMasterKey(masterKey: SecretKey, authorizedCipher: Cipher): String {
        val wrappedBytes = authorizedCipher.doFinal(masterKey.encoded)
        val iv = authorizedCipher.iv
        
        val combined = ByteArray(iv.size + wrappedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(wrappedBytes, 0, combined, iv.size, wrappedBytes.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Unwraps (decrypts) the Master Key using a biometric-authenticated Cipher.
     */
    fun unwrapMasterKey(wrappedBase64: String, authorizedCipher: Cipher): SecretKey {
        val combined = Base64.decode(wrappedBase64, Base64.NO_WRAP)
        val ivSize = 12
        val ciphertext = ByteArray(combined.size - ivSize)
        System.arraycopy(combined, ivSize, ciphertext, 0, ciphertext.size)
        
        val masterKeyBytes = authorizedCipher.doFinal(ciphertext)
        return SecretKeySpec(masterKeyBytes, "AES")
    }
}

/**
 * Singleton to securely manage active session credentials.
 * Wipes credentials on locking.
 */
object SessionManager {
    private var activeKey: SecretKey? = null
    var masterPasswordPlain: String? = null // helpful for backing up or re-encrypting

    fun setKey(key: SecretKey, password: String) {
        activeKey = key
        masterPasswordPlain = password
    }

    fun getKey(): SecretKey? = activeKey

    fun isUnlocked(): Boolean = activeKey != null

    fun lock() {
        activeKey = null
        masterPasswordPlain = null
    }
}
