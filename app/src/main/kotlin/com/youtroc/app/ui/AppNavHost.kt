package com.youtroc.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youtroc.app.ui.player.PlayerScreen
import com.youtroc.app.ui.player.PlayerViewModel
import com.youtroc.app.ui.search.SearchRoute

/**
 * The app's navigation graph. Three destinations: the Home shell, the
 * railless search screen, and a full-screen player. The title rides along
 * the player route (URL-encoded) so the screen can show it immediately,
 * before extraction resolves.
 *
 * GOLDEN RULE — match the original YouTube UX: a card confirm (Home, Search)
 * PLAYS the video directly (card→player), with NO detail-screen re-confirmation
 * gate. This overrides the earlier RF-CAT-04/RF-SRCH-03 "navega al detalle"
 * reading, which produced a non-YouTube pre-play gate the user rejected on-device.
 * The standalone `detail/{videoId}` route (and `:feature:video`) has been
 * deleted — its VideoDetail/related data is re-homed INTO the player as an
 * in-player "up next" panel (YouTube-style, `:feature:playback`'s `upnext`
 * package), not a blocking pre-play screen.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeShell(
                onVideoClick = { video ->
                    navController.navigate("player/${video.id}?title=${Uri.encode(video.title)}")
                },
                onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
            )
        }

        composable(ROUTE_SEARCH) {
            SearchRoute(
                onVideoClick = { video ->
                    navController.navigate("player/${video.id}?title=${Uri.encode(video.title)}")
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "player/{videoId}?title={title}",
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val videoId = entry.arguments?.getString("videoId").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty()
            val viewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModel.factory(videoId = videoId, title = title),
            )
            val state by viewModel.state.collectAsState()

            PlayerScreen(
                videoId = videoId,
                title = title,
                state = state,
                onBack = { navController.popBackStack() },
                onRetry = viewModel::resolve,
                // player-upnext REQ-U4: selecting an up-next item pushes a
                // FRESH `player/{id}` entry (same card->player pipeline as
                // Home/Search), replacing the active video; BACK returns to
                // THIS entry and resumes its saved position (REQ-12/13,
                // entry-scoped PlaybackViewModel — unchanged).
                onUpNextClick = { card ->
                    navController.navigate("player/${card.id}?title=${Uri.encode(card.title)}")
                },
            )
        }
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
