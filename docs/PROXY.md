# Proxy support

BitChord can send its HTTP and HTTPS traffic through an HTTP or SOCKS5 proxy.
The setting is under **Settings → Network → Proxy**. A per-source override is
available from each user-added addon's row on **Settings → Sources**.

## What is routed

The app uses one OkHttp client, [`Http.client`](../shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/Http.kt),
and every network path that matters derives from it:

- addon manifest, search, metadata and stream requests;
- module index and JS downloads, plus the API and CDN hosts those modules
  fetch from inside their sandbox;
- YouTube Music / InnerTube metadata and stream resolution;
- JioSaavn;
- audio streaming and downloads (ExoPlayer's `OkHttpDataSource`);
- artwork (Coil);
- lyrics providers, scrobbling, party sync and Discord REST/gateway calls.

When the proxy is disabled, the selector answers `Proxy.NO_PROXY` and the app
connects directly exactly as before.

## Protocols

| Setting | Behaviour |
| --- | --- |
| **HTTP** | Sends HTTP requests to the proxy and opens HTTPS destinations with `CONNECT`. This is the usual choice for a Squid, Privoxy, mitmproxy or home-router proxy. |
| **HTTPS** | Kept as a distinct choice, but Android's network stack cannot open a TLS connection *to the proxy itself*. OkHttp therefore treats it as an HTTP proxy; HTTPS destinations still work through `CONNECT`. Use **HTTP** unless you know your proxy expects a plaintext proxy connection. |
| **SOCKS5** | Uses a SOCKS5 proxy for both HTTP and HTTPS. Username/password authentication is supported. |

HTTP proxy authentication uses the standard `Proxy-Authorization: Basic`
challenge/response. SOCKS5 authentication is performed by the JDK's SOCKS
socket implementation.

## Per-source overrides

A user-added addon or module source can choose:

- **Use app proxy** — follow the global setting (default);
- **Direct connection** — always bypass the proxy, even when the global proxy is
  on;
- **Custom proxy** — use a different endpoint and credentials for this source.

The addon's own host is registered when the source list changes. When the addon
resolves a stream URL, that host is registered too, so the audio fetch follows
the same decision as the request that produced it. A module source also registers
every host its JS fetches, and its index and JS downloads are made with the
source's route in scope, so a redirect to a CDN does not fall back to the global
setting mid-call.

A host no source has claimed still follows the global setting. That includes a
redirect followed by an addon server request, which is outside the app's control
once the server answers.

Built-in sources (YouTube Music and JioSaavn) follow the global setting. The
per-source override is for user-added sources.

## Failure behaviour

A configured proxy is never silently bypassed:

- An enabled global proxy with a missing or invalid host/port makes requests fail
  instead of connecting directly.
- A **Custom** route whose endpoint is disabled or invalid also fails closed.
- A proxy that is down, rejects the credentials, or cannot reach the destination
  surfaces as a normal network error. **Test connection** reports whether the
  proxy was reached and distinguishes a rejected login from an unreachable
  endpoint.

Changing the global proxy or a source's route evicts pooled connections, so a
keep-alive socket opened through the old route cannot carry the next request
around the new one.

## Credentials

The global password is stored in the app's encrypted credential store
(`AuthStore`), not in plain-text preferences. A per-source password is stored in
the encrypted source configuration file. Both `ProxyConfig` and `SourceProxy`
redact credentials from `toString()` so an accidental log line cannot leak them.

## Limitations

- **Remote addon servers.** The Android app can proxy its request *to* the addon
  server and the audio URL *from* it. Requests the addon server makes to a music
  service happen on that server and are outside this app's control.
- **TLS to the proxy.** Not supported by Android/OkHttp. See the protocol table.
- **WebView traffic.** Google sign-in, Spotify authorization and the YouTube
  PoToken WebView use Android's WebView network stack, which follows the
  platform proxy rather than this app setting.
- **No PAC or bypass list.** Every HTTP/HTTPS host follows the global setting
  unless a registered source route says otherwise.
- **DNS.** The proxy hostname is resolved on the device. Destination resolution
  is left to the proxy where the protocol allows it (HTTP proxies always
  resolve the destination; SOCKS5 may receive a hostname), so a proxy can reach
  a service that the device's own DNS cannot.

## Testing

The proxy model, routing table and tester have JVM tests under
`shared/src/jvmTest/kotlin/com/music/bitchord/data/proxy/`. They cover:

- hostname and port validation, including IPv4 and IPv6 literals;
- global, inherit, direct and custom routing;
- stream-host registration and case-insensitive host matching;
- fail-closed invalid endpoints;
- HTTP proxy authentication, including per-source credentials at a shared
  endpoint;
- SOCKS5 with and without username/password;
- a rejected login, an unavailable proxy and a direct connection.
