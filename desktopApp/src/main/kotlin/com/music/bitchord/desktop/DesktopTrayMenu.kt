package com.music.bitchord.desktop

import com.music.bitchord.desktop.tray.DBusMenu
import com.music.bitchord.desktop.tray.DBusMenuEvent
import com.music.bitchord.desktop.tray.DBusMenuItem
import com.music.bitchord.desktop.tray.DBusMenuLayout
import com.music.bitchord.desktop.tray.DBusMenuProperties
import com.music.bitchord.desktop.tray.DBusMenuShowGroup
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.EventQueue

/** The tray menu, described rather than drawn. */
internal object DesktopTrayMenu {

    /** The root's id in the protocol; every real entry hangs off it. */
    private const val ROOT_ID = 0

    private const val ID_NOW_PLAYING = 1
    private const val ID_SEPARATOR_TOP = 2
    private const val ID_PLAY_PAUSE = 3
    private const val ID_NEXT = 4
    private const val ID_PREVIOUS = 5
    private const val ID_SEPARATOR_MIDDLE = 6
    private const val ID_OPEN_PLAYER = 7
    private const val ID_SETTINGS = 8
    private const val ID_SEPARATOR_BOTTOM = 9
    private const val ID_QUIT = 10

    private val lock = Any()

    private var nowPlaying: String? = null
    private var isPlaying = false
    private var revision = 1

    private var playPause: () -> Unit = {}
    private var next: () -> Unit = {}
    private var previous: () -> Unit = {}
    private var openPlayer: () -> Unit = {}
    private var openSettings: () -> Unit = {}
    private var quit: () -> Unit = {}

    /** Told to the host so it knows the menu it holds has gone stale. */
    private var onLayoutChanged: (Int) -> Unit = {}

    fun bind(
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onOpenPlayer: () -> Unit,
        onOpenSettings: () -> Unit,
        onQuit: () -> Unit,
    ) = synchronized(lock) {
        playPause = onPlayPause
        next = onNext
        previous = onPrevious
        openPlayer = onOpenPlayer
        openSettings = onOpenSettings
        quit = onQuit
    }

    fun onLayoutChanged(listener: (Int) -> Unit) = synchronized(lock) { onLayoutChanged = listener }

    /** Keeps the menu's text in step with playback; silent when nothing moved. */
    fun publish(title: String?, playing: Boolean) {
        val next = synchronized(lock) {
            if (title == nowPlaying && playing == isPlaying) return
            nowPlaying = title
            isPlaying = playing
            ++revision
        }
        onLayoutChanged(next)
    }

    fun revision(): Int = synchronized(lock) { revision }

    /** The menu as it stands. */
    private fun entries(): List<Entry> = synchronized(lock) {
        buildList {
            nowPlaying?.let { playing ->
                add(Entry(ID_NOW_PLAYING, label = playing, enabled = false))
                add(Entry(ID_SEPARATOR_TOP, separator = true))
            }
            add(
                Entry(
                    ID_PLAY_PAUSE,
                    label = if (isPlaying) "Pause" else "Play",
                    icon = if (isPlaying) "media-playback-pause" else "media-playback-start",
                    action = playPause,
                ),
            )
            add(Entry(ID_NEXT, label = DesktopStrings["widget_next", "Next"], icon = "media-skip-forward", action = next))
            add(Entry(ID_PREVIOUS, label = DesktopStrings["widget_previous", "Previous"], icon = "media-skip-backward", action = previous))
            add(Entry(ID_SEPARATOR_MIDDLE, separator = true))
            add(Entry(ID_OPEN_PLAYER, label = DesktopStrings["widget_open_bitchord", "Open BitChord"], icon = "media-playback-start", action = openPlayer))
            add(Entry(ID_SETTINGS, label = DesktopStrings["settings", "Settings"], icon = "preferences-system", action = openSettings))
            add(Entry(ID_SEPARATOR_BOTTOM, separator = true))
            add(Entry(ID_QUIT, label = DesktopStrings["d_quit_bitchord", "Quit BitChord"], icon = "application-exit", action = quit))
        }
    }

    /** The same menu, as AWT's own widgets. */
    fun awtPopup(): java.awt.PopupMenu = java.awt.PopupMenu().apply {
        entries().forEach { entry ->
            if (entry.separator) {
                addSeparator()
            } else {
                add(
                    java.awt.MenuItem(entry.label).apply {
                        isEnabled = entry.enabled
                        val action = entry.action
                        if (entry.enabled) addActionListener { EventQueue.invokeLater(action) }
                    },
                )
            }
        }
    }

    /** Runs whatever the clicked entry does, on the toolkit's own thread. */
    private fun activate(id: Int) {
        val action = entries().firstOrNull { it.id == id }?.action ?: return
        EventQueue.invokeLater(action)
    }

    private data class Entry(
        val id: Int,
        val label: String = "",
        val separator: Boolean = false,
        val enabled: Boolean = true,
        val icon: String? = null,
        val action: (() -> Unit)? = null,
    ) {
        /**
         * `_` marks a mnemonic in a dbusmenu label, so a title carrying one would lose it and gain
         * an underline.
         */
        fun properties(): Map<String, Variant<*>> = buildMap {
            if (separator) {
                put("type", Variant("separator"))
                return@buildMap
            }
            put("label", Variant(label.replace("_", "__")))
            put("enabled", Variant(enabled))
            put("visible", Variant(true))
            icon?.let { put("icon-name", Variant(it)) }
        }
    }

    /** The object the shell talks to. */
    internal class Exported : DBusMenu, Properties {

        override fun getObjectPath(): String = OBJECT_PATH

        override fun GetLayout(
            parentId: Int,
            recursionDepth: Int,
            propertyNames: Array<String>,
        ): DBusMenuLayout<UInt32, DBusMenuItem> {
            val wanted = propertyNames.toSet()
            val item = if (parentId == ROOT_ID) {
                // Depth 0 means "the whole tree"; anything else still fits, because this menu is
                // one level deep by design.
                val children = if (recursionDepth == 0) {
                    emptyList()
                } else {
                    entries().map { Variant(it.node(wanted), "(ia{sv}av)") }
                }
                DBusMenuItem(ROOT_ID, mapOf("children-display" to Variant("submenu")), children)
            } else {
                entries().firstOrNull { it.id == parentId }?.node(wanted)
                    ?: DBusMenuItem(parentId, emptyMap(), emptyList())
            }
            return DBusMenuLayout(UInt32(revision().toLong()), item)
        }

        override fun GetGroupProperties(ids: IntArray, propertyNames: Array<String>): List<DBusMenuProperties> {
            val wanted = propertyNames.toSet()
            val requested = ids.toSet()
            return entries()
                .filter { requested.isEmpty() || it.id in requested }
                .map { DBusMenuProperties(it.id, it.properties().retaining(wanted)) }
        }

        override fun GetProperty(id: Int, name: String): Variant<*> =
            entries().firstOrNull { it.id == id }?.properties()?.get(name) ?: Variant("")

        override fun Event(id: Int, eventId: String, data: Variant<*>?, timestamp: UInt32?) {
            if (eventId == "clicked") activate(id)
        }

        override fun EventGroup(events: List<DBusMenuEvent>): List<Int> {
            events.forEach { event -> if (event.eventId == "clicked") activate(event.id) }
            // An empty list is the protocol's "every one of them landed".
            return emptyList()
        }

        /**
         * Answered `true` so the host re-reads before it draws: the play/pause entry's label
         * depends on state that may have moved since the last time it looked.
         */
        override fun AboutToShow(id: Int): Boolean = true

        override fun AboutToShowGroup(ids: IntArray): DBusMenuShowGroup<IntArray, IntArray> =
            DBusMenuShowGroup(ids, IntArray(0))

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, property: String): A =
            menuProperties()[property] as A

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = menuProperties()

        override fun <A : Any?> Set(interfaceName: String, property: String, value: A) = Unit

        private fun menuProperties(): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(3)),
            "Status" to Variant("normal"),
            "TextDirection" to Variant("ltr"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )

        private fun Entry.node(wanted: Set<String>): DBusMenuItem =
            DBusMenuItem(id, properties().retaining(wanted), emptyList())

        /** An empty request means every property, which is what hosts send. */
        private fun Map<String, Variant<*>>.retaining(wanted: Set<String>): Map<String, Variant<*>> =
            if (wanted.isEmpty()) this else filterKeys { it in wanted }
    }

    /** Where the menu is exported, and what the tray icon points at. */
    const val OBJECT_PATH = "/MenuBar"

    /** The root's id, for the host's benefit when the layout changes. */
    const val ROOT = ROOT_ID
}
