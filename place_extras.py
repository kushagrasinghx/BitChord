#!/usr/bin/env python3
"""
place_extras.py  -  run from the repo root:   python3 place_extras.py

Expects these four files next to it (repo root), uploaded as they are:
    LyricsViewModel.kt  LyricsScreen.kt  LyricLineItem.kt  WearLyricsSync.kt
It moves them to their places (old versions are kept as .bak, which git ignores) and makes the
small edits: protocol paths, MainViewModel hooks, drawables and strings.
Safe to run twice.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path.cwd()
UI = ROOT / "wearos/src/main/java/com/music/bitchord/wear/ui"
RES = ROOT / "wearos/src/main/res"
APP_WEAR = ROOT / "app/src/main/java/com/music/bitchord/wear"
PATHS = next(iter((ROOT / "wearos/protocol").rglob("WearSyncPaths.kt")), None)
MAIN_VM = next(iter((ROOT / "app/src/main").rglob("MainViewModel.kt")), None)
problems = []

PLACES = {
    "LyricsViewModel.kt": UI,
    "LyricsScreen.kt": UI,
    "LyricLineItem.kt": UI,
    "WearLyricsSync.kt": APP_WEAR,
}


def read(p):
    return p.read_text(encoding="utf-8")


def backup_write(p, text):
    bak = Path(str(p) + ".bak")
    if p.exists() and not bak.exists():
        bak.write_text(read(p), encoding="utf-8")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def patch(path, old, new, marker, what):
    txt = read(path)
    if marker in txt:
        print(f"  = {path.name}: {what} (already done)")
        return
    if txt.count(old) != 1:
        problems.append(f"{path.name}: expected exactly one match for: {what} (found {txt.count(old)})")
        return
    backup_write(path, txt.replace(old, new, 1))
    print(f"  + {path.name}: {what}")


if PATHS is None or MAIN_VM is None or not UI.is_dir() or not APP_WEAR.is_dir():
    sys.exit("Run from the repo root, after wire_lyrics.py and wire_phone.py.")

print("[1/4] moving the uploaded files into place")
for name, folder in PLACES.items():
    src = ROOT / name
    dst = folder / name
    if not src.exists():
        problems.append(f"{name} is not in the repo root. Upload it there first.")
        continue
    backup_write(dst, read(src))
    src.unlink()
    print(f"  + {name} -> {dst.relative_to(ROOT)}")

print("[2/4] protocol paths")
anchor = '    const val LYRICS_REQUEST = "/bitchord/lyrics/request"\n'
patch(PATHS, anchor,
      anchor + '\n    /** Watch -> phone: its send time. Phone -> watch: that time echoed, then the phone\'s clock. */\n'
               '    const val CLOCK_PING = "/bitchord/lyrics/clock/ping"\n'
               '    const val CLOCK_PONG = "/bitchord/lyrics/clock/pong"\n',
      "CLOCK_PING", "clock paths")
patch(PATHS, anchor,
      anchor + '''
    /** Watch -> phone: "trackId\\nmode", mode = translation | romanization | original. */
    const val LYRICS_TRANSFORM = "/bitchord/lyrics/transform"

    /** Phone -> watch (DataClient): per-line translated or romanized text, aligned to the payload's lines. */
    const val LYRICS_EXTRA = "/bitchord/lyrics/extra"
    const val KEY_KIND = "kind"
    const val KEY_STATUS = "status"
    const val KEY_LANG = "lang"
    const val KEY_TEXTS = "texts"
    const val KIND_TRANSLATION = "translation"
    const val KIND_ROMANIZATION = "romanization"
    const val KIND_ORIGINAL = "original"
    const val STATUS_OK = "ok"
    const val STATUS_SAME = "same"
    const val STATUS_UNAVAILABLE = "unavailable"
''', "LYRICS_TRANSFORM", "translation paths")
patch(PATHS, anchor,
      anchor + '''
    /** Phone -> watch (DataClient): the player's lyrics offset in ms; positive = lyrics later. */
    const val LYRICS_OFFSET = "/bitchord/lyrics/offset"
    const val KEY_OFFSET_MS = "offsetMs"
''', "LYRICS_OFFSET", "offset path")

print("[3/4] MainViewModel.kt: lyrics the player picks or syncs reach the watch")
patch(MAIN_VM, "_lyrics.value = embedded", "_lyrics.value = embedded\n                    wearPublishLyrics(embedded)",
      "wearPublishLyrics(embedded)", "embedded lyrics")
patch(MAIN_VM, "_lyrics.value = selected?.lines", "_lyrics.value = selected?.lines\n            selected?.lines?.let { wearPublishLyrics(it) }",
      "selected?.lines?.let { wearPublishLyrics(it) }", "automatic lookup")
patch(MAIN_VM, "lyricsProviderResults[source]?.let(::showLyricsResult)",
      "lyricsProviderResults[source]?.let { showLyricsResult(it); wearPublishLyrics(it.lines) }",
      "showLyricsResult(it); wearPublishLyrics(it.lines)", "source picked from the drawer")
patch(MAIN_VM, "if (selectedLyricsSource == source) showLyricsResult(result)",
      "if (selectedLyricsSource == source) {\n            showLyricsResult(result)\n            wearPublishLyrics(result.lines)\n        }",
      "wearPublishLyrics(result.lines)", "source that finished after being picked")
patch(MAIN_VM, "    /**\n     * Selects a provider from the player drawer.",
      '''    /** What the player is showing now goes to the watch too (see WearLyricsSync). */
    private fun wearPublishLyrics(lines: List<com.music.bitchord.data.lyrics.LyricLine>) {
        val request = currentLyricsRequest ?: return
        com.music.bitchord.wear.WearLyricsSync.publish(
            getApplication<android.app.Application>(),
            request.videoId, request.title, request.artist, request.durationMs, lines,
            fromUi = true,
        )
    }

    /**
     * Selects a provider from the player drawer.''', "private fun wearPublishLyrics", "helper")

print("[4/4] resources")
ICON_HEAD = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n'
             '    android:viewportWidth="24"\n    android:viewportHeight="24">\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="')
ICON_TAIL = '" />\n</vector>\n'
TRANSLATE = ("M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53L17,6L17,4h-7L10,2L8,2v2L1,4v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 "
             "8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12z"
             "M15.88,17l1.62,-4.33L19.12,17h-3.24z")
LANGUAGE = ("M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 "
            "1.84,0.63 3.37,1.91 4.33,3.56zM12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96zM4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 "
            "0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2 0,0.68 0.06,1.34 0.14,2L4.26,14zM5.08,16h2.95c0.32,1.25 0.78,2.45 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56z"
            "M8.03,8L5.08,8c0.96,-1.66 2.49,-2.93 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43,1.43 -1.08,2.76 -1.91,3.96z"
            "M14.34,14L9.66,14c-0.09,-0.66 -0.16,-1.32 -0.16,-2 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32 0.16,2 0,0.68 -0.07,1.34 -0.16,2z"
            "M14.59,19.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56zM16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34 -0.14,-2h3.38"
            "c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z")
backup_write(RES / "drawable/ic_lyrics_translate.xml", ICON_HEAD + TRANSLATE + ICON_TAIL)
backup_write(RES / "drawable/ic_lyrics_romanize.xml", ICON_HEAD + LANGUAGE + ICON_TAIL)

EN = {
    "wear_lyrics": "Lyrics",
    "wear_lyrics_waiting": "Play a song in BitChord on your phone. Tap to reconnect.",
    "wear_lyrics_loading": "Loading lyrics",
    "wear_lyrics_none": "No synced lyrics for this song.",
    "wear_lyrics_stale": "Phone not responding",
    "wear_lyrics_stale_tap": "Phone not responding. Tap to retry.",
    "wear_lyrics_translate": "Translate",
    "wear_lyrics_romanize": "Latin script",
    "wear_lyrics_extra_loading": "Loading…",
    "wear_lyrics_extra_same": "Nothing to convert",
    "wear_lyrics_extra_unavailable": "Not available for this song",
}
TR = {
    "wear_lyrics": "Sözler",
    "wear_lyrics_waiting": "Telefonda BitChord ile bir şarkı çalın. Yeniden bağlanmak için dokunun.",
    "wear_lyrics_loading": "Sözler yükleniyor",
    "wear_lyrics_none": "Bu şarkı için senkronlu söz yok.",
    "wear_lyrics_stale": "Telefon yanıt vermiyor",
    "wear_lyrics_stale_tap": "Telefon yanıt vermiyor. Yeniden denemek için dokunun.",
    "wear_lyrics_translate": "Çeviri",
    "wear_lyrics_romanize": "Latin harfleri",
    "wear_lyrics_extra_loading": "Yükleniyor…",
    "wear_lyrics_extra_same": "Dönüştürülecek bir şey yok",
    "wear_lyrics_extra_unavailable": "Bu şarkı için yok",
}


def strings(table):
    body = "".join(f'    <string name="{k}">{v}</string>\n' for k, v in table.items())
    return f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{body}</resources>\n'


backup_write(RES / "values/strings_lyrics.xml", strings(EN))
backup_write(RES / "values-tr/strings_lyrics.xml", strings(TR))
print("  + drawables and strings (English + Turkish)")

for f in [APP_WEAR / "WearLyricsSync.kt", UI / "LyricsViewModel.kt", UI / "LyricLineItem.kt", UI / "LyricsScreen.kt", MAIN_VM]:
    s = read(f)
    if s.count("{") != s.count("}"):
        problems.append(f"{f.name}: unbalanced braces (an incomplete upload?)")
if "WearSyncPaths.LYRICS_OFFSET" not in read(UI / "LyricsViewModel.kt"):
    problems.append("LyricsViewModel.kt is not the latest version (no lyrics offset). Upload it again.")

if problems:
    print("\n!! NEEDS ATTENTION")
    for p in problems:
        print("  - " + p)
    sys.exit(1)
print("\nOK. Compile check:  ./gradlew :app:assembleProdDebug --console=plain 2>&1 | tee build.log | grep -E '^e: |BUILD|FAILED'")
