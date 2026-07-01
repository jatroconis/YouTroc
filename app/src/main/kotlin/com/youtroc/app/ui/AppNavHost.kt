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

/**
 * The app's navigation graph. Two destinations for now: the Home shell and a
 * full-screen player. The title rides along the route (URL-encoded) so the player
 * can show it immediately, before extraction resolves.
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
                state = state,
                onBack = { navController.popBackStack() },
                onRetry = viewModel::resolve,
            )
        }
    }
}

private const val ROUTE_HOME = "home"
