//! A log file a user can attach to a bug report: the player's own diagnostics
//! (its console lines start "BitChord"), every warning and error, and uncaught
//! exceptions, written to `%LOCALAPPDATA%\com.music.bitchord.windows\logs\bitchord.log`.
//! It listens through WebView2's DevTools protocol, so the page stays exactly
//! the web player as shipped. Kept small: at 1 MB the file rolls over to
//! `bitchord.1.log`, so there are never more than two.

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

use serde_json::Value;
use tauri::{Manager, Runtime, WebviewWindow};
use webview2_com::DevToolsProtocolEventReceivedEventHandler;
use webview2_com::Microsoft::Web::WebView2::Win32::ICoreWebView2CallDevToolsProtocolMethodCompletedHandler;
use windows::core::{HSTRING, PWSTR};

const ROLL_AT: u64 = 1024 * 1024;
static FILE: OnceLock<Mutex<PathBuf>> = OnceLock::new();

/// One line for a console call or exception, or None for chatter that isn't worth keeping.
pub(crate) fn line_for(event: &str, params: &Value) -> Option<String> {
    match event {
        "Runtime.consoleAPICalled" => {
            let level = params["type"].as_str().unwrap_or("log");
            let text = params["args"]
                .as_array()?
                .iter()
                .map(|arg| arg["value"].as_str().map(str::to_owned).or_else(|| arg["description"].as_str().map(str::to_owned)).unwrap_or_else(|| arg["value"].to_string()))
                .collect::<Vec<_>>()
                .join(" ");
            let kept = matches!(level, "error" | "warning" | "assert") || text.starts_with("BitChord");
            kept.then(|| format!("{level:<7} {text}"))
        }
        "Runtime.exceptionThrown" => {
            let details = &params["exceptionDetails"];
            let text = details["exception"]["description"].as_str().or_else(|| details["text"].as_str()).unwrap_or("exception");
            Some(format!("uncaught {text}"))
        }
        _ => None,
    }
}

fn write(line: &str) {
    let Some(path) = FILE.get() else { return };
    let Ok(path) = path.lock() else { return };
    if fs::metadata(&*path).map(|m| m.len() >= ROLL_AT).unwrap_or(false) {
        let _ = fs::rename(&*path, path.with_file_name("bitchord.1.log"));
    }
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(&*path) {
        let secs = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
        let _ = writeln!(file, "{} {line}", utc(secs));
    }
}

/// `2026-11-26 02:31:27Z` from Unix seconds (Howard Hinnant's days-to-civil), so the file needs no date crate.
pub(crate) fn utc(secs: u64) -> String {
    let days = (secs / 86_400) as i64;
    let rem = secs % 86_400;
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = doy - (153 * mp + 2) / 5 + 1;
    let month = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = yoe + era * 400 + i64::from(month <= 2);
    format!("{year:04}-{month:02}-{day:02} {:02}:{:02}:{:02}Z", rem / 3_600, rem % 3_600 / 60, rem % 60)
}

fn open(dir: &Path) -> Option<PathBuf> {
    fs::create_dir_all(dir).ok()?;
    Some(dir.join("bitchord.log"))
}

/// Starts logging the main webview's console and exceptions.
pub fn start<R: Runtime>(window: &WebviewWindow<R>) {
    let Some(path) = window.app_handle().path().app_log_dir().ok().and_then(|dir| open(&dir)) else { return };
    let _ = FILE.set(Mutex::new(path));
    write(&format!("info    BitChord {} started", window.app_handle().package_info().version));
    let _ = window.with_webview(|platform| unsafe {
        let Ok(core) = platform.controller().CoreWebView2() else { return };
        for event in ["Runtime.consoleAPICalled", "Runtime.exceptionThrown"] {
            let Ok(receiver) = core.GetDevToolsProtocolEventReceiver(&HSTRING::from(event)) else { continue };
            let handler = DevToolsProtocolEventReceivedEventHandler::create(Box::new(move |_, args| {
                let Some(args) = args else { return Ok(()) };
                let mut json = PWSTR::null();
                args.ParameterObjectAsJson(&mut json)?;
                let json = webview2_com::take_pwstr(json);
                if let Some(line) = serde_json::from_str(&json).ok().and_then(|params: Value| line_for(event, &params)) {
                    write(&line);
                }
                Ok(())
            }));
            let mut token = Default::default();
            let _ = receiver.add_DevToolsProtocolEventReceived(&handler, &mut token);
        }
        let _ = core.CallDevToolsProtocolMethod(&HSTRING::from("Runtime.enable"), &HSTRING::from("{}"), None::<&ICoreWebView2CallDevToolsProtocolMethodCompletedHandler>);
    });
}

#[cfg(test)]
mod tests {
    use super::{line_for, utc};

    #[test]
    fn stamps_lines_in_readable_utc() {
        assert_eq!(utc(0), "1970-01-01 00:00:00Z");
        assert_eq!(utc(951_782_400), "2000-02-29 00:00:00Z");
        assert_eq!(utc(1_795_727_487), "2026-11-26 21:11:27Z");
    }
    use serde_json::json;

    #[test]
    fn keeps_the_players_own_lines_and_every_warning_and_error() {
        let player = json!({ "type": "info", "args": [{ "type": "string", "value": "BitChord [abc] playing via YTMUSIC" }] });
        assert_eq!(line_for("Runtime.consoleAPICalled", &player).as_deref(), Some("info    BitChord [abc] playing via YTMUSIC"));
        let error = json!({ "type": "error", "args": [{ "type": "string", "value": "boom" }, { "type": "object", "description": "TypeError: x" }] });
        assert_eq!(line_for("Runtime.consoleAPICalled", &error).as_deref(), Some("error   boom TypeError: x"));
    }

    #[test]
    fn drops_other_chatter() {
        let chatter = json!({ "type": "log", "args": [{ "type": "string", "value": "[vite] connected" }] });
        assert_eq!(line_for("Runtime.consoleAPICalled", &chatter), None);
    }

    #[test]
    fn records_uncaught_exceptions() {
        let thrown = json!({ "exceptionDetails": { "text": "Uncaught", "exception": { "description": "Error: no stream" } } });
        assert_eq!(line_for("Runtime.exceptionThrown", &thrown).as_deref(), Some("uncaught Error: no stream"));
    }
}
