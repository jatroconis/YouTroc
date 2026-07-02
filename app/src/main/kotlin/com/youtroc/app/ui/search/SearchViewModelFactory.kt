package com.youtroc.app.ui.search

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.search.SearchVideos
import com.youtroc.data.extraction.innertube.InnerTubeVideoSearch
import com.youtroc.data.extraction.search.FallbackVideoSearch
import com.youtroc.data.extraction.search.NewPipeVideoSearch
import com.youtroc.feature.search.SearchViewModel
import java.util.Locale

/**
 * Composition-root factory for [SearchViewModel]: the ONLY place
 * [InnerTubeVideoSearch]/[NewPipeVideoSearch] are wired into the domain
 * [SearchVideos] use case — mirrors [com.youtroc.app.ui.home.homeViewModelFactory].
 * No DI framework, same manual-wiring convention as the rest of `:app`.
 *
 * [SearchVideos] is backed by a [FallbackVideoSearch]: the own-engine
 * [InnerTubeVideoSearch] is tried first, and [NewPipeVideoSearch] only runs
 * when it fails (first strangler-fig slice of the own InnerTube engine).
 *
 * Injects the device's region as a plain region-code string (mirrors
 * `homeViewModelFactory`'s `Locale.getDefault().country`): both adapters are
 * pure JVM with no Android `Context`, so they cannot read this themselves —
 * `:app` is the only layer that can. `:app` passes a plain `String`, never a
 * NewPipe type — the NewPipe SDK (including `ContentCountry`) must stay
 * confined to `:data:extraction`.
 */
fun searchViewModelFactory() = viewModelFactory {
    initializer {
        val regionCode = Locale.getDefault().country
        SearchViewModel(
            SearchVideos(
                FallbackVideoSearch(
                    primary = InnerTubeVideoSearch(regionCode = regionCode),
                    fallback = NewPipeVideoSearch(regionCode = regionCode),
                ),
            ),
        )
    }
}
