package com.youtroc.app.ui.home

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.catalog.ComposeHomeFeed
import com.youtroc.core.domain.catalog.SeguirViendoShelfSource
import com.youtroc.core.domain.playback.GetContinueWatching
import com.youtroc.data.extraction.innertube.ShortsShelfSource
import com.youtroc.data.extraction.innertube.assembleFullHomeShelfSources
import com.youtroc.data.extraction.innertube.homeShelfSources
import com.youtroc.data.persistence.DataStoreWatchProgressStore
import com.youtroc.feature.catalog.HomeViewModel
import java.util.Locale

/**
 * Composition-root factory for [HomeViewModel]: the ONLY place
 * [homeShelfSources]' concrete `:data:extraction` adapters (plus
 * [SeguirViendoShelfSource]/[ShortsShelfSource]) are wired into the domain
 * [ComposeHomeFeed] use case — mirrors
 * [com.youtroc.app.ui.search.searchViewModelFactory]'s own-first decorator
 * wiring, the same manual-wiring convention already used by
 * [com.youtroc.app.ui.player.playbackViewModelFactory] /
 * [com.youtroc.app.ui.player.PlayerViewModel.factory]. No DI framework.
 *
 * Injects the device's region as a plain region-code string (`gl`,
 * REQ-HF1's regionalization seam): every adapter behind [homeShelfSources] is
 * pure JVM and has no Android `Context`, so none of them can read this
 * itself — `:app` is the only layer that can. `:app` passes a plain
 * `String`, never a NewPipe type — the NewPipe SDK (including
 * `ContentCountry`) must stay confined to `:data:extraction`.
 *
 * [context] is new here (S3 carry-over): [SeguirViendoShelfSource] needs a
 * local [DataStoreWatchProgressStore], which — like [DataStoreWatchProgressStore]
 * everywhere else in this codebase (see `PlaybackRoute`) — can only be built
 * from an Android `Context`, so this factory widens to accept one. The final
 * 9-source order is [assembleFullHomeShelfSources]' job, not this function's —
 * see its KDoc for the design rev3 ordering this resolves.
 */
fun homeViewModelFactory(context: Context) = viewModelFactory {
    initializer {
        val regionCode = Locale.getDefault().country
        val watchProgressStore = DataStoreWatchProgressStore(context)
        val sources = assembleFullHomeShelfSources(
            thematicSources = homeShelfSources(regionCode),
            seguirViendo = SeguirViendoShelfSource(GetContinueWatching(watchProgressStore)),
            shorts = ShortsShelfSource(regionCode = regionCode),
        )
        HomeViewModel(ComposeHomeFeed(sources))
    }
}
