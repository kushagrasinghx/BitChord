<div align="center">

<br/>

<img src="Banner.png" alt="BitChord TV Banner" width="100%" />

# 📺 BitChord TV • Version 1.0.0

### The Ultimate Apple Music-Inspired Living Room Experience for Android TV & Google TV

<br/>

[![Version](https://img.shields.io/badge/Version-1.0.0_Stable-FA2D48?style=for-the-badge&labelColor=0d1117)](https://github.com/nimalanrao/bitchordTV/releases)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge&labelColor=0d1117)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_TV_%7C_Google_TV-4CAF50?style=for-the-badge&labelColor=0d1117)](#tv-compatibility)
[![Performance](https://img.shields.io/badge/Display-120Hz_Ultra--Smooth-FF9500?style=for-the-badge&labelColor=0d1117)](#ultra-performance-subsystem)
[![Engineering Lead](https://img.shields.io/badge/TV_Lead-Nithyanantha_(Nyxcore)-9C27B0?style=for-the-badge&labelColor=0d1117)](#credits--authors)

<br/>

[**Highlights**](#-key-features) · [**Top Navigation Bar**](#-top-navigation-bar) · [**Flowing Lyrics**](#-cinematic-cover-flow--flowing-lyrics) · [**Live Canvas**](#-live-video-canvas--spatial-audio) · [**Settings & Themes**](#-apple-tv-settings--custom-themes) · [**Remote Guide**](#-remote-controls--key-bindings) · [**Credits**](#-credits--authors)

</div>

> [!NOTE]
> **BitChord TV** is an open-source client engineered exclusively for television screens and home theaters, based on the original BitChord by Kushagra Singh (`@kushagrasinghx`). It delivers bit-exact lossless audio, live motion video artwork, synchronized syllable lyrics, and 120Hz remote navigation.


---

## 🌟 Key Features

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                BITCHORD TV 1.0                              │
│                                                                             │
│  [ Listen Now ]   Browse   Videos   Radio   Library   Now Playing   🔍  ⚙️  │
└─────────────────────────────────────────────────────────────────────────────┘
```

- 🏎️ **120Hz / 60Hz Ultra-Smooth Engine**: Hardware-accelerated drawing with a strict **8.33ms frame budget** on 120Hz TV panels and zero CPU layout re-measurements.
- 🎵 **Apple Music Clean Navigation**: Top navigation with `Home`, `Library`, `Now Playing`, and Settings.
- 🎤 **Apple-Physics Spring Scrolling Lyrics**: Dynamic vertical glide with VSYNC progressive syllable highlighting, background vocals, and continuous blur/scale depth.
- 🎬 **Multi-Provider Live Video Canvas**: Seamless looping video covers resolved from Apple Music Editorial Video, Tidal Video Covers, Spotify Canvas, and Community Index.
- 🎧 **3D Spatial Audio Virtualizer**: Hardware-accelerated soundstage widening with 1000mB stereo expander.
- 🖤 **True OLED Pure Black & Pure White Modes**: Infinite contrast with 100% pixel shutoff on OLED displays, or high-contrast crisp Pure White light mode.
- 📱 **Fast 1-Tap Google Sign-In**: Login directly on TV via Web Dialog or tap on your phone to link your YouTube Music account automatically.
- 🌐 **Latest BitChord Features**: Pluggable source modules, bit-exact lossless streaming, gapless crossfade, Automix DJ transitions, innerTubeX resolver, local audio & USB storage, Discord Rich Presence, and Last.fm/ListenBrainz scrobbling.

---

## 🧭 Unified Pill Top Navigation Bar

BitChord TV 1.0 features a single floating frosted glass navigation pill centered at the top of the display:

$$\mathbf{[ \text{Listen Now} ]}\quad\text{Browse}\quad\text{Videos}\quad\text{Radio}\quad\text{Library}\quad\text{Now Playing}\quad\mathbf{🔍}\quad\quad\mathbf{⚙️}$$

| Tab | Destination & Content |
| :--- | :--- |
| **Listen Now** | Personalized home shelves, quick picks, recently played, and recommended albums. |
| **Browse** | Full catalogue exploration, charts, genre collections, and mood stations. |
| **Videos** | Music video hits, live concerts, visual tracks, and trending video charts. |
| **Radio** | Endless automated radio stations, continuous mix algorithms, and genre radio. |
| **Library** | Liked songs, saved albums, custom playlists, offline downloads, and local device files. |
| **Now Playing** | One-touch jump into the active full-screen Cover Flow player or empty state. |
| **🔍 Search** | Embedded inside the navbar pill with fast voice / keyboard catalogue search. |
| **⚙️ Settings** | Apple TV-style system preferences, audio engine, fonts, themes, and accounts. |

---

## 🎤 Cinematic Cover Flow & Flowing Lyrics

The TV player is designed for 10-foot living room immersion:

- **Apple-Physics Smooth Glide**: Transitioning between active lines uses spring interpolation (`dampingRatio = 0.85f, stiffness = 100f`), gliding up with continuous vertical translation.
- **VSYNC Progressive Syllable Sweep**: Syllables illuminate left-to-right in real time matching the singer's voice.
- **Dynamic Depth & Optical Blur**: Active row is scaled to `1.04x` with 100% white contrast; past lines dim to `0.35f` with optical blur, and upcoming lines stay readable at `0.45f`.
- **Secondary Vocal Support**: Parenthesized background vocals render in italicized accents beneath the lead line.
- **No Music Is Playing State**: Clean placeholder screen on launch with back navigation, AirPlay device pill (`BitChord TV`), and a 1-click *"Explore Music"* button.

---

## 🎬 Live Video Canvas & Spatial Audio

- **Silent Looping Video Canvas**: ExoPlayer `TextureView` decodes 1080p looping vertical canvas video textures behind artwork.
- **Multi-Provider Fallback Hierarchy**:
  1. *Apple Music Editorial Video Canvas*
  2. *Tidal High-Res Video Covers*
  3. *Community Index Canvas*
  4. *Spotify Canvas Video*
- **Spatial Audio Engine**: Android AudioTrack virtualizer expands stereo tracks into an immersive 3D surround sound field.

---

## ⚙️ Apple TV Settings & Custom Themes

```
┌──────────────────────────────────────┬──────────────────────────────────────┐
│  Settings                            │  Appearance & Themes                 │
│                                      │                                      │
│  Apple TV 1:1 High-Contrast Invert   │  [ Pure Black (OLED)              ]  │
│  When hovering on options, pills     │  [ Pure White (Light Mode)        ]  │
│  invert to pure solid white with     │  [ Dynamic Artwork Dark           ]  │
│  sharp pure black typography!        │  [ Midnight Blue                  ]  │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

- **High-Contrast Focus Inversion**: On D-pad hover, setting items invert into a **Solid Pure White pill (`#FFFFFF`)** with **Pure Black text (`#000000`)**, identical to Apple TV tvOS.
- **Visual Theme Palettes**:
  - 🎨 **Dynamic Artwork**: Luxury dark glass canvas with dynamic album accent luminescence.
  - 🌌 **Midnight**: Deep charcoal-navy canvas with sapphire and violet highlights.
  - 🖤 **Pure Black (OLED)**: True `#000000` pitch black with zero background draw calls for 100% pixel shutoff.
  - ☀️ **Pure White (Light Mode)**: High-contrast `#F2F2F7` / `#FFFFFF` frosted glass canvas with deep `#1C1C1E` pure black text.

---

## 🔤 Custom Typography Suite

BitChord TV 1.0 bundles genuine `.ttf` and `.otf` font families accessible instantly in Settings:

| Typeface | Classification | Aesthetic |
| :--- | :--- | :--- |
| **Apple SF Pro Display** | Sans-Serif | Signature iOS / macOS luxury typography |
| **Google Sans** | Geometric Sans | Modern, crisp Android & Pixel interface styling |
| **Arial Classic** | Universal Sans | Standard clean universal legibility |
| **Minecraft Pixel** | Monospace / 8-Bit | Retro arcade pixelated gaming aesthetic |

---

## 🔑 1-Tap Google Sign-In

1. **Direct TV Sign-In**: Sign in directly on your TV with Google's official Web Dialog (complete 2FA, passkeys, and account picker).
2. **Fast Mobile Web Pairing**: Scan the on-screen QR code to open the mobile web portal on your phone, tap *"Open YouTube Music on Phone"*, and link your account with automated session capture!

---

## 🎮 Remote Controls & Key Bindings

| Remote Key | Action |
| :--- | :--- |
| **D-Pad Left / Right / Up / Down** | Spatial focus traversal with active spring halo |
| **D-Pad Center / OK** | Select item / Play track / Toggle playback (tactile 0.95x compression) |
| **D-Pad Left / Right (on Seek Bar)** | Fine seek backward / forward by ±10 seconds |
| **Play / Pause Button** | Global playback toggle from any screen |
| **Fast-Forward / Next** | Skip to next track in queue |
| **Rewind / Previous** | Return to previous track or track start |
| **Back Button** | Smoothly zoom out of Now Playing -> return to catalogue -> exit |

---

## 🌐 TV Compatibility

| Platform | Verified Devices | Status |
| :--- | :--- | :--- |
| **Google TV** | Chromecast with Google TV (4K & HD), Sony Bravia XR, TCL Google TVs, Hisense ULED | **100% Verified** |
| **Android TV OS** | NVIDIA Shield TV / Pro, Xiaomi Mi Box S, Mecool, Philips Android TVs | **100% Verified** |
| **Amazon Fire TV** | Fire TV Stick 4K / Max, Fire TV Cube (3rd Gen), Omni Series QLED TVs | **Compatible** |

---

## 🛠️ Build Instructions

To build the production TV release APK:

```bash
# Clone the repository
git clone https://github.com/nimalanrao/bitchordTV.git
cd bitchordTV

# Compile and package production release APK
./gradlew assembleProdRelease

# The output APK is generated at:
# app/build/outputs/apk/prod/release/app-prod-release.apk
```

---

## 👨‍💻 Credits & Authors

- **BitChord Original Creator & Lead Developer**: **Kushagra Singh** ([@kushagrasinghx](https://github.com/kushagrasinghx))
- **TV Platform Lead & Architecture**: **Nithyanantha / Nimalanrao** ([@nimalanrao](https://github.com/nimalanrao))
- **Version**: `1.0.0` (Build `100`)
- **License**: GNU General Public License v3.0 (GPLv3)
