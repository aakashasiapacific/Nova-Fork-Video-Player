package com.aakash.novafork.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalDeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.dimensFor
import com.aakash.novafork.ui.adaptive.rememberDeviceLayout
import com.aakash.novafork.ui.common.HudState
import com.aakash.novafork.ui.common.IosHudHost
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.IosToastHost
import com.aakash.novafork.ui.common.LocalGlassTint
import com.aakash.novafork.ui.common.LocalHud
import com.aakash.novafork.ui.common.LocalSheetDepth
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.SheetDepth
import com.aakash.novafork.ui.common.sinkBehindSheets
import com.aakash.novafork.ui.dialogs.NovaDialogs
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.phone.PhoneApp
import com.aakash.novafork.ui.tablet.TabletApp
import com.aakash.novafork.ui.theme.NovaTheme

/**
 * The whole library UI: theme, the phone or tablet family, dialogs, notices and system back.
 * Stateless; MainActivity feeds it the view model's state and actions.
 */
@Composable
fun NovaRoot(state: AppUiState, actions: AppActions, modifier: Modifier = Modifier) {
    val settings = state.settings.settings
    NovaTheme(settings.themeMode) {
        val layout = rememberDeviceLayout(settings.layoutMode)
        val hud = remember { HudState() }
        CompositionLocalProvider(
            LocalDeviceLayout provides layout,
            LocalNovaDimens provides dimensFor(layout),
            LocalHud provides hud,
            LocalGlassTint provides settings.glassTint,
            LocalSheetDepth provides remember { SheetDepth() },
        ) {
            Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().sinkBehindSheets()) {
                    if (layout.isTablet) {
                        TabletApp(state = state, actions = actions, layout = layout)
                    } else {
                        PhoneApp(state = state, actions = actions)
                    }
                    IosToastHost(
                        message = state.message,
                        onShown = actions::consumeMessage,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                    )
                    IosHudHost(hud, Modifier.align(Alignment.Center))
                }
                NovaDialogs(dialog = state.dialog, actions = actions, playlists = state.browse.playlists)
                state.crashReport?.let { report -> CrashReportDialog(report, actions) }
            }

            BackHandler(enabled = state.canGoBack) { actions.back() }
        }
    }
}

/** Shown once after a crash so the report can be sent to the developer. */
@Composable
private fun CrashReportDialog(report: String, actions: AppActions) {
    NovaAlertDialog(
        onDismissRequest = actions::dismissCrashReport,
        title = { Text("Nova Fork closed unexpectedly") },
        text = {
            Text(
                text = report.lineSequence().take(14).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { IosTextButton(onClick = actions::shareCrashReport) { Text("Share Report") } },
        dismissButton = { IosTextButton(onClick = actions::dismissCrashReport) { Text("Dismiss") } },
    )
}
