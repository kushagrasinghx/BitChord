# YouTube Music sign-in (phase 5)

Prompt §5.3. There's no login screen: BitChord uses the YouTube session the browser already has, through the extension. The cookies and the signature never reach the page; it only gets names, pictures and InnerTube responses.

## How a signed-in request travels

1. YouTube.js runs in the page as a second instance (`getSignedInnertube`, `app/src/playback/youtube/innertube.ts`). It is marked `logged_in` so its library calls aren't refused, but it holds no cookie and adds no credentials.
2. Its InnerTube calls go through the bridge as `yt.fetch` with `auth: true`. Everything else (the session bootstrap, the player script) loads like the anonymous instance's.
3. The extension (`extension/lib/ytFetch.ts`, `ytAuth.ts`):
   - accepts only `https://(music|www).youtube.com/youtubei/v1/*`;
   - drops any identity headers the page sent;
   - adds `Authorization: SAPISIDHASH <ts>_<sha1(ts SAPISID origin)>` (as `DesktopYouTubeAuth.kt`), `X-Goog-AuthUser`, `X-Goog-PageId` for a brand channel and `X-Origin`. That single hash is enough: in the user's Chrome it got the same signed-in answers as `SAPISIDHASH … SAPISID1PHASH … SAPISID3PHASH …`;
   - fetches with `credentials: 'include'`, so the browser attaches the cookies itself. A session rule for the extension's own requests (tabId −1) sets the YouTube Origin and Referer the signature was computed for.
4. `onBehalfOfUser` isn't sent. The desktop app found a stale `dataSyncId` answered 400 on every request, and `X-Goog-AuthUser` plus `X-Goog-PageId` already pick the identity.

## Accounts and channels

- **Logins:** a browser can hold up to ten Google logins, addressed by `X-Goog-AuthUser`, and the live ones needn't be contiguous. In the user's Chrome, 1, 2 and 4 were signed in and 0 and 3 had expired (`account_menu` answers 403 for those). So every index from 0 to 9 is tried, and a login counts only when `account_menu` returns an account header. `accounts_list` alone isn't trusted: it answers even for an expired index, with another login's channels. Taking that at face value made BitChord act as dead login 0, so everything came back signed out.
- **Channels:** `account/accounts_list` for each live login gives its own channel and its brand channels (`pageId` from the item's tokens). If that call fails, the login's own channel stands in.
- **Default:** the login YouTube Music's own page uses: `SESSION_INDEX` from the music.youtube.com HTML (4 for the user, not 0). Within it, the channel YouTube has selected (`isSelected`). The user's choice is stored in `storage.local` (`yt.prefs`).
- **Caching:** the list is cached for 10 minutes. A change to a session cookie (SAPISID, SID and their `__Secure-` forms; not SIDCC, which rotates constantly) clears it, re-reads, and pushes the new state.
- **Disconnect** (sidebar account menu, or the popup) stops BitChord using the session. The user stays signed in to Google.

## Pushes

`Bridge.subscribe`:
- On Chromium the page opens an `externally_connectable` port (`bitchord-events`).
- On Firefox the relay content script holds the port and forwards each event with `postMessage`.

An MV3 worker drops its ports when it sleeps, so ports reconnect only while the page is visible. The page also re-reads the state when the tab regains focus.

When the identity changes, the page resets every `['music', …]` query and the resolver's verdicts (`useYtAuth.adopt`).

## What's signed in

| Feature | How |
|---|---|
| Home, search, album, artist, playlist | The signed instance whenever signed in (personal home feed, private playlists) |
| Library | `/library/:section`: `FEmusic_history`, `…_library_corpus_track_artists`, `…_liked_albums`, `…_liked_videos`, `…_mixed_for_you`, `…_liked_playlists`, with continuations |
| Favorite | `/like/like` and `/like/removelike` on the videoId. State from `/next`'s `likeButtonRenderer` |
| Add to Library | The header's ToggleButton target: `/like/like` on the album's or playlist's list id |
| Add to Playlist | `/playlist/get_add_to_playlist`, then `/browse/edit_playlist` `ACTION_ADD_VIDEO` |
| Age-restricted playback | An age-gate refusal anonymously makes the engine ask again signed in: YTMUSIC, then WEB, then VISIONOS (the phone uses WEB_REMIX with cookies) |

## Verified, and not yet

- **Verified signed out** (Playwright Chromium and stock Firefox): the auth state, the refusal codes (`unauthenticated` signed out, `forbidden` off-host), pushes over both transports, the update notice.
- **Verified in Firefox with a planted first-party cookie:** the extension reads the session and signs requests.
  - Testing note: puppeteer's `page.setCookie` stores a partitioned cookie that the extension can't see. Set test cookies with `document.cookie` on a youtube.com page instead.
- **Verified in the user's Chrome (Claude in Chrome, 0.2.4)**, reads only:
  - the account, name and picture;
  - every library page (songs, albums, artists, history, Made for You, playlists);
  - the like state (`LIKE` for Liked Music tracks). Library "Songs" are saved songs, not liked ones, so they read `INDIFFERENT`;
  - the add-to-playlist list. YouTube's list has no "already contains" flag, and its Liked Music entry is a like, so it's filtered out;
  - an album's in-library state.
- **Dropped:** two changes made while chasing the expired-login bug did nothing and were reverted: the three-hash signature, and setting the Cookie header from the jar through a session rule. Chrome attaches the cookies to the worker's `credentials: 'include'` requests itself.
- **When the account can't be read**, the extension says why: an HTTP status, or YouTube's own `logged_in=0` from `responseContext.serviceTrackingParams`. The popup shows it, the Extension page too, and the sidebar shows a Retry pill.
- **Still to check signed in:** the writes (Favorite, Add to Library, Add to Playlist), not tried because they change the account; channel switching, and age-restricted playback (and whether the signed web clients' streams outlast ~60 s, given the WebPO problem in `youtube-playback.md`).
