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
 * YouTube's internal `youtubei/v1/player` endpoint -- no cipher/n-param
 * descrambling, no PoToken/BotGuard (spike-confirmed unnecessary). Third
 * strangler-fig slice of the own InnerTube engine; VOD only. Serves TWO
 * ladder rungs off the SAME implementation, parameterized by [context]: the
 * primary [androidVr] identity and the ios fallback-rung [ios] identity
 * (design D3) -- selected/ordered by
 * [com.youtroc.data.extraction.stream.LadderStreamProvider], which is also
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
 *
 * The primary constructor is `internal` -- [PlayerClientContext] is an
 * internal type and a public constructor cannot take an internal-typed
 * parameter (Kotlin rule). `:app` only ever sees the public [androidVr]/
 * [ios] factories below, which name INTENT, not mechanism.
 */
class InnerTubeStreamProvider internal constructor(
    private val client: OkHttpClient = InnerTubeHttp.client,
    private val regionCode: String? = null,
    private val context: PlayerClientContext = PlayerClientContext.ANDROID_VR,
) : StreamProvider {

    override suspend fun playableStreams(videoId: VideoId): StreamResult = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(videoId.value, regionCode, context)
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

    companion object {
        /** The PRIMARY rung identity -- Oculus/Quest 3, sdk 32, ANDROID_VR v1.60.19 (byte-identical to the shipped 4K60 path). */
        fun androidVr(client: OkHttpClient = InnerTubeHttp.client, regionCode: String? = null): InnerTubeStreamProvider =
            InnerTubeStreamProvider(client, regionCode, PlayerClientContext.ANDROID_VR)

        /** The FALLBACK-RUNG-ONLY ios identity -- never primary, never raced against android_vr (owner decision, design D1). */
        fun ios(client: OkHttpClient = InnerTubeHttp.client, regionCode: String? = null): InnerTubeStreamProvider =
            InnerTubeStreamProvider(client, regionCode, PlayerClientContext.IOS)
    }
}

/**
 * Builds the InnerTube `player` POST request from [buildPlayerRequest]'s pure
 * DTO -- the only I/O-touching step is `OkHttpClient.newCall(...).execute()`
 * in [InnerTubeStreamProvider.playableStreams], never this function.
 * `internal` (not `private`) so [context]'s conditional `User-Agent` header
 * is directly unit-testable. Attaches [PlayerClientContext.userAgent] as an
 * HTTP header ONLY when set -- `null` for [PlayerClientContext.ANDROID_VR]
 * (OkHttp's default UA stays unchanged, matching today's shipped request).
 */
internal fun buildRequest(
    videoId: String,
    regionCode: String?,
    context: PlayerClientContext = PlayerClientContext.ANDROID_VR,
): Request {
    val payload = buildPlayerRequest(videoId, regionCode, context)
    val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
    val builder = Request.Builder()
        .url(PLAYER_URL)
        .post(body)
    context.userAgent?.let { builder.header("User-Agent", it) }
    return builder.build()
}

/**
 * Builds the InnerTube `player` request body -- pure construction, no I/O, so
 * the shaping policy ([context] identity, `hl=es`, `gl` from [regionCode])
 * is directly unit-testable. Mirrors [buildDetailRequest]/[buildSearchRequest]'s
 * blank-region convention -- blank/empty regions omit `gl` rather than
 * sending `""`. [context] defaults to [PlayerClientContext.ANDROID_VR] --
 * the 2-arg call site stays byte-identical to the shipped android_vr request
 * (same field order, no [Client] DTO change).
 */
internal fun buildPlayerRequest(
    videoId: String,
    regionCode: String?,
    context: PlayerClientContext = PlayerClientContext.ANDROID_VR,
): PlayerRequest =
    PlayerRequest(
        context = Context(
            client = Client(
                clientName = context.clientName,
                clientVersion = context.clientVersion,
                hl = INNERTUBE_HL,
                gl = regionCode?.takeIf { it.isNotBlank() },
                deviceMake = context.deviceMake,
                deviceModel = context.deviceModel,
                androidSdkVersion = context.androidSdkVersion,
            ),
        ),
        videoId = videoId,
    )
