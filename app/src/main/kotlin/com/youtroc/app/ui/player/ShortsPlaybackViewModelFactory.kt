package com.youtroc.app.ui.player

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.playback.GetPlayableStreams
import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.data.player.Media3MediaPlayer
import com.youtroc.feature.playback.ShortsPlaybackViewModel
import com.youtroc.feature.playback.ShortsQueueItem

/**
 * Composition-root factory for [ShortsPlaybackViewModel] — mirrors
 * [playbackViewModelFactory]'s M1 initializer exactly: the concrete
 * [Media3MediaPlayer] is built INSIDE [initializer] (main-thread-once, tied
 * to this NavBackStackEntry), never via a composition-scoped `remember`, for
 * the same Activity-recreation-survival reason [playbackViewModelFactory]'s
 * own KDoc documents. [streamProvider] is the SAME shared
 * `YouTrocApp.streamProvider` instance every other player entry point uses
 * (design D1) — this is not a fresh adapter chain per Short.
 */
fun shortsPlaybackViewModelFactory(
    context: Context,
    streamProvider: StreamProvider,
    items: List<ShortsQueueItem>,
    startIndex: Int,
) = viewModelFactory {
    initializer {
        ShortsPlaybackViewModel(
            player = Media3MediaPlayer(context),
            getPlayableStreams = GetPlayableStreams(streamProvider),
            items = items,
            startIndex = startIndex,
        )
    }
}
