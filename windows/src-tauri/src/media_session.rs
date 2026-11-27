//! Windows' media controls (the media keys, the volume flyout, Quick Settings) as
//! BitChord's own. Chromium's integration registers them for msedgewebview2.exe,
//! which Windows shows as "Unknown app"; main.rs turns that off
//! (HardwareMediaKeyHandling) and this registers the controls for BitChord's own
//! window instead. shell/media-session.js reports the player's Media Session here,
//! and a button press runs the player's own handler for it.

use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};

use serde::Deserialize;
use tauri::{AppHandle, Manager, Runtime, State, Window, Wry};
use windows::Foundation::{TimeSpan, TypedEventHandler, Uri};
use windows::Media::{
    MediaPlaybackStatus, MediaPlaybackType, SystemMediaTransportControls, SystemMediaTransportControlsButton,
    SystemMediaTransportControlsButtonPressedEventArgs, SystemMediaTransportControlsTimelineProperties,
};
use windows::Storage::Streams::RandomAccessStreamReference;
use windows::Win32::Foundation::HWND;
use windows::Win32::System::WinRT::ISystemMediaTransportControlsInterop;
use windows::core::{HSTRING, Ref, Result, factory};

use crate::{discord_rpc, tray};

pub struct Controls {
    smtc: SystemMediaTransportControls,
    /// The artwork last shown, so a pause doesn't fetch it again.
    artwork: Mutex<Option<String>>,
    /// Mirrors whether the player is currently playing, for tray Play/Pause toggling.
    playing: AtomicBool,
}

impl Controls {
    pub fn is_playing(&self) -> bool {
        self.playing.load(Ordering::Relaxed)
    }
}

/// What shell/media-session.js reports: the player's navigator.mediaSession.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MediaState {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub artwork: Option<String>,
    /// "playing", "paused" or "none".
    pub playback: String,
    /// The actions the player has handlers for.
    pub actions: Vec<String>,
    #[serde(default)]
    pub duration: Option<f64>,
    #[serde(default)]
    pub position: Option<f64>,
    #[serde(default)]
    pub playback_rate: Option<f64>,
}

pub fn dispatch_action<R: Runtime>(app: &AppHandle<R>, action: &str) {
    if let Some(main) = app.get_webview_window("main") {
        let _ = main.eval(format!("window.__bitchordMediaAction?.({action:?})"));
    }
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
        dispatch_action(&app_for_buttons, action);
        Ok(())
    }))?;

    app.manage(Controls {
        smtc,
        artwork: Mutex::new(None),
        playing: AtomicBool::new(false),
    });
    Ok(())
}

#[tauri::command]
pub fn media_session(
    app: AppHandle<Wry>,
    state: MediaState,
    controls: State<'_, Controls>,
    presence: State<'_, discord_rpc::Presence>,
) {
    controls.playing.store(state.playback == "playing", Ordering::Relaxed);
    // Best effort: the player keeps playing whatever Windows or Discord makes of it.
    let _ = update(&controls, &state);
    discord_rpc::update(&presence, &state);
    tray::update_media(&app, &state);
}

/// Converts seconds into WinRT TimeSpan ticks (100-nanosecond intervals).
fn secs_to_timespan(secs: f64) -> TimeSpan {
    let clamped = secs.max(0.0);
    TimeSpan { Duration: (clamped * 10_000_000.0).round() as i64 }
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

    if let Some(duration) = state.duration.filter(|d| d.is_finite() && *d > 0.0) {
        let pos = state
            .position
            .filter(|p| p.is_finite() && *p >= 0.0)
            .unwrap_or(0.0)
            .min(duration);
        let timeline = SystemMediaTransportControlsTimelineProperties::new()?;
        timeline.SetStartTime(secs_to_timespan(0.0))?;
        timeline.SetMinSeekTime(secs_to_timespan(0.0))?;
        timeline.SetEndTime(secs_to_timespan(duration))?;
        timeline.SetMaxSeekTime(secs_to_timespan(duration))?;
        timeline.SetPosition(secs_to_timespan(pos))?;
        let _ = smtc.UpdateTimelineProperties(&timeline);
    }

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
