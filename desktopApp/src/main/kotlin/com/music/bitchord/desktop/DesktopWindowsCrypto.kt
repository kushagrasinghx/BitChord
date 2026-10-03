package com.music.bitchord.desktop

import com.sun.jna.platform.win32.Crypt32Util

/** Small, isolated bridge to the current Windows user's Data Protection API. */
internal object DesktopWindowsCrypto {
    fun protect(plain: ByteArray): ByteArray? {
        if (!DesktopPlatform.isWindows || plain.isEmpty()) return null
        return runCatching { Crypt32Util.cryptProtectData(plain) }.getOrNull()
    }

    fun unprotect(sealed: ByteArray): ByteArray? {
        if (!DesktopPlatform.isWindows || sealed.isEmpty()) return null
        return runCatching { Crypt32Util.cryptUnprotectData(sealed) }.getOrNull()
    }
}
