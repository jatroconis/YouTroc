package com.youtroc.core.domain.catalog

import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fans out over [sources] concurrently, one coroutine per [ShelfSource], each
 * bounded by its own [ShelfSource.timeoutMs] ceiling (B1 residual: a
 * JVM-level backstop over cancellable suspension points, NOT the production
 * bound -- the real one is the derived OkHttp `callTimeout` baked into each
 * adapter's client). Emits a [HomeFeedSnapshot] after every slot update, in
 * the FIXED [sources] declaration order regardless of which source resolves
 * first (REQ-HF5) -- a Mutex-guarded slot array (M2) avoids a torn read when
 * two sources complete on the same dispatcher tick.
 *
 * The Tendencias LEAD (the single source whose [ShelfSource.id] is
 * [ShelfId.TENDENCIAS]) additionally drives [HomeFeedSnapshot.leadOutcome]
 * from its BOUNDED attempt only -- its [ShelfSource.loadFallback] UNBOUNDED
 * late leg (N3, owner decision #4599) may append the shelf late but never
 * mutates [HomeFeedSnapshot.leadOutcome] once set. Every OTHER source's
 * default [ShelfSource.loadFallback] is a no-op (`null`), so this same late
 * leg branch is harmless dead weight for them.
 *
 * Never throws across the port boundary: a source whose [ShelfSource.load]
 * throws directly (violating its own contract) is still caught and mapped to
 * [CatalogResult.Error] here (REQ-HF2 isolation holds even against a
 * misbehaving source) -- cooperative cancellation is always rethrown.
 *
 * The returned [kotlinx.coroutines.flow.Flow] completes only once EVERY
 * launched leg finishes (including the unbounded late lead, worst-case ~30s)
 * -- callers must keep collecting until then to observe a late fill (F2/F6).
 */
class ComposeHomeFeed(
    private val sources: List<ShelfSource>,
) {
    operator fun invoke() = channelFlow {
        val mutex = Mutex()
        val slots = arrayOfNulls<Shelf>(sources.size)
        var leadOutcome: CatalogResult? = null

        fun snapshot() = HomeFeedSnapshot(shelves = slots.filterNotNull(), leadOutcome = leadOutcome)

        sources.forEachIndexed { index, source ->
            launch {
                val bounded = boundedLoad(source)
                val shelf = bounded.toNonEmptyShelfOrNull(source.id)
                mutex.withLock {
                    slots[index] = shelf
                    if (source.id == ShelfId.TENDENCIAS) leadOutcome = bounded
                    send(snapshot())
                }
                if (shelf == null) {
                    val late = lateLoad(source)
                    val lateShelf = late.toNonEmptyShelfOrNull(source.id)
                    if (lateShelf != null) {
                        mutex.withLock {
                            slots[index] = lateShelf
                            send(snapshot())
                        }
                    }
                }
            }
        }
    }
}

private suspend fun boundedLoad(source: ShelfSource): CatalogResult? =
    try {
        withTimeoutOrNull(source.timeoutMs) { source.load() }
    } catch (e: CancellationException) {
        throw e // never swallow cooperative cancellation
    } catch (e: Exception) {
        CatalogResult.Error(e)
    }

private suspend fun lateLoad(source: ShelfSource): CatalogResult? =
    try {
        source.loadFallback()
    } catch (e: CancellationException) {
        throw e // never swallow cooperative cancellation
    } catch (e: Exception) {
        null
    }

/** A [CatalogResult.Success] shelf with >=1 video, re-tagged with the owning source's [ShelfId]. */
private fun CatalogResult?.toNonEmptyShelfOrNull(id: ShelfId): Shelf? =
    (this as? CatalogResult.Success)
        ?.shelves?.firstOrNull()
        ?.takeIf { it.videos.isNotEmpty() }
        ?.copy(id = id)
