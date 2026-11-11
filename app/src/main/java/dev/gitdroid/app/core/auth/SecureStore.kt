package dev.gitdroid.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted, on-device storage for the GitHub token and the MCP bearer token.
 *
 * Values are sealed with an AES-256-GCM key that lives inside the Android
 * Keystore, so the key material never leaves the secure hardware (or the
 * TEE-backed keystore on devices that have one). The ciphertext is kept in
 * ordinary preferences, which keeps the token out of any backup.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── GitHub token ────────────────────────────────────────────────────────

    var githubToken: String?
        get() = prefs.getString(KEY_GITHUB_TOKEN, null)?.let { decrypt(it) }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_GITHUB_TOKEN) else putString(KEY_GITHUB_TOKEN, encrypt(value))
            }.apply()
        }

    // ── MCP bearer token ────────────────────────────────────────────────────

    var mcpToken: String?
        get() = prefs.getString(KEY_MCP_TOKEN, null)?.let { decrypt(it) }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_MCP_TOKEN) else putString(KEY_MCP_TOKEN, encrypt(value))
            }.apply()
        }

    // ── Sign-in metadata ────────────────────────────────────────────────────

    var githubLogin: String?
        get() = prefs.getString(KEY_GITHUB_LOGIN, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_GITHUB_LOGIN) else putString(KEY_GITHUB_LOGIN, value)
        }.apply()

    /** The method the current session was established with. */
    var authMethod: String?
        get() = prefs.getString(KEY_AUTH_METHOD, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_AUTH_METHOD) else putString(KEY_AUTH_METHOD, value)
        }.apply()

    /** OAuth device-flow tokens expire; this holds the refresh token when we have one. */
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)?.let { decrypt(it) }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_REFRESH_TOKEN) else putString(KEY_REFRESH_TOKEN, encrypt(value))
            }.apply()
        }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_GITHUB_TOKEN)
            .remove(KEY_GITHUB_LOGIN)
            .remove(KEY_AUTH_METHOD)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    // ── Crypto ──────────────────────────────────────────────────────────────

    private fun encrypt(plain: String): String = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.getOrElse {
        // If the keystore is unavailable (a rare OEM bug, or a rooted device with
        // a broken keystore), fall back to plain storage rather than locking the
        // user out of their own account.
        plain
    }

    private fun decrypt(stored: String): String? = runCatching {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 2) return@runCatching null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val payload = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(payload), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "gitdroid_secure_store"
        private const val KEY_GITHUB_TOKEN = "gh_token"
        private const val KEY_REFRESH_TOKEN = "gh_refresh"
        private const val KEY_MCP_TOKEN = "mcp_token"
        private const val KEY_GITHUB_LOGIN = "gh_login"
        private const val KEY_AUTH_METHOD = "auth_method"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "gitdroid_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val SEPARATOR = ":"

        /** Generates a URL-safe random token, used for the MCP bearer credential. */
        fun randomToken(byteLength: Int = 24): String {
            val bytes = ByteArray(byteLength)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
