package com.youtroc.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.youtroc.core.ui.theme.ElevatedSurface
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.YouTrocDimens
import com.youtroc.core.ui.theme.YouTrocRed

private val ShortsCardShape = RoundedCornerShape(12.dp)

/**
 * Atom: a portrait 9:16 Shorts card (recon #4604). Unlike [TvVideoCard], the
 * WHOLE card (not just the thumbnail) is the focusable surface, and the title
 * is overlaid at the BOTTOM of the thumbnail itself over a dark gradient scrim
 * (max 2 lines) — there is no channel/views/age line beneath it, since Shorts
 * carry no reliable per-channel data from their source shape (see
 * `ShortsShelfSource`'s own KDoc). Focus tokens (scale/border/glow) mirror
 * [TvVideoCard]'s exactly.
 */
@Composable
fun ShortsCard(
    video: ShortsCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ShortsCardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ElevatedSurface,
            focusedContainerColor = ElevatedSurface,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, YouTrocRed),
                shape = ShortsCardShape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = YouTrocRed, elevation = 12.dp),
        ),
        modifier = modifier
            .width(YouTrocDimens.shortsCardWidth)
            .aspectRatio(9f / 16f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(0.0f to Color.Transparent, 1.0f to Color.Black.copy(alpha = 0.8f)),
                    )
                    .padding(8.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
