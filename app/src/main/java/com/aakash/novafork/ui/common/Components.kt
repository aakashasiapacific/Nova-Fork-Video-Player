package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaType
import java.util.Locale

/** Icon from res/drawable (Material Icons Round, see docs/ARCHITECTURE.md for the list). */
@Composable
fun NovaIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painter = painterResource(res), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

/**
 * "★ 7.8". On artwork ([onImage]) a small dark capsule with white text; elsewhere the outlined
 * RatingBadge. Hidden when [rating] is null or 0. New code: use RatingBadge.
 */
@Composable
fun RatingPill(rating: Float?, modifier: Modifier = Modifier, onImage: Boolean = false) {
    if (rating == null || rating.isNaN() || rating <= 0f) return
    if (!onImage) {
        RatingBadge(rating, modifier)
        return
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(NovaGlyphs.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
        Text(
            text = String.format(Locale.US, "%.1f", rating),
            style = NovaType.caption2Semibold,
            color = Color.White,
        )
    }
}

/**
 * The Continue Watching progress capsule for the bottom of a card's artwork: white on white 20%,
 * 6dp from the bottom with 12dp side insets. Draws nothing for null/0. New code: ProgressBar.
 */
@Composable
fun ProgressStrip(progress: Float?, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    if (progress == null || progress.isNaN() || progress <= 0f) return
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
    ) {
        ProgressBar(progress = progress, height = height)
    }
}

/** Small round check on watched items (white check on a dark translucent circle). */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(NovaGlyphs.Check, contentDescription = "Watched", tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

/** Joins non-blank parts with " · ". */
fun metaLine(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

/**
 * Section title (Title 2 bold). With [onAction] the whole header is tappable and shows "›" after
 * the title ([actionLabel] becomes the accessibility label). The caller pads it; RowHeader is the
 * padded Apple TV row header for new code.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val click = if (onAction != null) Modifier.novaClickable(onClickLabel = actionLabel, onClick = onAction) else Modifier
    Box(modifier.fillMaxWidth()) {
        Row(
            click.semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = NovaType.title2,
                color = NovaColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onAction != null) {
                Spacer(Modifier.width(4.dp))
                Icon(NovaGlyphs.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp), tint = NovaColors.secondaryLabel)
            }
        }
    }
}

/**
 * iOS "content unavailable" view: a large secondary glyph, Title 2 title, Subhead body, a white
 * capsule for the main action and a blue text button for the second one.
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier
            .widthIn(max = 420.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = NovaColors.secondaryLabel, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = NovaType.title2,
            color = NovaColors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = NovaType.subhead,
            color = NovaColors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryCapsuleButton(
                text = actionLabel,
                onClick = onAction,
                playGlyph = false,
                height = 44.dp,
                containerColor = NovaColors.label,
                contentColor = NovaColors.background,
            )
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(4.dp))
            TextAction(text = secondaryLabel, onClick = onSecondary)
        }
    }
}

/**
 * Small status line while the library scans files ([ScanUi.running]) or fetches posters and
 * details ([ScanUi.fetchingArtwork]): an activity indicator, the label in Footnote secondary,
 * and a thin bar when the progress is known. Nothing when idle.
 */
@Composable
fun ScanBanner(scan: ScanUi, modifier: Modifier = Modifier) {
    if (!scan.running && !scan.fetchingArtwork) return
    val p = scan.progress
    val label = scan.label
    Column(modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActivityIndicator(size = 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = NovaType.footnote,
                    color = NovaColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (p == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                ActivityIndicator(size = 14.dp)
            }
        }
        if (p != null) {
            ProgressBar(
                progress = p,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                height = 2.dp,
                trackColor = NovaColors.fill,
                color = NovaColors.label,
            )
        }
    }
}
