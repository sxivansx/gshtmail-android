package space.ghstmail.android.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom

sealed interface GoogleSignInResult {
    /** [email] is the Google account's address. */
    data class Token(val idToken: String, val nonce: String, val email: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object Unavailable : GoogleSignInResult
    data class Failed(val detail: String?) : GoogleSignInResult
}

object GoogleSignIn {

    /**
     * The backend's Google client, the same one the website signs in with. The ID token is
     * issued for it, so the backend accepts it. Not a secret: it is in the website's Google
     * redirect. The Android side also needs an "Android" OAuth client in the same Google
     * Cloud project, registered with this app's package name and signing certificate.
     */
    private const val SERVER_CLIENT_ID =
        "3653311620-i10t7dpgej2ubrg8eengfc8h87rq2fi8.apps.googleusercontent.com"

    /** Shows Google's account sheet. [activityContext] must be an Activity. */
    suspend fun requestIdToken(activityContext: Context): GoogleSignInResult {
        val nonce = newNonce()
        val option = GetSignInWithGoogleOption.Builder(SERVER_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Token(google.idToken, nonce, google.id)
            } else {
                GoogleSignInResult.Failed("Unexpected credential type ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            // Google also reports "[16] Account reauth failed" as a cancellation. That one is
            // not the user backing out: it is what comes back when the Android OAuth client
            // for this package and signing certificate is missing in Google Cloud.
            if (e.message.orEmpty().contains("reauth", ignoreCase = true)) {
                GoogleSignInResult.Failed(e.message)
            } else {
                GoogleSignInResult.Cancelled
            }
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed(e.message)
        } catch (e: GetCredentialException) {
            // No Google Play services, no Google account, or the Android OAuth client is missing.
            if (e.type.contains("NO_CREDENTIAL") || e.type.contains("PROVIDER_CONFIGURATION")) {
                GoogleSignInResult.Unavailable
            } else {
                GoogleSignInResult.Failed("${e.type}: ${e.message}")
            }
        }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
