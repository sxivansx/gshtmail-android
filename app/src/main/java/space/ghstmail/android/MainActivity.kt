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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import space.ghstmail.android.auth.AuthManager
import androidx.lifecycle.viewmodel.compose.viewModel
import space.ghstmail.android.ui.account.AccountScreen
import space.ghstmail.android.ui.filters.FiltersScreen
import space.ghstmail.android.ui.home.HomeScreen
import space.ghstmail.android.ui.home.HomeViewModel
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

private enum class Screen { Home, Filters, Account }

@Composable
private fun GhstMailApp() {
    val context = LocalContext.current
    val auth = remember { AuthManager(context.applicationContext) }
    var isLoggedIn by remember { mutableStateOf(auth.isLoggedIn) }
    // Set when the server stopped taking the token, so the sign-in screen can say why.
    var sessionExpired by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    // Keeps the alias list where it was scrolled to while another screen is open.
    val screens = rememberSaveableStateHolder()
    val home: HomeViewModel = viewModel()

    fun signedOut(expired: Boolean) {
        home.reset()
        auth.logout()
        sessionExpired = expired
        screen = Screen.Home
        isLoggedIn = false
    }

    if (isLoggedIn) {
        screens.SaveableStateProvider(screen) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    onSessionExpired = { signedOut(expired = true) },
                    onOpenFilters = { screen = Screen.Filters },
                    onOpenAccount = { screen = Screen.Account },
                    viewModel = home,
                )

                Screen.Filters -> FiltersScreen(
                    onBack = { screen = Screen.Home },
                    onSessionExpired = { signedOut(expired = true) },
                )

                Screen.Account -> AccountScreen(
                    email = auth.email,
                    onBack = { screen = Screen.Home },
                    onSignOut = { signedOut(expired = false) },
                )
            }
        }
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
