package com.aakash.novafork.ui.phone

import android.app.Activity
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.DeviceAuth
import com.aakash.novafork.ui.settings.GroupFooter
import com.aakash.novafork.ui.settings.InfoRow
import com.aakash.novafork.ui.settings.SettingsCard
import com.aakash.novafork.ui.settings.SwitchRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.SwipeToDelete
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.HiddenItemUi
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors

/**
 * An actor's or director's page (a name tapped under Starring, Director or Created by): the
 * library's movies and shows they are credited in, newest first, in the library's own poster grid.
 */
@Composable
internal fun PhonePersonScreen(
    name: String,
    items: List<LibraryItemUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = name, onBack = actions::back)
        if (items.isEmpty()) {
            CenteredScrollable {
                PhoneEmptyLine("Nothing in your library")
            }
        } else {
            PhoneLibraryGrid(items = items, actions = actions)
        }
    }
}

/**
 * Preferences › Hidden Videos: what was hidden from the library (a show's hidden episodes as one
 * row). "Unhide", on the row or by swiping it, brings it back; nothing was ever deleted.
 */
@Composable
internal fun PhoneHiddenVideosScreen(
    items: List<HiddenItemUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    requireFingerprint: Boolean = false,
) {
    val context = LocalContext.current
    val canAuth = remember { DeviceAuth.available(context) }
    // Unlocked for this visit only; leaving the page and coming back asks again.
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }
    val locked = requireFingerprint && canAuth && !unlocked
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) unlocked = true else actions.back()
    }
    var signal by remember { mutableStateOf<CancellationSignal?>(null) }
    var gone by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            gone = true
            signal?.cancel()
        }
    }
    LaunchedEffect(locked) {
        if (locked && !asked) {
            asked = true
            signal = DeviceAuth.prompt(context, "Hidden Videos", legacy) { ok ->
                if (gone) return@prompt
                if (ok) unlocked = true else actions.back()
            }
        }
    }
    if (locked) {
        // Nothing of the list shows until the person is confirmed.
        Column(modifier.fillMaxSize()) { PhoneTopBar(title = "Hidden Videos", onBack = actions::back) }
        return
    }
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = "Hidden Videos", onBack = actions::back)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingsCard {
                if (canAuth) {
                    SwitchRow(
                        shape = RectangleShape,
                        icon = R.drawable.ic_lock,
                        title = "Require Fingerprint",
                        checked = requireFingerprint,
                        onCheckedChange = { on ->
                            unlocked = true
                            actions.updateSettings { it.copy(requireFingerprint = on) }
                        },
                    )
                } else {
                    InfoRow(RectangleShape, R.drawable.ic_lock, "Require Fingerprint", "Unavailable")
                }
            }
            GroupFooter(
                if (canAuth) {
                    "Asks for your fingerprint or screen lock each time this page opens."
                } else {
                    "Set a screen lock or fingerprint in Android settings to use this."
                },
            )
        }
        if (items.isEmpty()) {
            CenteredScrollable {
                PhoneEmptyLine("No hidden videos")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
            ) {
                items(items, key = { it.key }) { item ->
                    val card = remember(item) { VideoCardUi(id = 0L, title = item.title, subtitle = item.subtitle, artwork = item.artwork) }
                    SwipeToDelete(onDelete = { actions.unhideVideos(item.videoIds) }, label = "Unhide") {
                        PhoneVideoRow(
                            card = card,
                            onClick = { if (item.videoIds.size == 1) actions.openVideo(item.videoIds.first()) },
                            trailing = {
                                Text(
                                    text = "Unhide",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NovaColors.IosBlue,
                                    modifier = Modifier.clickable { actions.unhideVideos(item.videoIds) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
