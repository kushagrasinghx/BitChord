/*
 * BRIDGE — dagger.hilt's static accessor, answered from this module's own
 * registry instead of a generated component.
 */
package dagger.hilt.android

import android.content.Context

object EntryPointAccessors {
    fun <T : Any> fromApplication(
        @Suppress("UNUSED_PARAMETER") context: Context,
        entryPoint: Class<T>,
    ): T {
        return moe.rukamori.archivetune.di.BridgeEntryPointRegistry.resolve(entryPoint)
    }
}
