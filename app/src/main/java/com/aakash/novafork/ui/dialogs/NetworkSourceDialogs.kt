package com.aakash.novafork.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.NetworkForms
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.sourceTypeIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.NetworkSourceForm
import com.aakash.novafork.ui.model.PlexServerUi
import com.aakash.novafork.ui.model.PlexSignInUi
import com.aakash.novafork.ui.model.ServerLibraryUi
import com.aakash.novafork.ui.model.UpnpServerUi

/** One kind of server the "Add network source" picker offers. */
private class SourceKind(val type: SourceType, val title: String, val body: String)

private val FileServerKinds = listOf(
    SourceKind(SourceType.SMB, "SMB / Windows share", "A NAS or a computer's shared folders"),
    SourceKind(SourceType.FTP, "FTP", "FTP, FTPS or FTPES server"),
    SourceKind(SourceType.SFTP, "SFTP", "Files over SSH"),
    SourceKind(SourceType.NFS, "NFS", "A NAS or Linux export"),
    SourceKind(SourceType.WEBDAV, "WebDAV", "Nextcloud, ownCloud or any WebDAV server"),
    SourceKind(SourceType.UPNP, "DLNA / UPnP", "Media servers found on this network"),
)

private val MediaServerKinds = listOf(
    SourceKind(SourceType.JELLYFIN, "Jellyfin", "Its libraries, artwork and watch progress"),
    SourceKind(SourceType.EMBY, "Emby", "Its libraries, artwork and watch progress"),
    SourceKind(SourceType.PLEX, "Plex", "Sign in with a code, or paste a token"),
)

/** "Add network source": the kinds of server, each going on to its own dialog. */
@Composable
internal fun AddSourceDialog(actions: AppActions) {
    AlertDialog(
        onDismissRequest = actions::dismissDialog,
        icon = { NovaIcon(R.drawable.ic_lan, contentDescription = null) },
        title = { Text("Add network source") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KindHeader("File servers")
                FileServerKinds.forEach { kind -> KindRow(kind, onClick = { actions.chooseSourceType(kind.type) }) }
                Spacer(Modifier.height(8.dp))
                KindHeader("Media servers")
                MediaServerKinds.forEach { kind -> KindRow(kind, onClick = { actions.chooseSourceType(kind.type) }) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions::dismissDialog) { Text("Cancel") }
        },
    )
}

@Composable
private fun KindHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun KindRow(kind: SourceKind, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeBadge(kind.type)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = kind.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: SourceType) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(sourceTypeIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/**
 * Add or edit an FTP, SFTP, NFS, WebDAV, DLNA (by address), Jellyfin, Emby or Plex source with
 * the fields its type takes. As in [SmbServerDialog] the fields live here; the view model reports
 * tests, saves, the Jellyfin/Emby libraries and the Plex sign-in through [DialogUi.NetworkServer].
 */
@Composable
internal fun NetworkServerDialog(dialog: DialogUi.NetworkServer, actions: AppActions) {
    val initial = dialog.form
    val type = initial.type
    var name by remember(initial.id, type) { mutableStateOf(initial.name) }
    var address by remember(initial.id, type) { mutableStateOf(initial.address) }
    var port by remember(initial.id, type) { mutableStateOf(initial.port) }
    var path by remember(initial.id, type) { mutableStateOf(initial.path) }
    var username by remember(initial.id, type) { mutableStateOf(initial.username) }
    var password by remember(initial.id, type) { mutableStateOf(initial.password) }
    var token by remember(initial.id, type) { mutableStateOf(initial.token) }
    // Null = every library (also the ones added to the server later).
    var chosen by remember(initial.id, type) { mutableStateOf(initial.libraryIds.toSet().ifEmpty { null }) }

    val connection = NetworkSourceForm(
        id = initial.id,
        type = type,
        name = name,
        address = address,
        port = port,
        path = path,
        username = username,
        password = password,
        token = token,
    )
    // The result line belongs to the values that were tested; editing a field makes it stale.
    // A dialog that opens with a result shows it for the values it opened with.
    var checked by remember(initial.id, type) {
        mutableStateOf(if (dialog.testResult != null) initial.copy(libraryIds = emptyList()) else null)
    }
    val result = dialog.testResult.takeIf { connection == checked }
    val libraries = dialog.libraries
    val choosesLibraries = type == SourceType.JELLYFIN || type == SourceType.EMBY
    val noLibrary = choosesLibraries && noLibraryChosen(chosen, libraries)
    val form = connection.copy(libraryIds = if (choosesLibraries) chosenLibraryIds(chosen, libraries) else emptyList())
    val canTest = address.isNotBlank() && !dialog.testing
    val canSave = canTest && !noLibrary && (type != SourceType.PLEX || token.isNotBlank())

    AlertDialog(
        onDismissRequest = actions::dismissDialog,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { NovaIcon(sourceTypeIcon(type), contentDescription = null) },
        title = { Text(dialogTitle(type, adding = initial.id == null)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FormField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Display name (optional)",
                    placeholder = namePlaceholder(type),
                    capitalization = KeyboardCapitalization.Words,
                )
                if (type == SourceType.PLEX) {
                    PlexSignInPanel(signIn = dialog.plexSignIn, form = form, actions = actions)
                    Text(
                        text = "Or connect with a token",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = address,
                        onValueChange = { address = it },
                        label = addressLabel(type),
                        placeholder = addressPlaceholder(type),
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.weight(1f),
                    )
                    if (NetworkForms.takesPort(type)) {
                        FormField(
                            value = port,
                            onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                            label = "Port",
                            placeholder = portPlaceholder(type),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.width(96.dp),
                        )
                    }
                }
                if (NetworkForms.takesPath(type)) {
                    FormField(
                        value = path,
                        onValueChange = { path = it },
                        label = "Folder (optional)",
                        placeholder = "Movies",
                    )
                }
                if (NetworkForms.takesLogin(type)) {
                    FormField(
                        value = username,
                        onValueChange = { username = it },
                        label = if (choosesLibraries) "Username" else "Username (optional)",
                        supporting = if (choosesLibraries) null else "Leave empty for anonymous access",
                    )
                    SecretField(value = password, onValueChange = { password = it }, label = "Password")
                }
                if (type == SourceType.PLEX) {
                    SecretField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = "Token",
                        supporting = "Your X-Plex-Token",
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            checked = connection
                            actions.testNetworkSource(form)
                        },
                        enabled = canTest,
                    ) {
                        Text("Test connection")
                    }
                    if (dialog.testing) {
                        Spacer(Modifier.width(16.dp))
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                if (!dialog.testing && result != null) TestResultLine(result)
                if (choosesLibraries) {
                    LibraryChoice(
                        libraries = libraries,
                        storedCount = initial.libraryIds.size,
                        isChosen = { library -> chosen?.contains(library.id) ?: true },
                        onChoose = { library, on -> chosen = toggledLibraries(chosen, libraries.orEmpty(), library.id, on) },
                        noneChosen = noLibrary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    checked = connection
                    actions.saveNetworkSource(form)
                },
                enabled = canSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = actions::dismissDialog) { Text("Cancel") }
        },
    )
}

private fun dialogTitle(type: SourceType, adding: Boolean): String {
    val noun = if (type == SourceType.NFS) "share" else "server"
    return "${if (adding) "Add" else "Edit"} ${NetworkForms.typeLabel(type)} $noun"
}

private fun namePlaceholder(type: SourceType): String = when (type) {
    SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX -> "Home server"
    SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP, SourceType.SFTP,
    SourceType.NFS, SourceType.UPNP, SourceType.WEBDAV,
    -> "Living room NAS"
}

private fun addressLabel(type: SourceType): String = when (type) {
    SourceType.UPNP -> "Description URL or UDN"
    SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX -> "Server URL"
    SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP, SourceType.SFTP,
    SourceType.NFS,
    -> "Server"
}

private fun addressPlaceholder(type: SourceType): String = when (type) {
    SourceType.FTP -> "nas.local or ftps://nas.local"
    SourceType.UPNP -> "http://192.168.1.9:8200/rootDesc.xml"
    SourceType.WEBDAV -> "https://cloud.example.com/remote.php/dav"
    SourceType.JELLYFIN, SourceType.EMBY -> "http://192.168.1.5:8096"
    SourceType.PLEX -> "http://192.168.1.5:32400"
    SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.SFTP, SourceType.NFS,
    -> "nas.local"
}

private fun portPlaceholder(type: SourceType): String = when (type) {
    SourceType.SFTP -> "22"
    SourceType.WEBDAV -> "443"
    SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP, SourceType.NFS,
    SourceType.UPNP, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
    -> "21"
}

/** What to save: empty (every library) when all listed ones are chosen. */
private fun chosenLibraryIds(chosen: Set<String>?, libraries: List<ServerLibraryUi>?): List<String> = when {
    chosen == null -> emptyList()
    libraries == null -> chosen.toList()
    libraries.all { it.id in chosen } -> emptyList()
    else -> libraries.map { it.id }.filter { it in chosen }
}

/** Unticking every library (or keeping only ones the server no longer has) leaves nothing to scan. */
private fun noLibraryChosen(chosen: Set<String>?, libraries: List<ServerLibraryUi>?): Boolean = when {
    chosen == null -> false
    chosen.isEmpty() -> true
    libraries == null -> false
    else -> libraries.isNotEmpty() && libraries.none { it.id in chosen }
}

private fun toggledLibraries(chosen: Set<String>?, libraries: List<ServerLibraryUi>, id: String, on: Boolean): Set<String>? {
    val all = libraries.map { it.id }
    val current = chosen ?: all.toSet()
    val next = if (on) current + id else current - id
    return if (all.isNotEmpty() && all.all { it in next }) null else next
}

@Composable
private fun LibraryChoice(
    libraries: List<ServerLibraryUi>?,
    storedCount: Int,
    isChosen: (ServerLibraryUi) -> Boolean,
    onChoose: (ServerLibraryUi, Boolean) -> Unit,
    noneChosen: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Libraries", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp))
        when {
            libraries == null -> Text(
                text = if (storedCount == 0) {
                    "All of them. Test the connection to choose."
                } else {
                    "$storedCount chosen. Test the connection to change them."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            libraries.isEmpty() -> Text(
                text = "The server shows this user no video libraries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> libraries.forEach { library ->
                val on = isChosen(library)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = { onChoose(library, it) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(10.dp))
                    Text(library.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (noneChosen) {
            Text(
                text = "Choose at least one library",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Plex sign-in without a token: a code to enter at plex.tv/link, then the account's servers. */
@Composable
private fun PlexSignInPanel(signIn: PlexSignInUi?, form: NetworkSourceForm, actions: AppActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val error = signIn?.error
            val servers = signIn?.servers
            when {
                signIn == null -> {
                    Text("Sign in with plex.tv", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Get a code and enter it at plex.tv/link on any device signed in to Plex.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = actions::startPlexSignIn) { Text("Sign in with a code") }
                }
                error != null -> {
                    TestResultLine(error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = actions::startPlexSignIn) { Text("Get a new code") }
                        TextButton(onClick = actions::cancelPlexSignIn) { Text("Cancel") }
                    }
                }
                servers != null -> {
                    Text("Pick a server", style = MaterialTheme.typography.titleSmall)
                    if (servers.isEmpty()) {
                        Text(
                            text = "This Plex account has no servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    servers.forEach { server ->
                        PlexServerRow(server = server, onClick = { actions.pickPlexServer(server.id, form) })
                    }
                    TextButton(onClick = actions::cancelPlexSignIn) { Text("Cancel") }
                }
                else -> {
                    val link = signIn.linkUrl.substringAfter("://")
                    Text("Enter this code at $link", style = MaterialTheme.typography.bodyMedium)
                    val code = signIn.code
                    if (code == null) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Waiting for plex.tv…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { actions.openWebPage(signIn.linkUrl) }) { Text("Open $link") }
                        TextButton(onClick = actions::cancelPlexSignIn) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlexServerRow(server: PlexServerUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = server.reachable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NovaIcon(sourceTypeIcon(SourceType.PLEX), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = server.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A password or token: hidden until the eye is tapped. */
@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, supporting: String? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                NovaIcon(
                    if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    contentDescription = if (visible) "Hide ${label.lowercase()}" else "Show ${label.lowercase()}",
                )
            }
        },
    )
}

/** DLNA/UPnP servers that answered on this network; tapping one adds it. */
@Composable
internal fun UpnpServersDialog(dialog: DialogUi.UpnpServers, actions: AppActions) {
    AlertDialog(
        onDismissRequest = actions::dismissDialog,
        icon = { NovaIcon(sourceTypeIcon(SourceType.UPNP), contentDescription = null) },
        title = { Text("DLNA servers") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (dialog.searching) {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Looking for media servers on this network…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                dialog.servers.forEach { server ->
                    UpnpServerRow(server = server, onClick = { actions.addUpnpServer(server) })
                }
                val error = dialog.error
                if (error != null) {
                    TestResultLine(error)
                } else if (!dialog.searching && dialog.servers.isEmpty()) {
                    Text(
                        text = "No DLNA server answered. Check that it is on and on this network, or enter its address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions::discoverUpnpServers, enabled = !dialog.searching) { Text("Search again") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { actions.showDialog(DialogUi.NetworkServer(NetworkSourceForm(type = SourceType.UPNP))) },
                ) {
                    Text("Enter address")
                }
                TextButton(onClick = actions::dismissDialog) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun UpnpServerRow(server: UpnpServerUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(SourceType.UPNP)
            Spacer(Modifier.width(14.dp))
            Text(
                text = server.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            NovaIcon(R.drawable.ic_add, contentDescription = "Add ${server.name}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
