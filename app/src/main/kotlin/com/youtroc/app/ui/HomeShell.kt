package com.youtroc.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.youtroc.core.ui.component.ShelfRow
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.component.YouTrocLogo
import com.youtroc.core.ui.theme.AlmostBlack
import com.youtroc.core.ui.theme.ElevatedSurface
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocDimens

/**
 * Home shell: a custom nav rail beside the content, laid out as a plain [Row] so
 * D-pad focus moves freely left/right between the rail and the cards (no modal
 * drawer that traps focus). Focus starts on the first card; BACK from the rail
 * returns to the content instead of leaving the app.
 */
@Composable
fun HomeShell() {
    val shelves = remember { fakeShelves() }
    var selectedIndex by remember { mutableIntStateOf(1) } // Home
    val firstCardFocus = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { firstCardFocus.requestFocus() }
    }
    // While the rail holds focus, BACK returns to the content rather than exiting.
    BackHandler(enabled = railFocused) {
        runCatching { firstCardFocus.requestFocus() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavRail(
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                modifier = Modifier.onFocusChanged { railFocused = it.hasFocus },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                    verticalArrangement = Arrangement.spacedBy(YouTrocDimens.shelfSpacing),
                    contentPadding = PaddingValues(bottom = YouTrocDimens.overscanVertical),
                ) {
                    itemsIndexed(shelves, key = { _, shelf -> shelf.title }) { index, shelf ->
                        ShelfRow(
                            title = shelf.title,
                            videos = shelf.videos,
                            onVideoClick = { /* player destination lands in a later slice */ },
                            firstCardFocusRequester = if (index == 0) firstCardFocus else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRail(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(YouTrocDimens.railWidth)
            .padding(vertical = YouTrocDimens.overscanVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Cuenta",
            tint = OnDarkMuted,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(16.dp))

        RailIcon(Icons.Default.Search, "Buscar", selectedIndex == 0) { onSelect(0) }
        RailIcon(Icons.Default.Home, "Inicio", selectedIndex == 1) { onSelect(1) }

        Spacer(Modifier.weight(1f))

        RailIcon(Icons.Default.Settings, "Ajustes", selectedIndex == 2) { onSelect(2) }
    }
}

@Composable
private fun RailIcon(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
        modifier = Modifier.size(44.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
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
