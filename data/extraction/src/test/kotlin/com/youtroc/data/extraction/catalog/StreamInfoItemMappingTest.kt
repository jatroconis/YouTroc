package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of `StreamInfoItem` -> [Video] mapping:
 * constructs fake NewPipe POJOs (public constructors + setters, no network) and
 * asserts the field mapping, `viewCount` normalization, dropped-when-idless items,
 * blank-field coalescing, and the [pickThumbnail] policy.
 */
class StreamInfoItemMappingTest {

    private fun item(
        url: String = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        name: String = "A trending video",
        uploaderName: String = "A channel",
        viewCount: Long = 1_000_000,
        textualUploadDate: String? = "2 days ago",
        thumbnails: List<Image> = emptyList(),
    ): StreamInfoItem =
        StreamInfoItem(0, url, name, StreamType.VIDEO_STREAM).apply {
            this.uploaderName = uploaderName
            this.viewCount = viewCount
            this.textualUploadDate = textualUploadDate
            this.thumbnails = thumbnails
        }

    @Test
    fun `maps all fields from a well-formed StreamInfoItem`() {
        val thumbnail = Image(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            360,
            480,
            Image.ResolutionLevel.MEDIUM,
        )

        val video = item(thumbnails = listOf(thumbnail)).toVideoOrNull()

        assertEquals(
            Video(
                id = VideoId("dQw4w9WgXcQ"),
                title = "A trending video",
                channelName = "A channel",
                thumbnailUrl = thumbnail.url,
                viewCount = 1_000_000,
                publishedText = "2 days ago",
            ),
            video,
        )
    }

    @Test
    fun `unknown view count -1 maps to null`() {
        val video = item(viewCount = -1).toVideoOrNull()

        assertEquals(null, video?.viewCount)
    }

    @Test
    fun `an item whose url yields no video id is dropped`() {
        val video = item(url = "https://www.youtube.com/").toVideoOrNull()

        assertNull(video)
    }

    @Test
    fun `blank title is coalesced to a placeholder`() {
        val video = item(name = "").toVideoOrNull()

        assertEquals("Untitled video", video?.title)
    }

    @Test
    fun `blank channel name is coalesced to a placeholder`() {
        val video = item(uploaderName = "").toVideoOrNull()

        assertEquals("Unknown channel", video?.channelName)
    }

    @Test
    fun `pickThumbnail prefers the widest image`() {
        val small = Image("https://i.ytimg.com/vi/abc/default.jpg", 90, 120, Image.ResolutionLevel.LOW)
        val large = Image("https://i.ytimg.com/vi/abc/hqdefault.jpg", 360, 480, Image.ResolutionLevel.MEDIUM)

        assertEquals(large.url, pickThumbnail(listOf(small, large), "abc"))
    }

    @Test
    fun `pickThumbnail falls back to the hq720 template when there are no images`() {
        assertEquals("https://i.ytimg.com/vi/abc/hq720.jpg", pickThumbnail(emptyList(), "abc"))
    }
}
