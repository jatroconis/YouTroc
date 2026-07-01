package com.youtroc.core.domain.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VideoIdTest {

    @Test
    fun `holds the raw id value`() {
        val id = VideoId("dQw4w9WgXcQ")

        assertEquals("dQw4w9WgXcQ", id.value)
    }

    @Test
    fun `rejects a blank id`() {
        assertFailsWith<IllegalArgumentException> {
            VideoId("   ")
        }
    }
}
