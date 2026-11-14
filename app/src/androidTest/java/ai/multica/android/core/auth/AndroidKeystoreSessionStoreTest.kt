package ai.multica.android.core.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSessionStoreTest {
    @Test
    fun writesRecoversOverwritesAndClearsDeviceBoundToken() = runBlocking {
        val storageId = "instrumented_${android.os.Process.myPid()}"
        val aliasMarker = "multica_session_aes_v2_${storageId}_"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AndroidKeystoreSessionStore(context, storageId)
        store.clearToken()

        try {
            store.writeToken("instrumentation-token")
            assertEquals("instrumentation-token", AndroidKeystoreSessionStore(context, storageId).readToken())
            val firstAliases = aliasesContaining(aliasMarker)
            assertEquals(1, firstAliases.size)

            store.writeToken("instrumentation-token")
            assertEquals("instrumentation-token", AndroidKeystoreSessionStore(context, storageId).readToken())
            assertEquals(firstAliases, aliasesContaining(aliasMarker))

            store.clearToken()
            assertNull(AndroidKeystoreSessionStore(context, storageId).readToken())
            assertTrue(aliasesContaining(aliasMarker).isEmpty())
        } finally {
            store.clearToken()
        }
    }

    private fun aliasesContaining(marker: String): Set<String> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.aliases().toList().filter { it.contains(marker) }.toSet()
    }
}
