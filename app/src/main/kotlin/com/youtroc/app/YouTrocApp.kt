package com.youtroc.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.youtroc.data.extraction.NewPipeStreamProvider
import com.youtroc.data.extraction.innertube.InnerTubeStreamProvider
import com.youtroc.data.extraction.stream.LadderStreamProvider
import com.youtroc.data.extraction.stream.PrefetchingStreamProvider
import com.youtroc.data.extraction.stream.StreamRung
import com.youtroc.data.extraction.stream.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Configures a single shared Coil [ImageLoader] — a first-class performance guardrail
 * for the 3 GB C6K: one loader, a capped memory cache, and crossfade. Thumbnails are
 * downsampled by Coil to each card's constrained layout size, never full-res.
 */
class YouTrocApp : Application(), SingletonImageLoader.Factory {

    /**
     * Backs playback progress persistence (BLOCKER B1): OUTLIVES any single
     * NavBackStackEntry/ViewModel, so a `WatchProgressStore.save()` launched
     * from `PlaybackViewModel.pause()`/`onCleared()` still completes even
     * when the player destination's `ViewModelStore` is cleared mid-write
     * (e.g. BACK) — unlike `viewModelScope`, which is cancelled at that exact
     * moment. `SupervisorJob` so one failed save never cancels the others;
     * `Dispatchers.IO` since every save is a DataStore file write.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Speculative-prefetch caching decorator (design D1), same
     * cross-nav-entry lifetime idiom as [applicationScope]. The delegate
     * [LadderStreamProvider]/decorator pair is circular (the delegate's
     * `onResolved` callback needs the decorator; the decorator wraps the
     * delegate) — broken by a DEFERRED-capture lambda: the delegate is built
     * first with a lambda that only dereferences the `lateinit var` when a
     * resolution actually fires (always after assignment below). Binding
     * `decorator::recordSource` eagerly here would throw
     * `UninitializedPropertyAccessException` at construction.
     *
     * 3-rung ladder (stream-client-ladder): android_vr (primary) -> ios
     * (own fallback rung) -> NewPipe (terminal safety net). android_vr is
     * ALWAYS attempted first and stays byte-identical to the shipped
     * request; ios is reachable only when android_vr did not succeed.
     */
    val streamProvider: PrefetchingStreamProvider by lazy {
        lateinit var decorator: PrefetchingStreamProvider
        val delegate = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, InnerTubeStreamProvider.androidVr()),
                StreamRung(StreamSource.IOS, InnerTubeStreamProvider.ios()),
                StreamRung(StreamSource.FALLBACK, NewPipeStreamProvider()),
            ),
            onResolved = { id, s -> decorator.recordSource(id, s) },
        )
        decorator = PrefetchingStreamProvider(delegate = delegate, scope = applicationScope)
        decorator
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .crossfade(true)
            .build()
}
