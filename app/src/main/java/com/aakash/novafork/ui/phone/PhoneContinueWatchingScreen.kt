package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.iosScrollIndicator
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.VideoCardUi

/**
 * Everything left partway (Home's "Continue Watching ›"), last played first: the same wide cards
 * as Home's row with what is left under each, full width on a phone and side by side on wider
 * screens.
 */
@Composable
internal fun PhoneContinueWatchingScreen(
    cards: List<VideoCardUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = "Continue Watching", onBack = actions::back)
        if (cards.isEmpty()) {
            CenteredScrollable {
                EmptyState(
                    icon = R.drawable.ic_logo,
                    title = "Nothing in progress",
                    body = "Videos you stop partway through show up here.",
                )
            }
        } else {
            val state = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(ContinueCardMinWidth),
                state = state,
                modifier = Modifier.fillMaxSize().iosScrollIndicator(state),
                contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 8.dp, bottom = EndOfListGap + LocalBottomBarSpace.current),
                horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        ContinueWatchingCard(card = card, actions = actions, width = maxWidth)
                    }
                }
            }
        }
    }
}

private val ContinueCardMinWidth = 300.dp
