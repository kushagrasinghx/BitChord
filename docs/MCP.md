# Android MCP access (experimental)

BitChord's Android app provides a stateless MCP Streamable HTTP endpoint at `http://127.0.0.1:8765/mcp`. Protocol versions: `2025-06-18`, `2025-03-26`. This is not the legacy SSE transport. No separate desktop bridge or account credentials are required for the MCP connection.

## Setup

1. Install the **dev** APK from the **MCP Debug APK** GitHub Actions artifact. It installs as `com.dev.bitchord`, alongside the production app. Its library/settings are separate.
2. Open Settings → Miscellaneous → MCP.
3. Enable MCP, explicitly enable the required permissions, and select the local playlists the agent may access.
4. Copy the bearer token into your MCP client's custom `Authorization` header. Configure **Streamable HTTP**, URL `http://127.0.0.1:8765/mcp` and `Authorization: Bearer <token>`.
5. When enabled from the visible app, MCP runs with a persistent foreground-service notification. This reduces background suspension but does not bypass Doze, vendor battery restrictions or force-stop. Reopen the app if disconnected.

Loopback works for agents on the same phone. For a desktop, prefer `adb forward tcp:8765 tcp:8765` and connect to the desktop's `127.0.0.1:8765`. Optional LAN mode binds all IPv4 interfaces and requires using the phone's LAN IP. **HTTP is unencrypted: do not use on untrusted Wi-Fi, forward this port to the internet, or publish the token.** Clients sending browser `Origin` headers are rejected deliberately. Token rotation invalidates the old credential.

## Permissions and tools

Each tool call checks permissions; hiding a tool in discovery is not the security boundary. Defaults deny every permission. One token shares one policy; per-agent credentials are not implemented yet.

- `now_playing`: current track, playback state, position and duration.
- `history`: device-local `listening_history` and YouTube Music `account_history`.
- `queue`: `current_queue`, including entries retained before the current index. The player retains a bounded history window, not every track ever played.
- `playback`: `play` (catalogue video ID or omit to resume), `pause`, `resume`.
- `seek`: `seek` with `position_ms` (e.g. 90000 = 1:30).
- `search`: song `search` with `query`.
- `playlist_read`: `playlist_list`, `playlist_read`, only for explicitly selected local playlists.
- `playlist_create`: create an empty local playlist. MCP-created playlists automatically receive playlist access in both modes; global read/write permissions still apply.
- `playlist_write`: `playlist_add` by `video_id`, `playlist_remove` by zero-based index, only on selected local playlists.

Remote YouTube/Spotify playlist modification is not implemented. Local listening history uses the existing persisted statistics: totals per track and latest listening time, **not a complete play-by-play event log**. Account history depends on the YouTube Music session. Playback accepts catalogue video IDs, not arbitrary URLs or device paths. Tool output deliberately omits source credentials, stream URLs and local filesystem paths.

## Smoke test

Never paste a real token into an issue or commit. Substitute the token locally:

```sh
curl -i http://127.0.0.1:8765/mcp \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1"}}}'
```

Use the same headers for `tools/list` and `tools/call`. Subsequent requests should include `MCP-Protocol-Version: 2025-06-18`. Notifications receive 202; GET/DELETE receive 405 because this server does not provide unsolicited SSE or stateful sessions.

Test checklist:

- Disabled server cannot connect; enabled server rejects missing/wrong token.
- With all permissions denied, `tools/list` is empty and direct tool calls fail.
- Enable only now-playing: no history/search/playlist tools or direct access.
- Allow playlist A but not B: B is absent and direct read/edit of B fails.
- Search a song, play its returned video ID, pause/resume, seek to 1:30.
- Read queue and both history sources; verify their documented semantics.
- Create a playlist, verify automatic playlist access, then add/remove a song. Disable global write permission and verify editing fails.
- Revoke permissions and rotate token while connected; verify subsequent calls fail.
- Disable LAN and verify another device cannot connect.

## Build

The `MCP Debug APK` workflow builds `:app:testDevDebugUnitTest` and `:app:assembleDevDebug` on branch `feat/android-mcp` or manual dispatch. Artifacts are named `bitchord-mcp-arm64-dev-<run-number>`. No release-signing secrets are needed. Fresh GitHub runners use a fresh debug signing key unless a stable debug keystore is configured; a later APK may require uninstalling an earlier dev build, which removes its app data. Never uninstall your production app to test this dev build.

MCP test CI builds only `arm64-v8a` and disables the bundled Wear OS installer (`-Pbitchord.arm64Only=true -Pbitchord.bundleWear=false`), reducing download size. Normal builds retain all ABIs.

Playlist access supports allowlist (only selected entries) and blocklist (all except selected entries, including future playlists). The default remains allowlist. Selections are stored separately for each mode. Switching to blocklist requires confirmation. MCP-created playlists are added to the allowlist and removed from the blocklist; the owner can revoke access afterwards. Address and token rows copy on tap.

Resilience: HTTP dispatch waits at most 16 seconds for backend work and returns 503 when timed out or saturated. Catalogue metadata uses known queue/allowed playlist/download/history/cache entries before network resolution. New searches still require a working network/DNS; cached-query fallback is marked explicitly.
