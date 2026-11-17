package space.ghstmail.android.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import space.ghstmail.android.R
import space.ghstmail.android.api.AliasInfo
import space.ghstmail.android.api.AliasStatus
import space.ghstmail.android.api.AliasSummary
import space.ghstmail.android.ui.components.DashedDivider
import space.ghstmail.android.ui.components.GhostMark
import space.ghstmail.android.ui.components.GhstButton
import space.ghstmail.android.ui.components.GhstButtonStyle
import space.ghstmail.android.ui.components.GhstCard
import space.ghstmail.android.ui.components.GhstErrorBanner
import space.ghstmail.android.ui.components.GhstSegmented
import space.ghstmail.android.ui.components.GhstTextField
import space.ghstmail.android.ui.components.GhstTopBar
import space.ghstmail.android.ui.components.userMessage
import space.ghstmail.android.ui.theme.AddressSmallStyle
import space.ghstmail.android.ui.theme.AddressStyle
import space.ghstmail.android.ui.theme.GhstColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the fire takes to cross a row, then how long the row takes to close up. */
private const val BURN_MS = 950
private const val CLOSE_MS = 320
private const val BURN_END = 1.12f
private val BurnEasing = CubicBezierEasing(0.45f, 0f, 0.75f, 1f)

class HomeActions(
    val onGenerate: () -> Unit = {},
    val onCreate: (label: String, expiresInHours: Int?) -> Unit = { _, _ -> },
    val onCreateSheetChange: (open: Boolean) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onStatusChange: (AliasStatus) -> Unit = {},
    val onCopy: (AliasInfo) -> Unit = {},
    val onSetActive: (AliasInfo, Boolean) -> Unit = { _, _ -> },
    val onBurn: (AliasInfo) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onEnableAutofill: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var autofillEnabled by remember { mutableStateOf(isAutofillEnabled(context)) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    val animate = remember { animationsEnabled(context) }
    val copiedTemplate = stringResource(R.string.alias_copied)

    LifecycleResumeEffect(Unit) {
        autofillEnabled = isAutofillEnabled(context)
        onPauseOrDispose { }
    }

    fun copy(address: String) {
        clipboard.setText(AnnotatedString(address))
        // Android 13 and later show their own "copied" chip.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(copiedTemplate.format(address))
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.start()
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Copy -> copy(event.address)
                HomeEvent.AliasCreated -> createOpen = false
                HomeEvent.SessionExpired -> {
                    viewModel.reset()
                    onSessionExpired()
                }
            }
        }
    }

    val actions = remember(viewModel, animate) {
        HomeActions(
            onGenerate = viewModel::generate,
            onCreate = viewModel::create,
            onCreateSheetChange = { open ->
                createOpen = open
                viewModel.clearCreateError()
            },
            onQueryChange = viewModel::setQuery,
            onStatusChange = viewModel::setStatus,
            onCopy = { copy(it.address) },
            onSetActive = viewModel::setActive,
            onBurn = { viewModel.burn(it, if (animate) (BURN_MS + CLOSE_MS).toLong() else 0L) },
            onLoadMore = viewModel::loadMore,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onDismissError = viewModel::dismissError,
            onEnableAutofill = { openAutofillSettings(context) },
            onSignOut = {
                viewModel.reset()
                onSignOut()
            },
        )
    }

    HomeContent(
        state = state,
        autofillEnabled = autofillEnabled,
        createOpen = createOpen,
        animate = animate,
        snackbarHostState = snackbarHostState,
        actions = actions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    autofillEnabled: Boolean,
    createOpen: Boolean,
    animate: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var burnCandidate by remember { mutableStateOf<AliasInfo?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val hasAliases = state.summary.total > 0

    Scaffold(
        topBar = {
            GhstTopBar {
                TextButton(onClick = { confirmSignOut = true }) {
                    Text(
                        text = stringResource(R.string.home_sign_out),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhstColors.TextMuted,
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = GhstColors.Raised,
                    contentColor = GhstColors.Text,
                )
            }
        },
        containerColor = GhstColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 20.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 20.dp,
                    top = 28.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                ),
            ) {
                item(key = "header") {
                    Header(
                        generating = state.generating,
                        onGenerate = actions.onGenerate,
                        onNewAlias = { actions.onCreateSheetChange(true) },
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

                if (hasAliases) {
                    item(key = "overview") {
                        Spacer(Modifier.height(20.dp))
                        Overview(summary = state.summary, forwardsTo = state.forwardsTo)
                    }
                }

                if (!autofillEnabled) {
                    item(key = "autofill") {
                        Spacer(Modifier.height(12.dp))
                        AutofillCard(onEnable = actions.onEnableAutofill)
                    }
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

                    !hasAliases -> if (!state.loadFailed) {
                        item(key = "empty") {
                            Spacer(Modifier.height(12.dp))
                            EmptyAliases(onCreate = { actions.onCreateSheetChange(true) })
                        }
                    }

                    else -> {
                        item(key = "filters") {
                            Spacer(Modifier.height(28.dp))
                            Filters(
                                query = state.query,
                                status = state.status,
                                matching = state.matching,
                                total = state.summary.total,
                                onQueryChange = actions.onQueryChange,
                                onStatusChange = actions.onStatusChange,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        if (state.rows.isEmpty()) {
                            item(key = "no-match") {
                                Text(
                                    text = stringResource(R.string.aliases_no_match),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GhstColors.TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                )
                            }
                        }
                        items(state.rows, key = { it.id }) { alias ->
                            AliasRow(
                                alias = alias,
                                fresh = alias.id == state.freshId,
                                burning = alias.id == state.burningId,
                                animate = animate,
                                now = now,
                                onCopy = { actions.onCopy(alias) },
                                onSetActive = { actions.onSetActive(alias, it) },
                                onBurn = { burnCandidate = alias },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (state.canLoadMore) {
                            item(key = "more") {
                                LaunchedEffect(state.rows.size) { actions.onLoadMore() }
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = GhstColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        CreateAliasSheet(
            creating = state.creating,
            error = state.createError?.userMessage(),
            onCreate = actions.onCreate,
            onDismiss = { actions.onCreateSheetChange(false) },
        )
    }

    burnCandidate?.let { alias ->
        GhstDialog(
            title = stringResource(R.string.burn_title),
            onDismiss = { burnCandidate = null },
            confirm = {
                GhstButton(
                    text = stringResource(R.string.burn_confirm),
                    onClick = {
                        burnCandidate = null
                        actions.onBurn(alias)
                    },
                    style = GhstButtonStyle.Burn,
                    compact = true,
                    icon = painterResource(R.drawable.ic_flame),
                )
            },
        ) {
            Text(text = alias.address, style = AddressStyle, color = GhstColors.Text)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.burn_body),
                style = MaterialTheme.typography.bodyMedium,
                color = GhstColors.TextMuted,
            )
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
private fun Header(generating: Boolean, onGenerate: () -> Unit, onNewAlias: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.aliases_title),
            style = MaterialTheme.typography.headlineLarge,
            color = GhstColors.Text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.aliases_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = GhstColors.TextMuted,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhstButton(
                text = stringResource(R.string.aliases_instant),
                onClick = onGenerate,
                style = GhstButtonStyle.Secondary,
                loading = generating,
                icon = rememberVectorPainter(Icons.Outlined.Bolt),
                modifier = Modifier.weight(1f),
            )
            GhstButton(
                text = stringResource(R.string.aliases_new),
                onClick = onNewAlias,
                icon = rememberVectorPainter(Icons.Outlined.Add),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(summary: AliasSummary, forwardsTo: String?) {
    val rate = forwardRate(summary.received, summary.forwarded)
    GhstCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.overview_lead),
            style = MaterialTheme.typography.bodyMedium,
            color = GhstColors.TextMuted,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = summary.forwarded.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 48.sp),
                color = GhstColors.Text,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = stringResource(R.string.overview_of_emails, summary.received),
                style = MaterialTheme.typography.bodyLarge,
                color = GhstColors.TextMuted,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(14.dp))
        DeliveryBar(rate = rate)
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stat(stringResource(R.string.overview_aliases), summary.total)
            Stat(stringResource(R.string.overview_active), summary.active)
            Stat(stringResource(R.string.overview_paused), (summary.total - summary.active).coerceAtLeast(0))
            Stat(
                stringResource(R.string.overview_not_forwarded),
                (summary.received - summary.forwarded).coerceAtLeast(0),
            )
        }
        if (!forwardsTo.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            DashedDivider()
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.overview_forwarding_to),
                    style = MaterialTheme.typography.bodySmall,
                    color = GhstColors.TextDim,
                )
                Text(
                    text = forwardsTo,
                    style = AddressSmallStyle,
                    color = GhstColors.Forward,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = GhstColors.TextMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            color = GhstColors.Text,
        )
    }
}

@Composable
private fun DeliveryBar(rate: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(GhstColors.Raised, RoundedCornerShape(2.dp)),
    ) {
        if (rate != null && rate > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(rate)
                    .height(4.dp)
                    .background(GhstColors.Text.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun AutofillCard(onEnable: () -> Unit) {
    GhstCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.autofill_off_title),
            style = MaterialTheme.typography.titleLarge,
            color = GhstColors.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.autofill_off_body),
            style = MaterialTheme.typography.bodyMedium,
            color = GhstColors.TextMuted,
        )
        Spacer(Modifier.height(16.dp))
        GhstButton(
            text = stringResource(R.string.autofill_off_action),
            onClick = onEnable,
            style = GhstButtonStyle.Secondary,
            compact = true,
        )
    }
}

@Composable
private fun EmptyAliases(onCreate: () -> Unit) {
    GhstCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 36.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GhostMark(size = 40.dp, tint = GhstColors.TextMuted)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.aliases_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = GhstColors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.aliases_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = GhstColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            GhstButton(
                text = stringResource(R.string.aliases_empty_action),
                onClick = onCreate,
                icon = rememberVectorPainter(Icons.Outlined.Add),
            )
        }
    }
}

@Composable
private fun Filters(
    query: String,
    status: AliasStatus,
    matching: Int,
    total: Int,
    onQueryChange: (String) -> Unit,
    onStatusChange: (AliasStatus) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GhstTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.aliases_search_placeholder),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.aliases_search_clear),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhstSegmented(
                options = listOf(
                    AliasStatus.All to stringResource(R.string.aliases_filter_all),
                    AliasStatus.Active to stringResource(R.string.aliases_filter_active),
                    AliasStatus.Paused to stringResource(R.string.aliases_filter_paused),
                ),
                selected = status,
                onSelect = onStatusChange,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.aliases_matching, matching, total),
                style = MaterialTheme.typography.bodyMedium,
                color = GhstColors.TextMuted,
            )
        }
    }
}

@Composable
private fun AliasRow(
    alias: AliasInfo,
    fresh: Boolean,
    burning: Boolean,
    animate: Boolean,
    now: Long,
    onCopy: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onBurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val front = remember { Animatable(0f) }
    var closed by remember { mutableStateOf(false) }
    LaunchedEffect(burning) {
        if (burning) {
            if (animate) front.animateTo(BURN_END, tween(BURN_MS, easing = BurnEasing))
            else front.snapTo(BURN_END)
            closed = true
        } else {
            // The server refused: the fire is called off and the row comes back.
            closed = false
            front.snapTo(0f)
        }
    }

    AnimatedVisibility(
        visible = !closed,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically(tween(if (animate) CLOSE_MS else 0)),
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GhstColors.Card,
                contentColor = GhstColors.Text,
                border = BorderStroke(1.dp, GhstColors.Border),
            ) {
                AliasRowContent(
                    alias = alias,
                    fresh = fresh,
                    enabled = !burning,
                    now = now,
                    onCopy = onCopy,
                    onSetActive = onSetActive,
                    onBurn = onBurn,
                    modifier = Modifier.burnFront { front.value },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AliasRowContent(
    alias: AliasInfo,
    fresh: Boolean,
    enabled: Boolean,
    now: Long,
    onCopy: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onBurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = alias.label?.takeIf { it.isNotBlank() }
    val active = alias.is_active
    val stateText = stringResource(if (active) R.string.alias_state_active else R.string.alias_state_paused)
    // "Burn" alone does not say which one.
    val burnDescription = stringResource(R.string.alias_burn_description, alias.address)
    Column(
        modifier = modifier.clickable(
            enabled = enabled,
            onClickLabel = stringResource(R.string.alias_copy_label),
            onClick = onCopy,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = label ?: stringResource(R.string.alias_no_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (label != null && active) GhstColors.Text else GhstColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = alias.address,
                        style = AddressStyle.copy(fontSize = 13.sp),
                        color = if (active) GhstColors.Amber else GhstColors.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = GhstColors.TextDim,
                    )
                }
            }
            Switch(
                checked = active,
                onCheckedChange = onSetActive,
                enabled = enabled,
                modifier = Modifier.semantics { stateDescription = stateText },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GhstColors.Background,
                    checkedTrackColor = GhstColors.Amber,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = GhstColors.TextMuted,
                    uncheckedTrackColor = GhstColors.Input,
                    uncheckedBorderColor = Color.Transparent,
                    disabledCheckedThumbColor = GhstColors.Background,
                    disabledCheckedTrackColor = GhstColors.Amber.copy(alpha = 0.5f),
                    disabledUncheckedThumbColor = GhstColors.TextDim,
                    disabledUncheckedTrackColor = GhstColors.Input,
                    disabledUncheckedBorderColor = Color.Transparent,
                    disabledCheckedBorderColor = Color.Transparent,
                ),
            )
        }
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (fresh) {
                    AnnotatedString(
                        stringResource(R.string.alias_created_copied),
                        SpanStyle(color = GhstColors.Amber),
                    )
                } else {
                    aliasMeta(alias, now)
                },
                style = MaterialTheme.typography.bodySmall,
                color = GhstColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onBurn,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = GhstColors.TextMuted,
                    disabledContentColor = GhstColors.TextDim,
                ),
                modifier = Modifier.semantics { contentDescription = burnDescription },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flame),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.alias_burn),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun aliasMeta(alias: AliasInfo, now: Long): AnnotatedString {
    val paused = stringResource(R.string.alias_paused)
    val forwarded = stringResource(R.string.alias_forwarded, alias.emails_forwarded, alias.emails_received)
    val expiry = expiryOf(alias.expires_at, now)
    val expiryText = when (expiry) {
        Expiry.Never -> null
        Expiry.Expired -> stringResource(R.string.alias_expired)
        is Expiry.Left -> when {
            expiry.days >= 1 -> stringResource(R.string.alias_expires_days, expiry.days, expiry.hours)
            expiry.hours >= 1 -> stringResource(R.string.alias_expires_hours, expiry.hours, expiry.minutes)
            else -> stringResource(R.string.alias_expires_minutes, expiry.minutes)
        }
    }
    return buildAnnotatedString {
        if (!alias.is_active) append("$paused  ·  ")
        append(forwarded)
        if (expiryText != null) {
            append("  ·  ")
            if (expiry == Expiry.Expired) {
                withStyle(SpanStyle(color = GhstColors.Burn)) { append(expiryText) }
            } else {
                append(expiryText)
            }
        }
    }
}

/**
 * The fire front crossing the row left to right. [progress] is how far it has got, as a
 * share of the width. Behind it is char with a hot edge; ahead of it, the row as it was.
 */
private fun Modifier.burnFront(progress: () -> Float): Modifier = drawWithContent {
    val p = progress()
    if (p <= 0f) {
        drawContent()
        return@drawWithContent
    }
    val width = size.width
    val x = width * p
    val edge = x.coerceAtMost(width)

    clipRect(right = edge) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color(0xFF050403),
                ((x - 0.14f * width) / x).coerceIn(0f, 1f) to Color(0xFF0E0704),
                ((x - 0.03f * width) / x).coerceIn(0f, 1f) to Color(0xFF3B1206),
                1f to Color(0xFF7A2408),
                startX = 0f,
                endX = x,
            ),
        )
    }
    clipRect(left = edge) {
        this@drawWithContent.drawContent()
    }

    val bandWidth = 110.dp.toPx()
    val bandStart = x - 74.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.30f to Color(0x59FF4614),
            0.56f to Color(0xD9FF8C28),
            0.67f to Color(0xFFFFF3CF),
            0.72f to Color(0xE6FFBE46),
            0.84f to Color(0x59FF6E1E),
            1f to Color.Transparent,
            startX = bandStart,
            endX = bandStart + bandWidth,
        ),
        topLeft = Offset(bandStart, 0f),
        size = Size(bandWidth, size.height),
        blendMode = BlendMode.Screen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAliasSheet(
    creating: Boolean,
    error: String?,
    onCreate: (label: String, expiresInHours: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var expires by rememberSaveable { mutableStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = GhstColors.Card,
        contentColor = GhstColors.Text,
        scrimColor = GhstColors.Background.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(GhstColors.Border, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.create_title),
                style = MaterialTheme.typography.headlineSmall,
                color = GhstColors.Text,
            )
            Spacer(Modifier.height(20.dp))
            GhstTextField(
                value = label,
                onValueChange = { label = it },
                label = stringResource(R.string.create_label),
                placeholder = stringResource(R.string.create_label_placeholder),
                enabled = !creating,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onCreate(label, expires.takeIf { it > 0 }) }
                ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.create_expires),
                style = MaterialTheme.typography.titleSmall,
                color = GhstColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            GhstSegmented(
                options = listOf(
                    0 to stringResource(R.string.create_expires_never),
                    1 to stringResource(R.string.create_expires_1h),
                    24 to stringResource(R.string.create_expires_24h),
                    168 to stringResource(R.string.create_expires_7d),
                ),
                selected = expires,
                onSelect = { expires = it },
                fill = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                GhstErrorBanner(message = error)
            }
            Spacer(Modifier.height(20.dp))
            GhstButton(
                text = stringResource(R.string.create_action),
                onClick = { onCreate(label, expires.takeIf { it > 0 }) },
                loading = creating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A confirmation. Cancel comes first, so a reflex tap never destroys anything. */
@Composable
private fun GhstDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = { Column { body() } },
        confirmButton = confirm,
        dismissButton = {
            GhstButton(
                text = stringResource(R.string.home_cancel),
                onClick = onDismiss,
                style = GhstButtonStyle.Secondary,
                compact = true,
            )
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = GhstColors.Card,
        titleContentColor = GhstColors.Text,
        textContentColor = GhstColors.TextMuted,
    )
}

private fun isAutofillEnabled(context: Context): Boolean =
    context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true

/** False when the system's "remove animations" setting is on. */
private fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

private fun openAutofillSettings(context: Context) {
    val request = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
        .setData(Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(request)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
