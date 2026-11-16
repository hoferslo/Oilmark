package com.storagecloud.oilmark.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storagecloud.oilmark.data.ApiException
import kotlinx.coroutines.launch

/** The three states of anything fetched from the server. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** Holds the result of one server fetch and survives screen rotation. */
class Loader<T>(private val fetch: suspend () -> T) : ViewModel() {
    var state: Load<T> by mutableStateOf(Load.Loading)
        private set
    /** True only while a refresh the user asked for (by pulling down) is running. */
    var pulling by mutableStateOf(false)
        private set
    private var running = false

    /** Fetches again, quietly: what is on screen stays until the new data arrives. */
    fun refresh() {
        if (running) return
        running = true
        if (state is Load.Failed) state = Load.Loading
        viewModelScope.launch {
            state = try {
                Load.Ready(fetch())
            } catch (e: ApiException) {
                Load.Failed(e.message.orEmpty())
            }
            running = false
            pulling = false
        }
    }

    /** Same as [refresh], but shows the pull-to-refresh spinner while it runs. */
    fun pullToRefresh() {
        pulling = true
        refresh()
    }
}

/**
 * Fetches data for a screen and re-fetches every time the screen comes back into view,
 * so lists are up to date after returning from an edit screen.
 */
@Composable
fun <T> rememberLoader(fetch: suspend () -> T): Loader<T> {
    val loader = viewModel<Loader<T>> { Loader(fetch) }
    LifecycleResumeEffect(loader) {
        loader.refresh()
        onPauseOrDispose { }
    }
    return loader
}

/** Shows a spinner, an error with a retry button, or [content] - with pull-to-refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LoadContent(
    loader: Loader<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val state = loader.state
    PullToRefreshBox(
        isRefreshing = loader.pulling,
        onRefresh = loader::pullToRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when (state) {
            Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is Load.Failed -> Message(
                icon = Icons.Outlined.CloudOff,
                title = "Couldn't load",
                text = state.message,
                action = "Try again",
                onAction = loader::refresh,
            )
            is Load.Ready -> content(state.value)
        }
    }
}
