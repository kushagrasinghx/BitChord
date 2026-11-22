// No console window behind the app in release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

//! BitChord for Windows: upstream's web player (web/app) and its companion
//! extension (web/extension), both unmodified, in one WebView2 window.
//!
//! The extension is what lets the page reach YouTube, Spotify and lyric hosts
//! in a browser. WebView2 is Chromium, so it loads the same MV3 build the
//! Chrome zip holds; this file only hosts the two together.

mod media_session;
mod sign_in;
mod title_bar;
mod update;
#[cfg(test)]
mod upstream;

use tauri::{Manager, WebviewUrl, WebviewWindowBuilder, WindowEvent};

/// Every webview in the app gets these (WebView2 refuses a second webview whose
/// options differ). Tauri's defaults, which setting any replaces: its disabled
/// features, and autoplay without a click, which media keys rely on (their Play
/// reaches the page with no user gesture). Plus Chromium's own media-key handling
/// off, because media_session.rs registers Windows' media controls as BitChord.
pub const BROWSER_ARGS: &str = concat!(
    "--disable-features=msWebOOUI,msPdfOOUI,msSmartScreenProtection,HardwareMediaKeyHandling",
    " --autoplay-policy=no-user-gesture-required",
);

fn main() {
    tauri::Builder::default()
        // A second launch (a taskbar pin, a shortcut) focuses the running player.
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .register_uri_scheme_protocol(sign_in::SCHEME, |_ctx, _request| sign_in::serve())
        .invoke_handler(tauri::generate_handler![media_session::media_session])
        .on_window_event(|window, event| {
            if let WindowEvent::Resized(_) = event {
                sign_in::relayout(window.app_handle());
            }
        })
        .setup(|app| {
            // The identity the installer gives the Start menu and desktop shortcuts, so Windows
            // names the media controls and update notifications "BitChord", with its icon.
            let app_id: Vec<u16> = app.config().identifier.encode_utf16().chain([0]).collect();
            unsafe { windows_sys::Win32::UI::Shell::SetCurrentProcessExplicitAppUserModelID(app_id.as_ptr()) };

            // scripts/build.mjs bundles the built extension at resources/extensions/bitchord.
            let extensions = app.path().resource_dir()?.join("extensions");
            let handle = app.handle().clone();
            WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()))
                .title("BitChord")
                .inner_size(1280.0, 800.0)
                .min_inner_size(400.0, 600.0)
                .additional_browser_args(BROWSER_ARGS)
                .browser_extensions_enabled(true)
                .extensions_path(extensions)
                // Google's and Spotify's sign-in become a sheet over the player; other links open in the browser.
                .on_new_window(move |url, _features| sign_in::on_new_window(&handle, url))
                // shell/player.css (glass scrollbars, no extension entry), as a constructed sheet so upstream's page is untouched.
                .initialization_script(format!(
                    "{{ const s = new CSSStyleSheet(); s.replaceSync({:?}); document.adoptedStyleSheets = [...document.adoptedStyleSheets, s]; }}",
                    include_str!("../shell/player.css")
                ))
                .initialization_script(include_str!("../shell/media-session.js"))
                .build()?;
            if let Some(window) = app.get_window("main") {
                title_bar::apply(&window);
                media_session::init(app.handle(), &window)?;
            }
            update::spawn(app.handle());
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("BitChord failed to start");
}
