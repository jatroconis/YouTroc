package com.youtroc.feature.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeFocusTargetTest {

    @Test
    fun `Loading derives NONE`() {
        assertEquals(HomeFocusTarget.NONE, HomeUiState.Loading.focusTarget())
    }

    @Test
    fun `Content derives CONTENT even with an empty shelf list`() {
        assertEquals(HomeFocusTarget.CONTENT, HomeUiState.Content(emptyList()).focusTarget())
    }

    @Test
    fun `Offline derives MESSAGE`() {
        assertEquals(HomeFocusTarget.MESSAGE, HomeUiState.Offline.focusTarget())
    }

    @Test
    fun `Error derives MESSAGE`() {
        assertEquals(HomeFocusTarget.MESSAGE, HomeUiState.Error.focusTarget())
    }

    @Test
    fun `Empty derives MESSAGE`() {
        assertEquals(HomeFocusTarget.MESSAGE, HomeUiState.Empty.focusTarget())
    }
}
