package com.music.bitchord.ui.tv.personalization

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.text.BreakIterator

enum class AppThemeOption(
    val id: String,
    val title: String,
    val description: String,
) {
    DYNAMIC_ARTWORK(
        id = "dynamic_artwork",
        title = "Dynamic Artwork",
        description = "Apple Music luxury dark base with dynamic artwork-derived accent highlights.",
    ),
    MIDNIGHT(
        id = "midnight",
        title = "Midnight",
        description = "Deep charcoal-navy background with cool sapphire and violet accents.",
    ),
    PURE_BLACK(
        id = "pure_black",
        title = "Pure Black (OLED)",
        description = "True #000000 pitch black canvas engineered for maximum OLED contrast.",
    );

    companion object {
        fun fromId(id: String?): AppThemeOption = when (id?.lowercase()) {
            "midnight" -> MIDNIGHT
            "pure_black", "oled" -> PURE_BLACK
            else -> DYNAMIC_ARTWORK
        }
    }
}

@Immutable
data class TvThemePalette(
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceFocused: Color,
    val surfaceSelected: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentGlow: Color,
    val borderFocused: Color,
)

fun AppThemeOption.getPalette(): TvThemePalette = when (this) {
    AppThemeOption.DYNAMIC_ARTWORK -> TvThemePalette(
        background = Color(0xFF08080B),
        backgroundElevated = Color(0xFF101015),
        surface = Color(0xFF17171E),
        surfaceVariant = Color(0xFF22222C),
        surfaceFocused = Color(0xFF323242),
        surfaceSelected = Color(0xFF38151D),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA0A0AB),
        textMuted = Color(0xFF6E6E7A),
        accent = Color(0xFFFFFFFF),
        accentGlow = Color(0x66FFFFFF),
        borderFocused = Color(0xFFFFFFFF),
    )
    AppThemeOption.MIDNIGHT -> TvThemePalette(
        background = Color(0xFF0A0E17),
        backgroundElevated = Color(0xFF111827),
        surface = Color(0xFF1E293B),
        surfaceVariant = Color(0xFF334155),
        surfaceFocused = Color(0xFF475569),
        surfaceSelected = Color(0xFF1E1B4B),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        accent = Color(0xFF7C5CFC),
        accentGlow = Color(0x667C5CFC),
        borderFocused = Color(0xFF818CF8),
    )
    AppThemeOption.PURE_BLACK -> TvThemePalette(
        background = Color(0xFF000000),
        backgroundElevated = Color(0xFF080808),
        surface = Color(0xFF121212),
        surfaceVariant = Color(0xFF1E1E1E),
        surfaceFocused = Color(0xFF2C2C2C),
        surfaceSelected = Color(0xFF202020),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB0B0B0),
        textMuted = Color(0xFF707070),
        accent = Color(0xFFFFFFFF),
        accentGlow = Color(0x66FFFFFF),
        borderFocused = Color(0xFFFFFFFF),
    )
}

sealed class NicknameValidationResult {
    data object Valid : NicknameValidationResult()
    data class Invalid(val reason: String) : NicknameValidationResult()
}

object NicknamePolicy {
    const val MIN_GRAPHEMES = 1
    const val MAX_GRAPHEMES = 24
    const val DEFAULT_NICKNAME = "Listener"

    fun validate(raw: String): NicknameValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return NicknameValidationResult.Invalid("Nickname cannot be empty")
        }
        if (trimmed.contains('\n') || trimmed.contains('\r') || trimmed.contains('\t')) {
            return NicknameValidationResult.Invalid("No line breaks allowed")
        }
        val count = getGraphemeCount(trimmed)
        if (count > MAX_GRAPHEMES) {
            return NicknameValidationResult.Invalid("Must be 24 characters or less ($count/$MAX_GRAPHEMES)")
        }
        return NicknameValidationResult.Valid
    }

    fun getGraphemeCount(text: String): Int {
        if (text.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var count = 0
        while (it.next() != BreakIterator.DONE) {
            count++
        }
        return count
    }

    /**
     * Unicode-safe backspace that deletes exactly one grapheme cluster before cursor without splitting emoji.
     */
    fun deletePreviousGrapheme(text: String, cursorIndex: Int): Pair<String, Int> {
        if (text.isEmpty() || cursorIndex <= 0) return Pair(text, cursorIndex)
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val prevBoundary = it.preceding(cursorIndex)
        if (prevBoundary == BreakIterator.DONE) return Pair(text, cursorIndex)
        val newText = text.substring(0, prevBoundary) + text.substring(cursorIndex)
        return Pair(newText, prevBoundary)
    }

    /**
     * Unicode-safe cursor movement to the left by one grapheme cluster.
     */
    fun moveCursorLeft(text: String, cursorIndex: Int): Int {
        if (text.isEmpty() || cursorIndex <= 0) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val prev = it.preceding(cursorIndex)
        return if (prev != BreakIterator.DONE) prev else 0
    }

    /**
     * Unicode-safe cursor movement to the right by one grapheme cluster.
     */
    fun moveCursorRight(text: String, cursorIndex: Int): Int {
        if (text.isEmpty() || cursorIndex >= text.length) return text.length
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val next = it.following(cursorIndex)
        return if (next != BreakIterator.DONE) next else text.length
    }
}
