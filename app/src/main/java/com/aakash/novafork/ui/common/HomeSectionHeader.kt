package com.aakash.novafork.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens

/** Space between Home's sections (iOS: 28pt). */
val HomeSectionGap = 28.dp

/** Space between a Home section's header and its row. */
val HomeHeaderGap = 12.dp

/**
 * A Home section's header, as the iOS apps set it: the title in Title 2 (22sp bold) and, when the
 * whole list lies behind the row ([onOpen]), a smaller grey chevron after it. [gapBelow] is the
 * space before the row (12dp; less where the row brings padding of its own).
 */
@Composable
fun HomeSectionHeader(title: String, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null, gapBelow: Dp = HomeHeaderGap) {
    val dims = LocalNovaDimens.current
    val titleStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    Column(modifier) {
        if (onOpen != null) {
            Row(
                Modifier
                    // The tap area reaches 4dp past the text, which stays on the gutter.
                    .padding(start = dims.gutter - 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Open $title", onClick = onOpen)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                NovaIcon(
                    R.drawable.ic_chevron_right,
                    contentDescription = null,
                    tint = IosColors.SecondaryLabel,
                    modifier = Modifier.padding(start = 2.dp).size(18.dp),
                )
            }
        } else {
            Text(
                title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = dims.gutter, end = dims.gutter),
            )
        }
        Spacer(Modifier.height(gapBelow))
    }
}
