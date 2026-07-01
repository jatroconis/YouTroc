package com.youtroc.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.YouTrocRed

/**
 * The YouTroc brandmark, rendered in Compose (resolution-independent, dark-UI ready):
 * the red badge with a white "T" — the YouTube play-button silhouette, YouTroc's twist —
 * followed by the wordmark.
 */
@Composable
fun YouTrocLogo(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 30.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(height)
                .aspectRatio(1.42f)
                .clip(RoundedCornerShape(percent = 32))
                .background(YouTrocRed),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "T",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (height.value * 0.62f).sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "YouTroc",
            color = OnDark,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
