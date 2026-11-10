/*
 * BRIDGE — the login route constants ArchiveTune's LoginScreen exported.
 * The screen itself is not ported (this app has its own accounts); the
 * player only needs the route strings it navigates to after auth-style
 * errors, and those navigations land nowhere by design.
 */
package moe.rukamori.archivetune.ui.screens

import android.net.Uri

const val LOGIN_ROUTE = "login"
const val LOGIN_URL_ARGUMENT = "url"

fun buildLoginRoute(startUrl: String? = null): String {
    val resolvedUrl = startUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return LOGIN_ROUTE
    return "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT=${Uri.encode(resolvedUrl)}"
}
