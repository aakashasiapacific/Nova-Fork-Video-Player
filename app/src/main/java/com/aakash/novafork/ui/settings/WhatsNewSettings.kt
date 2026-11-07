package com.aakash.novafork.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosColors

/**
 * Preferences › About › What's New: this release's short list of changes, read from
 * res/raw/whats_new.txt (one change per line). Plain text on the card: no pop-up, no badge.
 */
internal fun whatsNewGroup(versionName: String): SettingsGroup = SettingsGroup(
    key = "whats-new",
    title = if (versionName.isBlank()) "What's New" else "What's New in $versionName",
    rows = listOf(
        SettingsRow("whats-new", "What's New", R.drawable.ic_info) { shape -> WhatsNewRow(shape) },
    ),
)

@Composable
private fun WhatsNewRow(shape: Shape) {
    val context = LocalContext.current
    val lines = remember {
        try {
            context.resources.openRawResource(R.raw.whats_new).bufferedReader().use { it.readLines() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    RowSurface(shape = shape) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lines.isEmpty()) {
                Text("Nothing to show.", style = MaterialTheme.typography.bodyMedium, color = IosColors.SecondaryLabel)
            }
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = IosColors.SecondaryLabel)
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
