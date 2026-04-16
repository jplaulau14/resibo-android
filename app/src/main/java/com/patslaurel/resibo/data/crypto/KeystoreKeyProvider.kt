package com.patslaurel.resibo.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SQLCipher database passphrase using the Android Keystore.
 *
 * Strategy:
 *   1. On first launch: generate a random 32-byte passphrase, encrypt it with an
 *      AES-256-GCM key stored in the hardware-backed Keystore, and persist the
 *      ciphertext + IV in SharedPreferences.
 *   2. On subsequent launches: load the ciphertext from SharedPreferences, decrypt
 *      with the Keystore key, and return the plaintext passphrase.
 *
 * The passphrase never leaves the process in plaintext. The Keystore key is not
 * extractable — it can only be used on-device, and only by this app's UID.
 */
@Singleton
class KeystoreKeyProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        /** Return the SQLCipher passphrase, creating it on first call. */
        fun getOrCreatePassphrase(): ByteArray {
            val stored = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
            return if (stored != null) {
                decryptPassphrase(stored)
            } else {
                val passphrase = generateRandomPassphrase()
                val encrypted = encryptPassphrase(passphrase)
                prefs.edit().putString(KEY_ENCRYPTED_PASSPHRASE, encrypted).apply()
                passphrase
            }
        }

        private fun generateRandomPassphrase(): ByteArray {
            val bytes = ByteArray(PASSPHRASE_LENGTH)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes
        }

        private fun getOrCreateKeystoreKey(): SecretKey {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

            val spec =
                KeyGenParameterSpec
                    .Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

            return KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply { init(spec) }
                .generateKey()
        }

        private fun encryptPassphrase(passphrase: ByteArray): String {
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
                }
            val ciphertext = cipher.doFinal(passphrase)
            val iv = cipher.iv
            val combined = iv + ciphertext
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        private fun decryptPassphrase(encoded: String): ByteArray {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                }
            return cipher.doFinal(ciphertext)
        }

        companion object {
            private const val ANDROID_KEYSTORE = "AndroidKeyStore"
            private const val KEYSTORE_ALIAS = "resibo_db_key"
            private const val PREFS_NAME = "resibo_keystore_prefs"
            private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_db_passphrase"
            private const val PASSPHRASE_LENGTH = 32
            private const val GCM_IV_LENGTH = 12
            private const val GCM_TAG_LENGTH_BITS = 128
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
        }
    }
