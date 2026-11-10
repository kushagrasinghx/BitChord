/*
 * BRIDGE — dagger.hilt's view-model marker, inert here: the bridge's
 * hiltViewModel() resolves through its own factory registry.
 */
package dagger.hilt.android.lifecycle

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class HiltViewModel
