package com.aakash.novafork

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.FakeLibrary.MOVIES_FOLDER
import com.aakash.novafork.FakeLibrary.SEVERANCE_REWATCH
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.UiMappers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A page title in the phone top bar never runs under the buttons beside it (owner: "Select" once
 * sat on top of "All Videos").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-420dpi")
class TopBarOverlapTest {
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

    private fun assertTitleClear(title: String, state: AppUiState) {
        compose.setContent { AppUnderTest(state) {} }
        compose.waitForIdle()
        // The top bar's copy of the title is the highest one on screen.
        val titleBounds = compose.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes()
            .minBy { it.boundsInRoot.top }.boundsInRoot
        val buttons = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            // Only what sits on the title's row (not the grid below it, nor a page-wide area).
            .filter { it.top < titleBounds.bottom && it.bottom > titleBounds.top && it.height < titleBounds.height * 4 }
        buttons.forEach { button ->
            assertFalse("\"$title\" runs under a button at $button (title at $titleBounds)", overlaps(titleBounds, button))
        }
    }

    private fun overlaps(a: Rect, b: Rect): Boolean = a.left < b.right && b.left < a.right

    @Test fun recentlyAdded() = assertTitleClear("Recently Added", FakeScreens.library())

    @Test fun allVideos() = assertTitleClear(
        "All Videos",
        FakeScreens.library().let { it.copy(library = UiMappers.library(FakeLibrary.index(), LibraryFilter.ALL, LibrarySort.TITLE)) },
    )

    @Test fun movies() = assertTitleClear("Library", FakeScreens.library(LibraryFilter.MOVIES))

    @Test fun duplicates() = assertTitleClear(
        "Duplicates",
        FakeScreens.library().let { it.copy(library = it.library.copy(filter = LibraryFilter.DUPLICATES)) },
    )

    @Test fun browse() = assertTitleClear("Sources", FakeScreens.browse())

    @Test fun browseDir() = assertTitleClear(FakeScreens.browseDir(MOVIES_FOLDER).browseDir!!.title, FakeScreens.browseDir(MOVIES_FOLDER))

    @Test fun playlist() = assertTitleClear(FakeScreens.playlist(SEVERANCE_REWATCH).playlist!!.name, FakeScreens.playlist(SEVERANCE_REWATCH))
}
