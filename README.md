# BitChord - Aesthetic YouTube Music Client

![BitChord banner](Banner.png)

An unofficial YouTube Music client for Android, built with Jetpack Compose. BitChord talks to YouTube Music's own web API (Innertube) directly — no official API key, no ads, no first-party app.

> BitChord is not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

## Features

- **Sign in with your Google account** — an in-app WebView runs the real `accounts.google.com` login (2FA and passkeys work as normal); only the resulting session cookies are captured, never the credential itself.
- **Search, browse and play** anything available on YouTube Music — songs, albums, artists, playlists.
- **Gapless playback with true crossfade**, adjustable 0–12s — two overlapping decoders on an equal-power curve, applied to manual skips as well as track ends, powered by Media3/ExoPlayer.
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
./gradlew assembleDevDebug
```

A debug build needs no extra setup. For a signed release build, create a keystore and a `keystore.properties` (see [`keystore.properties.example`](keystore.properties.example)):

```bash
keytool -genkey -v -keystore bitchord-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 -alias bitchord
```

Then:

```bash
./gradlew assembleProdRelease
```

The APK lands in `app/build/outputs/apk/prod/release/`. Without `keystore.properties`, the release build still runs but produces an unsigned APK.

### Build flavors

There are two: `dev` and `prod`. They exist only so a build you're working on can sit installed alongside the one you actually listen to — `dev` ships under a separate application id (`com.dev.bitchord`), is labelled "BitChord Dev" in the launcher, and carries a small "Dev" badge next to the logo in the app. `prod` is the shipped package and is what releases are cut from.

The flavourless `assembleDebug` and `assembleRelease` tasks still work, but each builds *both* flavors; name the variant (`assembleDevDebug`, `assembleProdRelease`) to get one APK in one place.

## Project structure

```
app/src/main/java/com/music/bitchord/
├── auth/          Google/YT Music sign-in
├── data/          Innertube client, models, settings, lyrics
├── playback/       Media3 service, queue, crossfade, sleep timer, cache
└── ui/            Screens (home, search, library, player), theming, components
```

## Contributing

Contributions are welcome — bug fixes, features, or cleanup. Open a PR, or open an [issue](../../issues) first for anything sizable so it can be discussed before you put work into it.

Found a bug or have a feature request? [File an issue](../../issues/new) with as much detail as you can (device, Android version, steps to reproduce, logs if you have them).

## Disclaimer

- This project is developed for **educational and research purposes only** (studying media playback, reverse-engineered API clients, and Android app architecture).
- BitChord is **not affiliated with, endorsed by, sponsored by, or in any way officially connected to YouTube, YouTube Music, Google LLC, or any of their subsidiaries or affiliates**. All trademarks, service marks, and trade names (including "YouTube" and "YouTube Music") are the property of their respective owners and are used here only for descriptive purposes.
- BitChord does not host, store, or distribute any copyrighted audio, video, or artwork. It streams content directly from YouTube's own servers using the credentials of the account signing in; no media is cached to a shared or public server.
- This app relies on YouTube Music's **internal, undocumented web API**, not the official YouTube Data API. That interface is not public, stable, or supported by Google, can change or break at any time without notice, and using it **may be against YouTube's Terms of Service**.
- You are solely responsible for how you use this software and for ensuring your use complies with YouTube's Terms of Service, applicable copyright law, and the laws of your jurisdiction. The author(s) accept no liability for accounts suspended, content misused, or any other consequence arising from use of this app.
- This software is provided "as is", without warranty of any kind, express or implied.
- If you are a rights holder and believe this project infringes your rights, please [open an issue](../../issues/new) and it will be addressed promptly, including takedown of the repository if required.
