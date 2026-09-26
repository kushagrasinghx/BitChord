package com.music.bitchord.desktop

import com.music.bitchord.data.innertube.InnerTubeXResolver

/** InnerTubeX's remote player config, kept in the desktop's preferences as the phone keeps it in its own. */
internal object DesktopInnerTubeXStore : InnerTubeXResolver.ConfigStore {
    private val persistence by lazy { DesktopPersistence() }

    override fun getString(key: String): String? = persistence.string(PREFIX + key, "").takeIf { it.isNotEmpty() }

    override fun getLong(key: String): Long? = persistence.string(PREFIX + key, "").toLongOrNull()

    override fun putString(key: String, value: String) = persistence.saveString(PREFIX + key, value)

    override fun putLong(key: String, value: Long) = persistence.saveString(PREFIX + key, value.toString())

    private const val PREFIX = "innertubex_"
}
