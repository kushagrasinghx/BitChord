package com.music.bitchord

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.ui.navigation.AppNavigator
import com.music.bitchord.ui.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app's one back stack, with a list standing in for the view model's
 * release pages.
 *
 * The cases are the ones the old per-page flags got wrong: a page opened from
 * a route has to come back to that route, and a route opened over a page has
 * to come back to that page, however deep the two are interleaved.
 */
class AppNavigatorTest {
    private val releases = mutableListOf<String>()
    private lateinit var navigator: AppNavigator

    @Before
    fun setUp() {
        navigator = AppNavigator(
            detailDepth = { releases.size },
            popDetail = {
                if (releases.isEmpty()) {
                    false
                } else {
                    releases.removeAt(releases.lastIndex)
                    navigator.onDetailStackShrunk(releases.size)
                    true
                }
            },
            clearDetails = {
                releases.clear()
                navigator.onDetailStackShrunk(0)
            },
        )
    }

    @Test
    fun backUnwindsAPlaylistOpenedFromSpotifyUnderSettings() {
        navigator.push(AppRoute.Settings)
        navigator.push(AppRoute.AccountScrobbling)
        navigator.push(AppRoute.Spotify)
        releases += "spotify:playlist"
        releases += "artist"
        assertNull(navigator.visibleEntry())

        assertTrue(navigator.pop())
        assertEquals(listOf("spotify:playlist"), releases)
        assertNull(navigator.visibleRoute())
        assertTrue(navigator.pop())
        assertEquals(AppRoute.Spotify, navigator.visibleRoute())
        assertTrue(navigator.pop())
        assertEquals(AppRoute.AccountScrobbling, navigator.visibleRoute())
        assertTrue(navigator.pop())
        assertEquals(AppRoute.Settings, navigator.visibleRoute())
        assertTrue(navigator.pop())
        assertNull(navigator.visibleRoute())
        assertFalse(navigator.pop())
    }

    @Test
    fun settingsOverAReleaseComesBackToIt() {
        releases += "album"
        navigator.push(AppRoute.Settings)
        assertEquals(AppRoute.Settings, navigator.visibleRoute())
        // Already on screen: a second tap on the account button stays put.
        navigator.push(AppRoute.Settings)
        assertEquals(1, navigator.entries.size)

        assertTrue(navigator.pop())
        assertNull(navigator.visibleRoute())
        assertEquals(listOf("album"), releases)
        assertTrue(navigator.pop())
        assertTrue(releases.isEmpty())
    }

    @Test
    fun aReleaseRemovedUnderARouteDoesNotHideTheNextOne() {
        releases += "playlist"
        navigator.push(AppRoute.History)
        // Deleted from under the route, not popped.
        releases.clear()
        navigator.onDetailStackShrunk(0)

        releases += "album"
        assertNull(navigator.visibleRoute())
        assertTrue(navigator.pop())
        assertEquals(AppRoute.History, navigator.visibleRoute())
    }

    @Test
    fun pushAllStartsFromWhicheverRouteIsShowing() {
        navigator.push(AppRoute.Settings)
        navigator.pushAll(AppRoute.Settings, AppRoute.ListenTogether)
        assertEquals(listOf(AppRoute.Settings, AppRoute.ListenTogether), navigator.entries.map { it.route })
        navigator.pushAll(AppRoute.Settings, AppRoute.ListenTogether)
        assertEquals(2, navigator.entries.size)
    }

    @Test
    fun clearAndResetLeaveNothingBehind() {
        navigator.push(AppRoute.Spotify)
        releases += "spotify:playlist"
        navigator.clear()
        assertTrue(navigator.entries.isEmpty())
        assertTrue(releases.isEmpty())
        assertFalse(navigator.pop())

        releases += "album"
        navigator.reset(AppRoute.Settings, AppRoute.ListenTogether)
        assertTrue(releases.isEmpty())
        assertEquals(AppRoute.ListenTogether, navigator.visibleRoute())
    }

    @Test
    fun showAllIsARouteLikeAnyOther() {
        navigator.push(AppRoute.LibraryShowAll(HomeShelf("Playlists", emptyList())))
        assertTrue(navigator.visibleRoute() is AppRoute.LibraryShowAll)
        releases += "playlist"
        assertTrue(navigator.pop())
        assertTrue(navigator.visibleRoute() is AppRoute.LibraryShowAll)
    }
}
