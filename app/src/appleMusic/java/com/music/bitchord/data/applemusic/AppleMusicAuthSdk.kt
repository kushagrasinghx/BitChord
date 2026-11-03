package com.music.bitchord.data.applemusic

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.apple.android.sdk.authentication.TokenError
import com.music.bitchord.BuildConfig
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Uses Apple's MusicKitAuth 1.1.5 interfaces from the official SDK archive.
 */
internal fun createAppleMusicAuthAdapter(context: Context, caller: ActivityResultCaller): AppleMusicAuthAdapter {
    val registry = (caller as? ActivityResultRegistryOwner)?.activityResultRegistry
    val owner = caller as? LifecycleOwner
    if (registry == null || owner == null) {
        return object : AppleMusicAuthAdapter {
            override fun unavailableReason() = AppleMusicAuthProblem.RUNTIME_UNAVAILABLE
            override suspend fun authorize(developerToken: String) =
                AppleMusicAuthorization.Failed(AppleMusicAuthProblem.RUNTIME_UNAVAILABLE)
        }
    }
    return MusicKitAuthAdapter(context.applicationContext, registry, owner)
}

private class MusicKitAuthAdapter(
    private val context: Context,
    registry: ActivityResultRegistry,
    owner: LifecycleOwner,
) : AppleMusicAuthAdapter {
    private val manager by lazy { AuthenticationFactory.createAuthenticationManager(context) }
    private var pending: CancellableContinuation<AppleMusicAuthorization>? = null
    // Do not restore an old Activity's native result into a newly created controller.
    // A fresh registry key also prevents an old result from satisfying a new attempt
    // after recreation/process restoration. Users reconnect after a cancelled recreation.
    private val launcher = registry.register(
        "bitchord.applemusic.auth.${UUID.randomUUID()}", owner, ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        val request = pending
        pending = null
        if (request != null && request.isActive) {
            val result = try {
                val data = activityResult.data
                if (data == null) {
                    AppleMusicAuthorization.Failed(
                        if (activityResult.resultCode == Activity.RESULT_CANCELED) AppleMusicAuthProblem.CANCELLED
                        else AppleMusicAuthProblem.AUTHORIZATION_DENIED,
                    )
                } else {
                    // Read SDK errors even if the outer Activity result was RESULT_CANCELED.
                    val token = manager.handleTokenResult(data)
                    if (token.isError) {
                        AppleMusicAuthorization.Failed(
                            when (token.error) {
                                TokenError.USER_CANCELLED -> AppleMusicAuthProblem.CANCELLED
                                TokenError.NO_SUBSCRIPTION -> AppleMusicAuthProblem.SUBSCRIPTION_REQUIRED
                                TokenError.SUBSCRIPTION_EXPIRED -> AppleMusicAuthProblem.SUBSCRIPTION_EXPIRED
                                TokenError.TOKEN_FETCH_ERROR -> AppleMusicAuthProblem.BACKEND
                                else -> AppleMusicAuthProblem.AUTHORIZATION_DENIED
                            },
                        )
                    } else {
                        val userToken = token.musicUserToken
                        if (userToken != null && validAuthToken(userToken)) {
                            val playbackStorefront = token.appleMusicStorefrontId
                            if (playbackStorefront.isNullOrBlank()) {
                                AppleMusicAuthorization.Failed(AppleMusicAuthProblem.STOREFRONT)
                            } else {
                                AppleMusicAuthorization.Authorized(userToken, playbackStorefront)
                            }
                        } else {
                            AppleMusicAuthorization.Failed(AppleMusicAuthProblem.AUTHORIZATION_DENIED)
                        }
                    }
                }
            } catch (_: LinkageError) {
                AppleMusicAuthorization.Failed(AppleMusicAuthProblem.RUNTIME_UNAVAILABLE)
            } catch (_: Exception) {
                AppleMusicAuthorization.Failed(AppleMusicAuthProblem.AUTHORIZATION_DENIED)
            }
            request.resume(result)
        }
    }

    override fun unavailableReason(): AppleMusicAuthProblem? {
        // File presence is not authorization validation. Never bypass the independent build gate.
        if (!BuildConfig.APPLE_MUSIC_AUTH_AVAILABLE) return AppleMusicAuthProblem.SDK_NOT_VALIDATED
        return try {
            context.packageManager.getPackageInfo("com.apple.android.music", 0)
            null
        } catch (_: PackageManager.NameNotFoundException) {
            AppleMusicAuthProblem.APP_MISSING
        } catch (_: Exception) {
            AppleMusicAuthProblem.RUNTIME_UNAVAILABLE
        }
    }

    override suspend fun authorize(developerToken: String): AppleMusicAuthorization = withContext(Dispatchers.Main.immediate) {
        unavailableReason()?.let { return@withContext AppleMusicAuthorization.Failed(it) }
        // A cancelled continuation remains a tombstone until its Activity result arrives.
        // Otherwise a late result from attempt A could be delivered to attempt B.
        if (pending != null) return@withContext AppleMusicAuthorization.Failed(AppleMusicAuthProblem.AUTHORIZATION_BUSY)
        suspendCancellableCoroutine<AppleMusicAuthorization> { continuation ->
            if (!continuation.isActive) return@suspendCancellableCoroutine
            pending = continuation
            try {
                launcher.launch(manager.createIntentBuilder(developerToken).build())
            } catch (_: ActivityNotFoundException) {
                pending = null
                continuation.resume(AppleMusicAuthorization.Failed(AppleMusicAuthProblem.APP_MISSING))
            } catch (_: LinkageError) {
                pending = null
                continuation.resume(AppleMusicAuthorization.Failed(AppleMusicAuthProblem.RUNTIME_UNAVAILABLE))
            } catch (_: Exception) {
                pending = null
                continuation.resume(AppleMusicAuthorization.Failed(AppleMusicAuthProblem.AUTHORIZATION_DENIED))
            }
        }
    }
}
