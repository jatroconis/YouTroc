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
 * ANDROID_VR client identity for [InnerTubeStreamProvider]'s `player`
 * request -- kept SEPARATE from [INNERTUBE_CLIENT_NAME]/[INNERTUBE_CLIENT_VERSION]
 * (WEB) as a deliberate regression guard: the search/detail adapters must
 * never start sending an ANDROID_VR identity, and this adapter must never
 * fall back to a stale WEB one. Spike-confirmed values (2026-07-02); a stale
 * [ANDROID_VR_CLIENT_VERSION] is masked at runtime by [FallbackStreamProvider][com.youtroc.data.extraction.stream.FallbackStreamProvider].
 */
internal const val ANDROID_VR_CLIENT_NAME = "ANDROID_VR"
internal const val ANDROID_VR_CLIENT_VERSION = "1.60.19"
internal const val ANDROID_VR_DEVICE_MAKE = "Oculus"
internal const val ANDROID_VR_DEVICE_MODEL = "Quest 3"
internal const val ANDROID_VR_SDK_VERSION = 32

/**
 * Pure digit-strip tail shared by every InnerTube view-count parser: strips
 * everything but digits and parses the remainder, or null on
 * absent/unparseable input -- never throws. Callers own the two-branch
 * simpleText/runs extraction (e.g. [ViewCountText.parsedViewCount]); this
 * function only does the final digits -> Long? step.
 */
internal fun String?.toCountOrNull(): Long? = this?.filter { it.isDigit() }?.toLongOrNull()
