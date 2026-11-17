package space.ghstmail.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import space.ghstmail.android.api.AliasInfo
import space.ghstmail.android.api.AliasListResponse
import space.ghstmail.android.api.AliasStatus
import space.ghstmail.android.api.AliasSummary
import space.ghstmail.android.api.ApiException
import space.ghstmail.android.api.GhstMailApi
import space.ghstmail.android.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadFailed: Boolean = false,
    val error: ApiException? = null,
    val rows: List<AliasInfo> = emptyList(),
    val matching: Int = 0,
    val summary: AliasSummary = AliasSummary(),
    val query: String = "",
    val status: AliasStatus = AliasStatus.All,
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val generating: Boolean = false,
    val creating: Boolean = false,
    val createError: ApiException? = null,
    val freshId: String? = null,
    val burningId: String? = null,
    val forwardsTo: String? = null,
)

sealed interface HomeEvent {
    data class Copy(val address: String) : HomeEvent
    data object AliasCreated : HomeEvent
    data object SessionExpired : HomeEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = AuthManager(application)

    private val _state = MutableStateFlow(HomeUiState(forwardsTo = auth.email))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Every alias, when the server sent them all at once instead of a page. */
    private var everything: List<AliasInfo>? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        _state.update { it.copy(forwardsTo = auth.email) }
        load(reset = true)
    }

    /** Forgets everything, for when the account signs out. */
    fun reset() {
        loadJob?.cancel()
        searchJob?.cancel()
        everything = null
        started = false
        _state.value = HomeUiState()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load(reset = true)
    }

    fun retry() {
        _state.update { it.copy(loading = it.rows.isEmpty(), error = null) }
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore || current.loadingMore || loadJob?.isActive == true) return
        _state.update { it.copy(loadingMore = true) }
        load(reset = false)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (applyLocally()) return
        // Requests run a beat behind what is typed.
        searchJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MS)
            load(reset = true)
        }
    }

    fun setStatus(status: AliasStatus) {
        if (status == _state.value.status) return
        _state.update { it.copy(status = status) }
        searchJob?.cancel()
        if (applyLocally()) return
        load(reset = true)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun clearCreateError() = _state.update { it.copy(createError = null) }

    /** Makes an alias with no label and copies it. */
    fun generate() {
        if (_state.value.generating) return
        val token = token() ?: return
        _state.update { it.copy(generating = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { GhstMailApi.createAlias(token) }
            _state.update { it.copy(generating = false) }
            result.onSuccess { added(it.alias) }.onFailure { fail(it) }
        }
    }

    fun create(label: String, expiresInHours: Int?) {
        if (_state.value.creating) return
        val token = token() ?: return
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                GhstMailApi.createAlias(token, label.trim().takeIf { it.isNotEmpty() }, expiresInHours)
            }
            _state.update { it.copy(creating = false) }
            result.onSuccess {
                added(it.alias)
                _events.send(HomeEvent.AliasCreated)
            }.onFailure { error ->
                if (error is ApiException.SessionExpired) fail(error)
                else _state.update { it.copy(createError = error.asApiException()) }
            }
        }
    }

    fun setActive(alias: AliasInfo, active: Boolean) {
        val token = token() ?: return
        replace(alias.copy(is_active = active), activeDelta = if (active) 1 else -1)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { GhstMailApi.setAliasActive(token, alias.id, active) }
            result.onSuccess { replace(it.alias, activeDelta = 0) }
                .onFailure {
                    replace(alias, activeDelta = if (active) -1 else 1)
                    fail(it)
                }
        }
    }

    /**
     * The request goes out as the fire starts, so the animation does not hold the real work
     * back. If the server refuses, the row comes back and the reason is shown.
     */
    fun burn(alias: AliasInfo, burnMillis: Long) {
        if (_state.value.burningId != null) return
        val token = token() ?: return
        _state.update { it.copy(burningId = alias.id, error = null) }
        viewModelScope.launch {
            val request = async(Dispatchers.IO) { GhstMailApi.deleteAlias(token, alias.id) }
            delay(burnMillis)
            val result = request.await()
            result.onSuccess {
                everything = everything?.filterNot { it.id == alias.id }
                _state.update { state ->
                    state.copy(
                        burningId = null,
                        rows = state.rows.filterNot { it.id == alias.id },
                        matching = (state.matching - 1).coerceAtLeast(0),
                        summary = state.summary.copy(
                            total = (state.summary.total - 1).coerceAtLeast(0),
                            active = (state.summary.active - if (alias.is_active) 1 else 0).coerceAtLeast(0),
                            received = (state.summary.received - alias.emails_received).coerceAtLeast(0),
                            forwarded = (state.summary.forwarded - alias.emails_forwarded).coerceAtLeast(0),
                        ),
                    )
                }
            }.onFailure {
                _state.update { state -> state.copy(burningId = null) }
                fail(it)
            }
        }
    }

    private fun load(reset: Boolean) {
        val token = token() ?: return
        val ask = _state.value
        val offset = if (reset) 0 else ask.rows.size
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                GhstMailApi.getAliases(token, PAGE_SIZE, offset, ask.query, ask.status)
            }
            result.onSuccess { show(it, reset) }.onFailure { error ->
                _state.update {
                    it.copy(loading = false, refreshing = false, loadingMore = false, loadFailed = true)
                }
                fail(error)
            }
        }
    }

    private fun show(page: AliasListResponse, reset: Boolean) {
        if (!page.isPaged) {
            everything = page.aliases
            applyLocally()
            _state.update {
                it.copy(loading = false, refreshing = false, loadingMore = false, loadFailed = false, error = null)
            }
            return
        }
        everything = null
        _state.update { state ->
            val rows = if (reset) page.aliases else (state.rows + page.aliases).distinctBy { it.id }
            val total = page.total ?: rows.size
            state.copy(
                loading = false,
                refreshing = false,
                loadingMore = false,
                loadFailed = false,
                error = null,
                rows = rows,
                matching = total,
                summary = page.summary ?: state.summary,
                canLoadMore = page.aliases.isNotEmpty() && rows.size < total,
            )
        }
    }

    /** Filters what is already here. False when the server pages, and has to be asked. */
    private fun applyLocally(): Boolean {
        val all = everything ?: return false
        _state.update { state ->
            val rows = filterAliases(all, state.query, state.status)
            state.copy(rows = rows, matching = rows.size, summary = summarize(all), canLoadMore = false)
        }
        return true
    }

    private fun added(alias: AliasInfo) {
        everything = everything?.let { listOf(alias) + it }
        _state.update { state ->
            state.copy(
                rows = listOf(alias) + state.rows.filterNot { it.id == alias.id },
                matching = state.matching + 1,
                summary = state.summary.copy(
                    total = state.summary.total + 1,
                    active = state.summary.active + if (alias.is_active) 1 else 0,
                ),
                freshId = alias.id,
                error = null,
            )
        }
        viewModelScope.launch {
            _events.send(HomeEvent.Copy(alias.address))
            delay(FRESH_MS)
            _state.update { if (it.freshId == alias.id) it.copy(freshId = null) else it }
        }
    }

    private fun replace(alias: AliasInfo, activeDelta: Int) {
        everything = everything?.map { if (it.id == alias.id) alias else it }
        _state.update { state ->
            state.copy(
                rows = state.rows.map { if (it.id == alias.id) alias else it },
                summary = state.summary.copy(
                    active = (state.summary.active + activeDelta).coerceIn(0, state.summary.total)
                ),
            )
        }
    }

    private fun fail(error: Throwable) {
        if (error is ApiException.SessionExpired) {
            viewModelScope.launch { _events.send(HomeEvent.SessionExpired) }
            return
        }
        _state.update { it.copy(error = error.asApiException()) }
    }

    private fun token(): String? {
        val token = auth.token
        if (token == null) viewModelScope.launch { _events.send(HomeEvent.SessionExpired) }
        return token
    }

    private fun Throwable.asApiException(): ApiException =
        this as? ApiException ?: ApiException.Unreachable()

    companion object {
        const val PAGE_SIZE = 25
        private const val SEARCH_DELAY_MS = 250L
        private const val FRESH_MS = 4000L
    }
}
