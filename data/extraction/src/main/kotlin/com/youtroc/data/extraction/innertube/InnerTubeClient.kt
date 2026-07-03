package com.youtroc.data.extraction.innertube

/**
 * Shared WEB client identity for every InnerTube adapter in this package
 * ([InnerTubeVideoSearch], [InnerTubeVideoDetail]) -- a single maintenance
 * knob: refresh [INNERTUBE_CLIENT_VERSION] here when the opt-in live tests
 * start failing, instead of hunting per-adapter duplicates (a stale version
 * is masked at runtime by the respective `Fallback*` decorator).
 */
internal const val INNERTUBE_CLIENT_NAME = "WEB"
internal const val INNERTUBE_CLIENT_VERSION = "2.20240814.00.00"
internal const val INNERTUBE_HL = "es"

/**
 * Pure digit-strip tail shared by every InnerTube view-count parser: strips
 * everything but digits and parses the remainder, or null on
 * absent/unparseable input -- never throws. Callers own the two-branch
 * simpleText/runs extraction (e.g. [ViewCountText.parsedViewCount]); this
 * function only does the final digits -> Long? step.
 */
internal fun String?.toCountOrNull(): Long? = this?.filter { it.isDigit() }?.toLongOrNull()
