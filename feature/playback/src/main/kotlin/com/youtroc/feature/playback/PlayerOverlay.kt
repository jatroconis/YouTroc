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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.playback.VideoQuality
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocRed
import com.youtroc.feature.playback.overlay.DpadAction
import com.youtroc.feature.playback.overlay.OverlayReducer
import com.youtroc.feature.playback.overlay.OverlayState
import com.youtroc.feature.playback.overlay.PlaybackTimeFormatter
import com.youtroc.feature.playback.overlay.SeekAmount
import com.youtroc.feature.playback.overlay.decideDpadAction
import com.youtroc.feature.playback.quality.QualityMenu
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
 * The ⚙ pill (REQ-Q2/REQ-11) opens the "Calidad" menu — [QualityMenu] — as a
 * modal layer this composable owns via local [menuVisible] state, rather
 * than forwarding to an external callback. While the menu is open: the
 * outer key handler stops intercepting D-pad input (menu owns it via normal
 * Compose focus search), the 4s auto-hide is suppressed, and a nested
 * [BackHandler] — composed AFTER the overlay's own, so LIFO makes it win —
 * closes only the menu (REQ-Q7), restoring focus to the settings pill.
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
) {
    var overlayState by remember { mutableStateOf<OverlayState>(OverlayState.Hidden) }
    var controlsFocused by remember { mutableStateOf(false) }
    var lastActivityMs by remember { mutableLongStateOf(0L) }
    var pendingEnterControls by remember { mutableStateOf(false) }
    var longPressActive by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    val neutralFocus = remember { FocusRequester() }
    val transportFocus = remember { FocusRequester() }
    val pillsFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
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

    // MAJOR-5 (design gate #4431): move focus into the "Calidad" menu
    // CONTAINER only after it actually composes — same M2 deferred-focus
    // pattern as [transportFocus] above, not requested inline during the
    // click that opened it. Closing the menu (BACK, or applying a
    // selection) restores focus to the settings pill that opened it.
    LaunchedEffect(menuVisible) {
        if (menuVisible) {
            runCatching { menuFocus.requestFocus() }
        } else {
            runCatching { settingsFocus.requestFocus() }
        }
    }

    // 4s auto-hide (REQ-11), reset by ANY control activity (MAJOR M4) — not
    // just the key event that first revealed the overlay. Keyed on
    // [lastActivityMs] rather than [overlayState] so in-row focus movement
    // and button clicks (which call [registerActivity]) restart the timer.
    // Also keyed/guarded on [menuVisible] (minor fix, #4431): collapsing the
    // controls behind an open "Calidad" menu would strand its focus, so
    // auto-hide is fully suppressed while the menu is open.
    LaunchedEffect(lastActivityMs, menuVisible) {
        if (menuVisible) return@LaunchedEffect
        val revealed = overlayState as? OverlayState.Revealed ?: return@LaunchedEffect
        delay(OverlayReducer.AUTO_HIDE_TIMEOUT_MS)
        val next = OverlayReducer.onInactivityTimeout(revealed, nowMs = revealed.sinceMs + OverlayReducer.AUTO_HIDE_TIMEOUT_MS)
        if (next is OverlayState.Hidden) {
            overlayState = next
            runCatching { neutralFocus.requestFocus() }
        }
    }

    // BACK first collapses the overlay back to Hidden; only when it is already
    // Hidden does BACK fall through to whatever the caller wires (pop to Home).
    BackHandler(enabled = visible) {
        overlayState = OverlayState.Hidden
        runCatching { neutralFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                // minor fix (#4431): while the "Calidad" menu is open it owns
                // ALL D-pad input via normal Compose focus search — the
                // reveal-then-seek gesture and pills/transport navigation
                // below must not intercept keys meant for the menu's rows.
                if (menuVisible) return@onPreviewKeyEvent false

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
                        currentOnSeek(action.deltaMs)
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
                Scrubber(positionMs = playbackState.positionMs, durationMs = playbackState.durationMs)

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
                        onSettings = { registerActivity(); menuVisible = true },
                    )
                    QualityBadge(label = activeQuality?.label ?: "Auto")
                }
            }
        }

        if (menuVisible) {
            QualityMenu(
                availableQualities = availableQualities,
                activeQuality = activeQuality,
                menuFocusRequester = menuFocus,
                onSelectQuality = { quality -> onSelectQuality(quality); menuVisible = false },
                onSelectAuto = { onSelectAuto(); menuVisible = false },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 120.dp),
            )
        }

        // minor fix (#4431): composed LAST so LIFO makes THIS handler win
        // over the overlay's own `BackHandler` above while the menu is open —
        // BACK closes the "Calidad" menu only, never pops the player.
        BackHandler(enabled = menuVisible) {
            menuVisible = false
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                up = upFocus
                @Suppress("DEPRECATION")
                exit = { direction -> if (direction == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default }
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

/** Small non-focusable "Calidad" indicator next to the pills (REQ-Q2): the active pick, or "Auto". */
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
