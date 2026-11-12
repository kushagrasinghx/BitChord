# Queue and player (phase 6)

## The queue

`app/src/playback/queue/timeline.ts` and `builder.ts` port the shared Kotlin rules (`QueueTimeline.kt`, `QueueSections.kt`, `QueueBuilder.kt`). `queue.test.ts` carries the Kotlin tests (QueueBuilderTest, QueueSectionsTest) plus cases for jumps and shuffle.

The queue has three tiers:

| Tier | What it is |
|---|---|
| `context` | The album, playlist, artist's top songs or library list the user started, or a one-off song |
| `user` | Tracks added with Play Next (head of the user section) or Add to Queue (its tail) |
| `autoplay` | YouTube Music's radio, appended when the queue runs low |

Rules, as on the phone:
- **Starting a list** puts the earlier tracks behind the picked one and keeps the user's upcoming queue right after it.
- **Tapping a row in Up Next** follows `computeJumpOrder`:
  - A context row is a seek, unless hand-queued tracks are skipped; those are pinned after the target instead.
  - A hand-queued row drops the hand-queued tracks it bypassed.
  - An AutoPlay row becomes the new context (a station pivot).
- **Clear** removes only the upcoming hand-queued tracks.
- **Shuffle:**
  - It never moves the user queue, and shuffles the context and AutoPlay each within their own section.
  - Turning it off restores the order the queue had.
  - A list started with shuffle on leads with the picked track.
- **Repeat all** prunes hand-queued tracks that have played, so only the context cycles.
- **Back** restarts the track after 10 seconds (`BACK_RESTARTS_AFTER_SEC`).

`QueueWindow.kt` isn't ported. It exists to feed Media3 a bounded window of a long queue; the web engine loads one track at a time, so the timeline is a plain array.

## AutoPlay

When fewer than three tracks are left (and repeat is off), the store asks for the last track's radio: `next` on `RDAMVM<id>`, as `Innertube.next` does. It's the signed-in user's station when signed in.

- `extendStation` keeps at most 10 per batch and de-duplicates by recording: normalised title plus a shared artist, so the audio and video cuts of a song don't both play.
- It caps any one artist at 2, or 4 for the seed's artist.
- It prefers the catalogue song over the music video. YouTube marks each row's `musicVideoType` (ATV is the song; OMV and UGC are videos).
- A radio seeded on a video id is all videos (measured on Despacito's OMV), and the builder falls back to those.
- A seed that adds nothing isn't asked again.

## Crossfade (two decks)

The store runs two `YouTubeEngine`s, each with its own `<audio>` element.

- Within the crossfade length of the end, the next track loads on the back deck at volume 0 and the store switches to it the moment its audio flows.
- The two then fade equal-power (cos/sin) over the time left.
- Pausing, seeking or choosing another track cancels a fade.
- If the next track can't load in time, playback carries on to the end and moves on normally.

Verified in Chromium and Firefox (Planet Her: the next track took over on the second deck and ended at full volume).

Lengths: off, 3, 6, 9 or 12 seconds, from the ⋯ menu in Up Next. Shuffle, repeat, AutoPlay, volume, mute and crossfade persist in `localStorage` (`bitchord:player`).

Known risk: googlevideo's SABR adapter registers Shaka's http(s) schemes globally. Two SABR streams at once (both decks on a web client mid-fade) could interfere. VISIONOS plays direct URLs, so it doesn't arise in practice today.

## UI

- **Player pill:**
  - Shuffle and Repeat (off → all → one) light in the key colour when on.
  - Previous and Next.
  - The LCD line seeks on press or drag; on hover it shortens to show elapsed and remaining time at its ends.
  - The speaker mutes, and hovering it shows the volume slider.
- **Up Next** (report §7):
  - "Playing from …"; 55px rows with a ✕ over the artwork and a ⋯ menu (Play Now, Play Next, Remove).
  - Drag to reorder.
  - Clear, the AutoPlay toggle (∞), and an AutoPlay heading where `autoplaySectionStart` puts it.
- **Track rows' ⋯:** Play Next and Add to Queue for everyone; Favorite and Add to Playlist when signed in.
- **Keyboard** (Apple's):
  - Space play/pause.
  - ⌘/Ctrl+→ and ← for next and previous.
  - ⌘/Ctrl+↑ and ↓ for volume.
  - ⌘/Ctrl+F or `/` for search.
  - Esc closes the panel.
- **Media Session:** previous and next track are wired up.

## Testing

Dev builds expose `window.__bcPlayer` (the store) and `window.__bcAudio` (the element in front). Scripts are in the scratch harness described in the testing memory.

Importing `/src/state/player.ts` from a test gets a separate copy of the module, not the app's store. Use the globals.
