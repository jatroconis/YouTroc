package com.youtroc.feature.playback.quality

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.domain.playback.VideoQuality
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocRed

/**
 * "Calidad" modal menu (REQ-Q2): overlays the player controls once the
 * settings pill (⚙) is pressed. Rows come from the pure
 * [QualityMenuItems.build] — this composable only renders them and reports
 * a pick back via [onSelectQuality]/[onSelectAuto]; [PlayerOverlay] owns
 * `menuVisible` and closes the menu after either callback runs.
 *
 * Focus containment (design gate #4431 MAJOR-5, the #1 risk for REQ-Q7): the
 * row [Column] is a SINGLE `focusGroup()` container with [focusProperties]
 * cancelling every exit direction, so UP-past-first / DOWN-past-last stays
 * inside the menu instead of escaping to the transport/pills rows composed
 * behind it — the same container-level containment already proven by
 * `HomeContent`/`TransportRow`/`PillsRow` in this codebase (item-level focus
 * anchors were the mistake flagged by gate reviews #4399/#4419). The only way
 * out is BACK, handled by `PlayerOverlay`'s nested `BackHandler`, not focus
 * search.
 *
 * [menuFocusRequester] is attached to this CONTAINER — never a lazy row
 * item — and is requested from `PlayerOverlay` one frame after the menu
 * actually composes (`LaunchedEffect(menuVisible)`, the same deferred-focus
 * pattern used across this feature), never inline during the click/key event
 * that opened it.
 *
 * Integration-only: Compose focus/D-pad behavior is validated on the TCL
 * 55C6K, the same convention as `PlayerOverlay`/`Media3MediaPlayer`.
 * [QualityMenuItems] (the pure row model) is the piece that is unit-tested.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QualityMenu(
    availableQualities: List<VideoQuality>,
    activeQuality: VideoQuality?,
    menuFocusRequester: FocusRequester,
    onSelectQuality: (VideoQuality) -> Unit,
    onSelectAuto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(availableQualities, activeQuality) {
        QualityMenuItems.build(availableQualities, activeQuality)
    }

    Column(
        modifier = modifier
            .width(280.dp)
            // gate S4: relative cap (fraction of screen height) instead of a
            // fixed dp, so the panel never runs off-top on denser TV
            // screens — bounds the top, the anchor `bottom` padding at the
            // call site bounds the bottom against the control band (REQ-S2).
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.45).dp)
            .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(16.dp)
            .focusRequester(menuFocusRequester)
            .focusProperties {
                // MAJOR-5 (#4431): never let focus escape the menu via D-pad
                // in ANY direction — the only exit is BACK.
                @Suppress("DEPRECATION")
                exit = { FocusRequester.Cancel }
            }
            .focusGroup()
            // gate R2/REQ-S2: scroll host is the focusGroup container itself
            // (LAST modifier) so D-pad UP/DOWN moves focus row-to-row and
            // Compose's auto `bringIntoView` scrolls the focused row through
            // the clipped list — same pattern as `DetailContent.kt`.
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Calidad", color = OnDark, style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            QualityMenuRowItem(
                row = row,
                onClick = { if (row.quality == null) onSelectAuto() else onSelectQuality(row.quality) },
            )
        }
    }
}

@Composable
private fun QualityMenuRowItem(row: QualityMenuRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = OnDark,
            contentColor = OnDarkMuted,
            focusedContentColor = Color.Black,
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.isActive) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = YouTrocRed)
            } else {
                Box(modifier = Modifier.width(24.dp))
            }
            Text(text = row.label)
        }
    }
}
