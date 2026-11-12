# DST decoder

`decoder.c` derives from Peter Ross's FFmpeg DST decoder through DSD-Nexus,
commit `b3f443d4c42ccf52806eb57def3b3a34805714bd`.

Source: https://github.com/wichers/dsd-nexus/tree/b3f443d4c42ccf52806eb57def3b3a34805714bd/libs/libdst

License: LGPL-2.1-or-later. See `COPYING.LGPL-2.1`.

Modified in September 2026: the utility dependency is replaced with bounded
input access (`compat.h`), output capacity and uncoded-frame lengths are
validated, table/filter errors are propagated, and a JNI entry point
(`jni.cpp`) is added. The files in this directory are distributed under the
same LGPL terms. Existing copyright notices remain in `decoder.c`.

The decoder is built as its own shared library, `libbitchord_dst.so`, by the
parent CMake file, so it can be rebuilt or replaced independently of the rest
of the app with the NDK and the repository's Gradle wrapper. Reverse
engineering for debugging modifications to this library is permitted.

Support covers mono/stereo DSD64 frames with shared, unsegmented
filter/probability data. Other segmentation modes return an explicit
unsupported error. A file is never treated as uncompressed DSD after a decode
failure.
