<div align="center">

<br/>
<br/>

<img src="Logo.png" alt="BitChord app icon" width="200" />

# BitChord

### Unofficial third-party player for YouTube Music

<br/>

[![Latest release](https://img.shields.io/github/v/release/kushagrasinghx/BitChord?style=for-the-badge&labelColor=0d1117)](https://github.com/kushagrasinghx/BitChord/releases)
[![License](https://img.shields.io/github/license/kushagrasinghx/BitChord?style=for-the-badge&labelColor=0d1117)](https://github.com/kushagrasinghx/BitChord/blob/main/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/kushagrasinghx/BitChord/total?style=for-the-badge&labelColor=0d1117)](https://github.com/kushagrasinghx/BitChord/releases)

<br/>

[**Download**](#download) · [**Service file**](#service-file) · [**Features**](#features) · [**Contributing**](#contributing) · [**Support**](#support) · [**Disclaimer**](#disclaimer)

<br/>

<a href="https://trendshift.io/repositories/177639?utm_source=trendshift-badge&utm_medium=badge&utm_campaign=badge-trendshift-177639" target="_blank" rel="noopener noreferrer"><img src="https://trendshift.io/api/badge/trendshift/repositories/177639/daily?language=Kotlin" alt="kushagrasinghx%2FBitChord | Trendshift" width="250" height="55"/></a>
<a href="https://trendshift.io/repositories/177639?utm_source=trendshift-badge&utm_medium=badge&utm_campaign=badge-trendshift-177639" target="_blank" rel="noopener noreferrer"><img src="https://trendshift.io/api/badge/trendshift/repositories/177639/weekly?language=Kotlin" alt="kushagrasinghx%2FBitChord | Trendshift" width="250" height="55"/></a>

</div>

> [!IMPORTANT]
> BitChord is an unofficial, independent third-party player. It is not affiliated with, endorsed by, or connected to YouTube, YouTube Music, or Google in any way. YouTube, YouTube Music, and Google are trademarks of Google LLC.
> Use it at your own discretion and only in compliance with YouTube's Terms of Service, Google's Terms of Service, and the copyright laws that apply to you.

---

<div align="center">

<img src="Banner.png" alt="BitChord banner" width="100%" />

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- **Search, browse and play** content your YouTube Music account can access.
- **Hi-Res lossless audio** — FLAC/ALAC from a configured module source, with YouTube Music as fallback.
- **Gapless playback with true crossfade**, adjustable 0–12s.
- **Automix [Beta]** — DJ-style transitions with beat-matching and tempo-stretching.
- **Offline downloads** — keep tracks your account can access with embedded metadata.
- **Local music library** integration.
- **Background playback** via a proper foreground media session.
- **Apple-like lyrics animation** — credit to [binimum](https://github.com/binimum/am-lyrics).

#### Experience
- **Animated album canvas** — motion artwork on the now-playing screen.
- **Word-synced lyrics** — word/syllable-level highlighting from multiple sources.
- **Dynamic, artwork-driven theming** — Material palette extracted from album art.
- **Frosted-glass UI** — Telegram-style translucent bars via Haze, Material 3 theming.

    </td>
    <td width="50%" valign="top">

#### Connectivity & Accounts
- **Sign in with your Google account** for personalized content.
- **Discord Rich Presence** — in-app login, live track/artist/album and progress.
- **Scrobbling** to Last.fm and ListenBrainz.
- **Pluggable sources** — add, edit, test and health-check module sources.

#### Controls & Tweaks
- **Per-network audio quality** — separate quality ceilings for Wi-Fi and mobile data.
- **Playback speed control** (0.5×–2.0×) and **skip silence**.
- **Sleep timer** — fixed presets or "stop after this track".
- **System equalizer** integration.
- **Stats for nerds** — codec, bit depth, sample rate, and more on the now-playing screen.

    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="service-file"></a>Service file</h1>

BitChord ships **no service endpoints, client identities, or link hosts**. On a fresh install, streaming, search, sign-in, and link handling stay dormant until you import a **service file**: a JSON document holding the endpoints, web client, player clients in preference order, stream-host suffix, link hosts, and login URLs the app should use.

* **Settings → Service file → Import service file**, pick the file you downloaded. The row reports its fingerprint and client count; **Test connection** checks reachability; **Remove service file** returns the app to local-files mode.
* **Or import from a link:** paste any `https` URL you trust — a raw file in your own repo or gist. The app downloads it once, validates it exactly like a picked file, and never re-fetches it. No link is baked into the app and nothing is fetched automatically.
* The file is validated before anything is stored: `https` only, no credentials in URLs, no private or loopback hosts, strict size caps. Anything else is refused whole.
* The optional `catalogue` section configures the third-party lossy source (endpoint, call names, stream key). Without it that source reports itself unavailable; everything else works.
* The session cookie is sent only to hosts at or under the built-in auth guard suffixes, whatever the file claims — a hostile file can redirect anonymous traffic, but never harvest a login.
* The file lives outside this repository on purpose. Do not commit service files, endpoint URLs, client versions, or user agents to this repo, its docs, issues, or pull requests.
* Two deliberate exceptions stay in code: the manifest's link-routing hosts (so shared links can open the app — routing, not endpoints, with no ownership claim) and the auth guard suffixes (a safety valve restricting where the session cookie may be sent).

```jsonc
{
  "app": "bitchord-service",
  "configVersion": 1,
  "endpoints": {"musicBase": "https://…", "tubeBase": "https://…", "musicOrigin": "https://…", "tubeOrigin": "https://…"},
  "login": {"origin": "https://…", "loginUrl": "https://…", "cookieOrigins": ["…"]},
  "webClient": {"name": "…", "version": "…", "id": "…", "ua": "…",
    "stats": {"cplayer": "…", "cbr": "…", "cbrver": "…", "cos": "…", "cosver": "…"}},
  "playerClients": [{"name": "…", "version": "…", "id": "…", "ua": "…",
    "osName": "…", "osVersion": "…", "deviceMake": "…", "deviceModel": "…",
    "androidSdkVersion": "…", "origin": "https://…", "apiBase": "music|tube",
    "needsSignatureTimestamp": false}],
  "clientOrder": ["…"],
  "streamHostSuffix": "…",
  "links": {"hosts": ["…"], "shortHost": "…", "shareOrigin": "https://…"},
  "swDataUrl": "https://…",
  "catalogue": {
    "apiBase": "https://…",
    "searchCall": "…",
    "detailsCall": "…",
    "urlKey": "…",
    "userAgent": "…",
    "forwardedFor": "…",
    "cookie": "…"
  }
}
```

</div>

---

<div align="center">

<h1><a id="download"></a>Download</h1>

Grab the latest signed APK from the [Releases](https://github.com/kushagrasinghx/BitChord/releases) page. Sideloading requires enabling "Install unknown apps" for whichever app you download it with.

</div>

---

<div align="center">

<h1><a id="contributing"></a>Contributing</h1>

We welcome contributions to BitChord! Please review our [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) before submitting a Pull Request.

[**Contributing Guide**](CONTRIBUTING.md) · [**Code of Conduct**](CODE_OF_CONDUCT.md) · [**Maintainers**](MAINTAINERS.md) · [**Additional Docs**](ADDITIONAL.md)

Please do not submit PRs that add stream-decryption keys, signature-solving workarounds, credential-harvesting, service endpoint URLs, client versions, user agents, link hosts, sample service files, or features whose purpose is to defeat age, region, or entitlement restrictions. Such PRs will be closed without review.

</div>

---

<div align="center">

<h1><a id="support"></a>Support</h1>

BitChord is free and always will be — if it's earned a spot in your rotation, you can chip in here:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/kushagrasinghx)
[![PayPal](https://img.shields.io/badge/PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white)](https://paypal.me/kuxhagrasingh)

<br/>
<br/>
<img src="upi_support.jpg" alt="UPI Support" width="250" />

</div>

---

<div align="center">

<h1><a id="disclaimer"></a>Disclaimer & Legal Notice</h1>

BitChord is an independent, community-driven third-party audio player and client. It is **not** associated with, affiliated with, or endorsed by Google LLC, YouTube, YouTube Music, Deezer, Telegram, or any of their parent companies. All third-party names, marks, and logos remain the property of their respective owners and are used here only to describe compatibility.

* **No Media Hosting:** BitChord does not host, upload, or store copyrighted music files. It operates strictly as an interface to scan local device storage or stream media directly from public, public-facing, or user-authenticated APIs.
* **Your Account, Your Responsibility:** Sign-in uses your own Google/YouTube Music account via Google's own web login. BitChord never sees your password. You are entirely responsible for ensuring your usage complies with YouTube's Terms of Service, Google's Terms of Service, and your local copyright laws, including rules on age-restricted, region-restricted, paid, and Premium-only content.
* **No Circumvention Claims:** BitChord does not claim to bypass technological protection measures, and age-, region-, and entitlement-restricted content is treated as unavailable when the service reports it as such.
* **No Ad-Blocking Guarantee:** While BitChord focuses on providing a clean listening environment, it does not guarantee permanent bypasses or modifications to commercial third-party platform conditions.
* **Report Abuse:** See [DMCA.md](DMCA.md) for how to report copyright concerns, and [NOTICE](NOTICE) for trademark and attribution details.
* **Copyleft:** BitChord is free software under the GPLv3. The license does not let anyone forbid others from selling or redistributing copies, but any distribution must come with the Corresponding Source under the same license.

</div>

---

<div align="center">

<h1><a id="license"></a>License</h1>

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for details.

</div>
