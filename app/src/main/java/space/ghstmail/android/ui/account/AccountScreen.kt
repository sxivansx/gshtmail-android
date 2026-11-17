package space.ghstmail.android.ui.account

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.ghstmail.android.R
import space.ghstmail.android.ui.components.GhstBackBar
import space.ghstmail.android.ui.components.GhstButton
import space.ghstmail.android.ui.components.GhstButtonStyle
import space.ghstmail.android.ui.components.GhstCard
import space.ghstmail.android.ui.components.GhstDialog
import space.ghstmail.android.ui.theme.AddressStyle
import space.ghstmail.android.ui.theme.GhstColors

private const val PRIVACY_URL = "https://www.ghstmail.space/privacy"
private const val TERMS_URL = "https://www.ghstmail.space/terms"
private const val SUPPORT_EMAIL = "support@ghstmail.space"

class AccountActions(
    val onBack: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenTerms: () -> Unit = {},
    val onAskToDelete: () -> Unit = {},
)

@Composable
fun AccountScreen(email: String?, onBack: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    val actions = remember(context, email, onBack, onSignOut) {
        AccountActions(
            onBack = onBack,
            onSignOut = onSignOut,
            onOpenPrivacy = { openLink(context, PRIVACY_URL) },
            onOpenTerms = { openLink(context, TERMS_URL) },
            onAskToDelete = { writeToSupport(context, email) },
        )
    }

    AccountContent(email = email, actions = actions)
}

@Composable
fun AccountContent(email: String?, actions: AccountActions) {
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { GhstBackBar(title = stringResource(R.string.account_title), onBack = actions.onBack) },
        containerColor = GhstColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        ) {
            GhstCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.account_signed_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = GhstColors.TextMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = email ?: stringResource(R.string.account_email_unknown),
                    style = AddressStyle,
                    color = GhstColors.Forward,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.account_forwarding_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhstColors.TextMuted,
                )
                Spacer(Modifier.height(16.dp))
                GhstButton(
                    text = stringResource(R.string.home_sign_out),
                    onClick = { confirmSignOut = true },
                    style = GhstButtonStyle.Secondary,
                    compact = true,
                )
            }

            Spacer(Modifier.height(12.dp))
            GhstCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                LinkRow(text = stringResource(R.string.account_privacy), onClick = actions.onOpenPrivacy)
                HorizontalDivider(color = GhstColors.Border)
                LinkRow(text = stringResource(R.string.account_terms), onClick = actions.onOpenTerms)
            }

            Spacer(Modifier.height(12.dp))
            GhstCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.account_delete_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = GhstColors.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (email != null) {
                        stringResource(R.string.account_delete_body, SUPPORT_EMAIL, email)
                    } else {
                        stringResource(R.string.account_delete_body_no_email, SUPPORT_EMAIL)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhstColors.TextMuted,
                )
                Spacer(Modifier.height(16.dp))
                GhstButton(
                    text = stringResource(R.string.account_delete_action),
                    onClick = actions.onAskToDelete,
                    style = GhstButtonStyle.Secondary,
                    compact = true,
                )
            }
        }
    }

    if (confirmSignOut) {
        GhstDialog(
            title = stringResource(R.string.home_sign_out_title),
            onDismiss = { confirmSignOut = false },
            confirm = {
                GhstButton(
                    text = stringResource(R.string.home_sign_out),
                    onClick = {
                        confirmSignOut = false
                        actions.onSignOut()
                    },
                    compact = true,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.home_sign_out_body),
                style = MaterialTheme.typography.bodyMedium,
                color = GhstColors.TextMuted,
            )
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = GhstColors.Text,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = GhstColors.TextMuted,
        )
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}

/** Opens the user's mail app on a deletion request; the server team acts on the email. */
private fun writeToSupport(context: Context, email: String?) {
    val body = if (email != null) {
        context.getString(R.string.account_delete_mail_body, email)
    } else {
        context.getString(R.string.account_delete_mail_body_no_email)
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.account_delete_mail_subject))
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.account_no_mail_app, SUPPORT_EMAIL),
            Toast.LENGTH_LONG,
        ).show()
    }
}
