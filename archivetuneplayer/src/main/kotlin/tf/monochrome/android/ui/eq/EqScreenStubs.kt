/*
 * BRIDGE — see tf/monochrome/android/audio/dsp/DspBridgeStubs.kt.
 *
 * ArchiveTune's EqualizerDialog embeds these two full-screen parametric
 * equalizer editors from the tf.monochrome engine stack. That stack — the
 * AutoEq measurement database, the per-band biquad chain, the spectrum
 * analyzer feeding the live graph — runs inside ArchiveTune's MusicService
 * with its native libraries, none of which this port carries (the whole
 * engine chain is stubbed inert). Carrying the editors without the engine
 * would render graphs that never move and knobs that turn nothing, so the
 * two sub-tabs close back out instead. The dialog's own tone/speed/pitch
 * and reverb controls — the halves wired to this port's equalizer hook —
 * are the copied originals and stay fully live.
 */

package tf.monochrome.android.ui.eq

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
    Box(Modifier.fillMaxSize())
}

@Composable
fun ParametricEqScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
    Box(Modifier.fillMaxSize())
}
