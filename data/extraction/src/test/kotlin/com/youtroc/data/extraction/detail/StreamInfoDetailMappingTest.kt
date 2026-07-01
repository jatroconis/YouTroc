package com.youtroc.data.extraction.detail

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic, network-free verification of `StreamInfo` -> `VideoDetailInfo`
 * mapping: constructs a fake NewPipe `StreamInfo` POJO (public constructor +
 * setters, no network — jar-verified, design-gate-review #4419) and asserts
 * the field mapping, `viewCount` normalization, `Description.content`
 * extraction, and related-items delegation to the reused catalog
 * `toVideoOrNull` mapping.
 */
class StreamInfoDetailMappingTest {

    private fun streamInfo(
        id: String = "dQw4w9WgXcQ",
        name: String = "A video",
        uploaderName: String = "A channel",
        viewCount: Long = 1_000_000,
        textualUploadDate: String? = "2 days ago",
        description: Description? = Description("A description", Description.PLAIN_TEXT),
        relatedItems: List<StreamInfoItem> = emptyList(),
    ): StreamInfo =
        StreamInfo(
            0,
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?v=$id",
            StreamType.VIDEO_STREAM,
            id,
            name,
            -1,
        ).apply {
            this.uploaderName = uploaderName
            this.viewCount = viewCount
            this.textualUploadDate = textualUploadDate
            this.description = description
            this.relatedItems = relatedItems
        }

    private fun relatedItem(
        url: String = "https://www.youtube.com/watch?v=relatedVid1",
        name: String = "Related video",
        uploaderName: String = "Related channel",
    ): StreamInfoItem =
        StreamInfoItem(0, url, name, StreamType.VIDEO_STREAM).apply {
            this.uploaderName = uploaderName
        }

    @Test
    fun `maps title, channel, view count and published date`() {
        val detail = streamInfo().toVideoDetailInfo(VideoId("dQw4w9WgXcQ"))

        assertEquals("A video", detail.title)
        assertEquals("A channel", detail.channelName)
        assertEquals(1_000_000, detail.viewCount)
        assertEquals("2 days ago", detail.publishedText)
    }

    @Test
    fun `extracts description content, not the Description object`() {
        val detail = streamInfo(description = Description("Plain text body", Description.PLAIN_TEXT))
            .toVideoDetailInfo(VideoId("dQw4w9WgXcQ"))

        assertEquals("Plain text body", detail.description)
    }

    @Test
    fun `blank description maps to null`() {
        val detail = streamInfo(description = Description("   ", Description.PLAIN_TEXT))
            .toVideoDetailInfo(VideoId("dQw4w9WgXcQ"))

        assertNull(detail.description)
    }

    @Test
    fun `unknown view count -1 maps to null`() {
        val detail = streamInfo(viewCount = -1).toVideoDetailInfo(VideoId("dQw4w9WgXcQ"))

        assertNull(detail.viewCount)
    }

    @Test
    fun `empty related is a valid mapping, not an error`() {
        val detail = streamInfo(relatedItems = emptyList()).toVideoDetailInfo(VideoId("dQw4w9WgXcQ"))

        assertTrue(detail.related.isEmpty())
    }

    @Test
    fun `related items delegate to the reused catalog toVideoOrNull mapping`() {
        val detail = streamInfo(relatedItems = listOf(relatedItem()))
            .toVideoDetailInfo(VideoId("dQw4w9WgXcQ"))

        assertEquals(
            listOf(
                Video(
                    id = VideoId("relatedVid1"),
                    title = "Related video",
                    channelName = "Related channel",
                    thumbnailUrl = "https://i.ytimg.com/vi/relatedVid1/hq720.jpg",
                    viewCount = null,
                    publishedText = null,
                ),
            ),
            detail.related,
        )
    }

    @Test
    fun `videoId passed to the mapper is carried through untouched`() {
        val detail = streamInfo(id = "xyz789").toVideoDetailInfo(VideoId("xyz789"))

        assertEquals(VideoId("xyz789"), detail.videoId)
    }
}
