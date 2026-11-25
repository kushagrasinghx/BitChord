#!/usr/bin/env bash
# Builds app/libs/onnxruntime-android-<version>-automix.aar: the official
# onnxruntime-android AAR with its libonnxruntime.so replaced by one built from
# the same tag with only the operators Automix's two models use.
#
# Why: the published AAR's libonnxruntime.so is ~28 MB per ABI, because it
# carries every ONNX operator plus the WebGPU, NNAPI and XNNPACK execution
# providers. BitChord runs two fixed models on the CPU provider, so almost all
# of it is dead weight in every APK.
#
# What changes and what does not: the Java classes and libonnxruntime4j_jni.so
# are the official ones, byte for byte. The JNI layer talks to the runtime only
# through ORT's versioned C API, and this script refuses to finish unless every
# symbol the official JNI library imports is exported by the reduced runtime.
# Kernels that are kept are the same code; ones not listed in
# required_operators.config are not compiled, so a model needing one fails at
# session creation (BeatTracker/VocalTracker then retry at BASIC optimisation,
# whose graph holds only the model's own operators).
#
# Changing either model means regenerating required_operators.config with
# opconfig.py (beside this script) and rerunning this.
#
# Runs on Linux, e.g. WSL:
#   wsl -d Ubuntu -- bash /mnt/d/AndroidPorjects/BitChord/native/onnxruntime/build.sh
# Needs: git, python3, curl, unzip, zip, cmake >= 3.28 and ninja on PATH (or
# CMAKE / NINJA), and the Linux Android NDK r27 (ANDROID_NDK).
set -euo pipefail

ORT_VERSION=1.28.0
ORT_COMMIT=da9b5e364c465de65c49d91e696cd6485270757f
ABIS=(arm64-v8a armeabi-v7a x86_64)
API=24

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-$HOME/ort-build}"
NDK="${ANDROID_NDK:-$WORK/android-ndk-r27}"
CMAKE="${CMAKE:-$(command -v cmake || echo "$WORK/tools/cmake/bin/cmake")}"
NINJA="${NINJA:-$(command -v ninja || echo "$WORK/tools/ninja")}"
SRC="$WORK/onnxruntime"
OUT="$REPO/app/libs/onnxruntime-android-$ORT_VERSION-automix.aar"
JOBS="${JOBS:-$(nproc)}"

export PATH="$(dirname "$NINJA"):$(dirname "$CMAKE"):$PATH"
mkdir -p "$WORK"

# ORT's operator-reduction script parses the config through flatbuffers; kept in
# a private directory so the system Python is left alone.
export PYTHONPATH="$WORK/pydeps${PYTHONPATH:+:$PYTHONPATH}"
python3 -c "import flatbuffers" 2>/dev/null \
    || python3 -m pip install -q --target "$WORK/pydeps" flatbuffers

if [ ! -d "$SRC/.git" ]; then
    git clone --depth 1 --branch "v$ORT_VERSION" https://github.com/microsoft/onnxruntime "$SRC"
fi
[ "$(git -C "$SRC" rev-parse HEAD)" = "$ORT_COMMIT" ] || { echo "onnxruntime is not at v$ORT_VERSION"; exit 1; }

# build.py insists on an SDK path for Android; nothing here uses it (no Java
# build, no tests), so an empty directory satisfies it.
mkdir -p "$WORK/android-sdk-stub"

for abi in "${ABIS[@]}"; do
    python3 "$SRC/tools/ci_build/build.py" \
        --build_dir "$WORK/out/$abi" \
        --config Release \
        --update --build \
        --parallel "$JOBS" \
        --skip_tests --skip_submodule_sync \
        --android --android_abi "$abi" --android_api "$API" \
        --android_sdk_path "$WORK/android-sdk-stub" \
        --android_ndk_path "$NDK" \
        --cmake_generator Ninja --cmake_path "$CMAKE" \
        --build_shared_lib --enable_lto \
        `# The official JNI library links OrtSessionOptionsAppendExecutionProvider_Nnapi` \
        `# directly, and ORT only stubs it out on Apple builds; without NNAPI the` \
        `# runtime would fail to load at all. BitChord never enables the provider.` \
        --use_nnapi \
        --include_ops_by_config "$HERE/required_operators.config" \
        --cmake_extra_defines onnxruntime_BUILD_UNIT_TESTS=OFF
done

# The official AAR supplies everything but the runtime itself.
AAR_DIR="$WORK/aar"
rm -rf "$AAR_DIR" && mkdir -p "$AAR_DIR"
curl -fsSL -o "$WORK/official.aar" \
    "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/$ORT_VERSION/onnxruntime-android-$ORT_VERSION.aar"
unzip -q "$WORK/official.aar" -d "$AAR_DIR"

READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
for abi in $(ls "$AAR_DIR/jni"); do
    if [[ ! " ${ABIS[*]} " =~ " $abi " ]]; then
        # BitChord ships no other native code for this ABI, so no device runs it.
        rm -rf "$AAR_DIR/jni/$abi"
        continue
    fi
    built="$WORK/out/$abi/Release/libonnxruntime.so"
    cp "$built" "$AAR_DIR/jni/$abi/libonnxruntime.so"
    "$STRIP" --strip-unneeded "$AAR_DIR/jni/$abi/libonnxruntime.so"

    # Every function the official JNI library calls into must be exported here.
    needed=$("$READELF" --dyn-syms -W "$AAR_DIR/jni/$abi/libonnxruntime4j_jni.so" \
        | awk '$7 == "UND" {print $8}' | sed 's/@.*//' | grep -E '^(Ort|OrtSessionOptions)' | sort -u)
    exported=$("$READELF" --dyn-syms -W "$AAR_DIR/jni/$abi/libonnxruntime.so" \
        | awk '$7 != "UND" {print $8}' | sed 's/@.*//' | sort -u)
    missing=$(comm -23 <(echo "$needed") <(echo "$exported"))
    if [ -n "$missing" ]; then
        echo "$abi: reduced runtime lacks symbols the JNI library imports:"; echo "$missing"; exit 1
    fi
done

rm -f "$OUT" && mkdir -p "$(dirname "$OUT")"
(cd "$AAR_DIR" && zip -q -r -X "$OUT" .)
ls -l "$OUT"
for abi in "${ABIS[@]}"; do ls -l "$AAR_DIR/jni/$abi/"; done
