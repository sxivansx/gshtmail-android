package space.ghstmail.android.ui.filters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import space.ghstmail.android.R
import space.ghstmail.android.api.FilterAction
import space.ghstmail.android.api.FilterRule
import space.ghstmail.android.ui.components.GhstBackBar
import space.ghstmail.android.ui.components.GhstButton
import space.ghstmail.android.ui.components.GhstButtonStyle
import space.ghstmail.android.ui.components.GhstCard
import space.ghstmail.android.ui.components.GhstErrorBanner
import space.ghstmail.android.ui.components.GhstSegmented
import space.ghstmail.android.ui.components.GhstTextField
import space.ghstmail.android.ui.components.userMessage
import space.ghstmail.android.ui.theme.AddressStyle
import space.ghstmail.android.ui.theme.GhstColors

class FiltersActions(
    val onBack: () -> Unit = {},
    val onDomainChange: (String) -> Unit = {},
    val onActionChange: (FilterAction) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onRemove: (FilterRule) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
fun FiltersScreen(
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: FiltersViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        viewModel.start()
        viewModel.events.collect { event ->
            when (event) {
                FiltersEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val actions = remember(viewModel, onBack) {
        FiltersActions(
            onBack = onBack,
            onDomainChange = viewModel::setDomain,
            onActionChange = viewModel::setAction,
            onAdd = viewModel::add,
            onRemove = viewModel::remove,
            onRetry = viewModel::retry,
            onDismissError = viewModel::dismissError,
        )
    }

    FiltersContent(state = state, actions = actions)
}

@Composable
fun FiltersContent(state: FiltersUiState, actions: FiltersActions) {
    Scaffold(
        topBar = { GhstBackBar(title = stringResource(R.string.filters_title), onBack = actions.onBack) },
        containerColor = GhstColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 20.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 20.dp,
                top = 20.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.filters_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhstColors.TextMuted,
                )
            }

            val error = state.error
            if (error != null) {
                item(key = "error") {
                    Spacer(Modifier.height(20.dp))
                    GhstErrorBanner(message = error.userMessage()) {
                        if (state.loadFailed) {
                            GhstButton(
                                text = stringResource(R.string.aliases_retry),
                                onClick = actions.onRetry,
                                style = GhstButtonStyle.Secondary,
                                compact = true,
                            )
                        } else {
                            IconButton(onClick = actions.onDismissError) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.home_dismiss),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "form") {
                Spacer(Modifier.height(20.dp))
                AddRuleCard(state = state, actions = actions)
            }

            when {
                state.loading -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = GhstColors.Amber,
                        )
                    }
                }

                state.rules.isEmpty() -> if (!state.loadFailed) {
                    item(key = "empty") {
                        Spacer(Modifier.height(12.dp))
                        EmptyRules()
                    }
                }

                else -> item(key = "rules") {
                    Spacer(Modifier.height(12.dp))
                    RulesCard(rules = state.rules, onRemove = actions.onRemove)
                }
            }
        }
    }
}

@Composable
private fun AddRuleCard(state: FiltersUiState, actions: FiltersActions) {
    val focus = LocalFocusManager.current
    val add = {
        focus.clearFocus()
        actions.onAdd()
    }
    GhstCard(modifier = Modifier.fillMaxWidth()) {
        GhstTextField(
            value = state.domain,
            onValueChange = actions.onDomainChange,
            placeholder = stringResource(R.string.filters_domain_placeholder),
            label = stringResource(R.string.filters_domain_label),
            enabled = !state.adding,
            isError = state.domainInvalid,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        if (state.domainInvalid) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.filters_domain_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = GhstColors.Burn,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.filters_action_label),
            style = MaterialTheme.typography.titleSmall,
            color = GhstColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        GhstSegmented(
            options = listOf(
                FilterAction.Block to stringResource(R.string.filters_action_block),
                FilterAction.Allow to stringResource(R.string.filters_action_allow),
            ),
            selected = state.action,
            onSelect = actions.onActionChange,
            modifier = Modifier.fillMaxWidth(),
            fill = true,
        )
        Spacer(Modifier.height(20.dp))
        GhstButton(
            text = stringResource(R.string.filters_add),
            onClick = add,
            enabled = state.domain.isNotBlank(),
            loading = state.adding,
            icon = rememberVectorPainter(Icons.Outlined.Add),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RulesCard(rules: List<FilterRule>, onRemove: (FilterRule) -> Unit) {
    GhstCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        rules.forEachIndexed { index, rule ->
            if (index > 0) HorizontalDivider(color = GhstColors.Border)
            RuleRow(rule = rule, onRemove = { onRemove(rule) })
        }
    }
}

@Composable
private fun RuleRow(rule: FilterRule, onRemove: () -> Unit) {
    val blocks = rule.action != FilterAction.Allow.wireValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            // The bin's ink ends 18dp inside its button: this lines it up with the card's 20dp inset.
            .padding(start = 20.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rule.domain,
            style = AddressStyle,
            color = GhstColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(if (blocks) R.string.filters_action_block else R.string.filters_action_allow),
            style = MaterialTheme.typography.labelMedium,
            color = if (blocks) GhstColors.Burn else GhstColors.TextMuted,
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.filters_delete_description, rule.domain),
                modifier = Modifier.size(20.dp),
                tint = GhstColors.TextMuted,
            )
        }
    }
}

@Composable
private fun EmptyRules() {
    GhstCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.filters_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = GhstColors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.filters_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = GhstColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
