package space.ghstmail.android.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import space.ghstmail.android.R
import space.ghstmail.android.api.ApiException

/** What to tell the user. The server's own reasons are already worded for them. */
fun ApiException.userMessage(context: Context): String = when (this) {
    is ApiException.Unreachable -> context.getString(R.string.error_unreachable)
    is ApiException.SessionExpired -> context.getString(R.string.error_session_expired)
    is ApiException.Server -> reason ?: context.getString(R.string.error_generic)
}

@Composable
fun ApiException.userMessage(): String = userMessage(LocalContext.current)

fun Throwable.userMessage(context: Context): String =
    (this as? ApiException ?: ApiException.Unreachable()).userMessage(context)
