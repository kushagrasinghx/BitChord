package com.music.bitchord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.sources.AddonSource
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.ui.components.AddonEditorAlert
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the app is allowed to get audio from.
 *
 * The order is fixed rather than something to argue with: the addons a user
 * added are tried first, because they are the sources chosen on purpose, and
 * YouTube Music is tried last, since it needs no setup and has the full
 * catalogue behind it. Nothing on this screen downloads code, and nothing on
 * it can teach the app a new way to behave after it has shipped — an addon
 * answers questions with JSON, and this app is the only thing here running
 * anything.
 */
@Composable
fun SourcesScreen(
    contentPadding: PaddingValues,
    /**
     * Asks the activity to open the editor for a source — or for a new one,
     * when handed a config the registry does not have yet.
     *
     * Raised rather than drawn here, and not as a style preference: this screen
     * sits inside the subtree carrying `hazeSource`, so a frosted card drawn
     * from within it is part of the very layer it samples and comes out with no
     * background at all. Hosting it at the activity — where every other alert
     * in this app already lives — is also what lets the scrim cover the tab bar
     * and the mini player.
     */
    onEditSource: (SourceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle()
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()

    /** Last known reachability per source, filled in as the probes come back. */
    val health = remember { mutableStateMapOf<String, SourceHealth>() }

    // Every source that has a server to reach is probed, so an addon gets a
    // reachability line — which is the only feedback that a URL just pasted in
    // was any good, and the only place a manifest that fails validation gets to
    // say why.
    val probeKey = configs.filter { it.kind.needsServer }.joinToString { "${it.id}@${it.baseUrl}" }
    LaunchedEffect(probeKey) {
        configs.filter { it.kind.needsServer && it.isComplete }.forEach { config ->
            val source = SourceRegistry.instance(config.id) ?: return@forEach
            health[config.id] = withContext(Dispatchers.IO) {
                runCatching { source.health() }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
            // An addon names itself, so the row takes that name rather than a
            // hostname — and rather than a field asking the user to make one up.
            //
            // Written here, off the probe that has just fetched the manifest
            // anyway, because this is the one place that both talks to every
            // configured source and is allowed to change what is stored. It
            // settles after one pass: the next probe finds the label already
            // equal and writes nothing, so there is no loop between this and
            // the [configs] it is reading. A rename on the addon's side is
            // picked up the next time this screen is opened.
            if (source is AddonSource) {
                val named = withContext(Dispatchers.IO) { runCatching { source.manifestName() }.getOrNull() }
                if (named != null && named != config.label) {
                    SourceRegistry.update(config.copy(label = named))
                }
            }
        }
    }

    // The ceiling in force *right now*, which is the only one this screen can
    // speak for: the rows below are switches the user set once, and whether a
    // given source is reached also depends on which connection is up. Reading
    // it here rather than baking it into the switches is deliberate — the
    // preset write that used to do that is what made a mobile-data rung follow
    // the user onto Wi-Fi. See [AudioQuality.permits].
    val ceiling = if (metered == true) cellularQuality else wifiQuality

    /** Whether the ceiling in force right now would cap a lossless stream anyway. */
    val cappedByQuality = ceiling != AudioQuality.LOSSLESS
    // Asked of the kinds themselves rather than of the module specifically, so
    // a source added later answers this on its own terms instead of being
    // invisible to it.
    val anyLosslessSource = configs.any { it.enabled && it.isComplete && it.kind.canServeLossless }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.source),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        SettingsGroup(
            header = stringResource(R.string.sources_order_header),
            footer = buildString {
                append(stringResource(R.string.sources_order_footer))
                if (cappedByQuality) {
                    append("\n\n")
                    append(stringResource(R.string.sources_quality_capped))
                } else if (!anyLosslessSource) {
                    append("\n\n")
                    append(stringResource(R.string.sources_no_lossless))
                }
            },
        ) {
            // The addons are split out from the rest because they are the only
            // rows whose order is the *user's*. Everything else ranks by kind,
            // which is fixed in [SourceKind] and not something a drag should be
            // able to argue with — a gesture that let JioSaavn be dragged above
            // an addon would be offering a choice the resolver does not
            // actually honour.
            //
            // They are still one continuously numbered list. The numbers say
            // what order the sources are tried in, and restarting the count
            // under a second heading would break the one thing this screen is
            // for.
            val addons = configs.filter { it.kind.isUserAdded }
            val fixed = configs.filterNot { it.kind.isUserAdded }
                .sortedBy { it.kind.rank }

            val row: @Composable (Int, SourceConfig, (@Composable () -> Unit)?) -> Unit =
                { position, config, handle ->
                    SourceRow(
                        position = position,
                        config = config,
                        health = health[config.id],
                        // Switched on, but not reached on this connection. Said
                        // on the row rather than by moving the switch, so the
                        // switch keeps meaning "I want this source" and the
                        // connection keeps meaning "…and here is what it costs
                        // today".
                        skippedByQuality = config.enabled && !ceiling.permits(config.kind),
                        onMetered = metered == true,
                        ceiling = ceiling,
                        // Anything the user configured is theirs to edit or
                        // delete. JioSaavn and YouTube have no address to
                        // change, so a tap on them would open an empty editor.
                        onClick = if (config.kind.needsServer) ({ onEditSource(config) }) else null,
                        // YouTube gets no switch at all — see
                        // [SourceRegistry.setEnabled] for why one would be a lie.
                        onToggle = if (config.kind == SourceKind.YOUTUBE) {
                            null
                        } else {
                            ({ SourceRegistry.setEnabled(config.id, it) })
                        },
                        handle = handle,
                    )
                }

            ReorderableAddons(
                addons = addons,
                onReorder = { SourceRegistry.reorderAddons(it.map(SourceConfig::id)) },
                row = row,
            )

            // No leading divider when addons came first: each of those wrappers
            // ends with one, which is what makes them all the same height for
            // the drag to measure against.
            fixed.forEachIndexed { index, config ->
                if (index > 0) RowDivider()
                row(addons.size + index + 1, config, null)
            }

            RowDivider()
            AddSourceRow(
                onClick = { onEditSource(SourceConfig(kind = SourceKind.ADDON)) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }

}

/**
 * The addon rows, draggable by their handles to set which is asked first.
 *
 * The mechanics are [LyricsSourcesDialog][com.music.bitchord.ui.components.LyricsSourcesDialog]'s,
 * because that list solved the same problem first and its notes are worth
 * reading: the gesture keeps only how far the finger has travelled and which
 * slot it started on, and both where the row is drawn and which slot it
 * occupies are derived from those two numbers, so they cannot drift apart
 * however many swaps happen along the way.
 *
 * Two things differ here. The list is on a vertically scrolling screen rather
 * than in a dialog, which is why the drag lives on the handle alone —
 * [detectDragGestures] there consumes the vertical drag before the scroll
 * container sees it, while a drag anywhere on the row would make the list
 * impossible to scroll past. And the order is only written back when the
 * gesture ends: [SourceRegistry.reorderAddons] persists to encrypted prefs and
 * rebuilds the source instances, which is not work to do on every frame of a
 * drag.
 *
 * No handle is drawn for a single addon. There is nothing to reorder, and a
 * grip that cannot move anything is a control that lies.
 */
@Composable
private fun ReorderableAddons(
    addons: List<SourceConfig>,
    onReorder: (List<SourceConfig>) -> Unit,
    row: @Composable (Int, SourceConfig, (@Composable () -> Unit)?) -> Unit,
) {
    if (addons.isEmpty()) return
    if (addons.size == 1) {
        row(1, addons.first(), null)
        // The same trailing rule the reorderable rows below emit, so the row
        // that follows this block is separated whichever branch drew it.
        RowDivider()
        return
    }

    var liveOrder by remember(addons) { mutableStateOf(addons) }
    var dragged by remember { mutableStateOf<String?>(null) }

    /** Distance the finger has covered since this gesture began, in pixels. */
    var totalDrag by remember { mutableFloatStateOf(0f) }

    /** Which slot of [liveOrder] it began on. */
    var startIndex by remember { mutableIntStateOf(0) }

    // The distance from one row's top to the next — the row plus the hairline
    // above it, measured off the wrapper holding both. Frozen for the duration
    // of a gesture so a relayout mid-drag cannot move the boundaries the drag
    // is being measured against underneath it.
    var pitchPx by remember { mutableFloatStateOf(0f) }
    var lockedPitchPx by remember { mutableFloatStateOf(0f) }

    liveOrder.forEachIndexed { index, config ->
        // Keyed on the config's id so this composable — gesture and all —
        // follows that addon from slot to slot. Matched by position instead,
        // the first swap would change the key under the finger, restart the
        // `pointerInput` coroutine mid-gesture, and stall the drag one swap
        // after it started.
        key(config.id) {
            val isDragging = config.id == dragged
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged { pitchPx = it.height.toFloat() }
                    .graphicsLayer {
                        // Read in the draw phase, so a drag moves the row
                        // without recomposing the list at all. The row sits
                        // wherever the finger has carried it from where it was
                        // picked up, less whatever the swaps have already moved
                        // its slot — so a swap relocates the slot and shortens
                        // this by exactly as much, and the row does not budge.
                        translationY = if (isDragging) {
                            totalDrag -
                                (liveOrder.indexOfFirst { it.id == config.id } - startIndex) * lockedPitchPx
                        } else {
                            0f
                        }
                    },
            ) {
                row(index + 1, config) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(20.dp)
                            // A constant key on purpose: the row is pinned by
                            // [key] above, so nothing about a reorder should
                            // restart this coroutine.
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragged = config.id
                                        totalDrag = 0f
                                        startIndex = liveOrder.indexOfFirst { it.id == config.id }
                                        lockedPitchPx = pitchPx
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val pitch = lockedPitchPx
                                        if (pitch <= 0f) return@detectDragGestures
                                        var at = liveOrder.indexOfFirst { it.id == config.id }
                                        if (at < 0) return@detectDragGestures

                                        // Held past either end the row stops
                                        // there under the finger, rather than
                                        // running off the list and having to be
                                        // dragged all the way back.
                                        totalDrag = (totalDrag + delta.y).coerceIn(
                                            -startIndex * pitch,
                                            (liveOrder.lastIndex - startIndex) * pitch,
                                        )

                                        // A loop, not an `if`: one pointer event
                                        // can cover several rows when the finger
                                        // is quick, and settling one row per
                                        // event would leave the list trailing.
                                        while (true) {
                                            val travelled = totalDrag / pitch
                                            val moved = (at - startIndex).toFloat()
                                            if (travelled > moved + SWAP_THRESHOLD && at < liveOrder.lastIndex) {
                                                liveOrder = liveOrder.toMutableList()
                                                    .apply { add(at + 1, removeAt(at)) }
                                                at++
                                            } else if (travelled < moved - SWAP_THRESHOLD && at > 0) {
                                                liveOrder = liveOrder.toMutableList()
                                                    .apply { add(at - 1, removeAt(at)) }
                                                at--
                                            } else {
                                                break
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragged = null
                                        totalDrag = 0f
                                        onReorder(liveOrder)
                                    },
                                    onDragCancel = {
                                        dragged = null
                                        totalDrag = 0f
                                        liveOrder = addons
                                    },
                                )
                            },
                    )
                }
                // After the row, not before it, so every wrapper is exactly one
                // row plus one hairline — the pitch the drag measures against.
                // With the divider leading, the first wrapper was short by a
                // rule and whichever row reported its size last decided the
                // pitch for all of them.
                RowDivider()
            }
        }
    }
}

/**
 * How far past a neighbour the finger has to carry a row before the two trade
 * places, as a share of one row's pitch.
 *
 * Deliberately more than half, for the reason spelled out in
 * [LyricsSourcesDialog][com.music.bitchord.ui.components.LyricsSourcesDialog]:
 * at exactly half, a row that has just swapped lands precisely on the boundary
 * of swapping back, so the shake in any real finger flips it back and forth for
 * as long as it is held near a crossing.
 */
private const val SWAP_THRESHOLD = 0.6f

/**
 * The one row on this screen that adds something rather than describing
 * something.
 *
 * Deliberately at the bottom of the same group as the sources rather than off
 * in a section of its own: what it adds goes to the *top* of that list, and a
 * row sitting under the numbered ones is the clearest way to say "and you can
 * put another one in here".
 */
@Composable
private fun AddSourceRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Aligned with the numbers above it rather than with their icons, so
        // the plus reads as belonging to the same column the list is indexed by.
        Spacer(Modifier.width(24.dp))
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.add_addon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.add_addon_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SourceRow(
    position: Int,
    config: SourceConfig,
    health: SourceHealth?,
    onClick: (() -> Unit)?,
    /** Null for a source that cannot be switched off, which gets a label instead. */
    onToggle: ((Boolean) -> Unit)?,
    /** On, but skipped by the ceiling the current connection is set to. */
    skippedByQuality: Boolean = false,
    /** Which of the two ceilings [ceiling] is, so the row can name it. */
    onMetered: Boolean = false,
    ceiling: AudioQuality = AudioQuality.LOSSLESS,
    /**
     * The drag grip, for a row whose position is the user's to set. Passed in
     * rather than drawn here because the gesture belongs to the list that knows
     * the other rows — see [ReorderableAddons] — and null for every row whose
     * rank is fixed by its kind.
     */
    handle: (@Composable () -> Unit)? = null,
) {
    // Dimmed for the same reason an off source is: it is not in the walk. The
    // switch stays where the user left it, so the row reads "on, but not
    // today" rather than "off".
    val dimmed = !config.enabled || skippedByQuality
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(18.dp)
                .alpha(if (dimmed) 0.4f else 1f),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = when (config.kind) {
                SourceKind.ADDON -> Icons.Rounded.Extension
                SourceKind.CUSTOM_MODULE -> Icons.Rounded.Extension
                SourceKind.MODULE -> Icons.Rounded.Extension
                SourceKind.JIOSAAVN -> Icons.Rounded.GraphicEq // or some other icon
                SourceKind.YOUTUBE -> Icons.Rounded.PlayCircle
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(ICON_SIZE)
                .alpha(if (dimmed) 0.4f else 1f),
        )
        Spacer(Modifier.width(ICON_GAP))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (dimmed) 0.4f else 1f),
        ) {
            Text(
                text = config.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The ceiling outranks the health line: a source that isn't
                // going to be asked at all is not usefully described by
                // whether its server answered a probe.
                text = if (skippedByQuality) {
                    stringResource(
                        R.string.source_skipped_by_quality,
                        stringResource(if (onMetered) R.string.mobile_data else R.string.wifi),
                        ceiling.localizedLabel(),
                    )
                } else {
                    config.statusLine(health)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    // Only a rejection is coloured, and only when it is what
                    // the line actually says. A server that is merely down
                    // will be up again without anyone doing anything, and
                    // painting that red trains people to ignore the colour by
                    // the time it means something.
                    skippedByQuality -> MaterialTheme.colorScheme.onSurfaceVariant
                    health is SourceHealth.Rejected -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (onToggle == null) {
            Text(
                text = stringResource(R.string.always_on),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        // Outside the dimming above, and last in the row. A source switched off
        // still has a position worth setting for when it is switched back on,
        // so the grip stays at full strength on a row that is otherwise faded.
        if (handle != null) {
            Spacer(Modifier.width(4.dp))
            handle()
        }
    }
}

@Composable
private fun AudioQuality.localizedLabel(): String = stringResource(
    when (this) {
        AudioQuality.LOW -> R.string.low
        AudioQuality.MEDIUM -> R.string.medium
        AudioQuality.HIGH -> R.string.high
        AudioQuality.LOSSLESS -> R.string.lossless
    },
)

/** The second line of a row: what this source is, or what is wrong with it. */
@Composable
private fun SourceConfig.statusLine(health: SourceHealth?): String = when {
    !isComplete -> stringResource(R.string.source_setup_required)
    health is SourceHealth.Ok -> listOfNotNull(
        health.detail,
        kind.labels.take(3).joinToString(" · "),
    ).joinToString(" · ")
    health is SourceHealth.Rejected -> health.reason
    health is SourceHealth.Unreachable -> stringResource(R.string.source_unreachable, health.reason)
    kind.needsServer -> stringResource(R.string.checking)
    else -> kind.labels.take(3).joinToString(" · ")
}

/**
 * Add or edit a source that has an address.
 *
 * Test is offered rather than required: a server that happens to be asleep is
 * still worth saving, and refusing to store it until it answers would make
 * setting one up from a coffee shop impossible. What Test does buy is the
 * difference between "not answering" and "answering, but not with something
 * this app can use" — a manifest missing the `stream` resource is a mistake
 * worth hearing about before the first track rather than after it.
 */
@Composable
internal fun SourceEditorAlert(
    hazeState: HazeState,
    config: SourceConfig,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDelete: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val isNew = SourceRegistry.config(config.id) == null
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsGood by remember { mutableStateOf(false) }

    val connected = stringResource(R.string.connected)
    val unreadable = stringResource(R.string.source_unrecognised)
    val alreadyAdded = stringResource(R.string.source_already_added)

    /**
     * Identify what is at the URL, then say so.
     *
     * Both buttons run this — the only difference is whether a success is then
     * stored. Testing and saving asking the *same* question is the point: a
     * Save that skipped identification could store a config whose kind was
     * guessed, and the guess is exactly what this screen no longer makes.
     */
    fun run(thenSave: Boolean) {
        busy = true
        status = null
        scope.launch {
            val identified = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.identify(baseUrl.trim(), config.takeUnless { isNew }) }
                    .getOrElse { Result.failure(it) }
            }
            val found = identified.getOrNull()
            if (found == null) {
                statusIsGood = false
                status = identified.exceptionOrNull()?.message ?: unreadable
                busy = false
                return@launch
            }

            // Already here? Checked against the *identified* base rather than
            // the typed text, so an addon's root and its manifest.json are
            // recognised as the one source they are — which is the whole reason
            // this runs after identification and not before it. Reported on
            // Test as well as Save: finding out by pressing Test beats finding
            // out by ending up with the same catalogue searched twice on every
            // track.
            SourceRegistry.duplicateOf(found.baseUrl, exceptId = config.id.takeUnless { isNew })
                ?.let { existing ->
                    statusIsGood = false
                    status = String.format(alreadyAdded, existing.displayName)
                    busy = false
                    return@launch
                }

            // Identified, and now asked whether it actually works. The two are
            // different questions: a manifest can be perfectly well formed and
            // its server still be refusing every search.
            val health = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.probeCandidate(found) }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
            statusIsGood = health.isOk
            status = when (health) {
                is SourceHealth.Ok -> listOfNotNull(
                    connected,
                    found.kind.label,
                    health.detail,
                ).joinToString(" · ")
                is SourceHealth.Rejected -> health.reason
                is SourceHealth.Unreachable -> health.reason
            }
            busy = false

            // Saved even when the probe came back unhappy, but only once the
            // format is known: a server that is asleep is still worth storing —
            // that was true before and is why Test was never mandatory — while
            // a URL nothing can be made of is not.
            if (thenSave) {
                if (isNew) SourceRegistry.add(found) else SourceRegistry.update(found)
                onSaved()
            }
        }
    }

    AddonEditorAlert(
        hazeState = hazeState,
        title = if (isNew) {
            stringResource(R.string.add_addon)
        } else {
            config.displayName
        },
        description = stringResource(R.string.addon_url_description),
        urlValue = baseUrl,
        // A result describes the address it was run against, so the moment that
        // address is edited it stops being true and is cleared. Left up, it
        // would report "Connected" over a URL nobody has tried.
        onUrlChange = { baseUrl = it; status = null },
        urlPlaceholder = "https://my-addon.example.com",
        status = status,
        statusIsGood = statusIsGood,
        testing = busy,
        canSubmit = baseUrl.isNotBlank(),
        onTest = { run(thenSave = false) },
        onSave = { run(thenSave = true) },
        onRemove = if (isNew) null else onDelete,
        onDismiss = onDismiss,
    )
}
