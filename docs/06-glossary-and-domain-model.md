# Glosario y Modelo de Dominio — YouTroc

> Propósito: fijar el vocabulario técnico del proyecto y el lenguaje ubicuo de `:core:domain`, de modo que puertos, adaptadores, features y conversaciones usen exactamente los mismos términos y significados.

YouTroc es un cliente de YouTube sin publicidad para Android TV, construido desde cero contra la API interna InnerTube. La ausencia de publicidad es **por construcción**: nunca ejecutamos el reproductor oficial de Google, así que simplemente ignoramos los campos `adPlacements`/`playerAds` que devuelve InnerTube. Este documento tiene dos partes: un **glosario técnico** (qué significa cada término que aparece en el proyecto) y el **modelo de dominio** (las entidades y value objects que viven en `:core:domain`, el módulo Kotlin puro del que dependen todos los demás).

---

## Cómo leer este documento

- **Parte 1 — Glosario técnico:** definiciones en español de los términos que se cruzan en extracción, reproducción, UI, build y sesión. Cada entrada dice *qué es* y *por qué importa en YouTroc*.
- **Parte 2 — Modelo de dominio:** el lenguaje ubicuo de `:core:domain`. Para cada entidad o value object: su tipo, propósito, campos principales e invariantes. Es lo que las features conocen; los adaptadores en `:data:*` lo traducen desde/hacia InnerTube, Media3, OAuth, etc.
- **Convención de identificadores:** los nombres técnicos (módulos `:core:domain`, puertos `StreamProvider`, tipos `PlayableStreams`, términos como InnerTube o DASH) se mantienen en inglés **exactamente** como en el código. La prosa explicativa está en español.
- **Decisiones confirmadas:** los detalles de producto que antes figuraban como `(Asunción — a confirmar)` quedaron **TODOS resueltos** en la fecha de confirmación. Cada entrada conserva ahora la decisión afirmada en línea y la lista completa se recopila al final, en *Decisiones confirmadas (resueltas en la fecha de confirmación)*.

---

## Parte 1 — Glosario técnico

### Extracción y núcleo anti-ads

Estos términos giran alrededor del puerto `StreamProvider` y del adaptador `:data:extraction`, que es el diferenciador y el **riesgo #1** del proyecto (motor de extracción propio).

| Término | Definición y relevancia en YouTroc |
| --- | --- |
| **InnerTube** | API interna y privada que usan las propias apps de YouTube (web, móvil, TV) para hablar con los servidores de Google. No es la YouTube Data API pública: no requiere API key de Google Cloud, pero sí emular un "cliente" (client name/version, contexto, cabeceras). Es la fuente de verdad de YouTroc: de aquí salen feeds, búsqueda, metadatos de video y, sobre todo, las URLs de streams reproducibles. Las peticiones se emiten con `hl=es` (español) y `gl`/región heredada del dispositivo. Construir nuestro propio extractor InnerTube detrás de `StreamProvider` es el objetivo de aprendizaje y el diferenciador del producto. |
| **PoToken** (Proof of Origin Token) | Token que YouTube exige para validar que la petición de reproducción proviene de un cliente "legítimo" y no de un bot. Si falta o es inválido, los streams pueden venir degradados, vacíos o bloqueados. Generarlo/obtenerlo correctamente es uno de los obstáculos centrales del motor de extracción propio. |
| **n-signature** (parámetro `n` / "throttling signature") | Parámetro cifrado en la URL del stream que YouTube ofusca mediante una función JavaScript embebida en su player. Si no se "resuelve" (descifra) correctamente, la descarga del video se estrangula (throttling) hasta volverse inservible. Resolver la n-signature es uno de los puntos de mantenimiento recurrente: cambia con frecuencia. |
| **BotGuard** | Sistema antifraude/antibot de Google (basado en un VM de JavaScript ofuscado) que produce atestaciones de "no soy un bot". Está relacionado con la generación del PoToken. Es parte del muro que el extractor propio debe sortear y una causa típica de bloqueos "not a bot". |
| **visitor-data** | Identificador de "visitante" anónimo que InnerTube asocia a una sesión sin login. Se obtiene al inicializar y se reenvía en peticiones posteriores para dar coherencia (y a veces para desbloquear contenido/streams). En YouTroc lo gestiona el adaptador de extracción/sesión para la `Session.Anonymous` de la Fase 1; es un detalle de `:data:session` y **no se filtra al dominio**. |
| **adPlacements / playerAds** | Campos de la respuesta de InnerTube que describen los anuncios a insertar (pre-roll, mid-roll, banners). **YouTroc los ignora deliberadamente**: como nunca ejecutamos el player oficial de Google, no hay nada que los "inserte". Aquí reside el ad-free *por construcción*; no usamos bloqueo DNS ni MITM ni microG. |
| **googlevideo CDN** | Red de distribución de contenido (dominios `*.googlevideo.com`) desde la que se descargan los bytes reales de audio y video. El detalle clave: los anuncios viajan por la **misma** CDN que el contenido, por eso bloquear por DNS no funciona para YouTube. Las URLs de stream resueltas por `StreamProvider` apuntan aquí. |

### Streaming y reproducción

Términos del puerto `MediaPlayer` y del adaptador `:data:player` (Media3/ExoPlayer). La cadena de reproducción tiene tres capas: **decodificación** (la hace el hardware del SoC vía MediaCodec, nunca nosotros), **motor de reproducción** (Media3) y **UI de controles** (nuestra, en Compose for TV).

| Término | Definición y relevancia en YouTroc |
| --- | --- |
| **DASH** (Dynamic Adaptive Streaming over HTTP) | Protocolo de streaming adaptativo en el que el audio y el video viajan en **pistas separadas** y en múltiples calidades, que el reproductor combina y conmuta según ancho de banda. YouTube sirve los formatos de mayor calidad en DASH con audio-only y video-only por separado. Esto explica por qué el dominio modela pistas separadas (ver `PlayableStreams`) y por qué el player necesita combinarlas. |
| **ABR** (Adaptive Bitrate) | Estrategia por la que el reproductor **elige y conmuta automáticamente** la calidad de las pistas DASH según el ancho de banda y el hardware. En YouTroc el ABR es **automático y acotado al hardware del C6K**, con preferencia de códec AV1 → VP9 → H.264. **No hay selector manual de calidad** en la primera iteración de Fase 1 (se evalúa después). |
| **MergingMediaSource** | Componente de Media3/ExoPlayer que fusiona dos fuentes —una de **solo video** y otra de **solo audio**— en una sola reproducción sincronizada. Es la pieza concreta que une las pistas DASH separadas de YouTube. Vive en `:data:player`; el dominio solo expresa que existen pistas separadas, no cómo se fusionan. |
| **MediaCodec** | API de Android que da acceso a los **decodificadores de hardware** del SoC. En el TCL 55C6K (MediaTek Pentonic 700) decodifica AV1/VP9/H.264 por hardware. YouTroc NO implementa decodificación: la hace el chip. Configuramos un `MediaCodecSelector` *hardware-first* y `setEnableDecoderFallback(true)` por si un decodificador falla. |
| **SurfaceView** | Vista de Android que pinta el video en una *surface* dedicada del compositor, en lugar de dentro del árbol de vistas (como `TextureView`). Es la opción correcta para video a pantalla completa en TV: menor consumo, mejor rendimiento y soporte limpio de HDR. YouTroc usa SurfaceView, no TextureView. |

### UI y toolkit

| Término | Definición y relevancia en YouTroc |
| --- | --- |
| **Leanback** | Antigua librería de AndroidX para construir UIs de Android TV (filas, tarjetas, navegación con D-pad). Está **oficialmente deprecada**. YouTroc NO la usa; se menciona solo para contraste histórico y porque el paquete oficial de YouTube para TV es un app Leanback aparte (`com.google.android.youtube.tv`). |
| **Compose for TV** | Toolkit declarativo de UI para Android TV (`androidx.tv` / `tv-material`) sobre Jetpack Compose, con foco, listas perezosas y componentes pensados para los 10 pies de distancia y el control remoto. Es el toolkit elegido para toda la UI de YouTroc, incluida la **UI de controles del player** que construimos sobre el puerto `MediaPlayer`. En el C6K (3GB) el coste extra de RAM/GC de Compose es despreciable. |
| **D-pad** (mando direccional) | Modelo de interacción del control remoto de TV. YouTroc sigue las **convenciones estándar de Android TV / Google TV**: navegación por foco, OK selecciona, BACK retrocede y controles propios de play/pausa/seek durante la reproducción. **Sin atajos personalizados** en Fase 1. |

### Build y rendimiento

| Término | Definición y relevancia en YouTroc |
| --- | --- |
| **Baseline Profile** | Lista de rutas de código "calientes" que se entrega con la app para que ART las compile a código nativo de forma anticipada (AOT), en vez de interpretarlas en el primer arranque. Reduce jank de arranque y de scroll. En YouTroc es **práctica obligatoria desde el día uno**, específica para nuestra app. |
| **R8** | Compilador/optimizador y *shrinker* oficial de Android: elimina código muerto, ofusca y optimiza el bytecode. Usamos **R8 full mode** como práctica obligatoria de día uno, junto con el Baseline Profile, para que la app sea pequeña y rápida en el dispositivo objetivo. Las metas de rendimiento se miden **siempre en el C6K real** con R8 full + Baseline Profile. |

### Sesión y extras

| Término | Definición y relevancia en YouTroc |
| --- | --- |
| **OAuth device-code** (OAuth 2.0 Device Authorization Grant) | Flujo de login pensado para dispositivos con entrada incómoda (TVs): la app muestra un código y el usuario lo introduce en `youtube.com/activate` desde el móvil/PC; al confirmar, el dispositivo recibe tokens. Es el estilo "SmartTube", **sin microG**. YouTroc lo usa en la **Fase 2** detrás del puerto `SessionGateway` para habilitar home personalizado, suscripciones, historial remoto, acciones de cuenta y SponsorBlock. El riesgo de baneo de cuenta aplica **solo con sesión iniciada** → se recomienda una cuenta Google desechable (*burner*). |
| **SponsorBlock** | Servicio comunitario que mantiene una base de datos colaborativa de segmentos no-contenido dentro de los videos (patrocinios, intros, outros, autopromoción, recordatorios de "suscríbete", tramos fuera de tema, previews, etc.), para **saltarlos o silenciarlos** automáticamente. Es una integración de terceros, no parte de YouTube. **Decisión confirmada:** YouTroc SÍ lo integra, pero en **Fase 2**. El dominio **MODELA** los segmentos saltables desde ya (ver `SponsorSegment`) para tener el modelo listo; el adaptador/integración real de SponsorBlock (`:data:sponsorblock`) se entrega en **Fase 2**. NO está en Fase 1 ni en el vertical slice. |
| **sideload** | Instalar una app en Android/Android TV directamente (APK), sin pasar por una tienda. Es el **único** método de distribución de YouTroc: es de uso personal, no para tiendas, y el dueño es el único usuario. Trivial de hacer sobre su propio C6K. |

---

## Parte 2 — Modelo de dominio (`:core:domain`)

`:core:domain` es **Kotlin puro**, sin dependencias del SDK de Android y sin depender de ningún otro módulo. Aquí viven dos cosas: los **modelos de dominio** (entidades y value objects de esta sección) y los **puertos** (interfaces). Las features (`:feature:*`) conocen solo estos tipos y estos puertos; los adaptadores (`:data:*`) los implementan y traducen desde InnerTube, Media3, OAuth y la persistencia local. El dominio se prueba con **fakes detrás de los puertos** (TDD activo, Decisión #7).

Recordatorio de puertos del dominio, para anclar el vocabulario:

- `StreamProvider` — resuelve los streams reproducibles de un video **ignorando los campos de anuncios**; devuelve `PlayableStreams` para un `VideoId`. Es el núcleo anti-ads y es intercambiable de motor (scaffold NewPipeExtractor ↔ motor propio).
- `VideoCatalog` — provee feeds (p. ej. el home/trending) y los videos relacionados read-only de Fase 1.
- `VideoSearch` — ejecuta búsquedas y devuelve resultados.
- `MediaPlayer` — controla la reproducción (play/pause/seek).
- `SessionGateway` — provee la `Session` actual (Fase 1 `Anonymous` / Fase 2 `Authenticated`).
- `SettingsStore` — persiste las **preferencias/ajustes** locales (puerto de `:core:domain`, implementado por `:data:persistence`).
- `FavoritesStore` — persiste los **favoritos** locales (puerto de `:core:domain`, implementado por `:data:persistence`).
- `WatchProgressStore` — persiste el **continuar viendo** (`PlaybackPosition`) (puerto de `:core:domain`, implementado por `:data:persistence`).

> **Resultado tipado de los puertos de repositorio.** `StreamProvider` y los demás puertos de repositorio (`VideoCatalog`, `VideoSearch`, …) exponen un **resultado tipado (sealed)** que distingue éxito / fallo-de-extracción / vacío / sin-red. La UI mapea cada caso a un estado explícito de error/empty/offline con mensaje claro y **acción de reintento**; la app permanece estable, nunca crashea ni muestra pantalla en blanco.

> Convención de campos: en las tablas, los tipos se expresan en términos de dominio (Kotlin), no de InnerTube. Todos los detalles de producto que antes figuraban como pendientes quedaron resueltos en la fecha de confirmación; las decisiones aparecen afirmadas en cada entrada y se recopilan al final.

---

### VideoId

- **Tipo:** Value Object
- **Propósito:** identidad única y estable de un video de YouTube. Es la clave que atraviesa todo el sistema: `StreamProvider` recibe un `VideoId` y devuelve sus `PlayableStreams`; `PlaybackPosition` se asocia a un `VideoId`; los feeds y resultados de búsqueda referencian videos por su `VideoId`.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `value` | `String` | El identificador de YouTube de 11 caracteres (p. ej. `dQw4w9WgXcQ`). |

**Invariantes:**
- `value` no es vacío y respeta el formato de id de YouTube (11 caracteres del alfabeto base64url). **Decisión confirmada:** el formato se valida estrictamente en el constructor.
- Dos `VideoId` con el mismo `value` son iguales (igualdad por valor).
- Envolver el `String` en un value object evita pasar identificadores "desnudos" y mezclar un id de video con otros strings.

---

### Video

- **Tipo:** Entity (su identidad es su `VideoId`)
- **Propósito:** representar los **metadatos** de un video para mostrarlo en listas y en la pantalla de detalle. NO contiene los streams reproducibles; esos se obtienen aparte vía `StreamProvider` para mantener separada la metadata (barata, cacheable) de la resolución de streams (cara, volátil, con tokens).

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id` | `VideoId` | Identidad del video. |
| `title` | `String` | Título mostrado. |
| `channel` | `Channel` (referencia ligera a él) | Canal autor del video. **Decisión confirmada:** en listas se incrusta de forma ligera (`channelId` + nombre + avatar) y el `Channel` completo se carga solo en la pantalla de detalle, para evitar sobre-fetch. |
| `durationSeconds` | `Long?` | Duración total en segundos; nulo en formatos donde no aplica (p. ej. directos en curso). **Decisión confirmada:** se incluye cuando InnerTube lo provee. |
| `thumbnails` | `List<Thumbnail>` | Miniaturas en varias resoluciones (URL + ancho/alto), para que Coil elija/baje de escala. |
| `publishedAt` | `Instant?` | Fecha/momento de publicación, si está disponible. **Decisión confirmada:** se incluye cuando InnerTube lo provee. |
| `viewCount` | `Long?` | Número de vistas, si está disponible. **Decisión confirmada:** se incluye cuando InnerTube lo provee. |
| `description` | `String?` | Descripción; se carga sobre todo en la pantalla de detalle. **Decisión confirmada:** la lista trae lo mínimo y el detalle completa la descripción. |

**Notas:**
- `Video` es un *resumen/metadato*. La separación frente a `PlayableStreams` es deliberada y consistente con los puertos: `VideoCatalog`/`VideoSearch` producen `Video`s; `StreamProvider` produce `PlayableStreams`.
- `Thumbnail` es un value object auxiliar (`url`, `width`, `height`).
- Los **Shorts**, si aparecen en feeds, se tratan como `Video`s normales: **no** hay UI dedicada en Fase 1.

---

### Stream

- **Tipo:** Value Object
- **Propósito:** representar **una sola pista** descargable/reproducible de un video: o solo-video, o solo-audio, o combinada (muxed). Modela la realidad de DASH, donde YouTube entrega audio y video por separado y en varias calidades. Es la unidad que el adaptador `:data:player` traduce a fuentes de Media3.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `url` | `String` | URL en la googlevideo CDN desde la que se descargan los bytes (ya con n-signature resuelta y, cuando aplica, PoToken). |
| `kind` | `StreamKind` | `VIDEO_ONLY`, `AUDIO_ONLY` o `MUXED`. Distingue las pistas separadas de DASH de un stream combinado. |
| `mimeType` | `String` | Tipo MIME y contenedor (p. ej. `video/mp4`, `video/webm`, `audio/webm`). |
| `codec` | `Codec` | Códec efectivo (AV1, VP9, H.264 para video; Opus/AAC para audio). Permite preferir AV1/VP9 (decodificables por hardware en el Pentonic 700) y caer a H.264. |
| `bitrate` | `Int?` | Bitrate aproximado en bits/s; insumo para la política de calidad. |
| `videoQuality` | `VideoQuality?` | Para pistas con video: resolución/etiqueta de calidad (p. ej. 1080p, 2160p). Nulo en `AUDIO_ONLY`. |
| `audioQuality` | `AudioQuality?` | Para pistas con audio: calidad/idioma de la pista de audio. Nulo en `VIDEO_ONLY`. |

**Notas:**
- `Stream` describe *qué hay disponible*; no decide qué se reproduce. La elección la hace la política de calidad (ABR automático) sobre `PlayableStreams`.
- El concepto evita filtrar detalles de InnerTube (como el `itag`) hacia el dominio. **Decisión confirmada:** el `itag` **no se expone** al dominio; el mapeo `itag → Stream` vive en `:data:extraction` (Regla de oro).

---

### PlayableStreams

- **Tipo:** Value Object (agregado de la resolución de un video)
- **Propósito:** ser **exactamente lo que `StreamProvider` devuelve para un `VideoId`**: el conjunto de pistas reproducibles ya resuelto y libre de anuncios (los campos `adPlacements`/`playerAds` se descartan en la extracción y nunca llegan aquí). Captura explícitamente la noción de **pistas separadas de audio y video**, que `:data:player` combinará con `MergingMediaSource`.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `videoId` | `VideoId` | Video al que pertenecen estos streams. |
| `videoTracks` | `List<Stream>` | Pistas de solo-video (`VIDEO_ONLY`) en distintas calidades/códecs. |
| `audioTracks` | `List<Stream>` | Pistas de solo-audio (`AUDIO_ONLY`) en distintas calidades/idiomas. |
| `muxedTracks` | `List<Stream>` | Pistas combinadas (`MUXED`), normalmente de menor calidad; fallback cuando no conviene/puede combinarse DASH. |
| `durationSeconds` | `Long?` | Duración del contenido reproducible, si la trae la respuesta de reproducción. |
| `isLive` | `Boolean` | Si es una emisión en directo (cambia la estrategia de buffering/seek). **Decisión confirmada:** se modela; un directo se maneja best-effort como un video normal, pero la Fase 1 inicial no compromete una UX de directo dedicada. |
| `captionTracks` | `List<CaptionTrack>` | Pistas de subtítulos disponibles. **Decisión confirmada:** el soporte de subtítulos queda **diferido** (no entra en el vertical slice ni en la Fase 1 inicial). El campo se **modela** desde ya pero **puede quedar vacío** hasta que se implemente el soporte real. |

**Notas e invariantes:**
- Para reproducir en alta calidad, `:data:player` elige **una** `Stream` de `videoTracks` y **una** de `audioTracks` y las une con `MergingMediaSource`; `muxedTracks` es el camino simple cuando no se combinan.
- **Política de calidad confirmada:** ABR (adaptive bitrate) **automático**, acotado al hardware del C6K, con preferencia de códec **AV1 → VP9 → H.264**. **Sin selector manual** de calidad en la primera iteración de Fase 1 (se evalúa después).
- `PlayableStreams` es efímero: las URLs caducan y los tokens rotan. Es candidato a NO persistirse (a diferencia de `Video`/`PlaybackPosition`).
- `CaptionTrack` es un value object auxiliar (`languageCode`, `name`, `url`, `isAutoGenerated`) que solo se materializa cuando se implemente el soporte de subtítulos; hasta entonces la lista puede quedar vacía.

---

### Channel

- **Tipo:** Entity (su identidad es su `channelId`)
- **Propósito:** representar un canal autor de videos. En Fase 1 se usa **solo como metadato dentro de `Video`** (nombre + avatar del autor). La **página de canal completa queda excluida de la Fase 1** (diferida a Fase 2 o posterior).

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id` | `ChannelId` | Identidad del canal (value object que envuelve el id de YouTube). |
| `name` | `String` | Nombre mostrado del canal. |
| `avatarThumbnails` | `List<Thumbnail>` | Avatares en varias resoluciones. **Decisión confirmada:** se incluyen cuando InnerTube los provee. |
| `subscriberCount` | `Long?` | Número de suscriptores, si está disponible. **Decisión confirmada:** se incluye cuando InnerTube lo provee. |

**Notas:**
- **Decisión confirmada:** la **página de canal** (con su propio feed de videos) **no entra en Fase 1**; se difiere a Fase 2 o posterior.
- La **suscripción** a un canal pertenece a la Fase 2 (requiere `Session.Authenticated` vía `SessionGateway`).

---

### Feed

- **Tipo:** Value Object (colección con nombre/identidad de origen)
- **Propósito:** representar una **lista ordenada de videos** producida por `VideoCatalog`. En Fase 1 el feed principal es el **home = trending** (anónimo) y, como única adición de navegación, los **videos relacionados en modo read-only**. En Fase 2 el home pasa a ser personalizado y se suman las suscripciones (requieren sesión autenticada). Es el modelo que alimenta las listas perezosas de Compose for TV.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `type` | `FeedType` | Origen del feed: `TRENDING` (Fase 1, home), `RELATED` (Fase 1, videos relacionados read-only), `PERSONALIZED_HOME` (Fase 2), `SUBSCRIPTIONS` (Fase 2). **Decisión confirmada:** en Fase 1 solo existen `TRENDING` y `RELATED`. |
| `items` | `List<Video>` | Videos del feed, como metadatos (`Video`), no como streams. |
| `continuation` | `ContinuationToken?` | Token opaco para paginar (cargar más). InnerTube pagina con "continuations"; el dominio lo expone como token opaco y `:data:extraction` lo interpreta. **Decisión confirmada:** la paginación usa un `ContinuationToken` opaco. |

**Notas:**
- `Feed` no contiene `PlayableStreams`: al abrir un video se resuelve aparte vía `StreamProvider`.
- **Decisión confirmada:** los feeds de Fase 1 son **trending** (home) y **relacionados** (read-only). **Playlists**, **páginas de canal**, **suscripciones-sin-login** y **historial remoto** quedan excluidos de Fase 1. No hay feed de historial local (los datos locales de Fase 1 se limitan a ajustes, favoritos y continuar viendo).

---

### SearchResult

- **Tipo:** Value Object
- **Propósito:** representar el resultado de una consulta de `VideoSearch`. Conceptualmente es muy parecido a un `Feed` (lista paginable), pero se modela aparte porque su origen (una query de texto) y su posible heterogeneidad (videos, canales) lo distinguen.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `query` | `String` | Texto buscado que originó el resultado. |
| `videos` | `List<Video>` | Videos que matchean la búsqueda. |
| `channels` | `List<Channel>` | Canales que matchean. **Decisión confirmada:** la búsqueda de Fase 1 devuelve sobre todo **videos**; `channels` se modela pero puede quedar vacío (las páginas de canal son Fase 2). Las **playlists** quedan excluidas de Fase 1. |
| `continuation` | `ContinuationToken?` | Token opaco para paginar más resultados. **Decisión confirmada:** la paginación usa un `ContinuationToken` opaco. |

**Notas:**
- **Decisión confirmada — alcance de búsqueda en Fase 1:** resultados de **videos**; `channels` modelado pero potencialmente vacío; playlists fuera de alcance.
- Las **sugerencias/autocompletado** entran en Fase 1 solo si el costo es bajo; si no, se difieren. El **historial de búsqueda** se asocia a la **cuenta en Fase 2** (no es local en Fase 1).
- Si en una primera iteración la búsqueda devuelve únicamente videos, `channels` puede quedar vacío sin romper el contrato.

---

### Session

- **Tipo:** sealed type (jerarquía cerrada): `Session.Anonymous` | `Session.Authenticated`
- **Propósito:** representar la **identidad/sesión actual** que provee `SessionGateway`. Existe desde el día uno aunque la Fase 1 sea solo anónima, para que el resto del sistema ya razone en términos de "hay o no hay cuenta" sin reescribirse al llegar la Fase 2.

**`Session.Anonymous` (Fase 1)**

- **Decisión confirmada:** `Session.Anonymous` es un **marcador** sin campos de InnerTube en el dominio. El `visitor-data` queda **encapsulado en `:data:session`** para mantener el dominio limpio; no se expone como campo del modelo.

**`Session.Authenticated` (Fase 2)**

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `accountLabel` | `String?` | Etiqueta legible de la cuenta (p. ej. nombre/handle), para mostrar quién está logueado. **Decisión confirmada:** se expone para identificar la sesión activa en la UI. |
| *(credenciales)* | — | Los tokens OAuth (access/refresh) y su renovación son un **detalle del adaptador `:data:session`**, NO del dominio; el dominio solo necesita saber que hay sesión autenticada. |

**Notas:**
- Habilita los feeds personalizados, suscripciones, historial remoto de la cuenta, acciones de cuenta (like, suscribir, añadir a playlist) y SponsorBlock de la Fase 2.
- El **riesgo de baneo aplica solo a `Session.Authenticated`** → se recomienda cuenta *burner*. La Fase 1 anónima no corre ese riesgo.
- El flujo de obtención es OAuth device-code (`youtube.com/activate`), **sin microG**, y vive enteramente en `:data:session`.

---

### PlaybackPosition

- **Tipo:** Value Object
- **Propósito:** recordar **dónde quedó** la reproducción de un video, para reanudar ("continuar viendo"). Se asocia a un `VideoId` y es el principal candidato a **persistencia local** (Room/DataStore) en `:data:persistence`, a través del puerto `WatchProgressStore`.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `videoId` | `VideoId` | Video al que pertenece la posición. |
| `positionMillis` | `Long` | Posición actual en milisegundos. |
| `durationMillis` | `Long?` | Duración total conocida, para calcular el % visto y decidir cuándo marcar "terminado". |
| `updatedAt` | `Instant` | Momento de la última actualización; permite ordenar/expirar. **Decisión confirmada:** se incluye para ordenar/expirar el continuar viendo. |

**Notas e invariantes:**
- `positionMillis >= 0` y, si `durationMillis` está presente, `positionMillis <= durationMillis`.
- Lo produce/consume el flujo del puerto `MediaPlayer` (play/pause/seek) y lo persiste `WatchProgressStore` sobre `:data:persistence`.
- **Decisión confirmada:** el **"continuar viendo" es local en la Fase 1 anónima** vía `WatchProgressStore`. En Fase 1 se guardan **solo** ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore`); **no** hay historial local completo y **nada sale del dispositivo**. El **historial remoto** es de Fase 2.

---

### SponsorSegment

- **Tipo:** Value Object
- **Propósito:** representar un **segmento saltable/silenciable** dentro de un video (patrocinio, intro, outro, autopromoción, recordatorio de "suscríbete", tramo fuera de tema, preview, etc.), tal como los publica la base comunitaria de **SponsorBlock**. Se asocia a un `VideoId`.
- **Estado:** **se MODELA desde ya** para tener el dominio listo, pero la **integración/adaptador de SponsorBlock** (`:data:sponsorblock`) se entrega en **Fase 2**. NO está en Fase 1 ni en el vertical slice. Su provisión vivirá detrás de un puerto del dominio entregado en Fase 2 (p. ej. `SponsorSegmentProvider`, nombre ilustrativo).

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `videoId` | `VideoId` | Video al que pertenece el segmento. |
| `category` | `SponsorCategory` | Categoría del segmento (p. ej. `SPONSOR`, `INTRO`, `OUTRO`, `SELF_PROMO`, `INTERACTION`, `MUSIC_OFFTOPIC`, `PREVIEW`). |
| `action` | `SponsorAction` | Acción a aplicar: `SKIP` (saltar) o `MUTE` (silenciar). |
| `startMillis` | `Long` | Inicio del segmento en milisegundos. |
| `endMillis` | `Long` | Fin del segmento en milisegundos. |

**Notas e invariantes:**
- `startMillis >= 0` y `endMillis >= startMillis`.
- En Fase 1 no se consume: el dominio expone el tipo, pero ningún adaptador lo provee hasta Fase 2.
- `SponsorCategory` y `SponsorAction` son enums auxiliares del dominio; el mapeo desde la API de SponsorBlock vive en `:data:sponsorblock`.

---

## Consistencia con los puertos

Para evitar deriva entre este modelo y los contratos del dominio, esta es la correspondencia esperada (tipos en términos de dominio, no firmas literales). Todos los puertos de repositorio devuelven un **resultado tipado (sealed)** que distingue éxito / fallo-de-extracción / vacío / sin-red.

| Puerto | Entrada principal | Salida principal | Nota |
| --- | --- | --- | --- |
| `StreamProvider` | `VideoId` | `PlayableStreams` | Descarta `adPlacements`/`playerAds`. Motor intercambiable: scaffold NewPipeExtractor ↔ motor propio, mismo contrato. Resultado tipado (éxito/fallo-extracción/vacío/sin-red). |
| `VideoCatalog` | `FeedType` (+ `ContinuationToken?`) | `Feed` (de `Video`) | Fase 1: `TRENDING` (home) y `RELATED` (read-only). Fase 2: `PERSONALIZED_HOME`, `SUBSCRIPTIONS`. Resultado tipado. |
| `VideoSearch` | `String` (query) (+ `ContinuationToken?`) | `SearchResult` | Fase 1: sobre todo videos; `channels` posiblemente vacío; sin playlists. Resultado tipado. |
| `MediaPlayer` | comandos (play/pause/seek) sobre `PlayableStreams` | estado de reproducción + `PlaybackPosition` | UI de controles propia en Compose for TV sobre este puerto. ABR automático, sin selector manual en Fase 1. |
| `SessionGateway` | — / flujo OAuth device-code (Fase 2) | `Session` (`Anonymous`/`Authenticated`) | Existe desde el día uno; Fase 1 siempre `Anonymous`. |
| `SettingsStore` | clave/valor de preferencias | preferencias/ajustes locales | Puerto de `:core:domain`, implementado por `:data:persistence` (DataStore). Solo local; nada sale del dispositivo. |
| `FavoritesStore` | `VideoId` / `Video` | favoritos locales | Puerto de `:core:domain`, implementado por `:data:persistence` (Room). Solo local. |
| `WatchProgressStore` | `PlaybackPosition` (`VideoId`) | continuar viendo (`PlaybackPosition`) | Puerto de `:core:domain`, implementado por `:data:persistence` (Room). Persiste el continuar viendo de Fase 1. Solo local. |

**Regla de oro mantenida:** las features dependen solo de `:core:domain` (estos tipos + estos puertos). Los detalles de InnerTube (itag, continuations, visitor-data, tokens), de Media3 (`MergingMediaSource`, `MediaCodecSelector`), de OAuth y de SponsorBlock viven en los `:data:*` y nunca se filtran al dominio.

---

## Decisiones confirmadas (resueltas en la fecha de confirmación)

Todas las decisiones de producto que antes figuraban como `(Asunción — a confirmar)` o como preguntas abiertas quedaron **CERRADAS** en la fecha de confirmación. Ya no son asunciones: son **decisiones afirmadas** que el dominio y los adaptadores deben respetar. Se listan aquí para trazabilidad.

### Modelo de dominio y campos

1. **`VideoId` valida el formato de YouTube** (11 caracteres base64url) en su constructor.
2. **`Video` referencia al `Channel` de forma ligera** (`channelId` + nombre + avatar) en listas y carga el `Channel` completo solo en la pantalla de detalle, para evitar sobre-fetch.
3. **Campos opcionales de `Video`** (`durationSeconds`, `publishedAt`, `viewCount`, `description`) se incluyen cuando InnerTube los provee; la lista trae lo mínimo y el detalle completa.
4. **`Stream` no expone el `itag`** al dominio; el mapeo `itag → Stream` queda en `:data:extraction` (Regla de oro).
5. **Subtítulos diferidos:** se modelan `captionTracks` / `CaptionTrack` en el dominio, pero **pueden quedar vacíos**. El soporte real **no** entra en el vertical slice ni en la Fase 1 inicial.
6. **SponsorBlock:** el dominio **modela** los segmentos saltables (`SponsorSegment`) desde ya; la **integración/adaptador** (`:data:sponsorblock`) se entrega en **Fase 2** (no en Fase 1 ni en el vertical slice).
7. **`Feed`/`SearchResult` paginan** con un `ContinuationToken` opaco (mapeado desde las "continuations" de InnerTube).
8. **`Session.Anonymous` es un marcador** sin campos de InnerTube en el dominio; el `visitor-data` queda **encapsulado en `:data:session`** (dominio limpio).
9. **`Session.Authenticated` expone `accountLabel`** para mostrar quién está logueado; los tokens OAuth viven en `:data:session`.
10. **`PlaybackPosition` lleva `updatedAt`** para ordenar/expirar el continuar viendo.

### Alcance de fases y comportamiento

11. **Límite de la Fase 1 (anónima):** incluye home = feed de **trending**, **búsqueda**, **detalle de video** (metadatos + reproducir), **reproducción sin ads** y **videos relacionados en modo read-only** (única adición de navegación). Datos **solo locales**: ajustes, favoritos y continuar viendo.
12. **Excluido de la Fase 1** (diferido a Fase 2 o posterior): páginas de canal, playlists, suscripciones-sin-login, Shorts (si aparecen en feeds se tratan como videos normales, sin UI dedicada), historial remoto y acciones de cuenta.
13. **Fase 2 (con cuenta, OAuth device-code):** home personalizada, suscripciones, historial remoto de la cuenta, acciones de cuenta (like, suscribir, añadir a playlist) y SponsorBlock.
14. **Datos locales en Fase 1:** se guardan **solo** ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo / `PlaybackPosition` (`WatchProgressStore`). **No** hay historial local completo; nada sale del dispositivo. El historial **remoto** es de Fase 2. (El antiguo nombre `WatchHistoryStore` queda eliminado: el puerto correcto es `WatchProgressStore`.)
15. **Política de calidad:** ABR (adaptive bitrate) **automático**, acotado al hardware del C6K, con preferencia de códec **AV1 → VP9 → H.264**. **Sin selector manual** de calidad en la primera iteración de Fase 1.
16. **Idioma/región InnerTube:** `hl=es` (español) y `gl`/región heredada del dispositivo.
17. **Errores / vacío / sin red:** `StreamProvider` y los demás puertos de repositorio devuelven un **resultado tipado (sealed)** que distingue éxito / fallo-de-extracción / vacío / sin-red. La UI muestra un estado explícito de error/empty/offline con mensaje claro y **acción de reintento**; la app permanece estable, nunca crashea ni muestra pantalla en blanco.
18. **Contenido restringido / age-gated:** en modo anónimo (Fase 1) no se garantiza; manejo **best-effort** y, si no es resoluble, mensaje explícito sin disrupción. La resolución real puede requerir cuenta (Fase 2).
19. **Mando / D-pad:** convenciones estándar de Android TV / Google TV (navegación por foco, OK selecciona, BACK retrocede, controles propios de play/pausa/seek en reproducción). **Sin atajos personalizados** en Fase 1.
20. **Búsqueda:** la Fase 1 devuelve sobre todo **videos**; `channels` se modela pero puede quedar vacío (las páginas de canal son Fase 2) y las **playlists** quedan excluidas. Las **sugerencias/autocompletado** entran en Fase 1 solo si el costo es bajo; si no, se difieren. El **historial de búsqueda** se asocia a la cuenta en **Fase 2** (no local en Fase 1).
21. **Directos (`isLive`)** quedan modelados; un video en directo se maneja best-effort como un video normal, pero la Fase 1 inicial no compromete una UX de directo dedicada.
22. **Fundamentos (Decisión #7):** TDD activo (dominio probado con **fakes detrás de los puertos**); persistencia local **Room/DataStore**; distribución por **sideload** al C6K; el **motor de extracción propio es el riesgo #1**, con **NewPipeExtractor** como red de seguridad.
