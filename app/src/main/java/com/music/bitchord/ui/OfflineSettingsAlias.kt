package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import com.music.bitchord.offline.OfflineLocalStore

@Composable
fun OfflineSettingsScreen(store: OfflineLocalStore, onBack: () -> Unit, onRefresh: () -> Unit) {
    OfflineLocalSettingsScreen(store = store, onBack = onBack, onRefresh = onRefresh)
}
