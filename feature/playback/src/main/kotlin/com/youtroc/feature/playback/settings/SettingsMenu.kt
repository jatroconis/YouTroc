package com.youtroc.feature.playback.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted

/**
 * "Ajustes" modal menu (REQ-S1): the ⚙ pill's new root, rendered instead of
 * the "Calidad" sub-panel directly. Rows come from the pure
 * [SettingsMenuItems.build] — this composable only renders them and reports
 * a pick back via [onOpenQuality]; `PlayerOverlay` owns the
 * `PlayerMenu` state and routes to the "Calidad" sub-panel after the
 * callback runs.
 *
 * Focus containment (REQ-S5, same pattern proven by `QualityMenu`): the row
 * [Column] is a SINGLE `focusGroup()` container with [focusProperties]
 * cancelling every exit direction, so UP-past-first / DOWN-past-last stays
 * inside the menu — the only way out is BACK, handled by `PlayerOverlay`'s
 * `BackHandler`, not focus search.
 *
 * [ajustesFocusRequester] is attached to this CONTAINER — never a lazy row
 * item — and is requested from `PlayerOverlay` one frame after the menu
 * actually composes, the same deferred-focus pattern used across this
 * feature. [calidadRowFocusRequester] targets the "Calidad" row itself: a
 * forward-safe restore hook for when Ajustes gains more rows (gate S3) — a
 * no-op today since Ajustes has exactly one row.
 *
 * Integration-only: Compose focus/D-pad behavior is validated on the TCL
 * 55C6K, the same convention as `PlayerOverlay`/`QualityMenu`.
 * [SettingsMenuItems] (the pure row model) is the piece that is unit-tested.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsMenu(
    activeQualityLabel: String?,
    ajustesFocusRequester: FocusRequester,
    calidadRowFocusRequester: FocusRequester,
    onOpenQuality: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(activeQualityLabel) {
        SettingsMenuItems.build(activeQualityLabel)
    }

    Column(
        modifier = modifier
            .width(280.dp)
            .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(16.dp)
            .focusRequester(ajustesFocusRequester)
            .focusProperties {
                // Never let focus escape the menu via D-pad in ANY
                // direction — the only exit is BACK (same as QualityMenu).
                @Suppress("DEPRECATION")
                exit = { FocusRequester.Cancel }
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Ajustes", color = OnDark, style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            SettingsMenuRowItem(
                row = row,
                focusRequester = calidadRowFocusRequester,
                onClick = { if (row.action == SettingsAction.OpenQuality) onOpenQuality() },
            )
        }
    }
}

@Composable
private fun SettingsMenuRowItem(
    row: SettingsMenuRow,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = OnDark,
            contentColor = OnDarkMuted,
            focusedContentColor = Color.Black,
        ),
        modifier = modifier.focusRequester(focusRequester),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.label)
            row.value?.let { Text(text = it, color = OnDarkMuted, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
