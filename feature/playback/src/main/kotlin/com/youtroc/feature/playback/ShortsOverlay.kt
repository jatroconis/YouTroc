package com.youtroc.feature.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted

/**
 * Shorts player chrome (recon #4604): a RIGHT metadata column only — title,
 * `@channel`, and a next-up hint — vertically centered beside the vertical
 * video panel. Mirrors [PlayerOverlay]'s "pure Compose chrome, knows nothing
 * about Media3" split, but is intentionally far simpler: there is no
 * interactive control here at all. Suscribirse/like/comments are OMITTED
 * (logged-out, ad-free — recon #4604's adaptation notes), so nothing in this
 * composable is focusable; DOWN/UP paging is handled by the composition
 * root's own key listener (`:app`'s `ShortsPlayerRoute`, F4), not here.
 */
@Composable
fun ShortsOverlay(
    title: String,
    channel: String,
    nextTitle: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp)
                .width(280.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = OnDark,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@$channel",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nextTitle != null) {
                Text(
                    text = "▶ $nextTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
