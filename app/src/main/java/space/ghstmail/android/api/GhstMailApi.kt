package space.ghstmail.android.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
data class AliasInfo(
    val id: String,
    val address: String,
    val label: String? = null,
    val is_active: Boolean = true,
    val emails_received: Int = 0,
    val emails_forwarded: Int = 0
)

@Serializable
data class AliasResponse(val alias: AliasInfo)

@Serializable
data class AliasListResponse(val aliases: List<AliasInfo>)

@Serializable
data class ErrorResponse(val error: String)

object GhstMailApi {

    private const val BASE_URL = "https://api.ghstmail.space"
    private val json = Json { ignoreUnknownKeys = true }
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

        return executeRequest(request) { responseBody ->
            json.decodeFromString(LoginResponse.serializer(), responseBody)
        }
    }

    fun register(email: String, password: String): Result<LoginResponse> {
        val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password))
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/register")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { responseBody ->
            json.decodeFromString(LoginResponse.serializer(), responseBody)
        }
    }

    fun createAlias(token: String, label: String? = null): Result<AliasResponse> {
        val body = json.encodeToString(
            CreateAliasRequest.serializer(),
            CreateAliasRequest(label = label)
        )
        val request = Request.Builder()
            .url("$BASE_URL/api/aliases")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { responseBody ->
            json.decodeFromString(AliasResponse.serializer(), responseBody)
        }
    }

    fun getAliases(token: String): Result<AliasListResponse> {
        val request = Request.Builder()
            .url("$BASE_URL/api/aliases")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return executeRequest(request) { responseBody ->
            json.decodeFromString(AliasListResponse.serializer(), responseBody)
        }
    }

    private fun <T> executeRequest(request: Request, parser: (String) -> T): Result<T> {
        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(parser(responseBody))
            } else {
                val errorMsg = try {
                    json.decodeFromString(ErrorResponse.serializer(), responseBody).error
                } catch (_: Exception) {
                    "Request failed (${response.code})"
                }
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
