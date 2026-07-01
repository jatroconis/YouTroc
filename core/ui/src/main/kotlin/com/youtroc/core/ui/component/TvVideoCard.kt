package com.youtroc.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocDimens
import com.youtroc.core.ui.theme.YouTrocRed

private val CardShape = RoundedCornerShape(12.dp)

/**
 * Atom: a video card in the YouTube-for-TV shape. ONLY the 16:9 thumbnail is the
 * focusable surface — title, channel and metadata sit BELOW it, outside the card,
 * as plain text (this is the JetStream `StandardCardContainer` structure).
 *
 * Focus animation is delegated to the tv-material surface tokens (scale + red border
 * + red glow), which are GPU-cheap and never trigger recomposition.
 */
@Composable
fun TvVideoCard(
    video: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(YouTrocDimens.cardWidth)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CardShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = ElevatedSurface,
                focusedContainerColor = ElevatedSurface,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, YouTrocRed),
                    shape = CardShape,
                ),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = YouTrocRed, elevation = 12.dp),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            color = OnDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = video.channel,
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = video.meta,
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
