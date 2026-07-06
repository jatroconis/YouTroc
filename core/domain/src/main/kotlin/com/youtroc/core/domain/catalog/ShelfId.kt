package com.youtroc.core.domain.catalog

/**
 * Identifies which Home shelf a [Shelf] represents. [ComposeHomeFeed] stamps
 * this onto every produced [Shelf] (overriding whatever placeholder a
 * [ShelfSource]'s underlying adapter constructed one with) so the feature
 * edge can key stable UI identity and Spanish title mapping on something
 * more durable than a source-provided display title.
 */
enum class ShelfId {
    SEGUIR_VIENDO,
    SHORTS,
    MUSICA,
    VIDEOJUEGOS,
    NOTICIAS,
    DEPORTES,
    CINE,
    EN_VIVO,
    TENDENCIAS,
}
