//! The title bar in the player's glass. The web player's glass (its player pill,
//! sidebar and side panel: `glass-pill` / `glass-material` in global.css) is a
//! tinted fill with a saturated blur behind it; Windows 11's own version of that
//! is the Acrylic backdrop, so the native title bar is put on Acrylic. It stays a
//! native bar: snap layouts, resize and the caption buttons are untouched. Its
//! light or dark is the player's own (Settings' System, Light or Dark), which
//! shell/title-bar.js reports through [title_bar_theme].

use std::ffi::c_void;

use tauri::{Runtime, Theme, Window};
use windows_sys::Win32::Graphics::Dwm::{
    DWMSBT_TRANSIENTWINDOW, DWMWA_SYSTEMBACKDROP_TYPE, DwmSetWindowAttribute,
};

pub fn apply<R: Runtime>(window: &Window<R>) {
    let Ok(hwnd) = window.hwnd() else { return };
    let backdrop = DWMSBT_TRANSIENTWINDOW; // Acrylic
    // Best effort: before Windows 11 22H2 there is no system backdrop, and the bar keeps its default.
    unsafe {
        DwmSetWindowAttribute(hwnd.0, DWMWA_SYSTEMBACKDROP_TYPE as u32, (&raw const backdrop).cast::<c_void>(), size_of_val(&backdrop) as u32);
    }
}

/// Draws the bar dark or light to match the page under it, so a dark player never sits under a light bar.
/// `dark` is None for Settings' System: the bar then follows Windows, as the page does. Through the window's
/// preferred theme rather than the DWM flag alone, since tao rewrites that flag from the system theme on every
/// WM_SETTINGCHANGE unless a theme is preferred. That also sets WebView2's preferred colour scheme to the same
/// theme (tauri-runtime-wry, on ThemeChanged), which is harmless: Light or Dark is what the page shows anyway,
/// and None hands both back to Windows.
#[tauri::command]
pub fn title_bar_theme<R: Runtime>(window: Window<R>, dark: Option<bool>) -> tauri::Result<()> {
    window.set_theme(dark.map(|dark| if dark { Theme::Dark } else { Theme::Light }))
}
