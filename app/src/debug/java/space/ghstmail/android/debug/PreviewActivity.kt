package space.ghstmail.android.debug

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import space.ghstmail.android.api.AliasInfo
import space.ghstmail.android.api.AliasStatus
import space.ghstmail.android.api.ApiException
import space.ghstmail.android.api.FilterRule
import space.ghstmail.android.ui.filters.FiltersActions
import space.ghstmail.android.ui.filters.FiltersContent
import space.ghstmail.android.ui.filters.FiltersUiState
import space.ghstmail.android.ui.filters.looksLikeDomain
import space.ghstmail.android.ui.filters.normalizeDomain
import space.ghstmail.android.ui.home.HomeActions
import space.ghstmail.android.ui.home.HomeContent
import space.ghstmail.android.ui.home.HomeUiState
import space.ghstmail.android.ui.home.filterAliases
import space.ghstmail.android.ui.home.summarize
import space.ghstmail.android.ui.theme.GhstMailTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Debug-only: the home screen on sample data, no account and no network.
 * adb shell am start -n space.ghstmail.android/.debug.PreviewActivity --es state list
 * States: list (default), empty, loading, error, create (sheet open), filters, filters-empty.
 */
class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val name = intent.getStringExtra("state") ?: "list"
        val animate = Settings.Global.getFloat(
            contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
        if (name.startsWith("filters")) {
            setContent { GhstMailTheme { FiltersPreview(empty = name == "filters-empty") } }
            return
        }
        setContent {
            GhstMailTheme {
                val scope = rememberCoroutineScope()
                var all by remember { mutableStateOf(if (name == "list" || name == "create") sampleAliases() else emptyList()) }
                var state by remember { mutableStateOf(sampleState(name, all)) }
                var createOpen by remember { mutableStateOf(name == "create") }
                var autofill by remember { mutableStateOf(false) }

                fun show(next: List<AliasInfo>, base: HomeUiState = state) {
                    all = next
                    val rows = filterAliases(next, base.query, base.status)
                    state = base.copy(rows = rows, matching = rows.size, summary = summarize(next))
                }

                fun add(alias: AliasInfo) {
                    show(listOf(alias) + all, state.copy(freshId = alias.id))
                    scope.launch {
                        delay(4000)
                        state = state.copy(freshId = null)
                    }
                }

                HomeContent(
                    state = state,
                    autofillEnabled = autofill,
                    createOpen = createOpen,
                    animate = animate,
                    snackbarHostState = remember { SnackbarHostState() },
                    actions = HomeActions(
                        onGenerate = {
                            val id = "new-${all.size}"
                            add(AliasInfo(id, "k${all.size}f2q.727eg@ghstmail.space", null, true, 0, 0))
                        },
                        onCreate = { label, hours ->
                            createOpen = false
                            val slug = label.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "alias" }
                            add(
                                AliasInfo(
                                    "new-${all.size}", "$slug.x9m2d@ghstmail.space", label.ifBlank { null }, true, 0, 0,
                                    hours?.let { Instant.now().plusSeconds(it * 3600L).toString() },
                                )
                            )
                        },
                        onCreateSheetChange = { createOpen = it },
                        onQueryChange = { show(all, state.copy(query = it)) },
                        onStatusChange = { show(all, state.copy(status = it)) },
                        onSetActive = { alias, active ->
                            show(all.map { if (it.id == alias.id) it.copy(is_active = active) else it })
                        },
                        onBurn = { alias ->
                            state = state.copy(burningId = alias.id)
                            scope.launch {
                                delay(if (animate) 1270 else 0)
                                show(all.filterNot { it.id == alias.id }, state.copy(burningId = null))
                            }
                        },
                        onDismissError = { state = state.copy(error = null) },
                        onRetry = { show(sampleAliases(), HomeUiState(loading = false, forwardsTo = "you@example.com")) },
                        onEnableAutofill = { autofill = true },
                        onSignOut = { finish() },
                    ),
                )
            }
        }
    }

    @Composable
    private fun FiltersPreview(empty: Boolean) {
        var state by remember {
            mutableStateOf(FiltersUiState(loading = false, rules = if (empty) emptyList() else sampleRules()))
        }
        FiltersContent(
            state = state,
            actions = FiltersActions(
                onBack = { finish() },
                onDomainChange = { state = state.copy(domain = it, domainInvalid = false) },
                onActionChange = { state = state.copy(action = it) },
                onAdd = {
                    val domain = normalizeDomain(state.domain)
                    state = if (looksLikeDomain(domain)) {
                        val rule = FilterRule("new-${state.rules.size}", domain, state.action.wireValue)
                        state.copy(rules = listOf(rule) + state.rules, domain = "")
                    } else {
                        state.copy(domainInvalid = true)
                    }
                },
                onRemove = { rule -> state = state.copy(rules = state.rules.filterNot { it.id == rule.id }) },
            ),
        )
    }

    private fun sampleRules() = listOf(
        FilterRule("1", "spam.com", "block"),
        FilterRule("2", "newsletters.parcelbird.example", "block"),
        FilterRule("3", "stackforge.dev", "allow"),
        FilterRule("4", "a-very-long-marketing-subdomain.promotions-and-offers.example.com", "block"),
    )

    private fun sampleState(name: String, all: List<AliasInfo>): HomeUiState {
        val base = HomeUiState(loading = false, forwardsTo = "you@example.com", status = AliasStatus.All)
        return when (name) {
            "empty" -> base
            "loading" -> base.copy(loading = true)
            "error" -> base.copy(loadFailed = true, error = ApiException.Unreachable())
            else -> base.copy(rows = all, matching = all.size, summary = summarize(all))
        }
    }

    private fun sampleAliases() = listOf(
        AliasInfo("1", "parcelbird.k7f2q@ghstmail.space", "Parcelbird", true, 2, 2),
        AliasInfo("2", "stackforge.m4x8d@ghstmail.space", "Stackforge", true, 41, 38),
        AliasInfo(
            "3", "trial.x9m2d@ghstmail.space", "Free trial", true, 1, 1,
            Instant.now().plusSeconds(26 * 3600L).toString(),
        ),
        AliasInfo("4", "nightowl.p2r9c@ghstmail.space", "Nightowl", false, 6, 1),
        AliasInfo("5", "q8w2e.h3k9z@ghstmail.space", null, true, 0, 0),
        AliasInfo(
            "6", "a-very-long-newsletter-name.q8w2e@ghstmail.space",
            "A very long newsletter name that does not fit", true, 128, 120,
            Instant.now().minusSeconds(3600).toString(),
        ),
    )
}
