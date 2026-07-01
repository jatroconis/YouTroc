package com.youtroc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.youtroc.app.ui.HomeShell
import com.youtroc.core.ui.theme.YouTrocTheme

/**
 * Fase 1 entry point: hosts the Compose for TV UI. The Hito 0 direct-playback path
 * lives on in git history and its domain/extraction/Media3 building blocks stay
 * intact — playback returns as a dedicated player destination in a later slice.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTrocTheme {
                HomeShell()
            }
        }
    }
}
