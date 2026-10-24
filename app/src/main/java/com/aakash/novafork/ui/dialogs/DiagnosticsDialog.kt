package com.aakash.novafork.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi

/** Settings › About › Library diagnostics: the report, with Copy and Share. Phone and tablet. */
@Composable
internal fun LibraryDiagnosticsDialog(dialog: DialogUi.LibraryDiagnostics, actions: AppActions) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val report = dialog.report
    NovaAlertDialog(
        form = true,
        onDismissRequest = actions::dismissDialog,
        icon = { NovaIcon(R.drawable.ic_info, contentDescription = null) },
        title = { Text("Library diagnostics") },
        text = {
            if (report == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IosSpinner(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Checking storage access, Android's media library and your sources…")
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "What Nova sees on this device. Copy or share it with whoever helps you; it names " +
                            "folders but no files, and holds no passwords or keys.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        if (report != null) {
                            clipboard.setText(AnnotatedString(report))
                            copied = true
                        }
                    },
                    enabled = report != null,
                ) { Text(if (copied) "Copied" else "Copy") }
                TextButton(
                    onClick = { if (report != null) actions.shareText("Nova Fork library diagnostics", report) },
                    enabled = report != null,
                ) { Text("Share") }
            }
        },
        dismissButton = {
            TextButton(onClick = actions::dismissDialog) { Text("Close") }
        },
    )
}
