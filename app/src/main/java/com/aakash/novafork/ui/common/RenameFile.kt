package com.aakash.novafork.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.VideoDetailsUi

/** The rules of "Rename File": what a typed name may be and how the whole file name is made from it. */
object FileRenames {
    /** The calm message for every failure (no permission, name taken, share unreachable). */
    const val FAILED = "Couldn't rename this file"

    /** The name without its extension, as the dialog prefills it. */
    fun baseName(fileName: String): String =
        if (fileName.lastIndexOf('.') > 0) fileName.substringBeforeLast('.') else fileName

    /** The extension with its dot (".mkv"), or "" when the name has none. It is always kept. */
    fun extension(fileName: String): String =
        if (fileName.lastIndexOf('.') > 0) "." + fileName.substringAfterLast('.') else ""

    /** Whether [name] (what was typed, without extension) may be used: not empty, no '/' or '\'. */
    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && '/' !in trimmed && '\\' !in trimmed
    }

    /** [typed] plus the extension of [oldFileName]; null when [typed] is not a valid name. */
    fun newFileName(oldFileName: String, typed: String): String? =
        if (isValid(typed)) typed.trim() + extension(oldFileName) else null
}

/** The "…" menu entry that renames the video's real file; absent for streams and media-server videos. */
@Composable
fun RenameFileMenuItem(details: VideoDetailsUi, close: () -> Unit, onRename: () -> Unit) {
    if (!details.renamable) return
    NovaMenuItem(
        text = { Text("Rename File…") },
        leadingIcon = { NovaIcon(R.drawable.ic_edit, contentDescription = null) },
        onClick = {
            close()
            onRename()
        },
    )
}

/**
 * iOS's naming alert for a video's file: the name without its extension (kept as it is), Cancel
 * and Rename. An empty name or one with / or \ shows the field's error state and does nothing.
 */
@Composable
fun RenameFileDialog(
    fileName: String,
    onRename: (baseName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(fileName) { mutableStateOf(FileRenames.baseName(fileName)) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val extension = FileRenames.extension(fileName)
    fun submit() {
        if (FileRenames.isValid(name)) {
            onRename(name.trim())
            onDismiss()
        } else {
            showError = true
        }
    }
    NovaAlertDialog(
        form = true,
        onDismissRequest = onDismiss,
        title = { Text("Rename File") },
        text = {
            NovaTextField(
                value = name,
                onValueChange = {
                    name = it
                    showError = false
                },
                isError = showError,
                supportingText = {
                    Text(
                        if (showError) "Enter a name without / or \\" else if (extension.isNotEmpty()) "Ends with $extension" else "",
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { IosTextButton(onClick = { submit() }) { Text("Rename") } },
        dismissButton = { IosTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
