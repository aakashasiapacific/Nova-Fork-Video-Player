package com.aakash.novafork.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A hero's title as Apple TV shows it: the title's logo artwork (TMDB) when it has one, fitted
 * into [maxWidthFraction] × [logoHeight] and sitting on the bottom of that box, else the title as
 * bold white text. A logo that fails to load falls back to the text too.
 */
@Composable
fun HeroTitle(
    title: String,
    logoUrl: String?,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    logoHeight: Dp = 96.dp,
    maxWidthFraction: Float = 0.72f,
    textAlign: TextAlign = TextAlign.Center,
    logoAlignment: Alignment = Alignment.BottomCenter,
) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl != null && !failed) {
        Box(modifier.fillMaxWidth(), contentAlignment = logoAlignment) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = logoAlignment,
                onError = { failed = true },
                modifier = Modifier
                    .fillMaxWidth(maxWidthFraction)
                    .height(logoHeight),
            )
        }
    } else {
        Text(
            text = title,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}
