#!/usr/bin/env bash
# Checks the reduced runtime against the official one on a connected phone.
#
# For each ABI the phone can run, both models go through the official AAR's
# libonnxruntime.so and through app/libs' reduced one at ALL_OPT (what the app
# uses), and through the reduced one at BASIC_OPT (the trackers' fallback).
# Every output is compared with the official result. Run after build.sh, from
# the same Linux shell; ADB may point at the Windows adb.exe under WSL, with
# PARITY_DIR somewhere under /mnt/c.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-$HOME/ort-build}"
NDK="${ANDROID_NDK:-$WORK/android-ndk-r27}"
ADB="${ADB:-adb}"
SRC="$WORK/onnxruntime"
AAR="$REPO/app/libs/onnxruntime-android-1.28.0-automix.aar"
OFFICIAL="$WORK/official.aar"
DEV=/data/local/tmp/ortparity
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
LOCAL="${PARITY_DIR:-$WORK/parity}"

# adb.exe driven from WSL drops its connection to the Windows server now and
# then, mostly on large transfers to \wsl.localhost paths; a retry rides over
# it. PARITY_DIR on /mnt/c avoids those paths altogether.
adbx() {
    local i
    for i in 1 2 3 4; do
        "$ADB" "$@" && return 0
        sleep 2
    done
    return 1
}

declare -A CC=([arm64-v8a]=aarch64-linux-android24-clang [armeabi-v7a]=armv7a-linux-androideabi24-clang [x86_64]=x86_64-linux-android24-clang)

abilist=$(adbx shell getprop ro.product.cpu.abilist | tr -d '\r')
echo "phone runs: $abilist"
rm -rf "$LOCAL" && mkdir -p "$LOCAL"
adbx shell "rm -rf $DEV && mkdir -p $DEV"
for model in beat_this_final0_q8.onnx vocals_umxhq_int8.onnx; do
    adbx push -q "$(wslpath -w "$REPO/app/src/main/assets/$model" 2>/dev/null || echo "$REPO/app/src/main/assets/$model")" "$DEV/$model" >/dev/null
done

host() { wslpath -w "$1" 2>/dev/null || echo "$1"; }

fail=0
for abi in arm64-v8a armeabi-v7a x86_64; do
    [[ ",$abilist," == *",$abi,"* ]] || { echo "skip $abi (phone cannot run it)"; continue; }
    mkdir -p "$LOCAL/$abi"
    "$TC/${CC[$abi]}" -O2 -I"$SRC/include/onnxruntime/core/session" "$HERE/parity.c" -o "$LOCAL/$abi/parity" -ldl
    unzip -p "$OFFICIAL" "jni/$abi/libonnxruntime.so" > "$LOCAL/$abi/official.so"
    unzip -p "$AAR" "jni/$abi/libonnxruntime.so" > "$LOCAL/$abi/reduced.so"
    adbx shell "mkdir -p $DEV/$abi"
    for f in parity official.so reduced.so; do adbx push -q "$(host "$LOCAL/$abi/$f")" "$DEV/$abi/$f" >/dev/null; done
    adbx shell "chmod 755 $DEV/$abi/parity"
    for kind in beat vocals; do
        model=$([ $kind = beat ] && echo beat_this_final0_q8.onnx || echo vocals_umxhq_int8.onnx)
        for run in official:all reduced:all reduced:basic; do
            lib=${run%%:*}; level=${run##*:}
            adbx shell </dev/null "cd $DEV/$abi && ./parity ./$lib.so ../$model $kind $level $kind-$lib-$level.bin" | tr -d '\r'
            adbx pull -q "$DEV/$abi/$kind-$lib-$level.bin" "$(host "$LOCAL/$abi/")" >/dev/null
        done
        # reduced-all is what the app runs and must match the official runtime;
        # it is expected to be bit-identical, the same kernels on the same graph.
        # reduced-basic is the trackers' fallback: an unfused graph whose dynamic
        # quantisation rounds differently, so its distance is reported, not judged.
        for other in reduced-all reduced-basic; do
            strict=$([ $other = reduced-all ] && echo 1 || echo 0)
            python3 - "$LOCAL/$abi/$kind-official-all.bin" "$LOCAL/$abi/$kind-$other.bin" "$abi $kind official-all vs $other" "$strict" <<'EOF' || fail=1
import array, sys
a, b = array.array("f"), array.array("f")
a.frombytes(open(sys.argv[1], "rb").read()); b.frombytes(open(sys.argv[2], "rb").read())
if len(a) != len(b): print(f"{sys.argv[3]}: size {len(a)} vs {len(b)}"); sys.exit(1)
diff = max(abs(x - y) for x, y in zip(a, b))
scale = max(abs(x) for x in a) or 1.0
same = sum(1 for x, y in zip(a, b) if x == y)
ok = diff <= 1e-5 * scale
print(f"{sys.argv[3]}: {len(a)} values, {same} identical, max |diff| {diff:.3g} "
      f"(output range {scale:.3g})" + ("" if sys.argv[4] == "1" else " [informational]"))
sys.exit(0 if ok or sys.argv[4] != "1" else 1)
EOF
        done
    done
done
adbx shell "rm -rf $DEV"
[ $fail = 0 ] && echo "PARITY OK" || { echo "PARITY FAILED"; exit 1; }
