//! Windows notification-area tray icon with playback controls and background
//! hide/show. Hiding the window to the tray puts WebView2 into the same low-memory
//! hidden state as minimising (`memory::set_background`), so music keeps playing
//! at idle CPU and low RAM without occupying the taskbar.

use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, Runtime, Wry};

use crate::media_session::{self, MediaState};
use crate::memory;

const TRAY_ID: &str = "bitchord-tray";
const ID_NOW_PLAYING: &str = "tray_now_playing";
const ID_PLAY_PAUSE: &str = "tray_play_pause";
const ID_NEXT: &str = "tray_next";
const ID_PREVIOUS: &str = "tray_previous";
const ID_TOGGLE_WINDOW: &str = "tray_toggle_window";
const ID_QUIT: &str = "tray_quit";

pub struct TrayState {
    now_playing: MenuItem<Wry>,
    play_pause: MenuItem<Wry>,
    toggle_window: MenuItem<Wry>,
}

pub fn init(app: &AppHandle<Wry>) -> tauri::Result<()> {
    let now_playing = MenuItem::with_id(app, ID_NOW_PLAYING, "BitChord", false, None::<&str>)?;
    let sep1 = PredefinedMenuItem::separator(app)?;
    let play_pause = MenuItem::with_id(app, ID_PLAY_PAUSE, "Play", true, None::<&str>)?;
    let next = MenuItem::with_id(app, ID_NEXT, "Next Track", true, None::<&str>)?;
    let previous = MenuItem::with_id(app, ID_PREVIOUS, "Previous Track", true, None::<&str>)?;
    let sep2 = PredefinedMenuItem::separator(app)?;
    let toggle_window = MenuItem::with_id(app, ID_TOGGLE_WINDOW, "Hide BitChord", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, ID_QUIT, "Quit BitChord", true, None::<&str>)?;

    let menu = Menu::with_items(
        app,
        &[&now_playing, &sep1, &play_pause, &next, &previous, &sep2, &toggle_window, &quit],
    )?;

    let mut builder = TrayIconBuilder::with_id(TRAY_ID)
        .tooltip("BitChord")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| handle_menu(app, event.id().as_ref()))
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                toggle_visibility(tray.app_handle());
            }
        });

    if let Some(icon) = app.default_window_icon().cloned() {
        builder = builder.icon(icon);
    }

    builder.build(app)?;
    app.manage(TrayState { now_playing, play_pause, toggle_window });
    Ok(())
}

/// Formats the tray header / tooltip for a track.
pub(crate) fn format_track_label(title: &str, artist: &str) -> String {
    let title = title.trim();
    let artist = artist.trim();
    let full = match (title.is_empty(), artist.is_empty()) {
        (true, _) => return "BitChord".into(),
        (false, true) => title.to_string(),
        (false, false) => format!("{title} — {artist}"),
    };
    if full.chars().count() <= 64 {
        full
    } else {
        let mut clipped: String = full.chars().take(63).collect();
        clipped.push('…');
        clipped
    }
}

pub fn update_media(app: &AppHandle<Wry>, state: &MediaState) {
    let label = format_track_label(&state.title, &state.artist);
    let playing = state.playback == "playing";
    if let Some(tray) = app.try_state::<TrayState>() {
        let _ = tray.now_playing.set_text(&label);
        let _ = tray.play_pause.set_text(if playing { "Pause" } else { "Play" });
    }
    if let Some(icon) = app.tray_by_id(TRAY_ID) {
        let tooltip = if label == "BitChord" {
            label
        } else {
            format!("BitChord — {label}")
        };
        let _ = icon.set_tooltip(Some(tooltip));
    }
}

pub fn show_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
        memory::set_background(&window, false);
    }
    sync_toggle_label(app, true);
}

pub fn hide_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_window("main") {
        let _ = window.hide();
        memory::set_background(&window, true);
    }
    sync_toggle_label(app, false);
}

fn toggle_visibility<R: Runtime>(app: &AppHandle<R>) {
    let Some(window) = app.get_window("main") else { return };
    let visible = window.is_visible().unwrap_or(false) && !window.is_minimized().unwrap_or(false);
    if visible {
        hide_window(app);
    } else {
        show_window(app);
    }
}

fn sync_toggle_label<R: Runtime>(app: &AppHandle<R>, visible: bool) {
    if let Some(tray) = app.try_state::<TrayState>() {
        let _ = tray
            .toggle_window
            .set_text(if visible { "Hide BitChord" } else { "Show BitChord" });
    }
}

fn handle_menu(app: &AppHandle<Wry>, id: &str) {
    match id {
        ID_PLAY_PAUSE => {
            let playing = app
                .try_state::<media_session::Controls>()
                .is_some_and(|c| c.is_playing());
            media_session::dispatch_action(app, if playing { "pause" } else { "play" });
        }
        ID_NEXT => media_session::dispatch_action(app, "nexttrack"),
        ID_PREVIOUS => media_session::dispatch_action(app, "previoustrack"),
        ID_TOGGLE_WINDOW => toggle_visibility(app),
        ID_QUIT => app.exit(0),
        _ => {}
    }
}

#[cfg(test)]
mod tests {
    use super::format_track_label;

    #[test]
    fn track_label_formats_and_clips_cleanly() {
        assert_eq!(format_track_label("", "Artist"), "BitChord");
        assert_eq!(format_track_label("Solo Track", ""), "Solo Track");
        assert_eq!(format_track_label("Midnight City", "M83"), "Midnight City — M83");
        let long_title = "A".repeat(80);
        let clipped = format_track_label(&long_title, "Artist");
        assert_eq!(clipped.chars().count(), 64);
        assert!(clipped.ends_with('…'));
    }
}
