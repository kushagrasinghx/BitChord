package com.music.bitchord.desktop

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import com.music.bitchord.desktop.tray.DBusMenu
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A tray icon, on the protocol this desktop actually speaks. */
private const val SNI_INTERFACE = "org.kde.StatusNotifierItem"
private const val SNI_OBJECT_PATH = "/StatusNotifierItem"
private const val SNI_WATCHER_NAME = "org.kde.StatusNotifierWatcher"
private const val SNI_WATCHER_PATH = "/StatusNotifierWatcher"

@DBusInterfaceName(SNI_WATCHER_NAME)
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

/** One icon at one size: `(iiay)`. */
class TrayPixmap(
    @JvmField @Position(0) val width: Int,
    @JvmField @Position(1) val height: Int,
    @JvmField @Position(2) val data: ByteArray,
) : Struct()

@DBusInterfaceName(SNI_INTERFACE)
interface StatusNotifierItem : DBusInterface {
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)
}

/** Publishes the tray icon and its menu, and keeps both in step with playback. */
internal class DesktopStatusNotifierController(
    private val onActivate: () -> Unit,
    private val onPlayPause: () -> Unit,
) {
    private val lock = Any()
    /** The thread the D-Bus work happens on, replaced whenever it has been shut down. */
    private var worker: ExecutorService? = null

    private fun worker(): ExecutorService = synchronized(lock) {
        worker?.takeIf { !it.isShutdown } ?: Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "BitChord-Tray").apply { isDaemon = true }
        }.also { worker = it }
    }

    private var connection: DBusConnection? = null
    private var started = false
    private var nowPlaying: String? = null
    private var playing = false

    /** The app's own logo, rendered once — see [setIcon]. */
    private var iconPixmaps: List<TrayPixmap> = emptyList()

    /** Hands the tray the application's own artwork. */
    fun setIcon(svg: ByteArray) {
        if (!isLinuxSession()) {
            // AWT wants one image at the size the tray asks for, not a ladder of pixmaps for a
            // shell to choose from.
            DesktopAwtTray.setIcon(renderSvg(svg, AWT_ICON_SIZE)?.toImage())
            return
        }
        val rendered = ICON_SIZES.mapNotNull { size -> renderSvg(svg, size) }
        if (rendered.isEmpty()) return
        synchronized(lock) { iconPixmaps = rendered }
        publishProperties()
    }

    fun start() {
        // Windows has no StatusNotifierItem; its tray is AWT's — see [DesktopAwtTray].
        if (!isLinuxSession()) {
            DesktopAwtTray.start(onActivate)
            return
        }
        if (synchronized(lock) { started }) return
        synchronized(lock) { started = true }
        worker().execute {
            runCatching {
                // Its own connection, not the shared default — see [DesktopMprisController] for why
                // sharing one breaks both.
                val bus = DBusConnectionBuilder.forSessionBus().withShared(false).build()
                bus.exportObject(SNI_OBJECT_PATH, ExportedItem(this))
                // The menu is a second object on the same connection, which the icon points at
                // through its `Menu` property.
                bus.exportObject(DesktopTrayMenu.OBJECT_PATH, DesktopTrayMenu.Exported())
                DesktopTrayMenu.onLayoutChanged { revision -> announceMenu(revision) }
                val watcher = bus.getRemoteObject(
                    SNI_WATCHER_NAME,
                    SNI_WATCHER_PATH,
                    StatusNotifierWatcher::class.java,
                )
                watcher.RegisterStatusNotifierItem(bus.uniqueName)
                synchronized(lock) { connection = bus }
            }.onFailure { failure ->
                synchronized(lock) {
                    connection?.close()
                    connection = null
                    started = false
                }
                // A session with no watcher is the ordinary case on plenty of desktops; say so once
                // rather than treating it as a fault.
                DesktopTrackLog.log("tray unavailable: ${failure.message}")
            }
        }
    }

    fun stop() {
        if (!isLinuxSession()) {
            DesktopAwtTray.stop()
            return
        }
        val bus = synchronized(lock) {
            started = false
            val current = connection
            connection = null
            current
        }
        runCatching { bus?.close() }
        synchronized(lock) {
            worker?.shutdownNow()
            worker = null
        }
    }

    fun update(title: String?, isPlaying: Boolean) {
        if (!isLinuxSession()) {
            DesktopAwtTray.update(title, isPlaying)
            return
        }
        val changed = synchronized(lock) {
            val was = nowPlaying to playing
            nowPlaying = title
            playing = isPlaying
            was != (title to isPlaying)
        }
        if (!changed) return
        publishProperties()
    }

    private fun publishProperties() {
        val bus = synchronized(lock) { connection } ?: return
        val runner = synchronized(lock) { worker } ?: return
        runner.execute {
            runCatching {
                bus.sendMessage(
                    Properties.PropertiesChanged(
                        SNI_OBJECT_PATH,
                        SNI_INTERFACE,
                        itemProperties(),
                        emptyList(),
                    ),
                )
            }.onFailure { DesktopTrackLog.log("tray update failed: ${it.message}") }
        }
    }

    /** Tells the host its copy of the menu is out of date. */
    private fun announceMenu(revision: Int) {
        val bus = synchronized(lock) { connection } ?: return
        val runner = synchronized(lock) { worker } ?: return
        runner.execute {
            runCatching {
                bus.sendMessage(
                    DBusMenu.LayoutUpdated(
                        DesktopTrayMenu.OBJECT_PATH,
                        UInt32(revision.toLong()),
                        DesktopTrayMenu.ROOT,
                    ),
                )
            }.onFailure { DesktopTrackLog.log("tray menu update failed: ${it.message}") }
        }
    }

    private fun icons(): List<TrayPixmap> = synchronized(lock) { iconPixmaps }

    private fun tooltipTitle(): String = synchronized(lock) { nowPlaying } ?: "BitChord"

    private fun itemProperties(): Map<String, Variant<*>> = mapOf(
        "Category" to Variant("ApplicationStatus"),
        "Id" to Variant("bitchord"),
        "Title" to Variant("BitChord"),
        "Status" to Variant("Active"),
        // The logo itself when it has been rendered, and a themed name as the fallback for a host
        // that ignores pixmaps.
        "IconName" to Variant(if (icons().isEmpty()) "multimedia-player" else ""),
        "IconPixmap" to Variant(icons(), "a(iiay)"),
        "ToolTip" to Variant(
            ToolTip("", icons(), "BitChord", tooltipTitle()),
        ),
        // Where the host reads the menu from.
        "Menu" to Variant(DBusPath(DesktopTrayMenu.OBJECT_PATH), "o"),
        "ItemIsMenu" to Variant(false),
    )

    /** `(sa(iiay)ss)` — icon name, pixmaps, title, description. */
    class ToolTip(
        @JvmField @Position(0) val iconName: String,
        @JvmField @Position(1) val iconPixmap: List<TrayPixmap>,
        @JvmField @Position(2) val title: String,
        @JvmField @Position(3) val description: String,
    ) : Struct()

    private class ExportedItem(
        private val owner: DesktopStatusNotifierController,
    ) : StatusNotifierItem, Properties {
        override fun getObjectPath(): String = SNI_OBJECT_PATH

        /** Left click: bring the player up. */
        override fun Activate(x: Int, y: Int) = owner.onActivate()

        /** Middle click: the one verb worth having without opening anything. */
        override fun SecondaryActivate(x: Int, y: Int) = owner.onPlayPause()

        /**
         * Right click, which a host that honours the advertised `Menu` never sends — it opens the
         * dbusmenu instead.
         */
        override fun ContextMenu(x: Int, y: Int) = Unit

        override fun Scroll(delta: Int, orientation: String) = Unit

        // The Variant itself, not its value.
        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, property: String): A =
            owner.itemProperties()[property] as A

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = owner.itemProperties()

        override fun <A : Any?> Set(interfaceName: String, property: String, value: A) = Unit
    }

}

/** The logo, rasterised at [size] and packed the way the protocol asks for it. */
/** The size Windows draws a tray icon at; AWT scales from it if it must. */
private const val AWT_ICON_SIZE = 32

/** The same pixels AWT wants, which counts them the other way round. */
private fun TrayPixmap.toImage(): java.awt.image.BufferedImage {
    val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val at = (y * width + x) * 4
            val a = data[at].toInt() and 0xFF
            val r = data[at + 1].toInt() and 0xFF
            val g = data[at + 2].toInt() and 0xFF
            val b = data[at + 3].toInt() and 0xFF
            image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
    }
    return image
}

private fun renderSvg(svg: ByteArray, size: Int): TrayPixmap? = runCatching {
    val dom = SVGDOM(Data.makeFromBytes(svg))
    dom.setContainerSize(size.toFloat(), size.toFloat())
    val surface = Surface.makeRasterN32Premul(size, size)
    // The container size alone does not resize an SVG that declares absolute width and height.
    val intrinsic = dom.root?.width?.value?.takeIf { it > 0f } ?: size.toFloat()
    surface.canvas.scale(size / intrinsic, size / intrinsic)
    dom.render(surface.canvas)
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32Premul(size, size))
    if (!surface.readPixels(bitmap, 0, 0)) return@runCatching null
    val bytes = bitmap.readPixels() ?: return@runCatching null

    val argb = ByteArray(size * size * 4)
    for (pixel in 0 until size * size) {
        val at = pixel * 4
        val b = bytes[at]
        val g = bytes[at + 1]
        val r = bytes[at + 2]
        val a = bytes[at + 3]
        argb[at] = a
        argb[at + 1] = r
        argb[at + 2] = g
        argb[at + 3] = b
    }
    TrayPixmap(size, size, argb)
}.getOrNull()

/** A host picks whichever of these suits its bar; offering one forces a rescale. */
private val ICON_SIZES = listOf(22, 32, 48)

private fun isLinuxSession(): Boolean =
    DesktopPlatform.isLinux
