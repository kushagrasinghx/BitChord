# BitVinyl TV (Legacy / Unmaintained)

> [!NOTE]
> BitVinyl prioritizes Android phones exclusively (`:app`). The Android TV module is **not actively supported or distributed** and is excluded from normal builds, CI, and GitHub releases. Its source directory is retained temporarily while shared dependencies are audited for cleanup.

Android TV / Google TV build of BitVinyl: a remote-first Compose-for-TV interface
(home, library, search, settings, a full-screen now-playing view with synced
lyrics, display refresh-rate switching, spatial audio, and QR-code sign-in).

This module holds only what differs from the phone app. Everything else — the
data and playback layers, resources, native code — is built straight from
[`app/`](../app), together with the shared `:shared` and `:sharedUi` modules.

| Path | What it is |
| --- | --- |
| `src/main/java/.../ui/tv/**`, `TvActivity.kt` | The TV interface |
| `src/main/java/...` (same path as a file under `app/`) | A phone-app class TV patches; it replaces the `app/` one at build time |
| `src/main/res` | TV-only resources: banner, fonts, TV theme |
| `src/main/AndroidManifest.xml` | TV's own manifest (`LEANBACK_LAUNCHER`, `TvActivity`) |

## Build (Opt-In Legacy Target)

```bash
./gradlew -Pbitvinyl.includeTvWear=true :tv:assembleDevDebug
```

The `:tv` module is omitted by default unless `-Pbitvinyl.includeTvWear=true` is passed.
