package ai.multica.android.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Stores only AES-GCM ciphertext in private preferences; the non-exportable key
 * remains in AndroidKeyStore. Reads and writes are serialized so logout/401
 * cannot interleave with a completed login write.
 */
class AndroidKeystoreSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun readToken(): String? = mutex.withLock {
        val iv = preferences.getString(KEY_IV, null)
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
        if (iv == null || ciphertext == null) {
            if (iv != null || ciphertext != null) preferences.edit { clear() }
            return@withLock null
        }
        runCatching {
            TokenCipher.decrypt(
                CipherEnvelope(Base64.decode(iv, Base64.NO_WRAP), Base64.decode(ciphertext, Base64.NO_WRAP)),
                getOrCreateKey(),
            )
        }.getOrElse {
            // Covers a restored ciphertext without its device-bound key and key invalidation.
            preferences.edit { clear() }
            null
        }
    }

    override suspend fun writeToken(token: String) = mutex.withLock {
        val envelope = TokenCipher.encrypt(token, getOrCreateKey())
        preferences.edit(commit = true) {
            putString(KEY_IV, Base64.encodeToString(envelope.iv, Base64.NO_WRAP))
            putString(KEY_CIPHERTEXT, Base64.encodeToString(envelope.ciphertext, Base64.NO_WRAP))
        }
    }

    override suspend fun clearToken() = mutex.withLock {
        preferences.edit(commit = true) { clear() }
        Unit
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "multica_session_aes_v1"
        const val PREFERENCES_NAME = "multica_secure_session"
        const val KEY_IV = "token_iv"
        const val KEY_CIPHERTEXT = "token_ciphertext"
    }
}
