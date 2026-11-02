# Third-party native code

| Folder | Upstream | Version | License |
|---|---|---|---|
| `signalsmith-stretch/` | https://github.com/Signalsmith-Audio/signalsmith-stretch | 1.3.2 (`88061fd4`) | MIT (`LICENSE.txt`) |
| `signalsmith-linear/` | https://github.com/Signalsmith-Audio/linear | 0.6.6 | MIT (`LICENSE.txt`) |

Copied verbatim; the Automix loop mix (`native/loopmix/`) is the only user.
Signalsmith Stretch includes `signalsmith-linear/stft.h`, which resolves
against this folder.

## Fetched at build time

| Library | Upstream | Version | License | Used by |
|---|---|---|---|---|
| Oboe | https://github.com/google/oboe | 1.11.0 (SHA-256 pinned in `app/src/main/cpp/CMakeLists.txt`) | Apache-2.0 | `libbitchord_exclusive.so` (AAudio exclusive output) |
| libusb | https://github.com/libusb/libusb | 1.0.27 (SHA-256 pinned in `app/src/main/cpp/CMakeLists.txt`) | LGPL-2.1-or-later | `libbitchord_usb.so` (direct USB output) |

## Adapted code

The bit-perfect output (`app/src/main/cpp/bitperfect/`,
`app/src/main/java/com/music/bitchord/playback/audio/bitperfect/`) adapts
parts of [Mornye](https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet,
licensed LGPL-3.0-only. Each adapted file names its Mornye source and keeps
that license in its header. The direct USB driver's realtime arithmetic has
host tests in `app/src/main/cpp/bitperfect/tests/` (see the build line at the
top of the file); its descriptor and volume logic is tested from JUnit.
