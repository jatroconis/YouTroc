package com.youtroc.core.domain.stream

/**
 * The high-dynamic-range intent carried by a [Stream], mapped from the
 * source's own colorInfo (own-engine PLAYER response, or NewPipe's
 * fallback). This is INTENT only -- it never touches Media3's own
 * container-derived `ColorInfo` (decode), it exists purely to let `:app`
 * decide when to request [android.view.Window.setColorMode].
 */
enum class HdrFormat {
    SDR,
    HDR10,
    HLG,
    ;

    /** True for anything other than [SDR]. */
    val isHdr: Boolean get() = this != SDR
}
