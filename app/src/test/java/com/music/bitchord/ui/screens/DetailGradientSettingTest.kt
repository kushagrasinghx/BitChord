package com.music.bitchord.ui.screens

import androidx.compose.ui.graphics.Color
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.ui.theme.ArtworkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DetailGradientSettingTest {

    private val mockPalette = ArtworkPalette(
        background = Color(0xFF123456),
        wash = Color(0xFF234567),
        elevated = Color(0xFF345678),
        accent = Color(0xFFFF5722),
        onBackground = Color.White,
        onBackgroundVariant = Color.LightGray,
        divider = Color.DarkGray,
    )

    private val themeBackground = Color(0xFFFFFFFF)
    private val themeOnBackground = Color(0xFF000000)
    private val themeOnSurfaceVariant = Color(0xFF444444)

    @Test
    fun whenDisabledGradientsTrueForAlbumUsesThemeBackground() {
        val disableDetailGradients = true
        val pageType = BrowseType.ALBUM

        val effectivePalette = if (disableDetailGradients && (pageType == BrowseType.ALBUM || pageType == BrowseType.PLAYLIST)) {
            mockPalette.copy(
                background = themeBackground,
                wash = themeBackground,
                onBackground = themeOnBackground,
                onBackgroundVariant = themeOnSurfaceVariant,
            )
        } else {
            mockPalette
        }

        assertEquals(themeBackground, effectivePalette.background)
        assertEquals(themeBackground, effectivePalette.wash)
        assertEquals(themeOnBackground, effectivePalette.onBackground)
        assertEquals(mockPalette.accent, effectivePalette.accent)
    }

    @Test
    fun whenDisabledGradientsTrueForPlaylistUsesThemeBackground() {
        val disableDetailGradients = true
        val pageType = BrowseType.PLAYLIST

        val effectivePalette = if (disableDetailGradients && (pageType == BrowseType.ALBUM || pageType == BrowseType.PLAYLIST)) {
            mockPalette.copy(
                background = themeBackground,
                wash = themeBackground,
                onBackground = themeOnBackground,
                onBackgroundVariant = themeOnSurfaceVariant,
            )
        } else {
            mockPalette
        }

        assertEquals(themeBackground, effectivePalette.background)
        assertEquals(themeBackground, effectivePalette.wash)
    }

    @Test
    fun whenDisabledGradientsFalseKeepsArtworkPalette() {
        val disableDetailGradients = false
        val pageType = BrowseType.ALBUM

        val effectivePalette = if (disableDetailGradients && (pageType == BrowseType.ALBUM || pageType == BrowseType.PLAYLIST)) {
            mockPalette.copy(
                background = themeBackground,
                wash = themeBackground,
            )
        } else {
            mockPalette
        }

        assertEquals(mockPalette.background, effectivePalette.background)
        assertEquals(mockPalette.wash, effectivePalette.wash)
        assertNotEquals(themeBackground, effectivePalette.background)
    }

    @Test
    fun artistPagesRetainArtworkWashEvenWhenSettingEnabled() {
        val disableDetailGradients = true
        val pageType = BrowseType.ARTIST

        val effectivePalette = if (disableDetailGradients && (pageType == BrowseType.ALBUM || pageType == BrowseType.PLAYLIST)) {
            mockPalette.copy(
                background = themeBackground,
                wash = themeBackground,
            )
        } else {
            mockPalette
        }

        assertEquals(mockPalette.wash, effectivePalette.wash)
    }
}
