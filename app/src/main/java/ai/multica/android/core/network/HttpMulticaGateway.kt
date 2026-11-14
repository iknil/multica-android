package ai.multica.android.core.network

import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.User
import ai.multica.android.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/** Minimal read-only P0 transport matching the verified server/mobile contract. */
class HttpMulticaGateway(
    baseUrl: String,
    private val metadata: ClientMetadata,
) : MulticaGateway {
    private val root = URI(baseUrl.trimEnd('/'))

    override suspend fun sendCode(email: String) {
        request(ApiRoutes.SEND_CODE, "POST", body = JSONObject().put("email", email))
    }

    override suspend fun verifyCode(email: String, code: String): AuthenticatedSession {
        val json = request(
            ApiRoutes.VERIFY_CODE,
            "POST",
            body = JSONObject().put("email", email).put("code", code),
        ).asObject()
        val token = json.requiredString("token")
        return AuthenticatedSession(token, json.requiredObject("user").toUser())
    }

    override suspend fun getMe(token: String): User =
        request(ApiRoutes.ME, token = token).asObject().toUser()

    override suspend fun getWorkspaces(token: String): List<Workspace> =
        request(ApiRoutes.WORKSPACES, token = token).asArray().mapObjects { value ->
            Workspace(
                id = value.requiredString("id"),
                name = value.optString("name"),
                slug = value.requiredString("slug"),
                issuePrefix = value.optString("issue_prefix"),
            )
        }

    override suspend fun getIssues(token: String, workspaceSlug: String): List<IssueSummary> {
        val response = request(ApiRoutes.ISSUES, token = token, workspaceSlug = workspaceSlug).asObject()
        return response.requiredArray("issues").mapObjects { value ->
            IssueSummary(
                id = value.requiredString("id"),
                identifier = value.requiredString("identifier"),
                title = value.requiredString("title"),
                status = value.requiredString("status"),
                priority = value.requiredString("priority"),
            )
        }
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        workspaceSlug: String? = null,
        body: JSONObject? = null,
    ): JsonPayload = withContext(Dispatchers.IO) {
        val connection = (URL(root.resolve(path).toString()).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = (ApiContract.TIMEOUT_SECONDS * 1_000).toInt()
            connection.readTimeout = (ApiContract.TIMEOUT_SECONDS * 1_000).toInt()
            ApiContract.headers(metadata, UUID.randomUUID().toString(), token, workspaceSlug)
                .forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            when {
                status == HttpURLConnection.HTTP_UNAUTHORIZED ->
                    throw GatewayException(GatewayException.Kind.UNAUTHORIZED, "Session expired")
                status !in 200..299 ->
                    throw GatewayException(GatewayException.Kind.SERVER, "Service returned HTTP $status")
                text.isBlank() -> JsonPayload.Empty
                text.trimStart().startsWith("[") -> JsonPayload.ArrayPayload(JSONArray(text))
                else -> JsonPayload.ObjectPayload(JSONObject(text))
            }
        } catch (error: GatewayException) {
            throw error
        } catch (error: JSONException) {
            throw GatewayException(GatewayException.Kind.PROTOCOL, "Service response did not match the contract", error)
        } catch (error: IOException) {
            throw GatewayException(GatewayException.Kind.NETWORK, "Service is unavailable", error)
        } finally {
            connection.disconnect()
        }
    }
}

private sealed interface JsonPayload {
    data object Empty : JsonPayload
    data class ObjectPayload(val value: JSONObject) : JsonPayload
    data class ArrayPayload(val value: JSONArray) : JsonPayload

    fun asObject(): JSONObject = (this as? ObjectPayload)?.value
        ?: throw GatewayException(GatewayException.Kind.PROTOCOL, "Expected an object response")

    fun asArray(): JSONArray = (this as? ArrayPayload)?.value
        ?: throw GatewayException(GatewayException.Kind.PROTOCOL, "Expected an array response")
}

private fun JSONObject.requiredString(name: String): String = optString(name).takeIf(String::isNotBlank)
    ?: throw GatewayException(GatewayException.Kind.PROTOCOL, "Missing response field: $name")

private fun JSONObject.requiredObject(name: String): JSONObject = optJSONObject(name)
    ?: throw GatewayException(GatewayException.Kind.PROTOCOL, "Missing response object: $name")

private fun JSONObject.requiredArray(name: String): JSONArray = optJSONArray(name)
    ?: throw GatewayException(GatewayException.Kind.PROTOCOL, "Missing response array: $name")

private fun JSONObject.toUser() = User(
    id = requiredString("id"),
    name = optString("name"),
    email = requiredString("email"),
)

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { index ->
        val value = optJSONObject(index)
            ?: throw GatewayException(GatewayException.Kind.PROTOCOL, "Expected object at index $index")
        transform(value)
    }
