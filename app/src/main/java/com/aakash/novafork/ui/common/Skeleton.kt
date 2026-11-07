package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.theme.Spacing

/**
 * True until the library has been read once (AppUiState.libraryLoaded false). Together with a
 * running scan it decides whether an empty page shows placeholders instead of "nothing here":
 * a library that is empty after loading never shows them.
 */
val LocalLibraryLoading = staticCompositionLocalOf { false }

/** The placeholder grey (secondarySystemGroupedBackground). */
val SkeletonColor = Color(0xFF1C1C1E)

/** A soft grey placeholder shape. Still, not shimmering: nothing to distract while it waits. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Spacer(modifier.clip(shape).background(SkeletonColor))
}

/** A short bar standing in for a line of caption text. */
@Composable
fun SkeletonBar(width: Dp, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    SkeletonBox(modifier.width(width).height(height), RoundedCornerShape(50))
}

/** A poster card's placeholder: the 2:3 art, then a title bar and a shorter caption bar. */
@Composable
fun PosterSkeleton(modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp) {
    Column(modifier) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f), RoundedCornerShape(cornerRadius))
        Spacer(Modifier.height(Spacing.sm))
        SkeletonBar(width = 84.dp)
        Spacer(Modifier.height(Spacing.xs + 2.dp))
        SkeletonBar(width = 48.dp, height = 10.dp)
    }
}

/** A library grid's placeholders, in the grid's own columns and gutters. Does not scroll. */
@Composable
fun PosterSkeletonGrid(modifier: Modifier = Modifier, topPadding: Dp = Spacing.sm, cells: Int = 18) {
    val dims = LocalNovaDimens.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dims.gridMinCell),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = topPadding),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(List(cells) { it }) { PosterSkeleton(cornerRadius = dims.cardRadius) }
    }
}

/** Home's placeholders: a wide card where the hero goes, then two rows of posters under title bars. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxSize().statusBarsPadding().padding(top = Spacing.lg)) {
        SkeletonBox(
            Modifier.padding(horizontal = dims.gutter).fillMaxWidth().aspectRatio(16f / 10f),
            RoundedCornerShape(dims.cardRadius),
        )
        repeat(2) {
            Spacer(Modifier.height(dims.sectionSpacing))
            SkeletonBar(width = 140.dp, modifier = Modifier.padding(horizontal = dims.gutter), height = 18.dp)
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.padding(horizontal = dims.gutter),
                horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
            ) {
                repeat(3) { PosterSkeleton(Modifier.width(dims.posterWidth), dims.cardRadius) }
            }
        }
    }
}
