#!/usr/bin/env bash
# Runs BitChord's frame suite unattended: install + AOT, launch on Home, start the
# suite, fling the feed until the app logs "suite done", pull every report.
# Usage: frame-suite.sh <apk> <out-dir> [seconds-per-mode] [modes|all]   (INSTALL=0 skips install)
# Needs a diagnostics build (-Pbitchord.performanceDiagnostics=true). Leave the phone unlocked
# on the page to fling; it scrolls itself until the suite is done.
set -euo pipefail
APK=$1; OUT=$2; SECS=${3:-20}; MODES=${4:-all}
PKG=com.dev.bitchord.benchmark
ACT=$PKG/com.music.bitchord.MainActivity
mkdir -p "$OUT"
if [ -n "${INSTALL:-1}" ] && [ "${INSTALL:-1}" != 0 ]; then
  adb install -r "$APK" >/dev/null
  adb shell cmd package compile -f -m speed-profile $PKG >/dev/null
fi
adb shell input keyevent KEYCODE_WAKEUP    # an install can outlast the screen timeout
sleep 1
if adb shell dumpsys window | grep -q "isKeyguardShowing=true"; then
  echo "The phone is locked: unlock it, leave it on, and run this again (INSTALL=0 skips the install)." >&2
  exit 2
fi
adb shell am start -n $ACT >/dev/null
sleep 8                                   # let launch work settle before anything records
adb logcat -c
adb shell am start -n $ACT --es bitchord.frameSuite "$MODES" --ei bitchord.frameSuiteSeconds "$SECS" >/dev/null
read W H < <(adb shell wm size | awk -F'[ x]' '{print $3, $4}')
X=$((W / 2)); LOW=$((H * 78 / 100)); HIGH=$((H * 30 / 100))
end=$((SECONDS + 600))
while [ $SECONDS -lt $end ]; do
  for i in 1 2 3; do adb shell input swipe $X $LOW $X $HIGH 140; sleep 0.7; done
  for i in 1 2 3; do adb shell input swipe $X $HIGH $X $LOW 140; sleep 0.7; done
  if adb logcat -d -s BitChordFrames:I | grep -q "suite done"; then break; fi
done
sleep 2
adb logcat -d -s BitChordFrames:I | tail -8
adb pull /sdcard/Android/data/$PKG/files/frame-suite/. "$OUT" >/dev/null
ls -1 "$OUT"
python3 -I "$(dirname "$0")/analyze_frames.py" "$OUT"/*.json
