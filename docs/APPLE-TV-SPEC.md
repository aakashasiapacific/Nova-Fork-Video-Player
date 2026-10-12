# Target: a 1:1 copy of the Apple TV app (iOS 26 / iPadOS 26), on Android

The owner wants Nova Fork to look and behave like the Apple TV app, screen for screen. Match
layout, proportions, spacing, typography scale, colours, materials, motion and interaction.
Only these differ, for legal/technical reasons: the font is Inter (SF Pro may not ship on
Android), icons are our Material Symbols drawables drawn at SF-Symbol-like weights, no Apple
names or logos, and the content is the user's own library.

## Global

- Dark appearance, pure black canvas `#000000`. Grouped/elevated surfaces `#1C1C1E`, raised
  `#2C2C2E`, separators `#38383A` (0.5dp hairlines). Text: primary `#FFFFFF`, secondary
  `#EBEBF5` @ 60%, tertiary @ 30%. Destructive `#FF453A`. Toggle on `#30D158`.
- Type (Inter, iOS scale, sp): Large Title 34 bold (−0.4 tracking) · Title 1 28 bold · Title 2 22
  bold · Title 3 20 semibold · Headline 17 semibold · Body 17 · Callout 16 · Subhead 15 ·
  Footnote 13 · Caption 1 12 · Caption 2 11. Small-caps labels ("EPISODE 3", "SEASON 1") are
  Caption 1 semibold, uppercase, tracking +0.6, secondary colour.
- Liquid Glass (iOS 26) for everything that floats: tab bar, top buttons over artwork, player
  chrome, sheets, menus. Blur ~ 20–30dp of what is behind, white tint 8–14%, a 0.5dp light rim,
  and a soft specular highlight at the top edge. Capsule and circle shapes only.
- Primary action button: white capsule, black Headline text, filled play glyph on the left
  (e.g. "▶ Play", "▶ Resume", "▶ Play S2, E4"). Height 50dp on phone (full width in details),
  56dp on iPad. Secondary actions: 50dp glass circles (icon only) next to it, or glass capsules.
- Row headers: Title 2 bold + a chevron "›" in secondary colour right after the text; the whole
  header is tappable (opens the full list). 20dp (phone) / 40dp (iPad) leading margin; 12dp below.
- Cards: 16:9 artwork, 10dp corners on phone / 14dp on iPad, no border, no shadow; text sits
  BELOW the artwork: title (Subhead semibold, 1 line) and caption (Footnote, secondary).
  Continue Watching cards show a 3dp progress capsule (white on white-20%) INSIDE the artwork,
  6dp from the bottom, 12dp side insets, and the caption reads "S2, E3 · 20m left".
  Posters (2:3) only in Library/category grids.
- Rows scroll horizontally with the first card aligned to the page margin, 12dp (phone) / 20dp
  (iPad) gaps, and snap to card edges.
- Lists (Settings, Browse, Information): iOS inset-grouped — 16dp side margin, 10dp corners,
  `#1C1C1E` rows 44dp min height, hairline separators inset to the text, chevron "›" on
  navigable rows, values in secondary colour on the right, coloured 29dp rounded-square icons
  (white glyph) on the left like the iOS Settings app. Section headers in Footnote uppercase
  secondary; footers in Footnote secondary.
- Sheets: rounded 12dp top corners, grabber (36×5dp, white @ 30%), glass or `#1C1C1E`, detents
  medium/large, swipe down to dismiss. Alerts: centred 270dp card, Title "Headline", message
  "Footnote", buttons separated by hairlines, bold for the default action.
- Context menus (long press on cards): glass rounded panel with rows (icon right-aligned), the
  pressed card lifted with a slight scale (1.03) and the background dimmed.
- Motion: spring-like ease (fast out, gentle settle), 250–350ms; press = scale 0.96 + 0.85 alpha.

## Phone (iPhone)

- **Tab bar** (iOS 26): floating Liquid Glass capsule at the bottom, 64dp tall, 20dp from the
  sides, above the navigation bar inset. Items: Home, Library, Browse (icon above a Caption 2
  label), selected item = white with a lighter glass lens behind it, others secondary. A
  separate 64dp glass circle with a magnifier to the right of the capsule = Search. Content
  scrolls underneath it and is padded so the last row clears it.
- **Home**:
  - No visible title bar. A glass circle "account" button (person glyph) top-right over the
    hero → Settings. (Settings is not a tab.)
  - Hero carousel: full-bleed from the very top (under the status bar), height = screen width
    × 1.25. Artwork = backdrop cropped to fill; a bottom gradient to black over the lower 45%.
    Bottom-left content with 20dp margins: title as large bold text (Title 1, up to 2 lines)
    standing in for the logo, one line of genre text ("Science Fiction · Adventure"), then the
    white "▶ Play" capsule (auto width, min 140dp) and a glass "+" / info circle. Page dots
    (6dp, white / white 35%) centred 12dp under the buttons. Swipe between pages; auto-advance
    every 8 s. The hero scrolls away with the page (no parallax needed, subtle is fine).
  - Then rows: "Continue Watching ›" (16:9 cards 300dp wide), "Recently Added ›", "Movies ›",
    "TV Shows ›", "Home Videos ›" (16:9 cards 220dp wide using backdrops / stills; fall back to
    poster art cropped). 32dp between rows.
- **Library**: Large Title "Library" (scrolls into an inline title in a glass header when
  scrolled), account circle top-right. A list (inset-grouped style but full-bleed rows with
  hairlines, like Apple's Library): "Movies", "TV Shows", "Home Videos", "Streams" each with a
  chevron and count. Under it "Recently Added" header and a 2-column grid of 16:9 cards.
  Category page: Large Title (e.g. "Movies"), a "Sort ▾" glass capsule (menu: Recently Added,
  Title, Release Date, Rating), 3-column 2:3 poster grid with title + year below.
- **Search**: Large Title "Search" and the iOS search field (36dp, `#1C1C1E` @ 100%, 10dp
  corners, magnifier, placeholder "Movies, Shows, and More"). Before typing: "Recently Added"
  list rows (thumbnail 16:9 64dp + title + kind). Results: rows grouped "Movies", "TV Shows",
  "Videos" with 16:9 thumbnails.
- **Movie / video details**:
  - Full-bleed artwork at the top (height = width × 1.25), bottom gradient to black.
  - Glass circles over it: back "‹" top-left; "…" (more: Identify, Mark as Watched) top-right.
  - Over the bottom of the artwork: title (Title 1 bold, centred), genre line centred.
  - Full-width white "▶ Play"/"▶ Resume" capsule (20dp margins), then under it, when resuming,
    a thin progress bar with "1h 41m left" and a "Start Over" text button.
  - Description (Subhead, 3 lines + "MORE" in bold white small caps).
  - Info line centred: "Drama · 2024 · 2 hr 46 min" then badge row: outlined 4dp-corner boxes,
    Caption 2 semibold secondary: "4K"/"HD", "★ 8.2", "CC" when subtitles exist.
  - Sections below (Title 2 headers): "About" (a `#1C1C1E` rounded card with title, genres and
    the full overview), "Information" (two-column key/value grid in Footnote: Released, Run
    Time, File, Size, Resolution, Source), "Up Next"/"From <show>" when it is an episode.
- **Show details**: same top. Under the description: "Season 1 ▾" glass capsule menu (lists
  seasons), then an episode carousel of 16:9 cards 300dp wide: artwork with progress capsule,
  "EPISODE 3" small caps, title (Subhead semibold), 3-line description (Footnote secondary),
  duration ("47 min"). The next-up episode is scrolled into view.
- **Browse** (our sources): Large Title "Browse". Inset-grouped sections exactly like iOS
  Settings: "ON THIS DEVICE" (Device Storage), "FOLDERS" (+ "Add Folder" blue text row),
  "NETWORK SHARES" (+ "Add Network Share"), "STREAMS" (saved streams + "Add Stream", "Open
  Network Stream"). Coloured icon squares (blue folder, green network, orange stream, gray
  phone). Swipe-to-delete reveals a red "Delete" (never on Device Storage). Folder pages: Large
  Title = folder name, rows for subfolders (folder icon, count, chevron) and 16:9 video rows.
- **Settings** (opened from the account circle; pushed page with a back chevron): exactly the
  iOS Settings app look: Large Title "Settings", inset-grouped sections with coloured icon
  squares, toggles, value rows that push a sub-page with a checkmark list. Sections: Library,
  Posters & Details (shows "Built In" when the app has a TMDB key; Advanced sub-page holds the
  key/server/DNS), Playback, Appearance, About.

## Tablet (iPad)

- **Tab bar**: floating glass capsule centred at the top (iPadOS 26): text tabs "Home",
  "Library", "Browse" (Subhead semibold; selected = white text on a lighter glass pill), plus a
  separate glass circle with a magnifier to its right (Search) and a separate glass circle with
  a person glyph at the far right (Settings). Content scrolls under it.
- **Home hero**: full-bleed, height = 78% of the screen height in landscape (60% in portrait).
  Leading + bottom gradients. Bottom-left (48dp margins, max text width 520dp): title 48sp
  bold, genre/meta line, 2-line description, white "▶ Play" capsule (56dp) + glass "+" circle.
  Page dots bottom-centre. Rows below exactly like phone but larger: Continue Watching cards
  400dp, other rows 300dp (16:9), 40dp margins, 20dp gaps.
- **Library**: sidebar-free iPad layout: Large Title, the category list as a horizontal row of
  glass capsules (All · Movies · TV Shows · Home Videos · Streams) and a "Sort ▾" capsule;
  adaptive 2:3 poster grid (min cell 170dp) with titles below. Landscape keeps the preview pane
  on the right (frosted glass over a blurred copy of the selected item's backdrop) with title,
  meta, badges, Play capsule, "Details ›", description.
- **Details**: full-screen artwork background (the whole page), heavy bottom and leading
  gradients; content bottom-left (48dp margins): title 48sp bold, meta line + badges, Play
  capsule + glass circles (Start Over, Watched, Identify), description (max 600dp). Below the
  fold: "Episodes" (season capsule + 360dp 16:9 episode cards), "About" card and "Information"
  grid in three columns.
- **Browse**: iPad Settings-style split view: 360dp sidebar with the inset-grouped source list
  on `#000000`, detail pane on the right with Large Title, folder rows and a grid of 16:9 video
  cards.
- **Settings**: iPad Settings split view (sidebar of sections with icon squares, detail pane).

## Player (both)

- Black. Video fills the screen. Controls appear on tap and fade after 3 s while playing.
- Top-left: glass circle ✕ (close) and glass circle PiP. Top-right: glass capsule containing a
  speaker glyph + thin volume slider (white fill) on iPad; on phone just a glass circle mute
  button.
- Centre: three large white glyphs WITHOUT backgrounds: "10" back arrow (36dp), play/pause
  (56dp), "10" forward arrow (36dp), 64dp apart, soft drop shadow.
- Bottom (20dp margins phone / 48dp iPad): title (Headline) and subtitle ("S1, E3 · Pilot",
  Subhead secondary) left-aligned; under them a thin scrubber: 4dp capsule track white @ 30%,
  white fill, grows to 8dp while dragging with a preview time bubble; elapsed time left
  (Caption 1, monospaced digits) and "−remaining" right. Bottom-right glass circles: speech
  bubble (audio & subtitles menu: two columns "Audio" / "Subtitles" with checkmarks, "Add
  Subtitle File…", subtitle delay stepper), speed ("1×" text), aspect, lock, rotate (hidden when
  rotation is not possible).
- Menus open as glass popovers anchored to their button (iPad) or glass sheets (phone).
- "Up Next" glass card bottom-right near the end with a circular countdown on the play glyph.
- Gesture HUDs (volume/brightness/seek) as small glass capsules with a glyph and a bar.
