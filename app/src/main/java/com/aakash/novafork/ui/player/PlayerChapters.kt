package com.aakash.novafork.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.common.playerGlass
import com.aakash.novafork.ui.model.ChapterUi
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import kotlinx.coroutines.delay

/** The file's chapters, each with where it starts, the playing one ticked; a tap goes there. */
@Composable
internal fun ChapterOptions(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    val chapters = state.chapters
    val current = currentChapterIndex(chapters, state.positionMs)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(chapters, key = { index, chapter -> "$index:${chapter.startMs}" }) { index, chapter ->
            ChapterRow(
                chapter = chapter,
                playing = index == current,
                onClick = {
                    onDismiss()
                    actions.seekTo(chapter.startMs)
                },
            )
        }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterUi, playing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClickLabel = "Go to ${chapter.name}", role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The playing chapter's tick, and room for it on the others so the names line up.
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Formats.clock(chapter.startMs),
            style = MaterialTheme.typography.bodyMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
            color = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** The chapter [positionMs] is in; -1 before the first one, or with no chapters. */
internal fun currentChapterIndex(chapters: List<ChapterUi>, positionMs: Long): Int =
    chapters.indexOfLast { it.startMs <= positionMs }

/** Where chapter [index] ends: its own length when the file gives one, else the next one's start, else the end. */
internal fun chapterEndMs(chapters: List<ChapterUi>, index: Int, durationMs: Long): Long {
    val chapter = chapters[index]
    val next = chapters.getOrNull(index + 1)?.startMs
    return when {
        chapter.durationMs > 0L -> chapter.startMs + chapter.durationMs
        next != null -> next
        else -> durationMs
    }
}

/** What a chapter can be skipped as, Netflix's way. */
internal enum class ChapterSkip(val label: String) {
    INTRO("Skip Intro"),
    CREDITS("Skip Credits"),
}

private val IntroName = Regex("""\b(intro|opening|op)\b""", RegexOption.IGNORE_CASE)
private val RecapName = Regex("""\b(recap|previously)\b""", RegexOption.IGNORE_CASE)
private val CreditsName = Regex("""\b(credits|ending|outro|ed)\b""", RegexOption.IGNORE_CASE)

/**
 * The skip for chapter [index], told from its name alone (nothing is guessed without chapters):
 * an opening ("Intro", "Opening", "OP", "Opening Credits") is skipped to its end; credits
 * ("Credits", "Ending", "End Credits", "ED") among the last chapters go to what plays next.
 */
internal fun chapterSkip(chapters: List<ChapterUi>, index: Int, durationMs: Long): ChapterSkip? {
    if (chapters.size < 2) return null
    val chapter = chapters.getOrNull(index) ?: return null
    val nearEnd = index >= chapters.lastIndex - 1 || (durationMs > 0L && chapter.startMs >= durationMs * CREDITS_FROM)
    return when {
        IntroName.containsMatchIn(chapter.name) -> ChapterSkip.INTRO
        nearEnd && CreditsName.containsMatchIn(chapter.name) -> ChapterSkip.CREDITS
        else -> null
    }
}

/** Credits only count as such in the last part of the video. */
private const val CREDITS_FROM = 0.7

/**
 * Netflix's Skip Intro / Skip Credits: a glass capsule while a chapter named so plays. It shows
 * for the chapter's first [SKIP_SHOWN_MS], and again whenever the controls are up; never over Up
 * Next (which offers the next episode itself) or while locked. Skip Intro goes to the opening's
 * end; Skip Credits to the next video, or to the end when there is none.
 */
@Composable
internal fun ChapterSkipButton(
    state: PlayerUiState,
    actions: PlayerActions,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val index = currentChapterIndex(state.chapters, state.positionMs)
    val skip = if (state.locked || state.upNext != null || state.castingTo != null) {
        null
    } else {
        chapterSkip(state.chapters, index, state.durationMs)
    }
    // A chapter named as a recap ("Recap", "Previously on …") is skipped by itself, without a
    // button (owner: no interruptions). Not while locked or casting.
    val recap = index >= 0 && !state.locked && state.castingTo == null &&
        RecapName.containsMatchIn(state.chapters.getOrNull(index)?.name.orEmpty())
    LaunchedEffect(index, recap) {
        if (recap) actions.seekTo(chapterEndMs(state.chapters, index, state.durationMs))
    }
    var fresh by remember { mutableStateOf(false) }
    LaunchedEffect(index, skip) {
        fresh = skip != null
        if (skip != null) {
            delay(SKIP_SHOWN_MS)
            fresh = false
        }
    }
    val shown = rememberLatestNonNull(skip)
    AnimatedVisibility(
        visible = skip != null && (fresh || controlsVisible),
        modifier = modifier,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f),
        exit = fadeOut(tween(200)),
    ) {
        val label = shown?.label.orEmpty()
        Box(
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .playerGlass(RoundedCornerShape(percent = 50))
                .clickable(onClickLabel = label, role = Role.Button) {
                    when (shown) {
                        ChapterSkip.INTRO -> if (index >= 0) actions.seekTo(chapterEndMs(state.chapters, index, state.durationMs))
                        ChapterSkip.CREDITS -> if (state.hasNext) actions.playNext() else actions.seekTo(state.durationMs)
                        null -> Unit
                    }
                }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** How long Skip Intro / Skip Credits stays up on its own as its chapter begins. */
private const val SKIP_SHOWN_MS = 10_000L
