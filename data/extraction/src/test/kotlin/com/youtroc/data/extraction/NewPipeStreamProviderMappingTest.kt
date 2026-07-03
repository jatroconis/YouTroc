package com.youtroc.data.extraction

import com.youtroc.core.domain.stream.HdrFormat
import com.youtroc.core.domain.stream.StreamKind
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T10 investigation (apply-time, per design/tasks): decompiled
 * NewPipeExtractor v0.26.3 (`org.schabi.newpipe.extractor.stream.VideoStream`,
 * its `Stream` superclass, and `ItagItem`) via `javap` -- NONE expose any
 * color/transfer-characteristics/HDR accessor, and a full `strings` scan of
 * the jar contains zero occurrences of "HDR" in any form (not even in
 * quality-label text). NewPipeExtractor v0.26.3 has NO HDR signal to map.
 *
 * Consequence (REQ-H2's NewPipe fallback scenario): the mapping ALWAYS
 * defaults to [HdrFormat.SDR] for this NewPipe version -- safe, no
 * regression, and explicit (never a stale/wrong non-default value) rather
 * than silently dropped. This is a REGRESSION GUARD, not a real mapping
 * table: if a future NewPipeExtractor upgrade adds a color accessor, this
 * test documents exactly what must change.
 */
class NewPipeStreamProviderMappingTest {

    private fun videoStream(): VideoStream = VideoStream.Builder()
        .setId("id")
        .setContent("https://cdn/video", true)
        .setIsVideoOnly(true)
        .setResolution("1080p")
        .setMediaFormat(MediaFormat.MPEG_4)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setItagItem(ItagItem(137, ItagItem.ItagType.VIDEO, MediaFormat.MPEG_4, 1080))
        .build()

    @Test
    fun `NewPipe stream mapping always defaults hdr to SDR -- v0-26-3 exposes no HDR signal`() {
        val provider = NewPipeStreamProvider()

        val stream = with(provider) { videoStream().toDomainOrNull(StreamKind.VIDEO_ONLY) }

        assertEquals(HdrFormat.SDR, stream?.hdr)
    }
}
