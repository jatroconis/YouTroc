package com.youtroc.app.ui.detail

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.detail.GetVideoDetail
import com.youtroc.data.extraction.detail.NewPipeVideoDetail
import com.youtroc.feature.video.DetailViewModel
import java.util.Locale

/**
 * Composition-root factory for [DetailViewModel]: the ONLY place
 * [NewPipeVideoDetail] is wired into the domain [GetVideoDetail] use case —
 * mirrors [com.youtroc.app.ui.search.searchViewModelFactory].
 *
 * Injects the device's region as a plain region-code string (mirrors
 * `searchViewModelFactory`'s [Locale.getDefault].country`): [NewPipeVideoDetail]
 * is pure JVM and has no Android `Context`, so it cannot read this itself —
 * `:app` is the only layer that can. `:app` passes a plain `String`, never a
 * NewPipe type — the NewPipe SDK (including `ContentCountry`) must stay
 * confined to `:data:extraction`.
 */
fun detailViewModelFactory(videoId: String) = viewModelFactory {
    initializer {
        DetailViewModel(
            videoId = videoId,
            getVideoDetail = GetVideoDetail(
                NewPipeVideoDetail(regionCode = Locale.getDefault().country),
            ),
        )
    }
}
