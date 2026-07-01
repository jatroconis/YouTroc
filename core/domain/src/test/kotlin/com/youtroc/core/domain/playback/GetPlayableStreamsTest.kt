package com.youtroc.core.domain.playback

import com.youtroc.core.domain.stream.FakeStreamProvider
import com.youtroc.core.domain.stream.PlayableStreams
import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetPlayableStreamsTest {

    private val anyVideo = VideoId("dQw4w9WgXcQ")

    @Test
    fun `returns the streams the provider resolves`() = runTest {
        val streams = PlayableStreams(
            listOf(Stream(url = "https://cdn/av1", container = "webm", kind = StreamKind.VIDEO_ONLY)),
        )
        val useCase = GetPlayableStreams(FakeStreamProvider(StreamResult.Success(streams)))

        val result = useCase(anyVideo)

        assertEquals(StreamResult.Success(streams), result)
    }

    @Test
    fun `asks the provider for the requested video`() = runTest {
        val provider = FakeStreamProvider(StreamResult.NotAvailable)
        val useCase = GetPlayableStreams(provider)

        useCase(anyVideo)

        assertEquals(anyVideo, provider.lastRequested)
    }

    @Test
    fun `propagates a typed failure without throwing`() = runTest {
        val useCase = GetPlayableStreams(FakeStreamProvider(StreamResult.Offline))

        assertEquals(StreamResult.Offline, useCase(anyVideo))
    }
}
