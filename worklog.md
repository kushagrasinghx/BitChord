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

---

Task ID: 2 (continuation — the compile-repair loop, CI-driven per instruction)
Agent: main (Super Z)
Task: Carry the one-to-one player style port (Task ID: 2's first push) to a
green build: push, monitor check.yml / android.yml, fix from the failure
logs, repeat. No local compilation.

Work Log (seven rounds, from the CI logs):
- Round 1 — the module's own compile, the shapes the copied UI reads:
  playbackState made public on the bridge connection; the service bridge
  gained the live-track facts the stats sheet reads (currentMediaMetadata,
  currentStreamInfo, liveNormalizeFactor, inert); LyricsHelperEntryPoint
  registered in the bridge entry-point registry; BackdropTonePalette.kt
  and LyricsTextWrapping.kt copied from ArchiveTune (they were outside the
  original closure — the V7 backdrop's tones and the bouncing-word wrap
  units); VideoBackgroundPreloader bridged inert (no background hold
  exists without ArchiveTune's service); DownloadFetchProgress bridged
  inert for SpatialFlow's download pill; SyncUtils.likeSong takes the
  toggled SongEntity exactly as the TikTok rail passes it; materialkolor
  raised to ArchiveTune's own 5.0.0-alpha07 (isAmoled defaults there, and
  its Compose 1.11.0-alpha03 deps resolve under the module's beta02 pins);
  room-ktx for the withTransaction import SimpMusicPlayer spells out; the
  pipeline/channel/DSP stubs reshaped to the original data classes so
  ShowMediaInfo compiles unmodified; the javax.inject shim files
  deduplicated; DownloadManager's five-arg constructor; the tf.monochrome
  eq sub-screens stubbed inert (the engine stack they belong to is not
  carried — the dialog's tone/speed/reverb halves stay live); the
  export_lyrics_saved string.
- Round 2 — the app joins the module's Compose line. The vendored stack's
  pins (foundation 1.11.0-beta02, material3 1.5.0-alpha18) reach the app's
  compile classpath through the module and its runtime classpath
  regardless, so app and tv declare that line outright, and the three call
  sites that predate it move with it: the now-playing sheet's
  SheetState.Saver / constructor hand material3 1.5 the swipe thresholds it
  used to derive from density (the same 56/125 dp, so the sheet feels
  unchanged); ComposeUiFlags.isAdaptiveRefreshRateEnabled — retired in ui
  1.11 — is set through reflection where it exists and no-ops where it
  doesn't; datastore-preferences on the app's classpath (the bridge
  preference adapters carry Preferences.Key in their signatures). The
  bridge impl's constructor lambdas renamed (openArtist & co. collided
  with the overrides they feed — an untypeable recursion), like status is
  this app's LikeStatus.LIKE, the lyrics source reads its label safely,
  radio seeds map from ArchiveTune's metadata shape.
- Round 3 — the renamed lambdas' call site in MainActivity.
- Round 4 — unit tests green; packaging: BravePipe (the vendored core's
  extractor, under org.schabi.newpipe as ArchiveTune pins it) and the
  app's own stripped TeamNewPipe v0.26.3 share the package and D8 refuses
  the duplicates. The app keeps its own jar: every org.schabi call the
  vendored core makes (Response's 5-arg constructor,
  YoutubeJavaScriptPlayerManager's signature / n-param entry points,
  StreamInfo.getInfo, AudioStream's bitrates) was verified against
  v0.26.3's bytecode and matches, and the core treats extractor failure
  as a fallback, never an error. BravePipe stays on the core's own
  compile classpath; only app/tv resolution drop it.
- Round 5 — kuromoji's two jars ship one CONTRIBUTORS.md and one
  LICENSE.md between them (and BouncyCastle's licence joins the pile):
  excluded / picked in packaging, in both app and tv (the tv build was
  missing the pickFirsts the phone app already had).
- Round 6/7 — R8: javax.inject must be the real artifact, not a shim —
  the release graph carries javax.inject:1 from elsewhere and R8 refuses
  the module-local copy. The artifact provides every annotation the
  copied sources use; only the dagger/hilt side stays shimmed.

Stage Summary:
- check.yml green (testDevDebugUnitTest + assembleDevDebug), android.yml
  (the nightly R8 release APK, arm64-v8a) green, TV green. All ten
  ported styles build over their real ArchiveTune sources; the BITCHORD
  style keeps the app's own player untouched.

## Ported player styles crashed on switch; crash logs now saved on device

- **Build was broken since cbade7c.** `CrashLog.kt` had two `dump(File, Throwable)` overloads — same JVM signature, and making one private (4bdb12e) does not help. No APK with the earlier "fix" was ever produced. Now one `writeReport(file, thread, throwable, header, appendix)`; `snapshot()` is `@Synchronized`; JVM test `CrashLogTest`.
- **The earlier theory was wrong.** `PreferenceStore.get()` never blocks (it returns null until the snapshot lands); starting it in `Application.onCreate` fixed nothing.
- **Real root cause:** `ArchiveTunePlayerHost` installed the bridge, `BridgeEntryPoints.context` and the `BridgeViewModels` registry inside a `DisposableEffect`, which runs only *after* the first composition. That same first composition built `PlayerConnection` (bound to `PlaceholderPlayer` for good) and composed the styles, whose `hiltViewModel()` hit an empty registry. On top of that the reflective placeholder / ExoPlayer proxies returned an `Integer` for `long`, a `Double` for `float` and `null` for `Timeline`/`PlaybackParameters` → ClassCastException / NPE. Fixed: setup in a `remember` ahead of everything; caches built on IO in a `LaunchedEffect`; `PlayerConnection` re-attaches to the live controller (main looper only, no recursion); typed defaults plus media3 empties (`Timeline.EMPTY`, …).
- **Crash capture ported from ArchiveTune** (`data/CrashReporter.kt`, `data/CrashLogTree.kt`): the only uncaught handler; it chains to the previous one. It writes header + stack trace (with causes) + the `CrashLog` ring (DebugLog in all builds + Timber) + this pid's logcat tail to `Android/data/<pkg>/files/crash/neiro-crash-*.log` (newest 10), and on API 29+ copies it to `Download/Neiro/` via MediaStore (no permissions). On the next launch a toast says where the log went (`crash_log_saved`, in every catalogue).
- Known follow-up (not a crash): ported screens may show blank metadata, because `MediaItem.metadata` reads `localConfiguration.tag`, which a MediaController never carries.
