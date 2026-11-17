package space.ghstmail.android.ui.filters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.ghstmail.android.api.ApiException
import space.ghstmail.android.api.FilterAction
import space.ghstmail.android.api.FilterRule
import space.ghstmail.android.api.GhstMailApi
import space.ghstmail.android.auth.AuthManager

data class FiltersUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: ApiException? = null,
    val rules: List<FilterRule> = emptyList(),
    val domain: String = "",
    val domainInvalid: Boolean = false,
    val action: FilterAction = FilterAction.Block,
    val adding: Boolean = false,
)

sealed interface FiltersEvent {
    data object SessionExpired : FiltersEvent
}

class FiltersViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = AuthManager(application)

    private val _state = MutableStateFlow(FiltersUiState())
    val state: StateFlow<FiltersUiState> = _state.asStateFlow()

    private val _events = Channel<FiltersEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null

    /** Whose rules are on screen, so another account never sees them. */
    private var loadedFor: String? = null

    /** Called each time the screen opens: rules can change on the website in between. */
    fun start() {
        val token = token() ?: return
        if (token != loadedFor) {
            loadedFor = token
            _state.value = FiltersUiState()
        }
        load(token)
    }

    fun retry() {
        val token = token() ?: return
        _state.update { it.copy(loading = it.rules.isEmpty(), error = null) }
        load(token)
    }

    fun setDomain(domain: String) = _state.update { it.copy(domain = domain, domainInvalid = false) }

    fun setAction(action: FilterAction) = _state.update { it.copy(action = action) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun add() {
        val current = _state.value
        if (current.adding) return
        val domain = normalizeDomain(current.domain)
        if (!looksLikeDomain(domain)) {
            _state.update { it.copy(domainInvalid = true) }
            return
        }
        val token = token() ?: return
        _state.update { it.copy(adding = true, error = null) }
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) {
                GhstMailApi.createFilter(token, domain, current.action)
            }
            created.onFailure {
                _state.update { state -> state.copy(adding = false) }
                fail(it)
                return@launch
            }
            // The answer's shape is the server's business: read the list back, like the website.
            val listed = withContext(Dispatchers.IO) { GhstMailApi.getFilters(token) }
            _state.update { state ->
                state.copy(
                    adding = false,
                    domain = "",
                    rules = listed.getOrNull()?.filters ?: state.rules,
                )
            }
            listed.onFailure { fail(it) }
        }
    }

    /** The row goes straight away and comes back where it was if the server refuses. */
    fun remove(rule: FilterRule) {
        val token = token() ?: return
        val index = _state.value.rules.indexOfFirst { it.id == rule.id }
        if (index < 0) return
        _state.update { state -> state.copy(rules = state.rules.filterNot { it.id == rule.id }, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { GhstMailApi.deleteFilter(token, rule.id) }
            result.onFailure { error ->
                _state.update { state ->
                    if (state.rules.any { it.id == rule.id }) return@update state
                    val rules = state.rules.toMutableList()
                    rules.add(index.coerceAtMost(rules.size), rule)
                    state.copy(rules = rules)
                }
                fail(error)
            }
        }
    }

    private fun load(token: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { GhstMailApi.getFilters(token) }
            result.onSuccess { list ->
                _state.update { it.copy(loading = false, loadFailed = false, error = null, rules = list.filters) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, loadFailed = true) }
                fail(error)
            }
        }
    }

    private fun fail(error: Throwable) {
        if (error is ApiException.SessionExpired) {
            loadedFor = null
            viewModelScope.launch { _events.send(FiltersEvent.SessionExpired) }
            return
        }
        _state.update { it.copy(error = error as? ApiException ?: ApiException.Unreachable()) }
    }

    private fun token(): String? {
        val token = auth.token
        if (token == null) viewModelScope.launch { _events.send(FiltersEvent.SessionExpired) }
        return token
    }
}
