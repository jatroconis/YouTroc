package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.stream.StoryboardLevel
import com.youtroc.core.domain.stream.StoryboardSpec
import kotlin.math.ceil

/** Every level segment has exactly this many `#`-separated fields on the wire. */
private const val LEVEL_FIELD_COUNT = 8

/**
 * Parses InnerTube's `storyboards.playerStoryboardSpecRenderer.spec` string
 * into a domain [StoryboardSpec] (REQ-SB1/SB2, design D1): pure `String ->
 * StoryboardSpec?`, no video duration needed -- `totalFrameCount` is spec
 * field[2] directly (yt-dlp/NewPipe's own approach), unlike
 * [PlayerResponse.resolveDurationMs] elsewhere in this package.
 *
 * Wire shape: `baseUrl|L0#w#h#count#cols#rows#intervalMs#name#sigh|L1#...`.
 * [baseUrl] carries `$L`/`$N` placeholders; each level segment's `name`
 * field is itself literally e.g. `M$M` -- introducing a NEW `$M` placeholder
 * the base URL never had (gate F4). Substitution order is therefore
 * load-bearing: `$L` (this level's 0-based index, in ORIGINAL segment order
 * -- design D1, verified against yt-dlp's reverse math) -> `$N` (the name
 * field, which is where `$M` comes from) -> `$M` (this sheet's 0-based page
 * index). Any other order leaves a literal `$M` in every sprite URL -- every
 * fetch would 404. `sigh` is appended verbatim as `&sigh=`, never substituted.
 *
 * Malformed level segments ([LEVEL_FIELD_COUNT]-field mismatch, or any
 * non-numeric numeric field) are dropped via `mapIndexedNotNull` (REQ-SB2)
 * -- never fatal, and the ORIGINAL index is preserved even across a skipped
 * segment, since `$L` must match the level's position in the untouched wire
 * order. Returns `null` (gate F1) whenever the resulting [StoryboardSpec]
 * has no usable [StoryboardSpec.previewLevel] (blank spec, only an L0 recap
 * sheet, or every level malformed) -- callers gate purely on `storyboard !=
 * null`, never on a nullable `previewLevel()`.
 */
internal fun String.toStoryboardSpecOrNull(): StoryboardSpec? {
    val parts = split("|")
    val baseUrl = parts.firstOrNull() ?: return null
    val levels = parts.drop(1).mapIndexedNotNull { levelIndex, segment ->
        segment.toStoryboardLevelOrNull(baseUrl, levelIndex)
    }

    val spec = StoryboardSpec(levels)
    return spec.takeIf { it.previewLevel() != null }
}

private fun String.toStoryboardLevelOrNull(baseUrl: String, levelIndex: Int): StoryboardLevel? {
    val fields = split("#")
    if (fields.size != LEVEL_FIELD_COUNT) return null

    val tileWidthPx = fields[0].toIntOrNull() ?: return null
    val tileHeightPx = fields[1].toIntOrNull() ?: return null
    val totalFrames = fields[2].toIntOrNull() ?: return null
    val columns = fields[3].toIntOrNull() ?: return null
    val rows = fields[4].toIntOrNull() ?: return null
    val intervalMs = fields[5].toLongOrNull() ?: return null
    val name = fields[6]
    val sigh = fields[7]
    if (columns <= 0 || rows <= 0) return null

    val framesPerSprite = columns * rows
    val pageCount = ceil(totalFrames.toDouble() / framesPerSprite).toInt().coerceAtLeast(1)
    val pageUrls = (0 until pageCount).map { pageIndex ->
        baseUrl
            .replace("\$L", levelIndex.toString())
            .replace("\$N", name)
            .replace("\$M", pageIndex.toString()) + "&sigh=$sigh"
    }

    return StoryboardLevel(
        tileWidthPx = tileWidthPx,
        tileHeightPx = tileHeightPx,
        columns = columns,
        rows = rows,
        intervalMs = intervalMs,
        totalFrames = totalFrames,
        pageUrls = pageUrls,
    )
}
