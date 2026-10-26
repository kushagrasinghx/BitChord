package com.music.bitchord.ui.player

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A TextureView owns its texture until onSurfaceTextureDestroyed returns false.
 * The decoder owns its output until a surface change/release acknowledges it.
 * All methods below run on the video owner's serial executor.
 */
internal class VideoOutputLease<O : Any>(
    private val createOutput: () -> O,
    private val destroyOutput: (O) -> Unit,
    private val destroyTexture: () -> Unit,
) {
    private var output: O? = null
    private var outputReleased = false
    private var textureDestroyed = false
    private var textureReleased = false

    fun output(): O {
        check(!outputReleased)
        return output ?: createOutput().also { output = it }
    }

    fun releaseOutput() {
        if (outputReleased) return
        outputReleased = true
        output?.let(destroyOutput)
        output = null
        releaseTextureIfUnused()
    }

    fun textureDestroyed() {
        textureDestroyed = true
        releaseTextureIfUnused()
    }

    private fun releaseTextureIfUnused() {
        if (textureDestroyed && outputReleased && !textureReleased) {
            textureReleased = true
            destroyTexture()
        }
    }
}

/**
 * Serial player/resource ownership. Callers enqueue work and never wait for the
 * decoder. In particular an old texture survives a blocked setOutput/release.
 * The executor must always enqueue, including calls made on its own thread.
 */
internal class AsyncVideoLifecycle<P : Any, O : Any>(
    private val executor: Executor,
    private val createPlayer: () -> P,
    private val setOutput: (P, O?) -> Unit,
    private val releasePlayer: (P) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private val closeRequested = AtomicBoolean()
    private var player: P? = null // Executor-confined, like the leases below.
    private var output: VideoOutputLease<O>? = null
    private var pendingOutput: VideoOutputLease<O>? = null

    val isClosed: Boolean get() = closeRequested.get()

    fun command(action: (P) -> Unit) = submit {
        if (!isClosed) action(player ?: createPlayer().also { player = it })
    }

    fun attach(surface: VideoOutputLease<O>) = submit {
        if (isClosed) {
            surface.releaseOutput()
        } else if (output !== surface) {
            // Keep both outputs alive even if creating/switching the player
            // fails. The failure path releases the decoder before either one.
            pendingOutput = surface
            val current = player ?: createPlayer().also { player = it }
            setOutput(current, surface.output())
            val previous = output
            output = surface
            pendingOutput = null
            previous?.releaseOutput()
        }
    }

    fun detach(surface: VideoOutputLease<O>) = submit {
        surface.textureDestroyed()
        if (output === surface) {
            player?.let { setOutput(it, null) }
            output = null
        }
        // A delayed destruction callback from an old view must not clear the
        // replacement surface. Releasing a lease twice is harmless.
        surface.releaseOutput()
    }

    fun close() {
        if (closeRequested.compareAndSet(false, true)) submit { releaseOnOwner() }
    }

    private fun releaseOnOwner() {
        val current = player
        player = null
        try {
            current?.let(releasePlayer)
        } finally {
            try {
                output?.releaseOutput()
            } finally {
                output = null
                pendingOutput?.releaseOutput()
                pendingOutput = null
            }
        }
    }

    private fun submit(action: () -> Unit) {
        executor.execute {
            try {
                action()
            } catch (failure: Exception) {
                closeRequested.set(true)
                try {
                    releaseOnOwner()
                } catch (cleanup: Exception) {
                    failure.addSuppressed(cleanup)
                }
                onFailure(failure)
            }
        }
    }
}
