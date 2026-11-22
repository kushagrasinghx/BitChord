//! Tells the user when a newer BitChord for Windows is out, as the phone app does
//! (AppUpdateChecker.kt): the same GitHub "latest release" and the same version
//! rule. A release counts only once it carries the Windows installer, which
//! .github/workflows/windows.yml attaches when a release is published, so the
//! notification never points at a release without one. The notification's
//! Download opens the installer in the browser; running it upgrades in place.
//! Where notifications are turned off, a dialog over the player asks instead.

use std::time::Duration;
use std::{fs, thread};

use tauri::{AppHandle, Manager, Runtime};
use windows::Data::Xml::Dom::XmlDocument;
use windows::Foundation::Uri;
use windows::UI::Notifications::{NotificationSetting, ToastNotification, ToastNotificationManager};
use windows::Web::Http::HttpClient;
use windows::core::HSTRING;
use windows_sys::Win32::UI::WindowsAndMessaging::{IDYES, MB_ICONINFORMATION, MB_YESNO, MessageBoxW};

pub(crate) const LATEST: &str = "https://api.github.com/repos/kushagrasinghx/BitChord/releases/latest";
/// What the workflow names the installer it attaches: BitChord-v1.11-windows-setup.exe.
const ASSET_SUFFIX: &str = "-windows-setup.exe";
const DOWNLOADS: &str = "https://github.com/kushagrasinghx/BitChord/releases/download/";
/// The player can run for days; it checks at launch and then this often.
const EVERY: Duration = Duration::from_secs(12 * 60 * 60);

type Error = Box<dyn std::error::Error>;

pub fn spawn<R: Runtime>(app: &AppHandle<R>) {
    let app = app.clone();
    thread::spawn(move || {
        loop {
            // Offline, rate-limited or no release yet: try again next time.
            let _ = check(&app);
            thread::sleep(EVERY);
        }
    });
}

fn check<R: Runtime>(app: &AppHandle<R>) -> Result<(), Error> {
    let release: serde_json::Value = serde_json::from_str(&get(LATEST)?)?;
    let current = app.package_info().version.to_string();
    let Some((version, url)) = newer_installer(&release, &current) else { return Ok(()) };

    // Once per version, not at every launch.
    let marker = app.path().app_local_data_dir()?.join("update-notified");
    if fs::read_to_string(&marker).is_ok_and(|seen| seen == version) {
        return Ok(());
    }
    tell(app, &version, &url)?;
    fs::create_dir_all(marker.parent().ok_or("no data dir")?)?;
    fs::write(marker, version)?;
    Ok(())
}

/// The release's version and Windows installer URL, when it is newer than [current] and has one.
fn newer_installer(release: &serde_json::Value, current: &str) -> Option<(String, String)> {
    let version = release["tag_name"].as_str()?.trim_start_matches('v').to_string();
    if !is_newer(&version, current) {
        return None;
    }
    let url = release["assets"].as_array()?.iter().find_map(|asset| {
        let name = asset["name"].as_str()?;
        let url = asset["browser_download_url"].as_str()?;
        // Only this repository's own downloads go into the notification.
        (name.ends_with(ASSET_SUFFIX) && url.starts_with(DOWNLOADS)).then(|| url.to_string())
    })?;
    Some((version, url))
}

/// AppUpdateChecker.isNewer: numbers compared dot by dot ("1.10" > "1.9"), and a
/// "-suffix" pre-release is older than the plain release of the same numbers.
fn is_newer(latest: &str, current: &str) -> bool {
    let parse = |raw: &str| {
        let (base, pre) = raw.split_once('-').map_or((raw, false), |(base, _)| (base, true));
        (base.split('.').map(|part| part.parse::<u64>().unwrap_or(0)).collect::<Vec<_>>(), pre)
    };
    let ((l, l_pre), (c, c_pre)) = (parse(latest), parse(current));
    for i in 0..l.len().max(c.len()) {
        let (a, b) = (l.get(i).copied().unwrap_or(0), c.get(i).copied().unwrap_or(0));
        if a != b {
            return a > b;
        }
    }
    c_pre && !l_pre
}

fn get(url: &str) -> windows::core::Result<String> {
    let client = HttpClient::new()?;
    let headers = client.DefaultRequestHeaders()?;
    // GitHub's API refuses requests without a User-Agent; a generic one, nothing about the user.
    headers.UserAgent()?.TryParseAdd(&HSTRING::from("BitChord-Windows"))?;
    headers.Accept()?.TryParseAdd(&HSTRING::from("application/vnd.github+json"))?;
    Ok(client.GetStringAsync(&Uri::CreateUri(&HSTRING::from(url))?)?.join()?.to_string())
}

/// A Windows notification, or, where the user has turned notifications off (for
/// BitChord or for every app), a dialog over the player, so the update is never missed.
fn tell<R: Runtime>(app: &AppHandle<R>, version: &str, url: &str) -> Result<(), Error> {
    let notifier = ToastNotificationManager::CreateToastNotifierWithId(&HSTRING::from(&app.config().identifier))?;
    if notifier.Setting()? == NotificationSetting::Enabled {
        return Ok(notifier.Show(&ToastNotification::CreateToastNotification(&toast(version, url)?)?)?);
    }
    let owner = app.get_webview_window("main").and_then(|w| w.hwnd().ok()).map_or(std::ptr::null_mut(), |h| h.0);
    let text: Vec<u16> = format!(
        "BitChord {version} is available.\n\nDownload the update now? Installing it keeps your sign-ins and settings."
    )
    .encode_utf16()
    .chain([0])
    .collect();
    let caption: Vec<u16> = "BitChord".encode_utf16().chain([0]).collect();
    let answer = unsafe { MessageBoxW(owner, text.as_ptr(), caption.as_ptr(), MB_YESNO | MB_ICONINFORMATION) };
    if answer == IDYES {
        crate::sign_in::open_in_browser(&url.parse()?);
    }
    Ok(())
}

/// The notification: BitChord's name and icon (the shortcut's identity), and Download opening [url].
fn toast(version: &str, url: &str) -> windows::core::Result<XmlDocument> {
    let (version, url) = (xml_escape(version), xml_escape(url));
    let xml = XmlDocument::new()?;
    xml.LoadXml(&HSTRING::from(format!(
        r#"<toast activationType="protocol" launch="{url}">
  <visual><binding template="ToastGeneric">
    <text>BitChord {version} is available</text>
    <text>Download the update for Windows. Installing it keeps your sign-ins and settings.</text>
  </binding></visual>
  <actions><action content="Download" activationType="protocol" arguments="{url}"/></actions>
</toast>"#
    )))?;
    Ok(xml)
}

fn xml_escape(text: &str) -> String {
    text.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('"', "&quot;")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn versions_compare_as_the_phone_does() {
        assert!(is_newer("1.11", "1.10.0"));
        assert!(is_newer("1.10", "1.9.2"));
        assert!(!is_newer("1.10", "1.10.0"));
        assert!(!is_newer("1.9", "1.10.0"));
        assert!(is_newer("1.10", "1.10.0-beta2"));
        assert!(!is_newer("1.10-beta3", "1.10.0"));
    }

    #[test]
    fn needs_a_windows_installer_on_the_release() {
        let release = |assets: serde_json::Value| serde_json::json!({ "tag_name": "v1.11", "assets": assets });
        let installer = serde_json::json!([
            { "name": "BitChord-v1.11.apk", "browser_download_url": "https://github.com/kushagrasinghx/BitChord/releases/download/v1.11/BitChord-v1.11.apk" },
            { "name": "BitChord-v1.11-windows-setup.exe", "browser_download_url": "https://github.com/kushagrasinghx/BitChord/releases/download/v1.11/BitChord-v1.11-windows-setup.exe" },
        ]);
        assert_eq!(
            newer_installer(&release(installer.clone()), "1.10.0"),
            Some(("1.11".into(), "https://github.com/kushagrasinghx/BitChord/releases/download/v1.11/BitChord-v1.11-windows-setup.exe".into()))
        );
        assert_eq!(newer_installer(&release(installer), "1.11.0"), None);
        assert_eq!(newer_installer(&release(serde_json::json!([{ "name": "BitChord-v1.11.apk", "browser_download_url": "https://github.com/kushagrasinghx/BitChord/releases/download/v1.11/BitChord-v1.11.apk" }])), "1.10.0"), None);
        // Not this repository's download: ignored.
        assert_eq!(newer_installer(&release(serde_json::json!([{ "name": "x-windows-setup.exe", "browser_download_url": "https://example.com/x-windows-setup.exe" }])), "1.10.0"), None);
    }

    #[test]
    #[ignore = "network: cargo test -- --ignored"]
    fn reads_the_live_latest_release() {
        let release: serde_json::Value = serde_json::from_str(&get(LATEST).unwrap()).unwrap();
        assert!(release["tag_name"].as_str().unwrap().starts_with('v'));
        assert!(release["assets"].is_array());
        // Never newer than itself, whatever it carries.
        let tag = release["tag_name"].as_str().unwrap().trim_start_matches('v');
        assert_eq!(newer_installer(&release, tag), None);
    }

    #[test]
    fn notification_text_is_escaped() {
        assert_eq!(xml_escape(r#"a&b<"c">"#), "a&amp;b&lt;&quot;c&quot;&gt;");
    }
}
