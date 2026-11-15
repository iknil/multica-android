package ai.multica.android.core.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Credential Manager adapter. The returned ID token remains in memory only. */
class AndroidGoogleCredentialProvider(
    context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context),
) : GoogleCredentialProvider {
    private val requestContext = context

    override suspend fun getIdToken(serverClientId: String, nonce: String): String {
        if (serverClientId.isBlank() || nonce.isBlank()) {
            throw GoogleCredentialException(
                GoogleCredentialException.Kind.PROTOCOL,
                "Google sign-in is not configured for this build.",
            )
        }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setNonce(nonce)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val credential = try {
            credentialManager.getCredential(requestContext, request).credential
        } catch (error: GetCredentialCancellationException) {
            throw GoogleCredentialException(
                GoogleCredentialException.Kind.CANCELLED,
                "Google sign-in was cancelled.",
                error,
            )
        } catch (error: GetCredentialException) {
            throw GoogleCredentialException(
                GoogleCredentialException.Kind.UNAVAILABLE,
                "Google sign-in is unavailable on this device. Check the credential provider and network.",
                error,
            )
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw GoogleCredentialException(
                GoogleCredentialException.Kind.PROTOCOL,
                "The credential provider returned an unsupported credential.",
            )
        }
        return runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }
            .getOrElse { error ->
                throw GoogleCredentialException(
                    GoogleCredentialException.Kind.PROTOCOL,
                    "The Google credential response could not be read.",
                    error,
                )
            }
            .takeIf(String::isNotBlank)
            ?: throw GoogleCredentialException(
                GoogleCredentialException.Kind.PROTOCOL,
                "The Google credential response did not contain an ID token.",
            )
    }

    override suspend fun clearCredentialState() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
