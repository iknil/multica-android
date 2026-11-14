package ai.multica.android.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stores only AES-GCM ciphertext in private, non-backed-up preferences. Each
 * installation gets a random AndroidKeyStore alias; the non-exportable key and
 * every GCM IV are independently generated. Reads and writes are serialized so
 * logout/401 cannot interleave with a completed login write.
 */
class AndroidKeystoreSessionStore(
    context: Context,
    storageId: String = DEFAULT_STORAGE_ID,
) : SessionStore {
    private val isDefaultStorage = storageId == DEFAULT_STORAGE_ID
    private val preferencesName = if (isDefaultStorage) PREFERENCES_NAME else "${PREFERENCES_NAME}_$storageId"
    private val aliasPrefix = "${KEY_ALIAS_PREFIX}${if (isDefaultStorage) "" else "${storageId}_"}"
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    init {
        require(STORAGE_ID_PATTERN.matches(storageId)) { "Invalid secure session storage id" }
    }

    override suspend fun readToken(): String? = mutex.withLock {
        val iv = preferences.getString(KEY_IV, null)
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
        val storedAlias = preferences.getString(KEY_ALIAS, null)
        if (iv == null || ciphertext == null) {
            if (iv != null || ciphertext != null || storedAlias != null) clearEnvelope(storedAlias)
            return@withLock null
        }

        // Envelopes written by v1 did not record an alias and used a fixed one.
        val alias = storedAlias ?: if (isDefaultStorage) LEGACY_KEY_ALIAS else null
        if (alias == null || !isOwnedAlias(alias)) {
            clearEnvelope(storedAlias)
            return@withLock null
        }
        val key = readKey(alias)
        if (key == null) {
            // Never create a replacement key for ciphertext whose key was lost.
            clearEnvelope(alias)
            return@withLock null
        }

        val token = runCatching {
            TokenCipher.decrypt(
                CipherEnvelope(Base64.decode(iv, Base64.NO_WRAP), Base64.decode(ciphertext, Base64.NO_WRAP)),
                key,
            )
        }.getOrElse {
            // Covers restored ciphertext without its device-bound key, corruption, and key invalidation.
            clearEnvelope(alias)
            return@withLock null
        }

        if (alias == LEGACY_KEY_ALIAS) {
            // Migrate the legacy fixed alias only after the new random-alias envelope commits.
            persistToken(token, createAlias())
            deleteKey(alias)
        }
        token
    }

    override suspend fun writeToken(token: String) = mutex.withLock {
        require(token.isNotBlank())
        val currentAlias = preferences.getString(KEY_ALIAS, null)?.takeIf(::isOwnedAlias)
        val alias = currentAlias?.takeIf { readKey(it) != null } ?: createAlias()
        persistToken(token, alias)
    }

    override suspend fun clearToken() = mutex.withLock {
        clearEnvelope(preferences.getString(KEY_ALIAS, null))
    }

    private fun persistToken(token: String, alias: String) {
        val key = readKey(alias) ?: generateKey(alias)
        val envelope = TokenCipher.encrypt(token, key)
        val committed = preferences.edit()
            .putString(KEY_ALIAS, alias)
            .putString(KEY_IV, Base64.encodeToString(envelope.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(envelope.ciphertext, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            deleteKey(alias)
            error("Secure session envelope could not be persisted")
        }
    }

    private fun clearEnvelope(alias: String?) {
        preferences.edit(commit = true) { clear() }
        alias?.takeIf(::isOwnedAlias)?.let(::deleteKey)
        if (isDefaultStorage) deleteKey(LEGACY_KEY_ALIAS)
    }

    private fun createAlias(): String {
        val randomSuffix = ByteArray(ALIAS_RANDOM_BYTES).also(SecureRandom()::nextBytes)
            .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
        return aliasPrefix + randomSuffix
    }

    private fun isOwnedAlias(alias: String): Boolean =
        alias.startsWith(aliasPrefix) || (isDefaultStorage && alias == LEGACY_KEY_ALIAS)

    private fun readKey(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun generateKey(alias: String): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }

    private fun deleteKey(alias: String) {
        runCatching {
            keyStore().apply {
                if (containsAlias(alias)) deleteEntry(alias)
            }
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_STORAGE_ID = "default"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val LEGACY_KEY_ALIAS = "multica_session_aes_v1"
        private const val KEY_ALIAS_PREFIX = "multica_session_aes_v2_"
        private const val PREFERENCES_NAME = "multica_secure_session"
        private const val KEY_ALIAS = "token_key_alias"
        private const val KEY_IV = "token_iv"
        private const val KEY_CIPHERTEXT = "token_ciphertext"
        private const val ALIAS_RANDOM_BYTES = 16
        private val STORAGE_ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,48}")
    }
}
