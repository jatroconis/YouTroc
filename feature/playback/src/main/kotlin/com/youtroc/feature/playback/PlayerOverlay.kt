package com.youtroc.feature.playback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.playback.VideoQuality
import com.youtroc.core.domain.stream.StoryboardSpec
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocRed
import com.youtroc.feature.playback.overlay.DpadAction
import com.youtroc.feature.playback.overlay.OverlayReducer
import com.youtroc.feature.playback.overlay.OverlayState
import com.youtroc.feature.playback.overlay.PlaybackTimeFormatter
import com.youtroc.feature.playback.overlay.PlayerMenu
import com.youtroc.feature.playback.overlay.PlayerMenuReducer
import com.youtroc.feature.playback.overlay.ScrubSeekSync
import com.youtroc.feature.playback.overlay.SeekAmount
import com.youtroc.feature.playback.overlay.decideDpadAction
import com.youtroc.feature.playback.overlay.isDpadKey
import com.youtroc.feature.playback.quality.QualityMenu
import com.youtroc.feature.playback.settings.SettingsMenu
import com.youtroc.feature.playback.upnext.DetailUiState
import com.youtroc.feature.playback.upnext.InfoUpNextPanel
import kotlinx.coroutines.delay

/**
 * Debounce window for the decoupled-scrub commit: the overlay waits this long
 * after the LAST L/R scrub tick before issuing a single absolute seek to the
 * previewed position, so a held or rapid scrub never re-buffers per step.
 */
private const val SCRUB_COMMIT_DELAY_MS = 400L

/**
 * Custom Compose-for-TV player overlay (REQ-11, docs/07 § Player a pantalla
 * completa / § 10), Netflix-style redesign. When VISIBLE it stacks three
 * vertically navigable focus ZONES, top → bottom:
 *
 * 1. **Timeline scrubber** — a FOCUSABLE progress bar. While it owns focus,
 *    LEFT/RIGHT move a DECOUPLED preview cursor ([SeekAmount] steps) that
 *    commits as a SINGLE absolute seek ([onSeekTo]) once scrubbing settles —
 *    the engine is never seeked per tick, so a held/rapid scrub moves freely
 *    across the whole timeline without re-buffering per step (the Netflix/Prime
 *    model). OK toggles play/pause. It shows a distinct focused state (taller
 *    track + a thumb + brighter time label) so the user sees it is active. Live
 *    has no scrubbing (REQ-L7), so [LiveIndicator] replaces it and is
 *    deliberately non-focusable.
 * 2. **Controls row** — exactly TWO controls: play/pause and the ⚙ settings
 *    button (no prev/next — the old dead SkipPrevious/SkipNext buttons were
 *    removed). LEFT/RIGHT move between the two, CONTAINED so they never escape
 *    the screen (`focusProperties.exit` → [FocusRequester.Cancel] at the
 *    boundaries — the fix for the old transport row's LEFT-exits-player bug).
 * 3. **Up-next panel** ("A continuación", `upnext` package) — reachable by
 *    DOWN from the controls row.
 *
 * D-pad map (see [decideDpadAction] for the pure decision):
 * - Overlay HIDDEN: ANY D-pad press reveals the overlay with focus on
 *   play/pause. There is no reveal-then-seek anymore — seeking lives entirely
 *   on the focused scrubber.
 * - Controls row: OK toggles play/pause; LEFT/RIGHT move between the two
 *   controls (contained); UP → scrubber; DOWN → up-next panel.
 * - Scrubber: LEFT/RIGHT move the decoupled preview cursor (committed as one
 *   seek on settle, [SCRUB_COMMIT_DELAY_MS]); OK = play/pause; DOWN → controls;
 *   UP is contained (top zone).
 * - BACK: settings menu or up-next panel open → close it (restore focus to the
 *   controls row); else visible → hide overlay; else → the caller's `onBack`.
 *   LEFT/RIGHT never trigger exit.
 *
 * Focus is CONTAINED per zone with `focusGroup()` + `focusProperties` so no
 * zone lets focus escape the overlay. Focus moves into a zone are DEFERRED one
 * frame via `LaunchedEffect` (MAJOR M2, HomeShell's proven pattern): the
 * revealed zones only compose once [visible] flips to `true`, so requesting
 * focus inline during the key event would target a not-yet-attached node.
 *
 * Knows nothing about Media3 or the concrete engine — only the domain
 * [PlaybackState] snapshot and plain callbacks. Integration-only: exercising
 * real D-pad focus/timing needs a real TV input stack, so this is validated on
 * the TCL 55C6K rather than unit-tested. The pure pieces it delegates to
 * ([decideDpadAction], [OverlayReducer], [SeekAmount], [PlaybackTimeFormatter])
 * ARE unit-tested.
 *
 * The ⚙ control (REQ-S1/REQ-11) opens a two-tier "Ajustes" → "Calidad" menu —
 * [SettingsMenu] then [QualityMenu] — as modal layers this composable owns via
 * local [menu] state (a pure [PlayerMenu] driven by [PlayerMenuReducer]). While
 * any panel is open the outer key handler stops intercepting D-pad input, the
 * 4s auto-hide is suppressed, and a nested [BackHandler] composed AFTER the
 * overlay's own (LIFO makes it win) unwinds exactly one level per press
 * (REQ-S4), restoring focus to the ⚙ control that opened it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerOverlay(
    title: String,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier,
    storyboard: StoryboardSpec? = null,
    onPlayPause: () -> Unit = {},
    onSeekTo: (positionMs: Long) -> Unit = {},
    availableQualities: List<VideoQuality> = emptyList(),
    activeQuality: VideoQuality? = null,
    onSelectQuality: (VideoQuality) -> Unit = {},
    onSelectAuto: () -> Unit = {},
    isLive: Boolean = false,
    upNextState: DetailUiState = DetailUiState.Loading,
    onUpNextClick: (VideoCardUi) -> Unit = {},
    onUpNextRetry: () -> Unit = {},
    onPanelOpened: () -> Unit = {},
) {
    var overlayState by remember { mutableStateOf<OverlayState>(OverlayState.Hidden) }
    // hasFocus of the Column wrapping ALL three revealed zones (scrubber,
    // controls row, up-next panel) — "some zone owns focus", the gate for
    // counting D-pad presses as control activity (MAJOR M4).
    var anyZoneFocused by remember { mutableStateOf(false) }
    // Distinct from [anyZoneFocused]: tracks whether the TIMELINE SCRUBBER
    // specifically owns focus, since that is the only state in which L/R scrub
    // (see [decideDpadAction]).
    var scrubberFocused by remember { mutableStateOf(false) }
    // Decoupled scrub (Netflix/Prime/SmartTube model): while the user moves L/R
    // on the scrubber this accumulates a PREVIEW position only (null = not
    // scrubbing, so the bar tracks the live position). The engine is NOT touched
    // per tick — a single seek is committed once scrubbing settles (see the
    // debounce effect below), so seeking never re-buffers per step and the
    // timeline moves freely even into not-yet-buffered regions.
    var scrubPreviewMs by remember { mutableStateOf<Long?>(null) }
    // Pending-seek latch ([ScrubSeekSync]): the committed target the engine
    // has not caught up to yet. Keeps the bar (and the BASE of a follow-up
    // scrub) on the committed target instead of snapping back to a stale
    // engine position while the seek is still buffering.
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var lastActivityMs by remember { mutableLongStateOf(0L) }
    var pendingEnterControls by remember { mutableStateOf(false) }
    var longPressActive by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<PlayerMenu>(PlayerMenu.Closed) }
    var panelExpanded by remember { mutableStateOf(false) }
    // REQ-U6/design D4: tracks whether the panel CONTAINER itself currently
    // owns focus, distinct from [panelExpanded] (which only flips false on
    // BACK). UP-from-panel-to-controls leaves [panelExpanded] true but moves
    // focus away — this is what the auto-hide guard below keys on instead.
    var panelFocused by remember { mutableStateOf(false) }
    val neutralFocus = remember { FocusRequester() }
    // The single "controls entry" anchor: the play/pause button. Reveal lands
    // here, UP-from-panel and DOWN-from-scrubber return here.
    val playPauseFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    val upNextFocus = remember { FocusRequester() }
    // Distinct from [upNextFocus] (gate R3): the panel container itself carries
    // `upNextFocus`, while this one binds ONLY the NotAvailable/Offline/Error
    // inline message/Retry anchor inside `InfoUpNextPanel`.
    val upNextContentFocus = remember { FocusRequester() }
    val ajustesFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    // Gate S3: forward-safe restore hook, currently a no-op passthrough —
    // FASE-1's Ajustes has exactly one row.
    val calidadRowFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)

    val visible = overlayState is OverlayState.Revealed

    fun registerActivity(nowMs: Long = System.currentTimeMillis()) {
        overlayState = OverlayReducer.onActivity(nowMs)
        lastActivityMs = nowMs
    }

    LaunchedEffect(Unit) {
        runCatching { neutralFocus.requestFocus() }
    }

    // MAJOR M2: land focus on play/pause only AFTER the controls row actually
    // composes (this effect re-runs once `visible` flips to true), instead of
    // requesting focus inline during the key-event handler that revealed it.
    LaunchedEffect(visible, pendingEnterControls) {
        if (visible && pendingEnterControls) {
            runCatching { playPauseFocus.requestFocus() }
            pendingEnterControls = false
        }
    }

    // Decoupled-scrub COMMIT: once [scrubPreviewMs] stops changing for
    // [SCRUB_COMMIT_DELAY_MS] (the user settled on a spot), seek ONCE to it and
    // clear the preview so the scrubber tracks the live position again. Keyed on
    // [scrubPreviewMs] so every fresh scrub tick RESTARTS the timer — the seek
    // fires only when scrubbing pauses, never per tick (the whole point: no
    // re-buffer per step). Setting it back to null re-enters with a no-op.
    LaunchedEffect(scrubPreviewMs) {
        val target = scrubPreviewMs ?: return@LaunchedEffect
        delay(SCRUB_COMMIT_DELAY_MS)
        // Latch BEFORE clearing the preview so the bar hands off preview →
        // pending → engine with no frame where a stale engine position wins.
        pendingSeekMs = target
        currentOnSeekTo(target)
        scrubPreviewMs = null
    }

    // Release the pending-seek latch once the engine reports it caught up.
    LaunchedEffect(playbackState.positionMs, pendingSeekMs) {
        pendingSeekMs = ScrubSeekSync.resolvePending(pendingSeekMs, playbackState.positionMs)
    }

    // Gate R2: the Info+Up-Next panel is CONDITIONALLY composed (only when
    // [panelExpanded]), so focus into it is deferred one frame — same M2
    // pattern. [onPanelOpened] triggers the lazy `UpNextViewModel.ensureLoaded()`
    // (REQ-U3) the FIRST time the panel opens for the current video.
    LaunchedEffect(panelExpanded) {
        if (panelExpanded) {
            onPanelOpened()
            runCatching { upNextFocus.requestFocus() }
        }
    }

    // MAJOR-5 extended to the 3-level menu (REQ-S5): move focus into the ACTIVE
    // panel's CONTAINER only after it composes, then restore focus to the ⚙
    // control when the menu closes (BACK, or applying a selection).
    //
    // HARDENED (menu-focus regression, Netflix redesign): SettingsMenu/QualityMenu
    // are CONDITIONALLY composed and added to the tree in the SAME frame `menu`
    // flips, but their focus node is not necessarily LAID OUT yet when this
    // effect's coroutine first runs — a bare `requestFocus()` then throws "not
    // initialized", `runCatching` swallows it, and the popup opens UNFOCUSED and
    // un-navigable. [requestFocusDeferred] waits for the next frame(s) so the
    // just-composed panel is attached and laid out before requesting, which is
    // the deferred-focus (M2) contract these menus' KDoc already assumes.
    LaunchedEffect(menu) {
        when (menu) {
            PlayerMenu.Ajustes -> enterMenuFocus(focusManager, ajustesFocus)
            PlayerMenu.Calidad -> enterMenuFocus(focusManager, menuFocus)
            PlayerMenu.Closed -> {
                withFrameNanos { }
                runCatching { settingsFocus.requestFocus() }
            }
        }
    }

    // 4s auto-hide (REQ-11/REQ-S4), reset by ANY control activity (MAJOR M4).
    // Keyed on [lastActivityMs] so in-zone focus movement and button clicks
    // (which call [registerActivity]) restart the timer. Suppressed while any
    // settings panel is open ([menu]) or the up-next panel owns focus
    // ([panelFocused], design D4) — browsing those is not inactivity.
    LaunchedEffect(lastActivityMs, menu, panelFocused) {
        if (menu != PlayerMenu.Closed || panelFocused) return@LaunchedEffect
        val revealed = overlayState as? OverlayState.Revealed ?: return@LaunchedEffect
        delay(OverlayReducer.AUTO_HIDE_TIMEOUT_MS)
        val next = OverlayReducer.onInactivityTimeout(revealed, nowMs = revealed.sinceMs + OverlayReducer.AUTO_HIDE_TIMEOUT_MS)
        if (next is OverlayState.Hidden) {
            overlayState = next
            // Fresh reveal should start clean — collapse a still-expanded panel.
            panelExpanded = false
            runCatching { neutralFocus.requestFocus() }
        }
    }

    // BACK unwinds exactly one level per press (REQ-U8). While the up-next panel
    // is expanded, BACK collapses ONLY the panel back to the controls row — the
    // overlay stays Revealed. Only once the panel is closed does BACK collapse
    // the WHOLE overlay to Hidden; from there BACK falls through to the caller
    // (pop to Home). The menu's own `BackHandler` below is composed LAST, so
    // LIFO makes it win whenever Ajustes/Calidad is open.
    BackHandler(enabled = visible) {
        if (panelExpanded) {
            panelExpanded = false
            runCatching { playPauseFocus.requestFocus() }
        } else {
            overlayState = OverlayState.Hidden
            runCatching { neutralFocus.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                // While any settings panel (Ajustes or Calidad) is open it owns
                // ALL D-pad input via normal Compose focus search — do not
                // intercept keys meant for the panel's rows.
                if (menu != PlayerMenu.Closed) return@onPreviewKeyEvent false

                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                // MAJOR M3: Android's KeyUp.repeatCount is ALWAYS 0, so the
                // held-vs-tap distinction is tracked ourselves across the
                // KeyDown/KeyUp pair — reset at the start of a fresh press.
                if (keyEvent.type == KeyEventType.KeyDown && repeatCount == 0) {
                    longPressActive = false
                }

                // MAJOR M4: navigating BETWEEN zones/controls with the D-pad
                // while a zone already owns focus IS activity (REQ-11), so
                // register it here and still let Compose perform its own focus
                // move (decideDpadAction resolves to Ignore -> false whenever the
                // scrubber is not focused, so this never double-handles the key).
                if (anyZoneFocused && keyEvent.type == KeyEventType.KeyDown && isDpadKey(keyEvent.key)) {
                    registerActivity()
                }

                val action = decideDpadAction(
                    key = keyEvent.key,
                    type = keyEvent.type,
                    repeatCount = repeatCount,
                    longPressActive = longPressActive,
                    scrubberFocused = scrubberFocused,
                    overlayState = overlayState,
                )

                if (keyEvent.type == KeyEventType.KeyDown && action is DpadAction.Seek) {
                    longPressActive = true
                } else if (keyEvent.type == KeyEventType.KeyUp && longPressActive) {
                    longPressActive = false
                }

                when (action) {
                    DpadAction.Ignore -> false

                    DpadAction.Reveal -> {
                        registerActivity()
                        // Land focus on play/pause once the controls row composes.
                        pendingEnterControls = true
                        true
                    }

                    is DpadAction.Seek -> {
                        // Decoupled scrub: move the PREVIEW cursor only, never the
                        // engine per tick (the debounce effect commits ONE seek on
                        // settle). REQ-L7: live has no scrubbing — the scrubber is
                        // never focusable there, so this is unreachable in live.
                        if (!isLive) {
                            val duration = playbackState.durationMs.coerceAtLeast(0L)
                            // Base = preview → pending → engine: a follow-up
                            // scrub continues from the in-flight target, never
                            // from the stale pre-seek engine position.
                            val base = ScrubSeekSync.displayPosition(scrubPreviewMs, pendingSeekMs, playbackState.positionMs)
                            scrubPreviewMs = (base + action.deltaMs).coerceIn(0L, duration)
                        }
                        registerActivity()
                        true
                    }

                    DpadAction.PlayPause -> {
                        currentOnPlayPause()
                        registerActivity()
                        true
                    }
                }
            },
    ) {
        // Invisible: only exists so a D-pad key event has somewhere to land
        // before any zone is composed (overlay starts Hidden).
        Box(modifier = Modifier.size(0.dp).focusRequester(neutralFocus).focusable())

        TitleLabel(title = title, modifier = Modifier.align(Alignment.TopStart))

        if (playbackState.phase == PlaybackState.Phase.Buffering) {
            BufferingSpinner(modifier = Modifier.align(Alignment.Center))
        }

        if (visible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(0.0f to Color.Transparent, 1.0f to Color.Black.copy(alpha = 0.75f)),
                    )
                    .padding(horizontal = 40.dp, vertical = 24.dp)
                    .onFocusChanged {
                        anyZoneFocused = it.hasFocus
                        // MAJOR M4: entering any zone also counts as activity.
                        if (it.hasFocus) registerActivity()
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isLive) {
                    LiveIndicator()
                } else {
                    Scrubber(
                        // Preview while scrubbing, committed target while the
                        // seek is in flight, engine position once caught up.
                        positionMs = ScrubSeekSync.displayPosition(scrubPreviewMs, pendingSeekMs, playbackState.positionMs),
                        durationMs = playbackState.durationMs,
                        scrubbing = scrubPreviewMs != null,
                        storyboard = storyboard,
                        focusRequester = scrubberFocus,
                        downFocus = playPauseFocus,
                        onFocusChanged = { scrubberFocused = it },
                    )
                }

                ControlsRow(
                    isPlaying = playbackState.isPlaying,
                    playPauseFocusRequester = playPauseFocus,
                    settingsFocusRequester = settingsFocus,
                    // UP from the controls returns to the scrubber; live has no
                    // scrubber, so UP is contained there.
                    upFocus = if (isLive) FocusRequester.Cancel else scrubberFocus,
                    qualityLabel = activeQuality?.label ?: "Automática",
                    onPlayPause = { registerActivity(); onPlayPause() },
                    onSettings = { registerActivity(); menu = PlayerMenuReducer.open(menu) },
                    onEnterPanel = { registerActivity(); panelExpanded = true },
                )

                // REQ-U1/gate R1/R3: TERMINAL child of the tracked zone Column —
                // CONDITIONALLY composed so a collapsed panel costs nothing and
                // never covers the screen unrequested.
                if (panelExpanded) {
                    InfoUpNextPanel(
                        state = upNextState,
                        onRelatedClick = onUpNextClick,
                        onRetry = onUpNextRetry,
                        contentFocusRequester = upNextContentFocus,
                        modifier = Modifier
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5).dp)
                            // REQ-U6/design D4: observes the panel container's OWN
                            // focus state, independent of [panelExpanded].
                            .onFocusChanged { panelFocused = it.hasFocus }
                            .focusRequester(upNextFocus)
                            .focusProperties {
                                up = playPauseFocus
                                // Terminal boundary (REQ-U1): DOWN is guarded here
                                // too — no further nav, no trap.
                                @Suppress("DEPRECATION")
                                exit = { direction -> if (direction == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default }
                            }
                            .focusGroup()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        if (menu is PlayerMenu.Ajustes) {
            SettingsMenu(
                activeQualityLabel = activeQuality?.label,
                ajustesFocusRequester = ajustesFocus,
                calidadRowFocusRequester = calidadRowFocus,
                onOpenQuality = { menu = PlayerMenuReducer.openQuality(menu) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 200.dp),
            )
        }

        if (menu is PlayerMenu.Calidad) {
            QualityMenu(
                availableQualities = availableQualities,
                activeQuality = activeQuality,
                menuFocusRequester = menuFocus,
                // REQ-S3: a resolution/Automática pick closes the ENTIRE menu.
                onSelectQuality = { quality -> onSelectQuality(quality); menu = PlayerMenuReducer.selectResolution(menu) },
                onSelectAuto = { onSelectAuto(); menu = PlayerMenuReducer.selectResolution(menu) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 200.dp),
            )
        }

        // Composed LAST so LIFO makes THIS handler win over the overlay's own
        // `BackHandler` above while any settings panel is open — BACK unwinds
        // exactly one level per press via the pure reducer.
        BackHandler(enabled = menu != PlayerMenu.Closed) {
            menu = PlayerMenuReducer.back(menu)
        }
    }
}

/**
 * Move focus INTO a just-opened settings/quality popup.
 *
 * On-device evidence (TCL 55C6K, logcat): when the ⚙ control owned focus, a bare
 * `target.requestFocus()` reported success but focus never actually left the ⚙ —
 * it lives inside the controls `focusGroup()` whose `exit = Cancel` holds focus
 * in the zone, and a plain programmatic request cannot pull focus out. The same
 * request DOES work at startup, when focus sits at the unfocused root. So the fix
 * is to RETURN to that state first: [FocusManager.clearFocus] with `force = true`
 * overrides the group containment and releases the ⚙, and only then can the popup
 * be focused.
 *
 * The popup is composed in the SAME frame `menu` flips, so its focus node may not
 * be attached/laid out yet — request across a few consecutive frames (a no-op
 * once it lands) to ride out layout lag, the deferred-focus (M2) contract these
 * menus' KDoc already assumes.
 */
private suspend fun enterMenuFocus(focusManager: FocusManager, target: FocusRequester, frames: Int = 4) {
    focusManager.clearFocus(force = true)
    repeat(frames) {
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }
}

@Composable
private fun TitleLabel(title: String, modifier: Modifier = Modifier) {
    if (title.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0.0f to Color.Black.copy(alpha = 0.55f), 1.0f to Color.Transparent))
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Text(text = title, color = OnDark, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/**
 * Minimal custom spinner: `androidx.tv.material3` (1.1.0) ships no progress
 * indicator, and the mobile Material one would mix design systems — docs/07
 * §5 explicitly forbids mixing `material3` mobile with `tv-material`.
 */
@Composable
private fun BufferingSpinner(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "buffering")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900, easing = LinearEasing)),
        label = "bufferingRotation",
    )
    Canvas(modifier = modifier.size(48.dp).rotate(rotation)) {
        drawArc(
            color = YouTrocRed,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * FOCUSABLE timeline scrubber (top zone). LEFT/RIGHT scrubbing and OK are
 * handled by the overlay's global key handler ([decideDpadAction]) while this
 * owns focus; here the modifier only wires the focus node and a distinct
 * focused visual (taller track + a thumb + brighter time label) so a D-pad user
 * can SEE the timeline is active. Focus containment: DOWN → the controls row
 * ([downFocus]); UP/LEFT/RIGHT are cancelled so focus never escapes the zone
 * (LEFT/RIGHT are consumed as seeks upstream and never reach focus search — the
 * cancels are belt-and-suspenders).
 *
 * [storyboard] renders [ScrubPreviewThumbnail] above the track (REQ-SB6),
 * reusing this composable's own `fullWidth`/`fraction` thumb math -- gated
 * internally on `scrubbing && storyboard != null`, so a null [storyboard]
 * (extraction never resolved one) renders exactly today's plain scrubber.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    scrubbing: Boolean,
    storyboard: StoryboardSpec?,
    focusRequester: FocusRequester,
    downFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    var focused by remember { mutableStateOf(false) }
    // "active" = the scrubber owns focus OR is mid-scrub. Mid-scrub keeps the
    // thumb + brighter time visible even across frames where focus briefly
    // flickers, so the moving preview cursor is always legible.
    val active = focused || scrubbing
    val trackHeight = if (active) 6.dp else 4.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusRequester(focusRequester)
            .focusProperties {
                down = downFocus
                up = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val fullWidth = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .background(OnDarkMuted.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            )
            Box(
                modifier = Modifier
                    .width(fullWidth * fraction)
                    .height(trackHeight)
                    .background(YouTrocRed, RoundedCornerShape(2.dp)),
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .offset(x = (fullWidth * fraction - 7.dp).coerceAtLeast(0.dp))
                        .size(14.dp)
                        .background(OnDark, CircleShape),
                )
            }

            ScrubPreviewThumbnail(
                storyboard = storyboard,
                scrubbing = scrubbing,
                positionMs = positionMs,
                fullWidth = fullWidth,
                fraction = fraction,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        Text(
            text = "${PlaybackTimeFormatter.format(positionMs)} / ${PlaybackTimeFormatter.format(durationMs)}",
            color = if (active) OnDark else OnDarkMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Pure-live replacement for [Scrubber] (REQ-L6/REQ-L7): a full-width bar snapped
 * to the live edge plus a red "EN VIVO" pill. Deliberately non-focusable — no
 * `.focusable()` modifier — so it never traps D-pad focus.
 */
@Composable
private fun LiveIndicator(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(YouTrocRed, RoundedCornerShape(2.dp)),
        )
        Box(
            modifier = Modifier
                .background(YouTrocRed, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(text = "EN VIVO", color = OnDark, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The controls zone: exactly TWO focusable controls — play/pause and ⚙ settings
 * — plus a non-focusable quality badge. The inner Row is a `focusGroup` whose
 * `exit` CONTAINS horizontal focus: LEFT on play/pause and RIGHT on ⚙ are
 * cancelled so focus never escapes the overlay (the fix for the old transport
 * row's LEFT-exits-player bug). UP leaves to [upFocus] (the scrubber, or cancel
 * for live); DOWN opens the up-next panel via [onEnterPanel] then cancels this
 * frame — the caller's deferred `LaunchedEffect(panelExpanded)` lands focus in
 * the panel one frame later.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ControlsRow(
    isPlaying: Boolean,
    playPauseFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    upFocus: FocusRequester,
    qualityLabel: String,
    onPlayPause: () -> Unit,
    onSettings: () -> Unit,
    onEnterPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .focusProperties {
                    @Suppress("DEPRECATION")
                    exit = { direction ->
                        when (direction) {
                            FocusDirection.Up -> upFocus
                            FocusDirection.Down -> {
                                onEnterPanel()
                                FocusRequester.Cancel
                            }
                            // LEFT/RIGHT at the group boundary: contained.
                            else -> FocusRequester.Cancel
                        }
                    }
                }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playPauseFocusRequester),
            )
            ControlButton(
                icon = Icons.Default.Settings,
                contentDescription = "Ajustes",
                onClick = onSettings,
                small = true,
                modifier = Modifier.focusRequester(settingsFocusRequester),
            )
        }
        QualityBadge(label = qualityLabel)
    }
}

/** Small non-focusable "Calidad" indicator next to the controls (REQ-Q2): the active pick, or "Automática". */
@Composable
private fun QualityBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = OnDarkMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val size = if (small) 40.dp else 56.dp
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = OnDark,
            contentColor = OnDark,
            focusedContentColor = Color.Black,
        ),
        modifier = modifier.size(size),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}
