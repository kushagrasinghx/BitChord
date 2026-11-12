package com.music.bitchord.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.ui.screens.SettingsCategory
import com.music.bitchord.ui.screens.SettingsChoiceKind

/**
 * A page pushed over the tabs: Settings and every page under it, and the
 * handful of pages a tab opens that are not a release.
 *
 * Album, artist and playlist pages are not routes. They live on
 * [com.music.bitchord.ui.MainViewModel.detailStack], which loads them and
 * updates them in place, and [AppNavigator] interleaves the two stacks into
 * one — see [RouteEntry.detailDepth].
 */
sealed interface AppRoute {
    data object Settings : AppRoute
    data object AccountScrobbling : AppRoute
    data object Sources : AppRoute
    data object ListenTogether : AppRoute
    data object WearOs : AppRoute
    data object Equalizer : AppRoute
    data object Replay : AppRoute
    data object History : AppRoute
    data object Discord : AppRoute
    data object Spotify : AppRoute
    data object Downloads : AppRoute

    /** A Library shelf's "Show all" — its own cards laid out again as a grid. */
    data class LibraryShowAll(val shelf: HomeShelf) : AppRoute

    /**
     * An artist page's "Show all" for one shelf. Not a page of its own: the
     * artist page draws the shelf in place, so this only records that it is
     * open — which is what lets an album opened from it come back to it.
     */
    data class ArtistShelf(val browseId: String, val shelf: HomeShelf) : AppRoute

    /** Find Your Mood's Show all: every mood and activity, as a grid. */
    data object Moods : AppRoute

    /** One category of Settings, opened from its row on the main Settings page. */
    data class SettingsSection(val category: SettingsCategory) : AppRoute

    /** A Settings choice with more options than its row can show, as a page. */
    data class SettingsChoice(val choice: SettingsChoiceKind) : AppRoute

    /** The Equalizer's list of preset curves. */
    data object EqualizerPresets : AppRoute
}

/**
 * One route on the stack.
 *
 * @property id unique for the life of the process, so two visits to the same
 *   route never share saved state — see [AppDestination.key].
 * @property detailDepth how many release pages were open when this was pushed.
 *   Release pages above that number were opened from this route and sit over
 *   it; those at or below it were open before it and sit under it.
 */
data class RouteEntry(val id: Long, val route: AppRoute, val detailDepth: Int)

/**
 * The one back stack the whole app navigates by.
 *
 * Every page used to be a flag of its own, with a back handler per flag and
 * each handler guessing from the other flags whether its page was the one on
 * top. Two pages could be up at once, back could close the page underneath
 * the one on screen, and a flag no one remembered to clear survived a tab
 * switch and covered the tab. Here there is a single order instead: whatever
 * was opened last is on screen, and back undoes exactly that.
 *
 * Release pages stay on the view model's stack and are interleaved by depth
 * rather than copied in here, because that stack is also their data — pages
 * fill in, reload and are removed in place there.
 */
@Stable
class AppNavigator(
    private val detailDepth: () -> Int,
    private val popDetail: () -> Boolean,
    private val clearDetails: () -> Unit,
) {
    private val stack = mutableStateListOf<RouteEntry>()

    // Seeded from the clock rather than zero: page state is saved under these
    // ids, and that survives process death while this navigator does not, so a
    // fresh one counting from zero would hand a new page an old one's state.
    private var nextId = System.currentTimeMillis()

    /** Every route on the stack, bottom first. */
    val entries: List<RouteEntry> get() = stack

    /** The route on screen, or null when a release page or the tab itself is. */
    fun visibleEntry(depth: Int = detailDepth()): RouteEntry? =
        stack.lastOrNull()?.takeIf { depth <= it.detailDepth }

    fun visibleRoute(depth: Int = detailDepth()): AppRoute? = visibleEntry(depth)?.route

    /** Whether [route] is anywhere on the stack, on screen or under something. */
    operator fun contains(route: AppRoute): Boolean = stack.any { it.route == route }

    /** Opens [route] over whatever is on screen. Already on screen, it stays put. */
    fun push(route: AppRoute) {
        val depth = detailDepth()
        if (visibleRoute(depth) == route) return
        stack += RouteEntry(nextId++, route, depth)
    }

    /**
     * Opens a path of routes, each over the one before — Settings and then one
     * of its pages, say. Skipped when the last of them is already on screen,
     * and starts from whichever of them is.
     */
    fun pushAll(vararg routes: AppRoute) {
        if (routes.isEmpty() || visibleRoute() == routes.last()) return
        routes.forEach(::push)
    }

    /**
     * One step back: the release page or route on top is closed. False when
     * nothing is pushed and the tab itself is what is on screen.
     */
    fun pop(): Boolean {
        val top = stack.lastOrNull()
        if (detailDepth() > (top?.detailDepth ?: 0)) return popDetail()
        if (top == null) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Back to the bare tab: every route and every release page closed. */
    fun clear() {
        stack.clear()
        clearDetails()
    }

    /** Clears everything, then opens [routes] from the bare tab. */
    fun reset(vararg routes: AppRoute) {
        clear()
        routes.forEach(::push)
    }

    /**
     * Release pages can go without back being pressed — a playlist deleted
     * while its page is open, say. A route that counted them under it would
     * then hide the next release opened over it, so its count comes down with
     * the stack.
     */
    fun onDetailStackShrunk(depth: Int) {
        for (i in stack.indices) {
            val entry = stack[i]
            if (entry.detailDepth > depth) stack[i] = entry.copy(detailDepth = depth)
        }
    }
}

/** What fills the page area: the key the page switcher animates between. */
sealed interface AppDestination {
    /** Also what the page's saved state is held under while it is on the stack. */
    val key: String

    data class Tab(val index: Int) : AppDestination {
        override val key: String get() = "tab:$index"
    }

    data class Route(val entry: RouteEntry) : AppDestination {
        override val key: String get() = "route:${entry.id}"
    }

    /** Keyed by browse id alone, as the page's hoisted list state is. */
    data class Release(val browseId: String) : AppDestination {
        override val key: String get() = browseId
    }
}
