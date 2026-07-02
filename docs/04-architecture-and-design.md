# Arquitectura y Diseño Técnico — YouTroc

> Cómo se estructura un cliente de YouTube sin ads para Android TV construido desde cero: módulos, puertos y adaptadores, el mecanismo anti-ads por construcción, el flujo de reproducción y el mapeo del stack.

---

## Principios rectores

Tres principios verificados guían cada decisión de este documento. No son adorno: explican *por qué* el grafo de módulos y los puertos quedan como quedan.

| Principio | Qué significa aquí | Dónde se ve |
|---|---|---|
| **Construir-vs-comprar** | Construimos desde cero SOLO donde diferenciamos (el extractor InnerTube). Usamos una librería donde el problema es infraestructura genérica y profundamente resuelta (el player). | `:data:extraction` es motor propio; `:data:player` es Media3/ExoPlayer. |
| **Hexagonal solo donde hay volatilidad** | Puertos & adaptadores NO se aplican dogmáticamente a todo. Solo donde hay volatilidad real o valor de aislamiento para testing: extracción, player, sesión, persistencia. | Los 8 puertos del dominio viven exactamente en esos cuatro límites de volatilidad. |
| **Screaming + vertical slice** | El nivel superior del proyecto grita el DOMINIO (catalog, search, video, playback, session), no las capas técnicas. Se entrega por rebanadas verticales: cada feature atraviesa UI -> caso de uso -> puerto. | `:feature:*` organizado por dominio; el arranque es un vertical slice sin UI. |

La regla transversal: **velocidad = cuellos de botella entendidos, NO el lenguaje elegido**. Las palancas reales (selección de codec, presupuesto de 16 ms por frame, presupuesto de memoria, Baseline Profile + R8) son agnósticas al lenguaje. Por eso el stack es 100% Kotlin: no hay un hot loop de cómputo que acelerar, el decode lo hace el hardware del SoC y la capa de UI/app de Android no se puede escribir en Rust.

---

## Grafo de módulos

Gradle multi-módulo: los límites los **fuerza el compilador**, no la disciplina del autor. Si un `:feature:*` intenta importar un `:data:*`, el build NO compila.

```
                            ┌───────────────────────────────────────┐
                            │                 :app                    │
                            │  composition root · DI                  │
                            │  ÚNICO módulo que conoce features Y     │
                            │  adapters, y los une (inyecta los       │
                            │  adapters dentro de los puertos)        │
                            └───┬─────────────────────────────────┬───┘
                conoce / depende│                                 │conoce / depende
              ┌─────────────────┘                                 └─────────────────┐
              ▼                                                                      ▼
   ┌───────────────────────────┐                              ┌───────────────────────────┐
   │          FEATURES         │                              │            DATA            │
   │   (vertical slices · UI)  │                              │    (adapters de puertos)   │
   ├───────────────────────────┤                              ├───────────────────────────┤
   │ :feature:catalog          │                              │ :data:extraction           │
   │ :feature:search           │                              │ :data:player               │
   │ :feature:video            │                              │ :data:session              │
   │ :feature:playback         │                              │ :data:persistence          │
   │ :feature:session          │                              │                            │
   └──────────┬────────────────┘                              └──────────────┬─────────────┘
              │ depende de                                                    │ implementa
              │ (SOLO conoce PUERTOS,                       (implementa los   │ los PUERTOS
              │  NUNCA :data:*)                              PUERTOS)          │
              │                                                                │
              │                ┌───────────────────────────────────┐         │
              └───────────────►│            :core:domain            │◄────────┘
                               │   Kotlin PURO (sin Android SDK)    │
              ┌───────────────►│   modelos de dominio + PUERTOS     │
              │      usa       │        NO depende de NADA          │
              │                └───────────────────────────────────┘
   ┌──────────┴──────────┐
   │      :core:ui       │
   │   Compose for TV    │   (tema, helpers de focus y componentes compartidos;
   │  componentes share  │    lo consumen los :feature:* y :app — nunca el dominio)
   └─────────────────────┘

   Dirección de TODAS las flechas de dependencia: HACIA :core:domain (el centro).
```

Responsabilidad por módulo:

| Módulo | Naturaleza | Depende de | Conoce adapters concretos |
|---|---|---|---|
| `:app` | Composition root + DI | features, data, core | **Sí** (es el único) |
| `:core:domain` | Kotlin puro: modelos + puertos | **nada** | No |
| `:core:ui` | Compose for TV compartido | (toolkit Compose) | No |
| `:feature:catalog` / `:search` / `:video` / `:playback` / `:session` | Vertical slices con UI + casos de uso | `:core:domain`, `:core:ui` | No (solo puertos) |
| `:data:extraction` / `:player` / `:session` / `:persistence` | Adapters | `:core:domain` | (son los adapters) |

---

## Regla de dependencias (forzada por el compilador)

La Dependency Rule de Clean Architecture: **las dependencias de código fuente apuntan SIEMPRE hacia adentro, hacia el dominio**. Aquí no es una convención que se pide respetar en code review; es una propiedad del grafo de Gradle.

- `:core:domain` no declara NINGUNA dependencia. Es Kotlin puro, sin Android SDK. No puede, físicamente, conocer a Media3, a OkHttp, ni a Compose.
- `:feature:*` declara `implementation(project(":core:domain"))` y `:core:ui`, y nada más del lado de datos. **No** declara `:data:*`. Por lo tanto un feature solo puede hablar con interfaces (puertos); jamás con una implementación concreta.
- `:data:*` declara `implementation(project(":core:domain"))` para implementar los puertos, pero NO conoce a los features.
- `:app` es el único vértice que ve ambos lados y los une por inyección de dependencias.

**Consecuencia de testing:** como los features dependen solo de puertos, el dominio y los casos de uso se prueban con *fakes* detrás de los puertos, sin Android, sin red y sin Media3. Esto es exactamente el "valor de aislamiento para testing" que justifica aplicar hexagonal en esos cuatro límites y no en todos lados.

---

## Puertos y adaptadores

Los puertos viven en `:core:domain` (interfaces). Los adaptadores los implementan en `:data:*`. `:app` inyecta el adaptador concreto en el puerto.

| Puerto (`:core:domain`) | Responsabilidad | Adaptador concreto (`:data:*`) | Fase |
|---|---|---|---|
| `StreamProvider` | Resuelve los streams reproducibles desde InnerTube **ignorando los campos de ads**. Es el núcleo anti-ads. Engine-swappable. | `:data:extraction` (motor propio + andamio NewPipeExtractor) | Slice / Fase 1 |
| `VideoCatalog` | Feeds del catálogo (home = trending en Fase 1, más feeds después) | `:data:extraction` | Fase 1 |
| `VideoSearch` | Búsqueda de videos | `:data:extraction` | Fase 1 |
| `MediaPlayer` | Control del engine de reproducción: play / pause / seek | `:data:player` (Media3/ExoPlayer) | Slice / Fase 1 |
| `SessionGateway` | Identidad/sesión. Fase 1 = `Anonymous` (visitor-data). Fase 2 = OAuth 2.0 device-code (flujo "ingresá el código en youtube.com/activate", sin microG) | `:data:session` | Fase 1 (anon) / Fase 2 (OAuth) |
| `SettingsStore` | Preferencias locales (ajustes) | `:data:persistence` (Room y/o DataStore) | Fase 1 |
| `FavoritesStore` | Favoritos locales | `:data:persistence` (Room y/o DataStore) | Fase 1 |
| `WatchProgressStore` | Continuar viendo (`PlaybackPosition` / watch progress local; **no** hay lista completa de historial local) | `:data:persistence` (Room y/o DataStore) | Fase 1 |

> `SessionGateway` existe desde el día 1 aunque la Fase 1 sea anónima: así la transición a OAuth en Fase 2 es cambiar el adaptador inyectado, no rediseñar los features. El riesgo de baneo de cuenta aplica SOLO logueado, por lo que la Fase 2 recomienda una cuenta Google quemable.

---

## Mecanismo anti-ads (sin ads POR CONSTRUCCIÓN)

Esta es la decisión raíz del proyecto y la razón de existir de `YouTroc`.

Hay dos caminos para tener YouTube sin ads:

1. **Parchear la app oficial** (Vanced -> ReVanced -> Morphe). **No funciona en TV**: ReVanced/Morphe parchean solo el paquete de teléfono (`com.google.android.youtube`); la app de TV es un paquete Leanback distinto (`com.google.android.youtube.tv`) que ningún set de parches mantenido ataca. ReVanced declinó explícitamente soportar TV (revanced-manager #3385) y deriva a SmartTube.
2. **Construir un cliente desde cero** que hable con la API interna InnerTube de YouTube. **Camino elegido.**

### Por qué somos ad-free sin bloquear nada

La respuesta `/player` de InnerTube trae, entre otros, estos campos:

- `streamingData` -> las URLs de los formatos adaptativos reales (audio-only y video-only).
- `adPlacements` y `playerAds` -> los descriptores de los cortes publicitarios que el **player oficial** lee para programar y reproducir ads.

El player oficial de Google es quien interpreta `adPlacements`/`playerAds` y agenda los breaks. **Nosotros nunca ejecutamos el player oficial.** Nuestro `StreamProvider`:

1. Pide `/player` a InnerTube.
2. Parsea **únicamente** `streamingData`.
3. **Ignora por completo** `adPlacements` y `playerAds`. No los lee, no los agenda, no los pasa aguas abajo.

El pipeline de Media3 solo reproduce los streams de contenido que le entregamos. No hay ad que reproducir porque nunca se programó ninguno: no está *bloqueado*, simplemente nunca se pidió ni se agendó.

Por eso este proyecto **no** usa:

- **DNS blocking** — falla para YouTube: los ads vienen del mismo CDN `googlevideo` que el contenido.
- **MITM / proxy de intercepción** — innecesario: no filtramos tráfico, parseamos JSON.
- **microG** — irrelevante: microG solo importa para el login de apps *parcheadas*, no para un cliente from-scratch.

#### Escenario de aceptación — anti-ads

```
Dado  un videoId cualquiera cuya respuesta /player incluye adPlacements y playerAds
Cuando el StreamProvider resuelve el stream
Entonces extrae solo streamingData
  Y     descarta adPlacements y playerAds sin interpretarlos
  Y     Media3 reproduce únicamente el stream de contenido
  Y     no se programa ni reproduce ningún corte publicitario
```

---

## Mapeo del stack tecnológico a módulos

Qué librería vive en qué módulo. El stack es 100% Kotlin (sin Rust, sin código nativo).

| Librería / tecnología | Módulo donde vive | Rol |
|---|---|---|
| OkHttp + kotlinx.serialization | `:data:extraction` (y `:data:session` para OAuth) | HTTP + parseo JSON de InnerTube |
| Motor InnerTube propio | `:data:extraction` | Extractor desde cero (diferenciador central) |
| NewPipeExtractor (andamio) | `:data:extraction` | Baseline/andamio detrás del MISMO puerto, red de seguridad |
| Media3 / ExoPlayer | `:data:player` | Engine de reproducción (NO se reimplementa) |
| Flujo OAuth 2.0 device-code | `:data:session` | Login Fase 2 (sin microG) |
| Room y/o DataStore | `:data:persistence` | settings / favorites / watch progress (`PlaybackPosition`) local — `SettingsStore`, `FavoritesStore`, `WatchProgressStore` |
| Jetpack Compose for TV (`androidx.tv` tv-material) + Compose foundation lazy lists | `:core:ui` y `:feature:*` | UI de la app |
| Coil | lado UI (`:core:ui` / `:feature:*`) | Carga de imágenes (thumbnails) con downsampling. **No** es `:data:*`: es infraestructura de UI. |
| R8 full mode + Baseline Profile app-specific | `:app` (build) | Práctica obligatoria desde el día 1 |

> Nota de ubicación: Media3 (`:data:player`) y Compose for TV (`:core:ui`/features) viven en lados opuestos del grafo. El player es un adaptador del puerto `MediaPlayer`; la UI de controles que construimos sobre ese puerto vive en el lado feature. El player y sus controles están deliberadamente desacoplados.

---

## Flujo de datos de una reproducción

El recorrido de "el usuario abre un video y suena", atravesando el vertical slice completo:

```
[1] UI (Compose for TV)                      :feature:playback / :feature:video
        │   el usuario selecciona un video / presiona play
        ▼
[2] Caso de uso                              :feature:playback
        │   orquesta el puerto; NO conoce InnerTube ni Media3
        ▼
[3] StreamProvider.resolve(videoId)          puerto en :core:domain
        │   -> :data:extraction resuelve streamingData
        │   -> IGNORA adPlacements / playerAds  (núcleo anti-ads)
        │   devuelve descriptores de stream (audio-only + video-only DASH)
        ▼
[4] MediaPlayer.play(streams)                puerto en :core:domain
        │   -> :data:player (adapter Media3) recibe los descriptores
        ▼
[5] MergingMediaSource                        construido DENTRO del adapter :data:player
        │   combina los streams separados audio-only + video-only de YouTube
        │   en una sola fuente reproducible
        ▼
[6] Media3 / ExoPlayer                        engine, :data:player
        │   demux + buffering + sincronización A/V
        ▼
[7] MediaCodec                                API Android -> decoder hardware del SoC
        │   decode AV1/VP9/H.264 por el Pentonic 700 (NUNCA en nuestro código)
        ▼
[8] SurfaceView                               render directo en pantalla
```

Puntos clave del flujo:

- El feature (pasos 1–2) habla **solo con puertos**. No sabe que detrás hay InnerTube ni Media3.
- `MergingMediaSource` (paso 5) es un concepto de Media3 y por lo tanto vive **dentro** del adaptador `:data:player`. YouTube entrega audio y video como streams DASH separados; combinarlos en una sola fuente es responsabilidad del adapter, no del dominio.
- El **decode** (paso 7) lo hace el chip vía `MediaCodec`. Nunca es nuestro. "El player" tiene tres capas: (1) decode = hardware del SoC; (2) engine = Media3; (3) UI de controles = nuestra, en Compose for TV.

#### Escenario de aceptación — reproducción

```
Dado  un videoId resuelto por StreamProvider con streams audio-only y video-only separados
Cuando el caso de uso invoca MediaPlayer.play
Entonces el adapter :data:player construye un MergingMediaSource combinando ambos streams
  Y     Media3 entrega los buffers a MediaCodec
  Y     el Pentonic 700 decodifica por hardware (AV1/VP9 preferido, luego H.264)
  Y     el frame se renderiza en un SurfaceView
```

---

## Diseño del módulo `:data:extraction`

Es el corazón del proyecto y su **riesgo #1**. Aloja dos implementaciones del **mismo** puerto `StreamProvider`, intercambiables por DI.

### Estrategia: andamio + motor propio detrás del mismo puerto

```
                       ┌───────────────────────────────┐
   :feature:* ───────► │   StreamProvider (puerto)     │   en :core:domain
                       └───────────────┬───────────────┘
                                       │ implementado por (DI en :app)
                  ┌────────────────────┴────────────────────┐
                  ▼                                          ▼
   ┌──────────────────────────────┐         ┌──────────────────────────────┐
   │   NewPipeStreamProvider       │         │   InnerTubeStreamProvider     │
   │   (ANDAMIO / baseline)        │         │   (MOTOR PROPIO desde cero)   │
   │   envuelve NewPipeExtractor   │         │   diferenciador central       │
   └──────────────────────────────┘         └──────────────────────────────┘
        red de seguridad inicial                 se construye en paralelo;
        y referencia de comportamiento           reemplaza al andamio cuando
                                                  iguala a la referencia
```

Secuencia (estrategia strangler-fig):

1. **Arranque del vertical slice:** el `StreamProvider` se implementa con **NewPipeExtractor** como andamio/baseline. Esto prueba la hipótesis central (resolver + reproducir sin ads) el día 1, sin esperar al motor propio.
2. **En paralelo:** se construye el **motor InnerTube propio** detrás del MISMO puerto.
3. **Swap:** cuando el motor propio iguala el comportamiento de la referencia NewPipe, se cambia el adaptador inyectado en `:app`. Los features no se enteran: el puerto no cambió.

### El riesgo, explícito

El motor propio debe resolver, y mantener al día, los mecanismos anti-bot de YouTube:

- **PoToken** (Proof-of-Origin Token)
- **n-signature solving** (descifrado del parámetro `n`)
- **BotGuard / "not a bot"** challenges
- **visitor-data** y manejo de contexto de cliente InnerTube

Estos cambian sin aviso del lado de YouTube: implica mantenimiento casi *on-call*. El andamio NewPipe es la red de seguridad: si el motor propio se rompe por un cambio upstream, se reinyecta el andamio mientras se repara, sin tocar features ni UI.

#### Escenario de aceptación — failover de extracción

```
Dado  que el InnerTubeStreamProvider propio está inyectado
Cuando un cambio upstream de YouTube rompe la resolución de streams
Entonces se puede reinyectar NewPipeStreamProvider en :app
  Y     los :feature:* siguen funcionando sin cambios (solo conocen el puerto)
```

> *(Decisión confirmada — resuelta en la fecha de confirmación)* El `StreamProvider` —y los demás puertos de repositorio— exponen un resultado **tipado (sealed)** que distingue **éxito**, **fallo de extracción**, **resultado vacío** y **sin-red**. La UI muestra un estado explícito de error / empty / offline con mensaje claro y acción de **reintento**; la app permanece estable: nunca crashea ni muestra pantalla en blanco. En modo anónimo (Fase 1) el contenido restringido / age-gated se maneja **best-effort** y, si no es resoluble, se informa con un mensaje explícito sin disrupción (la resolución real puede requerir cuenta en Fase 2).

---

## Configuración de Media3

Adaptador `:data:player`. Un único dispositivo objetivo conocido (TCL 55C6K, MediaTek Pentonic 700, AV1 por hardware) elimina la necesidad de *probing* de codecs por dispositivo: configuramos directo para el piso de hardware más moderno.

| Ajuste | Valor | Por qué |
|---|---|---|
| `MediaCodecSelector` | hardware-first | Usar el decoder del SoC, no software |
| Superficie de render | **SurfaceView** (no TextureView) | Menor overhead, path de composición directo, mejor para video TV |
| Preferencia de codecs | **AV1 / VP9** primero, luego **H.264** | AV1/VP9 son decodificables por hardware en el Pentonic 700 |
| Fuente combinada | `MergingMediaSource` | Combinar los streams DASH audio-only + video-only separados de YouTube |
| Control de carga | `DefaultLoadControl` con tope de memoria | Respetar el presupuesto de RAM (3 GB) del C6K |
| Fallback de decoder | `setEnableDecoderFallback(true)` | Caer a otro decoder si el preferido falla en runtime |
| Probing por dispositivo | **No se hace** | Hay un único target conocido; no hay matriz de compatibilidad |

> *(Decisión confirmada — resuelta en la fecha de confirmación)* La política de calidad es **ABR (adaptive bitrate) automático**, acotado al hardware del C6K, con preferencia de códec **AV1 -> VP9 -> H.264**. **No** hay selector manual de calidad en la primera iteración de Fase 1 (se evalúa después). Los subtítulos/captions quedan **diferidos**: el dominio **modela** `captionTracks` / `CaptionTrack` (pueden quedar **vacíos**) pero el soporte real no entra ni en el vertical slice ni en la Fase 1 inicial.

---

## Enfoque Compose for TV

| Aspecto | Decisión | Razón |
|---|---|---|
| Toolkit | Jetpack **Compose for TV** (`androidx.tv` tv-material) + Compose foundation lazy lists | Leanback está oficialmente deprecado |
| Listas | `TvLazyRow` / `TvLazyColumn` (foundation lazy) | Filas/carruseles de catálogo con scroll y focus de D-pad |
| Componentes compartidos | `:core:ui` (tema, helpers de focus) | Reutilización entre features sin acoplarlos entre sí |
| UI de controles del player | **Propia**, construida sobre el puerto `MediaPlayer` | No usamos UI de player de terceros; control total y desacople del engine |
| Optimización obligatoria día 1 | **R8 full mode + Baseline Profile** app-specific | Arranque y jank: práctica mandatoria, no opcional |
| Costo de RAM/GC de Compose | Despreciable en el C6K (3 GB) | El target tiene margen; el delta Compose vs Views no es el cuello de botella |

La UI grita el dominio (catalog, search, video, playback, session) y cada feature es una rebanada vertical autocontenida que consume `:core:ui` y los puertos de `:core:domain`.

> *(Decisión confirmada — resuelta en la fecha de confirmación)* La interacción con control remoto / D-pad sigue las **convenciones estándar de Android TV / Google TV**: navegación por foco, **OK** selecciona, **BACK** retrocede y controles propios de play / pausa / seek durante la reproducción. **Sin atajos personalizados en Fase 1.**

---

## Fases del proyecto (resumen arquitectónico)

| Hito | Alcance | Adapter `StreamProvider` | Adapter `SessionGateway` |
|---|---|---|---|
| **Vertical slice (arranque)** | SIN UI: `videoId` hardcodeado -> InnerTube -> reproducción Media3 sin ads | NewPipeExtractor (andamio) | Anonymous |
| **Fase 1 (anónima)** | home (trending) + search + detalle de video + reproducción sin ads + videos **relacionados read-only**, con UI Compose for TV. Datos **solo locales**: ajustes, favoritos y continuar viendo (`PlaybackPosition`) | Motor propio con andamio NewPipe de respaldo | Anonymous |
| **Fase 2 (cuenta)** | Login OAuth device-code -> home personalizada, suscripciones, historial remoto, acciones de cuenta (like / suscribir / añadir a playlist) y **SponsorBlock** | Motor propio | OAuth 2.0 device-code (sin microG) |

> *(Decisión confirmada — límite Fase 1 / Fase 2, resuelta en la fecha de confirmación)* **Fase 1 (anónima) incluye:** home = feed de trending; búsqueda; detalle de video (metadatos + reproducir); reproducción sin ads; y videos **relacionados en modo read-only** (única adición de navegación). Más datos **solo locales**: ajustes, favoritos y continuar viendo (watch progress / `PlaybackPosition`); nada sale del dispositivo (no hay lista completa de historial local). **Fase 1 excluye** (diferido a Fase 2 o posterior): páginas de canal, playlists, suscripciones-sin-login, Shorts (si aparecen en feeds se tratan como videos normales, **sin UI dedicada**), historial remoto y acciones de cuenta. **Fase 2 (cuenta, OAuth device-code) incluye:** home personalizada, suscripciones, historial remoto de la cuenta, acciones de cuenta (like, suscribir, añadir a playlist) y **SponsorBlock**.

> *(Decisión confirmada — modelado anticipado de dominio)* El dominio **modela desde ya** los segmentos saltables (`SponsorSegment`) para tener el modelo listo, aunque la **integración / adaptador de SponsorBlock se entrega en Fase 2** (no en Fase 1 ni en el vertical slice). De forma análoga, `captionTracks` / `CaptionTrack` se modelan pero pueden quedar **vacíos** hasta decidir el soporte real de subtítulos. Esto preserva el contrato de los puertos al sumar las features de Fase 2 sin rediseñar el dominio.
