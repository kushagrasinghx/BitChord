package com.music.bitchord.desktop

import com.music.bitchord.playback.smart.AnalysisLog
import com.music.bitchord.playback.smart.NativeAnalysisLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/** Unpacks what the analyser needs out of the jar, once per install. */
internal object DesktopAnalysisRuntime {

    private val started = AtomicBoolean(false)

    /** Where unpacked resources live, alongside the desktop log. */
    private val home: Path by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        Path.of(base, "bitchord", "analysis")
    }

    /** Where unpacked resources and cached analyses live. */
    fun analysisHome(): java.io.File = home.toFile().also { Files.createDirectories(home) }

    /** True once the native analyser is loaded and usable. */
    val available: Boolean get() = ensureStarted().let { NativeAnalysisLibrary.available }

    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        AnalysisLog.install { message, error ->
            DesktopTrackLog.log(
                when {
                    error == null -> message
                    else -> "$message: ${error.message}" + hintFor(error)
                },
            )
        }
        NativeAnalysisLibrary.install {
            // `mapLibraryName` gives `libbitchord_analysis.so` on Linux and `bitchord_analysis.dll`
            // on Windows.
            val fileName = System.mapLibraryName(NativeAnalysisLibrary.LIBRARY_NAME)
            System.load(unpack("/native/$fileName", fileName).toString())
        }
    }

    /** A sentence worth adding to a failure whose real cause is off-screen. */
    private fun hintFor(error: Throwable): String {
        if (!DesktopPlatform.isWindows) return ""
        val text = generateSequence(error, Throwable::cause).mapNotNull { it.message }.joinToString(" ")
        val missingRuntime = "dependent libraries" in text ||
            "OrtSession" in text ||
            "onnxruntime" in text && "UnsatisfiedLink" in error::class.java.simpleName
        return if (missingRuntime) {
            " — install the Microsoft Visual C++ Redistributable (x64); ONNX Runtime is built against it"
        } else {
            ""
        }
    }

    /**
     * Unpacks and loads a native library that travels in the jar.
     *
     * The analyser's own load goes through [NativeAnalysisLibrary]; this is the
     * same unpack for anything else that ships beside it, so there is one place
     * that knows where the jar keeps them.
     */
    fun loadNative(name: String) {
        val fileName = System.mapLibraryName(name)
        System.load(unpack("/native/$fileName", fileName).toString())
    }

    /** The absolute path of a model, unpacked on first use. */
    fun modelPath(asset: String): String = unpack("/models/$asset", asset).toString()

    /**
     * Copies a classpath resource to [name] under [home], skipping the copy when a file of the same
     * size is already there.
     */
    private fun unpack(resource: String, name: String): Path {
        Files.createDirectories(home)
        val target = home.resolve(name)
        val stream = javaClass.getResourceAsStream(resource)
            ?: error("$resource is missing from this build")
        stream.use { input ->
            val expected = javaClass.getResource(resource)?.openConnection()?.contentLengthLong ?: -1L
            if (Files.exists(target) && expected > 0 && Files.size(target) == expected) return target
            val partial = home.resolve("$name.partial")
            Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING)
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }
}
