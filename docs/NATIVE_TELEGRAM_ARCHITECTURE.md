# Native Telegram provider: compatibility audit

## Target checked

- BitChord release tag: `v1.10`, commit `7448b92e` (2026-11-22 workspace audit).
- The default `main` branch is still at the `v1.9.2` release commit; work is based on `v1.10`.
- The native Android build now uses AGP 9.1.1, Gradle 9.3.1, Kotlin 2.3.20, JDK 17, SDK 37.0, NDK 28.2.13676358, and CMake 3.22.1. AGP 9.1.1 is the first pinned build plugin in this checkout that recognizes the API 37.0 platform package; this toolchain change is required for the declared `compileSdk = 37`.
- AGP 9's built-in Kotlin and new DSL remain temporarily disabled so the existing KGP and Android KMP library modules can build without an unrelated migration. Build-feature `resValues` is explicitly enabled for the modules using generated flavor strings.
- Morphe's Telegram patch is GPL-3.0-or-later and targets BitChord 1.8.1. Its native Telegram implementation is useful as a behavior and API reference, but its patch hooks and host assumptions are version-specific.
- The Telegram Music Addon is a Node.js/GramJS/HTTP range-streaming server. It informs desired library behavior only; its server protocol is excluded from the application design.

## Current BitChord architecture

- Kotlin/JVM 17 Android app, Jetpack Compose UI, `minSdk 26`, `targetSdk 36`, `compileSdk 37`; phone, TV and Wear OS targets share portions of the codebase.
- Source catalogue API: `MusicSource` (`health`, `search`, `stream`) plus `SourceRegistry` and `SourceResolver`. Source tracks travel in `Song.videoId`; playlists and queues already accept ordinary `Song` records.
- Search has YouTube, local library and Addons modes. The Addons mode uses active `MusicSource`s and returns normal `Song`s.
- Playback runs through `PlaybackService`, a Media3 `MediaLibraryService` with `ExoPlayer`, `DefaultMediaSourceFactory`, `AudioCache`, output routing and `ResolvingDataSource`. The cache's upstream `DataSource.Factory` is the native playback seam.
- Settings navigation is `AppNavigator` + Compose routes in `MainActivity`; account/integration entries live on the main settings surface. Preferences use encrypted preferences where appropriate. `androidx.work:work-runtime-ktx` is already present.
- Android source data is stored through encrypted `SharedPreferences`; cross-platform models live in `shared` and include generic metadata but do not model remote Telegram chats.
- Audio uses Media3 1.11.0 with project-specific native DSP, DSD extractors and output negotiation. Adding an audio decoder must not change non-Telegram behavior.

## Reference compatibility

- Morphe's extension is a mature on-device TDLib client with authentication, chat selection, local index, range playback, artwork, cache policy, quality analysis, cue sheets and ZIP traversal. Its runtime code is Java and talks to TDLib 1.8.69; TDLib, OpenSSL and libc++ binaries are prebuilt for arm64-v8a, armeabi-v7a and x86_64. Its application/activity, internal-addon HTTP compatibility layer and Media3 1.11 host hook are not directly reusable in BitChord 1.10.
- Reusable concepts: TDLib auth-state machine, one client manager, private no-backup session directory, encrypted API credential store, paged chat history, persisted index cursors, throttled/resumable work, file-range reads, a Media3 `DataSource`, audio header checks, artwork extraction and explicit cache leases.
- Source adaptation must preserve the Morphe GPL-3.0-or-later notice and attribution for any copied/derived code. Prefer clean Kotlin adapters around TDLib, with a NOTICE entry if its implementation is reused.
- The reference addon has a local memory index and direct server HTTP 206 range streaming. Its HTTP semantics cannot be carried across; Telegram must be accessed with TDLib requests from the Android app.

## Playback finding

TDLib `downloadFile` supports byte offset and byte limit, and `localFile` reports a downloaded prefix at the current download offset. This is enough to implement a local progressive `DataSource` by requesting bounded windows, waiting for file updates and reading only bytes known to exist. A separate range request can serve a seek, though TDLib has one in-flight range window per file in the reference implementation and requests should be serialized per file. This is not an HTTP server and does not imply that every container/codec supports efficient arbitrary seeking. The reliable fallback is a complete TDLib-managed download followed by normal Media3 file playback.

## Proposed implementation plan

1. Add a singleton native `TelegramClientManager` that owns TDLib lifecycle, auth-state flow, credentials, encrypted local database key, chat browsing, and orderly logout.
2. Persist selected sources and per-chat message cursors in app-private storage; scan Telegram history incrementally with a unique `(chatId, messageId, fileId)` identity and a local searchable index.
3. Implement `TelegramMusicSource : MusicSource`; search only the local index; emit stable `Song`s with Telegram metadata and source quality; resolve `SourceStream` to an internal Telegram URI.
4. Add an internal Media3 `DataSource.Factory` below the existing cache. Begin with TDLib prefix/window downloads, cancel/reprioritize on close and seek; force full-download fallback for formats whose partial read cannot be proven safe.
5. Add Compose settings for credentials/login, authorization state, source selection/index progress, reindex, cache size/clear and logout. Run indexing as resumable WorkManager work that shares the same TDLib manager and yields to playback.
6. Implement format/header and embedded-tag inspection on downloaded/available bytes; use the existing BitChord `Song` and player path. Add automated unit coverage for state transitions, message mapping, index/search, and range source behavior before device validation.

## Known risks and scope boundaries

- TDLib is a large native dependency across all three BitChord Android ABIs. Its Java API and native build must be pinned to the same commit. The release build must package the Boost license and all dependent native notices.
- TDLib's private file paths and partial files are implementation details; only read bytes covered by `downloadedPrefixSize` and do not hand incomplete files to Media3 as completed assets.
- No authenticated Telegram account or physical Android device is available in the workspace. Authentication, private-chat access, background restrictions, and real-device audio/output behavior therefore need user-device validation.
- The current local index uses TDLib audio metadata when Telegram supplies it and filename/MIME hints for generic audio documents. It does not yet parse embedded tags, verify lossless provenance, extract/cache artwork, or expose album/artist browsing. Search results label quality as unverified; playback leaves codec unset until Media3 inspects the bytes.
- The player requests bounded TDLib file ranges and falls back to a complete TDLib download when a range request fails. Range seeking, network interruption recovery, and background playback have not been exercised against a real account/device. Complete download and cache-management UI still need dedicated Telegram acceptance testing.
- Embedded metadata for an incomplete FLAC may be readable early; formats with tail metadata (for example, MP4/M4A) may require full retrieval before reliable tags/artwork are available.
- Bit-perfect, Dolby Atmos and multichannel claims require validation through BitChord's full decode/output path; metadata alone cannot establish output fidelity.

## Milestone 2 build validation

- `:app:compileDevDebugKotlin` passed after adapting to the generated TDLib 1.8.64 API: `GetChats(ChatList, limit)`, audio documents without `DocumentAttributeAudio`, and the current authorization-state model.
- The native sign-in state machine also handles Telegram's email-address and email-code verification states, in addition to phone codes and two-step passwords.
- `:app:assembleDevDebug` passed with native CMake, dexing, R8, duplicate-class checks, and the bundled Wear APK. TDLib's JNI libraries are packaged in arm64-v8a, armeabi-v7a, and x86_64. The AAR also contains x86, so `ndk.abiFilters` now excludes that unsupported ABI from the universal APK.
- The final full app unit suite ran 1,340 tests: 17 locale completeness cases failed and one was skipped. The locale failures report 50 existing Automix/Replay strings missing from translated resources. The Telegram-related audio-quality ceiling test passes in a targeted rerun.
- `:app:testDevDebugUnitTest --tests com.music.bitchord.AudioQualityCeilingTest`, `:shared:jvmTest`, and `:sharedUi:jvmTest` passed.
- The final dev debug APK packages both the TDLib notice and Boost Software License text under `assets/licenses/`; arm64-v8a artifact size is about 119 MiB.
- No ADB device or emulator was connected. Authentication, playback, seeking, notification controls, downloads, cache cleanup, and native library loading remain unverified on a physical device.

## Credential initialization stall fix

- The credential screen cached the last auth state (`Starting`) for error recovery. Even after an initialization error it rendered the cached state, leaving the loading indicator visible. In addition, TDLib authorization updates were handled in separate coroutines, allowing a late `Starting` write after the phone-number state.
- Authorization updates now pass through one ordered consumer gated until `Client.create()` returns and the client reference is assigned. Parameter setup no longer writes a stale loading state after its request completes.
- Initialization has a 30-second timeout, private-directory creation failures surface as errors, and retry closes the failed TDLib client while retaining the encrypted credentials/session database. Initialization errors route back to the credential form; later auth errors retain their specific sign-in step.
- Code-level validation: app Kotlin compilation, targeted `AudioQualityCeilingTest`, and full `:app:assembleDevDebug` passed after the fix. ADB reported no connected devices, so the reported physical-device stall could not be reproduced or verified here.

## Chat selector polish

- The chat row displayed `Chat.title` as primary text and a Channel/Group type as supporting text. A blank TDLib title therefore made the type appear to be the chat's name; blank title updates could also replace a previously known name. The mapping now preserves a known nonblank title and the UI uses a stable chat-ID fallback only if TDLib has never supplied a title.
- The selector now uses a bounded lazy list, compact selectable rows with full-row touch targets, selected-state color, truncation, filter empty state, and real title/type filtering. Refresh and reindex have distinct labels and progress feedback. Indexing status is surfaced from the WorkManager worker.
- Selected chat IDs remain encrypted and persist immediately. Selection updates now read and write under the same mutex, avoiding lost changes from quick multi-selection taps. The displayed selected count comes from that same persisted ID set. A complete chat refresh prunes inaccessible IDs; incomplete refreshes preserve selections.
- The current Telegram screen contains no floppy-disk/save action: choosing a source commits immediately to encrypted storage. No screenshot files were present with the pasted request, so layout was guided by the described issues and existing BitChord components.
- Automated `TelegramChatSelectionTest` covers TDLib title/type mapping, blank-title preservation, unsupported private chats, and idempotent selection updates. The Telegram settings implementation currently contains credentials, authentication, source selection, search, indexing, reindex, and logout; backup/restore, shared indexes, name correction, audio-quality preferences, and Telegram-specific download/cache controls are not implemented and are not presented as decorative settings.
