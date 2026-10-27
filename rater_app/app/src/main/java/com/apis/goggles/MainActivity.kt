package com.apis.goggles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apis.goggles.ui.GogglesTheme
import com.apis.goggles.ui.HelpSheet
import com.apis.goggles.ui.HistoryScreen
import com.apis.goggles.ui.RateScreen
import com.apis.goggles.ui.RateTopBar
import com.apis.goggles.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: RaterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Leaving the app pushes any pending ratings to GitHub.
        lifecycle.addObserver(LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.autoSync() })
        setContent { GogglesTheme { App(vm) } }
    }
}

private enum class Screen { RATE, SETTINGS, HISTORY }

@Composable
private fun App(vm: RaterViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val goggle by vm.selected.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.RATE) }
    var help by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Message -> scope.launch { snackbar.showSnackbar(e.text, withDismissAction = true) }
                is UiEvent.Rated -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val label = e.rating.siblingOf?.let { "${e.rating.domain}: ${e.rating.describe()} (same as $it)" }
                        ?: "${e.rating.domain}: ${e.rating.describe()}"
                    val result = snackbar.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) vm.undo(e.rating)
                }
            }
        }
    }

    BackHandler(enabled = screen != Screen.RATE) { screen = Screen.RATE }

    when (screen) {
        Screen.SETTINGS -> SettingsScreen(state, vm::saveSettings, vm::resetPaging) { screen = Screen.RATE }
        Screen.HISTORY -> HistoryScreen(goggle, state.ratings, vm::undo) { screen = Screen.RATE }
        Screen.RATE -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                RateTopBar(
                    pending = state.pending().size,
                    syncing = syncing,
                    onSync = vm::sync,
                    onHelp = { help = true },
                    onHistory = { screen = Screen.HISTORY },
                    onSettings = { screen = Screen.SETTINGS },
                )
            },
        ) { pad ->
            if (!loaded) {
                Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val gs = state.of(goggle)
                RateScreen(
                    goggle = goggle,
                    goggleText = gs.text,
                    queue = gs.queue,
                    searching = goggle in searching,
                    hasBraveKey = state.braveKey.isNotBlank(),
                    onSelect = vm::select,
                    onRate = vm::rate,
                    onLater = vm::later,
                    onNeedPreview = vm::ensurePreview,
                    onFetchMore = { vm.refill(goggle) },
                    onSettings = { screen = Screen.SETTINGS },
                    modifier = Modifier.padding(pad),
                )
            }
        }
    }
    if (help) HelpSheet { help = false }
}
