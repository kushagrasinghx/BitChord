//! The title bar in the player's glass. The web player's glass (its player pill,
//! sidebar and side panel: `glass-pill` / `glass-material` in global.css) is a
//! tinted fill with a saturated blur behind it; Windows 11's own version of that
//! is the Acrylic backdrop, so the native title bar is put on Acrylic. It stays a
//! native bar: snap layouts, resize and the caption buttons are untouched. Its
//! light or dark is the player's own (Settings' System, Light or Dark), which
//! shell/title-bar.js reports through [title_bar_theme].

use std::ffi::c_void;

use tauri::{Runtime, Window};
use windows_sys::Win32::Graphics::Dwm::{
    DWMSBT_TRANSIENTWINDOW, DWMWA_SYSTEMBACKDROP_TYPE, DWMWA_USE_IMMERSIVE_DARK_MODE, DwmSetWindowAttribute,
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
#[tauri::command]
pub fn title_bar_theme<R: Runtime>(window: Window<R>, dark: bool) {
    let Ok(hwnd) = window.hwnd() else { return };
    let value: i32 = dark.into(); // a Win32 BOOL
    unsafe {
        DwmSetWindowAttribute(hwnd.0, DWMWA_USE_IMMERSIVE_DARK_MODE as u32, (&raw const value).cast::<c_void>(), size_of_val(&value) as u32);
    }
}
