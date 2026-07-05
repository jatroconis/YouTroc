package com.youtroc.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

/**
 * The exact display width [SpriteTile] renders at -- PUBLIC so a caller
 * positioning/clamping [SpriteTile] on screen (e.g. `:feature:playback`'s
 * scrub-preview thumbnail) knows its real on-screen size without needing
 * this file's internals. Paired with [spriteTileHeightFor] for the height,
 * which depends on each tile's own aspect ratio.
 */
val SpriteTileWidth = 200.dp

/** The display height [SpriteTile] renders at for a [tileWidthPx] x [tileHeightPx] tile, preserving its aspect ratio at [SpriteTileWidth]. */
fun spriteTileHeightFor(tileWidthPx: Int, tileHeightPx: Int): Dp = SpriteTileWidth * tileHeightPx / tileWidthPx

/**
 * Renders one crop of a storyboard sprite sheet (REQ-SB6, design D3): [url]
 * is the full sheet, ([srcXPx], [srcYPx]) is the tile's top-left pixel
 * offset within it, and ([tileWidthPx], [tileHeightPx]) is the tile's pixel
 * size. Every pixel argument is used ONLY as a RATIO (source-pixel to
 * display-dp scale), never as an absolute density-dependent unit -- so
 * PRIMITIVES ONLY, no `:core:domain` import: `:core:ui` carries no
 * dependency edge to that module, and this composable is reusable by any
 * future sprite-cropping need, not just storyboards.
 *
 * The full sheet is measured via [AsyncImagePainter.intrinsicSize] (its
 * real decoded pixel size, available once [AsyncImagePainter.State.Success])
 * scaled by `DisplayTileWidth / [tileWidthPx]`, then offset so the desired
 * tile lands at this [Box]'s origin, clipped there -- a pure offset + clip,
 * never a second decode. While loading or on error, nothing renders (a
 * transparent box, no placeholder/error glyph) -- design D3/D7: a
 * stale-`sigh` 403 or a slow network silently shows no thumbnail.
 *
 * [prefetchUrls] (design D4, gate F5) are enqueued fire-and-forget through
 * the shared [SingletonImageLoader] inside a [LaunchedEffect] keyed on [url]
 * -- never inline in composition -- so neighboring sprite pages are already
 * Coil-cached by the time a scrub tick crosses a sprite boundary.
 */
@Composable
fun SpriteTile(
    url: String,
    srcXPx: Int,
    srcYPx: Int,
    tileWidthPx: Int,
    tileHeightPx: Int,
    prefetchUrls: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val painter = rememberAsyncImagePainter(model = url)
    val state by painter.state.collectAsState()

    LaunchedEffect(url, prefetchUrls) {
        val loader = SingletonImageLoader.get(context)
        prefetchUrls.forEach { prefetchUrl ->
            loader.enqueue(ImageRequest.Builder(context).data(prefetchUrl).build())
        }
    }

    val displayTileHeight = spriteTileHeightFor(tileWidthPx, tileHeightPx)

    Box(modifier = modifier.size(SpriteTileWidth, displayTileHeight).clipToBounds()) {
        val intrinsic = painter.intrinsicSize
        if (state is AsyncImagePainter.State.Success && intrinsic.isSpecified) {
            val scale = SpriteTileWidth.value / tileWidthPx // dp per source pixel
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                // Plain `size()` COERCES to the parent Box's (tile-sized)
                // constraints, squashing the whole multi-frame sheet into the
                // tile window (observed on-device). `requiredSize` measures the
                // sheet at its true scaled size regardless of the parent, and
                // `wrapContentSize(TopStart, unbounded)` keeps the oversized
                // child anchored at the box origin instead of centered — the
                // offset then slides the desired tile into the clip window.
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize((intrinsic.width * scale).dp, (intrinsic.height * scale).dp)
                    .offset(x = (-srcXPx * scale).dp, y = (-srcYPx * scale).dp),
            )
        }
    }
}
