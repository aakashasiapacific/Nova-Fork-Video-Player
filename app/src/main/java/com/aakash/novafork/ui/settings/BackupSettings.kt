package com.aakash.novafork.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.IosSwitch
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.RestorePreviewUi
import java.text.DateFormat
import java.util.Date

/**
 * Settings › Backup & restore: saving a backup file (optionally with passwords and sign-ins) and
 * restoring one after a summary. Its state comes from [AppActions.backupState].
 */
internal fun backupGroup(actions: AppActions): SettingsGroup = SettingsGroup(
    key = "backup",
    title = "Backup & restore",
    rows = listOf(
        SettingsRow("backup-save") { shape -> BackupRow(shape, actions) },
        SettingsRow("backup-restore") { shape -> RestoreRow(shape, actions) },
    ),
    footer = "Settings, sources, watch history, manual matches and playlists. Restoring adds to what is " +
        "here; videos not found yet get their history once their source is scanned.",
)

@Composable
private fun BackupRow(shape: Shape, actions: AppActions) {
    val backup by actions.backupState().collectAsState()
    var asking by remember { mutableStateOf(false) }
    val last = backup.lastBackupAt
    val lastText = remember(last) { if (last == null) "No backup made on this device yet" else "Last backup ${dateTime(last)}" }
    RowSurface(shape = shape, onClick = { if (!backup.working) asking = true }) {
        RowIcon(R.drawable.ic_storage)
        RowTexts("Back Up", lastText, Modifier.weight(1f), note = "Saves a file you can keep or move to another device")
        if (backup.working) IosSpinner(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
    if (asking) {
        BackupDialog(
            onBackUp = { includeSecrets ->
                asking = false
                actions.exportBackup(includeSecrets)
            },
            onDismiss = { asking = false },
        )
    }
}

@Composable
private fun BackupDialog(onBackUp: (includeSecrets: Boolean) -> Unit, onDismiss: () -> Unit) {
    var includeSecrets by remember { mutableStateOf(false) }
    NovaAlertDialog(
        form = true,
        onDismissRequest = onDismiss,
        title = { Text("Back Up") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Settings, sources, watch history, manual matches and playlists go into a file you pick.")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(value = includeSecrets, role = Role.Switch, onValueChange = { includeSecrets = it })
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Include passwords and sign-ins", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    IosSwitch(checked = includeSecrets, onCheckedChange = null)
                }
                if (includeSecrets) {
                    Text(
                        text = "The file will hold share passwords, server sign-ins and API keys in plain text. " +
                            "Anyone who gets the file can read them: keep it somewhere private.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "Without them, you enter passwords and sign in to servers again after restoring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onBackUp(includeSecrets) }) { Text("Back Up") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestoreRow(shape: Shape, actions: AppActions) {
    val backup by actions.backupState().collectAsState()
    val waiting = backup.waiting
    RowSurface(shape = shape, onClick = { if (!backup.working) actions.importBackup() }) {
        RowIcon(R.drawable.ic_restart_alt)
        RowTexts(
            title = "Restore",
            summary = "Bring back a backup file",
            modifier = Modifier.weight(1f),
            note = when {
                waiting == 1 -> "1 item waits for its video to be scanned"
                waiting > 1 -> "$waiting items wait for their videos to be scanned"
                else -> null
            },
        )
    }
    val confirm = backup.confirm
    if (confirm != null) {
        RestoreDialog(confirm, onRestore = actions::confirmRestore, onCancel = actions::cancelRestore)
    }
}

@Composable
private fun RestoreDialog(preview: RestorePreviewUi, onRestore: () -> Unit, onCancel: () -> Unit) {
    val made = remember(preview.createdAt) { if (preview.createdAt > 0) dateTime(preview.createdAt) else null }
    NovaAlertDialog(
        form = true,
        onDismissRequest = onCancel,
        title = { Text("Restore this backup?") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val origin = listOfNotNull(
                    made?.let { "Made $it" },
                    preview.appVersion.takeIf { it.isNotBlank() }?.let { "Nova Fork $it" },
                ).joinToString(" · ")
                if (origin.isNotEmpty()) {
                    Text(origin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SummaryLine(count(preview.settings, "setting", "settings"))
                    SummaryLine(count(preview.sources, "source", "sources"))
                    SummaryLine(count(preview.videos, "video with watch history", "videos with watch history"))
                    SummaryLine(count(preview.matches, "manual match", "manual matches"))
                    if (preview.songs > 0) SummaryLine(count(preview.songs, "song with plays", "songs with plays"))
                    SummaryLine(count(preview.playlists, "playlist", "playlists"))
                }
                Text(
                    text = "Settings are replaced. Sources, watch history and playlists are added to what is here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (preview.includesSecrets) {
                        "It holds passwords and sign-ins: they are restored too."
                    } else {
                        "It holds no passwords or sign-ins: enter them again where a source asks."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onRestore) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun SummaryLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}

private fun count(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"

private fun dateTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
