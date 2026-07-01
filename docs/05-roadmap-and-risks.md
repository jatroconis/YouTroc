# Roadmap y Registro de Riesgos — youtroc

> Plan de entrega incremental (de la vertical slice sin UI a la experiencia completa con cuenta) y el registro de riesgos del proyecto, con su mitigación, para un cliente de YouTube sin anuncios en Android TV de uso personal (sideload).

---

## 0. Contexto y alcance del documento

Este documento cubre dos cosas:

1. **Roadmap** — la secuencia de hitos, desde el Hito 0 (vertical slice sin UI) hasta la Fase 2 (cuenta), respetando la regla de dependencias hacia el dominio y la separación por módulos.
2. **Registro de Riesgos** — la tabla de riesgos con probabilidad, impacto y mitigación, encabezada por el riesgo #1: la rotura de la extracción.

El proyecto es un cliente de YouTube **sin anuncios por construcción** para el **TCL 55C6K** (MediaTek Pentonic 700, 3GB RAM, 32GB ROM, Google TV moderno). Ese dispositivo es el **piso**: no hay objetivos por debajo de él. La distribución es **sideload** al equipo del propio dueño; no hay tiendas de aplicaciones.

> Convención de este documento: los defaults de producto que originalmente NO estaban cubiertos por las DECISIONS de origen fueron **confirmados el 2026-06-30** y aquí aparecen como decisiones cerradas (ya no como asunciones). Las resoluciones puntuales se listan en la sección "Decisiones confirmadas" al final. No se inventan decisiones de negocio.

---

# PARTE 1 — ROADMAP

El roadmap es estrictamente **incremental por riesgo**: primero se prueba la hipótesis nuclear (¿podemos resolver un stream reproducible vía InnerTube y reproducirlo sin anuncios?), y solo después se construye la experiencia. El orden NO es por facilidad, es por **dónde está el riesgo**.

## 1.1 Vista general de hitos

| Hito | Nombre | Objetivo | Estado de cuenta | Entrega de UI |
|------|--------|----------|------------------|---------------|
| **Hito 0** | Vertical slice (sin UI) | `videoId` hardcodeado → InnerTube → reproducir sin anuncio con Media3 | Anónimo | Ninguna (pantalla pelada / Activity de prueba) |
| **F1.A** | Catálogo / Trending | Home = trending navegable | Anónimo | Compose for TV |
| **F1.B** | Search | Búsqueda de videos | Anónimo | Compose for TV |
| **F1.C** | Video Detail + Relacionados (read-only) | Detalle del video + fila de relacionados read-only | Anónimo | Compose for TV |
| **F1.D** | Playback con controles | Controles propios sobre el `MediaPlayer` port | Anónimo | Compose for TV |
| **F1.E** | Datos locales | Ajustes + favoritos + continuar viendo (solo en el dispositivo) | Anónimo | Compose for TV |
| **F2.A** | Login OAuth (device-code) | Flujo `youtube.com/activate`, sin microG | → Autenticado | Compose for TV |
| **F2.B** | Home personalizada | Recomendaciones de la cuenta | Autenticado | Compose for TV |
| **F2.C** | Suscripciones | Feed de canales suscritos | Autenticado | Compose for TV |
| **F2.D** | Historial remoto | Historial de reproducción de la cuenta | Autenticado | Compose for TV |
| **F2.E** | Acciones de cuenta | Like, suscribir, añadir a playlist | Autenticado | Compose for TV |
| **F2.F** | SponsorBlock | Saltar segmentos patrocinados durante la reproducción | Autenticado | Compose for TV |

## 1.2 Hito 0 — Vertical slice (sin UI)

**Propósito:** probar la hipótesis nuclear del proyecto el día 1. Si esto no funciona, nada más importa.

**Definición:** dado un `videoId` **hardcodeado**, resolver un stream reproducible a través de **InnerTube** detrás del port `StreamProvider`, y reproducirlo con **Media3/ExoPlayer** **ignorando** los campos `adPlacements`/`playerAds` (sin anuncios por construcción). NO hay UI: una `Activity` mínima o un test instrumentado basta.

**Disciplina de arquitectura desde el primer commit:**

- El `videoId` entra; el `StreamProvider` port (en `:core:domain`, Kotlin puro) devuelve los streams reproducibles.
- El **scaffold** de extracción es **NewPipeExtractor** detrás del `StreamProvider` port (red de seguridad / baseline). En paralelo se construye **nuestro propio extractor InnerTube** detrás del **mismo** port, y se hace el swap cuando iguala a la referencia.
- El playback usa el `MediaPlayer` port, implementado en `:data:player` sobre Media3.
- El `SessionGateway` port existe desde el día 1 en modo **Anonymous**.

**Configuración del player desde Hito 0** (no se posterga, define la viabilidad):

| Aspecto | Decisión |
|---------|----------|
| Selección de codec | `MediaCodecSelector` hardware-first |
| Superficie de render | `SurfaceView` (NO `TextureView`) |
| Preferencia de códecs | AV1 → VP9 → H.264 (AV1/VP9 decodificables por hardware en Pentonic 700) |
| Streams DASH | `MergingMediaSource` para combinar el stream audio-only + el video-only separados de YouTube |
| Control de memoria | `DefaultLoadControl` con tope de memoria |
| Fallback | `setEnableDecoderFallback(true)` |
| Probing por dispositivo | No necesario (dispositivo único conocido) |

**Modelado de dominio desde Hito 0 (aunque la feature se entregue después):** el dominio modela `captionTracks` / `CaptionTrack` (subtítulos) y los segmentos saltables (p. ej. `SponsorSegment`) desde ya, para tener el modelo listo. Estos campos pueden quedar **vacíos** hasta que se implemente el soporte real más adelante (captions diferidos; SponsorBlock en Fase 2).

**Dado / Cuando / Entonces (criterio de aceptación del Hito 0):**

- **Dado** un `videoId` válido hardcodeado y red disponible,
- **Cuando** se ejecuta la vertical slice,
- **Entonces** el video se reproduce con audio y video sincronizados, en hardware, **sin ningún anuncio**, en el C6K real.

- **Dado** un `videoId` que requiere PoToken / n-signature,
- **Cuando** el scaffold (NewPipeExtractor) lo resuelve,
- **Entonces** la reproducción funciona, estableciendo el **baseline** que nuestro extractor propio debe igualar antes del swap.

**Salida del Hito 0:** hipótesis probada en hardware real. La `StreamProvider` queda validada como costura (seam) intercambiable de motor.

## 1.3 Fase 1 — Anónima (sin login)

La Fase 1 construye la experiencia completa **anónima** sobre la base del Hito 0. La cuenta sigue en modo **Anonymous** (`SessionGateway`). Cada feature es un módulo `:feature:*` que depende SOLO de `:core:domain` y `:core:ui`, NUNCA de `:data:*`.

**Alcance confirmado de Fase 1:** home = feed de trending; búsqueda; detalle de video (metadatos + reproducir); reproducción sin ads; videos **relacionados en modo read-only** (única adición de navegación); y más datos **SOLO locales**: ajustes, favoritos y continuar viendo. Quedan **FUERA** de Fase 1 (diferidos a Fase 2 o posterior): páginas de canal, playlists, suscripciones-sin-login, Shorts (si aparecen en feeds se tratan como videos normales, sin UI dedicada), historial remoto y acciones de cuenta.

### F1.A — Catálogo / Trending

- **Port:** `VideoCatalog` (feeds).
- **Feature:** `:feature:catalog`.
- **Entrega:** home = trending, navegable con D-pad, lazy lists de Compose foundation + `androidx.tv` tv-material.
- **Dado** que el usuario abre la app sin login, **cuando** carga la home, **entonces** ve el feed de trending navegable con el control remoto.

### F1.B — Search

- **Port:** `VideoSearch`.
- **Feature:** `:feature:search`.
- **Entrega:** búsqueda de videos con teclado on-screen de TV.
- **Sugerencias/autocompletado:** se incluyen en Fase 1 **solo si el costo es bajo**; si no, se difieren. El **historial de búsqueda** NO es local en Fase 1; se asocia a la cuenta en Fase 2.
- **Dado** un término de búsqueda, **cuando** el usuario lo confirma, **entonces** ve resultados navegables que puede abrir.

### F1.C — Video Detail + Relacionados (read-only)

- **Feature:** `:feature:video`.
- **Entrega:** pantalla de detalle del video (título, canal, metadatos, acción de reproducir) **más una fila de videos RELACIONADOS en modo read-only** — la única adición de navegación de Fase 1.
- **Límite confirmado:** los videos **relacionados read-only** SÍ entran en Fase 1. Las **páginas de canal** y las **playlists** quedan **FUERA** de Fase 1 (diferidas a Fase 2 o posterior). Los **Shorts** no tienen UI dedicada: si aparecen en feeds o relacionados, se tratan como videos normales.

### F1.D — Playback con controles

- **Port:** `MediaPlayer` (play/pause/seek).
- **Feature:** `:feature:playback`.
- **Entrega:** **controles propios** construidos en Compose for TV sobre el `MediaPlayer` port (NO se reusa la UI de controles de Media3). El motor de playback sigue siendo Media3; solo la UI de controles es nuestra.
- **Dado** un video en reproducción, **cuando** el usuario usa el D-pad, **entonces** puede play/pause/seek con controles propios.
- **Política de calidad confirmada:** **ABR (adaptive bitrate) automático**, acotado al hardware del C6K, con preferencia de códec **AV1 → VP9 → H.264**. **SIN selector manual de calidad** en la primera iteración de Fase 1 (se evalúa después).

### F1.E — Datos locales (ajustes / favoritos / continuar viendo)

- **Ports:** `SettingsStore` (preferencias), `FavoritesStore` (favoritos locales), `WatchProgressStore` (PlaybackPosition / continuar viendo) — definidos en `:core:domain`, implementados por `:data:persistence` (Room/DataStore).
- **Entrega:** pantalla de ajustes; marcar/desmarcar favoritos; **continuar viendo** (recuerda la posición de reproducción `PlaybackPosition`).
- **Alcance confirmado:** Fase 1 guarda **LOCALMENTE y SOLO** ajustes, favoritos y continuar viendo. **NO** hay lista completa de historial local. **Nada sale del dispositivo.** El historial remoto es de Fase 2.

### F1.X — Comportamientos transversales de Fase 1 (confirmados)

Aplican a todas las features anónimas:

- **Error / vacío / sin red:** `StreamProvider` y los demás ports de repositorio exponen un resultado **TIPADO (sealed)** que distingue éxito / fallo-de-extracción / vacío / sin-red. La UI muestra un estado explícito de error/empty/offline con **mensaje claro y acción de reintento**; la app permanece estable, nunca crashea ni muestra pantalla en blanco.
- **Contenido restringido / age-gated:** en modo anónimo **no se garantiza**; manejo **best-effort** y, si no es resoluble, mensaje explícito sin disrupción. La resolución real puede requerir cuenta (Fase 2).
- **Mando / D-pad:** convenciones **estándar de Android TV / Google TV** (navegación por foco, OK selecciona, BACK retrocede, controles propios de play/pausa/seek durante la reproducción). **Sin atajos personalizados** en Fase 1.
- **Idioma / región InnerTube:** `hl=es` (español); `gl`/región **heredada del dispositivo**.
- **Subtítulos / captions:** **diferidos** (no en el vertical slice ni en la Fase 1 inicial); el dominio modela `captionTracks` / `CaptionTrack` pero puede quedar **vacío** hasta implementarse el soporte real más adelante.
- **SponsorBlock:** el dominio modela los segmentos saltables (`SponsorSegment`) desde ya; la **integración/adaptador** se entrega en **Fase 2** (no en Fase 1 ni en el vertical slice).

**Criterio de salida de Fase 1:** experiencia anónima completa (trending + search + detail con relacionados read-only + playback con controles + datos locales de ajustes/favoritos/continuar viendo) corriendo en el C6K, con **nuestro motor de extracción** activo y NewPipeExtractor degradado a red de seguridad.

## 1.4 Fase 2 — Cuenta (login)

El riesgo de **baneo de cuenta** aplica SOLO con login. La Fase 2 se construye sobre los mismos ports; solo el `SessionGateway` cambia de **Anonymous** a **OAuth**.

**Alcance confirmado de Fase 2:** home personalizada, suscripciones, historial remoto de la cuenta, acciones de cuenta (like, suscribir, añadir a playlist) y SponsorBlock.

### F2.A — Login OAuth (device-authorization)

- **Port:** `SessionGateway` (modo OAuth).
- **Feature/adapter:** `:feature:session` + `:data:session`.
- **Flujo:** OAuth 2.0 **device-authorization** estilo SmartTube (el usuario ingresa un código en `youtube.com/activate`). **Sin microG** (microG solo importa para apps parcheadas; aquí es irrelevante).
- **Recomendación de seguridad:** usar una **cuenta quemable (burner)** de Google, porque el riesgo de baneo solo existe estando logueado.

### F2.B — Home personalizada

- **Port:** `VideoCatalog` (con sesión autenticada) → feeds personalizados de la cuenta.

### F2.C — Suscripciones

- **Port:** `VideoCatalog` / sesión → feed de canales suscritos.

### F2.D — Historial remoto

- **Port:** sesión → historial de reproducción **remoto** de la cuenta (no local).

### F2.E — Acciones de cuenta

- **Port:** `SessionGateway` / `VideoCatalog` con sesión autenticada.
- **Acciones:** like, suscribir, añadir a playlist.

### F2.F — SponsorBlock

- **Adapter:** integración de SponsorBlock que consume el modelo de segmentos saltables (`SponsorSegment`) ya presente en el dominio desde Fase 1.
- **Entrega:** saltar segmentos patrocinados durante la reproducción.

**Criterio de salida de Fase 2:** login por device-code funcional + home personalizada + suscripciones + historial remoto + acciones de cuenta + SponsorBlock, todo sobre los mismos ports de dominio.

## 1.5 Innegociables de build (aplican a TODOS los hitos)

Estos son **práctica day-one obligatoria**, no optimizaciones tardías:

| Innegociable | Regla | Verificación |
|--------------|-------|--------------|
| **R8 full mode** | Activo desde el primer build de release | Build de release siempre con R8 full mode |
| **Baseline Profile** | Perfil **app-specific**, generado para los flujos reales (arranque, home, playback) | Perfil presente y aplicado en release |
| **Medir en hardware real** | Toda métrica de rendimiento se mide en el **C6K real**, no en emulador ni en otro dispositivo | Trazas/medición en el TCL 55C6K con R8 full + Baseline Profile |

> En el C6K de 3GB el delta de RAM/GC de Compose es **negligible** según las decisiones, pero eso NO releva de la disciplina: R8 + Baseline Profile + medición en el dispositivo real son la base, no un extra.

## 1.6 Diagrama de dependencias (recordatorio de la regla)

```
:app  (composition root — único que conoce features + adapters; inyecta adapters en ports)
  |
  |-- :feature:catalog, :feature:search, :feature:video, :feature:playback, :feature:settings, :feature:session
  |        depend SOLO de  ->  :core:domain  +  :core:ui      (NUNCA de :data:*)
  |
  |-- :data:extraction, :data:player, :data:session, :data:persistence
  |        implementan los ports  ->  depend de  :core:domain
  |
  :core:domain  (Kotlin puro; modelos + ports; depende de NADA)
  :core:ui      (componentes Compose for TV compartidos)

Ports de dominio: StreamProvider, VideoCatalog, VideoSearch, MediaPlayer, SessionGateway,
                  SettingsStore, FavoritesStore, WatchProgressStore
```

---

# PARTE 2 — REGISTRO DE RIESGOS

Escala usada — **Probabilidad**: Alta / Media / Baja. **Impacto**: Crítico / Alto / Medio / Bajo. El registro está ordenado por severidad, encabezado por el riesgo #1.

## 2.1 Tabla de riesgos

| # | Riesgo | Probabilidad | Impacto | Mitigación |
|---|--------|--------------|---------|------------|
| **1** | **Rotura de la extracción** — PoToken, n-signature, BotGuard, visitor-data, bloqueos "not a bot" rompen el motor propio | **Alta** | **Crítico** | NewPipeExtractor como **scaffold/baseline** detrás del **mismo** `StreamProvider` port (red de seguridad si el motor propio cae); motor propio engine-swappable; mantenimiento casi de guardia; el port aísla el resto de la app del fallo del motor |
| **2** | **Bus factor / mantenedor único** — un solo desarrollador mantiene todo; sin redundancia humana | **Alta** | **Alto** | Arquitectura modular con límites forzados por el compilador → cambios localizados; ports que aíslan la volatilidad (extracción, player, session, persistencia); TDD del dominio con fakes detrás de los ports → red de regresión; NewPipeExtractor como fallback reduce la presión de mantenimiento del motor propio |
| **3** | **Juego del gato y el ratón server-side de YouTube** — YouTube cambia su API/protección del lado servidor de forma continua | **Alta** | **Crítico** | Tratar la extracción como dependencia **viva**, no estática; `StreamProvider` swappable para reaccionar rápido; baseline de NewPipeExtractor que absorbe cambios mientras se actualiza el motor propio; superada la dependencia: somos el mejor **cliente**, no un mejor YouTube |
| **4** | **ToS y baneo de cuenta (Fase 2)** — uso no oficial de la API interna estando logueado puede derivar en baneo | **Media** | **Alto** | El riesgo aplica **solo** con login; Fase 1 es 100% anónima (sin exposición de cuenta); recomendación explícita de **cuenta quemable (burner)** para Fase 2; mantener Fase 1 plenamente funcional sin cuenta |
| **5** | **Rendimiento de Compose si se descuida la disciplina** — jank, GC, presupuesto de 16ms si no se cuida | **Media** | **Alto** | R8 full mode + Baseline Profile **app-specific** day-one; medir en el **C6K real**; entender los bottlenecks reales (selección de codec, frame budget 16ms, presupuesto de memoria) que son **agnósticos del lenguaje**; en 3GB el delta de RAM/GC de Compose es negligible |
| **6** | **Distribución solo-sideload** — sin tienda, sin canal de actualización automático | **Baja** | **Bajo** | Decisión de diseño consciente: uso personal, no para tiendas; el dueño es el único usuario y el sideload al C6K es **trivial** para él; sin necesidad de pipeline de distribución pública |
| **7** | **ROM / almacenamiento** — 32GB ROM en el C6K, presupuesto finito para app + datos locales | **Baja** | **Medio** | Coil con downsampling para imágenes; persistencia local acotada (Room/DataStore) solo para ajustes, favoritos y continuar viendo (sin historial local); sin assets pesados embebidos; `DefaultLoadControl` con tope de memoria evita buffers desbordados |

## 2.2 Notas sobre el riesgo #1 (extracción)

El riesgo #1 es la **razón de ser** de la secuencia del roadmap:

- Es el **diferenciador del producto** y el **objetivo principal de aprendizaje**: por eso se construye desde cero detrás del `StreamProvider` port.
- La **mitigación estructural** ya está en la arquitectura: NewPipeExtractor entra como scaffold detrás del **mismo** port, de modo que un fallo del motor propio degrada a la referencia sin reescribir el resto de la app.
- Construimos desde cero **donde diferenciamos** (el extractor) y usamos librería **donde es infraestructura genérica** (el player, Media3). El riesgo se concentra deliberadamente en un solo seam intercambiable.

## 2.3 Innegociables de build como mitigación transversal

Los innegociables de la sección 1.5 también funcionan como mitigación del riesgo #5:

- **R8 full mode** + **Baseline Profile app-specific** son obligatorios desde el día 1.
- **Toda métrica se mide en el C6K real** — nunca en emulador. El dispositivo único conocido es el juez final del rendimiento.

---

## 3. Decisiones confirmadas (resueltas el 2026-06-30)

Estos defaults se eligieron a nivel PRD porque las DECISIONS de origen no los cubrían. **Todos quedaron confirmados** el 2026-06-30 y aquí se registran como decisiones cerradas:

1. **RESUELTO — Límite Fase 1 vs Fase 2.** Fase 1 (anónima) incluye home=trending, búsqueda, detalle de video, reproducción sin ads, **videos relacionados read-only** y **datos locales** (ajustes/favoritos/continuar viendo). Quedan **FUERA** de Fase 1: páginas de canal, playlists, suscripciones-sin-login, Shorts (sin UI dedicada; si aparecen en feeds se tratan como videos normales), historial remoto y acciones de cuenta — diferidos a Fase 2 o posterior.
2. **RESUELTO — Detalle de video (F1.C).** Muestra metadatos + acción de reproducir **+ relacionados read-only**; páginas de canal y playlists quedan fuera de Fase 1.
3. **RESUELTO — Política de calidad.** ABR (adaptive bitrate) automático, acotado al hardware del C6K, con preferencia de códec **AV1 → VP9 → H.264**; **sin selector manual** de calidad en la primera iteración de Fase 1.
4. **RESUELTO — Escala de riesgos.** Se acepta la escala (Probabilidad Alta/Media/Baja × Impacto Crítico/Alto/Medio/Bajo) y la asignación concreta de este documento, con la **extracción como riesgo #1 (Alta/Crítico)**.
5. **RESUELTO — Datos locales en Fase 1 anónima.** Solo ajustes, favoritos y continuar viendo (watch progress / `PlaybackPosition`) vía `SettingsStore` / `FavoritesStore` / `WatchProgressStore`. Sin historial local; nada sale del dispositivo; el historial remoto es de Fase 2.

---

## 4. Decisiones confirmadas — preguntas previas (resueltas el 2026-06-30)

Las preguntas que estaban abiertas antes de codificar quedaron todas resueltas el 2026-06-30:

1. **Límite exacto de la Fase 1.** RESUELTO — ver decisión 1 arriba. Relacionados read-only y datos locales (ajustes/favoritos/continuar viendo) entran en Fase 1; canal, playlists, suscripciones-sin-login, Shorts (UI dedicada), historial remoto y acciones de cuenta van a Fase 2 o posterior.
2. **Política de resolución/calidad de playback.** RESUELTO — ABR automático acotado al hardware, preferencia AV1 → VP9 → H.264; sin selector manual en la primera iteración de Fase 1.
3. **Integración de SponsorBlock.** RESUELTO — **Sí, en Fase 2**. El dominio modela los segmentos saltables (`SponsorSegment`) desde ya; la integración/adaptador se entrega en Fase 2 (no en Fase 1 ni en el vertical slice).
4. **Subtítulos/captions.** RESUELTO — **diferidos**; no en el vertical slice ni en la Fase 1 inicial. El dominio modela `captionTracks` / `CaptionTrack` pero puede quedar vacío hasta implementarse el soporte real más adelante.
5. **Idioma/región por defecto de InnerTube.** RESUELTO — `hl=es` (español); `gl`/región heredada del dispositivo.
6. **Fallo de extracción / resultados vacíos / sin red.** RESUELTO — `StreamProvider` y los demás ports exponen un resultado **tipado (sealed)** que distingue éxito / fallo-de-extracción / vacío / sin-red; la UI muestra un estado explícito de error/empty/offline con mensaje claro y reintento; la app permanece estable.
7. **Contenido restringido / age-gated.** RESUELTO — en modo anónimo no se garantiza: manejo best-effort y, si no es resoluble, mensaje explícito sin disrupción; la resolución real puede requerir cuenta (Fase 2).
8. **Datos locales exactos en Fase 1 anónima.** RESUELTO — solo ajustes, favoritos y continuar viendo (solo local); sin historial local.
9. **Interacción con control remoto / D-pad.** RESUELTO — convenciones estándar de Android TV / Google TV (foco, OK selecciona, BACK retrocede, controles propios de play/pausa/seek); sin atajos personalizados en Fase 1.
10. **"Continuar viendo" (local) en la Fase 1.** RESUELTO — **Sí**: watch progress / `PlaybackPosition` vía `WatchProgressStore`, solo local.
11. **Escala oficial de probabilidad/impacto del registro de riesgos.** RESUELTO — la escala propuesta en este documento se confirma como oficial, sin diferencias.
