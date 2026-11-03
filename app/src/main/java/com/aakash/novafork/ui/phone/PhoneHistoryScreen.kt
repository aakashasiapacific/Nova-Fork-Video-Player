package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.SwipeToDelete
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.HistoryDayUi
import java.text.DateFormat
import java.util.Date

/**
 * Everything that has been played (Home's Library list, "History"), newest first under day
 * headings. Swiping a row left only takes it off this list: the file, its resume point and its
 * watched mark are not touched, and playing it again brings it back.
 */
@Composable
internal fun PhoneHistoryScreen(
    entries: List<HistoryDayUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = "History", onBack = actions::back)
        if (entries.isEmpty()) {
            CenteredScrollable {
                PhoneEmptyLine("No history yet")
            }
        } else {
            val state = rememberLazyListState()
            val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
            ) {
                entries.forEach { day ->
                    item(key = "day:${day.title}") {
                        SectionHeader(
                            title = day.title,
                            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(day.entries, key = { "video:${it.card.id}" }) { entry ->
                        SwipeToDelete(onDelete = { actions.hideFromHistory(entry.card.id, entry.playedAt) }, label = "Remove") {
                            PhoneVideoRow(
                                card = entry.card,
                                onClick = { actions.openVideo(entry.card.id) },
                                trailing = {
                                    Text(
                                        text = timeFormat.format(Date(entry.playedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IosColors.SecondaryLabel,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
