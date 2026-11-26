//! Local Music in the player reads a folder the user picks in Windows' own
//! folder dialog. After that pick, WebView2 asks a second time, as a browser
//! would: "Allow this site to view and copy files?", naming
//! http://tauri.localhost, a host the user has never heard of. In the app the
//! pick itself is the consent, so the player's own page is allowed without the
//! second prompt. Any other page (the sign-in sheet, a help link) still gets
//! WebView2's normal prompt.

use tauri::{Runtime, WebviewWindow};
use webview2_com::Microsoft::Web::WebView2::Win32::{
    COREWEBVIEW2_PERMISSION_KIND, COREWEBVIEW2_PERMISSION_KIND_FILE_READ_WRITE, COREWEBVIEW2_PERMISSION_STATE_ALLOW,
};
use webview2_com::PermissionRequestedEventHandler;
use windows::core::PWSTR;

/// The player's own page, served by Tauri's protocol.
pub(crate) fn is_player(uri: &str) -> bool {
    uri == "http://tauri.localhost" || uri.starts_with("http://tauri.localhost/")
}

pub fn allow_player_file_access<R: Runtime>(window: &WebviewWindow<R>) {
    let _ = window.with_webview(|platform| unsafe {
        let Ok(core) = platform.controller().CoreWebView2() else { return };
        let handler = PermissionRequestedEventHandler::create(Box::new(|_, args| {
            let Some(args) = args else { return Ok(()) };
            let mut kind = COREWEBVIEW2_PERMISSION_KIND::default();
            args.PermissionKind(&mut kind)?;
            let mut uri = PWSTR::null();
            args.Uri(&mut uri)?;
            if kind == COREWEBVIEW2_PERMISSION_KIND_FILE_READ_WRITE && is_player(&webview2_com::take_pwstr(uri)) {
                args.SetState(COREWEBVIEW2_PERMISSION_STATE_ALLOW)?;
            }
            Ok(())
        }));
        let mut token = Default::default();
        let _ = core.add_PermissionRequested(&handler, &mut token);
    });
}

#[cfg(test)]
mod tests {
    use super::is_player;

    #[test]
    fn only_the_players_own_page_skips_the_prompt() {
        assert!(is_player("http://tauri.localhost"));
        assert!(is_player("http://tauri.localhost/settings"));
        assert!(!is_player("http://tauri.localhost.evil.example/"));
        assert!(!is_player("https://accounts.google.com/"));
    }
}
