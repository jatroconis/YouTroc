package com.youtroc.feature.playback.overlay

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure D-pad decision tests (REQ-11, Netflix-style overlay redesign): no
 * Compose state, no Android `KeyEvent`, no focus involved — [decideDpadAction]
 * only classifies a key event into a [DpadAction];
 * [com.youtroc.feature.playback.PlayerOverlay] applies the side effects.
 *
 * The model: from Hidden ANY press reveals (focus lands on play/pause);
 * seeking only happens while the SCRUBBER owns focus; OK on the scrubber
 * toggles play/pause; everything else is [DpadAction.Ignore] so Compose's
 * contained per-zone focus search moves focus.
 */
class DpadDecisionTest {

    // --- Reveal from Hidden -------------------------------------------------

    @Test
    fun `first KeyDown of a press from hidden is ignored so KeyUp reveals cleanly`() {
        val action = decideDpadAction(
            key = Key.DirectionDown,
            type = KeyEventType.KeyDown,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = false,
            overlayState = OverlayState.Hidden,
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `DOWN KeyUp while hidden reveals`() {
        assertEquals(DpadAction.Reveal, revealKeyFromHidden(Key.DirectionDown))
    }

    @Test
    fun `UP KeyUp while hidden reveals`() {
        assertEquals(DpadAction.Reveal, revealKeyFromHidden(Key.DirectionUp))
    }

    @Test
    fun `LEFT KeyUp while hidden only reveals, never seeks`() {
        assertEquals(DpadAction.Reveal, revealKeyFromHidden(Key.DirectionLeft))
    }

    @Test
    fun `RIGHT KeyUp while hidden only reveals, never seeks`() {
        assertEquals(DpadAction.Reveal, revealKeyFromHidden(Key.DirectionRight))
    }

    @Test
    fun `CENTER KeyUp while hidden reveals (no direct play-pause)`() {
        assertEquals(DpadAction.Reveal, revealKeyFromHidden(Key.DirectionCenter))
    }

    @Test
    fun `Enter KeyUp while hidden reveals`() {
        assertEquals(DpadAction.Reveal, revealKeyFromHidden(Key.Enter))
    }

    private fun revealKeyFromHidden(key: Key): DpadAction = decideDpadAction(
        key = key,
        type = KeyEventType.KeyUp,
        repeatCount = 0,
        longPressActive = false,
        scrubberFocused = false,
        overlayState = OverlayState.Hidden,
    )

    // --- Scrubbing (scrubber focused) --------------------------------------

    @Test
    fun `LEFT tap while scrubber focused seeks backward by the 10s single step`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Seek(-SeekAmount.SINGLE_STEP_MS), action)
    }

    @Test
    fun `RIGHT tap while scrubber focused seeks forward by the 10s single step`() {
        val action = decideDpadAction(
            key = Key.DirectionRight,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Seek(SeekAmount.SINGLE_STEP_MS), action)
    }

    @Test
    fun `first KeyDown of a scrubber press is ignored so KeyUp dispatches a clean single step`() {
        val action = decideDpadAction(
            key = Key.DirectionRight,
            type = KeyEventType.KeyDown,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `held KeyDown repeats fast-seek per SeekAmount while scrubber focused`() {
        val action = decideDpadAction(
            key = Key.DirectionRight,
            type = KeyEventType.KeyDown,
            repeatCount = 3,
            longPressActive = true,
            scrubberFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Seek(SeekAmount.forPress(3)), action)
    }

    @Test
    fun `terminal KeyUp after a scrubber long-press is swallowed, no extra single-step`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyUp,
            repeatCount = 0, // Android's real KeyUp.repeatCount is ALWAYS 0 (MAJOR M3)
            longPressActive = true,
            scrubberFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `OK on the focused scrubber toggles play-pause`() {
        val action = decideDpadAction(
            key = Key.DirectionCenter,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.PlayPause, action)
    }

    @Test
    fun `Enter on the focused scrubber toggles play-pause`() {
        val action = decideDpadAction(
            key = Key.Enter,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = true,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.PlayPause, action)
    }

    // --- Revealed but scrubber NOT focused (controls / panel own focus) -----

    @Test
    fun `LEFT while revealed and scrubber not focused is ignored so Compose contains the controls`() {
        val action = decideDpadAction(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `RIGHT while revealed and scrubber not focused is ignored (never seeks off the controls row)`() {
        val action = decideDpadAction(
            key = Key.DirectionRight,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `OK while revealed and scrubber not focused is ignored so the focused button handles it`() {
        val action = decideDpadAction(
            key = Key.DirectionCenter,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, action)
    }

    @Test
    fun `UP and DOWN while revealed are ignored so Compose's contained focus search moves between zones`() {
        val up = decideDpadAction(
            key = Key.DirectionUp,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )
        val down = decideDpadAction(
            key = Key.DirectionDown,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            longPressActive = false,
            scrubberFocused = false,
            overlayState = OverlayState.Revealed(sinceMs = 1_000L),
        )

        assertEquals(DpadAction.Ignore, up)
        assertEquals(DpadAction.Ignore, down)
    }
}
