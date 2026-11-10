/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

val LocalSettingsDialogShowing: ProvidableCompositionLocal<MutableState<Boolean>> =
    compositionLocalOf { mutableStateOf(false) }

@Composable
fun rememberSettingsDialogHostState(): MutableState<Boolean> = remember { mutableStateOf(false) }
