package com.aakash.novafork

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.FakeLibrary.DUNE
import com.aakash.novafork.FakeLibrary.MOVIES_FOLDER
import com.aakash.novafork.FakeLibrary.MOVIE_NIGHT
import com.aakash.novafork.FakeLibrary.SEVERANCE_KEY
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.model.AppUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PORTRAIT = "sw800dp-w800dp-h1280dp-port-xhdpi"

/**
 * Renders the tablet UI to build/screens/tablet_*.png (landscape, list-detail) and
 * tabletport_*.png (portrait, compact) with the fake library and generated artwork.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "sw800dp-w1280dp-h800dp-land-xhdpi")
class TabletScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installImages() {
        FakeImages.install(ApplicationProvider.getApplicationContext())
    }

    @After
    fun resetImages() {
        FakeImages.reset()
    }

    private fun capture(name: String, state: AppUiState, expected: DeviceLayout = DeviceLayout.TABLET_EXPANDED) {
        var layout: DeviceLayout? = null
        compose.captureScreen(name) {
            AppUnderTest(state) { layout = it }
        }
        assertEquals(expected, layout)
    }

    @Test fun home() = capture("tablet_home", FakeScreens.home())

    /** List-detail: the grid on the left, Dune loaded into the preview pane on the right. */
    @Test fun libraryTwoPane() = capture("tablet_library_two_pane", FakeScreens.libraryPreview(DUNE))

    @Test fun movieDetails() = capture("tablet_movie_details", FakeScreens.videoDetails(DUNE))

    @Test fun showDetails() = capture("tablet_show_details", FakeScreens.showDetails(SEVERANCE_KEY))

    @Test fun browseDir() = capture("tablet_browse_dir", FakeScreens.browseDir(MOVIES_FOLDER))

    /** Browse with a playlist open in the right pane. */
    @Test fun playlist() = capture("tablet_playlist", FakeScreens.playlist(MOVIE_NIGHT))

    @Test fun settings() = capture("tablet_settings", FakeScreens.settingsPage())

    @Test
    @Config(qualifiers = PORTRAIT)
    fun portraitHome() = capture("tabletport_home", FakeScreens.home(), DeviceLayout.TABLET_COMPACT)

    /** Portrait has no room for the preview pane: the grid alone, tapping opens details. */
    @Test
    @Config(qualifiers = PORTRAIT)
    fun portraitLibrary() = capture("tabletport_library", FakeScreens.library(), DeviceLayout.TABLET_COMPACT)
}
