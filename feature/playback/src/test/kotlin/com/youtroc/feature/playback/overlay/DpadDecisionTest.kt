package com.youtroc.feature.playback.overlay

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure D-pad decision tests (REQ-11, MAJOR M6): no Compose state, no Android
 * `KeyEvent`, no focus involved — [decideDpadAction] only classifies a key
 * event into a [DpadAction]; [com.youtroc.feature.playback.PlayerOverlay]
 * applies the side effects.
 */
class DpadDecisionTest {

    @Test
    fun `first KeyDown of a press is ignored so KeyUp dispatches a clean single press`() {
        val action = decideDpadAction(
            key = Key.DirectionRight,
            type = KeyEventType.KeyDown,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Hidden,
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `single tap KeyUp while hidden only reveals, no seek`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Hidden,
        )

        assertEquals(DpadAction.Reveal, action)
    }

    @Test
    fun `single tap KeyUp while revealed seeks by exactly the 10s single step`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Seek(-SeekAmount.SINGLE_STEP_MS), action)
    }

    @Test
    fun `second press while revealed seeks forward`() {
        val action = decideDpadAction(
            key = Key.DirectionRight,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Seek(SeekAmount.SINGLE_STEP_MS), action)
    }

    @Test
    fun `KeyDown repeats fast-seek per SeekAmount while already revealed`() {
        val action = decideDpadAction(
            key = Key.DirectionRight,
            type = KeyEventType.KeyDown,
            repeatCount = 3,
            longPressActive = true,
            controlsFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Seek(SeekAmount.forPress(3)), action)
    }

    @Test
    fun `held-from-hidden reveals then fast-seeks instead of doing nothing`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyDown,
            repeatCount = 2,
            longPressActive = true,
            controlsFocused = false,
            overlayState = OverlayState.Hidden,
        )

        assertEquals(DpadAction.Seek(-SeekAmount.forPress(2)), action)
    }

    @Test
    fun `terminal KeyUp after a long-press is swallowed, no extra single-step`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyUp,
            repeatCount = 0, // Android's real KeyUp.repeatCount is ALWAYS 0 (MAJOR M3)
            longPressActive = true,
            controlsFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `CENTER from hidden reveals and toggles play-pause directly`() {
        val action = decideDpadAction(
            key = Key.DirectionCenter,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Hidden,
        )

        assertEquals(DpadAction.PlayPause, action)
    }

    @Test
    fun `Enter from hidden reveals and toggles play-pause directly`() {
        val action = decideDpadAction(
            key = Key.Enter,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Hidden,
        )

        assertEquals(DpadAction.PlayPause, action)
    }

    @Test
    fun `DOWN from hidden reveals and requests entering the controls`() {
        val action = decideDpadAction(
            key = Key.DirectionDown,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Hidden,
        )

        assertEquals(DpadAction.EnterControls, action)
    }

    @Test
    fun `direction keys are ignored once focus is inside the controls`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `DOWN while already revealed is ignored so Compose's own focus search moves into the rows`() {
        val action = decideDpadAction(
            key = Key.DirectionDown,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            controlsFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }
}
