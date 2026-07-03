package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
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

private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

/**
 * Adapter: own-engine implementation of the domain [StreamProvider] port over
 * YouTube's internal `youtubei/v1/player` endpoint, requested as ANDROID_VR
 * -- no cipher/n-param descrambling, no PoToken/BotGuard (spike-confirmed
 * unnecessary). Third strangler-fig slice of the own InnerTube engine; VOD
 * only -- selected ahead of
 * [com.youtroc.data.extraction.NewPipeStreamProvider] by
 * [com.youtroc.data.extraction.stream.FallbackStreamProvider], which is also
 * the safety net for android_vr's A/B throttling and live video (D4: this
 * adapter attempts no live-manifest code path at all).
 *
 * OkHttp/kotlinx.serialization/InnerTube DTO types never cross this class --
 * callers only ever see [StreamResult] / [com.youtroc.core.domain.stream.PlayableStreams]
 * / [com.youtroc.core.domain.playback.PlaybackManifest]. Mirrors
 * [InnerTubeVideoDetail]/[InnerTubeVideoSearch]'s shape:
 * `withContext(Dispatchers.IO)`, cancellation rethrown before the generic
 * catch, everything else mapped via [PlayerResponse.toStreamResult] /
 * [Throwable.toStreamResult]. Shares the [InnerTubeHttp] connection pool.
 */
class InnerTubeStreamProvider(
    private val client: OkHttpClient = InnerTubeHttp.client,
    private val regionCode: String? = null,
) : StreamProvider {

    override suspend fun playableStreams(videoId: VideoId): StreamResult = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(videoId.value, regionCode)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("InnerTube player returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<PlayerResponse>(body)
                parsed.toStreamResult()
            }
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toStreamResult()
        }
    }
}

/**
 * Builds the InnerTube `player` POST request from [buildPlayerRequest]'s pure
 * DTO -- the only I/O-touching step is `OkHttpClient.newCall(...).execute()`
 * in [InnerTubeStreamProvider.playableStreams], never this function.
 */
private fun buildRequest(videoId: String, regionCode: String?): Request {
    val payload = buildPlayerRequest(videoId, regionCode)
    val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
    return Request.Builder()
        .url(PLAYER_URL)
        .post(body)
        .build()
}

/**
 * Builds the InnerTube `player` request body -- pure construction, no I/O, so
 * the shaping policy (ANDROID_VR identity, `hl=es`, `gl` from [regionCode])
 * is directly unit-testable. Mirrors [buildDetailRequest]/[buildSearchRequest]'s
 * blank-region convention -- blank/empty regions omit `gl` rather than
 * sending `""`.
 */
internal fun buildPlayerRequest(videoId: String, regionCode: String?): PlayerRequest =
    PlayerRequest(
        context = Context(
            client = Client(
                clientName = ANDROID_VR_CLIENT_NAME,
                clientVersion = ANDROID_VR_CLIENT_VERSION,
                hl = INNERTUBE_HL,
                gl = regionCode?.takeIf { it.isNotBlank() },
                deviceMake = ANDROID_VR_DEVICE_MAKE,
                deviceModel = ANDROID_VR_DEVICE_MODEL,
                androidSdkVersion = ANDROID_VR_SDK_VERSION,
            ),
        ),
        videoId = videoId,
    )
