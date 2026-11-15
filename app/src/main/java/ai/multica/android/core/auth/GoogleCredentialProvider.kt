package ai.multica.android.core.auth

/** Obtains a short-lived Google ID token. Implementations must never persist or log the token. */
interface GoogleCredentialProvider {
    suspend fun getIdToken(serverClientId: String, nonce: String): String
    suspend fun clearCredentialState()
}

class GoogleCredentialException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { CANCELLED, UNAVAILABLE, PROTOCOL }
}
