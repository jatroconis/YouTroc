package com.youtroc.app.ui.home

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.catalog.GetHomeFeed
import com.youtroc.data.extraction.catalog.FallbackVideoCatalog
import com.youtroc.data.extraction.catalog.NewPipeVideoCatalog
import com.youtroc.data.extraction.innertube.InnerTubeVideoCatalog
import com.youtroc.feature.catalog.HomeViewModel
import java.util.Locale

/**
 * Composition-root factory for [HomeViewModel]: the ONLY place
 * [InnerTubeVideoCatalog]/[NewPipeVideoCatalog] are wired into the domain
 * [GetHomeFeed] use case — mirrors
 * [com.youtroc.app.ui.search.searchViewModelFactory]'s own-first decorator
 * wiring, the same manual-wiring convention already used by
 * [com.youtroc.app.ui.player.playbackViewModelFactory] /
 * [com.youtroc.app.ui.player.PlayerViewModel.factory]. No DI framework.
 *
 * [GetHomeFeed] is backed by a [FallbackVideoCatalog]: the own-engine
 * [InnerTubeVideoCatalog] (a "Popular en {region}" shelf via seed-query
 * search) is tried first, and [NewPipeVideoCatalog] (the Trending kiosk)
 * only runs when it fails (strangler-fig, mirrors the search slice).
 *
 * Injects the device's region as a plain region-code string (`gl`,
 * RF-CAT-06's minor seam): both adapters are pure JVM and have no Android
 * `Context`, so neither can read this itself — `:app` is the only layer that
 * can. `:app` passes a plain `String`, never a NewPipe type — the NewPipe SDK
 * (including `ContentCountry`) must stay confined to `:data:extraction`.
 */
fun homeViewModelFactory() = viewModelFactory {
    initializer {
        val regionCode = Locale.getDefault().country
        HomeViewModel(
            GetHomeFeed(
                FallbackVideoCatalog(
                    primary = InnerTubeVideoCatalog(regionCode = regionCode),
                    fallback = NewPipeVideoCatalog(regionCode = regionCode),
                ),
            ),
        )
    }
}
