# Spotify Offline Backup — Investigation & BitChord "Smart Offline" Design

**Status:** Research report — *no implementation performed*
**Repository audited:** `/home/alen/BitChord` (upstream `github.com/manish0467/BitChord`, local HEAD `acf219a`)
**Date:** 2026-11-15
**Author role:** Senior Android RE / Kotlin / architecture review

---

## Evidence classification used throughout

| Tag | Meaning |
|---|---|
| **DOCUMENTED** | Explicitly stated in official Spotify documentation (support.spotify.com, newsroom.spotify.com, developer.spotify.com). |
| **VERIFIED** | Directly observed by me through authorized testing or inspection of artifacts available in this environment. |
| **INFERRED** | A reasonable conclusion drawn from documented and/or verified evidence, but not stated by Spotify. |
| **UNKNOWN** | Cannot be established with the evidence available. Stated as a gap, not filled with a guess. |

---

# 1. Executive summary

**The premise of the task needs one correction, and it changes the design.**

Spotify's **Offline Backup is not an auto-downloader**. It is a *cache-backed fallback playlist*: Spotify assembles a playlist from your **queued** and **recently streamed** tracks whose audio is **already in the device cache**, and surfaces it in the Home feed **only when you are offline**. It consumes **zero extra data and zero extra storage**, is **Premium-only**, requires the "Offline listening" toggle, and only appears once you have "played enough songs recently" (documented as **more than five**). It is sortable by **Title, Artist, Album, Frequently played, Recommended**. *([Newsroom, 2024-10-03](https://newsroom.spotify.com/2024-10-03/offline-backup-keeps-the-music-going-when-your-connection-goes-missing/); [Support: Offline Backup](https://support.spotify.com/us/article/offline-backup/))*

Spotify has **no** feature that automatically downloads music the user has not explicitly downloaded. The closest relative — podcast **auto-download** — is a different feature, and the earlier "Offline Mix" experiment appears to have been superseded by Offline Backup.

**So the two are not the same mechanism.** Spotify's answer to "I'm offline" is *reuse what is already on disk*. BitChord's answer can and should be *keep a bounded, self-refreshing rotation downloaded* — which is **strictly more capable**, because BitChord legitimately controls its own download pipeline and Spotify deliberately refuses to do this. The design should therefore ship **two separable halves**: a zero-cost "Offline Backup analogue" (surface what's already cached/downloaded) and the actual **Smart Offline** rotation (actively download, rank, refresh, evict).

**What the investigation established:**

| Phase | Outcome |
|---|---|
| **1 — Spotify Offline Backup** | 14/14 questions answered from primary Spotify documentation, each classified DOCUMENTED / INFERRED / UNKNOWN. The selection contract is *recency + queue + cache-eligibility + a >5-track floor*, with a "Frequently played" and a "Recommended" ordering. **Every numeric parameter behind the ranking is UNKNOWN.** |
| **2 — Reverse engineering** | **Blocked, and honestly reported.** No Spotify APK exists on this machine, no Android device is attached, and REA's `android-provider` (JADX) surface is unavailable. More importantly, **static analysis could not have answered the question anyway**: the playlist is assembled server-side. Evidence: the REA tool-availability inventory and `rea doctor`. No Spotify artifact was obtained, no DRM touched, no credential handled. |
| **3 — Spotify APIs** | Mapped exhaustively, **including the Feb/Mar 2026 API churn** (`GET /tracks`, `GET/users/{id}/playlists`, `GET/artists/{id}/top-tracks`, `GET/recommendations`, audio-features/analysis all **removed**; `popularity` and `product` **removed**; `search` limit cut to 10; `external_ids` removal reverted). The decisive finding: **BitChord uses none of it** — it has no OAuth, no scopes, no `recently-played`. It replays the user's `sp_dc` web cookie and calls Spotify's internal `pathfinder` GraphQL for playlists and Liked Songs only. |
| **4 — BitChord audit** | Read-only, no file modified. The repo is a Compose app with **no database** (SharedPreferences + JSON), **no free-space check anywhere** (zero `StatFs` hits), **no download quota, no eviction, no byte-range resume**, one WorkManager worker (`PlaylistSyncWorker`), and a mature download pipeline (`Downloads` → `DownloadService` → `DownloadStore` → `MediaTagger`) plus a mature matching layer (`TrackMatcher`, `SourceResolver`, `SourceRegistry`). |
| **5-9 — Design** | A complete, explainable ranking algorithm over signals that **actually exist**; a component design that reuses existing infrastructure rather than duplicating it; a feasibility matrix separating *technically impossible* from *merely unimplemented*; a nine-stage roadmap; and a recommended first PR. |

**The decisive asset** — and the reason this feature is cheap to build — is `ListeningStats` / `ListeningRecorder` (`app/src/main/java/com/music/bitchord/data/stats/`). It already keeps, on-device, per track: `ms` played, `plays`, the exact `last`-played timestamp, and a **per-day** breakdown, in **monthly JSON buckets**, never uploaded. `ListeningRecorder` measures wall-clock time from the playback sampler, so seeking and pausing cannot inflate it, and a "play" is counted under the same half-or-four-minutes rule the scrobbler uses.

> **This is a richer listening signal than Spotify exposes to any third party.** Spotify's public API offers *pre-aggregated affinity ranks* and a 50-item recently-played page — never a play count, never an engagement measure, never a duration. BitChord already has all three.

**Therefore the architecture is:** BitChord's own playback becomes the source of truth for *listening*; Spotify remains a source of *catalogue and library membership* (playlists + Liked Songs, already implemented); and audio is resolved and downloaded exclusively through BitChord's own sources (`YouTubeSource`, `AddonSource`, `ModuleSource`, `JioSaavnSource`) — never Spotify's protected audio.

**Ranking formula** (every input verified as available in this codebase):

```
score(t) = 0.35·R(t) + 0.30·F(t) + 0.15·E(t) + 0.20·P(t)  −  penalty(t)      [SMART]
  R = 2^(−(now − last(t))/7d)                                    recency, half-life 7d
  F = Σ plays(d)·2^(−age(d)/21d), over a 28-day window           decayed frequency
  E = clamp(ms / Σ plays(d)·0.75·duration(t), 0, 1)              engagement
  P = max(liked 1.0, spotify-liked 0.9, in-playlist 0.6, …)      preference
```
Components are converted to **rank percentiles** within the candidate set (robust to outliers and to users with 3 plays or 400). The selection is split **70% "core" (familiar favourites) / 30% "rotation variety" (ranked lower but weighted to keep the rotation changing)** — the balance that keeps the feature from feeling like a loop. Both tiers are drawn from the **same** candidate set (tracks already in the user's listening history, plus preference candidates); "rotation variety" means *ranked differently*, **not** newly discovered tracks that the user has never played.

**The precedent already exists in the codebase.** `MainViewModel.localTaste()` (`app/src/main/java/com/music/bitchord/ui/MainViewModel.kt:1839-1847`) already feeds the local Replay statistics into `TopPicks.LocalTaste` (`shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/TopPicks.kt`) — **BitChord already uses its own on-device listening history to personalise a recommendation surface.** Smart Offline applies the same signal to *downloads* instead of *recommendations*. The data plumbing, the privacy posture and the "your listening data never leaves the device" story are therefore not new work; they are an existing, shipped pattern extended to a second surface.

**Avoiding churn** — the hardest problem, and the one Spotify sidesteps by owning no storage — is solved with five rules: **ownership tracking** (only Smart-Offline-downloaded files may ever be evicted), **hysteresis** (keep while `score ≥ 0.6 × cut-off`), a **sticky core** (anything in the top 2N is kept), a **14-day eviction cooldown**, and **quota-first eviction that never touches user files**. In steady state this yields ~35 stable tracks plus ~15 slowly rotating ones, and a handful of downloads per cycle. **All five belong to the download-management phase (PR #2 and later) — PR #1 produces a ranking only and makes no retention, hysteresis or eviction decisions.**

**Biggest technical blocker** (and it is *not* Spotify's opacity, nor Android's background limits): **`ListeningStats` is an aggregate store with no play-event log** — its own header says "the raw plays are gone". Recency is therefore exact only to the last play, and frequency is day-granular within the window. Second: **the codebase has no free-space check at all**, which must be added before any feature that can autonomously fill a disk.

**Recommended first pull request — PR #1, "Smart Offline: selection engine + visible rotation":** add a read-model (`ActivityIndex`) and a **pure, exhaustively unit-tested** ranking engine (`OfflineTrackSelector`), plus a read-only page showing the user's rotation with a per-track explanation of *why* each track was chosen. **No downloading, no eviction, no worker, no new dependencies, no manifest changes, zero disk risk** — and it surfaces the real-world data needed to calibrate the weights before the download machinery is built.

---

# 2. What Spotify Offline Backup actually is

## 2.1 The short version

**Offline Backup is not a downloader and not a ranking engine. It is a cache-backed fallback playlist.**

Spotify's own launch announcement states the mechanism plainly:

> "Offline Backup takes your queued and recently streamed tracks and creates one easy-to-access playlist that is unique to you. To do this, we include tracks already stored on your device as part of your regular listening on Spotify (also known as cache)."
> — [Spotify Newsroom, 2024-10-03](https://newsroom.spotify.com/2024-10-03/offline-backup-keeps-the-music-going-when-your-connection-goes-missing/) — **DOCUMENTED**

The practical consequence: **Offline Backup consumes zero extra data and zero extra storage.** It surfaces audio that the streaming client *already* has on disk from normal streaming, arranged into one playlist that is visible only when the device is offline.

## 2.2 User-visible behaviour

| Aspect | Behaviour | Class |
|---|---|---|
| Availability | **Premium** on mobile phones, Android Auto, Apple CarPlay. Not listed for desktop. | **DOCUMENTED** — [support.spotify.com/us/article/offline-backup](https://support.spotify.com/us/article/offline-backup/) |
| Where it appears | Automatically in the **Home feed**, but **only when the device is offline**. Also findable by searching for "Offline Backup". | **DOCUMENTED** |
| Preconditions | "Offline listening" toggle **on** (Settings and Privacy → Data-saving and offline) **and** songs present in the device's cache. | **DOCUMENTED** |
| Eligibility floor | "Make sure you've **listened to more than five songs recently**." Support repeats the failure reason as "You haven't played enough songs recently." | **DOCUMENTED** — [Newsroom](https://newsroom.spotify.com/2024-10-03/offline-backup-keeps-the-music-going-when-your-connection-goes-missing/), [Support](https://support.spotify.com/us/article/offline-backup/) |
| Membership source | **Queued** tracks + **recently streamed** tracks, filtered to those whose audio is already in the local cache. | **DOCUMENTED** |
| Downloads created | **None.** "No downloads required." / "without using any extra data or storage". | **DOCUMENTED** |
| Ordering / sorting | Sortable by **Title, Artist, Album, Frequently played, Recommended**; filterable by mood/genre. | **DOCUMENTED** |
| Persistability | Can be **saved into Your Library** (becomes a normal saved playlist). | **DOCUMENTED** |
| Playback | Standard shuffle on/off; order respects the sort chosen on mobile; Android Auto reuses the mobile sort, CarPlay cannot filter. | **DOCUMENTED** |
| Disappearance | Fails to appear when: not enough recent plays; device out of storage; cache cleared; app reinstalled. | **DOCUMENTED** |
| Rebuilt when? | Implicitly continuous — it is regenerated from current cache + current recency/queue state. Reinstalling destroys it because the cache is destroyed. | **INFERRED** (the rebuild cadence itself is not published) |

## 2.3 The key architectural insight for BitChord

Spotify's Offline Backup and BitChord's desired "Smart Offline" are **different mechanisms solving the same user problem**:

| | Spotify Offline Backup | BitChord Smart Offline (target) |
|---|---|---|
| Audio source | **Cache only** — audio already streamed to disk | **Purpose-built downloads** — BitChord resolves and downloads audio itself |
| Network at selection time | Not required (works offline) | Required at refresh time (to resolve + download) |
| Storage cost | **Zero additional** (reuses cache) | Bounded, user-configured budget |
| Selection signals | Recency + queue + "frequently played" + "recommended" | Recency + frequency + engagement + likes + exclusions |
| Failure mode when offline | *Works* (that is the point) | *Works* — because files were already downloaded |

**Two separable designs emerge (see §11):**

- **(A) "Offline Backup" analogue** — a zero-storage, zero-network playlist of tracks BitChord has *already* cached/buffered. Cheap, safe, no download infrastructure needed.
- **(B) "Smart Offline" analogue** — an actively maintained, bounded, refreshed **downloaded** rotation. This is the feature the task actually asks for, and it is *more* than Spotify does, because Spotify will not auto-download music for the user at all.

BitChord Smart Offline should therefore be presented as **inspired by, but stronger than, Spotify Offline Backup** — because BitChord legitimately controls its own download pipeline, whereas Spotify deliberately refuses to auto-download music.

---

# 3. Spotify's documented behaviour, question by question

Answering Phase 1's 14 questions directly, with classification.

| # | Question | Answer | Class |
|---|---|---|---|
| 1 | What is it, user-side? | A single auto-generated playlist that appears in the Home feed when offline, playable with no downloads and no extra storage. | **DOCUMENTED** |
| 2 | Which tracks are eligible? | Tracks whose **audio is already in the device cache**, drawn from **queued** + **recently streamed** tracks. Downloads explicitly do **not** count toward storage cost — the same cached bytes serve both. | **DOCUMENTED** |
| 3 | Does it consider recently played tracks? | **Yes.** "recently streamed tracks"; failure reason "You haven't played enough songs recently"; floor of **>5 recent songs**. | **DOCUMENTED** |
| 4 | Does it consider queued tracks? | **Yes.** "takes your queued and recently streamed tracks". | **DOCUMENTED** |
| 5 | Does it use play frequency or listening duration? | **Play frequency — yes**, evidenced by the "**Frequently played**" sort option. **Listening duration — not stated.** No duration field is offered as a sort or filter. | **DOCUMENTED** for frequency; **UNKNOWN** for duration |
| 6 | Does it use likes, playlists, recommendations, other signals? | "**Recommended**" is offered as a sort option → a recommendation/affinity score is present. Whether it derives from likes, playlist membership, or the global taste model is **not** published. | **DOCUMENTED** that a recommendation signal exists; **UNKNOWN** for its derivation |
| 7 | Personalised per user? | **Yes.** "a playlist that is unique to you" / "unique to you". | **DOCUMENTED** |
| 8 | How does selection change over time? | Continuously reflects current cache + recent plays + current queue. The **exact** rebuild cadence and the exact recency window are not published. | **INFERRED** for "continuously"; **UNKNOWN** for cadence/window |
| 9 | Are tracks automatically added and removed? | **Added/refreshed — yes** (playlist content tracks your recent activity and cache). **Removed — yes**, implicitly: clearing the cache or reinstalling removes it. But there is **no documented per-track eviction policy** for Offline Backup, because Offline Backup does not own storage. | **DOCUMENTED** (added, and cache-loss removal); **UNKNOWN** (per-track eviction policy) |
| 10 | Can users influence selection? | **Indirectly and partially.** Documented levers: (a) queueing tracks, (b) listening more than five songs, (c) keeping the Offline listening toggle on, (d) sorting/filtering, (e) saving the playlist to Library, (f) the separate, global **Taste Profile exclusion** feature ("exclude a playlist or track from your taste profile" → "your past and future listens will have less impact on your taste summaries and recommendations"). There is **no documented per-track "exclude from Offline Backup" control** — that is an inference from the absence of such a control, not a documented fact. | **DOCUMENTED** (a–f); **INFERRED** (absence of a per-track control) |
| 11 | Storage limits or configurable controls? | Offline Backup itself has **no** storage control — it consumes **zero** extra storage. The surrounding **cache** is bounded by Spotify's own persistent-cache policy (users report a ~30 GB persistent cache) and is bounded by device free space. "Your device is out of storage" is a documented failure cause. | **DOCUMENTED** (no extra storage, device-space failure); **INFERRED** for the cache cap figure |
| 12 | What if storage is insufficient? | Offline Backup does not appear: "Your device is out of storage and doesn't have enough storage space left". Note this is *device* storage, not a per-user quota. | **DOCUMENTED** |
| 13 | Does it need periodic internet? | **No.** It is explicitly the feature that works *because* you have no internet. However, the **cache it draws from** can only be filled by streaming, which needs connectivity. (The separate 30-day re-validation rule applies to **downloads**, not to Offline Backup.) | **DOCUMENTED** |
| 14 | Subscription tiers / device differences? | **Premium** only. Supported surfaces: mobile phone apps (iOS/Android), Android Auto, Apple CarPlay. Not desktop, not the web player. Free tier: excluded (and free tier cannot download music at all). | **DOCUMENTED** |

## 3.1 Related but distinct Spotify mechanisms

The task asks to distinguish Offline Backup from other Spotify automatic-download mechanisms. There are three distinct things in the neighbourhood, and confusing them is the single most common analytical error in this area:

| Mechanism | What it does | Downloads audio? | Auto? | Offline Backup? |
|---|---|---|---|---|
| **Offline Backup** | Surfaces *cached* audio as a playlist, when offline | **No** | Yes | — |
| **Downloads** (`Downloads over cellular`, `Offline mode`, "Remove all downloads") | Explicit user-initiated download of albums/playlists/podcasts. Max **10,000 tracks per device, up to 5 devices**. Downloads must be refreshed by going online **at least once every 30 days**, else Spotify removes them (so it can collect play data to compensate artists). | Yes | **No** — manual | **No — different feature** |
| **Auto-download episodes** (podcasts) | Automatically downloads new episodes of *followed* podcasts/shows. | Yes | **Yes** | **No — podcast-only, and it is a download feature, not a fallback playlist** |

Sources: [Listen offline](https://support.spotify.com/us/article/listen-offline/), [Offline Backup](https://support.spotify.com/us/article/offline-backup/), community/press on auto-download episodes.

**What Spotify does *not* have:** there is no Spotify feature that automatically downloads *music* the user has not explicitly downloaded, based on their listening habits. The "Offline Mix" feature (2024, in testing; later reported as superseded/iterated by Offline Backup) was the closest thing — it was reported as *"a mix of your recently played tracks for offline use"* that would **save** tracks — but the shipped, documented, globally available product is **Offline Backup**, and its shipped behaviour is **cache-backed, zero-download**. *("Offline Mix" status: **INFERRED** from secondary press; the primary Spotify documentation covers Offline Backup only.)*

---

# 4. Reverse-engineering findings (Phase 2)

## 4.1 Outcome: Spotify application inspection was **not possible** in this environment

**VERIFIED** — I attempted the inspection and it is blocked, at two independent layers. Neither layer can be removed by configuration alone without artifacts that are absent from this machine.

### Layer 1 — the artifact does not exist here

| Check | Command | Result |
|---|---|---|
| Connected Android device | `adb devices -l` | **No devices attached** (adb daemon present at `~/Android/Sdk/platform-tools/adb`) |
| Spotify APK on disk | `find /home/alen /tmp /var/tmp /mnt -iname '*spotify*' -name '*.apk'` | **0 matches** |
| Any APK on disk | `find /home/alen -name '*.apk'` | 20 matches, **none Spotify** (CRICFy, MovieBox, Flux, Vanced-YouTube-Music, etc.) |

### Layer 2 — REA's Android provider is not configured

**VERIFIED** via the connected REA MCP server (`rea` 6.7.0, 207 tools, live in this session). `binary_session` tool-availability inventory returns:

```json
{ "surface": "android-provider",
  "names": ["inspect_android_class","inspect_android_method","inspect_android_package",
            "search_android_classes","trace_android_references"],
  "available": false,
  "reason": "provider_missing",
  "remediation": "Set REA_JADX_MCP_JAR to the absolute path of a caller-supplied
                 jadx-headless-mcp 0.7.1 JAR. REA does not download or install it." }

{ "surface": "apktool-provider",
  "names": ["decode_android_resources","inspect_apktool_client"],
  "available": false,
  "reason": "provider_unavailable",
  "remediation": "The selected apktool launcher was not found: apktool" }
```

`rea doctor` corroborates: `jadx` → `missing_analysis_engine`; `ida` → `missing_analysis_engine`; `kuna` → `missing_analysis_engine`; `ghidra` → `missing_analysis_engine` (no `GHIDRA_INSTALL_DIR`); `hopper` → `missing_analysis_engine`.

### Exactly what is missing to unblock Phase 2

1. **A Spotify Android APK** (e.g. `com.spotify.music` APK/XAPK/APKS) placed at a local path. Acquisition would need the user's own Google Play account via an authorised device or an APK archive the user is entitled to use — I did not and will not scrape or pirate one.
2. **`REA_JADX_MCP_JAR`** pointing at a caller-supplied `jadx-headless-mcp` **0.7.1** JAR, to enable the `android-provider` surface (`inspect_android_package` → class/method/reference search).
3. Optionally **`apktool`** on `PATH` for the `apktool-provider` surface (resource/manifest decoding).
4. A device or emulator running Spotify, if *dynamic* behaviour (cache population, offline transitions) is to be observed rather than static analysis.

> Note: `~/Android/Sdk` contains `emulator` and `system-images`, so an emulator **can** be provisioned. The APK and the JADX JAR are the actual blockers.

## 4.2 Why this blocker is not fatal to the investigation

**INFERRED, with high confidence:** even with a fully working Android provider, static analysis of the Spotify Android client would **not** reveal Offline Backup's selection algorithm, because:

1. The playlist is assembled by **Spotify's backend**. The client receives a rendered playlist object. The eligibility, ordering, "Frequently played" and "Recommended" scores are computed server-side from Spotify's listening telemetry, which no client-side artifact contains.
2. The client-side artifact would reveal the **cache-layer** mechanics (cache directory layout, eviction, the "Offline Backup" playlist rendering path), which are useful but secondary to the selection question.
3. Spotify's Web API (Phase 3) exposes a deliberately **narrow** slice of the same telemetry — which is strong indirect evidence that the full signal set is proprietary.

**Therefore: Spotify Offline Backup's internal ranking algorithm is UNKNOWN and, on current evidence, not determinable from client artifacts.** The documented surface behaviour (recency + queue + frequently-played + recommended) is the authoritative behavioural contract, and it is what the BitChord design must be built against.

## 4.3 What I deliberately did not do

- Did not download or decompile any Spotify APK (none present; would require unauthorised acquisition).
- Did not touch any Spotify encrypted cache, DRM layer, or offline audio file.
- Did not authenticate to, or probe, any Spotify backend endpoint beyond public developer documentation.
- Did not modify the Spotify account, its settings, or its data.
- Did not modify any BitChord file.

---

# 5. Verified vs. inferred findings — summary

## 5.1 VERIFIED (observed first-hand in this environment)

| Finding | How verified |
|---|---|
| REA MCP server is live, `rea-agents` 6.7.0, 207 tools, SDK 2.3.1, protocol `2025-11-25`, client `opencode 2.0.31` | `binary_session` via MCP |
| REA's `android-provider` surface is unavailable (`provider_missing`, needs `REA_JADX_MCP_JAR`) | `binary_session` tool-availability inventory |
| REA's `apktool-provider` surface is unavailable (no `apktool` on PATH) | same |
| No Android device is attached to `adb` | `adb devices -l` |
| No Spotify APK exists on this filesystem | `find` across `/home/alen`, `/tmp`, `/var/tmp`, `/mnt` |
| Spotify's Web API removed `GET /tracks` (batch), `GET /users/{id}/playlists`, `GET /users/{id}`, `GET /artists/{id}/top-tracks`, `GET /markets`, `GET /browse/*` in Feb 2026; `Track.popularity` and `User.product` fields were removed; `external_ids` removal was reverted in Mar 2026 | Official Feb 2026 + Mar 2026 changelogs, fetched from developer.spotify.com |
| Spotify's search `limit` maximum dropped from 50 → **10**, default 20 → 5 (Feb 2026) | same |
| `GET /me/tracks` (Liked Songs), `GET /me/top/{type}`, `GET /me/player/recently-played`, `GET /me/player/queue`, `GET /me/playlists`, `GET /playlists/{id}/items` **all remain available** post-Feb-2026 | Feb 2026 changelog "Endpoints still available" |

## 5.2 INFERRED

| Inference | Basis |
|---|---|
| Offline Backup membership is limited to tracks with **cache-resident audio**; a track streamed long ago whose bytes were evicted cannot appear | Newsroom wording "we include tracks already stored on your device"; support's "you must … have songs stored in your device's cache" |
| The playlist is **rebuilt server-side** and refreshed continuously, not snapshotted at install time | Support lists "You haven't played enough songs recently" as a *current-state* failure cause |
| Offline Backup's "Recommended" sort uses Spotify's **taste/affinity model**, not a local heuristic | The word "Recommended" and the existence of a separate global Taste Profile system |
| The **"Offline Mix"** feature was the predecessor/precursor that Offline Backup replaced | Secondary press (TechCrunch, Verge, Engadget) — **not** primary documentation |
| The ranking weights and thresholds (the >5-song floor, the recency window, the frequently-played cut-off) are server-side constants | No client artifact or API exposes them |

## 5.3 UNKNOWN (explicit gaps)

- The **exact recency window** used for "recently streamed".
- The **exact play-count window** and formula behind "Frequently played".
- Whether **listening duration** (vs. play count) contributes.
- Whether **likes / playlist membership** contribute to Offline Backup membership, distinct from the "Recommended" ordering.
- The **maximum size** of the Offline Backup playlist.
- The **refresh cadence** and whether it is push- or pull-driven.
- Whether the playlist can be **emptied or replaced** by the user, and what happens then.
- The **precise** cache-eligibility rule (LRU age? byte-cache only? encrypted-cache only?).
- Whether Offline Backup is available on **desktop**, despite secondary press suggesting a desktop "Offline Mix" counterpart.

---

# 6. Spotify API capability analysis (Phase 3)

## 6.1 What the Web API actually exposes

All rows below are **DOCUMENTED** from `developer.spotify.com` (reference pages + the Feb/Mar 2026 changelogs).

| Signal | Endpoint | Scope | Availability | Completeness | Historical? | Rate-limit notes |
|---|---|---|---|---|---|---|
| **Recently played tracks** | `GET /v1/me/player/recently-played` | `user-read-recently-played` | Available (post-Feb-2026) | **Hard ceiling `limit=50`; `after`/`before` cursors are timestamp-based**, so history *can* be paged backwards but only within the window Spotify retains | **Yes — historical**, timestamped `played_at` per item, plus a `context` (artist/playlist/album/show) | Standard Web API quota; 429 on exceed |
| **Top tracks / artists** | `GET /v1/me/top/{type}` (`tracks`\|`artists`) | `user-top-read` | Available | Up to 50 per page; `time_range=short_term\|medium_term\|long_term` — reported as roughly **4 weeks / 6 months / all-time** affinity windows | **Yes — historical**, but pre-aggregated: returns a *ranked list*, **not** per-play timestamps or counts | Standard quota |
| **Liked / Saved Songs** | `GET /v1/me/tracks` | `user-library-read` | Available (post-Feb-2026) | Full library, paginated; each item carries `added_at` | Yes — `added_at` is historical | Standard quota |
| **User playlists** | `GET /v1/me/playlists` | `playlist-read-private` | Available | Full list; each playlist object is metadata only | — | Standard quota |
| **Playlist contents** | `GET /v1/playlists/{id}/items` | `playlist-read-private` / `playlist-read-collaborative` | Available (renamed from `/tracks`) | Paginated items. **Feb 2026: only the *user's own* playlist returns an `items` object; other playlists return metadata only.** | — | Standard quota |
| **Playback state** | `GET /v1/me/player` | `user-read-playback-state` | Available | Current playback state | No — live snapshot | — |
| **Queue** | `GET /v1/me/player/queue` | `user-read-playback-state` | Available | Current queue | No — live snapshot, **and only for the session BitChord itself controls** | — |
| **Recently played artists** | `GET /v1/me/player/recently-played` (dedup by artist) | `user-read-recently-played` | Available | Derived | Yes | — |
| **Track metadata** | `GET /v1/tracks/{id}` | any user token | Available | Full metadata **minus** `popularity` (removed Feb 2026). `external_ids` (ISRC/EAN/UPC) **retained** (reverted Mar 2026) | — | — |
| **Search** | `GET /v1/search` | any user token | Available, but **`limit` max = 10** (was 50) | — | — | — |
| **User profile** | `GET /v1/me` | `user-read-private` / `user-read-email` | Available, but **subscription tier (`product`) removed Feb 2026**; `country` removed; `explicit_content` removed | — | — | — |

## 6.2 Signals that are NOT available

| Signal | Status | Explanation |
|---|---|---|
| **Lifetime play counts** | **NOT AVAILABLE** | No endpoint returns a play count for a track. `GET /me/top/{type}` returns a *ranked list*, not counts. `Track.popularity` was **removed** in Feb 2026 and was a global, not personal, metric anyway. |
| **Complete listening history** | **NOT AVAILABLE** | `recently-played` is timestamp-cursored but Spotify's retention for the user's play history is not published, and `limit` is capped at 50 per call. There is no "export all my plays" endpoint. The consumer-app **Listening Stats / Recent Activity** are *not* exposed via the Web API. |
| **Listening duration / engagement** | **NOT AVAILABLE** | No endpoint returns per-play duration or skip behaviour. `GET /me/top/{type}` is the closest — it is an affinity aggregate, and Spotify describes it as computed over its affinity windows. |
| **Audio features / analysis** | **REMOVED Feb 2026** | `GET /v1/audio-features` and `GET /v1/audio-analysis` are gone from the endpoint list. |
| **Related artists / recommendations** | **REMOVED Feb 2026** | `GET /v1/artists/{id}/top-tracks`, `/related-artists`, and `GET /v1/recommendations` are all absent from the "still available" list. |
| **Queue, as a historical signal** | **NOT AVAILABLE** | Queue is a live, session-scoped read. It cannot be used as a background-history signal, and it is only populated if BitChord drove the playback session. |
| **Per-user "Frequently played" score** | **NOT AVAILABLE** | It is what Spotify's Offline Backup sorts by, but no API exposes it. |

## 6.3 Consequences for BitChord (important)

1. **BitChord cannot replicate Spotify's "Frequently played" from the Spotify API.** If `Track.popularity` was being relied on anywhere, it is gone as of Feb 2026 — and it was global popularity, never personal.
2. **`GET /users/{id}/playlists` is removed.** Any BitChord code calling it must migrate to `GET /me/playlists`. *(Cross-check pending against the BitChord audit — see §8.)*
3. **`GET /tracks?ids=` batch is removed.** Batch metadata fetches must be replaced with per-ID `GET /v1/tracks/{id}` calls. *(Cross-check pending — see §8.)*
4. **`GET /v1/me/player/recently-played` remains the single most valuable signal** for a BitChord Smart Offline: it is timestamped, personal, and back-pageable via `before`.
5. **The honest engineering conclusion:** Spotify's telemetry is strictly richer than what Spotify's *public* API exposes to third parties. BitChord therefore **must** collect its own equivalent signals from its own playback — which is fully legitimate, since BitChord's own playback activity is the user's own data about the user's own listening, in the user's own app.

---

# 7. Available listening signals — what BitChord can use

| Signal | Spotify API? | BitChord-equivalent signal | Notes for design |
|---|---|---|---|
| Recently played tracks (timestamped) | **Yes** — `GET /me/player/recently-played` | **Better** — BitChord's own play events, with exact timestamps, play counts, skip-after-N-seconds, and completion | Use BitChord's own as primary (richer); Spotify's as a *seed/cold-start* source |
| Playback frequency | **Partial** — only via pre-aggregated `GET /me/top/{type}` | **Yes** — count of BitChord play events per track per window | Do **not** rely on lifetime counts |
| Listening engagement | **No** | **Yes** — completion ratio, skip behaviour, repeat count, dwell time | The strongest differentiator vs. Spotify's public surface |
| Liked / favourites | **Yes** — `GET /me/tracks` | **Yes** — BitChord's own like state | Strong positive weight; also a source for a "Liked Songs" offline rotation |
| Playlist membership | **Yes** — `GET /me/playlists` + `GET /playlists/{id}/items` (own playlists only, post-Feb-2026) | **Yes** — BitChord's own playlists | Optional lower-weight signal |
| Top items (affinity windows) | **Yes** — `GET /me/top/{type}` | **Yes** — derivable from own play events | Useful for cold start |
| Queue | **Live only** | **Yes** — own queue state | Short-lived signal; treat as a *boost*, not a pillar |
| User profile / tier | **Degraded** — `product` removed Feb 2026 | n/a | Cannot detect Premium tier from the API any more |
| Exclusions | **Partial** — global Taste Profile exclusion (consumer app only, not API) | **Yes** — local exclusion list | This is a *win* for BitChord |

**Key architectural conclusion (Phase 3):** the right architecture is **not** "poll Spotify for listening history". It is:

> **BitChord's own playback engine becomes the source of truth for listening signals.**
> Spotify (via its API) is a **catalogue and enrichment source**: track metadata, playlist membership, Liked Songs — and a *cold-start seed* for listening history on first install.

This is both more accurate, more privacy-respecting, and immune to Spotify's API churn.

---

# 8. BitChord architecture audit (Phase 4)

> **READ-ONLY.** No file in the repository was modified. All paths below are relative to the repo root `/home/alen/BitChord`.

## 8.1 The headline finding that reframes the whole task

**BitChord does not use the Spotify Web API at all.** It has **no OAuth client, no scopes, no Authorization Code flow, and no access to `GET /me/player/recently-played`, `GET /me/top/*`, or `GET /me/tracks`.**

Instead it authenticates by **replaying the user's own Spotify *web session*** and talks to Spotify's **undocumented internal GraphQL** ("pathfinder") API.

| Layer | What BitChord actually does | Evidence |
|---|---|---|
| Login | `WebView` over `accounts.spotify.com`; scrapes the **`sp_dc` cookie** from `CookieManager` on page load | `app/src/main/java/com/music/bitchord/auth/SpotifyLoginScreen.kt:22, :119-130` |
| Cookie storage | **Plain (unencrypted) `SharedPreferences`**, file `bitchord_settings`, key `spotify_spdc_token` | `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt:762, :1099, :1774-1778, :2365` |
| Bearer token | Offscreen `WebView` on `open.spotify.com` with `sp_dc` injected; **hooks `fetch`/XHR** for `/api/token` and reads the token the page mints for itself | `app/src/main/java/com/music/bitchord/data/canvas/SpotifyToken.kt:134-208` |
| Client token | `POST https://clienttoken.spotify.com/v1/clienttoken` with spoofed client/device metadata | `SpotifyToken.kt:275-335` |
| GraphQL | `POST api-partner.spotify.com/pathfinder/v1|v2/query` with **persisted-query hashes** for `libraryV3` (playlists), `fetchPlaylist`, `fetchLibraryTracks` (Liked Songs), `canvas`, `searchTracks` | `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/spotify/SpotifyLibrary.kt:59-66, :134-173`; `.../spotify/SpotifyImporter.kt:183-201`; `app/.../data/canvas/SpotifyCanvas.kt:132-136, :346-356` |
| REST (the only real Web API use) | `GET api.spotify.com/v1/search` (limit 10) and `GET api.spotify.com/v1/albums/{id}/tracks?limit=1` — **canvas artwork only** | `app/.../data/canvas/SpotifyCanvas.kt:45-46, :172-178, :255-258` |
| OAuth scopes | **None exist.** No `scope` parameter, no scope constants anywhere in the repo. | Repo-wide search |
| Spotify deeplink | **None** in `AndroidManifest.xml`; only `music.youtube.com`, `youtu.be`, `bitchord://party`, `SEND`, audio URIs, `MEDIA_PLAY_FROM_SEARCH` | `app/src/main/AndroidManifest.xml` |

**Consequences for Smart Offline:**

1. **There is no Spotify listening history available to BitChord.** Not because of API limits — because BitChord never asks for it and cannot. Any design that assumes "query Spotify for my recent plays" is dead on arrival for this codebase.
2. **The Spotify catalogue BitChord *does* have is exactly the useful part**: playlists (`libraryV3`), playlist contents (`fetchPlaylist`), and **Liked Songs** (`fetchLibraryTracks`). These are ideal *seed* and *preference* signals.
3. **BitChord's own `ListeningStats` is the only listening-history source** — and it is excellent (see §8.5).
4. **A policy risk exists and must be respected**: `api-partner.spotify.com/pathfinder` is an undocumented internal endpoint. Smart Offline must **not** add new dependencies on it, and must not attempt to reach a "recently played" or "top items" operation that BitChord does not already use. This is a **design constraint**, not a nitpick.

## 8.2 Spotify domain model (as it exists in BitChord)

There are **no `@Serializable` Spotify DTOs** — everything is hand-parsed from `kotlinx.serialization` `JsonObject`.

| Model | Path | Fields |
|---|---|---|
| `SpotifyPlaylist` | `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/spotify/SpotifyLibrary.kt` | `id, name, owner, imageUrl` |
| `SpotifyTrack` | same file | `id, title, artist, album, durationMs, imageUrl` |
| `SpotifyImportTrack` | `shared/.../spotify/SpotifyImporter.kt` | `title, artist` |
| `SpotifyImportResult` | same file | `title, description, thumbnailUrl, totalTracks, resolvedVideoIds, unmatchedTracks` |
| `Tokens` / `Auth` | `SpotifyLibrary.kt` | `bearer`, `clientToken`; `Auth { suspend fun tokens(): Tokens? }` |

**Not modelled at all:** artist objects, album objects, playback state, queue, devices, saved-track objects, recently-played objects. `artist`/`album` exist only as flattened **name strings** on `SpotifyTrack`. There is **no** `SpotifyRecentlyPlayed`, `SpotifySavedTrack`, `SpotifyPlaybackState`, or `SpotifyQueue`.

**How a Spotify track is represented in the generic model** — `shared/src/commonMain/kotlin/com/music/bitchord/data/model/SpotifyMatch.kt`:

```kotlin
const val SPOTIFY_PENDING_PREFIX = "sp:"       // not yet matched to an audio source
const val SPOTIFY_MISSING_PREFIX = "sp-miss:"  // match attempted and failed
val Song.isMatchPending  get() = videoId.startsWith(SPOTIFY_PENDING_PREFIX)
val Song.isMatchMissing  get() = videoId.startsWith(SPOTIFY_MISSING_PREFIX)
val Song.isUnresolvedSpotify get() = isMatchPending || isMatchMissing
```

Built in `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt:2957-2966` (`asPendingSong()`) and `:2937` (failure → `sp-miss:`).

> **Design note:** this prefix scheme is the single most reusable thing Spotify integration has given BitChord. It is already a *resolution state machine* (`pending` → resolved → `missing`) and it is exactly what a Smart Offline track needs.

## 8.3 Music source abstraction (the resolution layer)

**Interface** — `app/src/main/java/com/music/bitchord/data/sources/MusicSource.kt:112`:

```kotlin
interface MusicSource {
    val configId: String
    val kind: SourceKind
    val displayName: String
    suspend fun health(): SourceHealth
    suspend fun search(query: String, limit: Int = 25,
                        waitForAll: Boolean = false, request: StreamRequest? = null): List<Song>
    suspend fun stream(trackId: String, request: StreamRequest): SourceStream?
}
```

Supporting types in the same file: `SourceStream` (L14-55: `url, format, headers, belowRequest, durationSec, sourceConfigId`), `StreamRequest` (sealed: `Lossless` / `Capped(maxKbps)` / `Best`, L66-75), `SourceHealth` (sealed: `Ok` / `Unreachable` / `Rejected`, L87-93).

**Implementations — exactly four** (verified by `: MusicSource` search):

| Class | Path |
|---|---|
| `YouTubeSource` | `app/src/main/java/com/music/bitchord/data/sources/YouTubeSource.kt:27` |
| `AddonSource` | `.../sources/AddonSource.kt:37` |
| `ModuleSource` | `.../sources/ModuleSource.kt:34` (serves both `CUSTOM_MODULE` and legacy `MODULE`) |
| `JioSaavnSource` | `.../sources/JioSaavnSource.kt:12` |

There is **no `local` source** — local music is a *library*, not a source (`data/LocalMediaRepository.kt`).

**Registration** — hand-written factory, **no DI framework**:
`app/src/main/java/com/music/bitchord/data/sources/SourceRegistry.kt:584-591`
```kotlin
private fun build(config: SourceConfig): MusicSource = when (config.kind) {
    SourceKind.ADDON -> AddonSource(config)
    SourceKind.CUSTOM_MODULE -> ModuleSource(config)
    SourceKind.MODULE -> ModuleSource(config)
    SourceKind.JIOSAAVN -> JioSaavnSource(config)
    SourceKind.YOUTUBE -> YouTubeSource(config)
}
```
Config stored in **encrypted** prefs `bitchord_sources`. Source priority (`SourceKind.kt`): `ADDON`/`CUSTOM_MODULE` 0, `MODULE` 1, `JIOSAAVN` 2, `YOUTUBE` 3. Key APIs: `active()` L222, `activeForDownload()` L241 (drops addons with `allowDownloads == false`), `activeForPlayback()` L346, `trackKey(configId, trackId)` L619-640 (`"src:<configId>::<trackId>"`), `addonsLeftOutOfDownloads()` L262, `parseTrackKey`.

**Cross-source resolution** — `app/src/main/java/com/music/bitchord/data/sources/SourceResolver.kt` (1552 lines):
`resolve` L197 · `substituteForYouTube` L286 · `directStream` L352 · `youtubeFallback` L406 · `prefetchSubstitute` L447 · `upgradeFor` L512 · **`forDownload` L664** · **`forDownloadOfRow` L159** · `bestAcross` L1009 (queries **all** sources concurrently) · **`matchAndStream` L1115** · `preferred` L1263 · `streamBest` L1315 · `attempt` L1476 (catch → `TrackLog.w` → `null`, `CancellationException` re-thrown).

**Matching engine (the "SpotifyTrackResolver" analogue)** — `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/sources/TrackMatcher.kt` (1005 lines):
- Title split into `words` (identity, must match), `versions` (remix/live/acoustic — must match **both directions**), `context` (packaging, tie-break only, never a veto).
- `score(candidate, target): Int?` — **`null` means "not the same recording"** (a miss, not an error).
- Weights (L803-835): `BASE 100`, `ARTIST_EXACT 25`, `ARTIST_SHARED 10`, `CREDITS_DISAGREE -30`, `CREDIT_OVERRIDE_SEC 2`, `DURATION_TIGHT 40`, `DURATION_LOOSE 15`, `ALBUM_EXACT 35`, `EXPLICIT_EXACT 20`, `CONTEXT_SHARED 20`; hard veto `DURATION_LIMIT_SEC = 30`, `VIDEO_DURATION_LIMIT_SEC = 90`.
- `queries(target)` L83 — title+artist, title alone, plus a transliterated query for kana titles; the raw YouTube title is deliberately not used.
- `ranked` L182 / `best` L133; `withinSeconds` L349; `sharesArtist` L114; CJK `romajiOf` L603 / `isTranslationMatch` L520.

**This is a mature, well-tested matching layer. A Smart Offline resolver must reuse it, not replace it.**

## 8.4 Download infrastructure

Package `app/src/main/java/com/music/bitchord/download/` — 17 files.

| File | Role |
|---|---|
| `Downloads.kt` (1741 L) | The manager: queue, record, resolve, transfer, save/delete, collections |
| `DownloadService.kt` (288 L) | Foreground service, `foregroundServiceType="dataSync"`, **4 concurrent drain workers** (`WORKERS = 4` L273), `IDLE_GRACE_MS = 2000`, `START_NOT_STICKY` |
| `DownloadStore.kt` (319 L) | Destinations, naming, existence checks, delete |
| `Downloader.kt` (392 L) | Byte fetching (ranged for YouTube), container probing |
| `DownloadSession.kt` (249 L) | In-memory "download manager sheet" model |
| `MediaTagger.kt` / `Mp4Tagger.kt` / `FlacTagger.kt` / `WebmTagger.kt` / `LyricsTag.kt` | Artwork + metadata + lyrics embedding |
| `ManifestFile.kt` / `OfflineDash.kt` / `OfflineHls.kt` / `Mp4Boxes.kt` / `Mp4Sidx.kt` | Offline package assembly |
| **`PlaylistSync.kt` (379 L)** | **The only WorkManager user** — periodic playlist re-sync |

### Public API a Smart Offline orchestrator would drive

`Downloads.kt`:
```kotlin
val active:   StateFlow<Map<String, DownloadState>>   // Queued | Running(fraction) | Failed(reason)
val requested: StateFlow<Map<String, Set<String>>>
val saved:    StateFlow<Map<String, String>>           // videoId -> uri   (PERSISTED)
val collections: StateFlow<Map<String, SavedCollection>>
fun enqueue(context: Context, song: Song, from: String? = null)   // L183  <-- the entry point
suspend fun savedUri(context: Context, videoId: String): Uri?      // L347
suspend fun delete(context: Context, videoId: String): Boolean    // L405
fun forgetMissing(videoId: String)                               // L430
suspend fun getDownloadedSongs(context: Context): List<Song>      // L751
```

### On-disk layout

`DownloadStore.kt`:
```kotlin
const val FOLDER = "BitChord"                                          // L59
private val relativePath = "${Environment.DIRECTORY_MUSIC}/$FOLDER"    // L61
fun fileNameFor(song: Song, extension: String): String                // L87
    // -> "<artist> - <title>.<ext>", MAX_STEM_CHARS = 120, sanitised
```
Three destinations, chosen by `AppSettings.exportDownloads` + API level:
- **API 29+ exported** → `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`, `RELATIVE_PATH = Music/BitChord`, `IS_PENDING=1` until commit (L280-301).
- **API 26-28 exported** → `Environment.getExternalStoragePublicDirectory(DIRECTORY_MUSIC)/BitChord/`, written `.part` then renamed, then `MediaScannerConnection.scanFile` (L303-308).
- **App-private** → `context.filesDir/downloads/<name>` (L272-279).
- **Offline packages** → `context.filesDir/offline-hls/<id.hashCode()>/` with `playlist.m3u8`, `segment-00000.m4s`, `lyrics.lrc`, `cover.jpg` (`OfflineHls.kt:30`, `OfflineDash.kt:80`).

### Download state & persistence

- `Downloads.DownloadState` (`Downloads.kt:44-53`): `Queued`, `Running(fraction)`, `Failed(reason)`. **There is no `Done` state** — a finished download is *removed* from the map, and success is expressed by membership in `saved`.
- `DownloadState` and `DownloadSession` are **memory-only**; a cold start loses in-flight state.
- **Persisted** in `SharedPreferences` `"bitchord_settings"`: `downloaded_tracks` (videoId→uri), `downloaded_tracks_metadata`, `downloaded_collections` (`Downloads.kt:77-78, :1615`), loaded in `Downloads.init(context)` (`BitChordApplication.kt:166`).

### Retry / resume / duplicates

- **No byte-range resume.** A cancelled/failed download aborts the pending destination and deletes the `.part` (`Downloads.kt:1180-1182`).
- The only retry is a **re-resolve after HTTP 403/404/410 mid-transfer** (`Downloader.kt:102-116`); it errors if the new URL's length differs ("The stream changed mid-download — try again").
- `fetchDirect` (source-backed rows) has **no** retry, by design (`Downloader.kt:144-166`).
- **Duplicate protection exists**: `DownloadStore.existing(context, name)` (L156) queries MediaStore by `DISPLAY_NAME` + `RELATIVE_PATH LIKE %BitChord%` before starting, so a second request *adopts* the existing file instead of creating `… (1)` (`Downloads.kt:1020-1024, :1114-1121`).

### Metadata / artwork

`MediaTagger.kt`: `TAGGABLE = setOf("m4a","webm","flac")` (L69; `.wav` deliberately untagged L61-67). `artworkFor` (L84) fetches the cover **concurrently with the bytes**; `persistArtwork` (L135) writes a `filesDir/download-artwork/<sha256>.jpg` sidecar for WebM. `embed` (L190) is serialised by a global `Mutex` (L121) because a 40 MB FLAC rewrite costs ~120 MB of byte arrays. Publication order in `Downloads.transfer` (L1158-1178): bytes → lyrics/artwork → `embed` → `persistArtwork` → `commit()` (clears `IS_PENDING`) — tags land **before** the file becomes visible.

## 8.5 Listening history, statistics, persistence — **the key existing asset**

### No relational database

Repo-wide search for `RoomDatabase` / `@Database` / `@Entity` / `@Dao` → **zero hits**. BitChord uses **no Room, no SQLite, no SQLDelight**.

Persistence is: **`SharedPreferences`** + **JSON files**.

| Store | Purpose | Location |
|---|---|---|
| `bitchord_settings` | All app settings + download records + Spotify cookie | `SharedPreferences` |
| `bitchord_offline_playlists` | Local playlists incl. the built-in Liked list | `app/src/main/java/com/music/bitchord/data/OfflinePlaylists.kt:55-56` |
| `bitchord_playlist_sync` | Last playlist-sync check timestamp + approval | `download/PlaylistSync.kt:60-61` |
| `bitchord_sources` | Source configs (encrypted) | `data/sources/SourceRegistry.kt:145` |
| `ListeningStats` buckets | **Monthly JSON files** | `data/stats/ListeningStats.kt` |

### `ListeningStats` — `app/src/main/java/com/music/bitchord/data/stats/ListeningStats.kt`

This is the single most important find for this project. Its own header comment states the design:

> *"The obvious shape for listening history is a row per play, summed up when something asks… So the addition happens on the way in. A track's total is a counter that goes up while it plays… **the raw plays are gone**, so a question nobody thought of in advance cannot be answered later. Every question Replay asks is here."*

Structure:
- **One JSON file per calendar month**, in the device's own time zone; only the current bucket is held in memory (`OpenBucket`), the rest are read/merged/released.
- Per-track record — `TrackEntry` (L846-860):
```kotlin
data class TrackEntry(
    val id: String, val title: String = "", val artist: String = "",
    var album: String? = null, var albumId: String? = null, var artistId: String? = null,
    var art: String? = null,
    var ms: Long = 0L,        // total milliseconds played in this month
    var plays: Int = 0,       // play count in this month
    var last: Long = 0L,      // epoch millis of the LAST play  <-- the recency signal
    var days: MutableMap<String, DayStats> = mutableMapOf(),  // "YYYY-MM-DD" -> DayStats(ms, plays)
)
data class DayStats(var ms: Long = 0L, var plays: Int = 0)   // L839-843
```
- Also per-artist and per-album aggregates (`NameEntry` L913-924, same shape), 24-slot hour-of-day histogram, per-day-of-month totals.
- Public API: `init(context)` L109 · `record(song, playedMs, countsAsPlay)` L131 · `flush()` L246 · `months(): List<YearMonth>` L355 · `suspend fun summary(period: ReplaySummary)` L319 · `exportAll(): List<StoredBucket>` L431 · `importAll(buckets)` L444.
- **Storage location**: `File(context.filesDir, "listening")` → `filesDir/listening/YYYY-MM.json`, one file per month (`ListeningStats.kt:110, 822`, `DIRECTORY = "listening"`).
- **Retention**: `KEEP_MONTHS = 36`, `MAX_TRACKS = 600`, `MAX_NAMES = 400` per month bucket, evicted **by least-listened**. ⇒ A 28-day ranking window is comfortably inside the retained history; but note the 600-track/month cap means a very heavy month silently drops its least-played tracks from history.
- **Attribution**: keyed **only** by `song.videoId` (`ListeningStats.kt:140`). `playbackSource` / `playbackSourceType` are **not** recorded — there is no per-source attribution of a play. Artist is attributed by **lead-artist name**, lowercased (`primaryArtist()` splits on `,`, `&`, `x`, `feat.`); album by `albumId` when present, else `name \u001F artist` (`ALBUM_KEY_SEPARATOR = Char(31)`).
- `ReplayPeriod` (L995-1014): `THIS_WEEK`, `THIS_MONTH`, `THIS_YEAR`, `ALL_TIME`.
- `data/stats/Backup.kt` exports/imports the whole thing as JSON (the only way it leaves the device).
- Consumers: `ui/replay/ReplayModel.kt:71-96`, `ui/MainViewModel.kt:1840`, `data/stats/Backup.kt:65,102`.

### `ListeningRecorder` — `app/src/main/java/com/music/bitchord/data/stats/ListeningRecorder.kt`

Its header is a precise statement of the measurement methodology:

> *"The tempting implementation reads the track's position when it ends and calls that the listening. It is wrong in every direction that matters: a track skipped at 0:20 reports twenty seconds it did play, a track seeked back over reports less than it played, a track left paused for an hour reports the same as one played through…"*

- Fed from the playback service's **existing progress sampler** — each tick contributes *wall-clock* time since the last tick, capped at `MAX_STEP_MS`. Seeking cannot inflate it; pausing cannot inflate it.
- A **play** is counted once per track when enough has gone by: `threshold = min(len/2, PLAY_CEILING_MS).coerceAtLeast(PLAY_FLOOR_MS)` — the same half-or-four-minutes rule the scrobbler uses, so the two never disagree. Constants: `PLAY_FLOOR_MS = 30_000L`, `PLAY_CEILING_MS = 4 * 60 * 1000L`, `MAX_STEP_MS = 8_000L`.
- **Cadence**: sampled every ~5 s from `PlaybackService.reportProgress()` → `ListeningRecorder.onSample(it, player.duration)` (`PlaybackService.kt:6690`); flushed to disk every `FLUSH_EVERY = 6` samples. `onStopped()` is called on pause (`PlaybackService.kt:910`), so a paused gap is never billed as listening.
- **Enrichment is YouTube-only**: `albumId`/`albumName`/`artistId` are filled via `YtMusicRepository.trackLinks(videoId)` and only for 11-character YouTube ids (`YOUTUBE_ID_LENGTH = 11`); local files and module/addon tracks are skipped. ⇒ For non-YouTube sources the album/artist metadata in history is whatever the row carried, and `E(t)` has no duration for some rows — hence the "unknown duration ⇒ `E = 0.5` neutral" rule in §12.2.

**Assessment for Smart Offline: this is exactly the right substrate.** It provides recency (`last`), frequency (`plays`), engagement (`ms` vs. expected duration), all bucketed by month *and* by day, entirely on-device, with no network and no Spotify involvement.

**Limitations to design around:**
- **No per-play event log.** Only monthly/day aggregates. ⇒ You cannot compute "plays in the last 7 days" more precisely than day granularity, and you cannot reconstruct a play *time-of-day sequence*. `last` is your only exact timestamp.
- **Keyed by `song.videoId`** — the *resolved* source id (e.g. a YouTube id, or `src:<configId>::<trackId>` for source rows). A Spotify-origin track that resolved to a YouTube row is therefore recorded under the **YouTube id**, not the Spotify id. ⇒ Spotify-id ↔ YouTube-id correlation must be maintained by Smart Offline itself.
- **Not scoped to a user account** — it is device-local, which is correct and privacy-preserving.

### The existing "Replay" feature — and the precedent it sets

A full Spotify-Wrapped-style statistics page already exists:
- Data: `ReplaySummary` — `songs: List<RankedSong>`, `artists/albums/genres: List<RankedEntry>`, `totalMs`, `totalPlays`, `hourOfDay`, `busiestDay`, `distinctSongs/Artists/Albums`, `since`.
- UI: `app/src/main/java/com/music/bitchord/ui/replay/ReplayScreen.kt` (four charts: Top songs / Top artists / Top albums / Top genres, L201-247), plus `ReplayModel.kt`, `ReplayStories.kt` (8 story pages), `ReplayPoster.kt`, `ReplayShareSheet.kt`. Entry cards in `ui/screens/LibraryPhoneParts.kt` (`LibraryReplayEntry`).
- Shared UI: `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/replay/ReplayCard.kt`.

> **This is the precedent that justifies Smart Offline.** `MainViewModel.localTaste()` (`ui/MainViewModel.kt:1839-1847`) already feeds the local Replay into `TopPicks.LocalTaste` (`shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/TopPicks.kt`) — i.e. **BitChord already uses local listening statistics to personalise a recommendation surface.** Smart Offline applies the same idea to *downloads* rather than to *recommendations*. The data plumbing, the privacy posture and the "your data never leaves the device" story are already established in the codebase.

### Playback history UI

`app/src/main/java/com/music/bitchord/ui/screens/HistoryScreen.kt` — *"What the account has been listening to, most recent first."* This is the **account** (YouTube Music) history, i.e. a *remote* history, **not** the local `ListeningStats`. Do not confuse the two.

### Scrobbling

`data/scrobbling/`: `ScrobbleManager.kt`, `LastFM.kt`, `ListenBrainzManager.kt`, `PrimaryArtist.kt`. These already implement the half-or-four-minutes play rule and enrich a track's artist metadata — the same rule `ListeningRecorder` uses.

## 8.6 Background execution, manifest, build

- **WorkManager `2.10.1`** is already a dependency (`app/build.gradle.kts:304`).
- `WorkManagerInitializer` is **removed** from the manifest (`AndroidManifest.xml:662-671`) so WorkManager is initialised **on demand** — "a switch that defaults to off should cost a launch nothing."
- **Exactly one worker exists**: `PlaylistSyncWorker` (`download/PlaylistSync.kt:359`). Repo-wide grep for `WorkManager|CoroutineWorker|ListenableWorker|PeriodicWorkRequest|OneTimeWorkRequest` hits **only** that file.
- `PlaylistSync` design (**the template to copy for Smart Offline**):
  - Unique work names `playlist_sync` / `playlist_sync_now` (L58-59).
  - `PeriodicWorkRequestBuilder<PlaylistSyncWorker>(12 HOURS, 4 HOURS flex)` (L66-67) — "Android batches this with whatever else is waking the phone in the last flex window."
  - `ExistingPeriodicWorkPolicy.UPDATE` on schedule change; `KEEP` / `REPLACE` for one-offs.
  - Constraints (L146-150): `setRequiredNetworkType(UNMETERED or CONNECTED)`, `setRequiresBatteryNotLow(true)`, `setRequiresStorageNotLow(true)`.
  - **Catch-up on foreground**: a `ProcessLifecycleOwner` observer triggers `checkNow` when the last check is older than `CATCH_UP_MILLIS = 6 h` (L70, L103-106).
  - **Idempotence by construction**: "What is new is worked out fresh on every check and nothing about it is stored until it has been dealt with… Android stopping the job, the process dying or a page failing all leave the remaining tracks new, and the next check simply finds them again."
  - **Add-only policy**: "Only ever adds. A track the listener deleted is still named by its playlist's record, so it does not come back."
  - `PlaylistSyncApproval : BroadcastReceiver` (L375) — a user-approval path for batches over `ASK_ABOVE = 50` tracks (~1.7 GB of lossless).
- **Services**: `.playback.PlaybackService` (`mediaPlayback`, L600), `.wear.WearCommandService` (L616), `.download.DownloadService` (`dataSync`, L648-651).
- **Permissions**: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, **`FOREGROUND_SERVICE_DATA_SYNC`**, `POST_NOTIFICATIONS`, `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH_CONNECT`, `VIBRATE`, `REQUEST_INSTALL_PACKAGES`.
- **SDK**: `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` (`app/build.gradle.kts:73-81`).

> `FOREGROUND_SERVICE_DATA_SYNC` is already declared — **no manifest change is needed** for a dataSync-typed download worker.

## 8.7 Settings and UI

- **UI stack**: Jetpack Compose (Material 3) only — no XML layouts, no Fragments, no ViewBinding. `navigation-compose:2.8.5` **is** a dependency but is **unused**: there is no `NavHost`, no `rememberNavController`, no `composable(...)` route builder anywhere in `app/src/main/java`.
- **Navigation**: `app/src/main/java/com/music/bitchord/ui/navigation/AppNavigator.kt` — a hand-rolled sealed `AppRoute` hierarchy with a route stack. Full member list:
  `Settings`, `AccountScrobbling`, `Sources`, `ListenTogether`, `WearOs`, `Equalizer`, `Replay`, `History`, `Discord`, `Spotify`, **`Downloads`**, `LibraryShowAll(shelf)`, `ArtistShelf(browseId, shelf)`, `Moods`, **`SettingsSection(category)`**, **`SettingsChoice(choice)`**, `EqualizerPresets`.
  Album/artist/playlist pages are **not routes** — they live on `MainViewModel.detailStack` as "release pages" pushed over a route. Route→screen wiring is a single `if/else` chain in `MainActivity.kt` (~L2949-3140).
  > **Note for design:** `SettingsSection(category)` and `SettingsChoice(choice)` already exist, so a Smart Offline settings page needs **no new route type** — only a new `SettingsCategory`/`SettingsChoiceKind` entry.
- **Settings screen**: `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt`
  - `SettingsScreen(...)` L189; reusable `SettingsPageTitle` L2466, `SettingsGroup` L2480, `SettingsRow` L2547, `SettingsSubRow` L2636, `SettingsChoicePage` L2880.
  - `SettingsCategory` enum L2820: `DOWNLOADS`, `PLAYBACK`, `CONTENT_PREFERENCES`, `APPEARANCE`, `PERFORMANCE`, `LOCAL_MUSIC`, `STORAGE`, `YOUR_DATA`, `MISCELLANEOUS`, `LANGUAGE`.
  - `SettingsChoiceKind` enum L2849: `AUDIO_QUALITY_WIFI`, `AUDIO_QUALITY_CELLULAR`, `DOWNLOAD_QUALITY`, `AUTOMIX_PERFORMANCE`, `BIT_PERFECT`, `FONT`.
- **Downloads UI**: `app/src/main/java/com/music/bitchord/ui/components/DownloadManagerSheet.kt`; the `AppRoute.Downloads` page is composed in `app/src/main/java/com/music/bitchord/MainActivity.kt:3071`, with a top-bar button at `:4112-4113`.
- **Local playlists / Liked**: `app/src/main/java/com/music/bitchord/data/OfflinePlaylists.kt` — `PREFIX = "local:offline:"` (L52), `LIKED_ID = "liked"` (L53), persisted as JSON in `bitchord_offline_playlists` (L55-56); exposes `playlists: StateFlow<List<OfflinePlaylist>>` and `liked: StateFlow<Set<String>>` (L66, L71).
- **YouTube Music "Liked" is a browse id, not a screen**: `LIKED_MUSIC = "VLLM"` (`shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/YtMusicRepository.kt:948`), fetched by `YtMusicRepository.library()` (`:575-601`).
- **Like state is session-scoped and NOT persisted**: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/LikeState.kt` is a process-level singleton (`_overrides: MutableStateFlow<Map<String, LikeStatus>>`), re-seeded each session from YouTube by `LikeState.seedLiked(...)` and `MainViewModel.syncLikedMusic` (`ui/MainViewModel.kt:1948-1966`).
  > **Consequence for Smart Offline:** the *persisted* like signal is `OfflinePlaylists.liked` (local likes); `LikeState.overrides` is **not** durable and must not be the basis of a long-lived rotation.
- **ViewModels**: only **two** exist — `MainViewModel` (`app/src/main/java/com/music/bitchord/ui/MainViewModel.kt:121`, 3639 lines, ~44 public `StateFlow`s) and `WearViewModel`. **Most app state is not in a ViewModel** — it lives in `object AppSettings` (~90 `MutableStateFlow` settings) and other singletons (`Downloads`, `LikeState`, `OfflinePlaylists`, `LastPlayed`, `ListeningStats`, `AudioCache`), read directly by composables via `collectAsStateWithLifecycle()`. **No Hilt/Koin/Dagger anywhere** (zero hits for `hilt|koin|dagger|@Inject`).

### Download-relevant settings that already exist — `data/settings/AppSettings.kt`

| Setting | Default | Line | Note |
|---|---|---|---|
| `downloadQuality` | `DownloadQuality.LOSSLESS` | 220 | per-track size hints only ("~4 MB" / "~8 MB" / "~35 MB"), **not enforced** |
| `wifiOnlyDownloads` | `true` | 237 | already gates `PlaylistSync` and is exposed to sources via `AppSettings.permits` (L927) |
| `exportDownloads` | `false` | 245 | MediaStore export vs. app-private |
| `syncDownloadedPlaylists` | `false` | 252 | the on/off switch for the existing periodic downloader |
| `audioCacheLimitBytes` | `DEFAULT_CACHE_LIMIT_BYTES` | 652 | **playback cache** (512 MB), enforced via `DynamicLruCacheEvictor` in `playback/AudioCache.kt:86,226,264` — **not** the download folder |

## 8.8 Signal durability — durable vs. session-scoped vs. remotely fetched

This distinction governs what Smart Offline is allowed to build on. A signal that does not survive a restart cannot define a long-lived rotation; a signal fetched from a server cannot be read offline and may vanish.

| Signal | Durability | Where it lives | What Smart Offline may do with it |
|---|---|---|---|
| Listening totals — `ms`, `plays`, `last`, per-day | **Durable** | `filesDir/listening/YYYY-MM.json` (`ListeningStats`), 36 months retained | **The primary signal.** Safe to rank on, offline-safe, never leaves the device. |
| Local likes | **Durable** | `bitchord_offline_playlists` → `playlists_json`, built-in `liked` playlist (`OfflinePlaylists.liked`) | **A durable preference signal.** Safe to weight. |
| Spotify Liked Songs | **Durable but remote** | Fetched via `SpotifyLibrary` `fetchLibraryTracks` (pathfinder GraphQL) | Durable *once fetched*, but **not offline** and **not guaranteed** — it can fail or change shape. Use as a preference input with a graceful fallback, never as the only source. |
| Playlist membership | **Durable but remote** | `SpotifyLibrary` `libraryV3` / `fetchPlaylist`; locally, `OfflinePlaylists` and `bitchord_local_playlists` | Same as above. |
| YouTube Music ratings | **Session-scoped — NOT durable** | `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/LikeState.kt` — an in-memory `MutableStateFlow<Map<String, LikeStatus>>`, re-seeded each session from the server | **Must not be treated as a durable preference signal.** It is empty after a cold start and after a `clear()`. It may contribute a *within-session* boost; it may **not** define a rotation that is expected to persist. |
| The queue | **Durable but volatile** | `bitchord_last_played` (`LastPlayed`) and `bitchord_bounded_context` (`BoundedContextPersistence`) | A **snapshot**, not a history. Optional, short-lived ranking signal only — **weight 0, not implemented in PR #1**. |
| Recently-played shelf / History screen | **Remote** | `YtMusicRepository.recents()` / `homeRecentlyPlayed()` / `history()` — the YouTube Music account | Not a local signal at all. Unavailable offline and not used by Smart Offline. |
| Spotify `GET /me/player/recently-played`, `GET /me/top/*` | **Remote, and unreachable** | Not called — BitChord holds no OAuth grant and no scopes | Unusable without a full re-authentication project. **Out of scope.** |

**The rule this produces:** *Smart Offline's rotation is defined by durable, local signals only.* Session-scoped and remote signals may enrich a ranking for the current session, but nothing that must survive a restart may depend on them.

## 8.9 What does **not** exist (verified absent)

| Capability | Evidence |
|---|---|
| Any relational DB | No `Room`/`@Entity`/`@Dao`/`SQLDelight` anywhere |
| Free-space / disk-availability check | Repo-wide grep for `StatFs`, `freeSpace`, `availableBytes`, `getAvailableBytes` → **0 hits** |
| Download quota / max-folder-size | Nothing enforces `DownloadQuality`'s size hints |
| Automatic cleanup / eviction of downloads | The user deletes; the app only *forgets* records (`Downloads.forgetMissing`) |
| Byte-range resume of a partial download | `DownloadStore.Pending.abort()` deletes the `.part` |
| A second WorkManager worker | Grep hits only `PlaylistSync.kt` |
| A `local` `MusicSource` | Only 4 implementors; local music is `LocalMediaRepository` |
| Spotify OAuth / scopes | None |
| Spotify listening history access | `GET /me/player/recently-played`, `/me/top/*`, `/me/tracks` — **never called** |
| Per-track user exclusions | No such UI or store |
| A "Smart Offline"-style auto-selection page | `OfflinePlaylists` has only the built-in Liked list + user-created local playlists |
| A **local** "recently played" list | None — the Recents shelf and the History screen are **server-side** (`YtMusicRepository.recents()` / `homeRecentlyPlayed()` / `history()`; `data/innertube/PlaybackTracker.kt` registers plays against the YouTube account). The only local history is the aggregate `ListeningStats` |
| Per-source attribution of a play | Not recorded in `ListeningStats`. (The *queue* does carry `source`/`sourceType`/`sourceId` per entry via `LastPlayed`/`BoundedContextPersistence`, but that is a queue snapshot, not a history.) |
| A local scrobble history / retry queue | Scrobbles are fire-and-forget HTTP (`ScrobbleManager` holds in-memory timers only) |

---

# 9. Existing reusable components (what to build on)

| # | Component | Path | Reuse for Smart Offline |
|---|---|---|---|
| R1 | `ListeningStats` | `app/.../data/stats/ListeningStats.kt` | **Primary listening signal source.** Provides `ms`, `plays`, `last`, per-day `days` per track, monthly buckets. Needs a small **read-model** on top (see §13.3) because there is no per-track window query today. |
| R2 | `ListeningRecorder` | `app/.../data/stats/ListeningRecorder.kt` | Already feeds R1 from the playback sampler with correct engagement semantics. **No change needed** — engagement comes free. |
| R3 | `Downloads.enqueue(context, song, from)` | `app/.../download/Downloads.kt:183` | The single programmatic entry point for starting a download. Smart Offline should call this, not re-implement downloading. |
| R4 | `Downloads.saved` / `savedUri` / `delete` / `forgetMissing` | `Downloads.kt:115,347,405,430` | "Already downloaded?" check and eviction. `saved` is persisted, so it survives restarts. |
| R5 | `DownloadStore.existing` | `download/DownloadStore.kt:156` | Duplicate suppression already handled — no re-download of an existing file. |
| R6 | `SourceResolver.forDownload` / `forDownloadOfRow` | `data/sources/SourceResolver.kt:664,159` | Resolving a candidate track to a downloadable stream across YouTube/addon/module/JioSaavn. |
| R7 | `TrackMatcher` | `shared/.../data/sources/TrackMatcher.kt` | Spotify→audio matching, with `null` = "different recording". Use `queries()`, `best()`, `ranked()`. |
| R8 | `SourceRegistry.activeForDownload()` | `data/sources/SourceRegistry.kt:241` | Correct source set for downloads (respects `allowDownloads`). |
| R9 | `PlaylistSync` + `PlaylistSyncWorker` | `download/PlaylistSync.kt` | **The scheduling template.** Unique periodic work, flex window, Wi-Fi/battery/storage constraints, foreground catch-up, idempotent re-derivation, `BroadcastReceiver` approval path. |
| R10 | `OfflinePlaylists` | `data/OfflinePlaylists.kt` | The natural home for a **"Smart Offline" virtual page** alongside "Liked". |
| R11 | `AppSettings` | `data/settings/AppSettings.kt` | Settings home + `MutableStateFlow` pattern + `wifiOnlyDownloads` + `downloadsAllowedNow` (L926: `!wifiOnlyDownloads.value \|\| meteredConnection.value != true`) — the canonical "may a download start right now?" gate. |
| R12 | `Downloads` foreground service | `download/DownloadService.kt` | Already drains the queue with 4 workers; Smart Offline only needs to *enqueue*, not run a second downloader. |
| R13 | `SpotifyLibrary` (`libraryV3`, `fetchPlaylist`, `fetchLibraryTracks`) | `shared/.../data/spotify/SpotifyLibrary.kt` | Playlist + Liked Songs catalogue as **preference seeds** — already implemented, no new Spotify code needed. |
| R14 | `SpotifyMatch` prefixes (`sp:` / `sp-miss:`) | `shared/.../data/model/SpotifyMatch.kt` | Existing resolution-state machine for Spotify-origin tracks. |
| R15 | `MediaTagger` / `LyricsTag` | `download/MediaTagger.kt`, `LyricsTag.kt` | Artwork/metadata/lyrics embedding already happens inside `Downloads.transfer`. No work. |
| R16 | `LocalMediaRepository` | `data/LocalMediaRepository.kt` | Knows what's already on the device; Smart Offline must not re-download a local file. |
| R17 | `TopPicks.LocalTaste` | `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/TopPicks.kt` (+ `MainViewModel.localTaste()` at `ui/MainViewModel.kt:1839-1847`) | **The precedent**: local listening statistics already personalise a recommendation surface. Smart Offline applies the same signal to *downloads*. Also the place to hook "why this track?" explanations, since the Replay screen already renders per-track reasons. |
| R18 | `LastPlayed` / `BoundedContextPersistence` | `playback/LastPlayed.kt`, `playback/BoundedContextPersistence.kt` | The persisted queue (25 behind / 50 ahead, per-entry `source`/`sourceType`/`sourceId`). Useful for a *queue-aware* boost (Spotify's Offline Backup explicitly considers queued tracks) — but it is a queue snapshot, not a history, so it can only ever be a short-lived bonus. |

---

# 10. Missing infrastructure (what must be built)

| # | Gap | Why it matters |
|---|---|---|
| M1 | **A per-track, windowed activity query** over `ListeningStats` | Today only `summary(period)` (ranked artists/albums/tracks for Replay) exists. Smart Offline needs `plays`, `ms`, `last`, and per-day splits for an arbitrary window per **track id**. |
| M2 | **A ranking/scoring component** | Nothing computes a score from listening activity. |
| M3 | **An auto-download orchestrator** | `PlaylistSync` is playlist-driven and add-only. Nothing is driven by a *personalised selection*. |
| M4 | **A free-space check** | Zero `StatFs` usage today; a quota-enforcing feature without one can fill the user's disk. |
| M5 | **A download-storage budget + eviction policy** | No quota, no cleanup. Smart Offline owns files and must be able to reclaim them. |
| M6 | **Ownership tracking** (Smart-Offline-managed vs. user-managed downloads) | `Downloads.saved` has no owner field. Without it, Smart Offline could delete a file the user downloaded deliberately. **Requires a schema addition to the persisted `saved` map** or a parallel store. |
| M7 | **A per-track exclusion store** | No user exclusion mechanism exists anywhere. |
| M8 | **A second WorkManager worker** | Only `PlaylistSyncWorker` exists. |
| M9 | **Settings UI for the feature** | `SettingsCategory`/`SettingsChoiceKind` exist but have no Smart Offline entries. |
| M10 | **A browsing surface for the selection** | `OfflinePlaylists` holds *static* lists; a Smart Offline selection is dynamic and needs a virtual page. |
| M11 | **Spotify-id ↔ resolved-id correlation** | `ListeningStats` keys on resolved ids; Spotify ids live behind the `sp:` prefix. A map is needed so a Spotify-sourced candidate can be correlated with history. |

---

# 11. Proposed Smart Offline behaviour

## 11.1 What the feature is

**Smart Offline maintains a bounded, self-refreshing rotation of the user's own music, downloaded and available offline, without the user selecting anything.**

It has two distinct, independently useful halves:

**(A) "Offline Backup" analogue — *Available Now* (cheap, no downloads).**
A read-only virtual page listing tracks that BitChord has **already** buffered or downloaded and that match the user's recent activity. It works with **zero** network and **zero** storage — the literal Spotify Offline Backup experience, including the "more than five songs recently" style threshold.

**(B) "Smart Offline" — *Smart Rotation* (the real feature).**
A bounded set of tracks that Smart Offline **downloads** for you, ranked by your own listening signals, refreshed on a schedule, with eviction when the ranking changes.

> **Naming:** ship (A) as **"Offline Ready"** or as the *source* view that feeds (B). Ship (B) as **"Smart Offline"**. This avoids one feature name covering two materially different mechanisms (cache-backed vs. download-backed), which is exactly the confusion that surrounds Spotify's own Offline Backup / Offline Mix naming.

## 11.2 User-facing behaviour

| Aspect | Behaviour |
|---|---|
| On/off | A single switch in Settings → Downloads. **Default off** (BitChord's own convention — see `syncDownloadedPlaylists` and the "a switch that defaults to off should cost a launch nothing" note). |
| Where the selection appears | A **virtual page** in the library, beside "Liked", that plays like any other page; plus a Settings sub-page showing the ranked list, the last refresh time, and what is queued. |
| What it downloads | Only tracks the user has actually played (or liked), resolved through BitChord's own sources. |
| Budget | A configurable maximum (bytes and/or track count). Default: **50 tracks**, sized to the user's chosen `downloadQuality`. |
| Network | Honours the existing `wifiOnlyDownloads` toggle and `AppSettings.permits`. Downloads never start on a metered connection if the toggle is off. |
| Refresh | On a schedule (default every 6 h with a 1 h flex window), on app foreground if stale, and immediately after a completed batch. |
| Additions | Tracks entering the selection are downloaded; existing selections are left alone. |
| Removals | A track leaves the rotation when it falls out of the selection **and** it was downloaded by Smart Offline (never user-downloaded files). |
| User control | Exclude a track (it will not be re-added, and is removed if Smart Offline owns it); "keep this one" (pins it); per-track history of why it was chosen ("played 12 times in the last 30 days"). |
| Failure transparency | A visible "last refresh" timestamp, a reason when nothing could be downloaded (no network / no space / no matches), and a per-track failure reason rather than a silent no-op. |

## 11.3 Relationship to Spotify Offline Backup (honest comparison)

| | Spotify Offline Backup | BitChord Smart Offline |
|---|---|---|
| Downloads audio automatically | **No** (cache-backed only) | **Yes** |
| Storage cost | Zero | Bounded, user-set |
| Works with no network | **Yes** | **Yes** (after the download) |
| Selection signals | Recency, queue, "frequently played", "recommended" (server-side, opaque) | Recency, frequency, engagement, likes, playlist membership, exclusions (client-side, explainable) |
| Requires an online round-trip to refresh | No | **Yes** (to resolve + download) |
| Works for a *new* install with no history | No (needs >5 recent plays + a warm cache) | **Partly** — seeds from Liked Songs / playlists, and says so honestly |
| Explainability | None | Per-track reasons shown |

---

# 12. Proposed selection algorithm

## 12.1 Available inputs (established, not assumed)

| Signal | Source | Granularity | Notes |
|---|---|---|---|
| `plays` | `TrackEntry.plays` (monthly) + `TrackEntry.days[].plays` (daily) | day | Aggregate only — **no event log** |
| `ms` | `TrackEntry.ms`, `TrackEntry.days[].ms` | day | Wall-clock, engagement-aware |
| `last` | `TrackEntry.last` | exact epoch ms | The only precise timestamp available |
| Liked (local) | `OfflinePlaylists.liked` | — | Set of local `Song.localUri` — **persisted**, so usable as a durable signal |
| Liked (YouTube Music) | `LikeState.overrides` | — | **Session-scoped, not persisted** (`shared/.../data/LikeState.kt`) — usable as a within-session boost only, never as the basis of a long-lived rotation |
| Liked (Spotify) | `SpotifyLibrary` `fetchLibraryTracks` | — | Catalogue seed |
| Playlist membership | `OfflinePlaylists.playlists` + Spotify `libraryV3` | — | Weak preference signal |
| Queue | n/a | **Yes** — `LastPlayed` / `BoundedContextPersistence` persist the queue | Spotify's Offline Backup explicitly considers **queued** tracks, so BitChord can mirror that. It is a *snapshot*, not a history ⇒ a short-lived **bonus**, never a pillar |
| Already downloaded | `Downloads.saved` | — | Must not re-download |
| Unavailable | `sp-miss:` rows, `Downloads.DownloadState.Failed` | — | Avoid retry loops |
| User exclusion | **Does not exist** → M7 | — | Must be added |

**Deliberately excluded inputs:** Spotify's `GET /me/top/*` (BitChord has no OAuth and no scopes), `Track.popularity` (**removed by Spotify in Feb 2026** and global rather than personal anyway), lifetime play counts (**not exposed by Spotify at all**), and any signal that would require the undocumented pathfinder GraphQL to be extended.

**Caveats on these inputs (verified against the source):**
- **No per-source attribution.** `ListeningStats` records only `song.videoId` — there is no record of *which* source a play came from. ⇒ `E(t)` is a signal about the *track*, not about the *source*. Source quality is therefore **not** part of the score; a track that plays badly on one source but well on another is judged on the merged history.
- **Duration is not always known.** Album/duration enrichment is YouTube-only (11-char ids); local files and addon/module rows may carry no duration. ⇒ `E(t)` falls back to a neutral `0.5` for unknown-duration tracks (see §12.2), never to `0`.
- **History is capped.** 600 tracks and 400 names per month bucket, evicted by least-listened. ⇒ The candidate set is naturally bounded, and a heavy month cannot silently displace the favourites the rotation depends on.
- **Likes for remote tracks are session-scoped** (`LikeState` is not persisted). ⇒ Only `OfflinePlaylists.liked` (local files) and the Spotify Liked Songs catalogue are durable preference signals; `LikeState` may be used as an in-session boost but must not define a long-lived rotation.
- **No "played at" per play.** Recency is exact only to `last`. ⇒ The algorithm must be built on *last-played recency*, not on a reconstructed play sequence.

## 12.2 The ranking formula

For each candidate track `t` in the activity index:

```
score(t) = w_R · R(t) + w_F · F(t) + w_E · E(t) + w_P · P(t)  −  penalty(t)
```

**Recency — `R(t)`** (half-life decay on `last`):
```
R(t) = 2^( −(now − last(t)) / H ),   H = 7 days  (default, configurable)
```
`R ∈ [0,1]`; a track played today is 1.0, a week ago 0.5, two weeks ago 0.25. Using `last` (exact) rather than bucket recency is deliberate — it is the finest-grained timestamp `ListeningStats` keeps.

**Frequency — `F(t)`** (decayed play count over the window `W = 28 days`):
```
plays_decayed(t) = Σ_{d ∈ days(t), age(d) ≤ W}  plays(d) · 2^( −age(d) / 21 days )
```
Using decayed plays rather than raw plays means a track hammered last month and abandoned this month decays naturally, and it removes the need for a separate recency term to carry most of the signal. `21 days` half-life ≈ one third of the window.

**Engagement — `E(t)`** (does the user actually *finish* it?):
```
expected_ms(t) = Σ_{d} plays(d) · (duration(t) · 0.75)
E(t) = clamp( ms(t) / max(1, expected_ms(t)), 0, 1 )
```
This is what separates "skipped every time" from "played to the end", and it is a signal **Spotify's public API does not expose at all**. A track with `ms ≈ 0` but `plays > 0` scores `E ≈ 0` and is correctly demoted.
*Edge case:* tracks whose duration is unknown (no `duration_ms` on the row) fall back to `E = 0.5` (neutral) rather than 0, so unknown-duration tracks are neither rewarded nor punished.

**Preference — `P(t)`**:
```
P(t) = max of:
    1.00  locally liked
    0.90  in Spotify Liked Songs
    0.60  in any user playlist (local or Spotify)
    0.30  played ≥ 5 times in W   (an implicit favourite)
    0.00  otherwise
```

**Penalty — `penalty(t)`**:
```
penalty(t) = 0.30  if t failed to resolve/download ≥ 3 times
             0.20  if t was evicted before and its score has not recovered
             0.10  if t's duration differs > 30 s from the activity-index duration (identity doubt)
```
> **Later phases only.** The *user-exclusion* penalty (`0.50`, hard) and the *eviction-recovery* penalty both require a store that does not exist yet (M6/M7). They are listed here for completeness of the eventual design but are **not part of PR #1**, whose selector has no exclusion, ownership or eviction inputs.

**Optional queue bonus — weight 0, not implemented in PR #1.** Spotify's Offline Backup explicitly considers **queued** tracks, and BitChord persists the queue (`LastPlayed` / `BoundedContextPersistence`, 25 behind / 50 ahead, with per-entry `source`/`sourceType`/`sourceId`). Mirroring Spotify would give a queued track a small bonus so it is resolved and downloaded before it is actually needed. **It is deliberately not implemented in PR #1**: the queue is a *snapshot* that evaporates, so it cannot carry weight in a long-lived ranking, and adding it in PR #1 would couple the pure selector to the playback service for no user-visible benefit yet. The weight stays **0** and the feature is deferred to a later phase, where it belongs with the download machinery.

**Weights (defaults, all user-tunable via a strategy selector):**

| Strategy | `w_R` | `w_F` | `w_E` | `w_P` | Character |
|---|---|---|---|---|---|
| **Recently Played** | 0.70 | 0.10 | 0.10 | 0.10 | Mirrors "what did I just play". Fast-moving; churns more. |
| **Frequently Played** | 0.20 | 0.55 | 0.15 | 0.10 | Mirrors "favourites". Stable; churns least. |
| **Smart (default)** | 0.35 | 0.30 | 0.15 | 0.20 | Balanced. |

## 12.3 Normalisation

Components are **not** min-max normalised across candidates (unstable for small sets and skewed by one outlier). Instead each component is converted to its **rank percentile within the candidate set**:

```
R̂(t) = (rank of R(t) among candidates, descending) / N
```

- **Percentile ranking** is robust to outliers and to differing distributions (a user with 400 plays and one with 3 both get a well-spread 0-1 scale).
- **Fallback:** if `N < 5`, fall back to the raw normalised component values (with `F` divided by `max(1, maxPlays)` and clipped to 1) — the ranking is unstable at small N and the UI should say so.
- The final `score(t)` is then the **weighted sum of percentiles**, so it is on a stable 0-1 scale regardless of strategy weights.

## 12.4 Time windows

| Window | Default | Purpose |
|---|---|---|
| **Ranking window `W`** | **28 days** | Balances recency against habit formation. Spotify's public affinity windows are ~4 weeks / ~6 months / all-time; 28 days matches its short-medium window and is long enough for `ListeningStats` monthly buckets to be meaningful. |
| **Recency half-life `H`** | 7 days | A track not played for a month scores ~0.02 on recency. |
| **Frequency half-life** | 21 days | ≈ `W / 1.33`. |
| **Long tail** | all-time `ms`/`plays` retained | Used only as a tie-breaker and for the "familiar favourite" floor — **never** as a primary driver, so a track you loved two years ago does not permanently occupy the rotation. |

**Two-tier structure (the "familiar vs. discovered" balance):**
- **Core (70% of the budget)** — the highest-scoring tracks. These are the "familiar favourites". Chosen with a **higher engagement weight** and, in the download-management phase, a **hysteresis bonus** (see §12.8 — *not* part of PR #1).
- **Rotation variety (30% of the budget)** — the highest-scoring tracks **not** in Core, with a bias toward tracks with a *single recent play* and a **penalty for already-downloaded tracks** so the slot genuinely rotates.
  > **Both tiers are drawn from the same candidate set** — tracks already present in the user's listening history, plus preference candidates. "Rotation variety" means *ranked differently and weighted to keep changing*, **not** newly discovered music the user has never heard. Smart Offline never invents candidates; it reorders what the user has actually listened to.

This gives a rotation that is 70% predictable and 30% varied — the ratio that keeps a listener from feeling the app has locked them into a loop, without turning the feature into a random downloader.

## 12.5 Selection limits

| Limit | Default | Source |
|---|---|---|
| Max tracks | 50 | New setting |
| Max bytes | computed from `downloadQuality` size hints × max tracks, plus a user-set hard cap | New setting; `DownloadQuality` hints already exist (`AppSettings.kt:105-124`) |
| Min free space required before starting a batch | 512 MB (recommended) + the batch's own size | **New** — no `StatFs` check exists today (M4) |
| Max tracks downloaded per cycle | 10 | Prevents a burst of 50 downloads from fighting the user for bandwidth |
| Per-track download attempts before suppression | 3 | Feeds `penalty(t)` |

## 12.6 Tie-breaking

Deterministic and stable, in order:
1. Higher weighted score.
2. Higher `E(t)` (engagement) — a track you actually finish beats one you skip.
3. More recent `last(t)`.
4. Higher `plays_decayed(t)`.
5. **Lexicographic by `title`, then `artist`** — total order, so the selection never depends on hash iteration order and is reproducible across runs (important for a "why did this change?" UI and for tests).

## 12.7 Refresh policy

| Trigger | Schedule |
|---|---|
| Periodic | `PeriodicWorkRequest` every **6 h**, flex **1 h** (≥ the 15-minute WorkManager minimum). 6 h is half of `PlaylistSync`'s 12 h because Smart Offline reacts to *personal* taste, which changes faster than playlist contents. |
| Foreground catch-up | If the last successful refresh is older than 12 h, refresh on app foreground — mirroring `PlaylistSync`'s `CATCH_UP_MILLIS` pattern (`PlaylistSync.kt:70, 103-106`). |
| Settings change | Immediate one-off refresh when the user changes strategy, budget, or Wi-Fi preference. |
| After a batch | Re-derive the desired set after the batch completes so the UI reflects reality. |
| Debounce | A refresh that fails re-schedules with exponential backoff (WorkManager default), never in a tight loop. |

**Network constraint:** `UNMETERED` when `wifiOnlyDownloads` is on, else `CONNECTED`; plus `setRequiresBatteryNotLow(true)` and `setRequiresStorageNotLow(true)` — exactly the `PlaylistSync.constraints()` triple.

## 12.8 Download eviction policy — avoiding constant download/delete churn

> **Phase note.** This entire mechanism belongs to the **download-management phase (PR #2 and later)**. **PR #1's selector produces a ranking and nothing else** — no retention decisions, no hysteresis, no eviction. It is described here because the ranking in §12.2 was designed with it in mind (the hysteresis bonus on the core tier, and the "penalty for already-downloaded tracks" on the rotation-variety tier, are only meaningful once something is actually on disk).

This is the hardest part of the design, and Spotify's Offline Backup sidesteps it entirely by owning no storage. Five rules:

1. **Ownership.** Only files that Smart Offline *downloaded* may be evicted. Ownership is tracked in a new store (M6); user-downloaded files, local files, and files inside a user playlist are **never** touched.
2. **Hysteresis.** A downloaded track is evicted only if it falls **below** the current cut-off *and* sits in the bottom **20%** of the target set — equivalently, a downloaded track is kept while `score(t) ≥ 0.6 × cutScore`. A track oscillating around the boundary therefore stays.
3. **Sticky core.** A downloaded track that is still within the **top 2N** is kept even if it has dropped in rank. N = 50 ⇒ the top 100 is effectively "safe", which is what stops a single noisy week from swapping the whole rotation.
4. **Eviction cooldown.** A track evicted is put on a **suppression list for 14 days** unless its score rises back above the cut-off. This directly prevents "downloaded it, evicted it, downloaded it again".
5. **Quota-first, not score-first.** When the storage budget is exceeded, evict **lowest-scoring Smart-Offline-owned** tracks first, until the budget is met. User-owned files are never evicted to satisfy a Smart Offline budget. If the budget still cannot be met without touching user files, Smart Offline **stops and reports** rather than deleting anything the user asked for.

**Net effect:** with default settings and typical listening, a stable core of ~35 tracks is kept indefinitely; churn is limited to the rotation-variety tier (≈15 tracks), rotating roughly every few weeks, and never more than a handful of downloads per cycle.

## 12.9 Insufficient listening data (cold start)

| Situation | Behaviour |
|---|---|
| No `ListeningStats` at all (fresh install) | Smart Offline stays **off**/idle and says: *"Play a few tracks and Smart Offline will build your rotation."* It must not silently seed from nothing. |
| < 10 distinct tracks with ≥ 1 play | **Seeded mode** *(later phase)*. Fall back to the **Liked Songs** catalogue (Spotify `fetchLibraryTracks` or the local `OfflinePlaylists.liked`) as candidates, with `R` unavailable → treated as 0.5 (neutral) and `P` carrying the weight. Clearly labelled *"seeded from your likes"* in the UI. **PR #1 does not seed** — it ranks the listening history it is given. |
| < 5 tracks in the whole history | Below Spotify's own documented threshold of *"more than five songs recently"*. **PR #1 does not gate on this**: the selector ranks **any non-empty candidate set**, however small, and the page simply shows what there is. The five-track floor is a *download-phase* policy — there is no point auto-downloading a rotation of two tracks — and it belongs with the phase that actually downloads. |
| Any non-empty candidate set | The selector returns a ranked list for **every** candidate it is given. A one-track history yields a one-track result. There is no minimum size, no minimum plays, and no minimum window. |
| History exists but is old | `last` far in the past ⇒ `R ≈ 0` ⇒ tracks drop out naturally; the rotation-variety tier refills from recent plays. |

## 12.10 Duplicate and unavailable tracks

| Situation | Handling |
|---|---|
| Two Spotify tracks resolving to the same audio track | Dedupe on the **resolved identity** (`song.videoId` / `src:<configId>::<trackId>`), not the Spotify id. Keep the higher-scoring candidate. |
| Spotify track with no audio-source match | Mark `sp-miss:` (existing mechanism, `SpotifyMatch.kt`), `penalty += 0.30`, exclude from the set, and **retry at most 3 times** across refreshes before suppressing for 30 days. |
| Track already downloaded | Never re-downloaded (`DownloadStore.existing` already adopts the file; `Downloads.saved` tells us). |
| Track is a local file | Already offline — never download it; count it as satisfied. |
| Two candidates map to different YouTube ids for the same song | `TrackMatcher` resolves the ambiguity; `null` = miss. Use `TrackMatcher.best()`, not `candidates.firstOrNull()`. |
| Album/single duplicates | `TrackMatcher` weights `ALBUM_EXACT 35` and `DURATION_TIGHT 40`; the `DURATION_LIMIT_SEC = 30` veto prevents picking the wrong recording. |

---

# 13. Proposed Android architecture

## 13.1 Principles

1. **No new download infrastructure.** Smart Offline *enqueues* into `Downloads`; it never fetches bytes.
2. **No new persistence layer.** No Room. Reuse `SharedPreferences` + JSON, matching the codebase's existing idiom.
3. **No DI framework.** Hand-rolled singletons + `StateFlow`, like `Downloads`, `PlaylistSync`, `ListeningStats`, `OfflinePlaylists`.
4. **No new Spotify endpoints.** Use only `SpotifyLibrary`'s existing `libraryV3` / `fetchPlaylist` / `fetchLibraryTracks`.
5. **Pure, testable selection.** The ranking function must be a pure function over an immutable data snapshot, so it can be unit-tested without Android.
6. **Idempotent re-derivation**, copying `PlaylistSync`'s proven property: *"nothing about it is stored until it has been dealt with."*

## 13.2 Component map

```
                    ┌──────────────────────────┐
                    │   PlaybackService        │
                    │   (existing)             │
                    └────────┬─────────────────┘
                             │ progress sampler ticks
                             ▼
                    ┌──────────────────────────┐
                    │  ListeningRecorder       │  (existing)
                    │  → ListeningStats        │  (existing) monthly JSON buckets
                    └────────┬─────────────────┘
                             │ read-only
                             ▼
   ╔═════════════════════════════════════════════════════════╗
   ║  NEW  data/smartoffline/                                ║
   ║                                                        ║
   ║  ActivityIndex  ── reads ListeningStats buckets and     ║
   ║     │              produces per-track windowed stats    ║
   ║     ▼                                                  ║
   ║  OfflineTrackSelector  (PURE)  ── strategy, weights,    ║
   ║     │                    normalisation, ranking      ║
   ║     ▼                                                  ║
   ║  SmartOfflineManager  ── diff(desired, downloaded),      ║
   ║     │                 enqueue / evict / suppress       ║
   ╚═════╪═════════════════════════════╤════════════════════╝
         │                             │
         ▼                             ▼
  Downloads.enqueue()            OfflinePlaylists  (virtual "Smart Offline" page)
  (existing queue + FGS)                    │
         │                                  ▼
         ▼                            ui/screens/SmartOfflineScreen.kt
  MediaStore / app-private files            │
                                             ▼
                                     Settings (SettingsSheet.kt)
```

## 13.3 New components

### 13.3.1 `ActivityIndex` — `app/src/main/java/com/music/bitchord/data/smartoffline/ActivityIndex.kt` *(new — **PR #1**)*

A read-model over `ListeningStats` that answers "what did I listen to in the last N days, and how".

```kotlin
data class TrackActivity(
    val id: String,                 // resolved song.videoId
    val title: String,
    val artist: String,
    val playsInWindow: Int,
    val msInWindow: Long,
    val decayedPlays: Double,
    val lastPlayedAt: Long,         // epoch ms, 0 = never
    val durationMs: Long,           // 0 = unknown
    val perDay: Map<String, DayStats>,
)

object ActivityIndex {
    suspend fun build(windowDays: Int): Map<String, TrackActivity>
}
```

Implementation: iterate `ListeningStats.months()` → `exportAll()` buckets → filter to those overlapping the window → merge `TrackEntry.days` → apply the decay. **Read-only**; `ListeningStats` is not modified. *Note:* `ListeningStats` holds only the *current* month in memory and keeps older months as files, so `build()` reads files — do it on `Dispatchers.IO` and cache the result per refresh.

### 13.3.2 `OfflineTrackSelector` — `app/src/main/java/com/music/bitchord/data/smartoffline/OfflineTrackSelector.kt` *(new — **PR #1**)*

**Pure.** No Android, no I/O.

```kotlin
enum class SelectionStrategy { RECENTLY_PLAYED, FREQUENTLY_PLAYED, SMART }

data class SelectorWeights(val recency: Double, val frequency: Double,
                            val engagement: Double, val preference: Double)

data class SelectorConfig(
    val strategy: SelectionStrategy = SelectionStrategy.SMART,
    val windowDays: Int = 28,
    val recencyHalfLifeDays: Double = 7.0,
    val frequencyHalfLifeDays: Double = 21.0,
    val coreShare: Double = 0.7,
)

data class ScoredTrack(
    val id: String,
    val score: Double,
    val components: Components,     // R, F, E, P — for the "why this track?" UI
    val reasons: List<String>,      // e.g. "played 12 times in the last 28 days"
)

object OfflineTrackSelector {
    fun select(
        activity: Map<String, TrackActivity>,
        preferences: PreferenceLookup = PreferenceLookup.NONE,
        config: SelectorConfig = SelectorConfig(),
        now: Instant = Instant.now(),
    ): SelectionResult
}
```

`SelectionResult` contains `core: List<ScoredTrack>`, `rotationVariety: List<ScoredTrack>`, and `reasons` for every decision.

> **PR #1 scope note.** The signature above is the **PR #1** shape, and it is the shape that was implemented: there is **no `excluded` parameter** (no exclusion store exists yet — M7), **no `minTracks`** (any non-empty candidate set is supported), **no `alreadyDownloaded` input** (nothing is downloaded, so nothing can already be "one of ours"), and **no retention/hysteresis output** (that belongs to the download-management phase — §12.8). The only inputs are the history, the durable preferences, the configuration, and the moment to measure against.

**Unit-testable with zero fixtures** — this is the single most valuable property of the design, and the basis of the recommended MVP.

### 13.3.3 `SmartOfflineManager` — `app/src/main/java/com/music/bitchord/data/smartoffline/SmartOfflineManager.kt` *(new — **PR #2 and later**)*

The orchestrator. Responsibilities:

1. Build the `ActivityIndex` for the configured window.
2. Ask `OfflineTrackSelector` for the desired set.
3. **Diff** against `Downloads.saved` + the ownership store:
   - `toDownload = desired − alreadyDownloaded` (minus excluded, minus failures).
   - `toEvict = smartOfflineOwned − keepSet` (after hysteresis + sticky-core + cooldown).
4. **Enqueue** via `Downloads.enqueue(context, song, from = "smart_offline")`, capped at `maxPerCycle`.
5. Enforce the **byte budget** with a `StatFs` free-space check **before** enqueueing (M4) — the first `StatFs` usage in the codebase; it belongs here because this is the feature that can fill a disk.
6. Update the ownership + suppression stores.
7. Expose `StateFlow<SmartOfflineState>` for the UI: `{ enabled, lastRefreshAt, lastError, downloading, queued, evicting, usedBytes, budgetBytes, tracks: List<ScoredTrack> }`.

**Error handling:**
| Failure | Handling |
|---|---|
| No network | Skip the cycle; keep the last selection; report *"Waiting for Wi-Fi"*. |
| Insufficient free space | Do not enqueue; report the shortfall. Never evict user files to make room. |
| Resolution failed (no source) | `sp-miss:`; `penalty`; retry ≤ 3 times then suppress 30 days. |
| Download failed | `Downloads.DownloadState.Failed` already carries a reason; surface it; count towards the attempt limit. |
| Worker killed mid-cycle | **Safe by construction** — the desired set is re-derived from scratch on the next run; nothing is half-committed. |
| Process death between selection and enqueue | `Downloads.enqueue` is idempotent w.r.t. already-downloaded files (`DownloadStore.existing`), so a re-run is safe. |

### 13.3.4 `SmartOfflineWorker` — `app/src/main/java/com/music/bitchord/download/SmartOfflineWorker.kt` *(new, next to `PlaylistSyncWorker` — **PR #2 and later**)*

A `CoroutineWorker` mirroring `PlaylistSyncWorker` (L359): unique work name `smart_offline`, periodic `6 h` / `1 h` flex, the same constraints triple, `Result.success()` on "nothing to do" (so WorkManager does not back off a no-op), `Result.retry()` only on genuine transient failures.

Because the actual downloading is already handled by `DownloadService` (the `dataSync` FGS with 4 workers), **the worker only decides and enqueues** — it does not download. This avoids a second downloader and the `foregroundServiceType` complexity that would come with it.

### 13.3.5 Ownership & exclusion stores — `data/smartoffline/SmartOfflineStore.kt` *(new — **PR #2 and later**)*

`SharedPreferences` file `bitchord_smart_offline`, keys:
- `owned_tracks` → `Map<videoId, OwnedInfo(lastSelectedAt, addedAt)>` — the Smart-Offline-managed set (M6).
- `excluded_tracks` → `Set<videoId>` (M7).
- `pinned_tracks` → `Set<videoId>` (user "keep this one").
- `suppressed_until` → `Map<videoId, epochMs>` (eviction cooldown, M4/M7).
- `last_refresh_at`, `last_result`.

Deliberately **not** in `AppSettings`, because these are *state*, not *preferences* — matching how `Downloads` keeps its own `saved` map and `PlaylistSync` keeps `bitchord_playlist_sync`.

### 13.3.6 Settings — `data/settings/AppSettings.kt` *(modified)*

Add to the existing pattern (`MutableStateFlow` + key + load/save):

| Key | Type | Default |
|---|---|---|
| `smart_offline_enabled` | Boolean | `false` |
| `smart_offline_strategy` | String | `"SMART"` |
| `smart_offline_max_tracks` | Int | `50` |
| `smart_offline_max_bytes` | Long | `0` (auto from quality) |
| `smart_offline_refresh_hours` | Int | `6` |
| `smart_offline_core_share` | Float | `0.7` |

Reuse `wifiOnlyDownloads` and `downloadQuality` — **do not** add parallel copies.

### 13.3.7 UI

| New file | Purpose |
|---|---|
| `app/src/main/java/com/music/bitchord/ui/screens/SmartOfflineScreen.kt` | The ranked list, with per-track "why" (plays, last played), and actions: exclude, pin, download now. |
| `app/src/main/java/com/music/bitchord/ui/screens/SmartOfflineSettings.kt` | (Or a section in `SettingsSheet.kt`.) Toggle, strategy selector, budget, refresh interval, storage used, last refresh, "Refresh now", "Clear Smart Offline downloads". |

**Integration points:**
- `SettingsSheet.kt` — add a `SettingsChoiceKind.SMART_OFFLINE_STRATEGY` (the `SettingsChoice` route and `SettingsChoicePage` already exist, so **no new route type is needed**) plus a block in the `DOWNLOADS` and `STORAGE` categories.
- `AppNavigator.kt` — add `data object SmartOffline : AppRoute` **only if** a dedicated full-screen route is wanted; exposing the page through `OfflinePlaylists` avoids it entirely.
- `OfflinePlaylists.kt` — add a built-in virtual entry `SMART_ID = "smart"` alongside `LIKED_ID = "liked"`, under the same `local:offline:` prefix, whose contents are supplied by `SmartOfflineManager` rather than stored.
- `MainActivity.kt` — render the new route at the same place `AppRoute.Downloads` is composed (L3071-3075), and add the top-bar entry beside `TopBarDownloadButton` (L4112-4113).

**Recommendation:** put the selection in `OfflinePlaylists` **and** give it a dedicated screen; the offline playlist entry makes it playable from the library with zero new navigation, and the screen makes the ranking explainable.

## 13.4 Data flow

```
[PlaybackService sampler] → ListeningRecorder → ListeningStats (monthly JSON)
                                                          │
                        (periodic 6h / foreground / settings change)
                                                          ▼
                                             SmartOfflineWorker.doWork()
                                                          │
                                          SmartOfflineManager.refresh()
                                             │            │            │
                             ActivityIndex  │  Selector  │  Store
                                             ▼            ▼            ▼
                                       ListeningStats  pure score  owned/excluded/
                                                     │              suppressed
                                                     ▼
                                              desired set
                                                     │
                             ┌───────────────────────┴──────────────────┐
                             ▼                                          ▼
                    Downloads.enqueue(...)                    evict owned tracks
                             │                                          │
                             ▼                                          ▼
                    DownloadService (FGS dataSync)             Downloads.delete(...)
                             │
                             ▼
                    Music/BitChord/*.mp3 (+ artwork, lyrics, metadata)
```

## 13.5 Android background-execution considerations

| Constraint | Handling |
|---|---|
| **Doze / App Standby** | A `PeriodicWorkRequest` at 6 h is batched with other wake-ups. `PlaylistSync` already accepts this and adds a **foreground catch-up** — copy that. |
| **WorkManager minimum period** | 15 minutes. 6 h chosen deliberately — frequent enough for a personal rotation, rare enough not to be battery-hostile. |
| **Background FGS start restrictions (Android 12+)** | Smart Offline does **not** start a foreground service. It enqueues into the existing `DownloadService`, which is started by the app's own foreground path. `FOREGROUND_SERVICE_DATA_SYNC` is already declared. |
| **Network availability** | Enforced via `Constraints.setRequiredNetworkType` + `AppSettings.permits`. |
| **Battery** | `setRequiresBatteryNotLow(true)`; at most `maxPerCycle = 10` downloads per cycle; no work when the feature is off (WorkManager is initialised on demand in this app, so an off switch costs nothing at launch). |
| **Storage** | `setRequiresStorageNotLow(true)` + an explicit `StatFs` check before enqueueing. |
| **User-visible notification** | `DownloadService` already shows a progress notification for downloads. Smart Offline's own refresh is silent unless it fails or needs approval. |
| **Doze window** | Consider `setRequiresDeviceIdle(false)` (default) so the refresh isn't blocked by idle. |

---

# 14. Feasibility matrix

| Capability | Spotify API support | Existing BitChord support | Required work |
|---|---|---|---|
| **Recently played** | **Partial** — `GET /me/player/recently-played` (`user-read-recently-played`), max 50/page, timestamp-cursored. *But BitChord has no OAuth and never calls it.* | **Excellent** — `ListeningStats` tracks per-track `last` + per-day `plays`/`ms`, on-device, monthly JSON buckets | `ActivityIndex` read-model (M1). No Spotify work. |
| **Most played** | **Partial** — `GET /me/top/tracks` (`user-top-read`), pre-aggregated rank only, no counts. Not usable by BitChord (no OAuth). | **Excellent** — `TrackEntry.plays` per month and per day | Decayed-play computation in `ActivityIndex`; scoring in `OfflineTrackSelector` |
| **Listening frequency** | **No** personal counts exposed | **Yes** — `plays` per month/day | Decay + percentile normalisation |
| **Listening engagement** | **No** — no duration/skip/engagement endpoint at all | **Yes, uniquely** — `ms` from wall-clock sampling + `plays` → completion ratio | `E(t)` component; `durationMs` per track (needs duration on the activity row) |
| **Track matching** | n/a | **Excellent** — `TrackMatcher` (weights, version/context rules, duration veto) + `SourceResolver.matchAndStream` + `SourceRegistry.activeForDownload()` | Compose the two into a `SmartOfflineResolver`-style path; reuse `sp:`/`sp-miss:` states |
| **Automatic downloads** | **No** — Spotify never auto-downloads music; only podcast auto-download exists | **Yes** — `Downloads.enqueue`, `DownloadService` (4 workers), `PlaylistSync` periodic worker | `SmartOfflineWorker` + `SmartOfflineManager` (M3, M8) |
| **Storage limits** | **No** — Spotify's are device-level, not user-configurable per feature | **Partial** — `audioCacheLimitBytes` exists but governs the *playback cache*, not downloads. **No `StatFs` anywhere.** | New `StatFs` check (M4), byte budget + quota (M5), settings |
| **Automatic cleanup** | **No** — Spotify's downloads are user-managed | **No** — only `forgetMissing`; nothing deletes | Ownership store (M6) + eviction with hysteresis/cooldown (§12.8) |
| **Background refresh** | n/a | **Partial** — WorkManager dependency present, one worker, `PlaylistSync` template, on-demand init | Second worker + schedule (M8) |
| **Track exclusions** | **No** per-feature control; only the global "exclude from taste profile" in the consumer app (not API-accessible) | **No** | Exclusion + pin store (M7) + UI |
| **Liked Songs as a seed** | **Yes** — `GET /me/tracks` (`user-library-read`) — *unavailable to BitChord* (no OAuth) | **Yes** — Spotify `fetchLibraryTracks` (pathfinder) + local `OfflinePlaylists.liked` | Nothing; reuse existing |
| **Playlist membership** | **Yes** — `GET /me/playlists` + `GET /playlists/{id}/items` | **Yes** — `SpotifyLibrary.libraryV3` / `fetchPlaylist` + `OfflinePlaylists` | Nothing; reuse existing |
| **Per-user personalisation** | **Server-side only** — no client artifact exposes the ranking | **Client-side** — fully explainable, a deliberate BitChord advantage | Explainable per-track reasons UI |
| **Cold start (no history)** | **No** — requires > 5 recent plays | **Partial** — seed from Liked Songs / playlists | Seeded mode + honest labelling |

---

# 15. Risks and limitations

## 15.1 Technical impossibilities (not merely unimplemented)

| Risk | Detail |
|---|---|
| **R-IMP-1: Spotify listening history is unreachable from BitChord** | BitChord holds no OAuth grant and no scopes, so `GET /me/player/recently-played` and `GET /me/top/*` are **not callable**. Adding OAuth would mean a full re-auth flow — a much larger change than Smart Offline, and one that would replace a working cookie-based integration. **Smart Offline must not depend on it.** |
| **R-IMP-2: Spotify's ranking algorithm is not obtainable** | Server-side, proprietary, and no client artifact exposes it. BitChord cannot replicate Spotify's exact behaviour and should not claim to. It can only reproduce the *documented behaviour* (recency + queue + frequency + recommendation). |
| **R-IMP-3: No per-play event log in `ListeningStats`** | By design ("the raw plays are gone"). Consequence: "plays in the last 7 days" is only day-granular; "time of day you usually listen" is only a 24-slot hour histogram; per-play ordering within a day is lost. Any feature needing it requires changing `ListeningStats`' storage model — **out of scope for Smart Offline**, and a change that would break the Replay page's assumptions. |
| **R-IMP-4: No Spotify subscription-tier detection** | `User.product` was **removed from the Spotify API in Feb 2026**, and BitChord never read it. Smart Offline cannot gate on Premium. It doesn't need to — BitChord resolves audio through its own sources. |
| **R-IMP-5: Smart Offline cannot download Spotify audio** | Not a limitation to work around — it is the correct design. Audio is resolved and downloaded through YouTube Music / addon / module / JioSaavn sources. Spotify supplies **metadata and library membership only**. |

## 15.2 Implementation risks

| Risk | Mitigation |
|---|---|
| **R-TECH-1: Download churn** — the rotation thrashes, downloading and deleting constantly | Hysteresis (×0.6), sticky core (top 2N), 14-day eviction cooldown, `maxPerCycle = 10`, 6 h refresh interval. |
| **R-TECH-2: Disk exhaustion** — Smart Offline fills the device | `StatFs` check before every batch; byte budget; eviction of *owned* files only; `setRequiresStorageNotLow(true)`; report rather than delete user files. |
| **R-TECH-3: Wrong-recording downloads** — the matcher picks a remix/live/cover | Use `TrackMatcher.best()` (never `firstOrNull()`); the `DURATION_LIMIT_SEC = 30` veto and the `versions` bidirectional rule already handle this. Surface the resolved title/artist in the UI so the user can see a mismatch and exclude it. |
| **R-TECH-4: Duplicate downloads** | `DownloadStore.existing()` already adopts existing files by display name; `Downloads.saved` is the authoritative check. Dedupe on resolved identity. |
| **R-TECH-5: Metadata churn on already-downloaded files** | `Downloads.transfer` re-tags on every transfer. Never re-transfer an existing file — check `Downloads.saved` before enqueueing. |
| **R-TECH-6: Spotify pathfinder breakage** | Internal GraphQL endpoints and their persisted-query hashes can change without notice. Smart Offline must use **only** the operations BitChord already uses. If `fetchLibraryTracks` breaks, Liked Songs seeding degrades — the feature must still work from local history alone. |
| **R-TECH-7: Battery** | 6 h periodic + flex, `batteryNotLow`, bounded batch size, WorkManager only (no polling loops). |
| **R-TECH-8: Activity-index cost** | `ActivityIndex.build()` reads monthly JSON files. Do it on IO, once per refresh, and cache. Do not rebuild per UI frame. |
| **R-TECH-9: `ListeningStats` and Smart Offline coupling** | Read-only dependency. If `ListeningStats`' storage model changes, `ActivityIndex` is the only file to update. Keep the dependency one-directional. |
| **R-TECH-10: Ownership ambiguity** | If ownership metadata is lost (app data cleared, preferences reset), Smart Offline must **not** assume ownership of files it did not download. Default to "not owned" → never delete. |

## 15.3 Legal and policy considerations

| Consideration | Assessment |
|---|---|
| **No Spotify audio is downloaded** | **The core architectural principle holds.** Spotify supplies metadata and library membership; audio comes from BitChord's own configured sources. No Spotify DRM is touched, no encrypted cache is read, no protected audio is extracted. |
| **Use of Spotify's internal GraphQL** | BitChord already depends on undocumented `api-partner.spotify.com/pathfinder` endpoints. Smart Offline must **not widen** that dependency. Flagged as a pre-existing project risk, not one Smart Offline introduces. |
| **The `sp_dc` cookie stored in plain `SharedPreferences`** | A **pre-existing** security issue (contrast: the Discord token *is* encrypted via `auth/EncryptedPrefs.kt`; and `AppSettings.exportPrefs()` does exclude `spotify_spdc_token` from data exports, so the exposure is at-rest on the device rather than in exported archives). Out of scope for Smart Offline, but worth a separate issue. |
| **Downloading audio from YouTube Music / addon sources** | BitChord's own pre-existing posture; the README already carries a not-affiliated-with-YouTube/Google disclaimer. Smart Offline does not change the nature of what BitChord downloads, only *how much* and *when*. It does make download volume more visible, so the UI must make it clear what is being fetched and from where, and give the user a hard stop. |
| **Privacy** | Smart Offline's ranking runs entirely on-device over data BitChord already collects. Nothing new leaves the device. `ListeningStats` is already exportable only at the user's request (`data/stats/Backup.kt`). The feature should add no network transmission of listening data — and should say so in the UI. |
| **User consent** | The feature must be **opt-in** (default off), must state that it downloads audio automatically, and must let the user exclude tracks and disable it, with downloaded files removed on disable (or an explicit choice to keep them). |

---

# 16. Implementation roadmap

> Nine stages, each independently reviewable. **Nothing below has been implemented** — this is a plan.

### Stage 1 — Architecture audit ✅ *(complete: §8)*
- **Files:** none read-only.
- **Output:** this report.
- **Acceptance:** existing assets and gaps identified with file paths. **Done.**

### Stage 2 — Activity read-model
- **New files:** `app/src/main/java/com/music/bitchord/data/smartoffline/ActivityIndex.kt`
- **Changed files:** none (read-only over `ListeningStats`).
- **Dependencies:** none new.
- **Testing:** unit tests over synthetic `StoredBucket` fixtures — window boundaries, decayed plays, `last` selection across month buckets, unknown-duration tracks.
- **Risks:** `ListeningStats` keeps only the current month in memory; older months are files — I/O cost.
- **Acceptance:** `ActivityIndex.build(28)` returns correct `plays/ms/last/decayedPlays` for a fixture set spanning three months; no mutation of `ListeningStats` state.

### Stage 3 — Ranking engine (**the MVP core**)
- **New files:** `.../smartoffline/OfflineTrackSelector.kt` (+ `SelectionStrategy`, `SelectorConfig`, `ScoredTrack`)
- **Changed files:** none.
- **Dependencies:** Stage 2's `TrackActivity`.
- **Testing:** extensive pure unit tests — strategy weightings, percentile normalisation, the small-N fallback, tie-breaking determinism, empty and single-candidate candidate sets, rotation-variety allocation, unknown-duration engagement fallback, cold-start (no data) behaviour. *(Hysteresis, sticky-core retention and exclusion/pin penalties are **not** tested here — they belong to the download-management phase, Stage 5-6.)*
- **Risks:** the weights are a judgement call; they need to be *tunable*, and the UI should eventually expose them.
- **Acceptance:** given a fixed activity set, `select()` returns a deterministic, documented ranking; every returned track carries the reasons for its selection.

### Stage 4 — Track resolution and matching
- **New files:** `.../smartoffline/SmartOfflineResolver.kt` (a thin composition of `TrackMatcher` + `SourceResolver`)
- **Changed files:** none expected — this should be an *adapter*, not a rewrite.
- **Dependencies:** `shared/.../sources/TrackMatcher.kt`, `app/.../sources/SourceResolver.kt`, `SourceRegistry.activeForDownload()`.
- **Testing:** a resolver test over a stubbed `MusicSource` that returns no match (`null` from `stream()`) to prove the miss path, and a "wrong recording" case (remix vs. original) to prove the veto.
- **Risks:** `SourceResolver` is 1552 lines and complex; the adapter must stay thin.
- **Acceptance:** a Spotify-sourced candidate resolves to a downloadable `Song` or is cleanly reported as unmatched, with no new matching logic.

### Stage 5 — Automatic download orchestration
- **New files:** `.../smartoffline/SmartOfflineManager.kt`, `.../smartoffline/SmartOfflineStore.kt`, `app/.../download/SmartOfflineWorker.kt`
- **Changed files:** `app/src/main/java/com/music/bitchord/BitChordApplication.kt` (init + schedule, mirroring `PlaylistSync.init` at L168).
- **Dependencies:** Stage 3, Stage 4, existing `Downloads` + `DownloadService`.
- **Testing:** unit tests for the diff logic (desired vs. downloaded → toDownload / toEvict); an instrumentation test that a refresh with no network enqueues nothing; a test that the free-space check refuses to enqueue below the threshold.
- **Risks:** R-TECH-1..5, R-TECH-7.
- **Acceptance:** with the feature on and a simulated activity set, a refresh enqueues exactly the missing tracks, evicts only owned-and-fallen-out tracks, and is a no-op when re-run (idempotent).

### Stage 6 — Storage and cleanup management
- **New files:** extend `SmartOfflineStore` with ownership, suppression, pins.
- **Changed files:** `app/.../download/DownloadStore.kt` (add a `StatFs`-based free-space helper — the first in the codebase).
- **Dependencies:** Stage 5.
- **Testing:** budget-enforcement tests (over-budget → evicts lowest-scoring owned tracks only); "never evicts user-owned files"; "never evicts excluded-but-pinned files".
- **Risks:** R-TECH-2, R-TECH-10.
- **Acceptance:** total Smart-Offline bytes never exceed the configured budget; user-downloaded files are never deleted by Smart Offline.

### Stage 7 — Native Android UI
- **New files:** `app/.../ui/screens/SmartOfflineScreen.kt`, `app/.../ui/screens/SmartOfflineSettings.kt`
- **Changed files:** `app/.../ui/screens/SettingsSheet.kt` (settings), `app/.../data/OfflinePlaylists.kt` (virtual page), `app/.../ui/navigation/AppNavigator.kt` (route), `app/.../MainActivity.kt` (route rendering), `app/src/main/res/values/strings.xml` (strings).
- **Dependencies:** Stages 5-6.
- **Testing:** Compose UI tests for the settings toggles; a screenshot test for the ranked list.
- **Risks:** low.
- **Acceptance:** the user can toggle the feature, choose a strategy and budget, see the ranked selection with per-track reasons, exclude/pin a track, and force a refresh — all without leaving the app.

### Stage 8 — Background refresh
- **New files:** none (the worker exists from Stage 5); this stage *schedules* it.
- **Changed files:** `app/.../download/SmartOfflineWorker.kt`, `app/.../BitChordApplication.kt`.
- **Dependencies:** Stage 5.
- **Testing:** verify the periodic work is enqueued with the right interval/flex/constraints; verify the foreground catch-up path; verify that turning the feature off cancels the work and that WorkManager is never touched while it is off.
- **Risks:** R-TECH-7.
- **Acceptance:** with the feature on, a refresh runs on the schedule and on foreground-if-stale; with it off, no work is scheduled.

### Stage 9 — Testing and optimization
- **Files:** tests across `app/src/test/java/com/music/bitchord/` and `app/src/androidTest/java/com/music/bitchord/`.
- **Focus:** end-to-end soak test (100 simulated days of listening → assert bounded churn), ActivityIndex I/O cost, memory of the merged index, battery impact of the 6 h cycle, matcher accuracy on a corpus of ambiguous titles.
- **Acceptance:** churn bounded (≤ N downloads/day in steady state), no ANR, no disk-full in a long soak.

---

# 17. Recommended MVP — the first pull request

## **PR #1 — "Smart Offline: selection engine + visible rotation"** *(no automatic downloads)*

**Why this is the right first PR:**

- It delivers the **user-visible payoff** — "BitChord knows what I listen to" — with **zero** new download infrastructure, **zero** new storage risk, **zero** new Spotify dependency, and **zero** manifest changes.
- It contains **the entire intellectual core** of the feature: the ranking algorithm, in a pure, exhaustively unit-testable class. Everything after this is plumbing around it.
- It is **reviewable in one sitting** — a pure function plus its tests plus a read-only screen.
- It is **low-risk**: it reads `ListeningStats` and shows a list. It cannot fill a disk, cannot download anything, and cannot delete anything.
- It produces the **exact data** needed to calibrate the weights before the download machinery is built — real usage shows whether the ranking feels right.

### Scope of PR #1

| Item | Detail |
|---|---|
| **New** | `data/smartoffline/ActivityIndex.kt`, `data/smartoffline/OfflineTrackSelector.kt`, `ui/screens/SmartOfflineScreen.kt` |
| **Changed** | `data/settings/AppSettings.kt` (strategy + enabled + window settings), `data/OfflinePlaylists.kt` (virtual page), `ui/navigation/AppNavigator.kt` + `MainActivity.kt` (route), `ui/screens/SettingsSheet.kt` (a section), `res/values/strings.xml` |
| **Not in scope** | Automatic downloading; file deletion or eviction; WorkManager or any background worker; the ownership store; the exclusion store; the pinning store; the queue boost; storage quota or `StatFs`; hysteresis / retention decisions; Spotify OAuth, API or cookie changes |
| **Tests** | Pure unit tests for `OfflineTrackSelector` (≥ 30 cases: the three strategies, percentile normalisation, the small-N fallback, deterministic tie-breaking, empty and single-candidate sets, unknown-duration engagement fallback, component weighting); unit tests for `ActivityIndex` over synthetic buckets |
| **Dependencies added** | **None** |
| **Manifest changes** | **None** |
| **Risks** | Minimal — read-only. The only risk is `ActivityIndex` I/O cost on app open, mitigated by caching and doing it on `Dispatchers.IO`. |

### Acceptance criteria

1. The **Smart Offline** page lists the user's rotation, each track showing *why* it was selected ("played 12 times in the last 28 days · last played 3 days ago"). **Any non-empty candidate set is supported** — a history of one track yields one row, and a history of two hundred yields two hundred. There is no minimum track count and no minimum window.
2. The strategy selector changes the ordering immediately and deterministically.
3. **Nothing is downloaded, nothing is deleted, no storage is consumed**, and the page says so in plain language.
4. The page states explicitly that this phase **previews a personalised rotation and does not automatically download tracks**.
5. ≥ 30 unit tests cover the ranking, all passing; the whole selection runs in < 10 ms for a 5 000-track activity set.
6. Only durable local signals define the rotation: listening history and persisted local likes. Session-scoped signals (YouTube ratings) are not used as a durable preference input.

## Then, in order

- **PR #2** — "Smart Offline: automatic downloads" (Stages 4-6 + 8): the worker, the manager, ownership, eviction, the `StatFs` check.
- **PR #3** — "Smart Offline: polish" (Stage 7): full settings surface, per-track "why" sheet, one-tap "download now", "clear Smart Offline downloads".
- **PR #4** — "Offline Ready" (the Spotify-Offline-Backup analogue): a zero-cost page of tracks already buffered/downloaded that match recent activity, working fully offline.

---

# 18. Exact files likely to require changes

> Paths are repo-relative to `/home/alen/BitChord`. "New" = does not exist today.

### New files

| Path | Stage | Purpose |
|---|---|---|
| `app/src/main/java/com/music/bitchord/data/smartoffline/ActivityIndex.kt` | 2 | Read-model over `ListeningStats` → per-track windowed stats **[PR #1]** |
| `app/src/main/java/com/music/bitchord/data/smartoffline/OfflineTrackSelector.kt` | 3 | **Pure** ranking engine **[PR #1]** |
| `app/src/main/java/com/music/bitchord/data/smartoffline/Preferences.kt` | 3 | Durable-only preference lookup (persisted local likes + playlists); deliberately excludes the session-scoped `LikeState` **[PR #1]** |
| `app/src/main/java/com/music/bitchord/data/smartoffline/SelectionStrategy.kt` | 3 | Strategy enum + weights + config (or co-located in the above) **[PR #1]** |
| `app/src/main/java/com/music/bitchord/data/smartoffline/SmartOfflineManager.kt` | 5 | Orchestrator: diff → enqueue → evict **[PR #2+]** |
| `app/src/main/java/com/music/bitchord/data/smartoffline/SmartOfflineStore.kt` | 5-6 | Ownership, exclusions, pins, suppression, last-run state **[PR #2+]** |
| `app/src/main/java/com/music/bitchord/data/smartoffline/SmartOfflineResolver.kt` | 4 | Thin adapter: `TrackMatcher` + `SourceResolver` **[PR #2+]** |
| `app/src/main/java/com/music/bitchord/download/SmartOfflineWorker.kt` | 5, 8 | `CoroutineWorker` beside `PlaylistSyncWorker` **[PR #2+]** |
| `app/src/main/java/com/music/bitchord/ui/screens/SmartOfflineScreen.kt` | 7 | The ranked rotation + per-track reasons **[PR #1]** |
| `app/src/main/java/com/music/bitchord/ui/screens/SmartOfflineSettings.kt` | 7 | Settings surface (or a section in `SettingsSheet.kt`) **[PR #1]** |
| `app/src/test/java/com/music/bitchord/data/smartoffline/OfflineTrackSelectorTest.kt` | 3 | The core unit tests **[PR #1]** |
| `app/src/test/java/com/music/bitchord/data/smartoffline/ActivityIndexTest.kt` | 2 | Window/decay tests **[PR #1]** |

### Modified files

| Path | Stage | Nature of change |
|---|---|---|
| `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt` | 3, 7 | Add ~6 settings following the existing `MutableStateFlow` + key + load/save pattern (cf. `wifiOnlyDownloads` L237/L972/L1286, `syncDownloadedPlaylists` L252/L974/L1836) **[PR #1]** |
| `app/src/main/java/com/music/bitchord/data/OfflinePlaylists.kt` | 7 | Add a built-in virtual `SMART_ID` entry beside `LIKED_ID` (L53) with dynamic contents **[PR #1]** |
| `app/src/main/java/com/music/bitchord/ui/navigation/AppNavigator.kt` | 7 | Add `data object SmartOffline : AppRoute` beside `Downloads` (L29) — **optional**; the `SettingsSection`/`SettingsChoice` routes already exist, so a settings page needs no new route **[PR #1]** |
| `app/src/main/java/com/music/bitchord/MainActivity.kt` | 7 | Render the route beside `AppRoute.Downloads` (L3071-3075, L4112-4113) **[PR #1]** |
| `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt` | 7 | Add a `SettingsChoiceKind` entry (L2849) and a section in the `DOWNLOADS`/`STORAGE` categories (L2820) **[PR #1]** |
| `app/src/main/java/com/music/bitchord/BitChordApplication.kt` | 5, 8 | `SmartOfflineManager.init(...)` + schedule, mirroring `PlaylistSync.init` (L168) and `ListeningStats.init` (L172) **[PR #2+]** |
| `app/src/main/java/com/music/bitchord/download/DownloadStore.kt` | 6 | Add a `StatFs` free-space helper (the codebase's first) **[PR #2+]** |
| `app/src/main/res/values/strings.xml` | 7 | New strings (cf. the existing `R.string.downloads`, `R.string.download_quality`) **[PR #1]** |
| `app/build.gradle.kts` | — | **No change** — WorkManager 2.10.1 is already present (L304) |

### Files that should **not** need to change

| Path | Why |
|---|---|
| `app/src/main/java/com/music/bitchord/data/stats/ListeningStats.kt` | Read-only dependency; `ActivityIndex` is the adapter |
| `app/src/main/java/com/music/bitchord/data/stats/ListeningRecorder.kt` | Already produces the right signals |
| `app/src/main/java/com/music/bitchord/download/Downloads.kt` | `enqueue` already does the work |
| `app/src/main/java/com/music/bitchord/download/DownloadService.kt` | Already drains the queue |
| `app/src/main/java/com/music/bitchord/download/PlaylistSync.kt` | The template, not a dependency |
| `shared/src/commonMain/kotlin/com/music/bitchord/data/sources/TrackMatcher.kt` | Reused as-is |
| `app/src/main/java/com/music/bitchord/data/sources/SourceResolver.kt` | Reused as-is |
| `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/spotify/*` | Reused as-is — **no new Spotify endpoints** |
| `app/src/main/AndroidManifest.xml` | **No change** — `FOREGROUND_SERVICE_DATA_SYNC` (L12) and `POST_NOTIFICATIONS` (L13) are already declared |

---

# 19. Open questions requiring further investigation

| # | Question | Why it matters | How to resolve |
|---|---|---|---|
| Q1 | What is the **exact** recency window and play-count threshold Spotify's Offline Backup uses? | BitChord mirrors the documented "> 5 songs" gate; the window is unknown. | Only observable by instrumenting a real Spotify client with a prepared history — i.e. the blocked Phase 2 work (needs an APK + JADX + a device). Until then, treat as **UNKNOWN** and treat BitChord's 28-day window as a design choice, not a Spotify-derived fact. |
| Q2 | Does Spotify's Offline Backup use **listening duration** as well as play count? | Would change the `E` weight. | Same as Q1. Currently **UNKNOWN**. |
| Q3 | Does Spotify's Offline Backup consider **likes / playlist membership** for membership (as opposed to only the "Recommended" sort)? | Would decide whether BitChord's `P` weight should be higher. | Same as Q1. **UNKNOWN**; BitChord's `P` is a deliberate design choice, not a Spotify-derived fact. |
| Q4 | What is the **maximum size** of the Offline Backup playlist, and how are tracks evicted from it? | Affects the default `maxTracks`. | Not published. **UNKNOWN**. BitChord's 50 is a design choice. |
| Q5 | Is Offline Backup available on **desktop**? | Secondary press suggested a desktop "Offline Mix" counterpart; official docs say mobile + Android Auto + CarPlay. | Official documentation is authoritative → **DOCUMENTED as mobile-only**. The desktop counterpart's status is **UNKNOWN**. |
| Q6 | What is the "Offline Mix" feature's final status? | Secondary press suggested it was superseded by Offline Backup. | Only secondary sources → **INFERRED**. Spotify's own documentation covers Offline Backup only. |
| Q7 | Can BitChord read Spotify's **`fetchLibraryTracks`** reliably, and what happens when the pathfinder hash changes? | Liked Songs is a cold-start seed and a preference signal. | **Test empirically** against a real account (in-app, read-only) and observe breakage over time. |
| Q8 | Does `TrackMatcher` reliably resolve Spotify tracks to the *same recording*? | A wrong-recording download is the most user-visible defect. | Build a corpus: take N Spotify Liked Songs, resolve each through `TrackMatcher`, and have a human check title/artist/duration/album against the Spotify row. Quantify the mismatch rate. |
| Q9 | What is the **real** storage cost of a 50-track rotation at each `downloadQuality`? | The byte budget default depends on it. | `DownloadQuality` already carries size hints (~4 / ~8 / ~35 MB) — verify them empirically against actual downloads. |
| Q10 | Does `ListeningStats` record **source-backed** rows (`src:<configId>::<trackId>`) and local files? | If not, tracks from addons/modules/JioSaavn may be invisible to Smart Offline. | Read `ListeningRecorder.enriched(song)` (L85) and check what is written for a `src::` row. |
| Q11 | What is the **actual** relationship between `Downloads.saved` keys and `ListeningStats` track ids for the same song? | They must agree for "already downloaded ⇒ satisfied" and for "play this again" to line up. | Verify the id shapes end-to-end: `song.videoId` (YouTube id) vs `src:<configId>::<trackId>` vs `sp:<spotifyId>`. |
| Q12 | Is a `StatFs` check sufficient, or should the budget be measured against the *app's own* folder? | Different scope gives different answers. | Decide and document: the budget should cover **all** of BitChord's downloads (user + Smart Offline), since they share the same directory. |
| Q13 | Should Smart Offline's downloads be **exported to MediaStore** (user-visible, other apps can see them) or kept app-private? | `exportDownloads` already exists as a global switch. | Follow the user's existing global preference; do not introduce a second, conflicting one. |
| Q14 | How should Smart Offline behave when the user's **Spotify session expires**? | Liked Songs / playlist seeds stop working. | Degrade gracefully: local history and local likes must suffice. Test the offline path explicitly. |
| Q15 | Is there any Spotify **ToS** exposure in *using* the account's library metadata to drive downloads from a *third-party* source? | The design deliberately never downloads Spotify audio, which is the key mitigation. | A legal review of the Spotify Developer Policy and the Spotify Premium Terms with respect to the existing pathfinder usage. **Out of engineering scope — flagged, not answered.** |

---

# Terminal summary

**What Spotify Offline Backup actually does.** It is a *cache-backed fallback playlist*, not a downloader. Spotify assembles it from your **queued** and **recently streamed** tracks whose audio is **already in the device cache**, and surfaces it in the Home feed **only when you are offline**. It costs **zero extra data and zero extra storage**, requires Premium, works on mobile / Android Auto / CarPlay, needs the "Offline listening" toggle on, and only appears once you have "played enough songs recently" (documented as **more than five**). It can be saved to your Library and sorted by **Title, Artist, Album, Frequently played, Recommended**, and it disappears if you clear the cache, reinstall, run out of storage, or haven't played enough. It is **not** the same feature as Spotify's manual Downloads (10 000 tracks/device, 5 devices, 30-day online re-validation) nor as podcast auto-download.

**What is known about its selection mechanism.** Documented: recency (recently streamed) + queue + a cache-eligibility filter + a >5-track threshold, and the existence of a **"Frequently played"** and a **"Recommended"** ordering. Everything else — the recency window, the frequency formula, whether duration, likes or playlists contribute, the maximum size, the eviction rule, the refresh cadence — is **UNKNOWN**: the ranking is computed **server-side** and no client artifact or public API exposes it.

**What Spotify exposes through its APIs.** Publicly: `GET /me/player/recently-played` (≤50/page, timestamp-cursored), `GET /me/top/{type}` (pre-aggregated affinity ranks, ~4w/6m/all-time), `GET /me/tracks` (Liked Songs), `GET /me/playlists`, `GET /playlists/{id}/items`, `GET /me/player/queue` (live only). **Never** exposed: lifetime play counts, complete listening history, listening duration, engagement, or the Offline Backup score. In **Feb 2026** Spotify removed `GET /tracks` (batch), `GET /users/{id}/playlists`, `GET/users/{id}`, `GET/artists/{id}/top-tracks`, `GET/markets`, `GET/browse/*`, `GET/recommendations`, audio-features/analysis, and the `popularity` and `product` fields; it cut `search`'s `limit` max to 10; **March 2026** reverted the `external_ids` removal. **Crucially: BitChord uses none of this** — it has no OAuth and no scopes; it replays the user's `sp_dc` web cookie and calls Spotify's internal `pathfinder` GraphQL for playlists and Liked Songs only.

**What remains unknown.** Spotify's ranking algorithm and every numeric parameter behind it. Whether duration, likes or playlists feed membership. The playlist size cap and eviction rule. Whether it is available on desktop. The final status of "Offline Mix". Whether static analysis would even help — the playlist is assembled by Spotify's backend, so **no client artifact can answer this**, which is why Phase 2 is reported as blocked rather than approximated.

**What BitChord already supports.** A complete on-device listening history (`ListeningStats`/`ListeningRecorder`: per-track `ms`, `plays`, `last`, and per-day breakdowns, in monthly JSON buckets, never leaving the device) — which is *better* than anything Spotify's public API exposes. A mature download pipeline (`Downloads.enqueue` → `DownloadService` FGS with 4 workers → `DownloadStore` → `MediaTagger`), MediaStore/app-private destinations, `Music/BitChord` layout, duplicate suppression, artwork/metadata/lyrics embedding. A strong matching engine (`TrackMatcher`, `SourceResolver`, `SourceRegistry` with YouTube/addon/module/JioSaavn). A periodic-download template (`PlaylistSync` + `PlaylistSyncWorker`: unique work, flex window, Wi-Fi/battery/storage constraints, foreground catch-up, idempotent re-derivation). Local playlists + a Liked list (`OfflinePlaylists`), Spotify playlist/Liked Songs import (`SpotifyLibrary`), and a full Compose settings surface. `minSdk 26 / targetSdk 36`, `FOREGROUND_SERVICE_DATA_SYNC` already declared, `androidx.work:work-runtime-ktx:2.10.1` already a dependency. **Not supported:** any relational database, **any free-space check** (zero `StatFs` hits), any download quota, any automatic cleanup, any track-exclusion store, any second WorkManager worker, and any Spotify listening-history access.

**What must be implemented.** An activity read-model; a pure ranking engine; a resolution adapter; an auto-download orchestrator plus a WorkManager worker; an ownership/exclusion/suppression store; a `StatFs` free-space check; a byte budget with hysteresis-based eviction; a settings UI; and a virtual "Smart Offline" page in the library.

**Recommended architecture.** `ListeningStats` → `ActivityIndex` → `OfflineTrackSelector` (**pure**) → `SmartOfflineManager` → `Downloads.enqueue()` (reuse) → existing `DownloadService`; with `SmartOfflineWorker` (`CoroutineWorker`, 6 h periodic + 1 h flex, Wi-Fi/battery/storage constraints, foreground catch-up) scheduling the refresh, `SmartOfflineStore` (SharedPreferences) holding ownership/exclusions/pins, and the selection surfaced through `OfflinePlaylists` next to "Liked". **No new downloader, no new database, no new Spotify endpoints, no manifest change.**

**Biggest technical blocker.** Not Spotify's opacity and not Android's background limits — it is that **`ListeningStats` is an aggregate store with no play-event log** ("the raw plays are gone"), so recency is only exact to the last play and frequency is only day-granular within the window. Everything else (matching, downloading, scheduling, storage) has solid existing infrastructure to build on. Second-biggest is the **absence of any free-space check**, which must be added before a feature that can autonomously fill a disk.

**Recommended first pull request.** **PR #1 — "Smart Offline: selection engine + visible rotation."** Add `ActivityIndex` + the pure `OfflineTrackSelector` + a read-only Smart Offline page with per-track explanations, behind a settings toggle; **no downloading, no eviction, no worker, no ownership/exclusion/pinning store, no queue boost, no new dependencies, no manifest changes**, with ≥ 30 unit tests over the ranking. It delivers the user-visible payoff ("BitChord knows what I listen to"), contains the entire intellectual core of the feature in testable form, carries essentially no risk, and produces the real-world data needed to calibrate the weights before the download machinery is built.

**The precedent is already in the codebase:** `MainViewModel.localTaste()` → `TopPicks.LocalTaste` already personalises a recommendation surface from the same local listening statistics. Smart Offline extends a shipped pattern to a second surface rather than inventing a new one — and the rotation is defined by **durable local signals only** (listening history and persisted local likes), never by session-scoped or remotely fetched data.

---

