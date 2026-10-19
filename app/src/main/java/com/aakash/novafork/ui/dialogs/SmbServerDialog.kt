package com.aakash.novafork.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.SmbForm
import com.aakash.novafork.ui.theme.NovaColors

/**
 * Add or edit an SMB share. The fields live here; the view model only reports test and save
 * results through [DialogUi.SmbServer.testing] / [DialogUi.SmbServer.testResult].
 */
@Composable
internal fun SmbServerDialog(dialog: DialogUi.SmbServer, actions: AppActions) {
    val initial = dialog.form
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var host by remember(initial.id) { mutableStateOf(initial.host) }
    var port by remember(initial.id) { mutableStateOf(initial.port) }
    var share by remember(initial.id) { mutableStateOf(initial.share) }
    var path by remember(initial.id) { mutableStateOf(initial.path) }
    var username by remember(initial.id) { mutableStateOf(initial.username) }
    var password by remember(initial.id) { mutableStateOf(initial.password) }
    var domain by remember(initial.id) { mutableStateOf(initial.domain) }
    var showPassword by remember { mutableStateOf(false) }
    // The result line belongs to the values that were tested; editing a field makes it stale.
    var checkedForm by remember { mutableStateOf<SmbForm?>(null) }

    val form = SmbForm(
        id = initial.id,
        name = name,
        host = host,
        port = port,
        share = share,
        path = path,
        username = username,
        password = password,
        domain = domain,
    )
    val result = dialog.testResult.takeIf { form == checkedForm }
    // The share may still be empty: a full address pasted into Server ("smb://nas/Movies") fills it in.
    val canSubmit = host.isNotBlank() && !dialog.testing

    NovaAlertDialog(
        form = true,
        onDismissRequest = actions::dismissDialog,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { NovaIcon(R.drawable.ic_lan, contentDescription = null) },
        title = { Text(if (initial.id == null) "Add network share" else "Edit network share") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FormField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Display name (optional)",
                    placeholder = "Living room NAS",
                    capitalization = KeyboardCapitalization.Words,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = host,
                        onValueChange = { host = it },
                        label = "Server",
                        placeholder = "nas.local",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.weight(1f),
                    )
                    FormField(
                        value = port,
                        onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                        label = "Port",
                        placeholder = "445",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.width(96.dp),
                    )
                }
                FormField(
                    value = share,
                    onValueChange = { share = it },
                    label = "Share",
                    placeholder = "Movies",
                )
                FormField(
                    value = path,
                    onValueChange = { path = it },
                    label = "Folder in the share (optional)",
                    placeholder = "TV/Anime",
                )
                FormField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username (optional)",
                    supporting = "Leave empty for guest access",
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            NovaIcon(
                                if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                )
                FormField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = "Domain (optional)",
                    placeholder = "WORKGROUP",
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            checkedForm = form
                            actions.testSmb(form)
                        },
                        enabled = canSubmit,
                    ) {
                        Text("Test connection")
                    }
                    if (dialog.testing) {
                        Spacer(Modifier.width(16.dp))
                        IosSpinner(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                if (!dialog.testing && result != null) TestResultLine(result)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    checkedForm = form
                    actions.saveSmb(form)
                },
                enabled = canSubmit,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = actions::dismissDialog) { Text("Cancel") }
        },
    )
}

@Composable
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, maxLines = 1) }
        } else {
            null
        },
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
    )
}

/** "" means the connection worked; anything else is the error to show. */
@Composable
internal fun TestResultLine(result: String) {
    val ok = result.isEmpty()
    val color = if (ok) NovaColors.Mint else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NovaIcon(
            if (ok) R.drawable.ic_check_circle else R.drawable.ic_error,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (ok) "Connected" else result,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}
