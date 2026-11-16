# Cross-building the Automix analyser for Windows from a Linux host.
#
# The sources are platform-neutral C++17 and plain JNI, so the only thing
# Windows-specific about the build is the toolchain and one header: `jni.h` is
# the same file everywhere, but it includes `jni_md.h`, which is per-platform
# and which a Linux JDK does not carry a Windows copy of. See
# `native/win32/jni_md.h`.
#
# Everything is linked statically so the result depends on nothing but the
# system DLLs — a portable archive that also had to carry libstdc++-6.dll and
# libgcc_s_seh-1.dll beside it would be a portable archive with a footnote.
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

set(CMAKE_C_COMPILER x86_64-w64-mingw32-gcc)
set(CMAKE_CXX_COMPILER x86_64-w64-mingw32-g++)
set(CMAKE_RC_COMPILER x86_64-w64-mingw32-windres)

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)

set(CMAKE_EXE_LINKER_FLAGS_INIT "-static -static-libgcc -static-libstdc++")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-static -static-libgcc -static-libstdc++")
