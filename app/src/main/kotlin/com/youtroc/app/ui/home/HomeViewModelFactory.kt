package com.youtroc.app.ui.home

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.catalog.GetHomeFeed
import com.youtroc.data.extraction.catalog.NewPipeVideoCatalog
import com.youtroc.feature.catalog.HomeViewModel
import java.util.Locale

/**
 * Composition-root factory for [HomeViewModel]: the ONLY place
 * [NewPipeVideoCatalog] is wired into the domain [GetHomeFeed] use case — the
 * same manual-wiring convention already used by
 * [com.youtroc.app.ui.player.playbackViewModelFactory] /
 * [com.youtroc.app.ui.player.PlayerViewModel.factory]. No DI framework.
 *
 * Injects the device's region as a plain region-code string (`gl`,
 * RF-CAT-06's minor seam): [NewPipeVideoCatalog] is pure JVM and has no
 * Android `Context`, so it cannot read this itself — `:app` is the only
 * layer that can. `:app` passes a plain `String`, never a NewPipe type — the
 * NewPipe SDK (including `ContentCountry`) must stay confined to
 * `:data:extraction`.
 */
fun homeViewModelFactory() = viewModelFactory {
    initializer {
        HomeViewModel(
            GetHomeFeed(
                NewPipeVideoCatalog(
                    regionCode = Locale.getDefault().country,
                ),
            ),
        )
    }
}
