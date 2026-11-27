//! What the Windows shell relies on in upstream's own code, checked against upstream's
//! files. Everything else follows upstream by itself (the page and the extension are
//! built from web/ as they stand, the version from build.gradle.kts, colours from
//! tokens.css); these are the few places where an upstream change would break the
//! Windows app quietly instead of failing the build. CI runs them on every change to
//! web/ (.github/workflows/windows.yml), so such a change is flagged on its own PR.
//! Each failure says what moved and where the shell's side of it lives.

use std::path::Path;

use tauri::Url;

fn upstream(path: &str) -> String {
    let file = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..").join(path);
    std::fs::read_to_string(&file).unwrap_or_else(|e| panic!("{path} (upstream) can't be read: {e}"))
}

/// The first '…'-quoted string after [marker].
fn quoted_after(source: &str, marker: &str) -> String {
    let at = source.find(marker).unwrap_or_else(|| panic!("{marker:?} is gone"));
    let rest = &source[at + marker.len()..];
    let start = rest.find('\'').expect("no quoted string after it") + 1;
    let end = rest[start..].find('\'').expect("unterminated string");
    rest[start..start + end].to_string()
}

/// [url] opens in the sign-in sheet, and where it hands back closes the sheet.
fn assert_sheet_catches(url: &str) {
    let url = Url::parse(url).unwrap();
    let provider = crate::sign_in::provider(&url)
        .unwrap_or_else(|| panic!("{url}: upstream's sign-in moved; add its host to PROVIDERS in src/sign_in.rs"));
    let back = url
        .query_pairs()
        .find(|(key, _)| key == "continue")
        .map(|(_, value)| Url::parse(&value).unwrap())
        .unwrap_or_else(|| panic!("{url}: no `continue`; the sheet can't tell when sign-in is done"));
    assert_eq!(
        back.host_str(),
        Some(provider.done_host),
        "{url}: hands back to a new site; update done_host in src/sign_in.rs"
    );
}

#[test]
fn google_sign_in_opens_in_the_sheet() {
    assert_sheet_catches(&quoted_after(&upstream("web/app/src/shell/AccountButton.tsx"), "const SIGN_IN_URL ="));
}

#[test]
fn spotify_sign_in_opens_in_the_sheet() {
    assert_sheet_catches(&quoted_after(&upstream("web/extension/entrypoints/background.ts"), "case 'spotify.signIn':"));
}

#[test]
fn sheet_watches_the_session_cookies_the_extension_reads() {
    // src/sign_in.rs closes the sheet when these change; the extension signs with them.
    for (sign_in, source) in [
        ("https://accounts.google.com/ServiceLogin", "web/extension/lib/ytAuth.ts"),
        ("https://accounts.spotify.com/login", "web/extension/lib/spotify.ts"),
    ] {
        let code = upstream(source);
        let provider = crate::sign_in::provider(&Url::parse(sign_in).unwrap()).unwrap();
        for name in provider.session_cookies {
            assert!(
                code.contains(&format!("'{name}'")),
                "{source} no longer reads the {name} cookie; update session_cookies in src/sign_in.rs"
            );
        }
    }
}

#[test]
fn design_tokens_the_shell_draws_with_exist() {
    let tokens = upstream("web/app/src/styles/tokens.css");
    for (sheet, css) in [
        ("shell/sign-in.html", include_str!("../shell/sign-in.html")),
        ("shell/player.css", include_str!("../shell/player.css")),
    ] {
        for name in css.split("var(").skip(1).map(|s| s.split([')', ',']).next().unwrap().trim()) {
            assert!(
                tokens.contains(&format!("{name}:")),
                "{name} is no longer in web/app/src/styles/tokens.css; {sheet} draws with it"
            );
        }
    }
}

#[test]
fn extension_entries_are_where_player_css_hides_them() {
    // shell/player.css: `nav a[href="/extension"]` and `section > a[href="/extension"]`.
    assert!(upstream("web/app/src/pageRegistry.tsx").contains("path: '/extension'"), "the /extension route moved");
    let sidebar = upstream("web/app/src/shell/Sidebar.tsx");
    assert!(sidebar.contains("<nav") && sidebar.contains("to=\"/extension\""), "the sidebar's extension entry moved");
    // Settings: a LinkRow (a link at its root) straight inside the integrations <section>.
    let settings = upstream("web/app/src/pages/SettingsPage.tsx");
    let row = settings.find("to=\"/extension\"").expect("Settings' extension row moved");
    let section = settings[..row].rfind("<section").expect("Settings' extension row is no longer in a <section>");
    assert!(
        !settings[section..row].contains("</section>") && settings[..row].rfind("<LinkRow") > Some(section),
        "Settings' extension row is no longer a LinkRow directly in its <section>; update shell/player.css"
    );
    assert!(settings.contains("<PrefetchLink to={to}"), "LinkRow no longer renders a link at its root; update shell/player.css");
}

#[test]
fn player_still_reports_through_media_session() {
    // shell/media-session.js mirrors navigator.mediaSession to Windows' media controls.
    let mut sources = String::new();
    let mut stack = vec![Path::new(env!("CARGO_MANIFEST_DIR")).join("../../web/app/src")];
    while let Some(dir) = stack.pop() {
        for entry in std::fs::read_dir(dir).unwrap().flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
            } else if path.extension().is_some_and(|e| e == "ts" || e == "tsx") {
                sources += &std::fs::read_to_string(path).unwrap_or_default();
            }
        }
    }
    for api in ["navigator.mediaSession", ".setActionHandler(", "new MediaMetadata", ".playbackState ="] {
        assert!(sources.contains(api), "web/app no longer uses {api}; media keys would stop reaching it");
    }
}

#[test]
fn update_check_reads_the_same_releases_as_the_phone() {
    let phone = upstream("app/src/main/java/com/music/bitchord/data/AppUpdateChecker.kt");
    assert!(
        phone.contains(crate::update::LATEST),
        "the phone checks another repository's releases now; update LATEST and DOWNLOADS in src/update.rs"
    );
}
