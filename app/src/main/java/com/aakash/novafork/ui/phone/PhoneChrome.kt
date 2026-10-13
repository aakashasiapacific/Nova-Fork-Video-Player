package com.aakash.novafork.ui.phone

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.common.ActivityIndicator
import com.aakash.novafork.ui.common.AlertAction
import com.aakash.novafork.ui.common.AlertActionStyle
import com.aakash.novafork.ui.common.BackButton
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.GlassMenu
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.GlassNavBar
import com.aakash.novafork.ui.common.GlassPage
import com.aakash.novafork.ui.common.IosAlert
import com.aakash.novafork.ui.common.NavBarDefaults
import com.aakash.novafork.ui.common.NovaPullToRefreshBox
import com.aakash.novafork.ui.common.contextLift
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaMotion
import kotlinx.coroutines.delay

/*
 * Pieces every phone page shares: the large-title page frame, pull-to-refresh, context menus with
 * the dimmed background, the remove-source alert and the loading / not-available pages.
 */

// ───────────────────────────── Context menus ─────────────────────────────

/**
 * The dim behind an open context menu (iOS dims everything except the lifted item). One per
 * PhoneApp; the item that opens a menu cuts its own rounded rect out of the dim.
 */
@Stable
internal class PhoneContextDim {
    /** The lifted item's bounds in root coordinates; null = no dim. */
    var target by mutableStateOf<Rect?>(null)
        private set
    var radiusPx by mutableFloatStateOf(0f)
        private set
    private var owner: Any? = null

    fun show(bounds: Rect, cornerRadiusPx: Float): Any {
        val token = Any()
        owner = token
        radiusPx = cornerRadiusPx
        target = bounds
        return token
    }

    fun hide(token: Any) {
        if (owner === token) {
            owner = null
            target = null
        }
    }
}

internal val LocalPhoneContextDim = staticCompositionLocalOf<PhoneContextDim?> { null }

/** Draws the context-menu dim over the whole phone UI, leaving the lifted item clear. */
@Composable
internal fun PhoneContextDimOverlay(dim: PhoneContextDim, modifier: Modifier = Modifier) {
    val scrim = NovaColors.scrim
    val target = dim.target
    val alpha by animateFloatAsState(if (target != null) 1f else 0f, NovaMotion.fade(NovaMotion.FastMs), label = "contextDim")
    // Keeps the hole in place while the dim fades out.
    var shown by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(target) { if (target != null) shown = target }
    var origin by remember { mutableStateOf(Offset.Zero) }
    if (alpha <= 0f) return
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    val hole = shown
                    if (hole != null) {
                        val lifted = hole.translate(-origin.x, -origin.y).scaledAroundCenter(NovaMotion.LiftScale)
                        addRoundRect(RoundRect(lifted, CornerRadius(dim.radiusPx * NovaMotion.LiftScale)))
                    }
                }
                drawPath(path, scrim.copy(alpha = scrim.alpha * alpha))
            },
    )
}

private fun Rect.scaledAroundCenter(scale: Float): Rect {
    val w = width * scale
    val h = height * scale
    return Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
}

/** Open/closed state of one item's context menu and where the item is on screen. */
@Stable
internal class PhoneMenuState {
    var open by mutableStateOf(false)

    /** Updated on every layout; read only when the menu opens, so it is not snapshot state. */
    var bounds: Rect = Rect.Zero
}

@Composable
internal fun rememberPhoneMenuState(): PhoneMenuState = remember { PhoneMenuState() }

/** Lifts the item while its menu is open and records its bounds for the dim's cut-out. */
@Composable
internal fun Modifier.phoneContextTarget(state: PhoneMenuState): Modifier =
    this
        .contextLift(state.open)
        .onGloballyPositioned { state.bounds = it.boundsInRoot() }

/**
 * The glass context menu of a card or row (call it inside the item's Box/Column so it anchors to
 * the item). Dims the rest of the screen while open.
 */
@Composable
internal fun PhoneContextMenu(
    state: PhoneMenuState,
    items: List<GlassMenuItem>,
    cornerRadius: Dp = 0.dp,
    alignEnd: Boolean = false,
    title: String? = null,
) {
    val dim = LocalPhoneContextDim.current
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val open = state.open && items.isNotEmpty()
    if (dim != null) {
        DisposableEffect(open) {
            val token = if (open) dim.show(state.bounds, radiusPx) else null
            onDispose { if (token != null) dim.hide(token) }
        }
    }
    GlassMenu(
        expanded = open,
        onDismissRequest = { state.open = false },
        items = items,
        alignEnd = alignEnd,
        title = title,
    )
}

/**
 * The long-press menu of a video: Play, Go to Details, Mark as Watched, Identify… Saved streams
 * can also be removed ([onRemoveStream], which should confirm first).
 */
internal fun videoMenuItems(
    card: VideoCardUi,
    actions: AppActions,
    showDetailsItem: Boolean = true,
    onRemoveStream: ((VideoCardUi) -> Unit)? = null,
): List<GlassMenuItem> = buildList {
    add(GlassMenuItem("Play", icon = R.drawable.ic_play_arrow) { actions.play(card.id) })
    if (card.progress != null) {
        add(GlassMenuItem("Play from Beginning", icon = R.drawable.ic_restart_alt) { actions.play(card.id, startOver = true) })
    }
    if (showDetailsItem) {
        add(GlassMenuItem("Go to Details", icon = R.drawable.ic_info) { actions.openVideo(card.id) })
    }
    add(
        GlassMenuItem(
            label = if (card.watched) "Mark as Unwatched" else "Mark as Watched",
            icon = if (card.watched) R.drawable.ic_visibility_off else R.drawable.ic_check_circle,
            startsSection = true,
        ) { actions.setWatched(card.id, !card.watched) },
    )
    if (card.kind != MediaKind.STREAM) {
        add(GlassMenuItem("Identify…", icon = R.drawable.ic_search) { actions.identify(card.id) })
    }
    if (card.kind == MediaKind.STREAM && card.sourceId != null && onRemoveStream != null) {
        add(GlassMenuItem("Remove Stream…", icon = R.drawable.ic_delete, destructive = true, startsSection = true) { onRemoveStream(card) })
    }
}

// ───────────────────────────── Removing sources ─────────────────────────────

/** A source (folder, network share or saved stream) waiting for the user to confirm its removal. */
internal class PendingRemoval(val sourceId: Long, val name: String, val type: SourceType)

/** "Remove “NAS”?" with Cancel / Remove. Removal goes through actions.removeSource. */
@Composable
internal fun RemoveSourceAlert(pending: PendingRemoval, actions: AppActions, onClose: () -> Unit) {
    val message = when (pending.type) {
        SourceType.FOLDER -> "Its videos leave the library and their watch history is deleted. The files stay in the folder."
        SourceType.SMB -> "Its videos leave the library and their watch history is deleted. The files stay on the server."
        SourceType.STREAM -> "The stream is removed from your library."
        SourceType.DEVICE -> "Its videos leave the library."
    }
    IosAlert(
        title = "Remove “${pending.name}”?",
        message = message,
        actions = listOf(
            AlertAction("Cancel", AlertActionStyle.Cancel, onClick = onClose),
            AlertAction("Remove", AlertActionStyle.Destructive) {
                actions.removeSource(pending.sourceId)
                onClose()
            },
        ),
        onDismissRequest = onClose,
    )
}

// ───────────────────────────── Page frames ─────────────────────────────

/** iOS push: the new page slides in from the right over the old one, which drifts left and fades. */
internal fun phonePushTransition(): ContentTransform =
    (slideInHorizontally(NovaMotion.standard()) { it } + fadeIn(NovaMotion.fade(NovaMotion.FastMs))) togetherWith
        (slideOutHorizontally(NovaMotion.standard()) { -it / 4 } + fadeOut(NovaMotion.fade(NovaMotion.DurationMs)))

/** iOS pop: the page slides away to the right, uncovering the one below it. */
internal fun phonePopTransition(): ContentTransform =
    ((slideInHorizontally(NovaMotion.standard()) { -it / 4 } + fadeIn(NovaMotion.fade(NovaMotion.FastMs))) togetherWith
        (slideOutHorizontally(NovaMotion.standard()) { it } + fadeOut(NovaMotion.fade(NovaMotion.DurationMs))))
        .apply { targetContentZIndex = -1f }

/** Top of the area below the floating nav bar (status bar + bar), where spinners go. */
@Composable
internal fun phoneNavBarBottom(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + NavBarDefaults.Height

/**
 * A large-title page: [content] scrolls under a floating GlassNavBar whose glass background and
 * inline [title] fade in once the large title has scrolled away ([collapsed]).
 */
@Composable
internal fun PhoneLargeTitlePage(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPage(
        modifier = modifier
            .fillMaxSize()
            .background(NovaColors.background),
        chrome = {
            GlassNavBar(
                title = title,
                showBackground = collapsed,
                leading = leading,
                trailing = trailing,
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(NovaColors.background),
            content = content,
        )
    }
}

/**
 * Pull-to-refresh for phone pages. Spins only while files are being scanned (ScanUi.running),
 * never for the poster pass that follows, so the spinner does not stay pinned for minutes.
 */
@Composable
internal fun PhoneRefreshBox(
    scanning: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTop: Dp = phoneNavBarBottom(),
    content: @Composable BoxScope.() -> Unit,
) {
    NovaPullToRefreshBox(
        isRefreshing = scanning,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        indicatorPadding = PaddingValues(top = indicatorTop),
        content = content,
    )
}

/** Centres [content] in the page but still scrolls when it does not fit (landscape). */
@Composable
internal fun PhoneCenteredScroll(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * A details page whose data is not there yet: a spinner with a back button, and after a few
 * seconds "not available" (the item left the library, e.g. a show re-identified under a new
 * name), so the page never spins forever.
 */
@Composable
internal fun PhoneMissingPage(onBack: () -> Unit, modifier: Modifier = Modifier, what: String = "item") {
    var gaveUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MISSING_GRACE_MS)
        gaveUp = true
    }
    GlassPage(
        modifier = modifier
            .fillMaxSize()
            .background(NovaColors.background),
        chrome = {
            GlassNavBar(showBackground = false, leading = { BackButton(onClick = onBack) })
        },
    ) {
        if (gaveUp) {
            PhoneCenteredScroll {
                EmptyState(
                    icon = R.drawable.ic_info,
                    title = "Not Available",
                    body = "This $what is no longer in your library. It may have been removed or identified as something else.",
                    actionLabel = "Go Back",
                    onAction = onBack,
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ActivityIndicator(size = 36.dp)
            }
        }
    }
}

private const val MISSING_GRACE_MS = 3_000L
