package com.youtroc.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.youtroc.core.ui.component.ShelfRow
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.component.YouTrocLogo
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocDimens

/**
 * Home shell: the collapsible nav rail (avatar + Search / Home / Settings, expanding
 * on focus) over a header (YouTroc brandmark) and a stack of shelves with real
 * thumbnails. Data is still fabricated from real video ids; the catalog ViewModel
 * replaces the fake source next, without touching these presentational pieces.
 */
@Composable
fun HomeShell() {
    val shelves = remember { fakeShelves() }
    var selectedIndex by remember { mutableIntStateOf(1) } // Home
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            NavRail(
                open = drawerValue == DrawerValue.Open,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                modifier = Modifier.onFocusChanged {
                    drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                },
            )
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                    items(shelves, key = { it.title }) { shelf ->
                        ShelfRow(
                            title = shelf.title,
                            videos = shelf.videos,
                            onVideoClick = { /* player destination lands in a later slice */ },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationDrawerScope.NavRail(
    open: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileAvatar(open = open)
        Spacer(Modifier.height(12.dp))

        NavigationDrawerItem(
            selected = selectedIndex == 0,
            onClick = { onSelect(0) },
            leadingContent = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
        ) { Text("Buscar") }

        NavigationDrawerItem(
            selected = selectedIndex == 1,
            onClick = { onSelect(1) },
            leadingContent = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
        ) { Text("Inicio") }

        Spacer(Modifier.weight(1f))

        NavigationDrawerItem(
            selected = selectedIndex == 2,
            onClick = { onSelect(2) },
            leadingContent = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
        ) { Text("Ajustes") }
    }
}

@Composable
private fun ProfileAvatar(open: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Cuenta",
            tint = OnDarkMuted,
            modifier = Modifier.size(36.dp),
        )
        if (open) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Invitado",
                color = OnDark,
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
