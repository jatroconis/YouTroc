package com.youtroc.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.app.ui.home.homeViewModelFactory
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.component.YouTrocLogo
import com.youtroc.core.ui.theme.AlmostBlack
import com.youtroc.core.ui.theme.ElevatedSurface
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocDimens
import com.youtroc.feature.catalog.HomeContent
import com.youtroc.feature.catalog.HomeFocusTarget
import com.youtroc.feature.catalog.HomeViewModel
import com.youtroc.feature.catalog.focusTarget

private val RailExpandedWidth = 240.dp

/**
 * Home shell. The nav rail is an OVERLAY over the content (content stays put), and
 * it expands on focus to reveal labels. Crucially, only the 44dp icon is focusable;
 * the label overflows beside it as decoration, so a D-pad RIGHT always finds the
 * content to the right — the rail expands like YouTube's without trapping focus.
 *
 * The trending content itself is delegated to [HomeContent] (`:feature:catalog`);
 * this shell owns the [HomeViewModel] instance, the rail chrome, and the shared
 * [contentFocus] target the rail's RIGHT re-enters on.
 */
@Composable
fun HomeShell(
    onVideoClick: (VideoCardUi) -> Unit = {},
    onShortsClick: (startId: String, shelfItems: List<VideoCardUi>) -> Unit = { _, _ -> },
    onOpenSearch: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = homeViewModelFactory(context))
    val state by vm.state.collectAsState()

    var selectedIndex by remember { mutableIntStateOf(1) } // Home
    val contentFocus = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }

    // Re-request focus on every focus-CATEGORY change (NONE -> MESSAGE/CONTENT,
    // or MESSAGE <-> CONTENT), not on every raw state change (N1). A boolean
    // latch would collapse Offline/Error/Empty/Content into one bucket: a
    // Loading -> Offline -> late-Content sequence would never re-fire once
    // "hasFocusableContent" was already true, stranding focus on a Retry button
    // that already left composition. Keying on the 3-way category instead means
    // Content -> Content (a later shelf appending) is a no-op (same category,
    // preserves the original intent of not stealing focus while browsing), but
    // a terminal-to-Content flip (MESSAGE -> CONTENT) DOES re-fire and moves
    // focus off the vanished Retry.
    val focusTarget = state.focusTarget()
    LaunchedEffect(focusTarget) {
        if (focusTarget != HomeFocusTarget.NONE) {
            runCatching { contentFocus.requestFocus() }
        }
    }
    BackHandler(enabled = railFocused) {
        runCatching { contentFocus.requestFocus() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Content — inset so it clears the collapsed rail; it never reflows.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = YouTrocDimens.railWidth),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = YouTrocDimens.railContentStart,
                            end = YouTrocDimens.overscanHorizontal,
                            top = YouTrocDimens.overscanVertical,
                            bottom = 14.dp,
                        ),
                ) {
                    YouTrocLogo(modifier = Modifier.align(Alignment.CenterEnd))
                }

                HomeContent(
                    state = state,
                    onVideoClick = onVideoClick,
                    onShortsClick = onShortsClick,
                    onRetry = vm::load,
                    contentFocusRequester = contentFocus,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            // Rail overlay — expands on focus.
            NavRail(
                railFocused = railFocused,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                onOpenSearch = onOpenSearch,
                contentFocus = contentFocus,
                modifier = Modifier
                    .fillMaxHeight()
                    .onFocusChanged { railFocused = it.hasFocus },
            )
        }
    }
}

@Composable
private fun NavRail(
    railFocused: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val panelWidth by animateDpAsState(
        targetValue = if (railFocused) RailExpandedWidth else YouTrocDimens.railWidth,
        label = "railPanelWidth",
    )
    val background = if (railFocused) {
        Modifier.background(
            Brush.horizontalGradient(
                0.0f to AlmostBlack,
                0.6f to AlmostBlack,
                1.0f to Color.Transparent,
            ),
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .width(panelWidth)
            .then(background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = YouTrocDimens.overscanVertical, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Cuenta",
                    tint = OnDarkMuted,
                    modifier = Modifier.size(36.dp),
                )
                if (railFocused) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Invitado",
                        color = OnDark,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            RailItem(Icons.Default.Search, "Buscar", selectedIndex == 0, railFocused, contentFocus) {
                onSelect(0)
                onOpenSearch()
            }
            RailItem(Icons.Default.Home, "Inicio", selectedIndex == 1, railFocused, contentFocus) { onSelect(1) }

            Spacer(Modifier.weight(1f))

            RailItem(Icons.Default.Settings, "Ajustes", selectedIndex == 2, railFocused, contentFocus) { onSelect(2) }
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    rightFocus: FocusRequester,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // ONLY this 44dp surface is focusable, and RIGHT is wired explicitly to the
        // content so the overlay geometry can never trap focus in the rail.
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (selected) ElevatedSurface else Color.Transparent,
                focusedContainerColor = OnDark,
                contentColor = OnDark,
                focusedContentColor = AlmostBlack,
            ),
            modifier = Modifier
                .size(44.dp)
                .focusProperties { right = rightFocus },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label)
            }
        }
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                color = if (selected) OnDark else OnDarkMuted,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
        }
    }
}

