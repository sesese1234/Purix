package app.yomi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomi.data.YomiRepository
import app.yomi.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Shared plumbing for every screen model: the repository, a scope, and a couple
 * of shorthands that stop each feature from re-deriving the same things.
 */
abstract class YomiViewModel(protected val repository: YomiRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    protected val scope: CoroutineScope get() = viewModelScope

    /** Fire-and-forget for the many "user tapped something" intents. */
    protected fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    protected fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
