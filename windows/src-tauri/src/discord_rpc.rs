//! Native Discord Rich Presence over Discord Desktop's local Windows Named Pipe
//! (`\\.\pipe\discord-ipc-0` .. `9`).
//!
//! Unlike the browser extension — which has no IPC access and must read a user
//! token from a discord.com tab — the Windows shell speaks Discord's official
//! local Rich Presence protocol directly: no login, no user token, no extra
//! crates, and zero CPU while idle or when Discord Desktop isn't running.

use std::fs::{File, OpenOptions};
use std::io::{self, Read, Write};
use std::sync::mpsc::{self, Sender};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use serde_json::{Value, json};
use tauri::{AppHandle, Manager, Runtime};

use crate::media_session::MediaState;

/// BitChord's official Discord application ID (checked against web/extension/lib/discord.ts).
pub(crate) const APPLICATION_ID: &str = "1543679166967193620";
const PROJECT_URL: &str = "https://github.com/kushagrasinghx/BitChord";

const OP_HANDSHAKE: u32 = 0;
const OP_FRAME: u32 = 1;
const OP_CLOSE: u32 = 2;

/// Guard against corrupt frames over the pipe.
const MAX_FRAME_LEN: usize = 64 * 1024;
/// Minimum interval between pipe-open attempts when Discord Desktop isn't running.
const RECONNECT_COOLDOWN: Duration = Duration::from_secs(15);
/// Position drift threshold (seconds) above which a seek triggers a fresh SET_ACTIVITY.
const SEEK_DRIFT_SECS: f64 = 3.0;

pub struct Presence {
    tx: Sender<Command>,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Snapshot {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub artwork: Option<String>,
    pub playing: bool,
    pub duration: Option<f64>,
    pub position: Option<f64>,
    pub playback_rate: f64,
    pub captured_at: Instant,
    pub captured_unix_secs: f64,
}

enum Command {
    Update(Snapshot),
    Shutdown,
}

impl Snapshot {
    fn from_media(state: &MediaState) -> Self {
        let now_unix = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs_f64())
            .unwrap_or(0.0);
        let rate = state
            .playback_rate
            .filter(|r| r.is_finite() && *r > 0.0)
            .unwrap_or(1.0);
        Self {
            title: state.title.clone(),
            artist: state.artist.clone(),
            album: state.album.clone(),
            artwork: state.artwork.clone(),
            playing: state.playback == "playing",
            duration: state.duration.filter(|d| d.is_finite() && *d > 0.0),
            position: state.position.filter(|p| p.is_finite() && *p >= 0.0),
            playback_rate: rate,
            captured_at: Instant::now(),
            captured_unix_secs: now_unix,
        }
    }

    fn is_active(&self) -> bool {
        self.playing && !self.title.trim().is_empty()
    }
}

pub fn init() -> Presence {
    let (tx, rx) = mpsc::channel();
    let _ = thread::Builder::new()
        .name("bitchord-discord-ipc".into())
        .spawn(move || worker(rx));
    Presence { tx }
}

pub fn update(presence: &Presence, state: &MediaState) {
    let _ = presence.tx.send(Command::Update(Snapshot::from_media(state)));
}

pub fn shutdown<R: Runtime>(app: &AppHandle<R>) {
    if let Some(presence) = app.try_state::<Presence>() {
        let _ = presence.tx.send(Command::Shutdown);
    }
}

// ---- Worker loop ---------------------------------------------------------------

fn worker(rx: mpsc::Receiver<Command>) {
    let mut conn: Option<IpcConn> = None;
    let mut last_attempt: Option<Instant> = None;
    let mut last_sent: Option<Snapshot> = None;
    let mut has_active_card = false;
    let mut seq: u64 = 0;

    while let Ok(mut cmd) = rx.recv() {
        // Coalesce bursts (metadata + position + playback arriving back-to-back).
        while let Ok(next) = rx.try_recv() {
            cmd = next;
            if matches!(cmd, Command::Shutdown) {
                break;
            }
        }

        match cmd {
            Command::Shutdown => {
                if let Some(mut pipe) = conn.take() {
                    if has_active_card {
                        seq += 1;
                        let _ = pipe.send_activity(None, seq);
                    }
                    let _ = pipe.close();
                }
                return;
            }
            Command::Update(snapshot) => {
                if !should_publish(last_sent.as_ref(), &snapshot) {
                    continue;
                }

                // If nothing is playing and we never published a card, stay completely idle.
                if !snapshot.is_active() && !has_active_card {
                    last_sent = Some(snapshot);
                    continue;
                }

                if conn.is_none() {
                    if last_attempt.is_some_and(|t| t.elapsed() < RECONNECT_COOLDOWN) {
                        continue;
                    }
                    last_attempt = Some(Instant::now());
                    conn = IpcConn::connect().ok();
                    if conn.is_none() {
                        continue;
                    }
                }

                let activity = build_activity(&snapshot);
                let active_now = activity.is_some();
                seq += 1;
                if let Some(pipe) = conn.as_mut() {
                    if pipe.send_activity(activity, seq).is_ok() {
                        has_active_card = active_now;
                        last_sent = Some(snapshot);
                    } else {
                        // Discord closed or restarted: drop the handle and retry on the next eligible update.
                        conn = None;
                        has_active_card = false;
                        last_sent = None;
                    }
                }
            }
        }
    }
}

/// Returns true when [next] differs meaningfully from [prev] (track change, play/pause,
/// rate change, or a user seek beyond SEEK_DRIFT_SECS).
pub(crate) fn should_publish(prev: Option<&Snapshot>, next: &Snapshot) -> bool {
    let Some(prev) = prev else { return true };
    if prev.is_active() != next.is_active() {
        return true;
    }
    if !next.is_active() {
        return false;
    }
    if prev.title != next.title
        || prev.artist != next.artist
        || prev.album != next.album
        || prev.artwork != next.artwork
        || (prev.playback_rate - next.playback_rate).abs() > 0.01
    {
        return true;
    }
    match (prev.position, next.position, prev.duration, next.duration) {
        (Some(p0), Some(p1), Some(d0), Some(d1)) => {
            if (d0 - d1).abs() > 1.0 {
                return true;
            }
            let elapsed = next
                .captured_at
                .saturating_duration_since(prev.captured_at)
                .as_secs_f64();
            let expected = p0 + elapsed * prev.playback_rate;
            (p1 - expected).abs() > SEEK_DRIFT_SECS
        }
        (None, Some(_), _, Some(_)) => true,
        _ => false,
    }
}

// ---- Activity payload ----------------------------------------------------------

/// Clamps a Discord Rich Presence text field to Discord's 2..=128 char requirement.
pub(crate) fn clamp_field(raw: &str) -> Option<String> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return None;
    }
    let count = trimmed.chars().count();
    if count == 1 {
        return Some(format!("{trimmed} "));
    }
    if count <= 128 {
        return Some(trimmed.to_string());
    }
    let mut out: String = trimmed.chars().take(127).collect();
    out.push('…');
    Some(out)
}

/// Builds the Discord SET_ACTIVITY `activity` object, or `None` to clear the card.
pub(crate) fn build_activity(snapshot: &Snapshot) -> Option<Value> {
    if !snapshot.is_active() {
        return None;
    }
    let title = if (snapshot.playback_rate - 1.0).abs() > 0.01 {
        format!("{} [{:.2}x]", snapshot.title.trim(), snapshot.playback_rate)
    } else {
        snapshot.title.trim().to_string()
    };
    let details = clamp_field(&title)?;

    let mut activity = serde_json::Map::new();
    // 2 = Listening ("Listening to BitChord").
    activity.insert("type".into(), json!(2));
    activity.insert("details".into(), json!(details));

    if let Some(artist) = clamp_field(&snapshot.artist) {
        activity.insert("state".into(), json!(artist));
    }

    if let (Some(duration), Some(position)) = (snapshot.duration, snapshot.position) {
        let rate = snapshot.playback_rate.max(0.01);
        let pos = position.clamp(0.0, duration);
        let start = (snapshot.captured_unix_secs - pos / rate).max(0.0).round() as u64;
        let end = (snapshot.captured_unix_secs + (duration - pos) / rate)
            .max(start as f64)
            .round() as u64;
        activity.insert("timestamps".into(), json!({ "start": start, "end": end }));
    }

    let mut assets = serde_json::Map::new();
    if let Some(url) = snapshot
        .artwork
        .as_deref()
        .filter(|u| u.starts_with("https://") || u.starts_with("http://"))
    {
        assets.insert("large_image".into(), json!(url));
    }
    if let Some(album) = clamp_field(&snapshot.album) {
        assets.insert("large_text".into(), json!(album));
    }
    if !assets.is_empty() {
        activity.insert("assets".into(), Value::Object(assets));
    }

    activity.insert(
        "buttons".into(),
        json!([{ "label": "Visit BitChord", "url": PROJECT_URL }]),
    );

    Some(Value::Object(activity))
}

// ---- Windows Named Pipe IPC framing --------------------------------------------

pub(crate) fn encode_frame(opcode: u32, payload: &Value) -> io::Result<Vec<u8>> {
    let body = serde_json::to_vec(payload)
        .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))?;
    let len = u32::try_from(body.len())
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "frame too large"))?;
    let mut buf = Vec::with_capacity(8 + body.len());
    buf.extend_from_slice(&opcode.to_le_bytes());
    buf.extend_from_slice(&len.to_le_bytes());
    buf.extend_from_slice(&body);
    Ok(buf)
}

pub(crate) fn read_frame<R: Read>(reader: &mut R) -> io::Result<(u32, Value)> {
    let mut header = [0u8; 8];
    reader.read_exact(&mut header)?;
    let opcode = u32::from_le_bytes([header[0], header[1], header[2], header[3]]);
    let len = u32::from_le_bytes([header[4], header[5], header[6], header[7]]) as usize;
    if len > MAX_FRAME_LEN {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "Discord IPC frame exceeded limit",
        ));
    }
    let mut body = vec![0u8; len];
    reader.read_exact(&mut body)?;
    let payload = serde_json::from_slice(&body)
        .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))?;
    Ok((opcode, payload))
}

struct IpcConn {
    pipe: File,
}

impl IpcConn {
    fn connect() -> io::Result<Self> {
        let mut last_err = io::Error::new(io::ErrorKind::NotFound, "no Discord IPC pipe found");
        for slot in 0..10 {
            let path = format!(r"\\.\pipe\discord-ipc-{slot}");
            match OpenOptions::new().read(true).write(true).open(&path) {
                Ok(mut pipe) => {
                    let handshake = encode_frame(
                        OP_HANDSHAKE,
                        &json!({ "v": 1, "client_id": APPLICATION_ID }),
                    )?;
                    pipe.write_all(&handshake)?;
                    pipe.flush()?;
                    let (op, _) = read_frame(&mut pipe)?;
                    if op == OP_CLOSE {
                        return Err(io::Error::new(
                            io::ErrorKind::ConnectionRefused,
                            "Discord rejected handshake",
                        ));
                    }
                    return Ok(Self { pipe });
                }
                Err(err) => last_err = err,
            }
        }
        Err(last_err)
    }

    fn send_activity(&mut self, activity: Option<Value>, seq: u64) -> io::Result<()> {
        let payload = json!({
            "cmd": "SET_ACTIVITY",
            "args": {
                "pid": std::process::id(),
                "activity": activity,
            },
            "nonce": format!("bitchord-{seq}"),
        });
        let frame = encode_frame(OP_FRAME, &payload)?;
        self.pipe.write_all(&frame)?;
        self.pipe.flush()?;
        let (op, _) = read_frame(&mut self.pipe)?;
        if op == OP_CLOSE {
            return Err(io::Error::new(
                io::ErrorKind::BrokenPipe,
                "Discord closed IPC",
            ));
        }
        Ok(())
    }

    fn close(mut self) -> io::Result<()> {
        let frame = encode_frame(OP_CLOSE, &json!({}))?;
        self.pipe.write_all(&frame)?;
        self.pipe.flush()
    }
}

#[cfg(test)]
mod tests {
    use std::io::Cursor;
    use std::time::{Duration, Instant};

    use serde_json::json;

    use super::{
        OP_FRAME, Snapshot, build_activity, clamp_field, encode_frame, read_frame, should_publish,
    };

    fn sample_snapshot() -> Snapshot {
        Snapshot {
            title: "Midnight City".into(),
            artist: "M83".into(),
            album: "Hurry Up, We're Dreaming".into(),
            artwork: Some("https://lh3.googleusercontent.com/cover.jpg".into()),
            playing: true,
            duration: Some(243.0),
            position: Some(30.0),
            playback_rate: 1.0,
            captured_at: Instant::now(),
            captured_unix_secs: 1_700_000_000.0,
        }
    }

    #[test]
    fn frame_round_trips_little_endian_header_and_json() {
        let payload = json!({ "cmd": "SET_ACTIVITY", "nonce": "1" });
        let bytes = encode_frame(OP_FRAME, &payload).unwrap();
        assert_eq!(&bytes[0..4], &OP_FRAME.to_le_bytes());
        let expected_len = (bytes.len() - 8) as u32;
        assert_eq!(&bytes[4..8], &expected_len.to_le_bytes());

        let (op, decoded) = read_frame(&mut Cursor::new(bytes)).unwrap();
        assert_eq!(op, OP_FRAME);
        assert_eq!(decoded, payload);
    }

    #[test]
    fn playing_track_builds_listening_activity_with_timestamps() {
        let snap = sample_snapshot();
        let activity = build_activity(&snap).unwrap();
        assert_eq!(activity["type"], 2);
        assert_eq!(activity["details"], "Midnight City");
        assert_eq!(activity["state"], "M83");
        assert_eq!(activity["timestamps"]["start"], 1_699_999_970);
        assert_eq!(activity["timestamps"]["end"], 1_700_000_213);
        assert_eq!(
            activity["assets"]["large_image"],
            "https://lh3.googleusercontent.com/cover.jpg"
        );
        assert_eq!(activity["assets"]["large_text"], "Hurry Up, We're Dreaming");
        assert_eq!(activity["buttons"][0]["label"], "Visit BitChord");
    }

    #[test]
    fn playback_rate_scales_timestamps_and_tags_title() {
        let mut snap = sample_snapshot();
        snap.playback_rate = 1.5;
        snap.position = Some(30.0);
        snap.duration = Some(120.0);
        let activity = build_activity(&snap).unwrap();
        assert_eq!(activity["details"], "Midnight City [1.50x]");
        // 30 / 1.5 = 20s elapsed, 90 / 1.5 = 60s remaining.
        assert_eq!(activity["timestamps"]["start"], 1_699_999_980);
        assert_eq!(activity["timestamps"]["end"], 1_700_000_060);
    }

    #[test]
    fn paused_or_blank_clears_activity() {
        let mut paused = sample_snapshot();
        paused.playing = false;
        assert!(build_activity(&paused).is_none());

        let mut blank = sample_snapshot();
        blank.title = "   ".into();
        assert!(build_activity(&blank).is_none());
    }

    #[test]
    fn clamp_field_respects_discord_bounds_and_utf8() {
        assert_eq!(clamp_field("   "), None);
        assert_eq!(clamp_field("X"), Some("X ".into()));
        let long = "♪".repeat(150);
        let clamped = clamp_field(&long).unwrap();
        assert_eq!(clamped.chars().count(), 128);
        assert!(clamped.ends_with('…'));
    }

    #[test]
    fn small_playback_tick_is_deduped_while_seek_triggers_update() {
        let base = Instant::now();
        let mut prev = sample_snapshot();
        prev.captured_at = base;
        prev.position = Some(10.0);

        let mut normal_tick = prev.clone();
        normal_tick.captured_at = base + Duration::from_secs(5);
        normal_tick.position = Some(15.4);
        assert!(!should_publish(Some(&prev), &normal_tick));

        let mut seeked = prev.clone();
        seeked.captured_at = base + Duration::from_secs(5);
        seeked.position = Some(90.0);
        assert!(should_publish(Some(&prev), &seeked));
    }
}
