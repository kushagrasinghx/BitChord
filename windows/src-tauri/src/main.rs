// No console window behind the app in release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

//! BitChord for Windows: upstream's web player (web/app) and its companion
//! extension (web/extension), both unmodified, in one WebView2 window.
//!
//! The extension is what lets the page reach YouTube, Spotify and lyric hosts
//! in a browser. WebView2 is Chromium, so it loads the same MV3 build the
//! Chrome zip holds; this file only hosts the two together.

mod sign_in;

use tauri::{Manager, WebviewUrl, WebviewWindowBuilder, WindowEvent};

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
        .on_window_event(|window, event| {
            if let WindowEvent::Resized(_) = event {
                sign_in::relayout(window.app_handle());
            }
        })
        .setup(|app| {
            // scripts/build.mjs bundles the built extension at resources/extensions/bitchord.
            let extensions = app.path().resource_dir()?.join("extensions");
            let handle = app.handle().clone();
            WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()))
                .title("BitChord")
                .inner_size(1280.0, 800.0)
                .min_inner_size(400.0, 600.0)
                .browser_extensions_enabled(true)
                .extensions_path(extensions)
                // Google's and Spotify's sign-in become a sheet over the player; other links open in the browser.
                .on_new_window(move |url, _features| sign_in::on_new_window(&handle, url))
                .build()?;
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("BitChord failed to start");
}
