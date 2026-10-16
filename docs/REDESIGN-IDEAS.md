# Redesign ideas: owner's notes

**Built on the phone (round 1, branch `ccr-987fc293-7gnds9`):**
- the black theme (section 0);
- the four tabs (section 1);
- no title and a clear status bar (section 2);
- the hero, its dots, the muted preview and the mute button (section 3);
- the watchlist "+" and its row (section 6.3).

The tablet got the four tabs and the colours in its old side rail; its floating top bar (6.5) and
hero come later. Still to do on the phone:
- title logos from TMDB;
- 16:9 landscape cards in the rows (section 4);
- personal videos' frame-based hero art.

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
   - its icon "lights up" as a disc, popping in with a spring. *Changed after build 36:* the disc
     is coloured per tab: Video and Preferences iOS blue, Audio red, Search white.
9. **Dock size:** the owner will send a new idea (build 36's 54dp bar looked cramped).

Notes on how they were built:
- The hero uses `Artwork.tallUrl`. A title without a text-free poster shows its backdrop over the
  top 60%, fading into a blurred copy, which crops far less than filling the whole hero.
- Dragging along the tab bar picks a tab. The pages themselves don't switch on a swipe, because
  the hero and the rows already scroll sideways.
