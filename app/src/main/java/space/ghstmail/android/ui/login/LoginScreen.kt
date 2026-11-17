package space.ghstmail.android.ui.login

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.ghstmail.android.R
import space.ghstmail.android.api.ApiException
import space.ghstmail.android.api.GhstMailApi
import space.ghstmail.android.auth.AuthManager
import space.ghstmail.android.auth.GoogleSignIn
import space.ghstmail.android.auth.GoogleSignInResult
import space.ghstmail.android.ui.components.GhstButton
import space.ghstmail.android.ui.components.GhstButtonStyle
import space.ghstmail.android.ui.components.GhstCard
import space.ghstmail.android.ui.components.GhstErrorBanner
import space.ghstmail.android.ui.components.GhstTextField
import space.ghstmail.android.ui.components.GhstTopBar
import space.ghstmail.android.ui.components.userMessage
import space.ghstmail.android.ui.theme.GhstColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LoginMode { SignIn, Register, Reset, ResetSent }

private const val MIN_PASSWORD = 8
private const val TAG = "GhstMailLogin"

/** [notice] is shown above the form, for example after a session expired. */
@Composable
fun LoginScreen(auth: AuthManager, notice: String? = null, onSuccess: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(LoginMode.SignIn) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    val busy = loading || googleLoading

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun switchTo(next: LoginMode) {
        mode = next
        error = null
    }

    fun submit() {
        if (busy) return
        val address = email.trim()
        error = when {
            mode == LoginMode.Reset && address.isEmpty() -> context.getString(R.string.login_error_email_required)
            mode == LoginMode.Reset -> null
            address.isEmpty() || password.isEmpty() -> context.getString(R.string.login_error_required)
            mode == LoginMode.Register && password.length < MIN_PASSWORD ->
                context.getString(R.string.login_error_password_short)
            else -> null
        }
        if (error != null) return
        focusManager.clearFocus()
        loading = true
        scope.launch {
            if (mode == LoginMode.Reset) {
                val result = withContext(Dispatchers.IO) { GhstMailApi.requestPasswordReset(address) }
                loading = false
                result.onSuccess { mode = LoginMode.ResetSent }
                    .onFailure { error = it.userMessage(context) }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                if (mode == LoginMode.Register) GhstMailApi.register(address, password)
                else GhstMailApi.login(address, password)
            }
            loading = false
            result.onSuccess {
                auth.token = it.token
                auth.email = it.user.email
                onSuccess()
            }.onFailure {
                error = it.userMessage(context)
            }
        }
    }

    fun continueWithGoogle() {
        if (busy) return
        focusManager.clearFocus()
        googleLoading = true
        error = null
        scope.launch {
            when (val google = GoogleSignIn.requestIdToken(context)) {
                is GoogleSignInResult.Token -> {
                    val result = withContext(Dispatchers.IO) {
                        GhstMailApi.signInWithGoogle(google.idToken, google.nonce)
                    }
                    result.onSuccess {
                        auth.token = it.token
                        auth.email = it.email ?: google.email
                        onSuccess()
                    }.onFailure {
                        error = googleRefusal(context, it)
                    }
                }
                GoogleSignInResult.Cancelled -> Unit
                GoogleSignInResult.Unavailable ->
                    error = context.getString(R.string.login_google_unavailable)
                is GoogleSignInResult.Failed -> {
                    Log.w(TAG, "Google sign-in failed: ${google.detail}")
                    error = context.getString(R.string.login_google_failed)
                }
            }
            googleLoading = false
        }
    }

    BackHandler(enabled = mode != LoginMode.SignIn && !busy) { switchTo(LoginMode.SignIn) }

    Scaffold(
        topBar = { GhstTopBar() },
        containerColor = GhstColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    when (mode) {
                        LoginMode.SignIn -> R.string.login_title_sign_in
                        LoginMode.Register -> R.string.login_title_register
                        LoginMode.Reset -> R.string.login_title_reset
                        LoginMode.ResetSent -> R.string.login_title_reset_sent
                    }
                ),
                style = MaterialTheme.typography.headlineLarge,
                color = GhstColors.Text,
                textAlign = TextAlign.Center,
            )
            val subtitle = when (mode) {
                LoginMode.Reset -> stringResource(R.string.login_subtitle_reset)
                LoginMode.ResetSent -> stringResource(R.string.login_subtitle_reset_sent, email.trim())
                else -> null
            }
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhstColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 440.dp),
                )
            }

            if (mode == LoginMode.ResetSent) {
                Spacer(Modifier.height(28.dp))
                LinkRow(
                    prompt = stringResource(R.string.login_reset_sent_hint),
                    action = stringResource(R.string.login_reset_sent_retry),
                    onClick = { switchTo(LoginMode.Reset) },
                )
            } else {
                Spacer(Modifier.height(28.dp))
                GhstCard(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                    if (mode != LoginMode.Reset) {
                        GhstButton(
                            text = stringResource(R.string.login_google),
                            onClick = { continueWithGoogle() },
                            style = GhstButtonStyle.Secondary,
                            enabled = !loading,
                            loading = googleLoading,
                            icon = painterResource(R.drawable.ic_google),
                            tintIcon = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.login_google_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = GhstColors.TextDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HorizontalDivider(Modifier.weight(1f), color = GhstColors.Border)
                            Text(
                                text = stringResource(R.string.login_or_email),
                                style = MaterialTheme.typography.bodySmall,
                                color = GhstColors.TextDim,
                            )
                            HorizontalDivider(Modifier.weight(1f), color = GhstColors.Border)
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    val banner = error ?: notice?.takeIf { mode == LoginMode.SignIn }
                    if (banner != null) {
                        GhstErrorBanner(message = banner)
                        Spacer(Modifier.height(16.dp))
                    }

                    GhstTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = stringResource(R.string.login_email_label),
                        placeholder = stringResource(R.string.login_email_placeholder),
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (mode == LoginMode.Reset) ImeAction.Done else ImeAction.Next,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { submit() },
                        ),
                    )
                    if (mode != LoginMode.Reset) {
                        Spacer(Modifier.height(16.dp))
                        GhstTextField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            label = stringResource(R.string.login_password_label),
                            placeholder = stringResource(
                                if (mode == LoginMode.Register) R.string.login_password_placeholder_register
                                else R.string.login_password_placeholder_sign_in
                            ),
                            enabled = !busy,
                            visualTransformation = if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff
                                        else Icons.Outlined.Visibility,
                                        contentDescription = stringResource(
                                            if (passwordVisible) R.string.login_hide_password
                                            else R.string.login_show_password
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    GhstButton(
                        text = stringResource(
                            when (mode) {
                                LoginMode.Register -> R.string.login_action_register
                                LoginMode.Reset -> R.string.login_action_reset
                                else -> R.string.login_action_sign_in
                            }
                        ),
                        onClick = { submit() },
                        enabled = !googleLoading,
                        loading = loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mode == LoginMode.SignIn) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.login_forgot),
                            style = MaterialTheme.typography.bodySmall,
                            color = GhstColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable(enabled = !busy, role = Role.Button) {
                                    switchTo(LoginMode.Reset)
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            when (mode) {
                LoginMode.SignIn -> LinkRow(
                    prompt = stringResource(R.string.login_switch_to_register_prompt),
                    action = stringResource(R.string.login_switch_to_register_action),
                    enabled = !busy,
                    onClick = { switchTo(LoginMode.Register) },
                )
                LoginMode.Register -> LinkRow(
                    prompt = stringResource(R.string.login_switch_to_sign_in_prompt),
                    action = stringResource(R.string.login_switch_to_sign_in_action),
                    enabled = !busy,
                    onClick = { switchTo(LoginMode.SignIn) },
                )
                LoginMode.Reset, LoginMode.ResetSent -> LinkRow(
                    prompt = stringResource(R.string.login_remembered_prompt),
                    action = stringResource(R.string.login_switch_to_sign_in_action),
                    enabled = !busy,
                    onClick = { switchTo(LoginMode.SignIn) },
                )
            }
        }
    }
}

@Composable
private fun LinkRow(prompt: String, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = GhstColors.TextMuted,
        )
        Text(
            text = action,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = GhstColors.Amber,
        )
    }
}

/** The server's sign-in codes a person can do something about, worded like the website. */
private fun googleRefusal(context: Context, failure: Throwable): String {
    if (failure !is ApiException.Server) return failure.userMessage(context)
    val code = failure.reason.orEmpty().lowercase().replace('_', ' ')
    return context.getString(
        when {
            "not linked" in code -> R.string.login_google_not_linked
            "email not found" in code -> R.string.login_google_no_email
            else -> R.string.login_google_refused
        }
    )
}
