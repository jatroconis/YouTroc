package com.youtroc.feature.playback.overlay

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * Outcome of feeding a single D-pad key event through [decideDpadAction]
 * (REQ-11, MAJOR M6). Pure — [com.youtroc.feature.playback.PlayerOverlay]
 * applies the side effects (overlay state transition, focus movement,
 * play/pause/seek callbacks).
 */
sealed interface DpadAction {

    /** Nothing to do — let Compose's normal focus/key handling take over. */
    data object Ignore : DpadAction

    /** First press while [OverlayState.Hidden]: reveal only, no seek. */
    data object Reveal : DpadAction

    /**
     * Perform a seek by [deltaMs] (negative = backward). Also counts as
     * activity: the caller reveals/refreshes the overlay for this action too
     * (covers both a clean second press AND a long-press started from
     * [OverlayState.Hidden] — see [decideDpadAction]'s doc).
     */
    data class Seek(val deltaMs: Long) : DpadAction

    /** DOWN from [OverlayState.Hidden]: reveal AND move focus into the transport row once it composes. */
    data object EnterControls : DpadAction

    /** CENTER/Enter from [OverlayState.Hidden] (docs/07 §191): reveal AND toggle play/pause directly. */
    data object PlayPause : DpadAction
}

/**
 * Pure decision function for the player overlay's D-pad handling (REQ-11).
 * No Compose state reads/writes here.
 *
 * - Direction (L/R) keys only act while [controlsFocused] is `false`; once a
 *   transport/pills button owns focus, this always returns [DpadAction.Ignore]
 *   so Compose's default per-row focus search takes over.
 * - A clean single tap reveals on the first press ([OverlayState.Hidden]) and
 *   seeks by [SeekAmount.SINGLE_STEP_MS] on the second ([OverlayState.Revealed]).
 * - `KeyDown` repeats (a held key, `repeatCount > 0`) seek by the accelerating
 *   [SeekAmount.forPress] amount **regardless of [overlayState]** — this is
 *   what makes a long-press started from [OverlayState.Hidden] reveal-and-
 *   fast-seek (MAJOR M6) instead of doing nothing, the dead-zone this fixes.
 * - [longPressActive] is state the caller tracks across the KeyDown/KeyUp
 *   pair for a single held press: Android's `KeyEvent.getRepeatCount()` is
 *   ALWAYS `0` on `KeyUp` (MAJOR M3), so the terminal release can't tell a
 *   long-press from a plain tap on its own — when [longPressActive] is
 *   `true`, the terminal `KeyUp` is swallowed instead of firing an extra
 *   single-step seek on top of the fast-seek ticks already applied.
 * - CENTER/Enter/DOWN are only special-cased while [OverlayState.Hidden]:
 *   once Revealed, the rows are composed and Compose's normal focus/click
 *   handling already does the right thing.
 */
fun decideDpadAction(
    key: Key,
    type: KeyEventType,
    repeatCount: Int,
    longPressActive: Boolean,
    controlsFocused: Boolean,
    overlayState: OverlayState,
): DpadAction {
    val direction = when (key) {
        Key.DirectionLeft -> SeekDirection.Backward
        Key.DirectionRight -> SeekDirection.Forward
        else -> null
    }

    if (direction != null && !controlsFocused) {
        return when (type) {
            KeyEventType.KeyDown -> if (repeatCount > 0) {
                DpadAction.Seek(signedAmount(direction, SeekAmount.forPress(repeatCount)))
            } else {
                DpadAction.Ignore // let KeyUp below dispatch a clean single press
            }

            KeyEventType.KeyUp -> when {
                longPressActive -> DpadAction.Ignore // fast-seek ticks already applied; swallow the release
                overlayState is OverlayState.Hidden -> DpadAction.Reveal
                else -> DpadAction.Seek(signedAmount(direction, SeekAmount.SINGLE_STEP_MS))
            }

            else -> DpadAction.Ignore
        }
    }

    if (type == KeyEventType.KeyUp && !controlsFocused && overlayState is OverlayState.Hidden) {
        return when (key) {
            Key.DirectionDown -> DpadAction.EnterControls
            Key.DirectionCenter, Key.Enter -> DpadAction.PlayPause
            else -> DpadAction.Ignore
        }
    }

    return DpadAction.Ignore
}

private fun signedAmount(direction: SeekDirection, amountMs: Long): Long =
    if (direction == SeekDirection.Backward) -amountMs else amountMs
