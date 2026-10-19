package com.aakash.novafork.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.PermissionUi

/** What to tell the user about device storage access, and the label of the button that fixes it. */
@Immutable
data class DeviceAccessNotice(val title: String, val body: String, val allowLabel: String)

/**
 * The notice for device storage Nova can't scan (no access to videos) or sees only partly
 * (Android 14+ limited access); null when every video is visible or device scanning is off.
 */
fun deviceAccessNotice(permission: PermissionUi): DeviceAccessNotice? = when {
    !permission.needsAttention -> null
    !permission.granted -> DeviceAccessNotice(
        title = "Nova can't see the videos on this device",
        body = "Camera videos, downloads, screen recordings and messaging-app videos stay hidden until " +
            "you allow access to photos and videos.",
        allowLabel = "Allow Access",
    )
    permission.partial -> DeviceAccessNotice(
        title = "Nova sees only the videos you picked",
        body = "Other camera videos, downloads and screen recordings stay hidden. Allow access to all " +
            "photos and videos to see them.",
        allowLabel = "Allow All",
    )
    else -> null
}

/**
 * A card that says why device videos are missing, with the permission prompt ([onAllow]) and the
 * app's page in system settings ([onOpenSettings]) as ways to fix it. Phone and tablet.
 */
@Composable
fun DeviceAccessCard(
    notice: DeviceAccessNotice,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                NovaIcon(
                    R.drawable.ic_visibility_off,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(notice.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        notice.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenSettings) { Text("App Settings") }
                Button(onClick = onAllow) { Text(notice.allowLabel) }
            }
        }
    }
}

/**
 * Device storage opened with nothing in it: says whether access is missing, the last scan
 * failed ([error]) or Android listed no videos, each with a way forward.
 */
@Composable
fun DeviceStorageEmptyState(
    permission: PermissionUi,
    error: String?,
    onRescan: () -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = deviceAccessNotice(permission)
    when {
        notice != null -> EmptyState(
            icon = R.drawable.ic_visibility_off,
            title = notice.title,
            body = notice.body,
            modifier = modifier,
            actionLabel = notice.allowLabel,
            onAction = onAllow,
            secondaryLabel = "App Settings",
            onSecondary = onOpenSettings,
        )
        !error.isNullOrBlank() -> EmptyState(
            icon = R.drawable.ic_error,
            title = "Couldn't scan device storage",
            body = error,
            modifier = modifier,
            actionLabel = "Rescan",
            onAction = onRescan,
            secondaryLabel = "Library diagnostics",
            onSecondary = onDiagnostics,
        )
        else -> EmptyState(
            icon = R.drawable.ic_phone_android,
            title = "No videos found on this device",
            body = "Android's media library listed no videos Nova can show. Rescan, or open Library " +
                "diagnostics to see what Nova finds and share it.",
            modifier = modifier,
            actionLabel = "Rescan",
            onAction = onRescan,
            secondaryLabel = "Library diagnostics",
            onSecondary = onDiagnostics,
        )
    }
}
