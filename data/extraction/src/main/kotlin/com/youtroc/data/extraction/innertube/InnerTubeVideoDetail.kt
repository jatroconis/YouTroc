package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.VideoDetail
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.cancellation.CancellationException

private const val NEXT_URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

/**
 * Adapter: own-engine implementation of the domain [VideoDetail] port over
 * YouTube's internal `youtubei/v1/next` endpoint -- no API key, no
 * `visitorData`, no PoToken. Second strangler-fig slice of the own InnerTube
 * engine; selected ahead of
 * [com.youtroc.data.extraction.detail.NewPipeVideoDetail] by
 * [com.youtroc.data.extraction.detail.FallbackVideoDetail].
 *
 * OkHttp/kotlinx.serialization/InnerTube DTO types never cross this class --
 * callers only ever see [DetailResult] / [com.youtroc.core.domain.detail.VideoDetailInfo]
 * / [com.youtroc.core.domain.catalog.Video]. Mirrors [InnerTubeVideoSearch]'s
 * shape: `withContext(Dispatchers.IO)`, cancellation rethrown before the
 * generic catch, everything else mapped via [toDetailResult]. Shares the
 * [InnerTubeHttp] connection pool with the search adapter.
 */
class InnerTubeVideoDetail(
    private val client: OkHttpClient = InnerTubeHttp.client,
    private val regionCode: String? = null,
) : VideoDetail {

    override suspend fun detail(videoId: VideoId): DetailResult = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(videoId.value, regionCode)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("InnerTube next returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<NextResponse>(body)
                val info = parsed.videoDetailInfoOrNull(videoId.value)
                if (info == null) DetailResult.NotAvailable else DetailResult.Success(info)
            }
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toDetailResult()
        }
    }
}

/**
 * Builds the InnerTube `next` POST request from [buildDetailRequest]'s pure
 * DTO -- the only I/O-touching step is `OkHttpClient.newCall(...).execute()`
 * in [InnerTubeVideoDetail.detail], never this function.
 */
private fun buildRequest(videoId: String, regionCode: String?): Request {
    val payload = buildDetailRequest(videoId, regionCode)
    val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
    return Request.Builder()
        .url(NEXT_URL)
        .post(body)
        .build()
}

/**
 * Builds the InnerTube `next` request body -- pure construction, no I/O, so
 * the shaping policy (`hl=es`, `gl` from [regionCode]) is directly
 * unit-testable. Mirrors [buildSearchRequest]'s blank-region convention --
 * blank/empty regions omit `gl` rather than sending `""`.
 */
internal fun buildDetailRequest(videoId: String, regionCode: String?): DetailRequest =
    DetailRequest(
        context = Context(
            client = Client(
                clientName = INNERTUBE_CLIENT_NAME,
                clientVersion = INNERTUBE_CLIENT_VERSION,
                hl = INNERTUBE_HL,
                gl = regionCode?.takeIf { it.isNotBlank() },
            ),
        ),
        videoId = videoId,
    )
