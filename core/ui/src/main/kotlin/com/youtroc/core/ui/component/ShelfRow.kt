package com.youtroc.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.youtroc.core.ui.theme.YouTrocDimens

/**
 * Molecule: a titled horizontal shelf of video cards. The [LazyRow] is a
 * `focusGroup` so D-pad up/down jumps between shelves cleanly; `contentPadding`
 * keeps the focused card and its glow off the overscan edge.
 */
@Composable
fun ShelfRow(
    title: String,
    videos: List<VideoCardUi>,
    onVideoClick: (VideoCardUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.focusGroup()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                start = YouTrocDimens.railContentStart,
                bottom = 12.dp,
            ),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(YouTrocDimens.cardSpacing),
            contentPadding = PaddingValues(
                start = YouTrocDimens.railContentStart,
                end = YouTrocDimens.overscanHorizontal,
            ),
        ) {
            items(videos, key = { it.id }) { video ->
                TvVideoCard(
                    title = video.title,
                    subtitle = video.subtitle,
                    onClick = { onVideoClick(video) },
                )
            }
        }
    }
}
