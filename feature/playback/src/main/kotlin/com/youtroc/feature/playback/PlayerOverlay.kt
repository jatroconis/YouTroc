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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.playback.VideoQuality
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
import com.youtroc.feature.playback.overlay.SeekAmount
import com.youtroc.feature.playback.overlay.decideDpadAction
import com.youtroc.feature.playback.quality.QualityMenu
import com.youtroc.feature.playback.settings.SettingsMenu
import com.youtroc.feature.playback.upnext.DetailUiState
import com.youtroc.feature.playback.upnext.InfoUpNextPanel
import kotlinx.coroutines.delay

/**
 * Custom Compose-for-TV player overlay (REQ-11, docs/07 § Player a pantalla
 * completa / § 10). Renders the title, a position/duration scrubber, a
 * buffering spinner, and a 2D control area: a TRANSPORT row (prev /
 * play-pause centered / next) with a PILLS row (like/dislike/CC/settings)
 * below it.
 *
 * D-pad L/R drives the reveal-then-seek gesture ([decideDpadAction]) while no
 * button in the control rows owns focus: the first press only reveals the
 * overlay, the next one seeks. Once the user moves focus into the rows
 * (DOWN, or CENTER/Enter), L/R falls through to Compose's normal per-row
 * focus search instead, and vertical UP/DOWN is wired explicitly between the
 * two rows — HomeShell's proven `focusGroup()` + `focusProperties` pattern —
 * so neither row traps focus.
 *
 * Entering the controls (DOWN) defers the focus move to a
 * `LaunchedEffect(visible, pendingEnterControls)` — HomeShell's proven
 * pattern (MAJOR M2): the transport/pills rows only compose once [visible]
 * flips to `true`, one frame AFTER the key event that revealed them, so
 * calling `requestFocus()` inline during the event handler targets a node
 * that isn't attached yet and silently throws (swallowed by `runCatching`).
 * CENTER/Enter skip focus entirely and call [onPlayPause] directly (docs/07
 * §191: CENTER = play/pause) whenever no control-row button owns focus —
 * Hidden (no not-yet-composed button to focus) OR Revealed-but-unfocused
 * (e.g. right after a single L/R tap only revealed the overlay without
 * moving focus into a row).
 *
 * D-pad navigation ([Key.DirectionLeft]/[Key.DirectionRight]/[Key.DirectionUp]/
 * [Key.DirectionDown]/[Key.DirectionCenter]/[Key.Enter]) counts as control
 * activity and restarts the 4s auto-hide timer even while browsing between
 * buttons/rows with focus already inside the controls, not just on click —
 * see [isDpadNavigationKey] in the key handler below.
 *
 * Knows nothing about Media3 or the concrete engine — only the domain
 * [PlaybackState] snapshot and plain callbacks. Integration-only:
 * exercising real D-pad focus/timing needs a real TV input stack, so this is
 * validated on the TCL 55C6K rather than unit-tested — the same convention
 * already used for `Media3MediaPlayer`/`PlayerSurface`. The pure pieces it
 * delegates to ([decideDpadAction], [OverlayReducer], [SeekAmount],
 * [PlaybackTimeFormatter]) ARE unit-tested.
 *
 * The ⚙ pill (REQ-S1/REQ-11) opens a two-tier "Ajustes" -> "Calidad" menu —
 * [SettingsMenu] then [QualityMenu] — as modal layers this composable owns
 * via local [menu] state (a pure [PlayerMenu] driven by
 * [PlayerMenuReducer]), rather than forwarding to an external callback.
 * While any panel is open: the outer key handler stops intercepting D-pad
 * input (the panel owns it via normal Compose focus search), the 4s
 * auto-hide is suppressed, and a nested [BackHandler] — composed AFTER the
 * overlay's own, so LIFO makes it win — unwinds exactly one level per press
 * (REQ-S4), restoring focus to the row that opened the closed panel.
 *
 * A THIRD zone, the in-player Info+Up-Next panel (`upnext` package,
 * player-upnext REQ-U1), sits below the pills row. Continuing DOWN from the
 * pills flips local [PlayerOverlay]'s `panelExpanded` state — the panel is
 * CONDITIONALLY composed (design gate correction R1) so a collapsed panel
 * costs nothing and never covers the screen unrequested. Entry reuses the
 * SAME deferred-focus (M2) pattern as [pendingEnterControls]: the panel only
 * exists one frame after the key that expanded it, so focus is requested
 * from a `LaunchedEffect(panelExpanded)`, never inline. BACK collapses the
 * panel back to the pills row (overlay stays Revealed) before it ever
 * collapses the whole overlay (REQ-U8) — see the branch inside the overlay's
 * own [BackHandler] below. The panel is the tracked controls [Column]'s
 * TERMINAL child, so `controlsFocused` stays true while it owns focus and
 * L/R never seeks while browsing (gate R3).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerOverlay(
    title: String,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (deltaMs: Long) -> Unit = {},
    onLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    onCaptions: () -> Unit = {},
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
    var controlsFocused by remember { mutableStateOf(false) }
    var lastActivityMs by remember { mutableLongStateOf(0L) }
    var pendingEnterControls by remember { mutableStateOf(false) }
    var longPressActive by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<PlayerMenu>(PlayerMenu.Closed) }
    var panelExpanded by remember { mutableStateOf(false) }
    // REQ-U6/design D4: tracks whether the panel CONTAINER itself currently
    // owns focus, distinct from [panelExpanded] (which only flips false on
    // BACK). UP-from-panel-to-pills leaves [panelExpanded] true but moves
    // focus away — this is what the auto-hide guard below keys on instead,
    // so REQ-U6 scenario 2 (focus leaves panel -> auto-hide resumes) holds.
    var panelFocused by remember { mutableStateOf(false) }
    val neutralFocus = remember { FocusRequester() }
    val transportFocus = remember { FocusRequester() }
    val pillsFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val upNextFocus = remember { FocusRequester() }
    // Distinct from [upNextFocus] (gate R3, WU-1 note): the panel container
    // itself carries `upNextFocus`, while this one binds ONLY the
    // NotAvailable/Offline/Error inline message/Retry anchor inside
    // `InfoUpNextPanel`. Reusing the SAME instance for both would attach it
    // to two different focus nodes whenever an error state renders — this
    // keeps the two roles unambiguous rather than relying on an on-device
    // check to catch it.
    val upNextContentFocus = remember { FocusRequester() }
    val ajustesFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    // Gate S3: forward-safe restore hook, currently a no-op passthrough —
    // FASE-1's Ajustes has exactly one row, so requesting the Ajustes
    // container (see LaunchedEffect(menu) below) already lands on the sole
    // Calidad row; wired now for when Ajustes gains more rows.
    val calidadRowFocus = remember { FocusRequester() }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)

    val visible = overlayState is OverlayState.Revealed

    fun registerActivity(nowMs: Long = System.currentTimeMillis()) {
        overlayState = OverlayReducer.onActivity(nowMs)
        lastActivityMs = nowMs
    }

    LaunchedEffect(Unit) {
        runCatching { neutralFocus.requestFocus() }
    }

    // MAJOR M2: move focus into the transport row only AFTER it actually
    // composes (this effect re-runs once `visible` flips to true), instead of
    // requesting focus inline during the key-event handler.
    LaunchedEffect(visible, pendingEnterControls) {
        if (visible && pendingEnterControls) {
            runCatching { transportFocus.requestFocus() }
            pendingEnterControls = false
        }
    }

    // Gate R2 (player-upnext design gate correction): the Info+Up-Next panel
    // is CONDITIONALLY composed (only when [panelExpanded]), so a direct
    // inline `requestFocus()` on the DOWN key that flips it would target a
    // container that isn't attached yet and silently no-op — same M2
    // deferred-focus pattern as [pendingEnterControls] above, one frame after
    // the panel actually composes. [onPanelOpened] triggers the lazy
    // `UpNextViewModel.ensureLoaded()` (REQ-U3) the FIRST time the panel
    // opens for the current video; a later re-open reuses the cached state.
    LaunchedEffect(panelExpanded) {
        if (panelExpanded) {
            onPanelOpened()
            runCatching { upNextFocus.requestFocus() }
        }
    }

    // MAJOR-5 (design gate #4431) extended to a 3-level menu (REQ-S5): move
    // focus into the ACTIVE panel's CONTAINER only after it actually
    // composes — same M2 deferred-focus pattern as [transportFocus] above,
    // not requested inline during the click that opened it. Closing back to
    // Closed (BACK, or applying a selection) restores focus to the settings
    // pill that opened the whole menu.
    LaunchedEffect(menu) {
        when (menu) {
            PlayerMenu.Ajustes -> runCatching { ajustesFocus.requestFocus() }
            PlayerMenu.Calidad -> runCatching { menuFocus.requestFocus() }
            PlayerMenu.Closed -> runCatching { settingsFocus.requestFocus() }
        }
    }

    // 4s auto-hide (REQ-11), reset by ANY control activity (MAJOR M4) — not
    // just the key event that first revealed the overlay. Keyed on
    // [lastActivityMs] rather than [overlayState] so in-row focus movement
    // and button clicks (which call [registerActivity]) restart the timer.
    // Also keyed/guarded on [menu] (minor fix, #4431; widened to the 3-level
    // menu for REQ-S5): collapsing the controls behind an open Ajustes or
    // Calidad panel would strand its focus, so auto-hide is fully
    // suppressed while ANY settings panel is open. Widened again (REQ-U6,
    // fixed post-verify WARNING-1) for [panelFocused] rather than
    // [panelExpanded]: browsing the Info+Up-Next panel is also NOT
    // inactivity, but the panel stays `panelExpanded` until BACK — keying on
    // expansion would strand the suppression even after UP moves focus back
    // to the pills row. [panelFocused] tracks the panel container's actual
    // focus (design D4), so leaving it back to the pills re-triggers this
    // effect and re-arms the 4s window from that moment, same mechanism
    // [menu] already relies on.
    LaunchedEffect(lastActivityMs, menu, panelFocused) {
        if (menu != PlayerMenu.Closed || panelFocused) return@LaunchedEffect
        val revealed = overlayState as? OverlayState.Revealed ?: return@LaunchedEffect
        delay(OverlayReducer.AUTO_HIDE_TIMEOUT_MS)
        val next = OverlayReducer.onInactivityTimeout(revealed, nowMs = revealed.sinceMs + OverlayReducer.AUTO_HIDE_TIMEOUT_MS)
        if (next is OverlayState.Hidden) {
            overlayState = next
            // Fresh reveal should start clean — a still-expanded-but-hidden
            // panel would be a ghost state. [panelFocused] follows to false
            // on its own next frame once the container loses focus.
            panelExpanded = false
            runCatching { neutralFocus.requestFocus() }
        }
    }

    // BACK unwinds exactly one level per press (REQ-U8). While the
    // Info+Up-Next panel owns focus, BACK collapses ONLY the panel back to
    // the pills row — the overlay stays Revealed. Only once the panel is
    // already closed does BACK collapse the WHOLE overlay to Hidden; from
    // there BACK falls through to whatever the caller wires (pop to Home).
    // No new BackHandler: the menu's own `BackHandler(enabled = menu !=
    // Closed)` below is composed LAST, so LIFO still makes it win over this
    // one whenever Ajustes/Calidad is open (REQ-U8 branch 1, unchanged) —
    // the panel and the settings menu are never reachable/focused at once.
    BackHandler(enabled = visible) {
        if (panelExpanded) {
            panelExpanded = false
            runCatching { pillsFocus.requestFocus() }
        } else {
            overlayState = OverlayState.Hidden
            runCatching { neutralFocus.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                // minor fix (#4431; widened to the 3-level menu for REQ-S5):
                // while any settings panel (Ajustes or Calidad) is open it
                // owns ALL D-pad input via normal Compose focus search — the
                // reveal-then-seek gesture and pills/transport navigation
                // below must not intercept keys meant for the panel's rows.
                if (menu != PlayerMenu.Closed) return@onPreviewKeyEvent false

                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                // MAJOR M3: Android's KeyUp.repeatCount is ALWAYS 0, so the
                // held-vs-tap distinction must be tracked ourselves across
                // the KeyDown/KeyUp pair — reset at the start of a fresh press.
                if (keyEvent.type == KeyEventType.KeyDown && repeatCount == 0) {
                    longPressActive = false
                }

                // MAJOR M4 (round 2): navigating BETWEEN buttons/rows with the
                // D-pad while a control already owns focus never reached
                // registerActivity() before — only onClick and the Column's
                // onFocusChanged (which fires once, when the Column itself
                // gains focus, not on every row-to-row move) did. Browsing the
                // controls without clicking anything IS activity (REQ-11), so
                // register it here and still let Compose perform its own
                // focus move (decideDpadAction always resolves to Ignore -> false
                // while controlsFocused, so this never double-handles the key).
                if (controlsFocused && keyEvent.type == KeyEventType.KeyDown && isDpadNavigationKey(keyEvent.key)) {
                    registerActivity()
                }

                val action = decideDpadAction(
                    key = keyEvent.key,
                    type = keyEvent.type,
                    repeatCount = repeatCount,
                    longPressActive = longPressActive,
                    controlsFocused = controlsFocused,
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
                        true
                    }

                    is DpadAction.Seek -> {
                        // REQ-L7: pure-live has no scrubbing — L/R still keeps the
                        // overlay alive (registerActivity) but never seeks.
                        if (!isLive) currentOnSeek(action.deltaMs)
                        registerActivity()
                        true
                    }

                    DpadAction.EnterControls -> {
                        registerActivity()
                        pendingEnterControls = true
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
        // before any control row is composed (overlay starts Hidden).
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
                        controlsFocused = it.hasFocus
                        // MAJOR M4: entering the control rows also counts as activity.
                        if (it.hasFocus) registerActivity()
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isLive) {
                    LiveIndicator()
                } else {
                    Scrubber(positionMs = playbackState.positionMs, durationMs = playbackState.durationMs)
                }

                TransportRow(
                    isPlaying = playbackState.isPlaying,
                    focusRequester = transportFocus,
                    downFocus = pillsFocus,
                    onPrevious = { registerActivity(); onPrevious() },
                    onPlayPause = { registerActivity(); onPlayPause() },
                    onNext = { registerActivity(); onNext() },
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillsRow(
                        focusRequester = pillsFocus,
                        upFocus = transportFocus,
                        settingsFocusRequester = settingsFocus,
                        onLike = { registerActivity(); onLike() },
                        onDislike = { registerActivity(); onDislike() },
                        onCaptions = { registerActivity(); onCaptions() },
                        onSettings = { registerActivity(); menu = PlayerMenuReducer.open(menu) },
                        onEnterPanel = { registerActivity(); panelExpanded = true },
                    )
                    QualityBadge(label = activeQuality?.label ?: "Automática")
                }

                // REQ-U1/gate R1/R3: TERMINAL child of the tracked controls
                // Column — CONDITIONALLY composed so a collapsed panel costs
                // nothing and never covers the screen unrequested (design
                // gate correction). Living inside this Column (rather than a
                // separate BottomEnd surface like Ajustes/Calidad) keeps
                // `controlsFocused` true while the panel owns focus, so L/R
                // keeps falling through to Compose's normal focus search
                // (horizontal rail nav) instead of re-arming reveal-then-seek.
                if (panelExpanded) {
                    InfoUpNextPanel(
                        state = upNextState,
                        onRelatedClick = onUpNextClick,
                        onRetry = onUpNextRetry,
                        contentFocusRequester = upNextContentFocus,
                        modifier = Modifier
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5).dp)
                            // REQ-U6/design D4: observes the panel container's
                            // OWN focus state (bubbles up from whatever child
                            // — description Box, retry, related card — holds
                            // it), independent of [panelExpanded]. Placed
                            // BEFORE focusRequester/focusProperties/focusGroup
                            // so it wraps the whole focus group, same as the
                            // description Box's own onFocusChanged below.
                            .onFocusChanged { panelFocused = it.hasFocus }
                            .focusRequester(upNextFocus)
                            .focusProperties {
                                up = pillsFocus
                                // Terminal boundary (REQ-U1): DOWN is guarded
                                // here too, same shape as PillsRow's own exit
                                // below — no further nav, no trap.
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
            // Gate R1: pins the Ajustes anchor to the SAME clear-of-control-
            // band value as Calidad below, so the FIRST panel ⚙ opens is
            // provably clear of the scrubber/transport/pills band.
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
                // REQ-S3: a resolution/Automática pick closes the ENTIRE
                // menu back to the player, not just Calidad.
                onSelectQuality = { quality -> onSelectQuality(quality); menu = PlayerMenuReducer.selectResolution(menu) },
                onSelectAuto = { onSelectAuto(); menu = PlayerMenuReducer.selectResolution(menu) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 200.dp),
            )
        }

        // minor fix (#4431; widened to the 3-level menu for REQ-S4):
        // composed LAST so LIFO makes THIS handler win over the overlay's
        // own `BackHandler` above while any settings panel is open — BACK
        // unwinds exactly one level per press via the pure reducer, never
        // pops the player while a panel is open.
        BackHandler(enabled = menu != PlayerMenu.Closed) {
            menu = PlayerMenuReducer.back(menu)
        }
    }
}

/**
 * D-pad direction/center keys count as control activity (MAJOR M4, round 2)
 * regardless of what [decideDpadAction] resolves them to. Compose glue only —
 * no pure decision to unit-test here, same integration-only convention as the
 * rest of this composable's key/focus handling.
 */
private fun isDpadNavigationKey(key: Key): Boolean = when (key) {
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.DirectionCenter, Key.Enter,
    -> true

    else -> false
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

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(OnDarkMuted.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(YouTrocRed, RoundedCornerShape(2.dp)),
            )
        }
        Text(
            text = "${PlaybackTimeFormatter.format(positionMs)} / ${PlaybackTimeFormatter.format(durationMs)}",
            color = OnDarkMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Pure-live replacement for [Scrubber] (REQ-L6/REQ-L7): a full-width bar
 * snapped to the live edge (fraction = 1f, no position/duration progress)
 * plus a red "EN VIVO" pill. Deliberately non-focusable — no `.focusable()`
 * modifier — so it never traps D-pad focus.
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    focusRequester: FocusRequester,
    downFocus: FocusRequester,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .focusProperties {
                down = downFocus
                @Suppress("DEPRECATION")
                exit = { direction -> if (direction == FocusDirection.Up) FocusRequester.Cancel else FocusRequester.Default }
            }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(icon = Icons.Default.SkipPrevious, contentDescription = "Anterior", onClick = onPrevious)
        ControlButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            onClick = onPlayPause,
            modifier = Modifier.focusRequester(focusRequester),
        )
        ControlButton(icon = Icons.Default.SkipNext, contentDescription = "Siguiente", onClick = onNext)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PillsRow(
    focusRequester: FocusRequester,
    upFocus: FocusRequester,
    settingsFocusRequester: FocusRequester,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onCaptions: () -> Unit,
    onSettings: () -> Unit,
    onEnterPanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                up = upFocus
                // REQ-U1/gate R1: DOWN no longer traps focus here — it fires
                // [onEnterPanel] (flips `panelExpanded` at the call site) and
                // CANCELS for this frame only, since the panel isn't
                // composed yet. The caller's deferred
                // `LaunchedEffect(panelExpanded)` moves focus into it one
                // frame later (gate R2), the same M2 pattern already used to
                // enter the transport row.
                @Suppress("DEPRECATION")
                exit = { direction ->
                    if (direction == FocusDirection.Down) {
                        onEnterPanel()
                        FocusRequester.Cancel
                    } else {
                        FocusRequester.Default
                    }
                }
            }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ControlButton(icon = Icons.Default.ThumbUp, contentDescription = "Me gusta", onClick = onLike, small = true)
        ControlButton(icon = Icons.Default.ThumbDown, contentDescription = "No me gusta", onClick = onDislike, small = true)
        ControlButton(icon = Icons.Default.ClosedCaption, contentDescription = "Subtítulos", onClick = onCaptions, small = true)
        ControlButton(
            icon = Icons.Default.Settings,
            contentDescription = "Ajustes",
            onClick = onSettings,
            small = true,
            modifier = Modifier.focusRequester(settingsFocusRequester),
        )
    }
}

/** Small non-focusable "Calidad" indicator next to the pills (REQ-Q2): the active pick, or "Automática". */
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
