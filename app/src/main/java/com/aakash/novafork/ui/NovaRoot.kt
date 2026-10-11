package com.aakash.novafork.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalDeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.dimensFor
import com.aakash.novafork.ui.adaptive.rememberDeviceLayout
import com.aakash.novafork.ui.dialogs.NovaDialogs
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.phone.PhoneApp
import com.aakash.novafork.ui.tablet.TabletApp
import com.aakash.novafork.ui.theme.NovaTheme

/** Height of the phone's bottom navigation bar, which snackbars must clear. */
private val PhoneNavigationBarHeight = 80.dp

/**
 * The whole library UI: theme, the phone or tablet family, dialogs, snackbars and system back.
 * Stateless; MainActivity feeds it the view model's state and actions.
 */
@Composable
fun NovaRoot(state: AppUiState, actions: AppActions, modifier: Modifier = Modifier) {
    val settings = state.settings.settings
    NovaTheme(settings.themeMode) {
        val layout = rememberDeviceLayout(settings.layoutMode)
        CompositionLocalProvider(
            LocalDeviceLayout provides layout,
            LocalNovaDimens provides dimensFor(layout),
        ) {
            val snackbarHostState = remember { SnackbarHostState() }
            Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    if (layout.isTablet) {
                        TabletApp(state = state, actions = actions, layout = layout)
                    } else {
                        PhoneApp(state = state, actions = actions)
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (layout.isTablet) 16.dp else PhoneNavigationBarHeight + 8.dp),
                    )
                }
                NovaDialogs(dialog = state.dialog, actions = actions)
            }

            val message = state.message
            LaunchedEffect(message) {
                if (message != null) {
                    // A newer message restarts this effect and replaces the one on screen.
                    snackbarHostState.showSnackbar(message)
                    actions.consumeMessage()
                }
            }

            BackHandler(enabled = state.canGoBack) { actions.back() }
        }
    }
}
