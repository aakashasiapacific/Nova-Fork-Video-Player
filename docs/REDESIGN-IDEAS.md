# Redesign ideas: owner's notes

**Built on the phone (round 1, branch `ccr-987fc293-7gnds9`):**
- the black theme (section 0);
- the four tabs (section 1);
- no title and a clear status bar (section 2);
- the hero, its dots, the muted preview and the mute button (section 3);
- the watchlist "+" and its row (section 6.3).

**Round 2 (build 40):**
- TMDB title logos over the hero (phone and tablet), text when a title has none;
- 16:9 landscape cards in every Home row (section 4), phone and tablet;
- tablet: the floating tab capsule at the top centre (6.5) replaces the side rail, and the hero is
  the phone's pager in a wide layout (backdrop, title and buttons at the lower left, dots,
  previews and mute); landscape phones use the same wide layout.

Still to do: personal videos' frame-based hero art.

**Round 3 (build 41, owner's test of build 40):**
- The phone hero looks like a title's own page: backdrop on top, poster with title (logo), meta and
  highlight beside it, Play pill and "+" below.
- Home is only the hero, Continue watching and Library (All videos, Movies, TV shows, Folders,
  sources and playlists); the Recently added, Movies, TV shows, Videos and Watchlist strips are
  gone, and "Videos" left the library.
- All videos (sorted by Recently added) is an endless timeline of every video by when it reached
  the device, in date groups (Today, Yesterday, This week, months).
- A picked poster now shows on Home too.
- No "Not identified" card: Identify stays in the ⋮ menu.
- The phone tab bar truly floats: no black strip behind it, pages scroll on underneath
  (`LocalBottomBarSpace` pads their lists).

These are the owner's decisions and requests for the Apple TV-style redesign, plus what their
screenshots show. They come before `docs/APPLE-TV-SPEC.md` wherever the two disagree. Nothing here is
implemented yet.

Reference screenshots were sent in chat; they are not in the repo:
- Apple TV app, Home, phone, light mode (layout reference only; the app will be dark only)
- Nova Fork build 24, Home

## 0. Theme: AMOLED black, dark only (decided)

- **One theme only:** the Apple TV app's dark (night) look, on a pure black `#000000` background.
- **No light theme and no system light/dark switch.** One theme keeps the work and the testing at half
  the size.
- Reference screenshots of the dark Apple TV app come from the owner. The light-mode screenshot in the
  notes below was only used for layout and proportions.

## 1. Navigation: four tabs, in this order

1. **Video:** the video library and its home feed.
2. **Audio:** a new music player, a "ditto" of Apple Music. See section 5.
3. **Settings**
4. **Search**

Style, as in the Apple TV Android app:
- A bottom bar with an icon above a label.
- The selected tab gets a soft grey pill behind its icon and a bold label; the other tabs are grey.

## 2. Maximum immersion

- **No app title.** Remove the big "Nova Fork" heading; Apple TV shows "Apple TV" but we show nothing.
  There is no top bar at all; the hero starts at the very top of the screen.
- **Clear status bar.** The app draws edge to edge and the system status bar is fully transparent over
  the hero; today ours is not.
  - Pick the status bar icon colour (dark or light) from the brightness of the top of the hero image, and
    switch it as the hero changes or the page scrolls.

## 3. Hero carousel: big and easy to swipe

Apple TV's phone hero, as measured from the screenshot:
- **Size and fit:** full-bleed, from the top of the screen down to about **80–82% of the screen height**,
  with the image cropped to fill. Ours is about 45% now. The big size is what makes swiping between
  items so easy.
- **Centred content** in the lower part of the hero:
  - **Title as a logo:** the show's logo, about 80% of the screen width. TMDB has title logos (the
    `images` → `logos` endpoint), so use one when it exists and fall back to big bold text.
  - **Meta line:** a small badge, then "TV Show · Comedy · Sport · rating".
  - **Highlight line:** one bold line, e.g. "#1 in Comedy" in Apple TV. For us: "Continue · S2, E3 ·
    20 min left", "New", "Recently added" and so on.
  - **Buttons:** a **white pill** primary button ("▶ Play" / "▶ Resume") and a **dark round "+"**
    button next to it.
  - **Fine print:** an optional line under the buttons.
- **Page indicator:**
  - Small dots, where the active dot stretches into a pill that fills up as the auto-advance timer runs.
  - It has no colour accent; it is white or grey only.
- **Bottom edge:** the image meets the page background, and the first row header follows right after.
- **Personal videos** (no backdrop): use a frame from the video, scaled to fill, and maybe blurred
  behind a sharp centred copy.

What our current hero does differently:
- left-aligned text
- black title over a white fade
- orange Play and lavender Info buttons
- an orange dot pill
- a hero only about 45% of the screen

### Dark Apple TV screenshot (owner, 2026-10-16): what it shows
- **Colours:** a pure black background. Text is white for titles and light grey for secondary lines.
- **Primary button:** a white pill with black text.
- **Page dots:** white and grey, with the active one a stretched pill.
- **Tab bar:** black, with a thin dark separator line above it. The selected tab has a dark-grey pill
  behind its icon, a white label and white icon; the other tabs are grey.
- **Video preview:** the hero plays a muted video preview after a moment; it shows black while loading.
- **Mute button:** Apple TV puts it at the top right, as a dark round glass button next to the profile
  button.

### Owner's decisions on these
- **Hero video preview:** after a short pause, the hero plays a muted preview of the video itself. For
  our own files, that is a short clip from the middle of the video. *Changed after build 36:* only
  videos of a minute or less play, from the start and with sound; longer ones show their art.
- **Mute/unmute button at the bottom of the hero, not the top.** The top is out of thumb reach.
  Proposed spot: the right end of the page-dot row, as a small dark round glass button. It sits level with
  the dots, so the dots stay centred.

## 4. Rows under the hero

- **Row headers:** large bold titles with a chevron, e.g. "Top 10 TV Shows ›". The whole header opens
  the full list.
- **Our rows:** Continue Watching, Recently Added, Movies, TV Shows, Videos.
- **Cards:** landscape 16:9 artwork with the title and caption below. Our current "Recently added"
  cards are tall portrait crops with truncated titles.

## 5. Audio tab: an Apple Music "ditto" (new feature)

The app has no music/audio library today. This needs backend work as well as UI.

Backend:
- **Scanning:** audio files from MediaStore.Audio on the device and picked folders (local only).
- **Tags:** title, artist, album artist, album, track/disc number, year, genre and duration.
- **Artwork:** embedded album art, or `cover.jpg` / `folder.jpg` next to the files.
- **Storage:** new Room tables, so the database goes to v4 with a migration.
- **Playback:** through the existing `PlaybackService`, which already does background audio,
  MediaSession, the queue, shuffle and repeat.
- **Lyrics:** from `.lrc` files next to the songs, with time-synced highlighting.

UI, following Apple Music:
- **Library:** a list of Playlists, Artists, Albums, Songs and Genres, then a "Recently Added" grid of
  square album covers.
- **Album page:** big artwork, the title, the artist, Play and Shuffle pills, and the track list.
- **Artist page:** the artist's albums and top songs.
- **Mini player:** sits just above the tab bar on every tab while something plays.
- **Now Playing:** a full-screen sheet with:
  - a blurred-artwork background and large artwork;
  - the title and artist;
  - a scrubber, previous / play-pause / next, and volume;
  - buttons for lyrics, the queue and AirPlay-style casting.

**Built (build 44, from Apple Music's dark design as known; owner corrects from the APK):**
- Library home: big "Library", red-iconed Playlists / Artists / Albums / Songs / Genres rows,
  Recently Added grid of square covers; each opens a page with a red "‹ back" link and big title.
- Album page: big cover, title, artist in red, genre · year, grey Play/Shuffle pills with red
  icons, numbered tracks, "N songs, M minutes". Artist page: albums row and Top Songs.
- Mini player: a floating glass capsule above the tab bar on every tab (phone and tablet).
- Now Playing: blurred cover background, big cover (shrinks while paused), title and artist, thin
  scrubber with times, previous / play-pause / next, system volume, lyrics (big synced lines, tap
  to jump) and queue (shuffle, repeat, stop) panes; swipe down or back to close. No casting yet.

**Build 46 (owner's test of build 45):**
- Library home is only Playlists, Favorites (Apple Music's star, in Now Playing; Room v7
  `song_favorites`) and the albums grid (newest first, no "Recently Added" title).
- Now Playing has no volume slider; a star sits beside the title.
- The mini player follows Apple Music on iOS 26: above the full tab bar while a song plays; in
  the shrunk bar between the current tab and Search. A paused song shows only in the shrunk bar
  (the bar shrinks to show it). The last queue is kept when the app is cleared from recents and
  comes back paused, shown only on the Audio tab.
- Hero: a short clip (under a minute) fills its page with only a small caption and the mute
  button; title pages and clips start below the status bar, clear of the punch hole.
- Home has the Watchlist strip again (backdrop cards), under Continue watching.

**Build 48 (owner's test of build 47):**
- Hero: the poster sits under the backdrop (never over its subject); a sliver of the picture runs
  under the status bar, softly darkened; each page takes its own height (a reel stands tall, a
  film is backdrop plus poster row); the dots stay at one height; Play, star (video Favorites,
  Room v8 `video_favorites`) and "+".
- Library: Recently added (timeline), Watchlist, Favorites, All videos (A–Z), Movies, TV shows,
  Folders.
- Now Playing closes by dragging down anywhere, as in Apple Music.

**Build 51:** clips no longer play in the hero (dropped); the hero and the details pages share
one header (`BackdropPosterHeader`: 16:9 backdrop, poster 40dp into its faded edge), so the poster
stays put when a title is opened; each hero page shows for 3.5 s instead of 7 s.

**Build 52:** the hero is sized to its content (no empty band under the Play row: height =
status bar + 16:9 backdrop + `heroBelowBackdrop`); the text beside the poster is a short block
(title or logo, meta, rating with "Continue watching" on one line). A video with only a frame (camera
roll, saved YouTube: `Artwork.frameOnly`) shows one picture, its frame in its own shape on a blurred
copy of itself, with the title under it, instead of a backdrop plus a poster.

## 6. Decisions on the former open questions (owner, 2026-10-16)

1. **Library and sources are split:**
   - The library categories (Movies, TV Shows, Videos, Folders) live in the **Video tab**: the row
     headers open them, and a "Library" entry at the end of the feed lists them all.
   - Adding and managing sources (picked folders, network shares, Jellyfin/Emby/Plex servers) moves to
     **Settings › Sources**.
2. **Theme:** AMOLED black, dark only (section 0).
3. **The hero's round "+" button adds the title to a new Watchlist.**
   - The Watchlist is also a row on the Video tab, like Apple TV's "Up Next".
   - The "+" turns into a check mark when the title is already on the list; tapping it again removes it.
   - Backend: a small Room addition (a table or a flag), done in the same v4 migration as the audio
     tables.
4. **Music is local only** (changed by the owner, 2026-10-16): the phone (MediaStore.Audio) and the
   picked folders. No network shares and no Jellyfin/Emby/Plex music, now or later.
5. **Tablet: a floating tab bar at the top centre,** over the hero, as in the iPad Apple TV app. It
   keeps the same four tabs. Phone and tablet stay separate component families.
6. **Search: one search over videos and music.**
   - The results are grouped into sections: Movies, TV Shows, Videos, Songs, Albums and Artists.
   - Filter chips (All / Video / Music) sit at the top.
   - The Audio tab has no search of its own.

## 7. Video player: system brightness and volume (owner, 2026-10-16; built)

- **No brightness or volume of the player's own.** Today a vertical swipe on the left half sets
  the window's brightness (only while the player is open) and on the right half the media volume,
  with the player's own on-screen level. The owner doesn't want either. Brightness and volume are
  the phone's: the system brightness and the volume buttons.
- **Volume changes get an iOS-style indicator** instead of the player's current one: a slim
  vertical pill that fills as the volume changes and fades away after a moment, as on iPhone.
- **Decided (owner, 2026-10-16):**
  - **Remove the vertical swipes** for brightness and volume altogether. The player no longer
    touches brightness; the phone's own brightness applies. (Horizontal scrubbing and the
    double-tap seeks stay.)
  - **The iOS-style indicator replaces Android's volume panel** while the player is on screen:
    the player takes the volume keys itself (PlayerActivity's onKeyDown for KEYCODE_VOLUME_UP /
    DOWN / MUTE), changes STREAM_MUSIC with `adjustStreamVolume` without `FLAG_SHOW_UI`, so the
    system panel stays hidden, and shows its own pill. Outside the player (and in
    picture-in-picture) the system panel shows as usual.

## 8. Build 36 list (owner, 2026-10-16; built)

1. **Mute button:** match the owner's reference, Apple TV's button: a 44pt dark-grey (#2C2C2E-ish) solid circle
   with a white speaker glyph (a slash across it when muted). It sits next to a same-style profile
   button in Apple TV.
2. **The Settings tab becomes "Preferences"** with a person icon.
3. **Tab bar ("dock"):** it is too big and blocky, so make it slimmer. Swiping left or right on a
   tab's page switches tabs.
4. **The hero crops wide backdrops** (Ramaiya Vastavaiya lost most of its picture in the tall hero).
   Decided fix: use TMDB's portrait poster *without text* (textless key art, `images` → `posters`
   with no language) for the tall hero, as Apple TV does. Fall back to the backdrop when TMDB has
   none.
5. **Artwork and search:**
   - On a movie or show page, pick another poster or backdrop from TMDB's alternatives.
   - Search finds titles by cast and director (store the credits per title).
6. **App icon:** keep D with the play button.
7. **Audio tab:** a ♫ icon, and Apple Music red (#FA2D48) as the accent inside the tab and on its
   selected tab icon. The rest of the app stays iOS blue.
8. **Selected tab, like Apple TV:**
   - a big dark-grey pill around the selected tab's icon and label;
   - its icon "lights up" as a disc, popping in with a spring. *Changed after build 36:* the
     selected tab is coloured: Video and Preferences iOS blue, Audio red, Search white.
9. **Dock, iOS 26 style (from the owner's screen recording; replaces build 36's cramped 54dp bar):**
   - a floating dark-grey glass capsule (64dp), 20dp off the screen sides, with a lighter grey pill
     behind the selected tab; that tab's icon and label take its colour (no disc), others are white;
   - scrolling a page down shrinks it to two circles, the current tab on the left and Search on the
     right; scrolling up, tapping the left circle or changing tab brings it back.

Notes on how they were built:
- The hero uses `Artwork.tallUrl`. A title without a text-free poster shows its backdrop over the
  top 60%, fading into a blurred copy, which crops far less than filling the whole hero.
- Dragging along the tab bar picks a tab. The pages themselves don't switch on a swipe, because
  the hero and the rows already scroll sideways.

## 9. iOS design language, not "fake iOS" (owner, 2026-10-19; built in build 56)

The owner said the app looked like fake iOS: the Material star and audio icons and many other
details gave it away. Decisions:
- **Icons:** Framework7 Icons (MIT), which are drawn in the SF Symbols style. Apple's SF Symbols
  and SF Pro may not ship on Android, so the font stays Inter.
- **Switches:** iOS's green pill switch.
- **Settings:** an inset-grouped list, as in iOS Settings. Each group is one rounded card with
  hairline separators, grey caps headers and coloured icon tiles. Choices open a pop-up menu
  from the value on the right.
- **Menus and dialogs:** iOS context menus (wide, rounded, 17pt rows, red destructive rows) and
  iOS alerts (centred title and message, full-width buttons under a hairline). Sheets get iOS's
  grabber and rounder corners.
- **Type:** iOS's type scale. Large titles are bold, not extra-bold, and body text is 17.
- Also swept: the spinner is iOS's spoked one; filter chips are capsules with no checkmark; tabs
  inside sheets and dialogs are iOS segmented controls; checkboxes became switches or
  selection circles; radio buttons became a trailing checkmark.

## 10. Apple Music, more precisely (overnight, 2026-10-19; build 57)

The owner asked for Apple Music to be copied more precisely, with less clutter. Built:
- **Song and track rows:**
  - "…" (and a long press) opens Apple Music's song menu: Play Next, Play Last, Add to a
    Playlist…, Favorite / Undo Favorite, Go to Album, Go to Artist, and Remove from Playlist on a
    playlist.
  - This replaces the "+" and "×" buttons that sat on every row.
  - A favorite song shows a small star by its "…".
- **The playing song:** four red bars that bounce while it plays and rest while it is paused.
  In lists they sit over the cover.
- **Album and playlist pages:** a red "…" circle beside "‹ Back" with Play Next and Play Last.
  A playlist's Delete moved into that menu.
- **Album details line:** Apple's caps style ("ROCK · 1975").
- **Library:** the refresh button became a "…" menu (Rescan Library). Scanning shows a small
  spinner with "Updating library…" instead of a progress bar.
- **Now Playing:**
  - The blurred cover behind the screen turns slowly.
  - Long titles scroll.
  - The transport buttons are sized like Apple's.
  - The lyrics button uses the quote-bubble glyph, filled when lyrics are open.
- **Lyrics:** 28sp bold, left-aligned. The lines around the sung one dim and soften.
- **Queue:**
  - "Playing Next" with square Shuffle and Repeat toggles, then the real list of songs to come
    (tap one to jump to it), and Stop Playing at the end.
  - While lyrics or the queue are open, a small header keeps the cover, title and star at the
    top.
- **Player:** `MusicPlayer.enqueue` / `PlayQueue.insert` (Play Next, Play Last) and
  `MusicPlayerState.upNext`.

## 11. More iOS, less clutter (overnight, 2026-10-19; build 58)

- **Text fields:** iOS's rounded grey field with no outline. The label sits above it as a small
  grey caption, as in iOS forms, instead of floating inside (`NovaTextField`).
- **Notices:** a glass capsule that drops in under the status bar, replacing Material's snackbar
  (`IosToastHost`).
- **Pull to refresh:** iOS's spoked spinner fades in with the pull.
- **Pushed pages (phone):** iOS 26's bar, with a round glass back button and the title centred
  in 17pt semibold.
- **Show seasons:** Apple TV's "Season 2 ⌄" pull-down menu, replacing a tab row.
- **Video playlists:** one "…" per row, with Remove from Playlist in its menu, instead of an ×
  on every row.
- **Play / Shuffle:** two equal grey pills with blue icons and labels, as in Apple's apps.
- **Apple Music:**
  - "Add to Playlist" is a sheet: New Playlist… (a name field opens in place), then each
    playlist with its cover.
  - The artist page opens on a big picture with the name over it and a round red Play button.
  - An album ends with "More by <artist>".

## 12. Apple's wording and quieter cards (overnight, 2026-10-19; build 59)

- **Title Case,** as Apple's apps use for headers, buttons and menus: Continue Watching, Recently
  Added, All Videos, TV Shows, Up Next, Add to Playlist, Play All, Mark as Watched and so on.
  Sentences stay in sentence case.
- **No play circle** over Continue Watching cards or episode stills; Apple TV draws none.
- **Progress:** a rounded bar inset from the bottom of the picture on a translucent track,
  replacing a strip along the edge.
- **Synopsis:**
  - the toggle reads "MORE" / "LESS" in white caps;
  - taglines are upright and bold, not italic.
- **Audio without access:** only the access card shows, not a red error line repeating it.

## 13. Builds 60–61 (overnight, 2026-10-19)

- **Mini player:** a sideways swipe skips, as in Apple Music: left for the next song, right for
  the one before. The song slides with the finger and springs back.
- **Buttons:** Material's outlined buttons ("Test connection", "Add folder", "Identify",
  "Mark as Watched" on tablet) became iOS's grey filled buttons.
- **Episode rows (build 61):** Apple TV's small grey caps above the title ("EPISODE 3"), replacing
  a blue "E3".
- **Tablet (build 62):** the same as the phone. No play circle on Continue Watching cards, and
  "Season 2 ⌄" in place of season chips.
- **Playlists (build 63):** "New Playlist…" sits first, with a red "+" tile, as in Apple Music,
  and opens an iOS naming alert. Album captions are 14sp regular, as in Apple Music, not
  medium weight.
- **Buttons (build 64):** the remaining button labels are in Title Case too ("Allow Access to
  Videos", "Add a Network Source", "Test Connection", "Try Again", "Back Up").

## 14. The open items (2026-10-20; build 65)

- **Backups carry the watchlist, video favorites and starred songs** (optional fields, so older
  backups still read).
  - Shows are saved by their library key, which is the same on every device, and come back at
    once.
  - Movies and videos travel as references to their files, and starred songs as references to
    theirs. They wait in the pending restore until a scan finds them, like playback history.
  - A second restore adds nothing twice, and the dates come along.
- **Video seek bar:** iOS's. No knob; a white bar on a translucent track that swells while it is
  held.
- **Folder path (Browse):** written as the Files app writes it, with folders above in blue and
  the current one in white, replacing Material chips.
- **Sources page (build 66):** an inset-grouped list like Preferences. Grey caps headers, each
  section one rounded card with hairlines, and the same coloured icon tiles. "New Playlist…" is
  in Title Case everywhere.
- **Phone player (build 67):** the bottom row keeps only Lock, Speed and picture-in-picture, as
  iOS's player keeps its bar short. Aspect Ratio, Repeat, Shuffle and Rotate moved into the "…"
  menu, each showing its current state.

## 15. Owner's notes on build 67 (2026-10-20; build 68)

- **Hero, camera and saved videos:** the sharp frame is centred in the room under the status bar.
  It used to hang at the top with an empty blurred band below it.
- **Now Playing:** no grabber line at the top. Dragging down anywhere still closes it.
- **Smoother Now Playing drag (build 69):**
  - The screen follows the finger frame by frame.
  - It closes when let go past 140dp or with a quick flick, and carries on off screen from where
    it was.
  - It rounds its top corners as it is pulled.
  - The turning background is drawn from a 32px copy of the cover, which upscaled looks blurred.
    It used to be a full-screen blur redrawn every frame, which made the drag stutter.
  - The mini player's sideways swipe tracks the finger the same way.

## 16. Swipe between tabs; the shrunk bar's other section (owner, 2026-10-20; build 70)

- **Swiping between tabs:** a sideways swipe on a tab's own page moves to the next or previous
  tab (Video → Audio → Preferences → Search).
  - The page follows the finger a little, and the new tab slides in from that side.
  - Rows and the hero that scroll sideways keep their own swipes.
  - This replaces build 37's "no page swipes" decision.
- **Shrunk bar:** its left circle opens the other section, never the one being looked at. Audio
  shows Video, Video shows Audio, and Preferences and Search show Video.
- **Tab icons (owner's pick from six options, build 71):** iOS-style outlines. Video is a TV,
  Audio a note with a list, Preferences a gear and Search a magnifier
  (`ic_tab_video` / `ic_tab_audio` / `ic_tab_settings`).

## 17. The remaining list (2026-10-20; build 72)

- **Tablet player:** the same quiet bars as the phone. Aspect Ratio, Repeat, Shuffle and Rotate
  are in its "…" menu, so the transport row is just skip, play/pause and skip.
- **Progress bars:** iOS's plain rounded bar (`IosProgressBar`) replaces Material's
  LinearProgressIndicator with its gap and end dot. That covers resume progress, scans and
  folder loading.
- **Tablet swipes between tabs too,** through one shared `Modifier.swipeBetweenTabs`, and tab
  changes slide in from their side (`tabSlide`).
- **Now Playing "…":** Apple Music's circle beside the star, with Add to a Playlist… (the Apple
  Music sheet), Go to Album and Go to Artist. The last two close Now Playing, switch to the Audio
  tab and open that page (`MusicViewModel.requestPage`).
- **Swipe to delete (build 73):** iOS's swipe left on playlist rows, for videos and for songs.
  It uncovers a red Remove button; a long swipe removes at once (`SwipeToDelete`).
- **Add to Playlist sheet (videos):** a centred title, as iOS sheets have.

## 18. Owner's notes on build 73 (2026-10-20; build 74)

1. **No empty band under the hero:** the dots sit 4dp above its bottom (was 16), and the first
   section after it, Continue Watching or the Watchlist, starts 4dp below rather than a whole
   section gap.
2. **No sentences cut off mid-way:** titles, meta lines, subtitles and captions wrap to a second
   line before they end in "…". That covers cards, posters, the hero, details pages, rows,
   dialogs, the queue and tablet panes. One line is kept only where a second can't fit: tab and
   button labels, nav bar titles, player chrome, the mini player, and Now Playing's scrolling
   title.
3. **Details pages show the title's TMDB logo,** as Home's hero does. Plain text is used only
   when a title has no logo.
- **Recent searches (build 75):** with the field empty, Search lists the latest searches under
  "Recent Searches" with Clear, as on iOS. Tap one to run it again.
  - A search is remembered on the keyboard's Search key, or when you leave Search with results
    on screen.
  - Eight are kept, newest first and without repeats. They stay on the device.
- **Tablet search (build 76):** the same Recent Searches list as the phone.
- **Swipe right to go back (build 77):** iOS's back gesture.
  - On a page opened from a tab (details, sources, playlists, folders) a right swipe goes back,
    and the page follows the finger.
  - In Audio, an album, artist or playlist page swipes back to the Library instead of changing
    tab.
- **Haptics (build 78):** a light tick, as iOS gives, on a tab change (tapped, dragged along the bar or swiped), a settings switch, and a mini-player skip.
- **Touch feedback (build 79):** iOS-style instead of ripples. A tapped row, card or item lights up evenly and fades back (`IosTouch`). Material buttons keep a faint ripple, as they cannot take another effect.
- **Collapsing large titles (build 80):** in Preferences and every Audio page, once the big title scrolls away it shows small and centred in a dark bar at the top, as on iOS (`CollapsedTitleBar`).
- **Context menus lift (build 81):** a poster, card or song row grows slightly while its "…" menu is open, as iOS lifts the item a menu is for.
- **Press squish (build 82):** the hero Play pill, the star and +, and the round glass buttons (back, Search, the shrunk bar's circles) shrink a little while held, as iOS 26 buttons do (`Modifier.pressScale`).
- **Library list (build 83):** the Library links at the end of Home take the look of the Library list in Apple's TV and Music apps: blue icons, grey chevrons and hairlines inset to the labels.
- **Empty states (build 84):** iOS's "content unavailable" look — a large grey symbol on its own (no tinted circle), a bold title and grey text.
- **Playlists (build 85):** as in Apple Music, a playlist with four or more albums shows their covers in a 2×2 grid, ends with "N songs, M minutes", and an empty one says how to add songs.
- **Source tiles (build 86):** Add Network Source and the iPad's Browse show each kind of source in the coloured tile it gets in Preferences, under grey capital section headers, as iOS Settings does.
- **Fewer buttons in Preferences (build 87):** folders and network sources lose their pencil and bin; one "…" menu holds Edit and Remove (in red), and tapping a network source still edits it.
- **Permission row (build 88):** All files access says only Allowed / Not allowed with a blue Allow; its explanation moves under the group in grey, as iOS Settings does.
- **Information (build 89):** the file facts at the bottom of a phone video page drop their card for the Apple TV Information layout — a small white heading over each grey value, short ones two to a line.
- **Volume (build 90):** Now Playing has Apple Music's volume slider under the controls — the phone's media volume between a quiet and a loud speaker, following the volume keys.
- **iPad video page (build 91):** only Play (and Start Over) stay as buttons; Identify, Change Artwork, Mark as Watched and Add to Playlist move into one round "…", as on the Apple TV app's iPad pages. Preferences' "Not allowed" drops to the regular grey subtitle style.
- **Time left (build 92):** the video player's scrubber shows "−time left" on the right by default, as the iOS player does; a tap still switches to the full length.
- **Swipe tick (build 93):** swiping a row to delete gives a light tick at the point where letting go deletes it, as iOS does.
- **Build 94 (owner's list):**
  - Now Playing's background is a still gradient in the cover's colours, as Apple Music paints it, crossfading between songs; the turning cover is gone (`CoverGradient`).
  - A speaker button between Lyrics and Queue, where Apple Music has AirPlay, opens Android's output picker (phone, Bluetooth, cast devices).
  - Playlists in the list show the same 2×2 grid of album covers as their own page.
  - The library's All / Movies / TV Shows pills are an iOS segmented control.
  - The video player's title sits at the bottom left above the scrubber, as on iOS.
- **Build 95 (owner):**
  - Now Playing loses the volume slider and the output button again: the system's control centre does both; the app stays clean.
  - The video player follows Samsung's own player: back and the title at the top with only subtitles and "⋮" (audio track and Play on TV moved into it; cast shows only while casting); plain play/pause and 10-second steps with no circles; a thin seek bar with a small round knob showing the full length; lock and rotate bottom left, speed and picture-in-picture bottom right.
  - Videos open in their own shape: the player reads the file's size and rotation before playing, so a reel filmed upright opens in portrait straight away (rotate is still at hand).
- **Play and Delete (build 96, owner):** on a phone video page the blue Play (or Resume) is as wide as the poster, and a grey Delete with a red bin fills the rest of the row. Delete removes the file from the phone: Android's own "Allow Nova Fork to delete this video?" for device storage, our confirmation for a picked folder (folders picked from now on also grant write access; older ones say to add the folder again). The video leaves the library at once and its page closes. Network and server videos have no Delete. On the iPad, Delete is in the round "…" menu.
- **Samsung player, closer (build 97, owner's screenshots):** the whole picture dims; a bold title (no back arrow) with repeat, "CC" and a vertical "⋮" (Audio Track, Play on TV, Queue, Shuffle, Play in Background); previous, play/pause and next on dark see-through discs (previous restarts a single video); a big white knob on the seek bar, the times above it in portrait; fit, pop-up, a "1.0x" speed pill, rotate and lock along the bottom, spread across in portrait.
- **Home hero buttons (build 98, owner):** the hero's Play is iOS blue (no longer white) and as wide as the poster above it; the freed width holds the star, the "+" and, for a video file on this phone, a round red bin that deletes it (the same Delete as the video page).
- **Safe delete (build 99, owner):** Delete always asks first in the app (some phones granted Android's request with no prompt and deleted at once). On Android 11 and later the video goes to Android's bin, not deleted: Home › Library › Recently Deleted lists the bin with days left and Restore. Picked-folder videos have no bin; their question says "Delete for Good".
- **Polish (build 100, owner chose all six):**
  1. Now Playing's cover sinks back with a light shadow when paused and springs forward with a deep one when playing, as in Apple Music.
  2. A movie or show page zooms out of the poster that was tapped and shrinks back into it on Back (iOS 18's zoom transition), on the phone.
  3. Recently Added groups the videos under month headings ("October", "March 2024"), as Photos does.
  4. A soft light sweeps across pictures while they load, instead of flat grey boxes.
  5. Home's hero backdrop drifts in a slow zoom.
  6. A double tap shows Samsung's side bubble: three chevrons lighting up in turn and "10 seconds".
- **A real sheet (build 101, owner: "the swipe down is fake"):** Now Playing is moved by one 0–1 value shared by its slide, the finger's drag and the page behind. While it is open the page behind sinks back (7% smaller, rounded, dimmed) as iOS stacks a sheet on its page; pulling Now Playing down brings that page forward in step with the finger; near the bottom Now Playing fades into the mini player, which fades back in. Let go and it springs on from the speed of the flick (`NowPlayingSheet`).
- **Cover flight (build 102, owner):** while Now Playing opens or closes, one cover travels between the big cover and the mini player's: it rides along with the sheet as it starts down, then leaves it and shrinks into the mini player as the sheet fades (and the reverse on opening), as in Apple Music (`CoverFlight`).
- **Cover flight fix (build 103, owner's recording):** the flying cover started loading only when a flight began, so the cover blanked and then faded in as a ghost with the placeholder note. It is now decoded ahead at one fixed size, kept composed unseen, and drawn without a crossfade.
- **Polish batch A (build 104, owner):**
  - Continue Watching shows a file's frame where it was left (Apple TV's Up Next), not the poster (`Artwork.frameAtMs`).
  - Home's hero page takes a soft tint of its poster's colours, the backdrop fading into it instead of black.
  - Lyrics as in Apple Music: the sung line bright and full size; lines further away dimmer, smaller and softer, every change gliding.
  - Now Playing's scrubber: the times grow and brighten while held; the song's start and end give a light tick.
- **Polish batch B (build 105, owner):**
  - iOS edge bounce on every phone page instead of Android's stretch: past an end a list follows the finger with growing resistance and springs back; a fling into an end overshoots a little (`iosOverscroll`).
  - A movie or show page pulled down from its top shrinks toward its poster and closes when let go far enough, the zoom back finishing it.
  - Scrubbing a file on the phone shows the frame at that point above the time, as Samsung's player does (read with one MediaMetadataRetriever, the latest request winning).
- **Polish batch C (build 106, owner):**
  - Real frosted glass: the tab bar, the round glass buttons and the mini player blur what scrolls under them (Haze 1.1.0, Android 12 and later; a dark tint as before on older phones) (`frostedGlass`).
  - Long-press on a poster as iOS: the whole screen blurs and dims while a sharp copy of the card floats over it, a little larger, with its menu below (`LocalMenuFocus`).
- **Cover flight, second fix (build 107, owner's recording):** the real cover hid in the draw phase while the flying one moved in the next composition, so the cover blinked out as a drag began; and the hidden cover's shadow stayed as a dark box around the shrinking one. The flying cover is now laid out once at the big cover's size and only moved and scaled in the draw phase, in the same frame the real ones hide; the hidden cover drops its shadow; and the big cover is measured within Now Playing, so the sheet's slide never skews it.
- **Polish set 3 (build 108, owner: all in one go):**
  - Movie and show pages: the backdrop scrolls at half speed (parallax).
  - Home's dots already fill with the auto-advance timer (Apple TV).
  - The player grows out of the Play button that was tapped (a scale-up activity animation from the last touch).
  - Two-finger pinch in the player: spread to fill the screen, pinch in to fit; the player names the fit.
  - The tab bar's pill stretches as it slides (front edge fast, back edge following), as iOS 26's glass.
  - An album page glows softly in its cover's colours behind the cover.
  - The mini player's cover carries small bars, dancing while music plays, still when paused.
  - Grid posters and search results fade up one after another the first time they show.
  - A soft haptic knock as a poster's menu opens.
  - Android 12+: a plain black launch screen that fades into Home.
  - Continue Watching cards' menu: "Remove from Continue Watching" (forgets where it was left).
- **Build 109 (owner):**
  - Crash fix: the library pages (All Videos, Recently Added, …) and Recently Deleted crashed on the owner's Android 16 phone since the frosted glass (build 106). Haze, the backdrop-blur library, was the one part that ran only on a phone (it is off under the tests), so it came out; the glass is the tinted one again.
  - The classic iOS tab bar instead of the floating capsule: docked full width along the bottom, dark and see-through with a hairline on top, icons over names, the selected one coloured; it no longer shrinks on scroll. The mini player docks just above it whenever there is a queue, playing or paused.
  - Home's hero tint now comes from the foot of the backdrop, not the poster, and is strongest where the backdrop fades out, so the picture flows into it.
- **Build 110:** over the tinted hero the backdrop ended in a hard line (its fade went to clear, which is no fade); the picture itself is now masked out at its foot, so it melts into the tint.
- **Build 111 (owner, all in one go):**
  - Swiping right on a page goes back as on iOS: the page follows the finger and the page it came from lies underneath, a little to the left and shaded, sliding out as it is uncovered (scroll position and all); let go past a third or flick to go back, otherwise it springs back.
  - A movie or show page's title appears small in the middle of its top bar once the big title has scrolled away, and the bar turns dark with a hairline.
  - Posters and cards sink in and darken a little while pressed (no ripple), as Apple TV's tiles do.
  - Pinch All Videos or Recently Added to change the poster size (2 to 4 a row, one step per pinch, with a tick); the posters glide to their new places.
  - The hero's "+" turns round into a tick and the star pops as it fills; adding shows iOS's HUD ("Added to Watchlist", "Added to Favorites") in the middle of the screen.
  - The player's top and bottom bars slide in from the edges as they appear and back out as they hide.
  - The artist page's picture drifts at half speed as the page scrolls and grows into the gap when the page is pulled down.
  - Search: "Cancel" slides in beside the field while typing (the field narrows for it); it empties the field and puts the keyboard away.
  - Album track lists already show moving bars in place of the playing song's number.
- **Build 112 (owner):**
  - Movie and show pages are no longer blank black under the backdrop: the backdrop melts into a deep version of its own bottom colour (dark enough to read as the picture's shade on an AMOLED screen), which fades to true black by the end of the summary.
  - Now Playing's cover flies to and from the mini player in one straight diagonal, in step with the sheet from the first pixel, as in Apple Music; it used to ride down with the sheet and then swing across.
  - The old Apple Music mini player to go with the old tab bar: a full-width strip docked on the bar in the same dark material, a hairline between, a bigger cover and the song's title alone; it slides up from behind the bar.
  - Search: the hint stays on one line beside Cancel, and putting the keyboard away ends typing so Cancel goes with it.
