# BitChord

An unofficial YouTube Music client for Android, built with Jetpack Compose. BitChord talks to YouTube Music's own web API (Innertube) directly — no official API key, no ads, no first-party app.

> BitChord is not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

## Features

- **Sign in with your Google account** — an in-app WebView runs the real `accounts.google.com` login (2FA and passkeys work as normal); only the resulting session cookies are captured, never the credential itself.
- **Search, browse and play** anything available on YouTube Music — songs, albums, artists, playlists.
- **Gapless playback with crossfade**, adjustable 0–12s, powered by Media3/ExoPlayer.
- **Per-network audio quality** — separate quality ceilings for Wi-Fi and mobile data.
- **Playback speed control** (0.5×–2.0×) and **skip silence**.
- **Synced lyrics** via LRCLIB.
- **Sleep timer** — fixed presets or "stop after this track".
- **System equalizer** integration.
- **"Stats for nerds"** — codec, bitrate and sample rate overlay on the now-playing screen.
- **Dynamic now-playing background** — a mesh gradient generated from the current artwork's dominant colors.
- **Frosted-glass UI** — Telegram-style translucent bars via Haze, Material 3 theming with light/dark/system modes.
- **Background playback** via a proper foreground media session (lock screen controls, notification, Android media controls).

## How it works

BitChord doesn't use YouTube's official Data API. Instead it:

1. Speaks to the same **Innertube** endpoints the `music.youtube.com` web client uses, via a small Ktor-based client (`data/innertube`).
2. Resolves playable audio streams with [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), which handles YouTube's signature/`n`-parameter throttling.
3. Authenticates by capturing the session cookies from a real Google login (see [`auth/YtMusicLoginScreen.kt`](app/src/main/java/com/music/bitchord/auth/YtMusicLoginScreen.kt)) rather than reimplementing OAuth.

## Tech stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Playback | Media3 / ExoPlayer, MediaSessionService |
| Networking | Ktor client + kotlinx.serialization |
| Stream resolution | NewPipeExtractor |
| Images | Coil 3 + Palette |
| Blur / glass effects | Haze |
| Auth storage | AndroidX Security (encrypted prefs) |

Minimum SDK 26, target/compile SDK 36, Kotlin, portrait-only.

## Download

Grab the latest signed APK from the [Releases](../../releases) page. Sideloading requires enabling "Install unknown apps" for whichever app you download it with.

## Building from source

```bash
git clone https://github.com/kushagrasinghx/BitChord.git
cd BitChord
./gradlew assembleDebug
```

A debug build needs no extra setup. For a signed release build, create a keystore and a `keystore.properties` (see [`keystore.properties.example`](keystore.properties.example)):

```bash
keytool -genkey -v -keystore bitchord-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 -alias bitchord
```

Then:

```bash
./gradlew assembleRelease
```

Without `keystore.properties`, the release build still runs but produces an unsigned APK.

## Project structure

```
app/src/main/java/com/music/bitchord/
├── auth/          Google/YT Music sign-in
├── data/          Innertube client, models, settings, lyrics
├── playback/       Media3 service, queue, crossfade, sleep timer, cache
└── ui/            Screens (home, search, library, player), theming, components
```

## Disclaimer

This project is for educational purposes. It relies on YouTube Music's internal web API, which is not a public, stable, or officially supported interface — it can change or break at any time. Respect YouTube's Terms of Service when using this app.
