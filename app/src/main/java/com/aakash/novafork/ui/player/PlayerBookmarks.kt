package com.aakash.novafork.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.model.BookmarkUi
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.theme.NovaColors

/**
 * The playing video's bookmarks: "Add Bookmark" saves the position it is at, each saved spot
 * jumps there when tapped and is deleted by swiping it away.
 */
@Composable
internal fun BookmarkOptions(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = actions::addBookmark)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Add Bookmark at ${Formats.clock(state.positionMs)}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (state.bookmarks.isEmpty()) {
            Text(
                text = "No bookmarks in this video yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.bookmarks, key = { it.positionMs }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onJump = {
                            onDismiss()
                            actions.seekTo(bookmark.positionMs)
                        },
                        onDelete = { actions.removeBookmark(bookmark.positionMs) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: BookmarkUi, onJump: () -> Unit, onDelete: () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(Color(0xFFFF453A))
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete bookmark", tint = Color.White)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(NovaColors.PlayerSurface)
                .clickable(onClickLabel = "Go to ${Formats.clock(bookmark.positionMs)}", role = Role.Button, onClick = onJump)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_bookmark_fill),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = bookmark.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (bookmark.label != Formats.clock(bookmark.positionMs)) {
                Text(
                    text = Formats.clock(bookmark.positionMs),
                    style = MaterialTheme.typography.bodyMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
