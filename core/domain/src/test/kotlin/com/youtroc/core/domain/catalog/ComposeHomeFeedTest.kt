package com.youtroc.core.domain.catalog

import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic, virtual-time verification of [ComposeHomeFeed]'s fan-out:
 * per-source isolation (REQ-HF2), the per-source timeout ceiling (B1
 * residual -- a JVM-level backstop over CANCELLABLE fakes, not the
 * production bound), fixed shelf ordering regardless of completion order
 * (REQ-HF5), the unbounded late-lead leg (N3), REQ-HF13's total-thematic-
 * failure floor, and Mutex-guarded same-tick writes (M2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeHomeFeedTest {

    private fun testVideo(idSuffix: String): Video = Video(
        id = VideoId("id-$idSuffix"),
        title = "title-$idSuffix",
        channelName = "channel",
        thumbnailUrl = "https://example.com/$idSuffix.jpg",
        viewCount = null,
        publishedText = null,
    )

    private fun shelfWith(id: ShelfId, videoCount: Int = 1): Shelf =
        Shelf(id = id, title = id.name, videos = List(videoCount) { testVideo("${id.name}-$it") })

    private class FakeShelfSource(
        override val id: ShelfId,
        override val timeoutMs: Long = 10_000L,
        override val displayTitle: String? = null,
        private val loadFn: suspend () -> CatalogResult,
        private val loadFallbackFn: (suspend () -> CatalogResult?)? = null,
    ) : ShelfSource {
        override suspend fun load(): CatalogResult = loadFn()
        override suspend fun loadFallback(): CatalogResult? = loadFallbackFn?.invoke()
    }

    @Test
    fun `a throwing source is isolated -- the others still land`() = runTest {
        val sources = listOf(
            FakeShelfSource(ShelfId.TENDENCIAS, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.TENDENCIAS))) }),
            FakeShelfSource(ShelfId.MUSICA, loadFn = { throw IllegalStateException("boom") }),
            FakeShelfSource(ShelfId.VIDEOJUEGOS, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.VIDEOJUEGOS))) }),
            FakeShelfSource(ShelfId.NOTICIAS, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.NOTICIAS))) }),
            FakeShelfSource(ShelfId.DEPORTES, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.DEPORTES))) }),
            FakeShelfSource(ShelfId.CINE, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.CINE))) }),
        )

        val last = ComposeHomeFeed(sources)().last()

        assertEquals(
            setOf(ShelfId.TENDENCIAS, ShelfId.VIDEOJUEGOS, ShelfId.NOTICIAS, ShelfId.DEPORTES, ShelfId.CINE),
            last.shelves.map { it.id }.toSet(),
        )
    }

    @Test
    fun `a shelf source resolving to zero videos is excluded, not rendered empty`() = runTest {
        val sources = listOf(
            FakeShelfSource(ShelfId.TENDENCIAS, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.TENDENCIAS))) }),
            FakeShelfSource(
                ShelfId.MUSICA,
                loadFn = { CatalogResult.Success(listOf(Shelf(id = ShelfId.MUSICA, title = "Música", videos = emptyList()))) },
            ),
        )

        val last = ComposeHomeFeed(sources)().last()

        assertEquals(listOf(ShelfId.TENDENCIAS), last.shelves.map { it.id })
    }

    @Test
    fun `the per-source ceiling omits a slow source at its own timeoutMs, not the fake's full delay`() = runTest {
        val sources = listOf(
            FakeShelfSource(ShelfId.TENDENCIAS, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.TENDENCIAS))) }),
            FakeShelfSource(
                ShelfId.MUSICA,
                timeoutMs = 1_000L,
                loadFn = { delay(5_000); CatalogResult.Success(listOf(shelfWith(ShelfId.MUSICA))) },
            ),
        )

        val last = ComposeHomeFeed(sources)().last()

        assertEquals(listOf(ShelfId.TENDENCIAS), last.shelves.map { it.id })
        assertTrue(
            currentTime in 1_000..4_999,
            "expected completion around the 1000ms ceiling, not the fake's 5000ms delay; took ${currentTime}ms",
        )
    }

    @Test
    fun `shelves land in fixed source-declaration order regardless of completion order`() = runTest {
        val sources = listOf(
            FakeShelfSource(ShelfId.TENDENCIAS, loadFn = { delay(30); CatalogResult.Success(listOf(shelfWith(ShelfId.TENDENCIAS))) }),
            FakeShelfSource(ShelfId.MUSICA, loadFn = { delay(10); CatalogResult.Success(listOf(shelfWith(ShelfId.MUSICA))) }),
            FakeShelfSource(ShelfId.VIDEOJUEGOS, loadFn = { delay(20); CatalogResult.Success(listOf(shelfWith(ShelfId.VIDEOJUEGOS))) }),
        )

        val last = ComposeHomeFeed(sources)().last()

        // Completion order was MUSICA(10ms) < VIDEOJUEGOS(20ms) < TENDENCIAS(30ms), yet the
        // final shelves list must stay in the sources' DECLARATION order (REQ-HF5).
        assertEquals(listOf(ShelfId.TENDENCIAS, ShelfId.MUSICA, ShelfId.VIDEOJUEGOS), last.shelves.map { it.id })
    }

    @Test
    fun `a late-arriving lead fallback flips an earlier Terminal-eligible snapshot to Content`() = runTest {
        val sources = listOf(
            FakeShelfSource(
                ShelfId.TENDENCIAS,
                timeoutMs = 100L,
                loadFn = { CatalogResult.Empty },
                loadFallbackFn = { delay(500); CatalogResult.Success(listOf(shelfWith(ShelfId.TENDENCIAS))) },
            ),
            FakeShelfSource(ShelfId.MUSICA, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.MUSICA))) }),
        )

        val snapshots = mutableListOf<HomeFeedSnapshot>()
        ComposeHomeFeed(sources)().collect { snapshots.add(it) }

        val beforeLateFill = snapshots.first { it.leadOutcome == CatalogResult.Empty }
        assertFalse(ShelfId.TENDENCIAS in beforeLateFill.shelves.map { it.id })

        val finalSnapshot = snapshots.last()
        assertEquals(listOf(ShelfId.TENDENCIAS, ShelfId.MUSICA), finalSnapshot.shelves.map { it.id })
    }

    @Test
    fun `REQ-HF13 -- all thematic sources failing while Tendencias succeeds yields exactly one shelf`() = runTest {
        val sources = listOf(
            FakeShelfSource(ShelfId.TENDENCIAS, loadFn = { CatalogResult.Success(listOf(shelfWith(ShelfId.TENDENCIAS))) }),
            FakeShelfSource(ShelfId.MUSICA, loadFn = { CatalogResult.Error(IllegalStateException("boom")) }),
            FakeShelfSource(ShelfId.VIDEOJUEGOS, loadFn = { CatalogResult.Empty }),
            FakeShelfSource(ShelfId.NOTICIAS, loadFn = { CatalogResult.Offline }),
            FakeShelfSource(ShelfId.DEPORTES, loadFn = { CatalogResult.Error(IllegalStateException("boom")) }),
            FakeShelfSource(ShelfId.CINE, loadFn = { CatalogResult.Empty }),
            FakeShelfSource(ShelfId.EN_VIVO, loadFn = { CatalogResult.Offline }),
        )

        val last = ComposeHomeFeed(sources)().last()

        assertEquals(listOf(ShelfId.TENDENCIAS), last.shelves.map { it.id })
    }

    @Test
    fun `two sources completing on the same tick both land -- no torn slot write`() = runTest {
        val sources = listOf(
            FakeShelfSource(ShelfId.TENDENCIAS, loadFn = { delay(5); CatalogResult.Success(listOf(shelfWith(ShelfId.TENDENCIAS))) }),
            FakeShelfSource(ShelfId.MUSICA, loadFn = { delay(5); CatalogResult.Success(listOf(shelfWith(ShelfId.MUSICA))) }),
        )

        val last = ComposeHomeFeed(sources)().last()

        assertEquals(listOf(ShelfId.TENDENCIAS, ShelfId.MUSICA), last.shelves.map { it.id })
    }
}
