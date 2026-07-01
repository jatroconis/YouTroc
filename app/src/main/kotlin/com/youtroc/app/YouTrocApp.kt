package com.youtroc.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
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
