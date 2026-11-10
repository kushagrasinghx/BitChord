/*
 * BRIDGE — androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel, the
 * older import path some of the copied files use. Same resolution as the
 * navigation variant: the bridge registry, not Hilt.
 */
package androidx.hilt.lifecycle.viewmodel.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import moe.rukamori.archivetune.bridge.BridgeViewModels

@Composable
inline fun <reified VM : ViewModel> hiltViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(
        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current,
    ) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    },
    key: String? = null,
): VM = hiltViewModelInstance(VM::class.java, viewModelStoreOwner, key)

@PublishedApi
@Composable
internal fun <VM : ViewModel> hiltViewModelInstance(
    clazz: Class<VM>,
    owner: ViewModelStoreOwner,
    key: String?,
): VM {
    val factory = androidx.compose.runtime.remember { BridgeViewModels.asFactory() }
    val provider = androidx.lifecycle.ViewModelProvider(owner, factory)
    @Suppress("UNCHECKED_CAST")
    return (
        if (key != null) {
            provider.get(key, clazz)
        } else {
            provider.get(clazz)
        }
        ) as VM
}
