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
import androidx.compose.ui.input.key.KeyEvent
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
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocRed
import com.youtroc.feature.playback.overlay.OverlayReducer
import com.youtroc.feature.playback.overlay.OverlayState
import com.youtroc.feature.playback.overlay.PlaybackTimeFormatter
import com.youtroc.feature.playback.overlay.SeekAmount
import com.youtroc.feature.playback.overlay.SeekDirection
import kotlinx.coroutines.delay

/**
 * Custom Compose-for-TV player overlay (REQ-11, docs/07 § Player a pantalla
 * completa / § 10). Renders the title, a position/duration scrubber, a
 * buffering spinner, and a 2D control area: a TRANSPORT row (prev /
 * play-pause centered / next) with a PILLS row (like/dislike/CC/settings)
 * below it.
 *
 * D-pad L/R drives the reveal-then-seek gesture ([OverlayReducer]) while no
 * button in the control rows owns focus: the first press only reveals the
 * overlay, the next one seeks. Once the user moves focus into the rows
 * (DOWN, or CENTER/Enter), L/R falls through to Compose's normal per-row
 * focus search instead, and vertical UP/DOWN is wired explicitly between the
 * two rows — HomeShell's proven `focusGroup()` + `focusProperties` pattern —
 * so neither row traps focus.
 *
 * Knows nothing about Media3 or the concrete engine — only the domain
 * [PlaybackState] snapshot and plain callbacks. Integration-only:
 * exercising real D-pad focus/timing needs a real TV input stack, so this is
 * validated on the TCL 55C6K rather than unit-tested — the same convention
 * already used for `Media3MediaPlayer`/`PlayerSurface`. The pure pieces it
 * delegates to ([OverlayReducer], [SeekAmount], [PlaybackTimeFormatter]) ARE
 * unit-tested.
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
    onSettings: () -> Unit = {},
) {
    var overlayState by remember { mutableStateOf<OverlayState>(OverlayState.Hidden) }
    var controlsFocused by remember { mutableStateOf(false) }
    val neutralFocus = remember { FocusRequester() }
    val transportFocus = remember { FocusRequester() }
    val pillsFocus = remember { FocusRequester() }
    val currentOnSeek by rememberUpdatedState(onSeek)

    val visible = overlayState is OverlayState.Revealed

    LaunchedEffect(Unit) {
        runCatching { neutralFocus.requestFocus() }
    }

    // 4s auto-hide (REQ-11): ticks while revealed, hides once the timeout elapses.
    LaunchedEffect(overlayState) {
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
                handleDPadKeyEvent(
                    keyEvent = keyEvent,
                    controlsFocused = controlsFocused,
                    overlayState = overlayState,
                    onOverlayStateChange = { overlayState = it },
                    onEnterControls = { runCatching { transportFocus.requestFocus() } },
                    onSeek = currentOnSeek,
                )
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
                    .onFocusChanged { controlsFocused = it.hasFocus },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Scrubber(positionMs = playbackState.positionMs, durationMs = playbackState.durationMs)

                TransportRow(
                    isPlaying = playbackState.isPlaying,
                    focusRequester = transportFocus,
                    downFocus = pillsFocus,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                )

                PillsRow(
                    focusRequester = pillsFocus,
                    upFocus = transportFocus,
                    onLike = onLike,
                    onDislike = onDislike,
                    onCaptions = onCaptions,
                    onSettings = onSettings,
                )
            }
        }
    }
}

/**
 * D-pad L/R here is a global reveal-then-seek gesture, not per-row button
 * navigation (docs/07 §10: "1er L/R revela overlay, 2do hace seek"). It only
 * fires while [controlsFocused] is false; once focus is on an actual
 * transport/pills button, this returns `false` and Compose's default
 * per-row arrow-key focus search takes over instead.
 *
 * Reacts on `KeyUp` for a clean single tap (avoids the double-dispatch bug
 * `handleDPadKeyEvents` documents), but watches `KeyDown` repeats too so a
 * long-press engages a continuous, accelerating fast-seek ([SeekAmount])
 * instead of a single 10s step.
 */
private fun handleDPadKeyEvent(
    keyEvent: KeyEvent,
    controlsFocused: Boolean,
    overlayState: OverlayState,
    onOverlayStateChange: (OverlayState) -> Unit,
    onEnterControls: () -> Unit,
    onSeek: (Long) -> Unit,
): Boolean {
    val direction = when (keyEvent.key) {
        Key.DirectionLeft -> SeekDirection.Backward
        Key.DirectionRight -> SeekDirection.Forward
        else -> null
    }

    if (direction != null && !controlsFocused) {
        val repeatCount = keyEvent.nativeKeyEvent.repeatCount
        return when (keyEvent.type) {
            KeyEventType.KeyDown -> {
                if (repeatCount > 0 && overlayState is OverlayState.Revealed) {
                    onSeek(signedAmount(direction, SeekAmount.forPress(repeatCount)))
                    onOverlayStateChange(OverlayReducer.onActivity(nowMs = System.currentTimeMillis()))
                    true
                } else {
                    false // let KeyUp below dispatch a clean single press
                }
            }

            KeyEventType.KeyUp -> {
                if (repeatCount > 0) {
                    true // long-press ticks already handled on KeyDown; swallow the release
                } else {
                    val transition = OverlayReducer.onDirectionalKey(overlayState, direction, System.currentTimeMillis())
                    onOverlayStateChange(transition.nextState)
                    transition.seek?.let { onSeek(signedAmount(it, SeekAmount.SINGLE_STEP_MS)) }
                    true
                }
            }

            else -> false
        }
    }

    val entersControls = keyEvent.key == Key.DirectionDown || keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
    if (keyEvent.type == KeyEventType.KeyUp && entersControls && !controlsFocused && overlayState is OverlayState.Hidden) {
        onOverlayStateChange(OverlayReducer.onActivity(nowMs = System.currentTimeMillis()))
        onEnterControls()
        return true
    }

    return false
}

private fun signedAmount(direction: SeekDirection, amountMs: Long): Long =
    if (direction == SeekDirection.Backward) -amountMs else amountMs

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
        ControlButton(icon = Icons.Default.Settings, contentDescription = "Ajustes", onClick = onSettings, small = true)
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
