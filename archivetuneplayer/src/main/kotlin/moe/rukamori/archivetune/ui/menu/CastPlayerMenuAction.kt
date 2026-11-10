/*
 * BRIDGE — the foss shape of ArchiveTune's cast menu action (the gms flavor
 * wires Google Cast here; the ported player falls back to the system output
 * panel, exactly as ArchiveTune's foss builds do).
 */
package moe.rukamori.archivetune.ui.menu

import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.ui.component.NewAction

@Composable
fun rememberCastPlayerMenuAction(@Suppress("UNUSED_PARAMETER") renderSheet: Boolean = true): NewAction? = null
