package space.ghstmail.android.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val user: UserInfo, val token: String)

@Serializable
data class UserInfo(val id: String, val email: String)

@Serializable
data class CreateAliasRequest(
    val label: String? = null,
    val expires_in_hours: Int? = null
)

@Serializable
data class UpdateAliasRequest(val is_active: Boolean)

@Serializable
data class PasswordResetRequest(val email: String, val redirectTo: String)

@Serializable
data class IdTokenBody(val token: String, val nonce: String? = null)

@Serializable
data class SocialSignInRequest(val provider: String, val idToken: IdTokenBody)

@Serializable
data class SocialUser(val email: String? = null)

@Serializable
data class SocialSignInResponse(val user: SocialUser? = null)

@Serializable
data class DashboardTokenResponse(val token: String, val user: SocialUser? = null)

/** What signing in gives the app: the token every /api route takes, and whose it is. */
data class Session(val token: String, val email: String?)

@Serializable
data class AliasInfo(
    val id: String,
    val address: String,
    val label: String? = null,
    val is_active: Boolean = true,
    val emails_received: Int = 0,
    val emails_forwarded: Int = 0,
    val expires_at: String? = null
)

@Serializable
data class AliasResponse(val alias: AliasInfo)

@Serializable
data class AliasSummary(
    val total: Int = 0,
    val active: Int = 0,
    val received: Int = 0,
    val forwarded: Int = 0
)

/**
 * A server that understands paging answers with one page, [total] matches and account-wide
 * [summary]. An older one ignores the query and sends every alias, with neither.
 */
@Serializable
data class AliasListResponse(
    val aliases: List<AliasInfo> = emptyList(),
    val total: Int? = null,
    val offset: Int = 0,
    val summary: AliasSummary? = null
) {
    val isPaged: Boolean get() = total != null && summary != null
}

@Serializable
data class ErrorResponse(val error: String? = null, val message: String? = null)

enum class AliasStatus(val queryValue: String?) {
    All(null),
    Active("active"),
    Paused("paused")
}

sealed class ApiException(message: String) : IOException(message) {
    /** No answer, or one that was not JSON (a gateway error page). */
    class Unreachable : ApiException("Could not reach GhstMail")

    /** The token was refused. It lasts 7 days. */
    class SessionExpired : ApiException("Session expired")

    /** The server said no and gave a reason, already worded for the user. */
    class Server(val code: Int, val reason: String?) : ApiException(reason ?: "Request failed ($code)")
}

object GhstMailApi {

    private const val BASE_URL = "https://api.ghstmail.space"
    private const val RESET_REDIRECT = "https://www.ghstmail.space/reset-password"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val jsonMediaType = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun login(email: String, password: String): Result<LoginResponse> {
        val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password))
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/login")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return execute(request, authenticated = false) {
            json.decodeFromString(LoginResponse.serializer(), it)
        }
    }

    fun register(email: String, password: String): Result<LoginResponse> {
        val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password))
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/register")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return execute(request, authenticated = false) {
            json.decodeFromString(LoginResponse.serializer(), it)
        }
    }

    /** Emails a link that opens the website's reset page. Answers the same whether or not the account exists. */
    fun requestPasswordReset(email: String): Result<Unit> {
        val body = json.encodeToString(
            PasswordResetRequest.serializer(),
            PasswordResetRequest(email, RESET_REDIRECT)
        )
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/request-password-reset")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return execute(request, authenticated = false) { }
    }

    /**
     * Signs in with a Google ID token. Better Auth checks the token and answers with its
     * session cookie, which is then traded for the token every /api route uses, the same
     * two steps the website takes after its Google redirect.
     */
    fun signInWithGoogle(idToken: String, nonce: String?): Result<Session> {
        val body = json.encodeToString(
            SocialSignInRequest.serializer(),
            SocialSignInRequest("google", IdTokenBody(idToken, nonce))
        )
        val signIn = Request.Builder()
            .url("$BASE_URL/api/auth/sign-in/social")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        val first = send(signIn) ?: return Result.failure(ApiException.Unreachable())
        if (first.code !in 200..299) {
            return Result.failure(ApiException.Server(first.code, reasonIn(first.body)))
        }
        val cookie = first.cookies.joinToString("; ") { it.substringBefore(';') }
        if (cookie.isEmpty()) return Result.failure(ApiException.Server(first.code, null))
        val email = try {
            json.decodeFromString(SocialSignInResponse.serializer(), first.body).user?.email
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        val exchange = Request.Builder()
            .url("$BASE_URL/api/auth/dashboard-token")
            .header("Cookie", cookie)
            .get()
            .build()
        return execute(exchange, authenticated = false) {
            json.decodeFromString(DashboardTokenResponse.serializer(), it)
        }.map { Session(it.token, it.user?.email ?: email) }
    }

    fun createAlias(
        token: String,
        label: String? = null,
        expiresInHours: Int? = null
    ): Result<AliasResponse> {
        val body = json.encodeToString(
            CreateAliasRequest.serializer(),
            CreateAliasRequest(label = label, expires_in_hours = expiresInHours)
        )
        val request = Request.Builder()
            .url("$BASE_URL/api/aliases")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return execute(request) {
            json.decodeFromString(AliasResponse.serializer(), it)
        }
    }

    fun getAliases(
        token: String,
        limit: Int,
        offset: Int,
        query: String = "",
        status: AliasStatus = AliasStatus.All
    ): Result<AliasListResponse> {
        val url = "$BASE_URL/api/aliases".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query.trim())
                status.queryValue?.let { addQueryParameter("status", it) }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return execute(request) {
            json.decodeFromString(AliasListResponse.serializer(), it)
        }
    }

    fun setAliasActive(token: String, id: String, active: Boolean): Result<AliasResponse> {
        val body = json.encodeToString(UpdateAliasRequest.serializer(), UpdateAliasRequest(active))
        val request = Request.Builder()
            .url("$BASE_URL/api/aliases/$id")
            .header("Authorization", "Bearer $token")
            .patch(body.toRequestBody(jsonMediaType))
            .build()

        return execute(request) {
            json.decodeFromString(AliasResponse.serializer(), it)
        }
    }

    /** Burns the alias: it stops taking mail and cannot be brought back. */
    fun deleteAlias(token: String, id: String): Result<Unit> {
        val request = Request.Builder()
            .url("$BASE_URL/api/aliases/$id")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        return execute(request) { }
    }

    private class Answer(val code: Int, val body: String, val cookies: List<String>)

    /** Null when there was no answer at all. */
    private fun send(request: Request): Answer? = try {
        client.newCall(request).execute().use {
            Answer(it.code, it.body?.string() ?: "", it.headers("Set-Cookie"))
        }
    } catch (_: IOException) {
        null
    }

    // The /api routes send { error }, Better Auth's /api/auth routes send { message }.
    private fun reasonIn(body: String): String? = try {
        json.decodeFromString(ErrorResponse.serializer(), body).let { it.error ?: it.message }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * [authenticated] is false for the sign-in forms: no token was sent, so a 401 there
     * means wrong credentials, not an expired session.
     */
    private fun <T> execute(
        request: Request,
        authenticated: Boolean = true,
        parser: (String) -> T
    ): Result<T> {
        val answer = send(request) ?: return Result.failure(ApiException.Unreachable())

        if (answer.code == 401 && authenticated) {
            return Result.failure(ApiException.SessionExpired())
        }
        if (answer.code !in 200..299) {
            return Result.failure(ApiException.Server(answer.code, reasonIn(answer.body)))
        }
        return try {
            Result.success(parser(answer.body))
        } catch (_: SerializationException) {
            // A gateway error page is HTML, so parsing it throws too.
            Result.failure(ApiException.Unreachable())
        } catch (_: IllegalArgumentException) {
            Result.failure(ApiException.Unreachable())
        }
    }
}
