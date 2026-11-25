package com.music.bitchord.data.backup

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Permission to keep one hidden folder in the listener's Google Drive.
 *
 * Through Google Play services' own authorization sheet, not a browser or a
 * WebView: the accounts offered are the ones already on the phone, and the
 * consent screen is the system's. Google refuses OAuth inside embedded
 * WebViews in any case.
 *
 * The one scope asked for is `drive.appdata` — a folder only this app can see,
 * which the listener can size up and delete from Drive's "Manage apps", and
 * nothing else in their Drive. It is the same arrangement WhatsApp uses.
 *
 * Once granted, [authorize] hands back a fresh token without any UI, which is
 * what lets the scheduled backup run with the app closed. When it can't — the
 * grant was revoked, the account removed — it says so with a [PendingIntent]
 * that only the screen can launch.
 */
object DriveAuth {

    private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    private const val ACCOUNT_TYPE = "com.google"

    sealed interface Outcome {
        data class Granted(val accessToken: String) : Outcome

        /** The sheet has to be shown: only an activity can start [intent]. */
        data class NeedsConsent(val intent: PendingIntent) : Outcome
    }

    /** Whether this device has Google Play services to sign in with at all. */
    fun available(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /**
     * A token for [account], or the sheet that will get one.
     *
     * With [account] null the sheet lets the listener pick from every account
     * on the phone; with it set, the grant is looked up for that one only, so
     * a phone with two Google accounts never quietly backs up into the other.
     */
    suspend fun authorize(context: Context, account: String?): Outcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_APPDATA)))
            .apply { if (account != null) setAccount(Account(account, ACCOUNT_TYPE)) }
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        return result.toOutcome()
    }

    /** What the sheet returned, once the listener has been through it. */
    fun fromIntent(context: Context, data: Intent?): Outcome {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        return result.toOutcome()
    }

    /**
     * Drops a token Drive has refused, so the next [authorize] mints another
     * rather than handing the same expired one back from its cache.
     */
    suspend fun clearToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
        Unit
    }

    /**
     * Withdraws the grant itself, as "Disconnect" promises: Google's revoke
     * endpoint ends the grant behind the token, not just the token. Best
     * effort — with no grant left to find a token for, there is nothing to do.
     */
    suspend fun revoke(context: Context, account: String) = withContext(Dispatchers.IO) {
        runCatching {
            val token = (authorize(context, account) as? Outcome.Granted)?.accessToken ?: return@runCatching
            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/revoke")
                .post(FormBody.Builder().add("token", token).build())
                .build()
            Http.client.newCall(request).execute().close()
            GoogleAuthUtil.clearToken(context, token)
        }
        Unit
    }

    private fun AuthorizationResult.toOutcome(): Outcome {
        val pending = pendingIntent
        if (hasResolution() && pending != null) return Outcome.NeedsConsent(pending)
        val token = accessToken ?: error("Google didn't return an access token")
        return Outcome.Granted(token)
    }
}

/** A Play services task as a suspend call. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
