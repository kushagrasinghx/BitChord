package com.music.bitchord.data.innertube

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PremiumAudioPolicyTest {
    @Test
    fun loggedInHighQualityCanProbePremiumTier() {
        assertTrue(PremiumAudioPolicy.shouldProbe(true, true, Int.MAX_VALUE, false))
    }

    @Test
    fun neverTryPremiumForAnonymousOrLowerQualityOrUploads() {
        assertFalse(PremiumAudioPolicy.shouldProbe(false, true, Int.MAX_VALUE, false))
        assertFalse(PremiumAudioPolicy.shouldProbe(true, false, Int.MAX_VALUE, false))
        assertFalse(PremiumAudioPolicy.shouldProbe(true, true, 64, false))
        assertFalse(PremiumAudioPolicy.shouldProbe(true, true, Int.MAX_VALUE, true))
    }

    @Test
    fun standardOpusIsNotMislabelledAsPremium() {
        assertFalse(PremiumAudioPolicy.isPremiumTier(0))
        assertFalse(PremiumAudioPolicy.isPremiumTier(139))
        assertFalse(PremiumAudioPolicy.isPremiumTier(160))
        assertTrue(PremiumAudioPolicy.isPremiumTier(220))
        assertTrue(PremiumAudioPolicy.isPremiumTier(256))
    }
}
