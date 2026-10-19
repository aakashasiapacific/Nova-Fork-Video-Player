package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.model.Artwork

private val BackdropHeight = 280.dp

/** How far the details poster reaches up into the backdrop. */
private val PosterOverlap = 64.dp

/** 280dp, but at most half the page so a phone in landscape still shows the title. */
internal fun detailsBackdropHeight(pageHeight: Dp): Dp = BackdropHeight.coerceAtMost(pageHeight * 0.5f)

/**
 * Backdrop that melts into the page, with the poster overlapping its bottom edge and the title
 * block beside the poster.
 */
@Composable
internal fun PhoneDetailsHeader(
    artwork: Artwork,
    title: String,
    meta: String,
    rating: Float?,
    genres: List<String>,
    modifier: Modifier = Modifier,
    originalTitle: String? = null,
    backdropHeight: Dp = BackdropHeight,
) {
    val dims = LocalNovaDimens.current
    val posterShape = RoundedCornerShape(10.dp)
    Box(modifier.fillMaxWidth()) {
        BackdropArt(
            artwork = artwork,
            modifier = Modifier
                .fillMaxWidth()
                .height(backdropHeight)
                .bottomScrim(MaterialTheme.colorScheme.background, startFraction = 0.3f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = dims.gutter, end = dims.gutter, top = backdropHeight - PosterOverlap),
        ) {
            Box(
                Modifier
                    .width(dims.detailPosterWidth)
                    .aspectRatio(2f / 3f)
                    .shadow(12.dp, posterShape)
                    .clip(posterShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), posterShape),
            ) {
                PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = PosterOverlap + 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!originalTitle.isNullOrBlank() && originalTitle != title) {
                    Text(
                        text = originalTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (rating != null && rating > 0f) {
                    Spacer(Modifier.height(8.dp))
                    RatingPill(rating)
                }
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = genres.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Icon button on a dark translucent circle so it stays visible over any artwork. */
@Composable
internal fun ScrimIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
        ),
    ) {
        NovaIcon(icon, contentDescription = contentDescription)
    }
}

/**
 * Back button and optional overflow menu floating over the details page. [menu] receives a
 * `close` callback for its items.
 */
@Composable
internal fun PhoneDetailsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            // Keeps the status bar icons readable over bright artwork and scrolled content.
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrimIconButton(R.drawable.ic_arrow_back, contentDescription = "Back", onClick = onBack)
        Spacer(Modifier.weight(1f))
        if (menu != null) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                ScrimIconButton(R.drawable.ic_more_vert, contentDescription = "More options", onClick = { expanded = true })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    menu { expanded = false }
                }
            }
        }
    }
}

/** Shown while details for the current route are loading. */
@Composable
internal fun PhoneDetailsLoading(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        LoadingBox()
        PhoneDetailsTopBar(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}

/** Tagline and an overview clamped to a few lines with a More/Less toggle. */
@Composable
internal fun OverviewBlock(
    tagline: String?,
    overview: String?,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 3,
) {
    if (tagline.isNullOrBlank() && overview.isNullOrBlank()) return
    Column(modifier.fillMaxWidth()) {
        if (!tagline.isNullOrBlank()) {
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!overview.isNullOrBlank()) {
            var expanded by rememberSaveable(overview) { mutableStateOf(false) }
            var overflows by remember(overview) { mutableStateOf(false) }
            val canToggle = expanded || overflows
            Column(
                Modifier.clickable(
                    enabled = canToggle,
                    onClickLabel = if (expanded) "Show less" else "Show more",
                    onClick = { expanded = !expanded },
                ),
            ) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (!expanded) overflows = result.hasVisualOverflow },
                )
                if (canToggle) {
                    Text(
                        text = if (expanded) "Less" else "More",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Label/value line for the file facts card. */
@Composable
internal fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
