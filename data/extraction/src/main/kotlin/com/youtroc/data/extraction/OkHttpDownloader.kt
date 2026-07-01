package com.youtroc.data.extraction

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Pure-JVM [Downloader] backed by OkHttp, modeled on the extractor's own reference
 * implementation (DownloaderTestImpl, which lives in the extractor's test sources and
 * is not shipped, so we adapt it rather than import it).
 *
 * The abstract [Downloader] declares exactly one abstract method — [execute] — that all
 * get/head/post helpers delegate to, so this is the whole contract a backend must satisfy.
 */
class OkHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : Downloader() {

    // Recent Firefox-ESR User-Agent, as the extractor's own downloader uses.
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()

        val okRequest = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", userAgent)
            .apply {
                // Overwrite defaults per header, then add every value — a plain addHeader
                // would duplicate headers the extractor already set.
                request.headers().forEach { (name, values) ->
                    removeHeader(name)
                    values.forEach { value -> addHeader(name, value) }
                }
            }
            .build()

        client.newCall(okRequest).execute().use { resp ->
            // Contract the extractor relies on: HTTP 429 MUST surface as ReCaptchaException,
            // otherwise it manifests upstream as confusing parse failures.
            if (resp.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                resp.body?.string(),
                resp.request.url.toString(), // latest (possibly redirected) URL
            )
        }
    }
}
