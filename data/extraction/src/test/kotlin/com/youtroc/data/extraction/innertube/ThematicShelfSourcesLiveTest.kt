package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.ShelfId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that every non-Tendencias production
 * shelf source resolves real, non-empty content for region "CO". Opt-in
 * only (YOUTROC_LIVE=1) -- mirrors [InnerTubeVideoCatalogLiveTest]; a seed
 * returning nothing degrades gracefully at runtime (REQ-HF2) but should be
 * investigated (B1 residual, seed-quality).
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class ThematicShelfSourcesLiveTest {

    private fun productionSources() = listOf(
        SearchQueryShelfSource(id = ShelfId.MUSICA, displayTitle = "Música", query = "música", regionCode = "CO"),
        SearchQueryShelfSource(id = ShelfId.VIDEOJUEGOS, displayTitle = "Videojuegos", query = "videojuegos", regionCode = "CO"),
        SearchQueryShelfSource(id = ShelfId.NOTICIAS, displayTitle = "Noticias", query = "noticias", regionCode = "CO"),
        SearchQueryShelfSource(id = ShelfId.DEPORTES, displayTitle = "Deportes", query = "deportes", regionCode = "CO"),
        SearchQueryShelfSource(id = ShelfId.CINE, displayTitle = "Cine y tráilers", query = "cine y tráilers", regionCode = "CO"),
        EnVivoShelfSource(regionCode = "CO"),
    )

    @Test
    fun `every non-Tendencias production source resolves real content for region CO`() = runTest {
        productionSources().forEach { source ->
            val result = source.load()

            val success = assertIs<CatalogResult.Success>(result, "expected Success for ${source.id}, got $result")
            val shelf = success.shelves.single()
            assertTrue(shelf.videos.isNotEmpty(), "expected >=1 video for ${source.id}")
            assertTrue(shelf.videos.all { it.id.value.isNotBlank() }, "every video id must be non-blank for ${source.id}")
            assertTrue(
                shelf.videos.all { it.thumbnailUrl.startsWith("http") },
                "every thumbnail must be http(s) for ${source.id}",
            )
        }
    }
}
