package com.music.bitchord.ui.components.backdrop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import com.music.bitchord.ui.components.backdrop.effects.LensKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Checks cache lifetime and invalidation without invoking Android's renderer. */
class BackdropEffectCacheTest {
    private fun scope() = object : BackdropEffectScopeImpl() {
        override val shape = RoundedCornerShape(50)
    }

    private val baseLens = LensKey(
        input = null, width = 300f, height = 60f, padding = 0f,
        topLeft = 20f, topRight = 18f, bottomRight = 16f, bottomLeft = 14f,
        refractionHeight = 8f, refractionAmount = 8f,
        depthEffect = true, chromaticAberration = true,
    )

    private fun effect(input: RenderEffect? = null) = BlurEffect(input, 2f, 2f, TileMode.Clamp)

    @Test
    fun unchangedChainReusesEachStageAfterApplyResetsTheOutput() {
        val scope = scope()
        var creates = 0
        fun draw(): RenderEffect? {
            scope.apply {
                renderEffect = cachedEffect("color") { creates++; effect() }
                val input = renderEffect
                renderEffect = cachedEffect(baseLens.copy(input = input)) { creates++; effect(input) }
            }
            return scope.renderEffect
        }
        val first = draw()
        repeat(50) { assertSame(first, draw()) }
        assertEquals(2, creates)
    }

    @Test
    fun everyLensUniformChangeReplacesTheCachedSnapshot() {
        val changedKeys = listOf(
            baseLens.copy(width = 301f), baseLens.copy(height = 61f),
            baseLens.copy(padding = 1f),
            baseLens.copy(topLeft = 21f), baseLens.copy(topRight = 19f),
            baseLens.copy(bottomRight = 17f), baseLens.copy(bottomLeft = 15f),
            baseLens.copy(refractionHeight = 9f), baseLens.copy(refractionAmount = 9f),
            baseLens.copy(depthEffect = false), baseLens.copy(chromaticAberration = false),
            baseLens.copy(input = effect()),
        )
        for (changed in changedKeys) {
            val scope = scope()
            fun draw(key: LensKey): RenderEffect? {
                scope.apply { renderEffect = cachedEffect(key) { effect(key.input) } }
                return scope.renderEffect
            }
            val original = draw(baseLens)
            val updated = draw(changed)
            assertNotSame("Changed lens input $changed must create a new immutable snapshot", original, updated)
            assertSame(updated, draw(changed))
            // Animated values do not accumulate in an unbounded history cache.
            assertNotSame(original, draw(baseLens))
        }
    }

    @Test
    fun changedUpstreamEffectReplacesItsDependentLens() {
        val scope = scope()
        var colorCreates = 0
        var lensCreates = 0
        fun draw(saturation: Float): RenderEffect? {
            scope.apply {
                renderEffect = cachedEffect(saturation) {
                    colorCreates++
                    BlurEffect(null, saturation, saturation, TileMode.Clamp)
                }
                val input = renderEffect
                renderEffect = cachedEffect(baseLens.copy(input = input)) { lensCreates++; effect(input) }
            }
            return scope.renderEffect
        }
        val original = draw(1.8f)
        val updated = draw(2f)
        assertNotSame(original, updated)
        assertSame(updated, draw(2f))
        assertEquals(2, colorCreates)
        assertEquals(2, lensCreates)
    }

    @Test
    fun conditionalStagesAreTrimmedAndReenabledWithoutStaleResults() {
        val scope = scope()
        var colorCreates = 0
        var lensCreates = 0
        var color: RenderEffect? = null
        fun draw(lens: Boolean): RenderEffect? {
            scope.apply {
                renderEffect = cachedEffect("color") { colorCreates++; effect() }
                color = renderEffect
                if (lens) {
                    val input = renderEffect
                    renderEffect = cachedEffect(baseLens.copy(input = input)) { lensCreates++; effect(input) }
                }
            }
            return scope.renderEffect
        }
        val original = draw(true)
        assertSame(color, draw(false))
        val reenabled = draw(true)
        assertNotSame(original, reenabled)
        assertSame(reenabled, draw(true))
        assertEquals(1, colorCreates)
        assertEquals(2, lensCreates)
    }

    @Test
    fun resetDropsEffectsBeforeNodeReuse() {
        val scope = scope()
        var creates = 0
        fun draw(): RenderEffect? {
            scope.apply { renderEffect = cachedEffect(baseLens) { creates++; effect() } }
            return scope.renderEffect
        }
        val original = draw()
        scope.reset()
        assertEquals(null, scope.renderEffect)
        assertNotSame(original, draw())
        assertEquals(2, creates)
    }
}
