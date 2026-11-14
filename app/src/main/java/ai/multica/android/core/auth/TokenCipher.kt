package ai.multica.android.core.auth

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CipherEnvelope(val iv: ByteArray, val ciphertext: ByteArray)

object TokenCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    fun encrypt(token: String, key: SecretKey, random: SecureRandom = SecureRandom()): CipherEnvelope {
        require(token.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // AndroidKeyStore keys keep randomizedEncryptionRequired=true. The
        // provider must generate the encryption IV; caller-provided GCM IVs are
        // rejected for those keys. Decryption supplies the persisted IV below.
        cipher.init(Cipher.ENCRYPT_MODE, key, random)
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val iv = requireNotNull(cipher.iv).also { require(it.size == IV_BYTES) }
        return CipherEnvelope(iv, ciphertext)
    }

    fun decrypt(envelope: CipherEnvelope, key: SecretKey): String {
        require(envelope.iv.size == IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, envelope.iv))
        return cipher.doFinal(envelope.ciphertext).toString(Charsets.UTF_8)
    }
}
