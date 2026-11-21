//! Sign-in as a sheet over the player instead of a separate window.
//!
//! The page opens Google's sign-in with `window.open`, and the extension opens
//! Spotify's with `tabs.create`. Both arrive here and show as a BitChord modal
//! (shell/sign-in.html, drawn with upstream's tokens.css) with the provider's
//! own page inside it, untouched. It is a webview in this same profile, so the
//! session lands in the cookie jar the extension reads, and the sheet closes
//! itself once the provider hands back to its own site.

use std::sync::Mutex;
use std::thread;
use std::time::Duration;

use tauri::http::Response;
use tauri::webview::NewWindowResponse;
use tauri::{AppHandle, LogicalPosition, LogicalSize, Manager, Runtime, Url, WebviewBuilder, WebviewUrl};

pub const SCHEME: &str = "bitchord-shell";
const FRAME: &str = "sign-in-frame";
const PAGE: &str = "sign-in";

// The card's geometry; shell/sign-in.html lays out the same numbers in CSS.
const CARD_MAX: (f64, f64) = (520.0, 720.0);
const CARD_MARGIN: (f64, f64) = (32.0, 48.0);
const HEADER: f64 = 64.0;
const FOOTER: f64 = 44.0;

pub struct Provider {
    host: &'static str,
    name: &'static str,
    /// Where the provider sends the browser once signed in: the sheet closes there.
    done_host: &'static str,
    /// Every site the sign-in itself passes through. Anywhere else (help, privacy,
    /// terms) and the sheet offers the way back.
    flow: &'static [&'static str],
}

const PROVIDERS: &[Provider] = &[
    Provider {
        host: "accounts.google.com",
        name: "YouTube Music",
        done_host: "music.youtube.com",
        // Sign-in, the YouTube session hand-off (SetSID), cookie consent, and the recovery-options step.
        flow: &["accounts.google.com", "accounts.youtube.com", "consent.google.com", "consent.youtube.com", "gds.google.com"],
    },
    Provider {
        host: "accounts.spotify.com",
        name: "Spotify",
        done_host: "open.spotify.com",
        // Spotify's own login and captcha, and the sites behind "Continue with Google/Facebook/Apple".
        flow: &[
            "accounts.spotify.com",
            "challenge.spotify.com",
            "accounts.google.com",
            "www.facebook.com",
            "m.facebook.com",
            "appleid.apple.com",
        ],
    },
];

pub fn provider(url: &Url) -> Option<&'static Provider> {
    PROVIDERS.iter().find(|p| url.host_str() == Some(p.host))
}

/// Serves the sheet's chrome, with upstream's design tokens compiled in.
pub fn serve() -> Response<Vec<u8>> {
    let html = include_str!("../shell/sign-in.html")
        .replace("/*TOKENS*/", include_str!("../../../web/app/src/styles/tokens.css"));
    Response::builder()
        .header("Content-Type", "text/html; charset=utf-8")
        .body(html.into_bytes())
        .expect("static response")
}

/// What every popup from the player becomes: a sign-in sheet, a browser tab, or (the extension's own pages) a popup.
pub fn on_new_window<R: Runtime>(app: &AppHandle<R>, url: Url) -> NewWindowResponse<R> {
    if let Some(provider) = provider(&url) {
        let app = app.clone();
        // Off the WebView2 callback: creating webviews inside it can deadlock.
        thread::spawn(move || open(&app, url, provider));
    } else if !open_in_browser(&url) {
        return NewWindowResponse::Allow;
    }
    NewWindowResponse::Deny
}

/// http(s) links open in the user's browser; anything else is refused there.
fn open_in_browser(url: &Url) -> bool {
    if !matches!(url.scheme(), "http" | "https") {
        return false;
    }
    // explorer hands a URL to the default browser; no shell is involved, so the URL is one argument.
    let _ = std::process::Command::new("explorer").arg(url.as_str()).spawn();
    true
}

/// The open sheet: which provider, and the last sign-in step "Back to sign-in" returns to.
struct Session {
    provider: &'static Provider,
    resume: Url,
    /// A link that asked for a new window (help, privacy, terms) was opened in the sheet;
    /// until "Back to sign-in", the sheet is off the sign-in even on the provider's own host.
    left: bool,
}

static CURRENT: Mutex<Option<Session>> = Mutex::new(None);

/// Tells the sheet's header which site it is on (it follows every navigation), and whether that is off the sign-in.
fn show<R: Runtime>(app: &AppHandle<R>, provider: &Provider, host: &str, away: bool) {
    if let Some(frame) = app.get_webview(FRAME) {
        // Debug-formatted &str is a valid JS string literal for the ASCII hostnames and names here.
        let _ = frame.eval(format!("show({:?}, {:?}, {away})", provider.name, host));
    }
}

/// Loads [url] in the sheet, off the WebView2 callback that asked for it.
fn navigate<R: Runtime>(app: &AppHandle<R>, url: Url) {
    let app = app.clone();
    thread::spawn(move || {
        if let Some(page) = app.get_webview(PAGE) {
            let _ = page.navigate(url);
        }
    });
}

fn open<R: Runtime>(app: &AppHandle<R>, url: Url, provider: &'static Provider) {
    *CURRENT.lock().unwrap() = Some(Session { provider, resume: url.clone(), left: false });
    if let Some(page) = app.get_webview(PAGE) {
        show(app, provider, provider.host, false);
        let _ = page.navigate(url);
        return;
    }
    let Some(window) = app.get_window("main") else { return };
    let Some((full, body)) = layout(&window) else { return };

    let mut frame_url = Url::parse(&format!("{SCHEME}://localhost/sign-in")).expect("static url");
    frame_url.query_pairs_mut().append_pair("name", provider.name).append_pair("host", provider.host);
    let close_app = app.clone();
    let frame = WebviewBuilder::new(FRAME, WebviewUrl::CustomProtocol(frame_url))
        .transparent(true)
        // WebView2 refuses a second webview in this profile unless its environment matches main's.
        .browser_extensions_enabled(true)
        // The sheet's buttons navigate the frame: /close (close, Esc, the scrim) and /back ("Back to sign-in").
        .on_navigation(move |url| match url.path() {
            "/close" => {
                close(&close_app);
                false
            }
            "/back" => {
                let resume = CURRENT.lock().unwrap().as_mut().map(|s| {
                    s.left = false;
                    s.resume.clone()
                });
                if let Some(resume) = resume {
                    navigate(&close_app, resume);
                }
                false
            }
            _ => true,
        });
    let added = window.add_child(frame, full.0, full.1);
    if added.is_err() {
        return;
    }

    // Lets the card's fade-in show before the provider's page covers its middle.
    thread::sleep(Duration::from_millis(150));

    let nav_app = app.clone();
    let popup_app = app.clone();
    let page = WebviewBuilder::new(PAGE, WebviewUrl::External(url))
        .transparent(true)
        // WebView2 refuses a second webview in this profile unless its environment matches main's.
        .browser_extensions_enabled(true)
        .on_navigation(move |url| {
            let mut current = CURRENT.lock().unwrap();
            let Some(session) = current.as_mut() else { return true };
            let provider = session.provider;
            let host = url.host_str().unwrap_or_default();
            if host == provider.done_host {
                close(&nav_app);
                return false;
            }
            let away = session.left || !provider.flow.contains(&host);
            if !away {
                session.resume = url.clone();
            }
            drop(current);
            show(&nav_app, provider, host, away);
            true
        })
        .on_new_window(move |url, _| {
            let mut current = CURRENT.lock().unwrap();
            let Some(session) = current.as_mut() else { return NewWindowResponse::Deny };
            let host = url.host_str().unwrap_or_default();
            // "Continue with Google/Apple/Facebook" from Spotify: a popup to another identity
            // site that reports back to its opener, so it stays a real (in-app, same-profile) popup.
            if host != session.provider.host && session.provider.flow.contains(&host) {
                return NewWindowResponse::Allow;
            }
            // Help, privacy, terms: in the sheet, where "Back to sign-in" brings the user back.
            session.left = true;
            drop(current);
            navigate(&popup_app, url);
            NewWindowResponse::Deny
        });
    if let Ok(page) = window.add_child(page, body.0, body.1) {
        let _ = page.set_focus();
    }
}

fn close<R: Runtime>(app: &AppHandle<R>) {
    *CURRENT.lock().unwrap() = None;
    let app = app.clone();
    thread::spawn(move || {
        for label in [PAGE, FRAME] {
            if let Some(webview) = app.get_webview(label) {
                let _ = webview.close();
            }
        }
        if let Some(main) = app.get_webview("main") {
            let _ = main.set_focus();
        }
    });
}

/// Keeps the sheet over the player as the window resizes.
pub fn relayout<R: Runtime>(app: &AppHandle<R>) {
    let Some(window) = app.get_window("main") else { return };
    let Some((full, body)) = layout(&window) else { return };
    for (label, (position, size)) in [(FRAME, full), (PAGE, body)] {
        if let Some(webview) = app.get_webview(label) {
            let _ = webview.set_position(position);
            let _ = webview.set_size(size);
        }
    }
}

type Rect = (LogicalPosition<f64>, LogicalSize<f64>);

/// The whole window (the scrim), and the card's body between its header and footer.
fn layout<R: Runtime>(window: &tauri::Window<R>) -> Option<(Rect, Rect)> {
    let size = window.inner_size().ok()?.to_logical::<f64>(window.scale_factor().ok()?);
    let card_w = CARD_MAX.0.min(size.width - CARD_MARGIN.0);
    let card_h = CARD_MAX.1.min(size.height - CARD_MARGIN.1);
    let x = (size.width - card_w) / 2.0;
    let y = (size.height - card_h) / 2.0;
    Some((
        (LogicalPosition::new(0.0, 0.0), LogicalSize::new(size.width, size.height)),
        (LogicalPosition::new(x, y + HEADER), LogicalSize::new(card_w, card_h - HEADER - FOOTER)),
    ))
}
