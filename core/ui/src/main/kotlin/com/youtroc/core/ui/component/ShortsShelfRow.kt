package com.youtroc.core.ui.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.youtroc.core.ui.theme.YouTrocDimens

/**
 * Molecule: the Shorts shelf — a titled horizontal row of portrait
 * [ShortsCard]s (recon #4604, ~5.5 visible per row). Mirrors [ShelfRow]'s
 * structure exactly (same `focusGroup`/contentPadding/first-card-focus
 * conventions) but renders [ShortsCard] instead of [TvVideoCard]. The header
 * text is whatever [title] the caller passes — "Shorts", no "y más" suffix
 * (recon #4604), already the mapping `:feature:catalog` produces.
 */
@Composable
fun ShortsShelfRow(
    title: String,
    videos: List<ShortsCardUi>,
    onVideoClick: (ShortsCardUi) -> Unit,
    modifier: Modifier = Modifier,
    firstCardFocusRequester: FocusRequester? = null,
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
            itemsIndexed(
                items = videos,
                key = { _, v -> v.id },
                contentType = { _, _ -> "shortsCard" },
            ) { index, video ->
                ShortsCard(
                    video = video,
                    onClick = { onVideoClick(video) },
                    modifier = if (index == 0 && firstCardFocusRequester != null) {
                        Modifier.focusRequester(firstCardFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
