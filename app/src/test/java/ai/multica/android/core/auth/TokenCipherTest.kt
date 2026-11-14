package ai.multica.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.KeyGenerator

class TokenCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun roundTripsWithoutPlacingTokenInCiphertext() {
        val envelope = TokenCipher.encrypt("fixture:standard", key)
        assertFalse(envelope.ciphertext.toString(Charsets.UTF_8).contains("fixture:standard"))
        assertEquals("fixture:standard", TokenCipher.decrypt(envelope, key))
    }

    @Test
    fun rejectsModifiedCiphertext() {
        val envelope = TokenCipher.encrypt("fixture:standard", key)
        envelope.ciphertext[0] = (envelope.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { TokenCipher.decrypt(envelope, key) }
    }
}
