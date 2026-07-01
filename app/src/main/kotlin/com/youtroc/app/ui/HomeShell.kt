package com.youtroc.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.ui.component.ShelfRow
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.component.YouTrocLogo
import com.youtroc.core.ui.theme.AlmostBlack
import com.youtroc.core.ui.theme.ElevatedSurface
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocDimens

private val RailExpandedWidth = 240.dp

/**
 * Home shell. The nav rail is an OVERLAY over the content (content stays put), and
 * it expands on focus to reveal labels. Crucially, only the 44dp icon is focusable;
 * the label overflows beside it as decoration, so a D-pad RIGHT always finds the
 * content to the right — the rail expands like YouTube's without trapping focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeShell(
    onVideoClick: (VideoCardUi) -> Unit = {},
) {
    val shelves = remember { fakeShelves() }
    var selectedIndex by remember { mutableIntStateOf(1) } // Home
    val contentFocus = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { contentFocus.requestFocus() }
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

                LazyColumn(
                    // Content focus group: up/down stays in the card grid, and the rail's
                    // RIGHT re-enters here landing on a VISIBLE card — never an off-screen,
                    // recycled one (which was the "stuck in the rail" bug).
                    modifier = Modifier
                        .focusRequester(contentFocus)
                        .focusProperties {
                            // Keep vertical navigation inside the grid; only LEFT reaches
                            // the rail. Prevents the rail auto-opening on DOWN at the bottom.
                            @Suppress("DEPRECATION")
                            exit = { direction ->
                                if (direction == FocusDirection.Up || direction == FocusDirection.Down) {
                                    FocusRequester.Cancel
                                } else {
                                    FocusRequester.Default
                                }
                            }
                        }
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(YouTrocDimens.shelfSpacing),
                    contentPadding = PaddingValues(bottom = YouTrocDimens.overscanVertical),
                ) {
                    itemsIndexed(
                        items = shelves,
                        key = { _, shelf -> shelf.title },
                        contentType = { _, _ -> "shelf" },
                    ) { _, shelf ->
                        ShelfRow(
                            title = shelf.title,
                            videos = shelf.videos,
                            onVideoClick = onVideoClick,
                        )
                    }
                }
            }

            // Rail overlay — expands on focus.
            NavRail(
                railFocused = railFocused,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
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

            RailItem(Icons.Default.Search, "Buscar", selectedIndex == 0, railFocused, contentFocus) { onSelect(0) }
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

private data class Shelf(val title: String, val videos: List<VideoCardUi>)

private fun v(id: String, title: String, channel: String, meta: String) =
    VideoCardUi(
        id = id,
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hq720.jpg",
        title = title,
        channel = channel,
        meta = meta,
    )

private fun fakeShelves(): List<Shelf> = listOf(
    Shelf(
        "Tendencia",
        listOf(
            v("dQw4w9WgXcQ", "Rick Astley - Never Gonna Give You Up", "Rick Astley", "1.6 B vistas · hace 15 a."),
            v("9bZkp7q19f0", "PSY - GANGNAM STYLE (강남스타일)", "officialpsy", "5.4 B vistas · hace 13 a."),
            v("kJQP7kiw5Fk", "Luis Fonsi - Despacito ft. Daddy Yankee", "Luis Fonsi", "8.6 B vistas · hace 8 a."),
            v("OPf0YbXqDm0", "Mark Ronson - Uptown Funk ft. Bruno Mars", "Mark Ronson", "5.3 B vistas · hace 10 a."),
            v("JGwWNGJdvx8", "Ed Sheeran - Shape of You", "Ed Sheeran", "6.3 B vistas · hace 9 a."),
        ),
    ),
    Shelf(
        "Música",
        listOf(
            v("RgKAFK5djSk", "Wiz Khalifa - See You Again ft. Charlie Puth", "Wiz Khalifa", "6.4 B vistas · hace 10 a."),
            v("fJ9rUzIMcZQ", "Queen – Bohemian Rhapsody", "Queen Official", "1.8 B vistas · hace 16 a."),
            v("60ItHLz5WEA", "Alan Walker - Faded", "Alan Walker", "3.6 B vistas · hace 9 a."),
            v("CevxZvSJLk8", "Katy Perry - Roar", "Katy Perry", "4.1 B vistas · hace 12 a."),
            v("YQHsXMglC9A", "Adele - Hello", "Adele", "3.4 B vistas · hace 10 a."),
        ),
    ),
    Shelf(
        "Populares",
        listOf(
            v("hT_nvWreIhg", "OneRepublic - Counting Stars", "OneRepublic", "4.4 B vistas · hace 12 a."),
            v("09R8_2nJtjg", "Maroon 5 - Sugar", "Maroon 5", "4.0 B vistas · hace 10 a."),
            v("e-ORhEE9VVg", "Avicii - Wake Me Up", "Avicii", "2.3 B vistas · hace 12 a."),
            v("pRpeEdMmmQ0", "Shakira - Waka Waka (Esto es África)", "Shakira", "3.9 B vistas · hace 15 a."),
            v("450p7goxZqg", "John Legend - All of Me", "John Legend", "2.9 B vistas · hace 12 a."),
        ),
    ),
)
