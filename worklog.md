# Worklog

---

Task ID: 1
Agent: main (Super Z)
Task: Port the ArchiveTune Appearance settings (the features shown in the
user's screenshots) into Neiro's Appearance settings page: Liquid Glass
customization (refraction, blur, tint, optical quality), random theme on
startup, Color Palette picker with a custom palette editor, App icon (moved
from Miscellaneous into Appearance, matching ArchiveTune's placement),
app-wide Hide status bar, UI scale, and Blur intensity. Dark theme ("Theme"),
disable-blur ("Reduce dynamic blur") and disable-animations ("Reduce
animation") already existed in birchord's own idiom and were left as they
were, as the equivalents of ArchiveTune's rows.

Work Log:
- Studied ArchiveTune's AppearanceSettings / LiquidGlassSettings /
  PalettePickerScreen / Theme.kt / App.kt and Neiro's SettingsSheet, Theme,
  LiquidGlass, MainActivity, AppSettings and player backdrop code first.
- data/theme/ThemePalettes.kt (new): the 64-palette catalog, ThemeSeedPalette
  and ThemeSeedPaletteCodec ported verbatim — same palette ids, colors and
  "seedPalette:"-prefixed base64 JSON preference format, so themes exported
  from ArchiveTune import here unchanged. generateRandomPalette keeps
  ArchiveTune's hue offsets.
- AppSettings: LiquidGlassIntensity enum plus ten Liquid Glass tuning flows
  (intensity, refraction height/amount, blur radius, tint opacity, shadow
  depth, 3D depth, chromatic aberration, backdrop vibrancy, adaptive
  luminance), colorPalette, randomThemeOnStartup, hideStatusBar (app-wide),
  uiScale (0.85..1.30) and blurIntensity (0..64 dp, 48 default), each with
  clamped setters; readAll() grows and stores a random palette on every start
  when random-theme is on, exactly as ArchiveTune's App init does.
- Theme.kt: BitChordTheme takes a seedPalette; the scheme is grown per-role
  from the four seeds (ArchiveTune's mergedSeedColorScheme structure) through
  materialkolor, added at 2.1.1 — the last line built against Compose 1.7,
  which is what this app's BOM resolves (ArchiveTune's 5.0.0-alpha07 needs
  Compose 1.11 / Kotlin 2.4). Palette style follows the seed's chroma
  (Monochrome / Neutral / TonalSpot) as in ArchiveTune.
- LiquidGlass.kt: LiquidGlassTuning ported — every control is a multiplier
  around the fixed values the app has always drawn, so the stock look is the
  default preset; refraction height/amount scale the lens, blur radius scales
  the frost, tint and adaptive luminance scale the surface fill, vibrancy
  gates the saturation boost, shadow depth scales the drop shadow.
- MainActivity: decodes the palette into BitChordTheme, provides a scaled
  LocalDensity (fontScale * uiScale, dp layout untouched, as in ArchiveTune),
  and composes the app-wide HideStatusBar beside the player's own.
  StatusBarIcons.android.kt's HideStatusBar now reference-counts so
  overlapping hiders (app-wide + player) don't fight over the window.
- Player blur intensity: PlayerSettingsSource.blurIntensity flows from
  AppSettings through AndroidPlayerHost into NowPlayingScreen;
  rememberFullArtworkBlurImage bakes the strength into its cache key and
  boxBlurred maps 48 dp to the blur the player has always drawn (same radius,
  three passes), widening/narrowing with the setting.
- SettingsSheet (Appearance group): a "Customize Glass" row under the Liquid
  Glass switch opens the new Liquid Glass choice page (intensity segmented
  control, five optics sliders, four optical-quality switches, reset);
  Random theme on startup switch; Color Palette row (shows the palette's
  name) opening the new palette page — live preview card drawn in a real
  BitChordTheme grown from the seeds, custom palette editor with per-role
  RGB/hex color dialogs and Apply, the 64-preset swatch grid, and
  import/export through the document picker; the App icon picker moved here
  from Miscellaneous; Hide status bar switch; UI scale and Blur intensity
  slider rows.
- strings.xml: 126 new strings (palette names, glass tuning, seed editor).

Stage Summary:
- Every default multiplies to exactly the stock look: no palette selected, no
  glass tuning touched, 48 dp blur, 100% UI scale, status bar visible — the
  app renders identically until a setting is changed. Pushed to main; CI
  (Check: testDevDebugUnitTest + assembleDevDebug) to be confirmed green.

---

Task ID: 2
Agent: main (Super Z)
Task: Redo the player design styles port as a true one-to-one port. The
first attempt (commit 33fbec3) had adapted ArchiveTune's ten styles into
BitChord-flavoured rewrites under ui/player/designstyles/ — wrong: the user
asked for the styles exactly as ArchiveTune ships them, icons, animations,
queue pages, lyrics pages, transitions and everything related, with no
adaptation. This task replaces that with a vendored, byte-for-byte port.

Work Log:
- Deleted sharedUi/.../ui/player/designstyles/ (12 files, the adapted
  rewrites). Nothing else referenced them beyond the style enum wiring that
  stays.
- New Gradle module :archivetuneplayer (com.android.library, namespace
  moe.rukamori.archivetune, minSdk 26, JDK 17, the app's own pins: Compose
  BOM 2024.12.01 + foundation 1.10.0 + material3 1.3.1, coil 3.0.4,
  media3 1.11.0). Libraries ArchiveTune's stack needs that the app doesn't
  use are declared here: accompanist-lyrics, squigglyslider, backdrop,
  compose-shimmer, reorderable 3.1.0, lottie-compose 6.6.6,
  material-kolor 2.1.0 (last Compose-1.7 line), timber.
- 229 Kotlin files copied byte-for-byte from ArchiveTune dev @ d08d8da94
  under the original package moe.rukamori.archivetune: Player.kt and the
  eleven styles (V4/V5/V7/V9/V10, flamingo=AppleMusic, tiktok, simpmusic,
  spatialflow, looper), every queue sheet, lyrics screen, lottie icon,
  menu and transition they draw, plus their closure (ui/component, ui/menu,
  ui/theme, ui/utils, viewmodels, models, extensions, constants, playlist,
  lyrics pipeline plumbing, repository, playback queues).
- Vendored ArchiveTune's pure-JVM modules under archivetune/: core
  (InnerTube + NewPipe/BravePipe extraction, 122 files), canvas (6),
  betterlyrics (4), jiosaavn (1) — all byte-identical to upstream, wired as
  Gradle projects with their own pins (ktor 3.5.2, PipePipeExtractor via
  JitPack, protobuf-java excluded in favour of javalite as upstream does).
- The app-service layer those files talk to is reimplemented as a thin
  seam, marked "BRIDGE" in every file: PlayerConnection wraps the host's
  Media3 controller (MediaController is a Player) and mirrors the queue/
  metadata/sleep-timer surface; MusicService carries the ~20 members the
  player reaches (multi-source pickers and Discord sync inert);
  MusicDatabase answers from the host's data through ArchiveTuneBridge;
  DataStore keeps ArchiveTune's preference API over a module-local
  DataStore plus BridgedPreferences adapters; LyricsHelper serves the
  lyrics menu (empty searches); DownloadUtil and SyncUtils forward to the
  host. javax.inject / dagger.hilt / androidx.hilt are inert-annotation
  shims; hiltViewModel() resolves through the bridge's own factory
  registry. tf.monochrome, com.lastwave and the DSP chain are inert stubs
  (the pill simply hides, as it does when no DSP runs).
- bridge/ArchiveTunePlayerHost.kt: what the app mounts when a ported style
  is active — installs the host bridge, builds the PlayerConnection,
  provides every composition local (LocalPlayerConnection, LocalDatabase,
  LocalDownloadUtil, LocalSyncUtils, LocalMenuState,
  LocalBottomSheetPageState), and lays out ArchiveTune's own
  BottomSheetPlayer with its own mini player, byte-for-byte its hosting in
  ArchiveTune's activity. A BridgeNavController maps artist/album/search
  routes onto the host's pages.
- app/MainActivity.kt: when AppSettings.playerDesignStyle is not BITCHORD,
  the ArchiveTunePlayerHost mounts over the page and the app's own mini
  player, glass dock circles and full player stand down; BITCHORD keeps
  the app's player exactly as before. ArchiveTuneBridgeImpl answers the
  bridge: playback through the app's MediaController (playSongs,
  beginRadioQueue, enqueue surgery), likes and playlists through
  MainViewModel, lyrics translated from the app's LyricLine stream into
  ArchiveTune's LRC dialect, and the design-style / slider / button-style
  preferences bridged one source of truth.
- The module's resources are ArchiveTune's own (229 drawables incl. every
  style icon, 3 lottie raws, all 15 fonts, the strings the stack reads
  across 26 locales); app_name carried in its own values file.
- Compile verification by CI only, per the user's instruction: push,
  monitor check.yml, fix from the logs, repeat.

Stage Summary:
- All ten ported styles now run ArchiveTune's real code — the same icons,
  animations, queue sheets, lyrics screens and transitions the upstream
  app ships — over BitChord's playback through a thin bridge. BITCHORD
  stays the app's own untouched player. First push handed to CI; repair
  loop to follow.
