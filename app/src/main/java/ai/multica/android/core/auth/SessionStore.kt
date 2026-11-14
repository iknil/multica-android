package ai.multica.android.core.auth

interface SessionStore {
    suspend fun readToken(): String?
    suspend fun writeToken(token: String)
    suspend fun clearToken()
}

class InMemorySessionStore(initialToken: String? = null) : SessionStore {
    private var token = initialToken

    override suspend fun readToken(): String? = token

    override suspend fun writeToken(token: String) {
        require(token.isNotBlank())
        this.token = token
    }

    override suspend fun clearToken() {
        token = null
    }
}
