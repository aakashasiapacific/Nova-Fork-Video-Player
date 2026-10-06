# Nova Fork — architecture

A simple, VLC-powered Android video player with a poster/backdrop library (TMDB + local artwork),
and **two separate UI families**: phone (smallest width < 600dp) and tablet (≥ 600dp).

## Toolchain (fixed — do not change versions)

| What | Version | Notes |
|---|---|---|
| AGP | 8.11.1 | compileSdk 36, minSdk 26, targetSdk 36, Java 17 |
| Kotlin | 2.0.21 | compose + serialization plugins 2.0.21, KSP 2.0.21-1.0.28 |
| Compose BOM | 2024.12.01 | material3 1.3.1, foundation 1.7.x, `material-icons-core` only |
| Lifecycle | 2.8.7 | runtime-ktx, runtime-compose, viewmodel-compose |
| Room | 2.6.1 | KSP; enums are stored by name automatically |
| Coroutines | 1.9.0 | |
| kotlinx-serialization-json | 1.7.3 | |
| OkHttp | 4.12.0 | + okhttp-dnsoverhttps 4.12.0 |
| Coil | 2.7.0 | `coil-compose` (singleton `AsyncImage`), `coil-video` (`VideoFrameDecoder`) — **Coil 2 package names `coil.*`, not coil3** |
| libVLC | `org.videolan.android:libvlc-all:3.7.2` | Java API in `org.videolan.libvlc` (3.x API, *not* 4.x) |
| smbj | 0.13.0 | Java 8 bytecode; use `BCSecurityProvider` + `NtlmAuthenticator.Factory()` only |
| Tests | JUnit 4, Robolectric 4.14.1 (`@Config(sdk = [34])`), Roborazzi 1.32.2, coil-test 2.7.0 | |

Nothing else may be added. No Hilt, no Navigation-Compose, no DataStore, no material-icons-extended,
no Accompanist. Library APIs must exist in exactly these versions.

The Android SDK is not available in the authoring sandbox: the app is compiled by GitHub Actions
(`.github/workflows/build.yml`). The `core` module is plain Kotlin/JVM and can be built/tested
anywhere. Reference API dumps (javap) for libVLC 3.7.2, smbj 0.13.0 and Coil 2.7.0 are provided to
implementers separately; trust them over memory.

## Modules and packages

```
core/   com.aakash.novafork.core.*     pure Kotlin/JVM: parsing, matching, TMDB client, rules (unit-tested)
app/    com.aakash.novafork.*          Android app
  NovaApp, AppGraph                    process singletons (manual DI)
  data/                                Settings, repository, Room, network, metadata enrichment
  data/db/                             Room entities (contract), DAOs, database
  sources/                             scanners: MediaStore, SAF folders, SMB (smbj), Coil smb fetcher
  player/                              libVLC engine, PlayerController, PlayerActivity
  ui/model/                            UI state + action contracts (shared by everything below)
  ui/theme/, ui/adaptive/, ui/common/  theme, phone/tablet detection + dimens, shared art/components
  ui/phone/                            phone-only screens and components
  ui/tablet/                           tablet-only screens and components
  ui/player/                           player screen + phone and tablet player controls
  ui/settings/, ui/dialogs/            shared adaptive Settings screen and dialogs
  ui/MainActivity, MainViewModel, …    app shell: state assembly, navigation, permissions, pickers
```

### Contract files (already written — implement against them, do not change their public API)

- `core/.../parse/ParsedName.kt`, `parse/NameParser.kt`, `match/TitleMatcher.kt`, `tmdb/TmdbModels.kt`,
  `tmdb/TmdbClient.kt`, `artwork/ArtworkRules.kt`, `subs/SubtitleRules.kt`, `smb/SmbUris.kt`,
  `format/Formats.kt`, `library/VideoFiles.kt`, `library/LibraryKeys.kt`
  (function bodies marked `TODO("core agent")` are for the core implementer).
- `app/.../data/db/Entities.kt`, `data/Settings.kt`, `data/Scanning.kt`, `data/LibraryRepository.kt`
- `app/.../ui/model/UiModels.kt`, `ui/model/AppState.kt`, `ui/model/PlayerUi.kt`
- `app/.../player/PlayerIntents.kt`, `NovaApp.kt`, `AppGraph.kt`
- `app/.../ui/theme/Theme.kt`, `ui/adaptive/DeviceLayout.kt`, `ui/common/Art.kt`, `ui/common/Components.kt`

Adding *new* files and *new* internal helpers is fine. If a contract is genuinely wrong, keep the
existing signature working and note it in your report.

## Data flow

```
 Scanners (DEVICE MediaStore / FOLDER SAF / SMB smbj)
        │ List<ScannedVideo>
        ▼
 LibraryRepositoryImpl ── NameParser → VideoEntity (kind, parsedTitle, season…)
        │                 upsert by uri, keep playback fields, present=false for missing
        │ background: MetadataEnricher
        │   TMDB (findMovie / findShow + season) → MetadataEntity + episode fields, MatchCache
        ▼
 Room (videos, metadata, sources, match_cache) ──Flow──▶ MainViewModel ──▶ AppUiState ──▶ PhoneApp / TabletApp
                                                                │
                                         AppActions ◀───────────┘  (play → PlayerActivity)
```

### Artwork priority (TMDB first, local fallback)
For a movie / show poster: TMDB poster → local poster → (frameSource) → placeholder.
With `Settings.preferLocalArtwork`: local first, then TMDB. Same idea for backdrops
(TMDB backdrop → local fanart). Episode stills: TMDB still → local thumb → frame.
`frameSource` is set only for local files (content:// or file://), never for smb/http.
TMDB URLs: `TmdbClient.posterUrl(path)` (w500), `backdropUrl(path)` (w1280), `stillUrl(path)` (w780);
use w342 posters for small cards when convenient.

### Network
`Network.createOkHttp(context, settings)`: 15 s timeouts, 50 MB disk cache, User-Agent
"NovaFork/<version>", a `Dns` that uses DNS-over-HTTPS (Cloudflare 1.1.1.1 / 1.0.0.1 bootstrap,
Google 8.8.8.8 fallback) when `settings.secureDns` and falls back to `Dns.SYSTEM` on any failure.
`Network.createImageLoader(context, okHttp, smb)`: Coil 2 `ImageLoader.Builder` with
`okHttpClient`, `VideoFrameDecoder.Factory()`, the SMB fetcher `SmbFetcher.Factory(smb)` (in
`sources/`), crossfade, memory cache 20% and disk cache 250 MB in `cacheDir/image_cache`.

### Playback
`PlayerActivity` (separate task, PiP) is started through `PlayerIntents`. It resolves the media:
- `content://` → open a `ParcelFileDescriptor` ("r") and use `Media(libVLC, fd.fileDescriptor)`;
  keep the PFD open until the media is released.
- `file://` or absolute path → `Media(libVLC, path)`.
- `smb://` → `Media(libVLC, Uri)` plus options `:smb-user=…`, `:smb-pwd=…`, `:smb-domain=…` from
  `LibraryRepository.smbCredentials(host, share)`.
- `http(s)://`, `rtsp://`, etc. → `Media(libVLC, Uri)` plus `:network-caching=1500`.
Hardware decoding: `media.setHWDecoderEnabled(enabled, force)` from `Settings.hardwareDecoding`.
External subtitles: siblings matched by `SubtitleRules` (SAF/MediaStore folders, SMB listing) are
added with `mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, uri, false)`; the first one is selected
when the file has no embedded subtitles or `preferredSubtitleLanguage` matches.
Aspect: `mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT|SURFACE_FIT_SCREEN|SURFACE_FILL|SURFACE_16_9|SURFACE_4_3|SURFACE_ORIGINAL)`.
Video view: `org.videolan.libvlc.util.VLCVideoLayout` inside `AndroidView`, attached with
`mediaPlayer.attachViews(layout, null, true /*subtitles*/, false /*textureView*/)`.

## Phone vs tablet UI

Decided once per composition with `rememberDeviceLayout(settings.layoutMode)`; provided through
`LocalDeviceLayout` and `LocalNovaDimens`. Phone and tablet have **their own components** — share
only `ui/common`, `ui/settings`, `ui/dialogs`, and `ui/model`.

**Phone (`ui/phone`, entry `PhoneApp(state: AppUiState, actions: AppActions)`):**
bottom `NavigationBar` (Home, Library, Browse, Settings); Home = full-bleed backdrop hero pager
(≈ 62% of width in height, 16:10-ish) with title, meta line, Play + Info, then rows (Continue
watching as 16:9 cards, Recently added, Movies, TV shows, Videos) of 112dp posters; Library =
filter chips + 3-column poster grid; details = backdrop header with overlapping 112dp poster,
Play/Resume, overview, (shows) season tabs + episode list with 16:9 stills; Browse = list of
sources and streams with FABs; Search via top-bar icon.

**Tablet (`ui/tablet`, entry `TabletApp(state: AppUiState, actions: AppActions, layout: DeviceLayout)`):**
`NavigationRail` with app mark, search and tabs; Home = cinematic hero (backdrop fills the top
~55% with start/bottom scrims, poster on the left at 200dp, title in displaySmall, overview 3
lines, Play/Details/Identify) and wider rows (156dp posters, 320dp landscape cards); Library in
`TABLET_EXPANDED` = **list-detail**: adaptive poster grid (≥150dp cells) on the left, preview pane
(backdrop, poster, overview, Play) on the right driven by `actions.preview(item)` +
`state.videoDetails/showDetails/previewKey`; in `TABLET_COMPACT` tapping navigates; details =
backdrop as full-screen background with heavy scrim, 240dp poster column + text column, episodes
as a grid of 16:9 cards per season; Browse = two panes (sources list | folder content grid).

**Player (`ui/player`, entry `PlayerScreen(state, actions, layout, surface)`):**
phone controls = compact top bar (back, title, tracks, more), centre ⏪ ▶ ⏩, bottom seek bar with
times and an icon row (lock, aspect, subtitles, audio, speed, rotate, PiP) — track pickers in a
`ModalBottomSheet`. Tablet controls = larger targets, seek bar with title above, labelled
buttons in one bottom row, tracks/speed/subtitle delay in a right-side panel (no bottom sheet).
Both: tap toggles controls (auto-hide after 3.5 s while playing), double-tap left/right seeks by
`seekStepMs`, horizontal drag scrubs, vertical drag left = brightness / right = volume, lock mode,
resume banner, up-next card, buffering spinner, error card.

## Icons (res/drawable, Material Icons Round, white fill — tint with `Icon`)

`ic_add ic_arrow_back ic_aspect_ratio ic_audiotrack ic_brightness_6 ic_cast_connected ic_check
ic_check_circle ic_chevron_right ic_close ic_cloud ic_computer ic_create_new_folder ic_dark_mode
ic_delete ic_dns ic_done_all ic_edit ic_error ic_expand_more ic_filter_list ic_folder
ic_folder_open ic_folder_special ic_forward_10 ic_fullscreen ic_fullscreen_exit ic_grid_view ic_hd
ic_hide_image ic_history ic_home ic_image ic_info ic_key ic_lan ic_language ic_link ic_live_tv
ic_lock ic_lock_open ic_more_vert ic_movie ic_pause ic_phone_android ic_picture_in_picture_alt
ic_play_arrow ic_playlist_play ic_public ic_refresh ic_replay ic_replay_10 ic_restart_alt
ic_screen_rotation ic_sd_storage ic_search ic_settings ic_skip_next ic_skip_previous
ic_smart_display ic_sort ic_speed ic_star ic_storage ic_subtitles ic_subtitles_off ic_theaters
ic_timer ic_tune ic_tv ic_video_file ic_video_library ic_view_list ic_visibility
ic_visibility_off ic_volume_off ic_volume_up ic_wifi`

Use `NovaIcon(R.drawable.ic_x, "desc")` or `Icon(painterResource(R.drawable.ic_x), …)`.
(`ic_forward_10`/`ic_replay_10` show "10"; for other step sizes use ic_replay / ic_skip_next etc.)

## Screenshot tests (CI)

`app/src/test/.../PhoneScreensTest.kt` (`qualifiers = "w411dp-h891dp-port-420dpi"`) and
`TabletScreensTest.kt` (`"sw800dp-w1280dp-h800dp-land-xhdpi"` and a portrait
`"sw800dp-w800dp-h1280dp-port-xhdpi"` case) render the real composables with fake `AppUiState` /
`PlayerUiState` and a Coil `FakeImageLoaderEngine` that returns generated gradient bitmaps, then
`captureRoboImage("build/screens/<name>.png")`. CI publishes them to the `screens` branch.

## Conventions

- Kotlin official style, 4-space indent, trailing commas, no wildcard imports.
- Comments explain *why*, sparingly. No commented-out code.
- No `!!` on data from disk/network. Catch `CancellationException` separately (rethrow).
- Main-safe suspend functions (`withContext(Dispatchers.IO)` inside the data/sources layers).
- Strings are inline English in code (no strings.xml beyond app_name).
