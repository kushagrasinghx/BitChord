#!/usr/bin/env bash
# Builds app/src/main/jniLibs/<abi>/libffmpegJNI.so: Media3's FFmpeg audio
# decoder JNI (ffmpeg_jni.cc, beside this script) statically linked against
# libavcodec + libavutil from librempeg.
#
# Why librempeg rather than FFmpeg: it is the only FFmpeg line with an AC-4
# decoder, and AC-4 is what Dolby music downloads increasingly come as. Phones
# with Dolby's own decoder never reach this library for it — the FFmpeg
# renderer sits after MediaCodec in PlaybackService — so this is what plays
# AC-4 everywhere else, along with ALAC, E-AC-3/AC-3, TrueHD and DTS.
#
# Licence: built with --enable-gpl --enable-version3 and without --enable-agpl
# (in librempeg only avfilter's frame threading and the ffmpeg CLI are AGPL,
# and neither is built), so the result is GPLv3, like the app. libavfilter is
# built — librempeg's libavcodec will not configure without it — but with
# --disable-everything it carries no filters.
#
# Run from any bash with GNU make, e.g. MSYS2 on Windows:
#   D:/msys64/usr/bin/bash.exe -lc "/d/AndroidPorjects/BitChord/native/ffmpeg/build.sh"
# Needs: git, make, a host C compiler (HOST_CC, default gcc — configure only
# probes it), and an Android NDK (ANDROID_NDK or the one pinned below).
set -euo pipefail

LIBREMPEG_URL=https://github.com/librempeg/librempeg
LIBREMPEG_COMMIT=8e8b221e207f685119fcfe276ae28c94ff6f5aa0
API=26

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-$REPO/build/ffmpeg}"
NDK="${ANDROID_NDK:-${LOCALAPPDATA:-/c/Users/$USER/AppData/Local}/Android/Sdk/ndk/27.0.12077973}"
NDK="$(cd "$NDK" && pwd)"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) HOST=windows-x86_64; EXE=.exe ;;
    Darwin) HOST=darwin-x86_64; EXE= ;;
    *) HOST=linux-x86_64; EXE= ;;
esac
TC="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
JOBS="${JOBS:-$(nproc 2>/dev/null || echo 8)}"

DECODERS=ac4,alac,ac3,eac3,truehd,mlp,dca,flac,mp3,aac,vorbis,opus,pcm_alaw,pcm_mulaw

SRC="$WORK/librempeg"
if [ ! -d "$SRC/.git" ]; then
    mkdir -p "$WORK"
    # autocrlf off: configure is a shell script and dies on CRLF.
    git -c core.autocrlf=false clone "$LIBREMPEG_URL" "$SRC"
fi
git -C "$SRC" -c core.autocrlf=false fetch --depth 1 origin "$LIBREMPEG_COMMIT" 2>/dev/null || true
git -C "$SRC" -c advice.detachedHead=false checkout -q "$LIBREMPEG_COMMIT"

build_abi() {
    local abi=$1 triple=$2 arch=$3 cpu=$4 extra=$5
    local prefix="$WORK/prefix/$abi"
    echo "==> $abi"
    cd "$SRC"
    # In-tree on purpose: an out-of-tree build writes absolute source paths
    # into the .d files, and on Windows those carry a drive colon make
    # cannot parse. One tree, cleaned between ABIs.
    [ -f ffbuild/config.mak ] && make -s distclean >/dev/null 2>&1 || true
    ./configure \
        --prefix="$prefix" \
        --enable-cross-compile --target-os=android --arch="$arch" --cpu="$cpu" \
        --host-cc="${HOST_CC:-gcc}" \
        --cc="$TC/clang$EXE" --cxx="$TC/clang++$EXE" \
        --ar="$TC/llvm-ar$EXE" --nm="$TC/llvm-nm$EXE" \
        --ranlib="$TC/llvm-ranlib$EXE" --strip="$TC/llvm-strip$EXE" \
        --extra-cflags="--target=$triple$API -fPIC -O2 $extra" \
        --extra-ldflags="--target=$triple$API" \
        --enable-gpl --enable-version3 \
        --enable-static --disable-shared --enable-pic \
        --disable-autodetect --disable-programs --disable-doc \
        --disable-avdevice --disable-avformat --disable-swscale \
        --enable-avfilter \
        --disable-network --disable-everything \
        --enable-decoder="$DECODERS" \
        ${EXTRA_CONFIGURE:-} $6 >/dev/null
    make -j"$JOBS" >/dev/null
    make install >/dev/null

    local out="$REPO/app/src/main/jniLibs/$abi"
    mkdir -p "$out"
    # The NDK compiler is a native binary: on MSYS a path glued to a flag
    # (-I/d/...) reaches it untranslated, so hand it Windows paths there.
    local native_prefix="$prefix"
    command -v cygpath >/dev/null && native_prefix="$(cygpath -m "$prefix")"
    # avcodec and avfilter reference each other in librempeg, hence the group.
    "$TC/clang++$EXE" --target="$triple$API" -shared -fPIC -O2 -fvisibility=hidden \
        -I"$native_prefix/include" "$HERE/ffmpeg_jni.cc" \
        -L"$native_prefix/lib" \
        -Wl,--start-group -lavcodec -lavfilter -lavutil -Wl,--end-group -llog -lm \
        -static-libstdc++ -Wl,--exclude-libs,ALL -Wl,--gc-sections \
        -Wl,-z,max-page-size=16384 -Wl,-soname,libffmpegJNI.so \
        -o "$out/libffmpegJNI.so"
    "$TC/llvm-strip$EXE" --strip-unneeded "$out/libffmpegJNI.so"
    echo "    $(wc -c < "$out/libffmpegJNI.so") bytes -> $out/libffmpegJNI.so"
}

ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64}"
for abi in $ABIS; do
    case $abi in
        arm64-v8a) build_abi arm64-v8a aarch64-linux-android aarch64 armv8-a "" "" ;;
        armeabi-v7a) build_abi armeabi-v7a armv7a-linux-androideabi arm armv7-a "-mfpu=neon -mfloat-abi=softfp" "--enable-neon" ;;
        # No nasm on the build machine; x86_64 is the emulator, not a phone.
        x86_64) build_abi x86_64 x86_64-linux-android x86_64 x86-64 "" "--disable-x86asm" ;;
        *) echo "unknown ABI $abi" >&2; exit 1 ;;
    esac
done
