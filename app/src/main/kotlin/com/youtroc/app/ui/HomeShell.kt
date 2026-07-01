package com.youtroc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.ui.component.ShelfRow
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.theme.YouTrocDimens

/**
 * Fase 1, slice 1 shell: the collapsible nav rail (Search / Home / Settings) plus a
 * vertical stack of shelves. Data is fake for now — the container/ViewModel and real
 * InnerTube catalog land in the next slice; the presentational pieces stay untouched.
 */
@Composable
fun HomeShell() {
    val shelves = remember { fakeShelves() }
    var selectedIndex by remember { mutableIntStateOf(1) } // Home

    ModalNavigationDrawer(
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RAIL_ITEMS.forEach { item ->
                    NavigationDrawerItem(
                        selected = selectedIndex == item.index,
                        onClick = { selectedIndex = item.index },
                        leadingContent = {
                            Icon(imageVector = item.icon, contentDescription = item.label)
                        },
                    ) {
                        Text(item.label)
                    }
                }
            }
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(YouTrocDimens.shelfSpacing),
                contentPadding = PaddingValues(vertical = YouTrocDimens.overscanVertical),
            ) {
                items(shelves, key = { it.title }) { shelf ->
                    ShelfRow(
                        title = shelf.title,
                        videos = shelf.videos,
                        onVideoClick = { /* navega al player en un slice posterior */ },
                    )
                }
            }
        }
    }
}

private data class RailItem(val label: String, val icon: ImageVector, val index: Int)

private val RAIL_ITEMS = listOf(
    RailItem("Search", Icons.Default.Search, 0),
    RailItem("Home", Icons.Default.Home, 1),
    RailItem("Settings", Icons.Default.Settings, 2),
)

private data class Shelf(val title: String, val videos: List<VideoCardUi>)

private fun fakeShelves(): List<Shelf> {
    fun row(prefix: String) = (1..12).map { n ->
        VideoCardUi(
            id = "$prefix-$n",
            title = "$prefix · video $n",
            subtitle = "Canal $n · ${n}M vistas",
        )
    }
    return listOf(
        Shelf("Tendencia", row("trend")),
        Shelf("Música", row("music")),
        Shelf("Gaming", row("game")),
    )
}
