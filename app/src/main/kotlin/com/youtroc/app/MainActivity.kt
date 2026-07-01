package com.youtroc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.youtroc.app.ui.AppNavHost
import com.youtroc.core.ui.theme.YouTrocTheme

/**
 * App entry point: hosts the Compose for TV navigation graph (Home -> Player).
 * Playback is reconnected as a dedicated destination that drives the same
 * domain/extraction/Media3 stack proven in Hito 0.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTrocTheme {
                AppNavHost()
            }
        }
    }
}
