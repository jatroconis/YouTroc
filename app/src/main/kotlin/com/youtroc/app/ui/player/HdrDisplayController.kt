package com.youtroc.app.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Display
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.youtroc.core.domain.stream.HdrFormat

/**
 * REQ-H5 (load-bearing): opts the hosting [Activity]'s [Window] into HDR
 * display mode for the lifetime of a `hdr`-keyed composition, restoring the
 * default color mode on dispose. `Window`/`ActivityInfo`/`Display` imports
 * live ONLY in this file (`:app`-only, hexagon boundary per design Fork 5)
 * -- `:feature:playback` and `:data:player` must never import them.
 *
 * Manifest-independent: this seam reads the domain [HdrFormat] intent
 * threaded from `Stream.hdr`/`PlayableStreams.hdr`, not anything derived
 * from the DASH manifest (design-gate #4512 -- `MpdBuilder` carries zero
 * color signal either way).
 */
@Composable
fun HdrDisplayController(hdr: HdrFormat) {
    val context = LocalContext.current
    DisposableEffect(hdr) {
        val window = (context as? Activity)?.window
        if (window != null && shouldRequestHdr(hdr, window.isDisplayHdrCapable())) {
            window.setColorMode(ActivityInfo.COLOR_MODE_HDR)
        }
        onDispose {
            window?.setColorMode(ActivityInfo.COLOR_MODE_DEFAULT)
        }
    }
}

/**
 * The SDR-safety invariant as a pure boolean: request HDR display mode iff
 * the selected stream's [HdrFormat] is HDR (HDR10/HLG) AND the display
 * confirms HDR capability. Absent or unconfirmed capability MUST be treated
 * as SDR (never switches color mode).
 */
internal fun shouldRequestHdr(hdr: HdrFormat, isDisplayHdrCapable: Boolean): Boolean =
    hdr.isHdr && isDisplayHdrCapable

/**
 * [Display.isHdr] requires API 28+; `minSdk` here is 26. Below API 28 the
 * display's HDR capability cannot be confirmed, so this treats those devices
 * as non-HDR-capable -- consistent with the SDR-safety invariant ("absent
 * signal MUST be treated as SDR"). `Window.setColorMode`/
 * `ActivityInfo.COLOR_MODE_HDR` themselves are API 26+, fine at minSdk.
 */
private fun Window.isDisplayHdrCapable(): Boolean {
    if (Build.VERSION.SDK_INT < 28) return false
    return decorView.display?.isHdr == true
}
