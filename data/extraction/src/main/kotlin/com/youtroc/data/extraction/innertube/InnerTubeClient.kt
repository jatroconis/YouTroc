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
 * [ANDROID_VR_CLIENT_VERSION] is masked at runtime by [LadderStreamProvider][com.youtroc.data.extraction.stream.LadderStreamProvider]
 * (android_vr version bump DEFERRED -- owner decision, see design D4).
 */
internal const val ANDROID_VR_CLIENT_NAME = "ANDROID_VR"
internal const val ANDROID_VR_CLIENT_VERSION = "1.60.19"
internal const val ANDROID_VR_DEVICE_MAKE = "Oculus"
internal const val ANDROID_VR_DEVICE_MODEL = "Quest 3"
internal const val ANDROID_VR_SDK_VERSION = 32

/**
 * ios fallback-rung client identity for [InnerTubeStreamProvider]'s `player`
 * request -- spike-confirmed, live-verified values (spike/multiclient-racing,
 * #4536). ios is NEVER primary and NEVER raced against android_vr (owner
 * decision, design D1): reachable only when the ANDROID_VR rung above it in
 * [LadderStreamProvider][com.youtroc.data.extraction.stream.LadderStreamProvider]
 * did not succeed. [IOS_USER_AGENT]'s embedded version MUST equal
 * [IOS_CLIENT_VERSION] -- the only hard rule; the os/locale substring is
 * cosmetic and freely tunable.
 */
internal const val IOS_CLIENT_NAME = "IOS"
internal const val IOS_CLIENT_VERSION = "21.02.3"
internal const val IOS_DEVICE_MAKE = "Apple"
internal const val IOS_DEVICE_MODEL = "iPhone16,2"
internal const val IOS_USER_AGENT =
    "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X; es_ES)"

/**
 * Adapter-internal identity descriptor threaded through [buildPlayerRequest]/
 * [buildRequest] -- selects which client identity (and, for ios, which
 * `User-Agent` HTTP header) an [InnerTubeStreamProvider] instance requests
 * as. Never leaves this package: [InnerTubeStreamProvider]'s public surface
 * is its [InnerTubeStreamProvider.androidVr]/[InnerTubeStreamProvider.ios]
 * factories, not this type -- a public constructor cannot take an
 * internal-typed parameter, so the factories keep [PlayerClientContext]
 * fully internal (no leak of client-name/UA mechanics to `:app`).
 * [userAgent] is `null` for [ANDROID_VR] -- an HTTP header, not a body
 * field, attached only when set (android_vr keeps OkHttp's default UA
 * unchanged).
 */
internal data class PlayerClientContext(
    val clientName: String,
    val clientVersion: String,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val userAgent: String? = null,
) {
    companion object {
        val ANDROID_VR = PlayerClientContext(
            clientName = ANDROID_VR_CLIENT_NAME,
            clientVersion = ANDROID_VR_CLIENT_VERSION,
            deviceMake = ANDROID_VR_DEVICE_MAKE,
            deviceModel = ANDROID_VR_DEVICE_MODEL,
            androidSdkVersion = ANDROID_VR_SDK_VERSION,
            userAgent = null,
        )
        val IOS = PlayerClientContext(
            clientName = IOS_CLIENT_NAME,
            clientVersion = IOS_CLIENT_VERSION,
            deviceMake = IOS_DEVICE_MAKE,
            deviceModel = IOS_DEVICE_MODEL,
            androidSdkVersion = null,
            userAgent = IOS_USER_AGENT,
        )
    }
}

/**
 * Pure digit-strip tail shared by every InnerTube view-count parser: strips
 * everything but digits and parses the remainder, or null on
 * absent/unparseable input -- never throws. Callers own the two-branch
 * simpleText/runs extraction (e.g. [ViewCountText.parsedViewCount]); this
 * function only does the final digits -> Long? step.
 */
internal fun String?.toCountOrNull(): Long? = this?.filter { it.isDigit() }?.toLongOrNull()
