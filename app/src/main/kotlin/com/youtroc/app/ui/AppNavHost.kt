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
 * the player route (URL-encoded) so the player can show it immediately,
 * before extraction resolves.
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
            )
        }
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
