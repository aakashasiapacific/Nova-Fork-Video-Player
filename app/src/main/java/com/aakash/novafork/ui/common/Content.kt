package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaType

/*
 * Text blocks of the details pages: the clamped description with "MORE", the About card and the
 * Information grid. Phone and tablet details both use them, with their own sizes and placement.
 */

/**
 * A description clamped to [collapsedMaxLines] with a bold white "MORE" at the end of the last
 * line when it does not fit (the text under it fades into [fadeColor], the colour behind the
 * text: Color.Black over artwork). Tapping expands it in place, or calls [onMore] when set (e.g.
 * to show the full text in a GlassSheet, as the Apple TV app does).
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    style: TextStyle = NovaType.subhead,
    color: Color = NovaColors.secondaryLabel,
    moreColor: Color = NovaColors.label,
    fadeColor: Color = NovaColors.background,
    onMore: (() -> Unit)? = null,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    val tappable = overflows || expanded
    val click = if (tappable) {
        Modifier.novaClickable(
            onClickLabel = if (expanded) "Show less" else "Show more",
            pressScale = 1f,
            pressAlpha = 0.6f,
        ) {
            if (onMore != null && !expanded) onMore() else expanded = !expanded
        }
    } else {
        Modifier
    }
    Box(modifier.then(click)) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Clip,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows && !expanded) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor))),
                )
                Text(
                    text = "MORE",
                    style = style.copy(
                        fontSize = NovaType.smallCaps.fontSize,
                        letterSpacing = NovaType.smallCaps.letterSpacing,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = moreColor,
                    maxLines = 1,
                    modifier = Modifier
                        .background(fadeColor)
                        .padding(start = 2.dp),
                )
            }
        }
    }
}

/**
 * The details "About" card: a cell-coloured card with the form factor's card radius, Headline
 * [title], optional Footnote secondary [subtitle] (genres) and the full [body] in Subhead
 * secondary. [onClick] makes the card pressable (e.g. to open the text in a sheet).
 */
@Composable
fun AboutCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    bodyMaxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
) {
    val click = if (onClick != null) Modifier.novaClickable(onClick = onClick) else Modifier
    Column(
        modifier
            .then(click)
            .clip(RoundedCornerShape(LocalNovaDimens.current.cardRadius))
            .background(NovaColors.cell)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = NovaType.headline,
            color = NovaColors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = NovaType.footnote,
                color = NovaColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = NovaType.subhead,
                color = NovaColors.secondaryLabel,
                maxLines = bodyMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The details "Information" section: key/value pairs in [columns] columns (phone 2, iPad 3),
 * each a Footnote semibold key ("Released") over its Footnote secondary value ("2024"). Entries
 * with a blank value are left out. Fills the width it is given; pad it with the page gutter.
 */
@Composable
fun InfoGrid(
    entries: List<Pair<String, String?>>,
    columns: Int,
    modifier: Modifier = Modifier,
    columnSpacing: Dp = 20.dp,
    rowSpacing: Dp = 16.dp,
    keyColor: Color = NovaColors.label,
    valueColor: Color = NovaColors.secondaryLabel,
) {
    val cells = entries.filter { !it.second.isNullOrBlank() }
    if (cells.isEmpty()) return
    val perRow = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        cells.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(columnSpacing)) {
                row.forEach { (key, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(text = key, style = NovaType.footnoteSemibold, color = keyColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(text = value.orEmpty(), style = NovaType.footnote, color = valueColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
