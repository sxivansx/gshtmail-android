package com.pvot.ghstmail

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pvot.ghstmail.api.AliasInfo
import com.pvot.ghstmail.api.GhstMailApi
import com.pvot.ghstmail.auth.AuthManager
import com.pvot.ghstmail.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GhstMailTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val auth = remember { AuthManager(context) }
    var isLoggedIn by remember { mutableStateOf(auth.isLoggedIn) }

    if (isLoggedIn) {
        DashboardScreen(auth) { auth.logout(); isLoggedIn = false }
    } else {
        LoginScreen(auth) { isLoggedIn = true }
    }
}

@Composable
fun LoginScreen(auth: AuthManager, onSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var isRegister by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ghost icon
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Amber
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "GhstMail",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            "Your email. Invisible.",
            fontSize = 14.sp,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        AnimatedVisibility(visible = error != null) {
            Text(
                error ?: "",
                color = Error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Email and password required"
                    return@Button
                }
                loading = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        if (isRegister) GhstMailApi.register(email.trim(), password)
                        else GhstMailApi.login(email.trim(), password)
                    }
                    loading = false
                    result.onSuccess {
                        auth.token = it.token
                        auth.email = it.user.email
                        onSuccess()
                    }.onFailure {
                        error = it.message ?: "Login failed"
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Background
                )
            } else {
                Text(
                    if (isRegister) "Create Account" else "Sign In",
                    fontWeight = FontWeight.Bold,
                    color = Background
                )
            }
        }

        TextButton(onClick = { isRegister = !isRegister; error = null }) {
            Text(
                if (isRegister) "Already have an account? Sign in"
                else "Don't have an account? Register",
                color = Amber,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun DashboardScreen(auth: AuthManager, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var aliases by remember { mutableStateOf<List<AliasInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var autofillEnabled by remember { mutableStateOf(false) }

    // Re-check autofill status on every resume (after returning from Settings)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val afm = context.getSystemService(AutofillManager::class.java)
                autofillEnabled = afm?.hasEnabledAutofillServices() == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load aliases
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            GhstMailApi.getAliases(auth.token!!)
        }
        result.onSuccess { aliases = it.aliases }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "GhstMail",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        auth.email ?: "",
                        fontSize = 13.sp,
                        color = Muted
                    )
                }
                TextButton(onClick = onLogout) {
                    Text("Sign out", color = Muted, fontSize = 13.sp)
                }
            }
        }

        // Autofill status card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (autofillEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (autofillEnabled) Success else Amber,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (autofillEnabled) "Autofill Active" else "Autofill Not Enabled",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (autofillEnabled) "GhstMail will suggest aliases in email fields"
                            else "Enable GhstMail as your autofill provider",
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                    if (!autofillEnabled) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Enable", color = Background, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Aliases", aliases.size.toString(), Modifier.weight(1f))
                StatCard("Active", aliases.count { it.is_active }.toString(), Modifier.weight(1f))
                StatCard("Received", aliases.sumOf { it.emails_received }.toString(), Modifier.weight(1f))
            }
        }

        // Section label
        item {
            Text(
                "Recent Aliases",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
                }
            }
        } else if (aliases.isEmpty()) {
            item {
                Text(
                    "No aliases yet.\nUse any app's email field — GhstMail will offer to fill it.",
                    color = Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
        } else {
            items(aliases.take(10)) { alias ->
                AliasRow(alias)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Amber)
            Text(label, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun AliasRow(alias: AliasInfo) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (alias.is_active) Success else Error)
            )
            Column(modifier = Modifier.weight(1f)) {
                if (!alias.label.isNullOrBlank()) {
                    Text(
                        alias.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    alias.address,
                    fontSize = 12.sp,
                    color = Muted,
                    fontWeight = FontWeight.Normal
                )
            }
            Text(
                "${alias.emails_received} rcvd",
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}
