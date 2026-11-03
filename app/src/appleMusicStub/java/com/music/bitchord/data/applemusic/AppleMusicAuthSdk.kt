package com.music.bitchord.data.applemusic

import android.content.Context
import androidx.activity.result.ActivityResultCaller

/** No reflection, credential collection, or pretend connected state in a no-SDK build. */
internal fun createAppleMusicAuthAdapter(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") caller: ActivityResultCaller,
): AppleMusicAuthAdapter = object : AppleMusicAuthAdapter {
    override fun unavailableReason() = AppleMusicAuthProblem.SDK_MISSING
    override suspend fun authorize(developerToken: String) =
        AppleMusicAuthorization.Failed(AppleMusicAuthProblem.SDK_MISSING)
}
