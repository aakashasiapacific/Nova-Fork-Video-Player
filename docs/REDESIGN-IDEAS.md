# Redesign ideas: owner's notes (not built yet)

These are the owner's decisions and requests for the Apple TV-style redesign, plus what their
screenshots show. They come before `docs/APPLE-TV-SPEC.md` wherever the two disagree. Nothing here is
implemented yet.

Reference screenshots were sent in chat; they are not in the repo:
- Apple TV app, Home, phone, light mode
- Nova Fork build 24, Home

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

## 4. Rows under the hero

- **Row headers:** large bold titles with a chevron, e.g. "Top 10 TV Shows ›". The whole header opens
  the full list.
- **Our rows:** Continue Watching, Recently Added, Movies, TV Shows, Videos.
- **Cards:** landscape 16:9 artwork with the title and caption below. Our current "Recently added"
  cards are tall portrait crops with truncated titles.

## 5. Audio tab: an Apple Music "ditto" (new feature)

The app has no music/audio library today. This needs backend work as well as UI.

Backend:
- **Scanning:** audio files from MediaStore.Audio on the device, picked folders, and later network
  sources.
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

## 6. Open questions, to settle before building

1. **Library and Browse:** with the tabs now Video / Audio / Settings / Search, where do the library
   categories and the sources list (folders, network shares, servers) go? Options:
   - inside the Video tab, behind row headers and a "Library" entry;
   - in Settings.
2. **Theme:** light, dark, or follow the system? The Apple TV screenshot is in light mode.
3. **The hero's round "+" button:** add to a playlist, a new "Watchlist", or something else?
4. **Music sources:** only files on the phone and in picked folders, or also network shares and
   Jellyfin/Plex music?
5. **Tablet:** the same four tabs as a floating bar at the top, as on iPad?
6. **Search:** one search across videos and music, or separate?
