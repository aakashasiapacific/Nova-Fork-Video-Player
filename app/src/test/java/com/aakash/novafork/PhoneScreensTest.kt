package com.aakash.novafork

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.FakeLibrary.DUNE
import com.aakash.novafork.FakeLibrary.MOVIES_FOLDER
import com.aakash.novafork.FakeLibrary.SEVERANCE_KEY
import com.aakash.novafork.FakeLibrary.SEVERANCE_REWATCH
import com.aakash.novafork.FakeLibrary.SEVERANCE_S2E3
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.LibraryFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the phone UI to build/screens/phone_*.png with the fake library and generated artwork.
 * Sized like a Galaxy S21 FE: 411×891dp at 2.625x.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-420dpi")
class PhoneScreensTest {
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

    private fun capture(name: String, state: AppUiState, wholeScreen: Boolean = false) {
        var layout: DeviceLayout? = null
        // Dialogs open on top of the settled screen (see captureScreen).
        var shown by mutableStateOf(state.copy(dialog = null))
        val reveal: (() -> Unit)? = if (state.dialog == null) null else fun() { shown = state }
        compose.captureScreen("phone_$name", wholeScreen = wholeScreen, reveal = reveal) {
            AppUnderTest(shown) { layout = it }
        }
        assertEquals("a phone must get the phone UI", DeviceLayout.PHONE, layout)
    }

    @Test fun home() = capture("home", FakeScreens.home())

    /** First launch: no videos and no storage permission yet. */
    @Test fun homeEmpty() = capture("home_empty", FakeScreens.onboarding())

    @Test fun library() = capture("library", FakeScreens.library())

    @Test fun libraryShows() = capture("library_shows", FakeScreens.library(LibraryFilter.SHOWS))

    @Test fun movieDetails() = capture("movie_details", FakeScreens.videoDetails(DUNE))

    @Test fun episodeDetails() = capture("episode_details", FakeScreens.videoDetails(SEVERANCE_S2E3))

    @Test fun showDetails() = capture("show_details", FakeScreens.showDetails(SEVERANCE_KEY))

    @Test fun browse() = capture("browse", FakeScreens.browse())

    @Test fun browseDir() = capture("browse_dir", FakeScreens.browseDir(MOVIES_FOLDER))

    @Test fun search() = capture("search", FakeScreens.search("pa"))

    @Test fun playlist() = capture("playlist", FakeScreens.playlist(SEVERANCE_REWATCH))

    @Test fun settings() = capture("settings", FakeScreens.settingsPage())

    @Test fun smbDialog() = capture("dialog_smb", FakeScreens.smbDialog(), wholeScreen = true)

    @Test fun identifyDialog() = capture("dialog_identify", FakeScreens.identifyDialog(), wholeScreen = true)
}
