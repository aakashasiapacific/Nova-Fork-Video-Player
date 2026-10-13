# Nova Fork design system

Nova Fork is a 1:1 copy of the **Apple TV app on iOS 26 / iPadOS 26**. `docs/APPLE-TV-SPEC.md` says
*what* every screen looks like; this document is the toolkit for building it: tokens, sizes, and every
component in `ui/theme`, `ui/adaptive` and `ui/common`, with exact signatures and when to use each.
Only the font (Inter), icon drawables, names/logos and content differ from Apple's app.

**No Material UI.** Never use these material3 composables in app UI: `Button`, `FilledTonalButton`,
`OutlinedButton`, `TextButton`, `IconButton`, `FloatingActionButton`, `Chip`/`FilterChip`/`AssistChip`,
`NavigationBar`, `NavigationRail`, `TopAppBar`, `Switch`, `Slider`, `AlertDialog`, `ModalBottomSheet`,
`Card`, `TabRow`/`ScrollableTabRow`, `SegmentedButton`, `CircularProgressIndicator`,
`LinearProgressIndicator`, `DropdownMenu`, `Snackbar`. Allowed: `Text`, `Icon`, `MaterialTheme` (as a
token holder), `Box`/`Surface`, lazy lists/grids, `HorizontalPager`, `BasicTextField`, `Dialog`/`Popup`
windows, and material3 `PullToRefreshBox` with `NovaRefreshIndicator`. Every banned component has a
replacement below.

| Instead of | Use |
|---|---|
| `Button` / `FilledTonalButton` / `OutlinedButton` | `PrimaryCapsuleButton` (white "▶ Play"), `GlassCapsuleButton` |
| `IconButton` / `FloatingActionButton` | `GlassCircleButton`, `AccountButton`, `BackButton`, `GlassMenuButton` |
| `TextButton` | `TextAction` |
| `NavigationBar` | `GlassTabBar` (phone) |
| `NavigationRail` / `TabRow` | `GlassTopTabs` (iPad) |
| `TopAppBar` | `GlassNavBar` + `LargeTitleHeader` |
| `Switch` | `IosToggle`, `ListToggleRow` |
| `Slider` | `ThinSlider` |
| `SegmentedButton` / `FilterChip` / `AssistChip` | `SegmentedControl`, `GlassCapsuleRow`, `GlassCapsuleButton(selected = …)` |
| `TabRow` / `ScrollableTabRow` inside a page | `SegmentedControl` (2–4 options), `GlassCapsuleRow`, `PullDownButton` ("Season 1 ▾") |
| `Card` | `InsetGroup` (lists), `AboutCard`, a clipped `Box` with `cardRadius` (artwork) |
| `ListItem` / `RadioButton` / `HorizontalDivider` | `ListRow` (`checked = true` for the chosen option), `Hairline` |
| `OutlinedTextField` / `TextField` | `ListTextFieldRow` (forms in an `InsetGroup`), `IosTextField` (alerts, single fields), `SearchField` |
| `Scaffold` | `GlassPage` (content + floating chrome) |
| `ModalBottomSheet` | `GlassSheet` |
| `AlertDialog` | `IosAlert` |
| `DropdownMenu` | `GlassMenu`, `PullDownButton`, `GlassMenuButton` |
| `CircularProgressIndicator` | `ActivityIndicator`, `NovaRefreshIndicator` |
| `LinearProgressIndicator` | `ProgressBar`, `ScanBanner` |
| `Snackbar` / `SnackbarHost` | `GlassToast` |

Files: `ui/theme/Theme.kt` (palette, colours, type, theme), `ui/theme/Motion.kt` (motion, radii),
`ui/adaptive/DeviceLayout.kt` (phone/tablet and sizes), and in `ui/common`: `Glass.kt`, `Glyphs.kt`,
`Buttons.kt`, `Navigation.kt`, `Indicators.kt`, `Controls.kt`, `Fields.kt`, `Lists.kt`, `Sheets.kt`,
`Content.kt`, `Art.kt`, `Components.kt` (legacy helpers). All are in package `com.aakash.novafork.ui.common` unless noted;
import each symbol explicitly (no wildcards).

---

## 1. Theme and tokens

`NovaRoot` wraps the app in `NovaTheme(settings.themeMode)`; the player uses `NovaPlayerTheme` (always
dark). Both provide `LocalNovaPalette`, a Material colour scheme mapped onto the palette, `NovaTypography`
(Material styles mapped onto the iOS scale) and `LocalContentColor = label`, so a bare `Text` is white
on black in dark mode.

```kotlin
@Composable
fun NovaTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit)
```

```kotlin
@Composable
fun NovaPlayerTheme(content: @Composable () -> Unit)
```

### 1.1 Colours (`com.aakash.novafork.ui.theme.NovaColors`)

DARK (the Apple TV look) is a pure black canvas; LIGHT uses the iOS light grouped colours.
Read appearance colours as `NovaColors.label`, `NovaColors.cell`, … — they are `@Composable` getters,
so read them in composition and capture the value before entering `drawBehind`/`Canvas`/`graphicsLayer`
lambdas. The full palette object is `LocalNovaPalette.current` (`NovaPalette`; the two instances are
`DarkPalette` and `LightPalette`).

| `NovaColors.` | Dark | Light | Use |
|---|---|---|---|
| `background` | #000000 | #F2F2F7 | page canvas |
| `cell` | #1C1C1E | #FFFFFF | grouped rows, About card |
| `cellPressed` | #3A3A3C | #D1D1D6 | pressed row |
| `raised` | #2C2C2E | #F2F2F7 | controls on a cell; grouped rows inside sheets |
| `separator` | #38383A | #C6C6C8 | 0.5dp hairlines |
| `label` | #FFFFFF | #000000 | primary text |
| `secondaryLabel` | #EBEBF5 @ 60% | #3C3C43 @ 60% | secondary text, captions, chevrons in headers |
| `tertiaryLabel` | #EBEBF5 @ 30% | #3C3C43 @ 30% | row chevrons, disabled text |
| `quaternaryLabel` | #EBEBF5 @ 18% | #3C3C43 @ 18% | faint fills |
| `fill` | #767680 @ 24% | #767680 @ 12% | segmented track, stepper, pressed menu rows |
| `searchField` | #1C1C1E | #E3E3E8 | search field |
| `toggleOff` | #39393D | #E9E9EA | switch track off |
| `segmentThumb` | #636366 | #FFFFFF | selected segment |
| `tint` | #0A84FF | #007AFF | links, text buttons, checkmarks (blue) |
| `green` | #30D158 | #34C759 | toggle on, network icon |
| `red` | #FF453A | #FF3B30 | destructive |
| `orange` | #FF9F0A | #FF9500 | stream icon |
| `yellow` | #FFD60A | #FFCC00 | rating |
| `purple` | #BF5AF2 | #AF52DE | icon squares |
| `pink` | #FF375F | #FF2D55 | icon squares |
| `teal` | #40C8E0 | #30B0C7 | icon squares |
| `indigo` | #5E5CE6 | #5856D6 | icon squares |
| `gray` | #8E8E93 | #8E8E93 | device icon |
| `scrim` | #000000 @ 45% | #000000 @ 20% | dim behind modals |

Fixed colours (same in both appearances): `NovaColors.Black`, `White`, `OnMedia` (#FFFFFF: text and
glyphs over artwork and video), `OnMediaSecondary` (#EBEBF5 @ 60%), `ProgressTrack` (white @ 20%),
`DotInactive` (white @ 35%), `PlayerScrim` (black @ 60%), `PlayerSurface` (#1C1C1E @ 88%), and the
legacy `Amber`, `Coral` (brand gradient), `Gold` (star), `Mint` (success = systemGreen).
`NovaColors.isDark` tells the current appearance.

Rules:
- Text over artwork or video is always `OnMedia` / `OnMediaSecondary`, never `label` (light mode would
  turn it black).
- Links, text buttons, checkmarks: `NovaColors.tint` (systemBlue). Destructive: `NovaColors.red`.
  Toggle on: `NovaColors.green`. There is no other accent colour: no brand orange in UI chrome.
- Settings icon squares use the system colours: blue folder (`tint`), green network (`green`), orange
  stream (`orange`), gray phone/device (`gray`), plus `indigo`, `purple`, `pink`, `teal`, `red`,
  `yellow` as needed.

### 1.2 Type (`com.aakash.novafork.ui.theme.NovaType`)

The iOS Dynamic Type scale in Inter (`Inter` FontFamily). Styles carry no colour: pass `color =`.

| Token | Size / line | Weight | Tracking | Use |
|---|---|---|---|---|
| `largeTitle` | 34 / 41 sp | Bold | -0.4 | page titles (LargeTitleHeader) |
| `title1` | 28 / 34 sp | Bold | -0.4 | phone hero/details title |
| `title2` | 22 / 28 sp | Bold | -0.3 | row headers, section titles |
| `title3` | 20 / 25 sp | Semibold | -0.25 | sub-sections |
| `headline` | 17 / 22 sp | Semibold | -0.4 | buttons, inline titles, alert titles |
| `body` | 17 / 22 sp | Regular | -0.4 | list rows, menus, search |
| `callout` | 16 / 21 sp | Regular | -0.3 | secondary body |
| `subhead` | 15 / 20 sp | Regular | -0.2 | descriptions, meta lines |
| `subheadSemibold` | 15 / 20 sp | Semibold | -0.2 | card titles, capsule labels, iPad tabs |
| `footnote` | 13 / 18 sp | Regular | -0.08 | captions, section headers/footers, Information grid |
| `footnoteSemibold` | 13 / 18 sp | Semibold | -0.08 | emphasis in footnotes |
| `caption1` | 12 / 16 sp | Regular | 0 | small labels |
| `caption2` | 11 / 13 sp | Regular | 0.06 | tiny labels |
| `caption2Semibold` | 11 / 13 sp | Semibold | 0.06 | badges |
| `smallCaps` | 12 / 16 sp | Semibold | 0.6 | "EPISODE 3" (uppercase it) |
| `tabLabel` | 11 / 13 sp | Semibold | 0.06 | phone tab bar labels |
| `heroTitle` | 48 / 54 sp | Bold | -0.9 | iPad hero/details title |
| `monoCaption` | 12 / 16 sp | Medium | 0 + tnum | player times (tabular digits) |
| `monoSubhead` | 15 / 20 sp | Regular | -0.2 + tnum | changing numbers |

`MaterialTheme.typography` is mapped onto the same scale: `displayLarge` = heroTitle 48,
`displaySmall`/`headlineLarge` = Large Title 34, `headlineMedium` = Title 1, `headlineSmall` = Title 2,
`titleLarge` = Title 3, `titleMedium` = Headline, `titleSmall` = Subhead semibold, `bodyLarge` = Body,
`bodyMedium` = Subhead, `bodySmall` = Footnote, `labelLarge` = Callout semibold, `labelMedium` =
Caption 1 medium, `labelSmall` = Caption 2 medium. New code should use `NovaType` directly.

### 1.3 Motion (`com.aakash.novafork.ui.theme.NovaMotion`)

Spring-like: fast out, gentle settle, 250–350 ms.

| Member | Value | Use |
|---|---|---|
| `NovaMotion.standard<T>()` | spring(damping 0.86, stiffness 380) | position/size changes, sheets, thumbs |
| `NovaMotion.bouncy<T>()` | spring(damping 0.74, stiffness 420) | tab lenses, things that pop in |
| `NovaMotion.press<T>()` | spring(damping 0.62, stiffness 700) | press down/up |
| `NovaMotion.fade<T>(durationMs = 300)` | tween with `EaseOut` | fades and colour changes |
| `EaseOut` | CubicBezier(0.2, 0.9, 0.25, 1) | custom tweens |
| `DurationMs` / `FastMs` | 300 / 180 | |
| `PressScale` / `PressAlpha` | 0.96 / 0.85 | every pressable control (`novaClickable`) |
| `LiftScale` | 1.03 | long-pressed card while its context menu is open (`contextLift`) |

Page transitions: crossfade (`fadeIn(tween(220, delayMillis = 40)) togetherWith fadeOut(tween(140))`) or
a push (`slideInHorizontally { it } + fadeIn` / `slideOutHorizontally { -it / 4 } + fadeOut`) with
`NovaMotion.standard()`.

### 1.4 Radii (`com.aakash.novafork.ui.theme.NovaRadius`)

| Member | dp | Use |
|---|---|---|
| card artwork | `LocalNovaDimens.current.cardRadius` (10 phone / 14 iPad) | every 16:9 / 2:3 artwork |
| `Group` | 10 | inset-grouped lists, About cards |
| `Field` | 10 | search field |
| `Sheet` | 12 | sheet top corners (all corners on iPad form sheets) |
| `Alert`, `Menu` | 14 | alerts, menus |
| `IconSquare` | 7 | 29dp Settings icon squares |
| `Badge` | 4 | outlined "4K" / "CC" badges |
| capsules, circles | `CircleShape` | all glass controls, buttons, progress bars |

### 1.5 Glyphs (`NovaGlyphs`)

SF Symbols–style vectors for what `res/drawable` lacks: `Person` (account), `Back` (chevron.left),
`ChevronRight`, `ChevronDown`, `Ellipsis`, `Plus`, `Search` (magnifier), `Check`, `Close` (xmark),
`Play` (rounded play.fill), `Star`, `Info`. Use with `Icon(NovaGlyphs.X, contentDescription, tint = …)`
or the `ImageVector` overloads of the glass buttons. Everything else comes from the Material Round
drawables listed in ARCHITECTURE.md via `painterResource(R.drawable.ic_x)` / `NovaIcon`.
Glyph sizes: tab icons 24dp, toolbar circles ~20dp, row chevrons 13dp, header chevrons 15dp.

---

## 2. Phone vs tablet

Phone (smallest width < 600dp) and tablet are **separate UI families**: `ui/phone` and `ui/tablet`
each build their own screens and cards from the primitives here; only `ui/common`, `ui/settings`,
`ui/dialogs` and `ui/model` are shared. Decide with `LocalDeviceLayout.current`
(`PHONE`, `TABLET_COMPACT` = iPad portrait, `TABLET_EXPANDED` = iPad landscape) and read every size
from `LocalNovaDimens.current` — never hard-code a size that is in this table.

```kotlin
enum class DeviceLayout
```

```kotlin
fun deviceLayoutFor(smallestWidthDp: Int, widthDp: Int, mode: LayoutMode = LayoutMode.AUTO): DeviceLayout
```

```kotlin
@Composable
fun rememberDeviceLayout(mode: LayoutMode = LayoutMode.AUTO): DeviceLayout
```

```kotlin
fun dimensFor(layout: DeviceLayout): NovaDimens
```

```kotlin
fun heroHeightFor(layout: DeviceLayout, maxWidth: Dp, maxHeight: Dp): Dp
```

`LocalDeviceLayout` and `LocalNovaDimens` are provided by `NovaRoot`. `dimensFor` returns
`PhoneDimens` for `PHONE` and `TabletDimens` for both tablet layouts.

| `NovaDimens` field | Phone | iPad | Meaning |
|---|---|---|---|
| `gutter` | 20dp | 40dp | Horizontal page margin: row headers, the first card of a row, large titles. |
| `posterWidth` | 112dp | 170dp | Width of a 2:3 poster card in a horizontal row. Posters live in Library/category grids. |
| `gridMinCell` | 104dp | 170dp | Minimum cell width for adaptive poster grids. |
| `landscapeCardWidth` | 220dp | 300dp | Width of a 16:9 card in an ordinary row (Recently Added, Movies, TV Shows, Home Videos). |
| `cardRadius` | 10dp | 14dp | Artwork corner radius of cards. |
| `rowSpacing` | 12dp | 20dp | Gap between cards in a row and between grid cells. |
| `sectionSpacing` | 32dp | 40dp | Vertical space between rows / page sections. |
| `detailPosterWidth` | 112dp | 240dp | Poster on legacy details screens. |
| `continueCardWidth` | 300dp | 400dp | Width of a Continue Watching 16:9 card. |
| `episodeCardWidth` | 300dp | 360dp | Width of a 16:9 episode card in the details episode carousel. |
| `posterGridColumns` | 3 | adaptive (gridMinCell) | Fixed column count for poster grids, or null to use [gridMinCell] adaptively. |
| `heroMargin` | 20dp | 48dp | Margin of the text and buttons that sit on hero / details artwork. |
| `heroTextMaxWidth` | 520dp | 520dp | Max width of the title, meta line and description on the Home hero. |
| `descriptionMaxWidth` | 600dp | 600dp | Max width of the description and About text on details pages. |
| `primaryButtonHeight` | 50dp | 56dp | White primary capsule ("▶ Play") height. |
| `glassButtonSize` | 50dp | 56dp | Glass circles next to the primary button ("+", info, Start Over…). |
| `toolbarButtonSize` | 44dp | 48dp | Glass circles in the top bar (back, more, account). |
| `rowHeaderBottom` | 12dp | 12dp | Space under a row header before the cards. |
| `listInset` | 16dp | 16dp | Side margin of inset-grouped lists. |
| `tabBarHeight` | 64dp | 48dp | Height of the floating tab bar (phone bottom capsule / iPad top capsule). |
| `pageDotSize` | 6dp | 7dp | Hero page dots. |

Other per-form-factor rules (from the spec):

| | Phone | iPad |
|---|---|---|
| Tabs | `GlassTabBar`: bottom capsule (Home, Library, Browse) + Search circle | `GlassTopTabs`: centred top capsule + Search circle + account circle |
| Settings | account circle (`AccountButton`) top-right on Home/Library → pushed page | account circle in the top tabs → Settings split view |
| Home hero height | width × 1.25 | 78% of height (landscape) / 60% (portrait) — `heroHeightFor` |
| Hero text | Title 1 (28) bold, up to 2 lines, 20dp margins | `NovaType.heroTitle` (48), 48dp margins, max width 520dp, 2-line description |
| Primary button | 50dp, full width on details | 56dp |
| Rows | Continue Watching 300dp, others 220dp, 12dp gaps, 20dp gutter | 400dp / 300dp, 20dp gaps, 40dp gutter |
| Row header | Title 2 + "›", 20dp leading, 12dp below | same, 40dp leading |
| Library | category list rows + 2-column 16:9 grid; category page = 3-column posters | capsule filters + adaptive posters (min 170dp), preview pane in landscape |
| Episode cards | 300dp | 360dp |
| Sheets / menus | `GlassSheet` from the bottom | centred form sheet (`GlassSheet` does this automatically) or `GlassMenu` popover |
| Description max width | — | 600dp (`descriptionMaxWidth`) |
| Split views | — (pushed pages) | Browse and Settings: 360dp sidebar of inset-grouped rows (`ListRow(selected = …)`) on `background`, detail pane on the right |
| Text under cards | title Subhead semibold 1 line, caption Footnote secondary, 8dp below the artwork | same |

---

## 3. Liquid Glass

Everything that floats is Liquid Glass: tab bars, top buttons over artwork, player chrome, sheets,
menus, alerts. The page is recorded into a graphics layer (`glassSource`) and every glass control draws
that layer again underneath itself, blurred (24dp) and saturated, then a white tint (8–14%), a soft
specular sheen at the top and a 0.5dp light rim. Shapes are capsules and circles only (`CircleShape`;
bars use `RectangleShape`). On Android < 12, in Robolectric screenshots and inside dialog/popup windows
there is nothing to blur and the same shape is drawn with a translucent fill, sheen and rim.

**The one rule:** a glass control must never sit inside the backdrop it looks through. Put the page in
`GlassPage(content = …)` and the floating chrome in `GlassPage(chrome = …)`. Inside `content`,
`LocalGlassState` is null, so glass controls there use the fallback — unless you wrap the artwork they
sit on in its own `GlassBackdrop` and provide that state to them (see the hero recipe). `GlassSheet`,
`IosAlert` and `GlassMenu` live in other windows and always use the fallback.

| `GlassStyle` | Use | Look |
|---|---|---|
| `Regular` | tab bars, nav bar, capsules and circles over the page | white tint 12% over the blurred page (light mode: white 60%) |
| `Clear` | controls over artwork and video: hero, details, player chrome, HUDs | dimmed + white 9%, always dark with white glyphs |
| `Dense` | menus, sheets, alerts (they carry text) | #1C1C1E @ 72% (light #F9F9F9 @ 78%) |

```kotlin
object GlassConfig {
    /** Tests flip this on to check the backdrop lines up (no blur in software rendering). */
    @Volatile var forceBackdrop: Boolean = false

    val backdropSupported: Boolean
        get() = forceBackdrop ||
            (Build.VERSION.SDK_INT >= 31 && !Build.FINGERPRINT.contains("robolectric", ignoreCase = true))
}
```

`GlassConfig.forceBackdrop` (tests only) draws the unblurred backdrop to check alignment.

```kotlin
class GlassState internal constructor(val layer: GraphicsLayer) {
    var position by mutableStateOf(Offset.Zero)
        internal set

    /** Bumped after every recording so the glass above redraws with fresh content. */
    var tick by mutableLongStateOf(0L)
        internal set
}
```

```kotlin
@Composable
fun rememberGlassState(): GlassState
```

`val LocalGlassState: ProvidableCompositionLocal<GlassState?>` — the backdrop glass components read by
default (`glass: GlassState? = LocalGlassState.current` on every component).

```kotlin
fun Modifier.glassSource(state: GlassState?): Modifier
```

```kotlin
@Composable
fun GlassBackdrop(state: GlassState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
```

```kotlin
@Composable
fun GlassPage(
    modifier: Modifier = Modifier,
    state: GlassState = rememberGlassState(),
    chrome: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
)
```

```kotlin
@Composable
fun Modifier.glass(
    shape: Shape,
    state: GlassState? = LocalGlassState.current,
    style: GlassStyle = GlassStyle.Regular,
    elevation: Dp = 8.dp,
    rim: Boolean = true,
): Modifier
```

Use `Modifier.glass` directly for custom floating shapes (a snackbar capsule, gesture HUDs, the
"Up Next" card): `Modifier.glass(RoundedCornerShape(…) or CircleShape, style = GlassStyle.Clear)`. It
clips to the shape; put padding after it.

---

## 4. Components

### 4.1 Press feedback

Every tappable thing uses `novaClickable` (no ripple; scale 0.96 + alpha 0.85 while held). Put it
*before* `clip`/`background`/`glass` so the whole control scales.

```kotlin
@Composable
fun Modifier.novaClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    pressScale: Float = NovaMotion.PressScale,
    pressAlpha: Float = NovaMotion.PressAlpha,
    onClick: () -> Unit,
): Modifier
```

```kotlin
@Composable
fun Modifier.pressEffect(
    interactionSource: InteractionSource,
    scale: Float = NovaMotion.PressScale,
    alpha: Float = NovaMotion.PressAlpha,
): Modifier
```

```kotlin
@Composable
fun Modifier.contextLift(lifted: Boolean): Modifier
```

Context menus (long press on cards): `novaClickable(onLongClick = { menu = true }) { open() }`,
`Modifier.contextLift(menu)` on the card, and a `GlassMenu(expanded = menu, …)` inside the card's Box.

### 4.2 Buttons

**PrimaryCapsuleButton** — the white capsule with black Headline text and a filled play glyph:
"▶ Play", "▶ Resume", "▶ Play S2, E4". One per screen. Auto width (min 140dp) on heroes; pass
`Modifier.fillMaxWidth()` (with the 20dp page margins) on phone details. `playGlyph = false` for
non-playback actions (e.g. "Add Folder" in an empty state). Height follows `primaryButtonHeight`.

```kotlin
@Composable
fun PrimaryCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playGlyph: Boolean = true,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    height: Dp = LocalNovaDimens.current.primaryButtonHeight,
    minWidth: Dp = 140.dp,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black,
)
```

**GlassCapsuleButton** — glass capsule with a Subhead semibold label: "Sort ▾" and "Season 1 ▾"
(`trailingChevron = true`, usually via `PullDownButton`), iPad Library category capsules
(`selected = true` → solid label-coloured pill with background-coloured text), player "1×". Use
`style = GlassStyle.Clear` over artwork/video.

```kotlin
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailingChevron: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
)
```

**GlassCapsuleRow** — a horizontally scrolling row of glass capsules with one selected (solid pill):
the iPad Library categories "All · Movies · TV Shows · Home Videos · Streams" with a `PullDownButton`
("Sort ▾") in `trailing`, and other single-choice filters. Starts at the gutter by default.

```kotlin
@Composable
fun GlassCapsuleRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = LocalNovaDimens.current.gutter),
    spacing: Dp = 8.dp,
    height: Dp = 40.dp,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
    trailing: (@Composable RowScope.() -> Unit)? = null,
)
```

**GlassCircleButton** — icon-only glass circle: secondary actions next to the primary button ("+",
info, Start Over, Watched, Identify: `glassButtonSize`, 50/56dp), top-bar buttons (`toolbarButtonSize`,
44/48dp), player buttons. Three overloads: drawable, `ImageVector` (NovaGlyphs) and a content slot.
`selected = true` adds the lighter lens. The glyph defaults to `label` (white on `Clear`).

```kotlin
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.glassButtonSize,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
)

@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.glassButtonSize,
    iconSize: Dp = size * 0.44f,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
    onLongClick: (() -> Unit)? = null,
)

@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.glassButtonSize,
    iconSize: Dp = size * 0.4f,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
    onLongClick: (() -> Unit)? = null,
)
```

**AccountButton** — the person glass circle: top-right on phone Home/Library and at the far right of
the iPad top tabs; opens Settings (Settings is not a tab).

```kotlin
@Composable
fun AccountButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.toolbarButtonSize,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
)
```

**BackButton** — "‹" glass circle, top-left of pushed pages. `GlassStyle.Clear` over artwork.

```kotlin
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.toolbarButtonSize,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
)
```

**TextAction** — iOS text button in `tint` blue: "Start Over", "Cancel", "Add Folder", "Clear". For
the details "MORE" link use `color = NovaColors.OnMedia` (or `label`) and `style = NovaType.smallCaps`.

```kotlin
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NovaColors.tint,
    style: TextStyle = NovaType.body,
    enabled: Boolean = true,
)
```

**PlayGlyph** — the rounded play triangle on its own (Up Next card, episode thumbnails).

```kotlin
@Composable
fun PlayGlyph(modifier: Modifier = Modifier, color: Color = Color.Black, size: Dp = 16.dp)
```

### 4.3 Navigation chrome

**GlassTabBar** (PHONE ONLY) — the iOS 26 tab bar: a 64dp glass capsule 20dp from the sides above the
navigation bar with icon + Caption 2 label items (selected = label colour on a lighter lens that slides,
others secondary) and a separate 64dp Search glass circle. Put it in `GlassPage(chrome = …)` with
`Modifier.align(Alignment.BottomCenter)`. `selectedIndex = -1` + `searchSelected = true` while Search
is open. Highlight the tab of the stack root (`AppUiState.tab`), not `route.top`.

```kotlin
data class GlassTabItem(val label: String, @DrawableRes val icon: Int)
```

```kotlin
@Composable
fun GlassTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searchSelected: Boolean = false,
    glass: GlassState? = LocalGlassState.current,
)
```

```kotlin
@Composable
fun glassTabBarClearance(): Dp
```

Pad every phone page's scrolling content with `PaddingValues(bottom = glassTabBarClearance())` so the
last row clears the bar.

**GlassTopTabs** (TABLET ONLY) — the iPadOS 26 tab bar: centred glass capsule of text tabs (Subhead
semibold, selected on a sliding lighter pill), the Search circle right after it and the account circle
at the far right (gutter). Floats over content at the top; content scrolls under it.

```kotlin
@Composable
fun GlassTopTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
    searchSelected: Boolean = false,
    accountSelected: Boolean = false,
    glass: GlassState? = LocalGlassState.current,
)
```

**GlassNavBar** — the floating top bar of a page: `leading`/`trailing` glass circles are always shown;
the glass background and the centred Headline inline `title` fade in when `showBackground` is true
(the large title scrolled away). Over artwork (details, hero) pass `showBackground = false` and use
`GlassStyle.Clear` buttons. Height = status bar + `NavBarDefaults.Height` (52dp).

```kotlin
@Composable
fun GlassNavBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    showBackground: Boolean = true,
    glass: GlassState? = LocalGlassState.current,
    horizontalPadding: Dp = NavBarDefaults.HorizontalPadding,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
)
```

`object NavBarDefaults { Height = 52.dp; HorizontalPadding = 16.dp; TabBarSearchGap = 12.dp; TabBarBottomGap = 8.dp }`

**LargeTitleHeader** — "Library", "Search", "Browse", "Settings", category and folder names: Large
Title 34 bold at the gutter, as the first item of the scrolling list. It sits below the floating bar
(phone) or the top tabs (iPad) by default (`largeTitleTopPadding()`). Pair it with
`rememberTitleCollapsed(listState)` → `LargeTitleHeader(collapsed = …)` and
`GlassNavBar(showBackground = …, title = …)`.

```kotlin
@Composable
fun LargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    horizontalPadding: Dp = LocalNovaDimens.current.gutter,
    topPadding: Dp = largeTitleTopPadding(),
    trailing: (@Composable RowScope.() -> Unit)? = null,
)
```

```kotlin
@Composable
fun largeTitleTopPadding(): Dp
```

```kotlin
@Composable
fun rememberTitleCollapsed(state: LazyListState, threshold: Dp = 40.dp): Boolean

@Composable
fun rememberTitleCollapsed(state: LazyGridState, threshold: Dp = 40.dp): Boolean

@Composable
fun rememberTitleCollapsed(state: ScrollState, threshold: Dp = 40.dp): Boolean
```

**RowHeader** — "Continue Watching ›": Title 2 bold with a secondary chevron right after the text;
the whole header is tappable (opens the full list). Includes the gutter and the 12dp gap below; no
chevron when `onClick` is null.

```kotlin
@Composable
fun RowHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    horizontalPadding: Dp = LocalNovaDimens.current.gutter,
    bottomPadding: Dp = LocalNovaDimens.current.rowHeaderBottom,
)
```

**SmallCapsText** — "EPISODE 3", "SEASON 1", "MORE": Caption 1 semibold, uppercase, +0.6 tracking,
secondary.

```kotlin
@Composable
fun SmallCapsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NovaColors.secondaryLabel,
    maxLines: Int = 1,
)
```

### 4.4 Indicators and badges

**ProgressBar** — thin white capsule on white 20%. Continue Watching: 3dp INSIDE the artwork, 6dp from
the bottom, 12dp side insets. Details: under the Resume button with "1h 41m left". It fills the width
given. Off artwork in light mode pass `trackColor = NovaColors.fill, color = NovaColors.label`.

```kotlin
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    trackColor: Color = NovaColors.ProgressTrack,
    color: Color = Color.White,
)
```

**ActivityIndicator** — the iOS 8-spoke spinner. 20dp (medium) or 36dp (large); `color = Color.White`
over artwork and video (player buffering). `revealFraction` shows a static partial spinner.

```kotlin
@Composable
fun ActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = NovaColors.secondaryLabel,
    revealFraction: Float? = null,
)
```

**NovaRefreshIndicator / NovaPullToRefreshBox** — pull-to-refresh: a spinner that fills in spoke by
spoke as you pull, spins while refreshing. Pass only `ScanUi.running` (file scanning) as
`isRefreshing` — never `fetchingArtwork`, or the spinner stays pinned for the whole poster pass.

```kotlin
@Composable
fun NovaRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NovaColors.secondaryLabel,
)
```

```kotlin
@Composable
fun NovaPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
)
```

**PageDots** — hero page dots: 6dp (iPad 7dp), white / white 35%, centred 12dp under the buttons.

```kotlin
@Composable
fun PageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = LocalNovaDimens.current.pageDotSize,
    activeColor: Color = Color.White,
    inactiveColor: Color = NovaColors.DotInactive,
)
```

**Badge / RatingBadge** — outlined 4dp-corner boxes with Caption 2 semibold secondary text in the
details badge row: `Badge("4K")` / `Badge("HD")`, `RatingBadge(8.2f)` ("★ 8.2"), `Badge("CC")` when
subtitles exist. Over artwork pass `color = NovaColors.OnMediaSecondary`.

```kotlin
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = NovaColors.secondaryLabel)
```

```kotlin
@Composable
fun RatingBadge(rating: Float?, modifier: Modifier = Modifier, color: Color = NovaColors.secondaryLabel)
```

**GlassHud** — player gesture HUD (volume, brightness, seek): a 44dp Clear glass capsule with a
white glyph, a 120dp thin bar for `fraction` and/or a short `text` ("+10 s"). Over video only.

```kotlin
@Composable
fun GlassHud(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    fraction: Float? = null,
    text: String? = null,
)
```

**CountdownRing** — a thin white ring filling clockwise from the top around its content: the
"Up Next" card's play button (`CountdownRing(progress) { PlayGlyph(color = Color.White) }`).

```kotlin
@Composable
fun CountdownRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = Color.White,
    trackColor: Color = Color(0x4DFFFFFF),
    content: @Composable BoxScope.() -> Unit = {},
)
```

**GlassToast** — replaces `Snackbar`: a glass capsule with Subhead semibold text (optional glyph)
that slides in, stays `durationMs`, slides out and then calls `onDismissed` (consume the one-shot
`AppUiState.message` there). A new message replaces the visible one; a tap dismisses it early. Put it
in the root `Box`, bottom-centre: above `glassTabBarClearance()` on phone, 24dp up on iPad.

```kotlin
@Composable
fun GlassToast(
    message: String?,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    durationMs: Long = 3_000L,
    glass: GlassState? = LocalGlassState.current,
)
```

### 4.5 Controls

**IosToggle** — the iOS 26 switch: 63×28 capsule track (`green` on, `toggleOff` off) with a white pill
knob that stretches while held. In lists use `ListToggleRow`.

```kotlin
@Composable
fun IosToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
```

**SegmentedControl** — capsule track with a sliding raised thumb: Audio | Subtitles, Phone | Tablet
choices with 2–4 options. Give it a width.

```kotlin
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
)
```

**SearchField** — the iOS search field (36dp, `searchField` colour, 10dp corners, magnifier,
placeholder "Movies, Shows, and More", clear button). Under the Search large title; show
`TextAction("Cancel")` beside it while focused.

```kotlin
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Movies, Shows, and More",
    autoFocus: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    onSubmit: (String) -> Unit = {},
)
```

**ThinSlider** — thin white capsule slider that grows from 4dp to 8dp while dragged: player volume
(iPad, inside a glass capsule with a speaker glyph) and brightness. Value 0–1.

```kotlin
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    color: Color = Color.White,
    trackColor: Color = Color(0x4DFFFFFF),
    trackHeight: Dp = 4.dp,
    activeTrackHeight: Dp = 8.dp,
    contentDescription: String? = null,
)
```

**Stepper** — iOS "− | +" stepper: subtitle delay, playback speed.

```kotlin
@Composable
fun Stepper(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    decrementDescription: String = "Decrease",
    incrementDescription: String = "Increase",
)
```

**IosTextField** — the iOS rounded text field: filled (`NovaColors.fill`) box with 10dp corners, Body
text, tertiary placeholder, blue cursor, clear button while focused. 44dp; pass `height = 36.dp`
inside an `IosAlert`. `secure = true` for passwords (also switches to the password keyboard).
`singleLine = false` grows from `height`. Use for the stream URL alert, Identify's search field and
other single fields outside lists.

```kotlin
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    secure: Boolean = false,
    singleLine: Boolean = true,
    height: Dp = 44.dp,
    containerColor: Color = NovaColors.fill,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = false,
)
```

**ListTextFieldRow** — a form row inside an `InsetGroup`: optional `label` (Body, `labelWidth` wide)
and the field filling the rest, like iOS "Other Network" forms ("Server", "Share", "Username",
"Password"). Tapping anywhere on the row focuses the field; the hairline is inset like `ListRow`'s.
Use it for the network-share form (in a `GlassSheet`) and the TMDB key / server settings.

```kotlin
@Composable
fun ListTextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    secure: Boolean = false,
    labelWidth: Dp = 110.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = false,
    showDivider: Boolean = true,
)
```

### 4.6 Lists (iOS Settings style)

Settings, Browse, Information and every list page use inset-grouped sections: 16dp side margin, 10dp
corners, `cell` (#1C1C1E) rows of 44dp min height, hairlines inset to the text, "›" on navigable rows,
values in secondary on the right, 29dp coloured icon squares with white glyphs on the left. Section
headers Footnote uppercase secondary; footers Footnote secondary. Put
`InsetGroupDefaults.SectionSpacing` (28dp) between groups. Library's category list is the same rows
without a group (`ListRow(horizontalPadding = dims.gutter)` on the page background).

```kotlin
object InsetGroupDefaults {
    /** Vertical gap between two groups (put it between InsetGroups yourself). */
    val SectionSpacing = 28.dp
    val RowMinHeight = 44.dp
    /** Leading/trailing padding inside a row. */
    val RowPadding = 16.dp
    /** Settings icon square. */
    val IconSize = 29.dp
    /** Gap between a leading icon and the title. */
    val IconGap = 14.dp
}
```

```kotlin
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    horizontalPadding: Dp = LocalNovaDimens.current.listInset,
    containerColor: Color = groupedCellColor(),
    content: @Composable ColumnScope.() -> Unit,
)
```

```kotlin
@Composable
fun ListSectionHeader(text: String, modifier: Modifier = Modifier)
```

```kotlin
@Composable
fun ListSectionFooter(text: String, modifier: Modifier = Modifier)
```

**ListRow** — one row. Navigation rows: `chevron = true`. Choice lists: `checked = true` on the
selected row (blue checkmark). iPad sidebars (Settings, Browse): `selected = true` on the open
section (tint-blue row, white text, no hairline). Action rows ("Add Folder", "Open Network Stream"): `titleColor =
NovaColors.tint`, no chevron; destructive actions `NovaColors.red`. Search results / folder videos:
`leading` = a 64dp 16:9 thumbnail and `dividerInset = 16.dp + 64.dp + 14.dp`. `onLongClick` for
row context menus.

```kotlin
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    titleStyle: TextStyle = NovaType.body,
    titleMaxLines: Int = 1,
    chevron: Boolean = false,
    checked: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    dividerInset: Dp? = null,
    horizontalPadding: Dp = InsetGroupDefaults.RowPadding,
    minHeight: Dp = InsetGroupDefaults.RowMinHeight,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
)
```

```kotlin
@Composable
fun ListToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
)
```

**ListRowIcon** — the coloured 29dp rounded square with a white glyph.

```kotlin
@Composable
fun ListRowIcon(
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = InsetGroupDefaults.IconSize,
)

@Composable
fun ListRowIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = InsetGroupDefaults.IconSize,
)
```

**SwipeToDeleteRow** — swipe left reveals a red "Delete"; a long swipe deletes. Use for folders,
network shares and saved streams — never for Device Storage (pass `enabled = false`). The row slides
back after `onDelete`, so it can show a confirmation `IosAlert`.

```kotlin
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actionLabel: String = "Delete",
    containerColor: Color = groupedCellColor(),
    content: @Composable () -> Unit,
)
```

```kotlin
@Composable
fun Hairline(modifier: Modifier = Modifier, startInset: Dp = 0.dp, color: Color = NovaColors.separator)
```

`val LocalGroupedCellColor: ProvidableCompositionLocal<Color?>` and

```kotlin
@Composable
fun groupedCellColor(): Color
```

`GlassSheet` raises grouped cells to #2C2C2E (dark) so groups stand out on the sheet; provide it
yourself for other raised containers.

### 4.7 Sheets, alerts and menus

**GlassSheet** — iOS sheet in its own Dialog window: grabber (36×5dp, white 30%), optional centred
Headline title with a glass ✕, dense glass with 12dp top corners; slides up, dims the page, drags down
to dismiss (also from a list scrolled to its top). `SheetDetent.Fit` (content height), `Medium`
(half screen, drags up to Large), `Large`. On tablets it becomes a centred form sheet. The content
lambda receives `dismiss` (animates away, then calls `onDismissRequest`); `onDismissRequest` must
remove the sheet from composition. Form sheets (iOS 26 style): pass `onConfirm` (+ `confirmEnabled`,
`confirmLabel`) to get a blue ✓ circle on the right with the ✕ on the left; the ✓ runs `onConfirm`
and then animates the sheet away. Use for track pickers on phone, "Identify", "Add Network Share".

```kotlin
enum class SheetDetent { Fit, Medium, Large }
```

```kotlin
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    detent: SheetDetent = SheetDetent.Fit,
    title: String? = null,
    showClose: Boolean = title != null,
    dismissible: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmLabel: String = "Done",
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
)
```

**IosAlert** — centred 270dp alert: Headline title, Footnote message, optional content (a text field),
blue text buttons separated by hairlines — side by side for two short actions, stacked otherwise; red
for `Destructive`; bold for the `preferred` action (else for Cancel). Outside taps do nothing; back
calls `onDismissRequest`. Buttons only run their `onClick` — hide the alert yourself.

```kotlin
enum class AlertActionStyle { Default, Cancel, Destructive }
```

```kotlin
class AlertAction(
    val label: String,
    val style: AlertActionStyle = AlertActionStyle.Default,
    val preferred: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)
```

```kotlin
@Composable
fun IosAlert(
    title: String,
    actions: List<AlertAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
)
```

Examples: removing a source (`Cancel` + `Remove` as `Destructive`); the crash report shown after a
crash (`AppUiState.crashReport != null`): title "Nova Fork Quit Unexpectedly", a one-line message,
actions `AlertAction("Not Now", AlertActionStyle.Cancel) { actions.dismissCrashReport() }` and
`AlertAction("Share Report", preferred = true) { actions.shareCrashReport() }`; a stream URL prompt
with an `IosTextField(height = 36.dp)` in `content`.

**GlassMenu** — iOS pull-down / context menu: a dense glass panel (250dp, 14dp corners) growing out of
its anchor, rows of Body text with the icon on the right, a leading checkmark for the selected option,
red destructive rows, a thick gap before `startsSection` rows. Call it inside the anchor's `Box`.
Selecting runs the item and closes the menu. On iPad player and Library, menus are these popovers; on
phone, long lists of choices go in a `GlassSheet` instead.

```kotlin
class GlassMenuItem(
    val label: String,
    @DrawableRes val icon: Int? = null,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val startsSection: Boolean = false,
    val onClick: () -> Unit,
)
```

```kotlin
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    title: String? = null,
    width: Dp = 250.dp,
)
```

**PullDownButton** — "Sort ▾" / "Season 1 ▾" capsule that opens a GlassMenu.

```kotlin
@Composable
fun PullDownButton(
    label: String,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    menuTitle: String? = null,
    alignEnd: Boolean = false,
    height: Dp = 40.dp,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
)
```

**GlassMenuButton** — "…" glass circle with a menu (details "more": Identify, Mark as Watched).

```kotlin
@Composable
fun GlassMenuButton(
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    icon: ImageVector = NovaGlyphs.Ellipsis,
    contentDescription: String = "More",
    size: Dp = LocalNovaDimens.current.toolbarButtonSize,
    menuTitle: String? = null,
    alignEnd: Boolean = true,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
)
```

### 4.8 Details text blocks (`Content.kt`)

Phone and tablet details pages both use these; each family places and sizes them itself.

**ExpandableText** — the description: `collapsedMaxLines` (3) lines, then a bold "MORE" at the end of
the last line over a fade into `fadeColor` (the colour behind the text: `Color.Black` over artwork).
Tap expands in place, or calls `onMore` (show the full text in a `GlassSheet`).

```kotlin
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    style: TextStyle = NovaType.subhead,
    color: Color = NovaColors.secondaryLabel,
    moreColor: Color = NovaColors.label,
    fadeColor: Color = NovaColors.background,
    onMore: (() -> Unit)? = null,
)
```

**AboutCard** — the "About" section: a `cell`-coloured card (`cardRadius` corners) with Headline
title, Footnote secondary genres and the full overview in Subhead secondary.

```kotlin
@Composable
fun AboutCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    bodyMaxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
)
```

**InfoGrid** — the "Information" section: key/value pairs (Footnote semibold key over Footnote
secondary value) in 2 columns on phone, 3 on iPad: Released, Run Time, File, Size, Resolution,
Source. Blank values are skipped.

```kotlin
@Composable
fun InfoGrid(
    entries: List<Pair<String, String?>>,
    columns: Int,
    modifier: Modifier = Modifier,
    columnSpacing: Dp = 20.dp,
    rowSpacing: Dp = 16.dp,
    keyColor: Color = NovaColors.label,
    valueColor: Color = NovaColors.secondaryLabel,
)
```

### 4.9 Artwork (`Art.kt`)

Artwork has **no border and no shadow**; clip it with `RoundedCornerShape(dims.cardRadius)`; text sits
BELOW cards (title Subhead semibold 1 line, caption Footnote secondary). 16:9 everywhere except
Library/category grids (2:3 posters). Placeholders are subtle near-black gradients with the title.

```kotlin
@Composable
fun ArtImage(
    candidates: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    frameSource: String? = null,
    placeholder: @Composable () -> Unit,
)
```

```kotlin
@Composable
fun PosterArt(
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    showTitleOnPlaceholder: Boolean = true,
)
```

```kotlin
@Composable
fun LandscapeArt(
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    showTitleOnPlaceholder: Boolean = false,
)
```

```kotlin
@Composable
fun BackdropArt(artwork: Artwork, modifier: Modifier = Modifier)
```

```kotlin
@Composable
fun ArtPlaceholder(seed: String, title: String?, modifier: Modifier = Modifier)
```

```kotlin
fun placeholderColors(seed: String): Pair<Color, Color>
```

**Scrims.** Heroes: `bottomScrim(Color.Black, startFraction = 0.55f)` (gradient to black over the lower
45%); iPad heroes/details add `startScrim(Color.Black.copy(alpha = 0.7f))`; artwork under the status bar
and top glass buttons gets `topScrim()`. Gradients are eased (no band at the start).

```kotlin
fun Modifier.bottomScrim(color: Color, startFraction: Float = 0.35f): Modifier
```

```kotlin
fun Modifier.startScrim(color: Color, endFraction: Float = 0.7f): Modifier
```

```kotlin
fun Modifier.topScrim(color: Color = Color.Black.copy(alpha = 0.45f), endFraction: Float = 0.25f): Modifier
```

### 4.10 Legacy helpers (`Components.kt`)

Kept with their old signatures so current screens build; restyled to the iOS look. Prefer the new
components in new code.

| Helper | Now looks like | Prefer |
|---|---|---|
| `NovaIcon(res, contentDescription, modifier, tint)` | `Icon(painterResource(res))` | — (still fine) |
| `metaLine(vararg parts)` | joins with " · " | — (still fine) |
| `RatingPill(rating, modifier, onImage)` | outlined RatingBadge; on images a dark capsule | `RatingBadge` |
| `ProgressStrip(progress, modifier, height)` | white capsule inside the artwork, 12dp insets, 6dp up | `ProgressBar` |
| `WatchedBadge(modifier)` | white check on a dark translucent circle | — |
| `SectionHeader(title, modifier, actionLabel, onAction)` | Title 2 (+ "›" when tappable), caller pads | `RowHeader` |
| `EmptyState(icon, title, body, …)` | iOS content-unavailable view: glyph, Title 2, Subhead, capsule + text action | — |
| `ScanBanner(scan, modifier)` | spinner + Footnote label + thin bar while `running` or `fetchingArtwork` | — |

```kotlin
@Composable
fun NovaIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
)
```

```kotlin
fun metaLine(vararg parts: String?): String
```

```kotlin
@Composable
fun RatingPill(rating: Float?, modifier: Modifier = Modifier, onImage: Boolean = false)
```

```kotlin
@Composable
fun ProgressStrip(progress: Float?, modifier: Modifier = Modifier, height: Dp = 3.dp)
```

```kotlin
@Composable
fun WatchedBadge(modifier: Modifier = Modifier)
```

```kotlin
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
)
```

```kotlin
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
)
```

```kotlin
@Composable
fun ScanBanner(scan: ScanUi, modifier: Modifier = Modifier)
```

---

## 5. Recipes

These compile against the design system as written (they are checked in a compile harness).

### Phone shell

```kotlin
@Composable
fun PhoneShellSample(selected: Int, onSelect: (Int) -> Unit, onSearch: () -> Unit, page: @Composable () -> Unit) {
    GlassPage(
        modifier = Modifier.fillMaxSize(),
        chrome = {
            GlassTabBar(
                items = listOf(
                    GlassTabItem("Home", R.drawable.ic_home),
                    GlassTabItem("Library", R.drawable.ic_video_library),
                    GlassTabItem("Browse", R.drawable.ic_folder),
                ),
                selectedIndex = selected,
                onSelect = onSelect,
                onSearch = onSearch,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    ) {
        page()
    }
}
```

### iPad shell

```kotlin
@Composable
fun TabletShellSample(selected: Int, onSelect: (Int) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit, page: @Composable () -> Unit) {
    GlassPage(
        modifier = Modifier.fillMaxSize(),
        chrome = { GlassTopTabs(listOf("Home", "Library", "Browse"), selected, onSelect, onSearch, onSettings) },
    ) {
        page()
    }
}
```

### Home page and hero

```kotlin
/** Hero height comes from the PAGE size (outside the scrolling list, where maxHeight is bounded). */
@Composable
fun HomePageSample(hero: @Composable (height: Dp) -> Unit, rows: @Composable () -> Unit) {
    val layout = LocalDeviceLayout.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = heroHeightFor(layout, maxWidth, maxHeight)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = glassTabBarClearance())) {
            item { hero(heroHeight) }
            item { rows() }
        }
    }
}

@Composable
fun HeroSample(height: Dp, artwork: Artwork, title: String, genres: String, pages: Int, page: Int, onPlay: () -> Unit, onAdd: () -> Unit) {
    val dims = LocalNovaDimens.current
    val heroGlass = rememberGlassState()
    Box(Modifier.fillMaxWidth().height(height)) {
        // The artwork is a backdrop of its own, so the circle over it blurs the picture.
        GlassBackdrop(heroGlass, Modifier.matchParentSize()) {
            BackdropArt(artwork, Modifier.matchParentSize().bottomScrim(Color.Black, startFraction = 0.55f))
        }
        CompositionLocalProvider(LocalGlassState provides heroGlass) {
            Column(Modifier.align(Alignment.BottomStart).padding(dims.heroMargin)) {
                Text(title, style = NovaType.title1, color = NovaColors.OnMedia, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(genres, style = NovaType.subhead, color = NovaColors.OnMediaSecondary, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryCapsuleButton(text = "Play", onClick = onPlay)
                    Spacer(Modifier.width(12.dp))
                    GlassCircleButton(onClick = onAdd, icon = NovaGlyphs.Plus, contentDescription = "Add", style = GlassStyle.Clear)
                }
                Spacer(Modifier.height(12.dp))
                PageDots(count = pages, current = page, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
```

### A row of 16:9 cards (with context menu)

Rows scroll horizontally with the first card on the gutter and snap to card edges.

```kotlin
class CardSample(val id: Long, val title: String, val caption: String, val artwork: Artwork, val progress: Float?)

@Composable
fun CardRowSample(title: String, cards: List<CardSample>, continueWatching: Boolean, onMore: () -> Unit, onOpen: (Long) -> Unit) {
    val dims = LocalNovaDimens.current
    val state = rememberLazyListState()
    Column {
        RowHeader(title, onClick = onMore)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        ) {
            items(cards, key = { it.id }) { card ->
                var menu by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .width(if (continueWatching) dims.continueCardWidth else dims.landscapeCardWidth)
                        .contextLift(menu)
                        .novaClickable(onLongClick = { menu = true }) { onOpen(card.id) },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(dims.cardRadius))) {
                        LandscapeArt(card.artwork, card.title, Modifier.matchParentSize())
                        val p = card.progress
                        if (continueWatching && p != null) {
                            ProgressBar(p, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(card.title, style = NovaType.subheadSemibold, color = NovaColors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(card.caption, style = NovaType.footnote, color = NovaColors.secondaryLabel, maxLines = 1)
                    GlassMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        items = listOf(
                            GlassMenuItem("Play", icon = R.drawable.ic_play_arrow) { onOpen(card.id) },
                            GlassMenuItem("Mark as Watched", icon = R.drawable.ic_check_circle) {},
                        ),
                    )
                }
            }
        }
    }
}
```

### Large-title list page

```kotlin
@Composable
fun ListPageSample(onBack: () -> Unit, onSettings: () -> Unit, refreshing: Boolean, onRefresh: () -> Unit) {
    val list = rememberLazyListState()
    val collapsed = rememberTitleCollapsed(list)
    GlassPage(
        modifier = Modifier.fillMaxSize(),
        chrome = {
            GlassNavBar(
                title = "Browse",
                showBackground = collapsed,
                trailing = { AccountButton(onClick = onSettings) },
            )
        },
    ) {
        NovaPullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().background(NovaColors.background),
                contentPadding = PaddingValues(bottom = glassTabBarClearance()),
            ) {
                item { LargeTitleHeader("Browse", collapsed = collapsed) }
                item {
                    InsetGroup(header = "Folders") {
                        ListRow(
                            title = "Films",
                            leading = { ListRowIcon(R.drawable.ic_folder, NovaColors.tint) },
                            value = "31",
                            chevron = true,
                            onClick = onBack,
                        )
                        ListRow(title = "Add Folder", titleColor = NovaColors.tint, onClick = {})
                    }
                }
            }
        }
    }
}
```

### Sheet and alert

```kotlin
@Composable
fun SheetAndAlertSample(sheetOpen: Boolean, onSheetClosed: () -> Unit, alertOpen: Boolean, onAlertClosed: () -> Unit, onRemove: () -> Unit) {
    if (sheetOpen) {
        GlassSheet(onDismissRequest = onSheetClosed, title = "Audio & Subtitles") { dismiss ->
            InsetGroup(header = "Audio") {
                ListRow("English · AC3 5.1", checked = true, onClick = dismiss)
                ListRow("Japanese · AAC", onClick = dismiss)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (alertOpen) {
        IosAlert(
            title = "Remove “NAS”?",
            message = "Its videos leave the library. The files stay on the server.",
            actions = listOf(
                AlertAction("Cancel", AlertActionStyle.Cancel, onClick = onAlertClosed),
                AlertAction("Remove", AlertActionStyle.Destructive) {
                    onRemove()
                    onAlertClosed()
                },
            ),
            onDismissRequest = onAlertClosed,
        )
    }
}
```

### Form sheet with text fields

```kotlin
@Composable
fun NetworkShareSheetSample(onClose: () -> Unit, onSave: (host: String, share: String, user: String, password: String) -> Unit) {
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    GlassSheet(
        onDismissRequest = onClose,
        detent = SheetDetent.Large,
        title = "Add Network Share",
        onConfirm = { onSave(host.trim(), share.trim(), user, password) },
        confirmEnabled = host.isNotBlank() && share.isNotBlank(),
        confirmLabel = "Save",
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            InsetGroup(header = "Server") {
                ListTextFieldRow(
                    host,
                    { host = it },
                    label = "Server",
                    placeholder = "nas.local",
                    autoFocus = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                ListTextFieldRow(share, { share = it }, label = "Share", placeholder = "Movies", showDivider = false)
            }
            Spacer(Modifier.height(InsetGroupDefaults.SectionSpacing))
            InsetGroup(header = "Sign In", footer = "Leave both empty to connect as a guest.") {
                ListTextFieldRow(user, { user = it }, label = "Username", placeholder = "Optional")
                ListTextFieldRow(password, { password = it }, label = "Password", placeholder = "Optional", secure = true, showDivider = false)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
```

### Details text blocks

```kotlin
@Composable
fun DetailsTextSample(overview: String, title: String, genres: String, info: List<Pair<String, String?>>, onMore: () -> Unit) {
    val dims = LocalNovaDimens.current
    val tablet = LocalDeviceLayout.current.isTablet
    Column(Modifier.fillMaxWidth()) {
        ExpandableText(
            overview,
            Modifier.padding(horizontal = dims.gutter).widthIn(max = dims.descriptionMaxWidth),
            fadeColor = Color.Black,
            onMore = onMore,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = dims.gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge("4K")
            RatingBadge(8.2f)
            Badge("CC")
        }
        Spacer(Modifier.height(dims.sectionSpacing))
        RowHeader("About")
        AboutCard(title, overview, Modifier.padding(horizontal = dims.gutter).fillMaxWidth(), subtitle = genres)
        Spacer(Modifier.height(dims.sectionSpacing))
        RowHeader("Information")
        InfoGrid(info, columns = if (tablet) 3 else 2, modifier = Modifier.padding(horizontal = dims.gutter))
    }
}
```

### Root toast (replaces SnackbarHost)

```kotlin
@Composable
fun RootToastSample(message: String?, consumeMessage: () -> Unit, content: @Composable () -> Unit) {
    val tablet = LocalDeviceLayout.current.isTablet
    Box(Modifier.fillMaxSize()) {
        content()
        GlassToast(
            message = message,
            onDismissed = consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (tablet) 24.dp else glassTabBarClearance()),
        )
    }
}
```

---

## 6. Checklist

- Black canvas (`NovaColors.background`), white text; secondary text `secondaryLabel`; no Material
  colour roles in new code.
- No borders or shadows on artwork; 10dp (phone) / 14dp (iPad) corners; text under the card.
- Everything floating is glass (capsule or circle), drawn over the page via `GlassPage(chrome = …)`.
- One white `PrimaryCapsuleButton` per screen; secondary actions are glass circles/capsules.
- Every size from `LocalNovaDimens`; every colour from `NovaColors`; every text style from `NovaType`.
- Press feedback through `novaClickable`; no ripples.
- Phone and tablet screens stay in their own packages and use their own composition of these parts.
