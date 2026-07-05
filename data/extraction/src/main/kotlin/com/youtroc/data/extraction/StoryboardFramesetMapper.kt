package com.youtroc.data.extraction

import com.youtroc.core.domain.stream.StoryboardLevel
import com.youtroc.core.domain.stream.StoryboardSpec
import org.schabi.newpipe.extractor.stream.Frameset

/**
 * Maps NewPipeExtractor's [Frameset] (terminal fallback ladder rung, REQ-SB1)
 * onto the SAME domain [StoryboardSpec] the InnerTube parser produces -- a
 * single level, since one [Frameset] already models one full resolution.
 * `null` (gate F1, same contract as [com.youtroc.data.extraction.innertube.toStoryboardSpecOrNull])
 * whenever the mapped [StoryboardSpec] has no usable [StoryboardSpec.previewLevel] --
 * defensive: NewPipeExtractor's [Frameset] does not model an L0-style recap
 * sheet, but the null-at-source contract stays uniform across BOTH ladder rungs.
 */
internal fun Frameset.toStoryboardSpecOrNull(): StoryboardSpec? =
    storyboardSpecFrom(
        urls = urls,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        totalCount = totalCount,
        durationPerFrame = durationPerFrame,
        framesPerPageX = framesPerPageX,
        framesPerPageY = framesPerPageY,
    ).takeIf { it.previewLevel() != null }

/**
 * Field-for-field mapping from [Frameset]'s primitive shape into a
 * single-level [StoryboardSpec] (REQ-SB1, design D8c): takes PRIMITIVES, not
 * a real [Frameset], so a fake can drive it directly without constructing
 * NewPipeExtractor's type -- [frameWidth]/[frameHeight] -> tile size,
 * [totalCount] -> totalFrames, [durationPerFrame] -> intervalMs,
 * [framesPerPageX]/[framesPerPageY] -> columns/rows, [urls] -> pageUrls
 * verbatim (NewPipe already resolves these to real, fetchable URLs -- no
 * `$L`/`$N`/`$M` template to substitute, unlike the InnerTube parser).
 */
internal fun storyboardSpecFrom(
    urls: List<String>,
    frameWidth: Int,
    frameHeight: Int,
    totalCount: Int,
    durationPerFrame: Int,
    framesPerPageX: Int,
    framesPerPageY: Int,
): StoryboardSpec = StoryboardSpec(
    listOf(
        StoryboardLevel(
            tileWidthPx = frameWidth,
            tileHeightPx = frameHeight,
            columns = framesPerPageX,
            rows = framesPerPageY,
            intervalMs = durationPerFrame.toLong(),
            totalFrames = totalCount,
            pageUrls = urls,
        ),
    ),
)
