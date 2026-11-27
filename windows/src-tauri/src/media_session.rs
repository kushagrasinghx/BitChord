//! Windows' media controls (the media keys, the volume flyout, Quick Settings) as
//! BitChord's own. Chromium's integration registers them for msedgewebview2.exe,
//! which Windows shows as "Unknown app"; main.rs turns that off
//! (HardwareMediaKeyHandling) and this registers the controls for BitChord's own
//! window instead. shell/media-session.js reports the player's Media Session here,
//! and a button press runs the player's own handler for it.

use std::sync::Mutex;

use serde::Deserialize;
use tauri::{AppHandle, Manager, Runtime, State, Window};
use windows::Foundation::{TypedEventHandler, Uri};
use windows::Media::{
    MediaPlaybackStatus, MediaPlaybackType, SystemMediaTransportControls, SystemMediaTransportControlsButton,
    SystemMediaTransportControlsButtonPressedEventArgs,
};
use windows::Storage::Streams::RandomAccessStreamReference;
use windows::Win32::Foundation::HWND;
use windows::Win32::System::WinRT::ISystemMediaTransportControlsInterop;
use windows::core::{HSTRING, Ref, Result, factory};

pub struct Controls {
    smtc: SystemMediaTransportControls,
    /// The artwork last shown, so a pause doesn't fetch it again.
    artwork: Mutex<Option<String>>,
}

/// What shell/media-session.js reports: the player's navigator.mediaSession.
#[derive(Deserialize)]
pub struct MediaState {
    title: String,
    artist: String,
    album: String,
    artwork: Option<String>,
    /// "playing", "paused" or "none".
    playback: String,
    /// The actions the player has handlers for.
    actions: Vec<String>,
}

pub fn init<R: Runtime>(app: &AppHandle<R>, window: &Window<R>) -> Result<()> {
    let hwnd = window.hwnd().map_err(|_| windows::core::Error::empty())?;
    let interop = factory::<SystemMediaTransportControls, ISystemMediaTransportControlsInterop>()?;
    let smtc: SystemMediaTransportControls = unsafe { interop.GetForWindow(HWND(hwnd.0)) }?;
    smtc.SetIsEnabled(true)?;

    let app_for_buttons = app.clone();
    smtc.ButtonPressed(&TypedEventHandler::new(move |_, args: Ref<SystemMediaTransportControlsButtonPressedEventArgs>| {
        let Some(args) = args.as_ref() else { return Ok(()) };
        let action = match args.Button()? {
            SystemMediaTransportControlsButton::Play => "play",
            SystemMediaTransportControlsButton::Pause => "pause",
            SystemMediaTransportControlsButton::Next => "nexttrack",
            SystemMediaTransportControlsButton::Previous => "previoustrack",
            SystemMediaTransportControlsButton::Stop => "stop",
            _ => return Ok(()),
        };
        if let Some(main) = app_for_buttons.get_webview_window("main") {
            let _ = main.eval(format!("window.__bitchordMediaAction?.({action:?})"));
        }
        Ok(())
    }))?;

    app.manage(Controls { smtc, artwork: Mutex::new(None) });
    Ok(())
}

#[tauri::command]
pub fn media_session(state: MediaState, controls: State<'_, Controls>) {
    // Best effort: the player keeps playing whatever Windows makes of it.
    let _ = update(&controls, &state);
}

fn update(controls: &Controls, state: &MediaState) -> Result<()> {
    let smtc = &controls.smtc;
    let has = |action: &str| state.actions.iter().any(|a| a == action);
    smtc.SetIsPlayEnabled(has("play"))?;
    smtc.SetIsPauseEnabled(has("pause"))?;
    smtc.SetIsNextEnabled(has("nexttrack"))?;
    smtc.SetIsPreviousEnabled(has("previoustrack"))?;
    smtc.SetIsStopEnabled(has("stop"))?;
    smtc.SetPlaybackStatus(match state.playback.as_str() {
        "playing" => MediaPlaybackStatus::Playing,
        "paused" => MediaPlaybackStatus::Paused,
        _ => MediaPlaybackStatus::Closed,
    })?;

    let display = smtc.DisplayUpdater()?;
    display.SetType(MediaPlaybackType::Music)?;
    let music = display.MusicProperties()?;
    music.SetTitle(&HSTRING::from(&state.title))?;
    music.SetArtist(&HSTRING::from(&state.artist))?;
    music.SetAlbumTitle(&HSTRING::from(&state.album))?;

    let mut shown = controls.artwork.lock().unwrap();
    if *shown != state.artwork {
        match &state.artwork {
            Some(url) => display.SetThumbnail(&RandomAccessStreamReference::CreateFromUri(&Uri::CreateUri(&HSTRING::from(url))?)?)?,
            None => display.SetThumbnail(None)?,
        }
        shown.clone_from(&state.artwork);
    }
    display.Update()
}
