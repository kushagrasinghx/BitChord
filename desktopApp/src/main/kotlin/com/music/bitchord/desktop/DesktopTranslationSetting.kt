package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.TRANSLATION_LANGUAGES
import com.music.bitchord.data.lyrics.translationLanguageName

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which language the lyrics translate button translates *into*.
 *
 * Blank — the default — means "whatever the app is set to", and is stored as blank rather than
 * resolved once: someone who has never touched this has expressed no preference, and switching
 * BitChord to Spanish should carry their lyrics with it rather than leaving them on the English
 * they happened to be reading the day the setting was written.
 */
internal object DesktopTranslationSetting {

    private const val KEY = "translation_language"

    private val _language = MutableStateFlow(DesktopPersistence().string(KEY, ""))

    /** The stored tag, blank while it follows the app. */
    val language: StateFlow<String> = _language

    /** Blank restores "follow the app language". */
    fun set(tag: String) {
        _language.value = tag
        DesktopPersistence().saveString(KEY, tag)
    }

    /** The tag to actually send, which is the app's own language when nothing is set. */
    fun resolved(): String = _language.value.takeIf { it.isNotBlank() } ?: DesktopStrings.resolvedTag()

    /** What the Settings row says under its title. */
    fun describe(tag: String): String {
        val locale = Locale.forLanguageTag(DesktopStrings.resolvedTag().ifBlank { "en" })
        val name = translationLanguageName(resolvedFor(tag), locale)
        return if (tag.isBlank()) "$name · follows the app" else name
    }

    private fun resolvedFor(tag: String): String =
        tag.takeIf { it.isNotBlank() } ?: DesktopStrings.resolvedTag()
}
