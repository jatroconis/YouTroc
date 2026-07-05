package com.youtroc.feature.playback.overlay

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * Outcome of feeding a single D-pad key event through [decideDpadAction]
 * (REQ-11, player-overlay Netflix redesign). Pure —
 * [com.youtroc.feature.playback.PlayerOverlay] applies the side effects
 * (overlay state transition, focus movement, play/pause/seek callbacks).
 */
sealed interface DpadAction {

    /** Nothing to do — let Compose's normal focus/key handling take over. */
    data object Ignore : DpadAction

    /**
     * ANY D-pad press while [OverlayState.Hidden]: reveal the overlay and land
     * focus on the play/pause control. No seek — seeking now only happens while
     * the scrubber owns focus (see [Seek]).
     */
    data object Reveal : DpadAction

    /**
     * Scrub by [deltaMs] (negative = backward). Only ever produced while the
     * timeline SCRUBBER owns focus; a clean tap steps [SeekAmount.SINGLE_STEP_MS]
     * and a held key engages the accelerating [SeekAmount.forPress] fast-seek.
     */
    data class Seek(val deltaMs: Long) : DpadAction

    /** OK/Enter while the scrubber owns focus (owner model): toggle play/pause directly. */
    data object PlayPause : DpadAction
}

/**
 * Pure decision function for the player overlay's D-pad handling (REQ-11,
 * Netflix-style redesign). No Compose state reads/writes here.
 *
 * The overlay has three vertically stacked focus zones — scrubber (top),
 * controls row (play/pause + ⚙), and the up-next panel (bottom). This function
 * only owns two behaviours the containment `focusProperties` cannot express;
 * everything else resolves to [DpadAction.Ignore] so Compose's own focus search
 * (constrained by the zone `focusGroup`/`focusProperties`) moves focus.
 *
 * - **Reveal from Hidden**: while [OverlayState.Hidden], ANY D-pad key
 *   (direction, OK/Enter) reveals the overlay and focus lands on play/pause —
 *   there is NO reveal-then-seek anymore. Resolved on `KeyUp` (mirrors the
 *   proven single-press pattern; a held key from Hidden simply reveals on
 *   release rather than fast-seeking into an unfocused void).
 * - **Scrub from the focused scrubber**: L/R only seek while [scrubberFocused]
 *   is `true`. A clean tap seeks [SeekAmount.SINGLE_STEP_MS]; a held key's
 *   `KeyDown` repeats seek by the accelerating [SeekAmount.forPress] amount.
 *   [longPressActive] is state the caller tracks across the KeyDown/KeyUp pair
 *   for a single held press: Android's `KeyEvent.getRepeatCount()` is ALWAYS
 *   `0` on `KeyUp` (MAJOR M3), so the terminal release can't tell a long-press
 *   from a plain tap on its own — when [longPressActive] is `true`, the
 *   terminal `KeyUp` is swallowed instead of firing an extra single-step seek
 *   on top of the fast-seek ticks already applied. OK/Enter while the scrubber
 *   is focused toggles play/pause directly (owner model: OK on the timeline =
 *   play/pause).
 * - **Everything else** (any key while the controls row or up-next panel owns
 *   focus, once revealed) returns [DpadAction.Ignore]: LEFT/RIGHT stay CONTAINED
 *   inside the controls group (`focusProperties.exit`), UP/DOWN move between
 *   zones, and OK is handled by the focused button's own `onClick`.
 */
fun decideDpadAction(
    key: Key,
    type: KeyEventType,
    repeatCount: Int,
    longPressActive: Boolean,
    scrubberFocused: Boolean,
    overlayState: OverlayState,
): DpadAction {
    val direction = when (key) {
        Key.DirectionLeft -> SeekDirection.Backward
        Key.DirectionRight -> SeekDirection.Forward
        else -> null
    }

    // Scrubbing only ever happens while the timeline scrubber owns focus.
    // `scrubberFocused` implies the overlay is already Revealed, so there is no
    // Hidden/reveal case to fold in here anymore.
    if (direction != null && scrubberFocused) {
        return when (type) {
            KeyEventType.KeyDown -> if (repeatCount > 0) {
                DpadAction.Seek(signedAmount(direction, SeekAmount.forPress(repeatCount)))
            } else {
                DpadAction.Ignore // let KeyUp below dispatch a clean single press
            }

            KeyEventType.KeyUp -> when {
                longPressActive -> DpadAction.Ignore // fast-seek ticks already applied; swallow the release
                else -> DpadAction.Seek(signedAmount(direction, SeekAmount.SINGLE_STEP_MS))
            }

            else -> DpadAction.Ignore
        }
    }

    // OK/Enter on the focused scrubber toggles play/pause (owner model).
    if (scrubberFocused && type == KeyEventType.KeyUp && (key == Key.DirectionCenter || key == Key.Enter)) {
        return DpadAction.PlayPause
    }

    // From Hidden, ANY D-pad press reveals the overlay with focus on play/pause.
    if (overlayState is OverlayState.Hidden && type == KeyEventType.KeyUp && isDpadKey(key)) {
        return DpadAction.Reveal
    }

    return DpadAction.Ignore
}

/**
 * The six D-pad interaction keys (directions + OK/Enter). Shared predicate for
 * every "is this a D-pad key?" decision in the overlay: the reveal-from-Hidden
 * gate here, and `PlayerOverlay`'s activity tracking (MAJOR M4) — one place to
 * extend if a new remote key ever joins the set.
 */
internal fun isDpadKey(key: Key): Boolean = when (key) {
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.DirectionCenter, Key.Enter,
    -> true

    else -> false
}

private fun signedAmount(direction: SeekDirection, amountMs: Long): Long =
    if (direction == SeekDirection.Backward) -amountMs else amountMs
