package com.youtroc.app.ui.player

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.detail.GetVideoDetail
import com.youtroc.data.extraction.detail.FallbackVideoDetail
import com.youtroc.data.extraction.detail.NewPipeVideoDetail
import com.youtroc.data.extraction.innertube.InnerTubeVideoDetail
import com.youtroc.feature.playback.upnext.UpNextViewModel
import java.util.Locale

/**
 * Composition-root factory for [UpNextViewModel]: the ONLY place
 * [FallbackVideoDetail] is wired into the domain [GetVideoDetail] use case
 * for the in-player Info+Up-Next panel (REQ-U3, design D5) — mirrors the
 * deleted `:feature:video` module's `detailViewModelFactory` and
 * [com.youtroc.app.ui.search.searchViewModelFactory]. Own-engine
 * [InnerTubeVideoDetail] is tried first; [NewPipeVideoDetail] is the kept
 * fallback (second strangler-fig slice of the own InnerTube engine).
 *
 * Injects the device's region as a plain region-code string (mirrors
 * `searchViewModelFactory`'s `Locale.getDefault().country`): neither adapter
 * is Android-aware, so it cannot read this itself — `:app` is the only layer
 * that can. `:app` passes a plain `String`, never a NewPipe type — the
 * NewPipe SDK (including `ContentCountry`) must stay confined to
 * `:data:extraction`.
 */
fun upNextViewModelFactory(videoId: String) = viewModelFactory {
    initializer {
        UpNextViewModel(
            videoId = videoId,
            getVideoDetail = GetVideoDetail(
                FallbackVideoDetail(
                    primary = InnerTubeVideoDetail(regionCode = Locale.getDefault().country),
                    fallback = NewPipeVideoDetail(regionCode = Locale.getDefault().country),
                ),
            ),
        )
    }
}
