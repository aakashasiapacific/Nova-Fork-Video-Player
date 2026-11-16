package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.CastUi

/** A grey "Trailer" pill; tapping it opens [url] (YouTube app or browser). */
@Composable
fun TrailerPill(url: String, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = { onOpen(url) },
        shape = CircleShape,
        color = Color(0xFF2C2C2E),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Box(Modifier.heightIn(min = 34.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text("Trailer", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A small grey line such as "Downloaded" or "4 of 8 watched". */
@Composable
fun GreyCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = IosColors.SecondaryLabel,
        modifier = modifier,
    )
}

/**
 * The "Cast" row of a movie or show page: round photos with the name and character under each.
 * Tapping a person calls [onPerson] with the name. Nothing is drawn for an empty list.
 */
@Composable
fun CastRow(
    cast: List<CastUi>,
    onPerson: (String) -> Unit,
    gutter: Dp,
    modifier: Modifier = Modifier,
    photoSize: Dp = 84.dp,
) {
    if (cast.isEmpty()) return
    Column(modifier) {
        SectionHeader(title = "Cast", modifier = Modifier.padding(start = gutter, end = gutter, bottom = 10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(cast, key = { it.name }) { person ->
                Column(
                    Modifier
                        .width(photoSize + 12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Open ${person.name}") { onPerson(person.name) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtImage(
                        candidates = listOfNotNull(person.photoUrl),
                        contentDescription = person.name,
                        modifier = Modifier.size(photoSize).clip(CircleShape),
                        placeholder = {
                            Box(Modifier.fillMaxSize().background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
                                Text(
                                    person.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = IosColors.SecondaryLabel,
                                )
                            }
                        },
                    )
                    Text(
                        person.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    val character = person.character
                    if (!character.isNullOrBlank()) {
                        Text(
                            character,
                            style = MaterialTheme.typography.labelSmall,
                            color = IosColors.SecondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The "Rename in Library" menu entries: "Rename in Library…" ([onRename] opens the name field) and,
 * for a title already renamed, "Use Original Title". Only the name in the app changes.
 */
@Composable
fun RenameInLibraryMenuItems(
    titleKey: String,
    customised: Boolean,
    close: () -> Unit,
    onRename: () -> Unit,
    onReset: () -> Unit,
) {
    if (titleKey.isBlank()) return
    NovaMenuItem(
        text = { Text("Rename in Library…") },
        leadingIcon = { NovaIcon(R.drawable.ic_edit, contentDescription = null) },
        onClick = {
            close()
            onRename()
        },
    )
    if (customised) {
        NovaMenuItem(
            text = { Text("Use Original Title") },
            leadingIcon = { NovaIcon(R.drawable.ic_refresh, contentDescription = null) },
            onClick = {
                close()
                onReset()
            },
        )
    }
}

/** The same naming alert as "Rename File", for the title shown in the library; the file is untouched. */
@Composable
fun RenameTitleDialog(
    title: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(title) { mutableStateOf(title) }
    var showError by rememberSaveable { mutableStateOf(false) }
    fun submit() {
        if (name.isNotBlank()) {
            onRename(name.trim())
            onDismiss()
        } else {
            showError = true
        }
    }
    NovaAlertDialog(
        form = true,
        onDismissRequest = onDismiss,
        title = { Text("Rename in Library") },
        text = {
            NovaTextField(
                value = name,
                onValueChange = {
                    name = it
                    showError = false
                },
                isError = showError,
                supportingText = { Text(if (showError) "Enter a name" else "Only the name in Nova changes, not the file") },
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
