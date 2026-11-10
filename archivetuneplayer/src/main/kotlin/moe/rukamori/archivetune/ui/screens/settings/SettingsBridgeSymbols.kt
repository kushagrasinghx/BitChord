/*
 * BRIDGE — the two symbols ArchiveTune's appearance settings screen
 * exported that the player reads. The settings screens themselves are this
 * app's own (already ported in earlier rounds); the player just wants the
 * dark-mode enum and the po-token route.
 */
package moe.rukamori.archivetune.ui.screens.settings

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

const val PO_TOKEN_ROUTE = "settings/po_token"
