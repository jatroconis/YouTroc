package com.youtroc.core.domain.stream

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PlayableStreamsTest {

    @Test
    fun `requires at least one stream`() {
        assertFailsWith<IllegalArgumentException> {
            PlayableStreams(emptyList())
        }
    }
}
