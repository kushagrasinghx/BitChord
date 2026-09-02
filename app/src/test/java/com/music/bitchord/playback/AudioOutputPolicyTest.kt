package com.music.bitchord.playback

import com.music.bitchord.data.settings.OutputPcmMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputPolicyTest {
    @Test
    fun floatRequiresPreferredUsbRouteAndAdvertisedSupport() {
        assertTrue(
            AudioOutputPolicy.shouldUseFloatOutput(
                OutputPcmMode.FLOAT_32,
                isPreferredUsbRoute = true,
                advertisesPcmFloat = true,
            ),
        )
    }

    @Test
    fun floatFallsBackOnSpeakerEvenWhenRequested() {
        assertFalse(
            AudioOutputPolicy.shouldUseFloatOutput(
                OutputPcmMode.FLOAT_32,
                isPreferredUsbRoute = false,
                advertisesPcmFloat = true,
            ),
        )
    }

    @Test
    fun floatFallsBackWhenUsbDoesNotAdvertiseIt() {
        assertFalse(
            AudioOutputPolicy.shouldUseFloatOutput(
                OutputPcmMode.FLOAT_32,
                isPreferredUsbRoute = true,
                advertisesPcmFloat = false,
            ),
        )
    }

    @Test
    fun pcm16NeverRequestsFloat() {
        assertFalse(
            AudioOutputPolicy.shouldUseFloatOutput(
                OutputPcmMode.PCM_16,
                isPreferredUsbRoute = true,
                advertisesPcmFloat = true,
            ),
        )
    }

    @Test
    fun samsungVendorFlacDecoderIsBlockedForFloatOutput() {
        assertTrue(AudioOutputPolicy.isUnsafeFloatFlacDecoder("c2.sec.flac.decoder"))
        assertTrue(AudioOutputPolicy.isUnsafeFloatFlacDecoder("OMX.SEC.FLAC.Decoder"))
        assertFalse(AudioOutputPolicy.isUnsafeFloatFlacDecoder("c2.android.flac.decoder"))
    }
}
