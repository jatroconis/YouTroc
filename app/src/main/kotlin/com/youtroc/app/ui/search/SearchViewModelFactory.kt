package com.youtroc.app.ui.search

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.search.SearchVideos
import com.youtroc.data.extraction.search.NewPipeVideoSearch
import com.youtroc.feature.search.SearchViewModel
import java.util.Locale

/**
 * Composition-root factory for [SearchViewModel]: the ONLY place
 * [NewPipeVideoSearch] is wired into the domain [SearchVideos] use case —
 * mirrors [com.youtroc.app.ui.home.homeViewModelFactory]. No DI framework,
 * same manual-wiring convention as the rest of `:app`.
 *
 * Injects the device's region as a plain region-code string (mirrors
 * `homeViewModelFactory`'s [Locale.getDefault].country`): [NewPipeVideoSearch]
 * is pure JVM and has no Android `Context`, so it cannot read this itself —
 * `:app` is the only layer that can. `:app` passes a plain `String`, never a
 * NewPipe type — the NewPipe SDK (including `ContentCountry`) must stay
 * confined to `:data:extraction`.
 */
fun searchViewModelFactory() = viewModelFactory {
    initializer {
        SearchViewModel(
            SearchVideos(
                NewPipeVideoSearch(
                    regionCode = Locale.getDefault().country,
                ),
            ),
        )
    }
}
