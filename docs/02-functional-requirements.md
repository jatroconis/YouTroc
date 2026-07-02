# Requerimientos Funcionales — YouTroc

> Propósito: definir QUÉ debe hacer YouTroc (cliente de YouTube sin ads para Android TV) por módulo de feature y por fase, con identificadores estables y criterios de aceptación verificables.

## Tabla de contenidos

- [Alcance y convenciones](#alcance-y-convenciones)
- [Mapa módulo → puerto → fase](#mapa-módulo--puerto--fase)
- [Slice vertical (precondición técnica)](#slice-vertical-precondición-técnica)
- [Catálogo / Home (:feature:catalog)](#catálogo--home-featurecatalog)
- [Búsqueda (:feature:search)](#búsqueda-featuresearch)
- [Detalle de video y relacionados (:feature:video)](#detalle-de-video-y-relacionados-featurevideo)
- [Reproducción (:feature:playback)](#reproducción-featureplayback)
- [Sesión / Identidad (:feature:session)](#sesión--identidad-featuresession)
- [Matriz Fase 1 vs Fase 2](#matriz-fase-1-vs-fase-2)
- [Decisiones confirmadas (resueltas en la fecha de confirmación)](#decisiones-confirmadas-resueltas-en-la-fecha-de-confirmación)
- [Preguntas resueltas (cerradas en la fecha de confirmación)](#preguntas-resueltas-cerradas-en-la-fecha-de-confirmación)

---

## Alcance y convenciones

YouTroc es un cliente de YouTube **sin ads por construcción**: nunca ejecuta el player oficial de Google, habla directamente con la API interna **InnerTube** vía el puerto `StreamProvider` e **ignora** los campos `adPlacements` / `playerAds` que devuelve InnerTube. No hay bloqueo por DNS ni MITM. Es de uso personal (sideload al TCL 55C6K), no para tiendas.

Convenciones de este documento:

- **ID estable** por requerimiento: `RF-<MÓDULO>-NN`. Familias: `CAT` (catálogo/home), `SRCH` (búsqueda), `VID` (detalle de video), `PLAY` (reproducción), `SESS` (sesión).
- **Fase 1 (anónima)**: home = trending, búsqueda, detalle de video y reproducción sin ads, **videos relacionados en modo read-only** (única adición de navegación), sin login. Datos SOLO locales: ajustes, favoritos y continuar viendo (watch progress / `PlaybackPosition`). Numeración `01–09`.
- **Fase 2 (con cuenta)**: login OAuth device-code, home personalizada, suscripciones, historial remoto, acciones de cuenta y SponsorBlock. Numeración `10+`.
- Criterios de aceptación en formato **Dado / Cuando / Entonces**.
- **Resultado tipado:** `StreamProvider` y los puertos de repositorio (`VideoCatalog`, `VideoSearch`) exponen un resultado **sellado (sealed)** que distingue éxito / fallo de extracción / vacío / sin red; la UI deriva de él los estados explícitos de error/empty/offline con mensaje claro y acción de reintento, sin crashear ni mostrar pantalla en blanco.
- Todos los ítems de frontera de producto quedaron **resueltos en la fecha de confirmación**; las marcas previas **(Asunción — a confirmar)** ya no aplican y su resolución está listada en [Decisiones confirmadas](#decisiones-confirmadas-resueltas-en-la-fecha-de-confirmación) y [Preguntas resueltas](#preguntas-resueltas-cerradas-en-la-fecha-de-confirmación).

Prioridad de prueba: el dominio (`:core:domain`) se valida con TDD usando fakes detrás de los puertos; cada requerimiento funcional debe ser expresable como caso de prueba contra su puerto.

---

## Mapa módulo → puerto → fase

| Módulo de feature | Puerto(s) de dominio | Adapter(s) (`:data:*`) | Fase 1 | Fase 2 |
|---|---|---|---|---|
| `:feature:catalog` | `VideoCatalog` | `:data:extraction` | Trending | Personalizada + suscripciones |
| `:feature:search` | `VideoSearch` | `:data:extraction` | Sí | Historial de búsqueda (cuenta) |
| `:feature:video` | `VideoCatalog`, `StreamProvider` | `:data:extraction` | Detalle + relacionados (read-only) | Acciones de cuenta |
| `:feature:playback` | `MediaPlayer`, `StreamProvider`, `WatchProgressStore` | `:data:player`, `:data:extraction`, `:data:persistence` | Sin ads, play/pausa/seek, continuar viendo | Historial remoto |
| `:feature:session` | `SessionGateway` | `:data:session`, `:data:persistence` | Anónima | OAuth device-code |

Regla de dependencias: los `:feature:*` dependen SOLO de `:core:domain` y `:core:ui` (conocen únicamente puertos); los `:data:*` implementan los puertos; `:app` inyecta los adapters en los puertos.

Puertos de persistencia local (transversales, en `:core:domain`, implementados por `:data:persistence`): `SettingsStore` (preferencias), `FavoritesStore` (favoritos locales), `WatchProgressStore` (`PlaybackPosition` / continuar viendo). Ningún dato sale del dispositivo; no hay lista completa de historial local (el historial remoto es de Fase 2).

---

## Slice vertical (precondición técnica)

Hito de arranque previo a la UI de Fase 1: probar la hipótesis central en día 1. No es una feature de usuario final, pero condiciona todos los `RF-PLAY`.

| ID | Requerimiento |
|---|---|
| RF-PLAY-00 | Con un `videoId` fijo en código, resolver streams vía `StreamProvider` (InnerTube) ignorando los campos de ads y reproducir con Media3/ExoPlayer, sin UI propia. Sirve como prueba de concepto del núcleo anti-ads y del puerto `StreamProvider` (engine-swappable: NewPipeExtractor como scaffold, motor propio en paralelo). |

**Dado** un `videoId` válido hardcodeado
**Cuando** se invoca el `StreamProvider` y se entregan los streams a Media3
**Entonces** el video se reproduce sin ads y sin haber ejecutado el player oficial de Google.

El slice vertical NO incluye subtítulos ni SponsorBlock (diferidos / Fase 2).

---

## Catálogo / Home (:feature:catalog)

Puerto: `VideoCatalog`. Adapter: `:data:extraction`.

### Fase 1 — anónima

| ID | Requerimiento |
|---|---|
| RF-CAT-01 | La home muestra contenido **trending** sin requerir login, obtenido vía `VideoCatalog`. |
| RF-CAT-02 | El usuario navega el catálogo con el D-pad del control remoto siguiendo las **convenciones estándar de Android TV / Google TV**: navegación por foco (visible y predecible), OK selecciona, BACK retrocede. Sin atajos personalizados en Fase 1. |
| RF-CAT-03 | Las miniaturas se cargan con Coil aplicando downsampling acorde al presupuesto de memoria (3 GB RAM). |
| RF-CAT-04 | Al seleccionar un ítem, se navega al detalle de video (`:feature:video`). |
| RF-CAT-05 | La home maneja explícitamente, a partir del **resultado tipado (sealed)** de `VideoCatalog`, los estados de **error de extracción**, **resultados vacíos** y **sin red**, mostrando mensaje claro y opción de reintento; la app permanece estable. |
| RF-CAT-06 | Las peticiones a InnerTube usan idioma/región por defecto `hl=es` (español) y `gl`/región heredada del dispositivo. |

**RF-CAT-01 — Trending anónimo**
**Dado** que no hay sesión iniciada (modo anónimo)
**Cuando** el usuario abre la aplicación
**Entonces** la home muestra una lista de videos trending obtenida vía `VideoCatalog`, sin pedir login y sin mostrar ads.

**RF-CAT-05 — Sin red**
**Dado** que el dispositivo no tiene conectividad
**Cuando** se intenta cargar la home
**Entonces** el resultado tipado reporta el caso "sin red", la UI muestra un estado de error con mensaje claro y acción de reintento, y la app no cierra ni queda en blanco.

### Fase 2 — con cuenta

| ID | Requerimiento |
|---|---|
| RF-CAT-10 | Con sesión OAuth activa, la home muestra contenido **personalizado** (recomendaciones de la cuenta) en lugar de trending. |
| RF-CAT-11 | El usuario accede a sus **suscripciones** desde el catálogo. |
| RF-CAT-12 | La home conmuta automáticamente entre **trending** (anónimo) y **personalizada** (con sesión) según el estado de `SessionGateway`. |

**RF-CAT-12 — Conmutación por estado de sesión**
**Dado** que el usuario inicia sesión con OAuth device-code
**Cuando** vuelve a la home
**Entonces** el contenido cambia de trending a personalizado sin reiniciar la app.

---

## Búsqueda (:feature:search)

Puerto: `VideoSearch`. Adapter: `:data:extraction`.

### Fase 1 — anónima

| ID | Requerimiento |
|---|---|
| RF-SRCH-01 | El usuario busca videos por texto mediante un teclado en pantalla operable con D-pad. |
| RF-SRCH-02 | Los resultados se obtienen vía `VideoSearch` y se muestran como lista navegable. |
| RF-SRCH-03 | Al seleccionar un resultado, se navega al detalle de video (`:feature:video`). |
| RF-SRCH-04 | La búsqueda maneja, a partir del **resultado tipado (sealed)** de `VideoSearch`, los estados de **sin resultados**, **error de extracción** y **sin red**, con mensaje claro y reintento; la app permanece estable. |
| RF-SRCH-05 | Se muestran sugerencias/autocompletado mientras se escribe **solo si el costo de implementación es bajo**; en caso contrario, esta capacidad queda **diferida**. El historial de búsqueda NO es local en Fase 1 (se asocia a la cuenta en Fase 2). |

**RF-SRCH-01 — Búsqueda por texto anónima**
**Dado** que no hay sesión iniciada
**Cuando** el usuario introduce un término y confirma la búsqueda
**Entonces** se muestran resultados obtenidos vía `VideoSearch`, sin login y sin ads.

**RF-SRCH-04 — Sin resultados**
**Dado** un término que no devuelve coincidencias
**Cuando** se ejecuta la búsqueda
**Entonces** el resultado tipado reporta el caso "vacío" y la UI muestra un estado vacío explícito en lugar de una lista en blanco.

### Fase 2 — con cuenta

| ID | Requerimiento |
|---|---|
| RF-SRCH-10 | Con sesión activa, las búsquedas registran/usan el **historial de búsqueda de la cuenta** (la fuente es la cuenta, no almacenamiento local). |

---

## Detalle de video y relacionados (:feature:video)

Puertos: `VideoCatalog` (relacionados), `StreamProvider` (metadata de reproducción). Adapter: `:data:extraction`.

### Fase 1 — anónima

| ID | Requerimiento |
|---|---|
| RF-VID-01 | La pantalla de detalle muestra metadata del video: título, canal, descripción y datos básicos disponibles vía extracción. |
| RF-VID-02 | Existe una acción **Reproducir** que inicia `:feature:playback` para el `videoId` seleccionado. |
| RF-VID-03 | Se muestran **videos relacionados** navegables en modo **read-only**, que llevan a su propio detalle. Es la única adición de navegación de Fase 1. |
| RF-VID-04 | El nombre del canal NO enlaza a página de canal en Fase 1: las **páginas de canal** quedan diferidas a **Fase 2 o posterior**. |
| RF-VID-05 | El contenido **restringido / age-gated** se maneja best-effort en modo anónimo; cuando no es resoluble, se muestra un mensaje explícito sin disrupción (la resolución real puede requerir cuenta en Fase 2). |

**RF-VID-02 — Iniciar reproducción**
**Dado** un video en la pantalla de detalle
**Cuando** el usuario activa la acción Reproducir
**Entonces** se abre `:feature:playback`, se resuelven los streams vía `StreamProvider` y comienza la reproducción sin ads.

**RF-VID-03 — Relacionados (read-only)**
**Dado** un video en detalle
**Cuando** la pantalla termina de cargar
**Entonces** se muestra una sección de videos relacionados navegable con D-pad, en modo read-only, donde cada ítem abre su propio detalle.

### Fase 2 — con cuenta

| ID | Requerimiento |
|---|---|
| RF-VID-10 | Con sesión activa, el detalle ofrece **acciones de cuenta** (like, suscribirse). |
| RF-VID-11 | El usuario puede añadir el video a **playlists** o "ver más tarde". |

---

## Reproducción (:feature:playback)

Puertos: `MediaPlayer` (play/pausa/seek), `StreamProvider` (resolución de streams), `WatchProgressStore` (continuar viendo / `PlaybackPosition`). Adapters: `:data:player` (Media3/ExoPlayer), `:data:extraction`, `:data:persistence`. La UI de controles es propia en Compose for TV sobre el puerto `MediaPlayer`.

### Fase 1 — anónima

| ID | Requerimiento |
|---|---|
| RF-PLAY-01 | La reproducción es **sin ads por construcción**: el `StreamProvider` ignora `adPlacements` / `playerAds` y nunca se ejecuta el player oficial de Google. |
| RF-PLAY-02 | Controles de **play / pausa** operables con D-pad. |
| RF-PLAY-03 | **Seek** (avanzar/retroceder) con barra de progreso y posición/duración visibles. |
| RF-PLAY-04 | Los streams **separados** audio-only + video-only (DASH) se combinan en reproducción mediante `MergingMediaSource`. |
| RF-PLAY-05 | Selección de códec **hardware-first** (preferir AV1 → VP9 → H.264) con `SurfaceView`, `MediaCodecSelector` hardware-first y `setEnableDecoderFallback(true)`. |
| RF-PLAY-06 | Política de **calidad/resolución**: **ABR (adaptive bitrate) automático**, acotado al hardware del C6K, con preferencia de códec AV1 → VP9 → H.264. **Sin selector manual de calidad** en la primera iteración de Fase 1 (se evalúa después). |
| RF-PLAY-07 | Manejo de fallos a partir del **resultado tipado (sealed)** del `StreamProvider`: **stream no resoluble**, **error de extracción** y **fallback de decoder**, con mensaje claro y sin cierre de la app. |
| RF-PLAY-08 | **Subtítulos / captions**: **diferidos** (no en el slice vertical ni en la Fase 1 inicial). El dominio MODELA `captionTracks` / `CaptionTrack`, pero la colección puede quedar vacía hasta que se decida/implemente el soporte real más adelante. |
| RF-PLAY-09 | **SponsorBlock**: el dominio MODELA los segmentos saltables (`SponsorSegment`) desde Fase 1 para tener el modelo listo; la **integración/adaptador** de SponsorBlock para saltarlos se entrega en **Fase 2** (no en Fase 1 ni en el slice vertical). |
| RF-PLAY-10 | **Continuar viendo** local: recordar la posición de reproducción del último video vía `WatchProgressStore` (`PlaybackPosition`). Confirmado **local en Fase 1**; nada sale del dispositivo. |

**RF-PLAY-01 — Sin ads por construcción**
**Dado** un video resuelto vía `StreamProvider`
**Cuando** comienza la reproducción
**Entonces** no se reproduce ningún ad porque los campos `adPlacements` / `playerAds` se ignoran y no se ejecuta el player oficial.

**RF-PLAY-03 — Seek**
**Dado** un video en reproducción
**Cuando** el usuario mueve la barra de progreso con el D-pad y confirma
**Entonces** la reproducción salta a la posición elegida y la posición/duración se actualizan.

**RF-PLAY-04 — Streams separados audio+video**
**Dado** un video cuyo InnerTube devuelve pistas separadas de solo-audio y solo-video
**Cuando** se construye la fuente de medios
**Entonces** ambas pistas se combinan con `MergingMediaSource` y se reproducen sincronizadas.

**RF-PLAY-07 — Fallo de extracción**
**Dado** un video cuya resolución de streams falla (p. ej. bloqueo de InnerTube)
**Cuando** el usuario intenta reproducir
**Entonces** el resultado tipado reporta el fallo de extracción, se muestra un error claro con opción de reintento y la app permanece estable.

**RF-PLAY-10 — Continuar viendo local**
**Dado** un video que el usuario dejó a mitad de reproducción
**Cuando** vuelve a abrir ese video
**Entonces** `WatchProgressStore` provee la última `PlaybackPosition` y la reproducción puede retomarse desde ahí, con los datos guardados solo en el dispositivo.

### Fase 2 — con cuenta

| ID | Requerimiento |
|---|---|
| RF-PLAY-20 | Con sesión activa, la reproducción se registra en el **historial de YouTube** de la cuenta. |

---

## Sesión / Identidad (:feature:session)

Puerto: `SessionGateway` (presente desde día 1). Adapters: `:data:session`, `:data:persistence`.

### Fase 1 — anónima

| ID | Requerimiento |
|---|---|
| RF-SESS-01 | La app opera en **modo anónimo** por defecto: sin login, con home = trending, búsqueda, reproducción sin ads y relacionados read-only. |
| RF-SESS-02 | El `SessionGateway` provee el contexto anónimo necesario para InnerTube (p. ej. `visitor-data`), gestionado internamente y sin exponerse al usuario. |
| RF-SESS-03 | El estado de sesión (anónimo) es consultable por los demás features para decidir su comportamiento (p. ej. trending vs personalizada). |
| RF-SESS-04 | Los datos locales en modo anónimo se limitan a **ajustes** (`SettingsStore`), **favoritos** (`FavoritesStore`) y **continuar viendo** (`WatchProgressStore` / `PlaybackPosition`). No hay lista completa de historial local y nada sale del dispositivo. |

**RF-SESS-01 — Anónimo por defecto**
**Dado** una instalación nueva sin login
**Cuando** el usuario abre la app
**Entonces** `SessionGateway` reporta estado anónimo y todas las features de Fase 1 funcionan sin credenciales.

### Fase 2 — con cuenta

| ID | Requerimiento |
|---|---|
| RF-SESS-10 | El usuario inicia sesión mediante **OAuth 2.0 device-authorization** (flujo "introduce el código en youtube.com/activate", estilo SmartTube), **sin microG**. |
| RF-SESS-11 | Los tokens de sesión se **persisten de forma segura** vía `:data:session` / `:data:persistence`. |
| RF-SESS-12 | Los tokens se **renuevan** (refresh) de forma transparente cuando expiran. |
| RF-SESS-13 | El usuario puede **cerrar sesión**, volviendo al modo anónimo (home = trending). |
| RF-SESS-14 | Antes/durante el login se advierte el **riesgo de baneo de cuenta** (aplica solo con sesión iniciada) y se recomienda usar una **burner account**. |

**RF-SESS-10 — Login device-code**
**Dado** que el usuario elige iniciar sesión
**Cuando** la app muestra un código y el usuario lo introduce en youtube.com/activate y autoriza
**Entonces** `SessionGateway` pasa a estado autenticado, sin microG, y la home se vuelve personalizada.

**RF-SESS-13 — Logout**
**Dado** una sesión OAuth activa
**Cuando** el usuario cierra sesión
**Entonces** los tokens se descartan, `SessionGateway` vuelve a anónimo y la home muestra trending.

---

## Matriz Fase 1 vs Fase 2

| Capacidad | Fase 1 (anónima) | Fase 2 (con cuenta) |
|---|---|---|
| Home | Trending | Personalizada + suscripciones |
| Búsqueda | Sí | Sí + historial de cuenta |
| Detalle de video | Sí | Sí + acciones de cuenta |
| Relacionados | Sí (read-only) | Sí |
| Página de canal | No | Sí |
| Playlists | No | Sí |
| Reproducción sin ads | Sí | Sí |
| Controles play/pausa/seek | Sí | Sí |
| Subtítulos | Diferido (dominio modela `captionTracks` / `CaptionTrack`) | Por evaluar |
| SponsorBlock | No (dominio modela `SponsorSegment`) | Sí (integración/adaptador) |
| Continuar viendo (local) | Sí (`WatchProgressStore` / `PlaybackPosition`) | Sí |
| Historial remoto | No | Sí |
| Login | No (anónimo) | OAuth device-code, sin microG |
| Riesgo de baneo | No aplica | Aplica → burner account |

---

## Decisiones confirmadas (resueltas en la fecha de confirmación)

Todos los defaults de nivel PRD que antes se marcaban inline como **(Asunción — a confirmar)** quedaron **CERRADOS en la fecha de confirmación**. Ya no son asunciones: son decisiones afirmadas. La frontera de Fase 1 está fijada.

1. **Videos relacionados** → **CONFIRMADO**: incluidos en Fase 1 como navegación **read-only** (`RF-VID-03`). Única adición de navegación de Fase 1.
2. **Páginas de canal** → **CONFIRMADO**: diferidas a **Fase 2 o posterior** (`RF-VID-04`).
3. **Playlists** → **CONFIRMADO**: Fase 2 (`RF-VID-11`).
4. **Suscripciones sin login** → **CONFIRMADO**: no soportadas; requieren cuenta (Fase 2, `RF-CAT-11`).
5. **Shorts** → **CONFIRMADO**: fuera del alcance inicial; si aparecen en feeds, se tratan como videos normales, sin UI dedicada de Shorts.
6. **"Continuar viendo" local** → **CONFIRMADO**: existe en Fase 1, local, vía `WatchProgressStore` / `PlaybackPosition` (`RF-PLAY-10`). No hay lista completa de historial local.
7. **Política de calidad/resolución** → **CONFIRMADO**: **ABR automático** acotado al hardware del C6K, con preferencia de códec AV1 → VP9 → H.264, **sin selector manual** en la primera iteración de Fase 1 (`RF-PLAY-06`).
8. **SponsorBlock** → **CONFIRMADO**: integración en **Fase 2**; el dominio modela `SponsorSegment` desde ya (`RF-PLAY-09`).
9. **Subtítulos / captions** → **CONFIRMADO**: **diferidos**, no en Fase 1 inicial; el dominio modela `captionTracks` / `CaptionTrack`, posiblemente vacíos (`RF-PLAY-08`).
10. **Idioma/región por defecto para InnerTube** → **CONFIRMADO**: `hl=es` y `gl`/región heredada del dispositivo (`RF-CAT-06`).
11. **Comportamiento ante fallo de extracción / resultados vacíos / sin red** → **CONFIRMADO**: los puertos exponen un **resultado tipado (sealed)**; la UI muestra error/empty/offline con mensaje claro + reintento, app estable (`RF-CAT-05`, `RF-SRCH-04`, `RF-PLAY-07`).
12. **Contenido restringido / age-gated** → **CONFIRMADO**: en anónimo, manejo best-effort; si no es resoluble, mensaje explícito sin disrupción (puede requerir cuenta en Fase 2) (`RF-VID-05`).
13. **Datos locales en Fase 1 anónima** → **CONFIRMADO**: solo `SettingsStore` (ajustes) + `FavoritesStore` (favoritos) + `WatchProgressStore` (`PlaybackPosition`); nada sale del dispositivo (`RF-SESS-04`).
14. **Interacción control remoto / D-pad** → **CONFIRMADO**: convenciones estándar de Android TV / Google TV (navegación por foco, OK selecciona, BACK retrocede, controles play/pausa/seek en reproducción), sin atajos personalizados en Fase 1 (`RF-CAT-02`).
15. **Contexto anónimo InnerTube (`visitor-data`)** → **CONFIRMADO**: gestionado por `SessionGateway` sin exponerse al usuario (`RF-SESS-02`).
16. **Sugerencias/autocompletado de búsqueda en Fase 1** → **CONFIRMADO**: incluido solo si el costo es bajo; de lo contrario diferido (`RF-SRCH-05`).
17. **Fuente del historial de búsqueda** → **CONFIRMADO**: cuenta en Fase 2; no local en Fase 1 (`RF-SRCH-10`).
18. **Acciones de cuenta e historial remoto en Fase 2** → **CONFIRMADO**: like/suscribir y registro de historial de la cuenta (`RF-VID-10`, `RF-PLAY-20`).

---

## Preguntas resueltas (cerradas en la fecha de confirmación)

Las decisiones de producto que afectaban la frontera de Fase 1 / Fase 2 quedaron **todas resueltas**. No queda ninguna pregunta abierta.

1. **¿Videos relacionados en Fase 1 o Fase 2?** → Resuelto: **Fase 1, read-only**.
2. **¿Páginas de canal en Fase 1 o Fase 2?** → Resuelto: **Fase 2 o posterior**.
3. **¿Playlists en Fase 2 o más adelante?** → Resuelto: **Fase 2**.
4. **¿Suscripciones sin login se descartan?** → Resuelto: **sí, solo con cuenta (Fase 2)**.
5. **¿Tratamiento de Shorts?** → Resuelto: **sin UI dedicada; si aparecen, se tratan como videos normales** (fuera del alcance inicial).
6. **¿"Continuar viendo" / historial local en Fase 1?** → Resuelto: **sí, "continuar viendo" local vía `WatchProgressStore`; no hay historial local completo**.
7. **¿Política de calidad/resolución y selector manual?** → Resuelto: **ABR automático acotado al hardware, AV1 → VP9 → H.264, sin selector manual en Fase 1**.
8. **¿Se integra SponsorBlock?** → Resuelto: **sí, en Fase 2; dominio modela `SponsorSegment` desde ya**.
9. **¿Subtítulos / captions y desde qué fase?** → Resuelto: **diferidos; dominio modela `captionTracks` / `CaptionTrack` (posiblemente vacíos)**.
10. **¿Idioma/región por defecto en InnerTube?** → Resuelto: **`hl=es` + `gl`/región del dispositivo**.
11. **¿Comportamiento ante fallo / vacío / sin red?** → Resuelto: **resultado tipado (sealed); UI error/empty/offline con mensaje claro + reintento; app estable**.
12. **¿Manejo de contenido restringido / age-gated en anónimo?** → Resuelto: **best-effort; si no resuelve, mensaje explícito; puede requerir cuenta (Fase 2)**.
13. **¿Qué datos locales se almacenan en Fase 1 anónima?** → Resuelto: **`SettingsStore` + `FavoritesStore` + `WatchProgressStore`; nada sale del dispositivo**.
14. **¿Expectativas de interacción con D-pad?** → Resuelto: **convenciones estándar de Android TV / Google TV, sin atajos personalizados en Fase 1**.
15. **¿Búsqueda con sugerencias/autocompletado en Fase 1?** → Resuelto: **solo si el costo es bajo; si no, diferidas**.
16. **¿Historial de búsqueda local o de cuenta y en qué fase?** → Resuelto: **de la cuenta, en Fase 2**.
17. **¿Qué acciones de cuenta e historial remoto en Fase 2?** → Resuelto: **like, suscribir, añadir a playlist y registro de historial remoto de la cuenta**.
