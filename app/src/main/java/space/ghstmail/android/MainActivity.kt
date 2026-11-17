package space.ghstmail.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import space.ghstmail.android.auth.AuthManager
import space.ghstmail.android.ui.home.HomeScreen
import space.ghstmail.android.ui.login.LoginScreen
import space.ghstmail.android.ui.theme.GhstMailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            GhstMailTheme {
                GhstMailApp()
            }
        }
    }
}

@Composable
private fun GhstMailApp() {
    val context = LocalContext.current
    val auth = remember { AuthManager(context.applicationContext) }
    var isLoggedIn by remember { mutableStateOf(auth.isLoggedIn) }
    // Set when the server stopped taking the token, so the sign-in screen can say why.
    var sessionExpired by rememberSaveable { mutableStateOf(false) }

    if (isLoggedIn) {
        HomeScreen(
            onSignOut = {
                auth.logout()
                isLoggedIn = false
            },
            onSessionExpired = {
                auth.logout()
                sessionExpired = true
                isLoggedIn = false
            },
        )
    } else {
        LoginScreen(
            auth = auth,
            notice = if (sessionExpired) stringResource(R.string.error_session_expired) else null,
            onSuccess = {
                sessionExpired = false
                isLoggedIn = true
            },
        )
    }
}
