# Apple Music web player: DOM and design report

Measured on music.apple.com on 2026-10-15, in Chromium, in dark appearance (`prefers-color-scheme: dark`). Values were taken with `getComputedStyle` at a 1440×900 viewport unless a row says otherwise. The `:root` tokens come from Apple's own stylesheet (`/assets/index~*.css`; the track-list rules are in a lazily loaded `List~*.css`).

Apple's CSS is not copied into this repo. This file records measurements, which are facts, and `web/app/src/styles/tokens.css` is written from them.

**Where the site differs from the build prompt.** The prompt (written earlier) describes a player bar across the top and a search field in the sidebar. The live site has since been redesigned ("Liquid Glass"):

- The player is a **floating glass pill centred at the bottom** of the content area.
- On the search page, the **search field is a centred pill at the top of the content area**. The sidebar keeps a "Search" nav item.

We replicate the live site.

**Font.** Decided for BitChord: **DM Sans from Google Fonts** (weights 400/500/600/700/800 plus italics, `display=swap`) is used everywhere, in place of the SF Pro / Inter stack from §2.2. Apple's type *scale* (sizes, weights, line heights) is kept. DM Sans is a little wider than SF Pro Text, so single-line labels that are truncated (lockup titles, LCD title) keep `text-overflow: ellipsis`.

---

## 1. App shell

```
div.body-container                               0,0   1440×900
  div.app-container (.is-drawer-open when a side panel shows; grid 260px | 1fr)
    div.header > nav.navigation                  0,0   260×(100vh − upsell)
      div.navigation__header > div.logo          8,8   251×72   (padding 0 20px)
      div#navigation.navigation__content         margin-top −36px
        div.navigation__scrollable-container     padding 36px 12px 0 20px
          [data-testid=navigation-items-primary] ul > li.navigation-item > a.navigation-item__link
          div.navigation-items__header "Library  Edit"
          ul  Recently Added · Artists · Albums · Songs · Music Videos · Made for You
          div.navigation-items__header "Playlists"
          ul  All Playlists · <user playlists…>
        div.navigation__native-cta   ("Open in Music ↗" + red "Sign In" pill when signed out;
                                      account menu "<avatar> Name" when signed in)
    div.player-bar.player-bar__floating-player   bottom 20px, padding 0 20px, centred in the content column
      div.wrapper > div.chrome-player (pill)
    div#scrollable-page.scrollable-page          padding-left 260px (the sidebar width); the whole window scrolls
      main > div.content-container
    div.side-panel                               (Up Next / Lyrics; fixed, right, 300px)
```

### Sidebar (`.navigation`)

| Property | Value |
|---|---|
| Width | `--web-navigation-width`: **260px** at ≥767.32px, `33.884vw` at 484–767px, hidden (top bar) below 484px |
| Background | glass: `--glassMaterialBackground` (dark `#26262899`, light `#f5f5f78c`) + `backdrop-filter: saturate(2.2) blur(16px)` |
| Nav item | li 227×36, `padding 4px`, `margin-bottom 4px`, `radius 8px`, 14px/20px weight 400, `--systemPrimary` |
| Nav icon | 24×24 inline SVG, gap ≈ 6px before the label |
| Selected item | background `rgba(255,255,255,.05)` (dark) / `--navSidebarSelectedState` (`#3c3c431a` light); icon fill `--keyColor` (dark `#fa586a` on non-Apple fonts), icon weight 600 |
| Section header | `.navigation-items__header`: 12px/15px weight 600, `--systemSecondary`, padding 4px 8px, radius 6px; an "Edit" link sits at the right end of the Library header |
| Account (signed in) | bottom, 24px row: avatar circle and name, opens the account context menu |

### Floating player pill (`.chrome-player`)

| Property | Value |
|---|---|
| Size | 668×56 at 1440px; grid `152px 427px 57px` (controls, LCD, actions); padding 0 16px; `border-radius: 1000px` |
| Position | `.player-bar` sits 20px above the viewport bottom, centred in the content column. When the side panel opens, `.player-bar` gets `padding-inline-end: 320px` (transition `padding-inline-end .3s cubic-bezier(.215,.61,.355,1)`) so the pill re-centres in the narrower space |
| Glass | `::before`: `--glassMaterialBackground` + `backdrop-filter: saturate(2.2) blur(16px)` + `box-shadow 0 10px 40px var(--glassMaterialShadowColor)` (`#0003` dark / `#0000001a` light) |
| Inner stroke | `::after`: four inset 0.5px white box-shadows (one per corner direction) at `opacity: .25`. The same "has-borders" stroke is used on artwork |
| Playback controls | shuffle · previous · play/pause · next · repeat, all 24×24 glyphs; play is disabled at 40% opacity when nothing is loaded |
| LCD | artwork 34×34 radius 6px (hover overlay `rgba(51,51,51,.3)` with an "expand" glyph that opens full screen); title 13px/16px **600** (marquee scroll when it overflows); subtitle 12px/15px **500** `--systemSecondary` ("Artist — Album", each a link); progress bar 2px track along the bottom (padding 4px 0), with times appearing on hover |
| Empty LCD | centred app glyph, 20×24 (BitChord uses its own mark) |
| Actions | Lyrics (shown only when the track has lyrics) · Up Next (24×28, radius 4px) · volume (24×28; slider on hover) |

### Main column

- `.header-nav` (page title): margin 0 40px, padding-top 32px; h1 **34px/40px, 700**.
- `--bodyGutter`: **25px** below 1000px, **40px** at ≥1000px. Every section is inset by it.
- `.section`: padding-top 12px. Each shelf section is header + shelf, and the next one starts right after it (sections are ≈ 300px tall at 1440px).
- Section header `h2.title`: **17px/22px, 700**. When it links somewhere (to "See All"), a chevron glyph follows the text and the whole title is the link.
- Footer: 11px/14px, background `--footerBg` (`#323232` dark), padding 14px 40px 90px.

### Sign-in / upsell banner (signed out)

A full-width red band (`--keyColor` background) is pinned at the bottom, above the player: a 2-line message, with a white "Try It Now" pill on the right. **BitChord uses this exact pattern for the "Install the extension" prompt** (decided in prompt §1).

---

## 2. Home / Browse shelves

Every shelf is `section.shelf-grid` with **inline custom properties** that set its column count per breakpoint:

```
--grid-type: <type>; --grid-rows: N;
--grid-max-content-xsmall: <px>; --grid-column-gap-xsmall: 10px;
--grid-small: a; --grid-medium: b; --grid-large: c; --grid-xlarge: d;
--grid-column-gap-{small..xlarge}: 20px; --grid-row-gap-*: 24px (0 for track lockups)
```

`ul.shelf-grid__list` is a horizontal grid: `grid-auto-flow: column; grid-template-rows: repeat(var(--grid-rows), max-content); grid-auto-columns: calc((100% − (n − 1)·gap) / n)`, where n is the column count for the current breakpoint. The list scrolls with `scroll-snap-type: x mandatory`, `scroll-behavior: smooth` (auto under reduced motion), `overscroll-behavior-x: none`, and a hidden scrollbar. Each `li` has `scroll-snap-align: start`, plus a 15px padding with a −15px margin so that hover shadows aren't clipped (`--standard-lockup-shadow-offset: 15px`). In full-width shelves, the list's inline padding is `--bodyGutter`.

| Shelf type (Apple's name) | Lockup | Rows | Columns xs(px) / S / M / L / XL | Artwork at 1440 | Radius |
|---|---|---|---|---|---|
| A: editorial hero | `editorial-card` (eyebrow, title, subtitle, 16:9 image) | 1 | 298 / 2 / 2 / 3 / 3 | 540×309 | 10px |
| G: square albums/playlists | `square-lockup` | 1 | 144 / 4 / 5 / 6 / 6 | 204×204 | 7px |
| TrackLockupsShelf: songs | `track-lockup` (40px art row) | 4 (search: 3) | 270 / 2 / 3 / 4 / 4 | 40×40 | 5px |
| B: horizontal lockups | `horizontal-lockup` (art left, 2 lines right) | 2 | 298 / 2 / 3 / 4 / 4 | 97×97 | 5px |
| C: videos | `vertical-video` (16:9) | 1 | 200 / 3 / 4 / 5 / 5 | 260×146 | 7px |
| H: artists (circles) | circular artwork + centred name | 1 | — / 6 / 8 / 10 / 10 | 118×118 | 50% |

Breakpoints for these columns (`--viewport-default`): xsmall < 1000px (the xs pixel width; the shelf scrolls), small 1000–1259px, medium 1260–1579px, large 1580–1939px, xlarge ≥ 1940px.

**Arrow buttons** (`.shelf-grid-nav__arrow`): only at ≥1000px. 28×52, `border-radius: 1000px`, glass (`--systemStandardThinMaterialSover`, `#28282880` dark) with `backdrop-filter: saturate(180%) blur(60px)`. Glyph 8.5×30.5, fill `--systemSecondary`. Vertically centred on the artwork; transitions `background-color/color/opacity .25s linear`. When disabled the button is `opacity 0`. They show on shelf hover (`.shelf-grid--onhover`).

### Square lockup

```
div.square-lockup-wrapper > div.product-lockup
  div.product-lockup__artwork.has-controls
    div.artwork-component (aspect 1, radius 7px, --artwork-bg-color: <dominant colour> as placeholder)
    div.product-lockup__controls  (absolute, opacity var(--scrimOpacity,0) → 1 on hover; transition opacity .1s ease-in)
      a.product-lockup__link   (full-size overlay link)
      button.play-button--platter  30×30 at bottom-left, 10px inset
      button.more-button--platter  30×30 at bottom-right, 10px inset
  div.product-lockup__content
    title 12px/15px 400 --systemPrimary (one line, ellipsis); margin-top ≈ 4px after the artwork
    subtitle 12px/15px 400 --systemSecondary, margin-top 1px
```

- Hover scrim: `--lockupHoverBGColor` `#3333334d` over the artwork.
- Platter buttons: circular glass (`--systemStandardThinMaterialSover`, blur 60px saturate 180%), glyph white.

### Track lockup (song rows inside shelves)

A grid of `52px 1fr auto` (artwork 40×40 radius 5px + 12px gap; content padded 0 4px; ⋯ button 28×28). Row padding 7.5px 14px 7.5px 0, height 55px. Title 13px/16px 400; subtitle 12px/15px 400 `--systemSecondary`. The explicit badge is a 10×16 glyph 4px after the title. Hovering the artwork reveals a play glyph over a `#3333334d` scrim.

### Horizontal lockup

97×97 artwork; title 15px/20px 400 (up to 2 lines); subtitle 12px/15px `--systemSecondary`.

### Editorial card

Eyebrow 11px/14px **600** uppercase-style, `--systemSecondary`; title 13px/16px; then the 16:9 image (radius 10px) with its caption overlaid at the bottom on a gradient scrim.

---

## 3. Album page

```
div.container-detail-header-wrapper     (::after paints --joe-color behind the header)
  div.container-detail-header (grid; areas: "secondary-actions ×3" / "artwork headings headings" /
                                "artwork description description" / "artwork primary-actions secondary-actions")
    [slot=artwork] .artwork-wrapper  270×270, radius 10px, margin-right 34px,
                                      box-shadow 0 10px 20px 0 var(--radiosityShadowColor)
      .artwork__radiosity   (a copy of the artwork, blur(20px) saturate(2), opacity .3, scale .88: a glow behind it)
      .artwork__main        (pointer-driven sheen and refraction strokes: Apple's tilt effect, optional)
    .headings
      h1.headings__title     26px/30px 700
      .headings__subtitles a 26px/30px 400 (artist link, primary colour)
      .headings__metadata-bottom 12px/15px 600 uppercase-free, "Rock · 2026", secondary
    .description p           13px/18px secondary, 3 lines max then "MORE" (11px/14px 600)
    .primary-actions         margin-top 14px: [▶ Play] [⤮ Shuffle] pills (signed out: one "Preview" pill)
                             pill: height 36px, padding 0 12px (icon + label), radius 24px, 15px/20px 600,
                             background white with black text (dark mode) / black with white text (light)
    secondary actions        ⋯ 30×30 circle (top-right) + add-to-library
div.songs-list (display: table; table-layout: fixed; font: --callout 12px/15px; margin 0 --bodyGutter)
  div.songs-list-row  ×N   height 46px
    col favourite-or-popular (star / popularity) | col song: index 40px wide → name 13px/16px primary |
    col time: "PREVIEW" 10px/13px 500 when signed out, duration 13px secondary, ⋯ 28×28 (margin-left 7px)
div.tracklist-footer        margin −12px 40px 0, padding-left 12px, 13px/18px secondary:
                            "October 9, 2026" / "13 songs, 58 minutes" / "℗ 2026 Label"
section.section--alternate  "More By <Artist>", "Featured On", "You Might Also Like" shelves on
                            --opaqueShelfBG blended with joe-color (rgb(16,26,38) for this album)
```

- **Theme override from the artwork.** Album and playlist pages set `--joe-color` (the artwork's background colour) on `.app-container.has-theme-override`, plus derived `--systemSecondary-vibrantOnDark-blended-plusL` (secondary text), `--systemTertiary-…` and `--opaqueShelfBG-blended-plusD` (alternate section background). It's dark or light layout (`container-detail-header--dark-layout`) depending on the artwork's luminance. BitChord takes the dominant colour from the thumbnail on the client to do the same.
- **Row states.**
  - Dividers: 0.5px `--labelDivider` top border on each cell (plus a bottom border on the last row); they hide on hover.
  - Hover: `--tracklistHoverColor` (`#ffffff0f` dark / `#0000000f` light) with `--songs-list-row-border-radius: 6px` on the outer cells.
  - Alternate-row tint `--tracklistAltRowColor` exists but is near-invisible (`#ffffff04`).
  - Selected row: `--selectionColor` (`#c11b2e` dark, `#dd1f34` light), and every label in it turns white.
  - On hover the index number is swapped for a play glyph; the playing row shows animated bars.

## 4. Artist page

- `.artist-expression-header`: a full-bleed hero, 604px tall at 1440px (the content column; it bleeds under the sidebar edge).
  - Artwork or video fills it.
  - `.artist-expression-header__artwork-tint` paints the artist colour at 95% below the image.
  - `.blur-frame` applies `backdrop-filter: blur(30px)` through a radial mask, so the bottom of the image melts into the page.
  - A centred stack: badge ("Upcoming Concerts" pill, 30px), artist name or logo, then an actions row.
- Actions row (gap 16px, padding-top 16px): Info 36×36 circle · **Play 56×56** circle (key colour) · Favourite 36×36. The small circles have `box-shadow 0 2px 6px −4px #0006, inset 0 0 2px #ffffff1a`.
- Then: "Pinned by <Artist>" plus **Top Songs** (track lockups, 160px art for the pinned item, then a 4-column grid of 50px rows) · Albums (G) · Music Videos (B, 353×200) · Artist Playlists (G) · Singles & EPs (G) · Upcoming concerts (calendar lockups, 2 columns) · More To See (C) · Similar Artists (H, 118px circles).

## 5. Playlist page

The same template as the album page: `container-detail-header`, a description, `songs-list` (playlist variant: rows have a 40×40 artwork column instead of the index, with an Artist column and an Album column), and a footer with the count and duration. It uses the same theme override from the artwork.

## 6. Search

- Landing (`/search`): a "Browse Categories" grid, `ul.grid.grid--category-bricks`: 4 columns at 1440px (256px), gap 20px, tiles at 16:9 (256×144) radius 10px, with the title overlaid at the bottom-left.
- **Search field** (`.search-input__text-field`): 492×38 centred at the top of the content, `border-radius 1000px`, `border 1px solid --searchBarBorderColor` (`#ffffff26`), background `--pageBG`, padding 6px 5px 5px 28px (magnifier glyph inset left), 13px text.
- **Suggestions dropdown** (`ul.search-suggestions`, 302px wide, 3px under the field):
  - Glass: `rgba(40,40,40,.7)` + `backdrop-filter: blur(60px) saturate(2.2)`, radius 9px, padding 6px 0, `box-shadow inset 0 0 0 1px #fff3, 0 8px 40px #0000008c`, plus a `1px solid #0003` border.
  - Text hints: 23px rows, padding 4px, margin 0 6px, radius 3px, grid `16px 1fr` (glyph, then text). The typed part is primary, the rest is secondary (`--systemSecondary`). The weight is the same, colour only.
  - Lockup hints: 46px artwork (artists circular, others radius 5px), primary 13px, secondary 12px.
  - Hover or keyboard selection: `--keyColor` background, white text.
- **Results** (`/search?term=`):
  - A sticky `.content-scope-bar` (62px, `--pageBG`-like background, padding 12px 0) holds the field, plus a segmented scope control (Apple Music / Your Library) when signed in.
  - **Top Results**: grid of 3 columns, gap 20px; 118px cards with 90px artwork (circle for artists), 15px/20px title, secondary kind line; `radius 8px` hover background.
  - Then shelves: Artists (H) · Albums (G) · Songs (track lockups, 3 rows) · Playlists (G) · Stations (G) · Music Videos (C).

## 7. Up Next and Lyrics panels

- Both open in `div.side-panel`: `position: fixed`, right 0, **300px** wide, full height. Glass: `--glassMaterialBackground` + `saturate(220%) blur(16px)`, `box-shadow 0 10px 40px var(--glassMaterialShadowColor)`. It overlays the content (the page doesn't reflow); only the player re-centres. Open/close is a 0.3s slide (`animation-duration: .3s; animation-fill-mode: forwards`).
- **Up Next**:
  - Header: padding 23px 20px 12px; "Up Next" 17px/22px **700**; "Clear" 15px/20px `--keyColor`, right-aligned.
  - Items: `li.up-next-item` 264×55, padding 0 10px, radius 6px, grid `[art] 40px [title] 1fr [right] 40px`, gap 12px. Title 13px primary, subtitle 13px secondary. On the right, the duration (13px secondary) is swapped on hover for the ⋯ button (28×28).
  - A remove ✕ (22×22 circle) appears over the artwork's top-left on hover. Rows reorder by drag.
  - Below the queue: an "Autoplay" section (similar tracks), which we drive from YouTube Music radio.
- **Lyrics panel** (`.lyrics__sidebar` hosting `<amp-lyrics offset-ratio="0.15">`):
  - The current line sits at 15% of the panel height.
  - A 28×28 translation button (radius 8px, `#ffffff1a` + blur 60px) shows at the top-right on hover.
  - The empty state is centred: "No lyrics available" 12px 600 secondary, then a 12px explanation. BitChord hosts **am-lyrics** here instead (prompt §10.2).
- **Full-screen player and lyrics** (`article.lyrics__container`, a modal over a `#00000073` backdrop):
  - Grid areas `"controls lyrics"`; at 1440px the columns are `461px 576px` with padding 0 144px (31vw/38vw with 12vw padding at medium, 30vw/38vw with 13vw padding at large).
  - Left: artwork 461×461, radius 10px, `0 4px 10px #0000001a`, with a radiosity glow (blur 20px saturate 2 at 40%). Under it: metadata (title, artist), the progress bar, transport (315×52), and volume.
  - Right: lyrics, with a vertical fade mask: `linear-gradient(180deg, transparent, #000 80px, #000 50%, transparent)`.
  - The background is the artwork, blurred and animated, over `--nowPlayingBackdropBG` `#878787`. Close ✕ at the top-left (20px, 16px).
  - Lyric tokens:
    - Colours: line `--systemTertiary-onDark`, current line `--systemPrimary-onDark` with `scale 1.05`, hover `--systemSecondary-onDark`.
    - Line font size by breakpoint: 28px (xsmall) → 38px (≥980px) → 50px (≥1320px) → 72px (≥1680px), line height ≈ 1.2.
    - Secondary (background-vocal) text is 0.54× the line size, translation 0.64×.

## 8. Context menus, toasts, modals, focus

- **Context menu** (`amp-contextual-menu`):
  - Glass panel (`--systemStandardThickMaterialSover`, `#282828b3` dark + blur), min item width **185px**.
  - Items 13px; hover background `--systemQuinary`. Separators: 0.5px `--vibrantDivider`. Destructive items in `--contextMenuOverrideTextColor` (`#ff3b30`).
  - z-index `--z-contextual-menus` (9951), with a scrim one below it.
- **Modals**: scrim `--modalScrimColor` `#00000073`, z `--z-modal` (10001), card radius 14px; close button 30px circle `--modalCloseButtonBGColor` (`#333336` dark / `#e8e8ed` light). They enter with a fade plus a slight zoom (`modalFadeIn`, `modalZoomIn`).
- **Focus ring**: `outline: 4px solid rgba(var(--keyColor-rgb), .6)` (`--keyColor-focus-color`), offset 1–2px; ⋯ triggers use `box-shadow: 0 0 0 4px` instead. Only on `:focus-visible`.
- **z-index scale**: default 1 · bubbles 50 · gpu 1001 · web chrome 9901 · context menus 9951 · modal 10001.
- **Global transition**: `opacity .1s ease-in` (`--global-transition`). Panel and player motion: `.3s cubic-bezier(.215,.61,.355,1)`. Shelf arrows: `.25s linear`.
- **Keyboard** (the web player's shortcuts, which BitChord matches): Space play/pause; ⌘/Ctrl+→ / ← next/previous; ⌘/Ctrl+↑ / ↓ volume; ⌘/Ctrl+F or `/` focuses search; Esc closes full screen or a modal.

## 9. Colour tokens

Apple defines every colour as a CSS variable with `-onLight`, `-onDark`, `-vibrant` and `_IC` (increased contrast) variants. Dark values apply under `@media (prefers-color-scheme: dark)`; increased-contrast values under `(prefers-contrast: more)`.

Apple also ships a set of overrides for browsers **without** `font: -apple-system-body` (everything except Apple devices). Because BitChord uses DM Sans on every platform, **we always use those overrides**: higher-contrast text alphas and a deeper key colour on light.

| Token | Light | Dark | Notes |
|---|---|---|---|
| `--pageBG` | `#ffffff` | `#1f1f1f` | |
| `--systemPrimary` | `#000000e0` | `#ffffffeb` | non-Apple-font variant |
| `--systemSecondary` | `#0000008f` | `#ffffffa3` | (Apple-font: `#00000080` / `#ffffff8c`) |
| `--systemTertiary` | `#0000007a` | `#ffffff66` | |
| `--systemQuaternary` | `#0000001a` | `#ffffff1a` | |
| `--systemQuinary` | `#0000000d` | `#ffffff0d` | |
| `--keyColor` | `#d60017` | `#fa586a` | non-Apple-font; Apple-font: `#fa233b` / `#fa2d48` |
| `--keyColorBG` | `#d60017` | `#fa233b` | filled red buttons and banners |
| `--selectionColor` | `#d60017` | `#c11b2e` | selected track row |
| `--labelDivider` | `#00000026` | `#ffffff1a` | hairlines (0.5px) |
| `--vibrantDivider` | `#3c3c434a` | `#ebebf530` | menu separators |
| `--systemGray` … `Gray6` | `#8e8e93 #aeaeb2 #c7c7cc #d1d1d6 #e5e5ea #f2f2f7` | `#98989d #636366 #48484a #3a3a3c #2c2c2e #1c1c1e` | |
| `--systemRed` / `Green` / `Blue` | `#ff3b30` `#28cd41` `#007aff` | `#ff453a` `#32d74b` `#0a84ff` | |
| `--glassMaterialBackground` | `#f5f5f78c` | `#26262899` | sidebar, player pill, side panel |
| `--glassMaterialShadowColor` | `#0000001a` | `#00000033` | |
| `--systemStandardThinMaterialSover` | `#f6f6f67a` | `#28282880` | arrows, platter buttons |
| `--systemStandardThickMaterialSover` | `#f6f6f6b8` | `#282828b3` | context menus |
| `--systemHeaderMaterialSover` | `#ffffffcc` | `#1e1e1ecc` | sticky headers |
| `--navSidebarSelectedState` | `#3c3c431a` | `#ffffff0d` (measured) | |
| `--tracklistHoverColor` | `#0000000f` | `#ffffff0f` | |
| `--shelfBG` / `--opaqueShelfBG` | `#0000000d` / `#f2f2f2` | `#ffffff0d` / `#2b2b2b` | alternate sections |
| `--footerBg` | `#fbfbfb` | `#323232` | |
| `--lockupHoverBGColor` | `#3333334d` | `#3333334d` | artwork scrim |
| `--genericJoeColor` | `#ebebeb` | `#323232` | artwork placeholder |
| `--modalScrimColor` | `#00000073` | `#00000073` | |
| `--searchBarBorderColor` | `#00000026` | `#ffffff26` | |
| `--segmentedControlBG` / selected | `#7676801f` / `#ffffff` | `#7676803d` / `#636366` | |
| `--button-pill-color` / `-background-color` | `#fff` / `#000` | `#000` / `#fff` | Play/Shuffle pills |
| `--playerPlatterButtonBGFill` | `#6c6c6c` | `#ffffff` | |
| `--nowPlayingBackdropBG` | `#878787` | `#878787` | |
| `--radiosityShadowColor` | `#0000001a` | `#0000004d` | artwork drop shadow |
| `--dialogShadowColor` | `#00000040` | `#0000008c` | |

## 10. Type scale

From `:root` (`font: <weight> <size>/<line-height> var(--font-family)`). Each style also has `-medium` (500), `-semibold` (600), `-bold` (700), `-heavy` (800), `-emphasized`, `-short` (tighter) and `-tall` (looser) variants. Letter spacing is `normal` throughout.

| Token | Weight | Size | Line height | Used for |
|---|---|---|---|---|
| `--header-emphasized` | 700 | 34px | 40px (1.176) | page titles (h1) |
| `--large-title-emphasized` | 700 | 26px | 32px (1.231) | album/playlist title |
| `--large-title` | 400 | 26px | 32px | album artist line |
| `--title-1-emphasized` | 700 | 22px | 26px (1.182) | |
| `--title-2-emphasized` | 700 | 17px | 22px (1.294) | section headers, panel headers |
| `--title-3` / `-emphasized` | 400 / 600 | 15px | 20px (1.333) | horizontal lockup title, Play pill, Clear |
| `--body` / `--headline` | 400 / 700 | 13px | 16px (1.231) | base text, nav header |
| `--body-reduced` | 400 | 14px | 20px (1.429) | sidebar nav items (`--title-navigation` at ≥484px) |
| `--callout` / `-emphasized` | 400 / 600 | 12px | 15px (1.25) | lockup title/subtitle, track list, metadata line |
| `--subhead-emphasized` | 600 | 11px | 14px (1.273) | eyebrows, MORE |
| `--caption-2` / `--footnote` | 500 / 400 | 10px | 13px (1.3) | PREVIEW, badges |

Responsive editorial styles (hero headlines) step up at 1000px, 1260px and 1580px: `--headline-reduced` is 28px, then 32px, then 40px; `--eyebrow` is 21px, then 24px.

## 11. Breakpoints

| Width | What changes |
|---|---|
| < 484px | Sidebar hidden. A 52px glass top bar holds a menu button, the centred logo and an account button. The player becomes a full-width `mini-player` (366×52) docked at the bottom. Search sits inside the page. Grid layout `xs-1` (`xs-2` from 415px) |
| 484–767px | Sidebar visible, `33.884vw` wide ("constrained") |
| ≥ 767.32px | Sidebar 260px ("large") |
| < 1000px | `--bodyGutter: 25px`; shelves scroll by touch at fixed xs widths; no arrow buttons |
| ≥ 1000px | `--bodyGutter: 40px`; shelf columns "small"; arrow buttons |
| ≥ 1260px | shelf columns "medium" (1440px falls here) |
| ≥ 1580px | "large" |
| ≥ 1940px | "xlarge" |
| 980 / 1320 / 1680 / 2000 / 2561px | full-screen lyrics and info layout steps |

## 12. Scroll behaviour

- The page scrolls inside `#scrollable-page`; the sidebar and the player pill stay fixed.
- On the search results page, the scope bar is sticky at the top, with `--systemHeaderMaterialSover` glass once it scrolls.
- Shelves: horizontal scroll-snap; the arrows page by one viewport of columns.
- The artist hero is fixed (`--fixed`) and the content slides over it; a scrim (`__scrim-scroll`) darkens it as you scroll.

## 13. Not measured live (and why)

- Synced lyrics animation: the signed-in test account plays 30-second previews only, and previews have no lyrics. am-lyrics renders our lyrics with its own animation anyway (prompt §10.2). The panel and full-screen sizing above come from Apple's CSS.
- Light appearance was read from the token definitions, not from screenshots. The pane follows the app's dark theme.
- Screenshots at 1024×768 and 390×844: the pane's emulated captures are too small to read detail, so the breakpoint behaviour above comes from the DOM tree at each width and from the CSS media queries.

## 14. Signed in (measured 2026-10-15, phase 4)

Measured with the user's own account in the built-in pane. The account has no subscription, so Apple still shows previews only.

### Album and playlist actions

The header's actions are a three-column grid: `primary-start primary-center primary-end`, `grid-template-columns: 1fr auto 1fr`, gap 12–14px, `margin-top: 14px`. Without a subscription Apple shows only a "Preview" pill, so the values below come from its CSS rather than a live element.

- **Play**: a pill. Height 36px, min-width 150px, radius 24px, padding 0 12px, `--title-3-semibold` (15px/20px 600). Background `--button-pill-background-color` (white on dark), text `--button-pill-color` (black). Glyph, then "Play". It sits in the centre column.
- **Shuffle** (start column) and **Add to library** (end column) are circles: 36×36, `aspect-ratio: 1`. Background `--button-circle-background-color` (`#ffffff0a` dark / `#0000000f` light), border `0.75px solid --button-circle-border-color` (`#ffffff14` / `#0000000a`).
- At the large header layout, pills are 48px tall and 160px wide in `--title-2-semibold`; circles are 48×48.
- The ⋯ menu is a 30px circle at the header's top right.

### Playlist track list (also every song "See All")

- Header row: 32px tall. Labels "Song", "Artist", "Album" and a time column, in 12px/15px 600 secondary colour, not uppercased. Static (not sticky).
- Rows are 54px. At 1440px the columns are Song 396px, Artist 274px (padding-right 10px), Album 274px (padding-right 16px), Time 141px (padding-right 18px).
- The Song cell holds a 40×40 artwork (radius 5px, 7px from the row's left edge), then the title 13px/16px primary at x+59. The play button sits over the artwork on hover.
- Artist and album are links in the secondary colour (13px/16px). The outer cells round at **12px** on hover (the album list uses 6px).
- No alternate-row tint in dark mode.
- A song "See All" page is just the page title (34px) followed by this list, inside a section with top spacing (padding-top 28px).

### Home shelves (signed in)

- "Top Picks for You" and "Playlists Made for You" use **powerswoosh** cards: 3:4 portrait (260×347 at 1440px), C-grid columns 3/4/5/5.
  - Radius 10px. The artwork fills the card over `--artwork-background-color`.
  - A **46px chin** at the bottom: blurred artwork with a legibility gradient. Inside it, padding 0 16px: eyebrow 11px/14px 600 white (margin-bottom 3px), title 12px/15px white (margin-bottom 15px).
  - A 30px dark platter play button at the bottom right (10px inset), above the chin.
- "Recently Played", genre and mood shelves: square lockups (G).
- "Recommended Playlist": a single full-width hero, 1085×444.
- "Concerts": rows with 56px artwork.
