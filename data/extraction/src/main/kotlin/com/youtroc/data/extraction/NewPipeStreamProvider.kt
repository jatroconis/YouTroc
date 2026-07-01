package com.youtroc.data.extraction

import com.youtroc.core.domain.playback.ManifestInputs
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.playback.PlaybackSelectionPolicy
import com.youtroc.core.domain.stream.PlayableStreams
import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.schabi.newpipe.extractor.stream.Stream as NewPipeStream

/**
 * Adapter: implements the domain [StreamProvider] port with NewPipeExtractor.
 *
 * It translates NewPipe's world into the domain's ubiquitous language and maps every
 * failure onto a typed [StreamResult] — nothing throws across the port boundary. The
 * NewPipe types stay inside this class; they never leak into the domain.
 *
 * Ad-free by construction: NewPipeExtractor only parses streamingData.formats and
 * .adaptiveFormats. YouTube's adPlacements/playerAds fields are never deserialized, so
 * there is no ad-break data in the object graph to honor — structural absence, not a filter.
 */
class NewPipeStreamProvider(
    private val bootstrap: () -> Unit = NewPipeBootstrap::ensureInitialized,
) : StreamProvider {

    override suspend fun playableStreams(videoId: VideoId): StreamResult =
        withContext(Dispatchers.IO) {
            bootstrap()
            try {
                val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl(videoId))
                val streams = collectStreams(info)
                if (streams.isEmpty()) {
                    // Empty format lists are the classic SABR / PoToken-gate degradation,
                    // not a healthy extraction: treat as "cannot play anonymously".
                    StreamResult.NotAvailable
                } else {
                    StreamResult.Success(PlayableStreams(streams, manifest(info, streams)))
                }
            } catch (e: CancellationException) {
                throw e // never swallow cooperative cancellation
            } catch (e: Exception) {
                e.toStreamResult()
            }
        }

    private fun collectStreams(info: StreamInfo): List<Stream> =
        buildList {
            info.videoStreams.forEach { it.toDomainOrNull(StreamKind.MUXED)?.let(::add) }
            info.videoOnlyStreams.forEach { it.toDomainOrNull(StreamKind.VIDEO_ONLY)?.let(::add) }
            info.audioStreams.forEach { it.toDomainOrNull(StreamKind.AUDIO_ONLY)?.let(::add) }
        }

    /**
     * Assembling the DASH manifest is best-effort: a malformed/unusual itag
     * never fails the whole extraction, it just degrades to
     * PROGRESSIVE/MERGED delivery ([PlaybackSelectionPolicy] handles that
     * fallback, and a lone video-only track is never selected).
     */
    private fun manifest(info: StreamInfo, streams: List<Stream>): PlaybackManifest? {
        val dashMpd = try {
            DashManifestAssembler.assemble(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val inputs = ManifestInputs(
            dashMpd = dashMpd,
            muxedUrl = streams.filter { it.kind == StreamKind.MUXED }.bestByQualityOrNull()?.url,
            videoOnlyUrl = streams.filter { it.kind == StreamKind.VIDEO_ONLY }.bestByQualityOrNull()?.url,
            audioOnlyUrl = streams.filter { it.kind == StreamKind.AUDIO_ONLY }.bestByQualityOrNull()?.url,
        )
        return PlaybackSelectionPolicy.select(inputs)
    }

    private fun NewPipeStream.toDomainOrNull(kind: StreamKind): Stream? {
        // getContent() is the URL for progressive streams or an inline manifest for DASH/HLS.
        val content = this.content
        if (content.isNullOrBlank()) return null
        return Stream(
            url = content,
            container = format?.suffix.orEmpty(),
            kind = kind,
            codec = codecOrNull(),
            heightPx = heightOrNull(),
            bitrateBps = bitrateOrNull(),
        )
    }

    private fun NewPipeStream.codecOrNull() =
        (this as? VideoStream)?.codec?.let(::toDomainVideoCodec)

    private fun NewPipeStream.heightOrNull(): Int? =
        (this as? VideoStream)?.height?.takeIf { it > 0 }

    private fun NewPipeStream.bitrateOrNull(): Int? = when (this) {
        is VideoStream -> bitrate.takeIf { it > 0 }
        is AudioStream -> bitrate.takeIf { it > 0 }
        else -> null
    }

    private fun watchUrl(videoId: VideoId): String =
        "https://www.youtube.com/watch?v=${videoId.value}"
}

/**
 * Maps a NewPipe failure onto the domain's typed outcome. Kept as an internal top-level
 * function so the mapping policy is deterministic and unit-testable without any network.
 *
 * Order matters: [ContentNotAvailableException] is a subtype of [ExtractionException].
 */
internal fun Throwable.toStreamResult(): StreamResult = when (this) {
    is ContentNotAvailableException -> StreamResult.NotAvailable
    is IOException -> StreamResult.Offline
    is ExtractionException -> StreamResult.Error(this)
    else -> StreamResult.Error(this)
}
