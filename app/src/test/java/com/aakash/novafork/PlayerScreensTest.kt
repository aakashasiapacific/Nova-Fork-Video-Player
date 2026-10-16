package com.aakash.novafork

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.rememberDeviceLayout
import com.aakash.novafork.ui.model.NoOpPlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.player.PlayerScreen
import com.aakash.novafork.ui.theme.NovaPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val TABLET = "sw800dp-w1280dp-h800dp-land-xhdpi"

/** Long enough for the player's controls to hide themselves while playing (3.5 s plus the fade). */
private const val CONTROLS_HIDDEN_MS = 4_500L

/**
 * Renders the player over a fake video frame, as PlayerActivity does but without libVLC:
 * build/screens/player_phone_*.png (phone in landscape) and player_tablet_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-420dpi")
class PlayerScreensTest {
    @get:Rule
    val compose = createComposeRule()

    /** Dune is shot in scope (2.39:1), so it letterboxes on both screens; the episode is 16:9. */
    private val scopeFrame: Bitmap by lazy { FakeImages.videoFrame("dune-part-two-frame", 1280, 536) }
    private val episodeFrame: Bitmap by lazy { FakeImages.videoFrame("severance-s2e3-frame") }

    @Before
    fun installImages() {
        FakeImages.install(ApplicationProvider.getApplicationContext())
    }

    @After
    fun resetImages() {
        FakeImages.reset()
    }

    private fun capture(
        name: String,
        state: PlayerUiState,
        expected: DeviceLayout,
        frame: Bitmap = scopeFrame,
        settleMs: Long = 1_200L,
    ) {
        var layout: DeviceLayout? = null
        val prefix = if (expected.isTablet) "player_tablet" else "player_phone"
        compose.captureScreen("${prefix}_$name", settleMs = settleMs) {
            NovaPlayerTheme {
                val current = rememberDeviceLayout()
                SideEffect { layout = current }
                PlayerScreen(state = state, actions = remember { NoOpPlayerActions() }, layout = current) {
                    VideoFrame(frame)
                }
            }
        }
        assertEquals(expected, layout)
    }

    // Phone, landscape

    /** Paused keeps the controls up. */
    @Test fun phonePaused() = capture("paused", FakePlayer.paused(), DeviceLayout.PHONE)

    @Test fun phoneSeek() = capture("seek", FakePlayer.seeking(), DeviceLayout.PHONE, settleMs = CONTROLS_HIDDEN_MS)

    @Test fun phoneUpNext() =
        capture("up_next", FakePlayer.upNext(), DeviceLayout.PHONE, frame = episodeFrame, settleMs = CONTROLS_HIDDEN_MS)

    @Test fun phoneError() = capture("error", FakePlayer.error(), DeviceLayout.PHONE, frame = episodeFrame)

    /** Just resumed: the banner sits above the controls, which are still up from opening. */
    @Test fun phoneResume() = capture("resume", FakePlayer.resumed(), DeviceLayout.PHONE)

    /** A shuffled queue repeating all: shuffle and repeat lit in the bottom row, the queue sheet closed. */
    @Test fun phoneQueue() = capture("queue", FakePlayer.queued(), DeviceLayout.PHONE, frame = episodeFrame)

    /** Playing on a Chromecast: a placeholder names the device where the video would be. */
    @Test fun phoneCasting() = capture("casting", FakePlayer.casting(), DeviceLayout.PHONE)

    // Tablet, landscape

    @Test
    @Config(qualifiers = TABLET)
    fun tabletPaused() = capture("paused", FakePlayer.paused(), DeviceLayout.TABLET_EXPANDED)

    @Test
    @Config(qualifiers = TABLET)
    fun tabletSeek() = capture("seek", FakePlayer.seeking(), DeviceLayout.TABLET_EXPANDED, settleMs = CONTROLS_HIDDEN_MS)

    @Test
    @Config(qualifiers = TABLET)
    fun tabletUpNext() =
        capture("up_next", FakePlayer.upNext(), DeviceLayout.TABLET_EXPANDED, frame = episodeFrame, settleMs = CONTROLS_HIDDEN_MS)

    @Test
    @Config(qualifiers = TABLET)
    fun tabletError() = capture("error", FakePlayer.error(), DeviceLayout.TABLET_EXPANDED, frame = episodeFrame)

    @Test
    @Config(qualifiers = TABLET)
    fun tabletResume() = capture("resume", FakePlayer.resumed(), DeviceLayout.TABLET_EXPANDED)

    @Test
    @Config(qualifiers = TABLET)
    fun tabletQueue() = capture("queue", FakePlayer.queued(), DeviceLayout.TABLET_EXPANDED, frame = episodeFrame)
}

/** Stands in for libVLC's video layout: one decoded frame, fitted like the default "Fit" aspect. */
@Composable
private fun VideoFrame(frame: Bitmap) {
    val image = remember(frame) { frame.asImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
}
