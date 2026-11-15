package ai.multica.android.core.network

import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.GoogleAuthChallenge
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.IssueDetail
import ai.multica.android.model.TimelineItem
import ai.multica.android.model.TimelineKind
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
import java.net.URLEncoder
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

    override suspend fun createGoogleAuthChallenge(): GoogleAuthChallenge {
        val json = request(
            ApiRoutes.GOOGLE_NATIVE_CHALLENGE,
            "POST",
            body = JSONObject(),
        ).asObject()
        return GoogleAuthChallenge(
            attemptId = json.requiredString("attempt_id"),
            nonce = json.requiredString("nonce"),
        )
    }

    override suspend fun exchangeGoogleIdToken(
        attemptId: String,
        idToken: String,
    ): AuthenticatedSession {
        val json = request(
            ApiRoutes.GOOGLE_NATIVE,
            "POST",
            body = JSONObject()
                .put("attempt_id", attemptId)
                .put("id_token", idToken),
        ).asObject()
        return AuthenticatedSession(
            token = json.requiredString("token"),
            user = json.requiredObject("user").toUser(),
        )
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

    override suspend fun getIssue(token: String, workspaceSlug: String, issueId: String): IssueDetail {
        val value = request(
            ApiRoutes.issue(issueId.pathSegment()),
            token = token,
            workspaceSlug = workspaceSlug,
        ).asObject()
        return value.toIssueDetail()
    }

    override suspend fun getTimeline(
        token: String,
        workspaceSlug: String,
        issueId: String,
    ): List<TimelineItem> = request(
        // The verified endpoint deliberately returns the complete oldest-first
        // array. UI pagination is local and no legacy cursor params are sent.
        ApiRoutes.timeline(issueId.pathSegment()),
        token = token,
        workspaceSlug = workspaceSlug,
    ).asArray().mapObjects(JSONObject::toTimelineItem)

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

private fun JSONObject.toIssueDetail() = IssueDetail(
    id = requiredString("id"),
    identifier = requiredString("identifier"),
    title = requiredString("title"),
    description = if (isNull("description")) null else optString("description"),
    status = requiredString("status"),
    priority = requiredString("priority"),
    createdAt = requiredString("created_at"),
    updatedAt = requiredString("updated_at"),
)

private fun JSONObject.toTimelineItem(): TimelineItem {
    val kind = if (optString("type") == "comment") TimelineKind.COMMENT else TimelineKind.ACTIVITY
    val deleted = !isNull("deleted_at") && has("deleted_at")
    val body = when {
        deleted -> "Deleted comment"
        kind == TimelineKind.COMMENT -> optString("content").ifBlank { "Comment without text" }
        else -> optString("action").ifBlank { "updated this task" }
    }
    return TimelineItem(
        id = requiredString("id"),
        kind = kind,
        actorName = optString("actor_name").ifBlank {
            optString("actor_id").ifBlank { optString("actor_type", "Unknown actor") }
        },
        createdAt = requiredString("created_at"),
        body = body,
        parentId = if (isNull("parent_id")) null else optString("parent_id").ifBlank { null },
        deleted = deleted,
    )
}

private fun String.pathSegment(): String = URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { index ->
        val value = optJSONObject(index)
            ?: throw GatewayException(GatewayException.Kind.PROTOCOL, "Expected object at index $index")
        transform(value)
    }
