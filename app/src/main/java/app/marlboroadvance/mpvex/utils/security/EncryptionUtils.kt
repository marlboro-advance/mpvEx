package app.marlboroadvance.mpvex.utils.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Utility class for encrypting and decrypting sensitive data using Android Keystore
 * Uses AES-256-GCM for secure encryption
 */
object EncryptionUtils {
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "mpvex_network_credentials_key"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val IV_SEPARATOR = "]"
  private const val GCM_TAG_LENGTH = 128

  /**
   * Get or create the encryption key from Android Keystore
   */
  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)

    // Check if key already exists
    if (keyStore.containsAlias(KEY_ALIAS)) {
      val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
      entry?.secretKey?.let { return it }
    }

    // Generate new key
    val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      ANDROID_KEYSTORE,
    )

    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setUserAuthenticationRequired(false) // Set to true if you want to require biometric/PIN
      .build()

    keyGenerator.init(keyGenParameterSpec)
    return keyGenerator.generateKey()
  }

  /**
   * Encrypt a string value
   * @param plainText The text to encrypt
   * @return Base64 encoded encrypted text with IV, or empty string if encryption fails
   */
  fun encrypt(plainText: String): String {
    if (plainText.isEmpty()) return ""

    return try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

      val iv = cipher.iv
      val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

      // Combine IV and encrypted data: [IV_base64]encrypted_base64
      val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
      val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

      "$ivBase64$IV_SEPARATOR$encryptedBase64"
    } catch (e: Exception) {
      android.util.Log.e("EncryptionUtils", "Encryption failed", e)
      ""
    }
  }

  /**
   * Decrypt an encrypted string
   * @param encryptedText The encrypted text (with IV)
   * @return Decrypted plain text, or empty string if decryption fails
   */
  fun decrypt(encryptedText: String): String {
    if (encryptedText.isEmpty()) return ""

    return try {
      // Split IV and encrypted data
      val parts = encryptedText.split(IV_SEPARATOR)
      if (parts.size != 2) {
        android.util.Log.e("EncryptionUtils", "Invalid encrypted text format")
        return ""
      }

      val iv = Base64.decode(parts[0], Base64.NO_WRAP)
      val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

      val cipher = Cipher.getInstance(TRANSFORMATION)
      val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

      val decryptedBytes = cipher.doFinal(encryptedBytes)
      String(decryptedBytes, StandardCharsets.UTF_8)
    } catch (e: Exception) {
      android.util.Log.e("EncryptionUtils", "Decryption failed", e)
      ""
    }
  }

  /**
   * Check if a string is encrypted (has IV separator)
   */
  fun isEncrypted(text: String): Boolean {
    return text.contains(IV_SEPARATOR)
  }
}
