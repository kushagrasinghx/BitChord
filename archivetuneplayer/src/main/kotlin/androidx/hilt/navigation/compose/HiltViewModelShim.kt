/*
 * BRIDGE — androidx.hilt.navigation.compose.hiltViewModel.
 *
 * The real one resolves through Hilt's generated factories; there is no Hilt
 * here. This shim constructs view models through the bridge registry (see
 * moe/rukamori/archivetune/bridge/BridgeViewModels), which the hosting app
 * seeds when it mounts the player. Call shapes match the real API: bare
 * hiltViewModel() and the typed hiltViewModel<VM>().
 */
package androidx.hilt.navigation.compose

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
