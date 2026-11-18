package com.music.bitchord.auth

/**
 * A persisted, in-flight account switch.
 *
 * Stored in [AuthStore]'s encrypted prefs so that a process death in the middle of a switch is
 * detectable on the next launch instead of leaving the two accounts' state interleaved in the
 * shared session.
 */
data class AccountTransition(
    val from: String?,
    val to: String,
    val startedAt: Long,
)

/**
 * Pure helpers for the transition state machine, kept free of Android so they can be exercised
 * directly.
 */
object AccountTransitionRules {

    /** A transition is complete only when the target account is the active one. */
    fun isComplete(transition: AccountTransition?, activeAccountId: String?): Boolean =
        transition == null || (transition.to == activeAccountId && transition.to.isNotBlank())

    /** A transition is stale (and safe to discard) once its target is active. */
    fun shouldClear(transition: AccountTransition?, activeAccountId: String?): Boolean =
        transition != null && transition.to == activeAccountId

    /** A transition that never reached its target is recoverable by re-activating the target. */
    fun isRecoverable(transition: AccountTransition?, availableAccountIds: Set<String>): Boolean =
        transition != null && transition.to in availableAccountIds

    /** A transition whose target is no longer available must be rolled back to the source. */
    fun rollbackTarget(transition: AccountTransition?, availableAccountIds: Set<String>): String? =
        if (transition != null && transition.to !in availableAccountIds) transition.from else null
}
