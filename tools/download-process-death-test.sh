#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
APP="${APP:-com.dev.bitchord}"
RECEIVER="$APP/com.music.bitchord.download.DownloadTestReceiver"
ACTIVITY="$APP/com.music.bitchord.MainActivity"
SEED="com.music.bitchord.debug.SEED_DOWNLOAD_DEATH_TEST"
VERIFY="com.music.bitchord.debug.VERIFY_DOWNLOAD_DEATH_TEST"
CLEAN="com.music.bitchord.debug.CLEAN_DOWNLOAD_DEATH_TEST"

device_cat() {
  "$ADB" shell run-as "$APP" cat "files/$1" 2>/dev/null | tr -d '\r'
}

broadcast() {
  "$ADB" shell am broadcast -a "$1" -n "$RECEIVER" >/dev/null
}

wait_for() {
  local file="$1" pattern="$2" timeout="${3:-120}"
  local i
  for ((i = 0; i < timeout * 2; i++)); do
    if device_cat "$file" | grep -q "$pattern"; then
      return 0
    fi
    sleep 0.5
  done
  echo "timed out waiting for $pattern in $file" >&2
  return 1
}

echo "== clean previous run =="
"$ADB" shell am start -W -n "$ACTIVITY" >/dev/null
broadcast "$CLEAN"
sleep 1

echo "== phase 1: seed a throttled download =="
broadcast "$SEED"
wait_for download-process-death-result.properties '^phase=1-running'

PID=$("$ADB" shell pidof "$APP" | tr -d '\r')
if [[ -z "$PID" ]]; then
  echo "app process did not stay alive" >&2
  exit 1
fi
echo "PID before death: $PID"
echo "-- progress --"
device_cat download-process-death-progress.properties
echo "-- durable journal before death --"
"$ADB" shell run-as "$APP" cat shared_prefs/bitchord_settings.xml 2>/dev/null | grep -o 'pending_download_jobs_v1.*' | head -1 || true

echo "== SIGKILL app UID (not force-stop) =="
"$ADB" shell run-as "$APP" kill -9 "$PID"
for _ in $(seq 1 40); do
  if [[ -z "$("$ADB" shell pidof "$APP" | tr -d '\r')" ]]; then break; fi
  sleep 0.25
done
if [[ -n "$("$ADB" shell pidof "$APP" | tr -d '\r')" ]]; then
  echo "app process survived SIGKILL" >&2
  exit 1
fi

echo "== phase 2: relaunch and let WorkManager recover =="
"$ADB" shell am start -W -n "$ACTIVITY" >/dev/null
broadcast "$VERIFY"
wait_for download-process-death-result.properties '^phase=2-' 180

echo "-- result --"
device_cat download-process-death-result.properties
echo "-- final journal --"
"$ADB" shell run-as "$APP" cat shared_prefs/bitchord_settings.xml 2>/dev/null | grep -o 'pending_download_jobs_v1.*' | head -1 || true

if ! device_cat download-process-death-result.properties | grep -q '^phase=2-complete'; then
  echo "process-death recovery failed" >&2
  exit 1
fi

echo "== passed =="
