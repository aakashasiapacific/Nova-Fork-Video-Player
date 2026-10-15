package com.aakash.novafork.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.FakeImages
import com.aakash.novafork.FakePlayer
import com.aakash.novafork.captureScreen
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.rememberDeviceLayout
import com.aakash.novafork.ui.model.NoOpPlayerActions
import com.aakash.novafork.ui.model.SubtitleResultUi
import com.aakash.novafork.ui.model.SubtitleSearchUi
import com.aakash.novafork.ui.player.PlayerScreen
import com.aakash.novafork.ui.player.SUBTITLE_SEARCH_TITLE
import com.aakash.novafork.ui.player.SubtitleSearchContent
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

/**
 * The "Download subtitles" list: build/screens/player_tablet_subtitle_search.png (the side panel
 * over the paused player) and player_phone_subtitle_search*.png (what the phone's bottom sheet
 * holds, drawn without the sheet's dialog window).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-420dpi")
class SubtitleSearchScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val frame: Bitmap by lazy { FakeImages.videoFrame("dune-part-two-frame", 1280, 536) }

    private val results = SubtitleSearchUi(
        languages = "English, Hindi",
        results = listOf(
            SubtitleResultUi(
                id = "1",
                language = "English",
                release = "Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX",
                matchesFile = true,
                downloadCount = 48_211,
            ),
            SubtitleResultUi(
                id = "2",
                language = "English",
                release = "Dune.Part.Two.2024.1080p.WEBRip.x264-RARBG",
                hearingImpaired = true,
                downloadCount = 31_877,
                downloaded = true,
            ),
            SubtitleResultUi(
                id = "3",
                language = "Hindi",
                release = "Dune Part Two (2024) Hindi",
                downloadCount = 2_140,
                machineTranslated = true,
            ),
            SubtitleResultUi(id = "4", language = "Hindi", release = "Dune.Part.Two.2024.720p.HDRip", downloadCount = 1),
        ),
    )

    @Before
    fun installImages() {
        FakeImages.install(ApplicationProvider.getApplicationContext())
    }

    @After
    fun resetImages() {
        FakeImages.reset()
    }

    @Test
    @Config(qualifiers = "sw800dp-w1280dp-h800dp-land-xhdpi")
    fun tabletPanel() {
        var layout: DeviceLayout? = null
        compose.captureScreen("player_tablet_subtitle_search") {
            NovaPlayerTheme {
                val current = rememberDeviceLayout()
                SideEffect { layout = current }
                PlayerScreen(
                    state = FakePlayer.paused().copy(subtitleSearch = results.copy(downloadingId = "1")),
                    actions = remember { NoOpPlayerActions() },
                    layout = current,
                ) {
                    VideoFrame(frame)
                }
            }
        }
        assertEquals(DeviceLayout.TABLET_EXPANDED, layout)
    }

    @Test
    fun phoneList() = capturePhone("player_phone_subtitle_search", results)

    @Test
    fun phoneQuotaReached() = capturePhone(
        "player_phone_subtitle_search_error",
        results.copy(error = "Download limit reached, resets at 1:03 PM"),
    )

    @Test
    fun phoneSearching() = capturePhone(
        "player_phone_subtitle_search_loading",
        SubtitleSearchUi(loading = true, languages = "English"),
    )

    private fun capturePhone(name: String, search: SubtitleSearchUi) {
        compose.captureScreen(name) {
            NovaPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(top = 16.dp)) {
                        Text(
                            text = SUBTITLE_SEARCH_TITLE,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                        )
                        SubtitleSearchContent(search = search, actions = remember { NoOpPlayerActions() })
                    }
                }
            }
        }
    }
}

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
