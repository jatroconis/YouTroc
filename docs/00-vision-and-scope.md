# Visión y Alcance — YouTroc

> Cliente de YouTube para Android TV, sin anuncios por construcción, hecho desde cero en Kotlin para un único dispositivo objetivo (TCL 55C6K).

---

## 1. El problema

El uso de YouTube en el televisor está saturado de anuncios. En la TV, la experiencia de los anuncios es especialmente intrusiva: interrumpen la reproducción a pantalla completa, son difíciles de saltar con un mando D-pad y no existe una vía cómoda de cliente alternativo oficial sin pagar Premium.

Las dos formas conocidas de obtener YouTube sin anuncios son:

| Vía | Qué es | Por qué (no) sirve en TV |
|---|---|---|
| **(A) Patchear la app oficial** | Vanced → ReVanced → Morphe: parchean el paquete oficial de YouTube | **NO funciona en TV.** Los parches solo cubren el paquete de móvil (`com.google.android.youtube`). La app de TV es un paquete Leanback distinto (`com.google.android.youtube.tv`) que ningún set de parches mantenido apunta. ReVanced declinó explícitamente el soporte de TV (revanced-manager #3385) y redirige a SmartTube. |
| **(B) Cliente propio desde cero** | Construir un cliente que habla con la API interna **InnerTube** de YouTube | **Vía elegida.** Sin anuncios *por construcción*: nunca ejecutamos el reproductor oficial de Google, así que simplemente ignoramos los campos `adPlacements` / `playerAds` que devuelve InnerTube. |

El bloqueo por DNS no es una alternativa válida para YouTube: los anuncios se sirven desde el mismo CDN `googlevideo` que el contenido, por lo que bloquear DNS rompe también la reproducción. Tampoco se usa MITM ni microG.

---

## 2. Objetivo del producto

Construir **YouTroc**: un cliente de YouTube para Android TV, sin anuncios, que **replique la experiencia del cliente oficial de YouTube TV** (experiencia completa), pensado para uso personal vía sideload. No es una app para tiendas.

Se trata como un proyecto serio y grande, con la expansión futura abierta. El objetivo de aprendizaje principal y el diferenciador de producto es construir un **extractor InnerTube propio** desde cero.

---

## 3. Usuario objetivo

El **único usuario y dueño es el propio desarrollador**. No hay otros perfiles de usuario, ni multiusuario, ni soporte público.

| Atributo | Valor |
|---|---|
| Quién | El propio dueño/desarrollador |
| Dónde | Su televisor **TCL 55C6K (2025)** |
| Cómo se distribuye | Sideload directo al C6K (trivial para el dueño) |
| Cómo se interactúa | Mando a distancia / D-pad de Google TV con convenciones estándar de Android TV (foco, OK selecciona, BACK retrocede, controles propios de play/pausa/seek durante la reproducción; sin atajos personalizados en Fase 1) |

### Hardware objetivo (único dispositivo conocido)

El TCL 55C6K es el **suelo (floor)**: nada por debajo importa. Tener un único objetivo moderno libera al proyecto del lastre de compatibilidad universal de SmartTube y nos permite usar el stack más moderno.

| Componente | Especificación |
|---|---|
| SoC | MediaTek Pentonic 700 |
| RAM | 3 GB |
| ROM | 32 GB |
| SO | Google TV (moderno) |
| Pantalla | 4K 144 Hz, QD-Mini LED, Dolby Vision IQ |
| Decodificación | **AV1 por hardware** (también VP9 y H.264) |

---

## 4. La DECISIÓN RAÍZ de ideología

> **YouTroc se construye como un cliente propio desde cero (vía B), no como un parche de la app oficial (vía A).**

Esta decisión fue verificada mediante investigación adversarial y es la raíz de todo el proyecto.

**Por qué patchear NO sirve en TV:**

- ReVanced/Morphe parchean **únicamente el paquete de móvil** (`com.google.android.youtube`).
- La app de YouTube para TV es un **paquete Leanback separado** (`com.google.android.youtube.tv`) que **ningún set de parches mantenido apunta**.
- ReVanced **declinó explícitamente** el soporte de TV (revanced-manager #3385) y remite a los usuarios a SmartTube.

**Qué implica elegir la vía B (cliente propio):**

- **Sin anuncios por construcción:** nunca corremos el reproductor oficial de Google. Hablamos directamente con InnerTube y **ignoramos** los campos `adPlacements` / `playerAds`. No hay un anuncio que "saltar"; simplemente nunca entra en el flujo.
- **No DNS blocking** (falla porque los anuncios vienen del mismo CDN `googlevideo` que el contenido).
- **No MITM.**
- **No microG.** microG es irrelevante para este proyecto: solo importa para el login de apps *parcheadas*; aquí no parcheamos nada.

---

## 5. Propuesta de valor

> **Superar a SmartTube usando el stack más moderno, gracias a tener un único objetivo de hardware.**

SmartTube carga con el lastre de la compatibilidad universal (debe correr en TVs viejas y dispares). YouTroc tiene un solo objetivo conocido (el C6K, hardware moderno de 2025), lo que permite adoptar deliberadamente el stack más actual sin concesiones de compatibilidad.

| Palanca | Cómo la aprovecha YouTroc |
|---|---|
| UI | **Jetpack Compose for TV** (`androidx.tv` tv-material + Compose foundation lazy lists). Leanback está deprecado oficialmente. En 3 GB el delta de RAM/GC de Compose es negligible. |
| Reproductor | **Media3/ExoPlayer** como motor de reproducción (infraestructura genérica, no se reimplementa); UI de controles **propia** en Compose. |
| Decodificación | **AV1/VP9 por hardware** del Pentonic 700, luego H.264; `MediaCodecSelector` hardware-first. |
| Rendimiento | **R8 full mode + Baseline Profile específico de la app** como práctica obligatoria desde el día uno. |
| Diferenciador | **Extractor InnerTube propio** detrás del puerto `StreamProvider` — el corazón anti-ads, intercambiable por motor. |

**Principio rector:** somos el **mejor CLIENTE**, no un "mejor YouTube". Dependemos de la API InnerTube de YouTube y del CDN `googlevideo`. La velocidad es cuestión de **cuellos de botella entendidos**, no del lenguaje elegido: las palancas reales (selección de códec, el presupuesto de 16 ms por frame, el presupuesto de memoria, Baseline Profiles + R8) son agnósticas al lenguaje. Por eso el stack es **100% Kotlin** (sin Rust, sin código nativo): la decodificación la hace el hardware del SoC vía MediaCodec, no el lenguaje de la app, y no existe un bucle de cómputo caliente que acelerar.

---

## 6. Alcance IN — qué hace la app

Lo que YouTroc **sí** hace (a través de sus fases):

- **Reproducción sin anuncios** de videos de YouTube, resolviendo streams reproducibles vía InnerTube e ignorando los campos de anuncios (`StreamProvider`).
- **Home / Trending** (catálogo de feeds) — `VideoCatalog`.
- **Búsqueda** de videos — `VideoSearch`.
- **Detalle de video** y reproducción con controles propios — `MediaPlayer`.
- **Videos relacionados (read-only)** en el detalle, como **única adición de navegación** de Fase 1.
- **Combinación de streams DASH** separados (audio-only + video-only) de YouTube vía `MergingMediaSource`.
- **Sesión/identidad** desde el día 1 a través del puerto `SessionGateway`:
  - **Fase 1: anónima** (sin login): home = trending, búsqueda, detalle + reproducción sin ads y **videos relacionados en modo read-only**.
  - **Fase 2: con cuenta** vía **OAuth 2.0 device-authorization** (estilo SmartTube, el flujo de "introduce el código en `youtube.com/activate`", **sin microG**): home personalizada, suscripciones, historial remoto de la cuenta, acciones de cuenta (like, suscribir, añadir a playlist) y SponsorBlock.
- **Persistencia local** (Room y/o DataStore) **solo** para ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo / watch progress (`WatchProgressStore`, `PlaybackPosition`). No hay lista completa de historial local; el historial remoto es de Fase 2. Nada sale del dispositivo.
- **UI con Compose for TV** y controles de reproductor construidos sobre el puerto `MediaPlayer`.

### Núcleo anti-ads

El puerto `StreamProvider` resuelve streams reproducibles **ignorando los campos de anuncios**. Es intercambiable por motor:

- Arranca el vertical slice con **NewPipeExtractor** como **scaffold/baseline** detrás del puerto `StreamProvider`.
- En paralelo se construye el **motor propio** detrás del mismo puerto.
- Se reemplaza el scaffold cuando el motor propio iguala a la referencia.

---

## 7. Alcance OUT / No-Goals — qué NO hace la app

YouTroc **explícitamente NO** hace lo siguiente:

| No-Goal | Razón |
|---|---|
| **No es para app stores** | Uso personal vía sideload al C6K. No hay publicación en Google Play ni en ninguna tienda. |
| **No patching** | No parchea la app oficial de YouTube (ni móvil ni TV). Es un cliente desde cero. |
| **No "mejor que YouTube" a nivel infraestructura** | Somos un CLIENTE; dependemos de la API InnerTube y del CDN `googlevideo`. No competimos con la infraestructura de YouTube. |
| **No soporta hardware por debajo del C6K** | El TCL 55C6K es el suelo. No hay compatibilidad universal estilo SmartTube. |
| **No DNS blocking** | Falla para YouTube: los anuncios vienen del mismo CDN que el contenido. |
| **No MITM** | No interceptamos tráfico TLS. |
| **No microG** | Irrelevante: solo aplica a apps parcheadas. YouTroc no parchea nada. |
| **No reimplementa el motor de reproducción** | Media3/ExoPlayer es infraestructura genérica y profundamente trabajada. Solo construimos la UI de controles. |
| **No reimplementa la decodificación** | La decodificación la hace el hardware del SoC vía MediaCodec; nunca es nuestra. |
| **No multiusuario / no soporte público** | Un único usuario: el dueño. |

---

## 8. Criterios de éxito

### Hipótesis del vertical slice (prueba del día 1)

> **Dado** un `videoId` hardcodeado (sin UI), **cuando** se resuelve vía InnerTube y se reproduce con Media3, **entonces** el video se reproduce **sin anuncios** en el TCL 55C6K.

Esto prueba la **hipótesis central** del proyecto el primer día: que el cliente propio puede obtener y reproducir contenido sin pasar por el reproductor oficial.

### Escenarios de aceptación (Dado / Cuando / Entonces)

**Reproducción sin anuncios**

- **Dado** un video de YouTube con `adPlacements`/`playerAds` en la respuesta de InnerTube,
- **Cuando** YouTroc resuelve el stream vía `StreamProvider`,
- **Entonces** esos campos de anuncios se ignoran y la reproducción no contiene anuncios.

**Reproducción fluida en el C6K**

- **Dado** un video disponible en AV1/VP9,
- **Cuando** se reproduce en el TCL 55C6K,
- **Entonces** la decodificación se hace por **hardware** (Pentonic 700) priorizando AV1/VP9 y luego H.264, con `SurfaceView`, respetando el presupuesto de 16 ms por frame.

**Combinación de streams DASH**

- **Dado** que YouTube entrega audio-only y video-only por separado,
- **Cuando** se prepara la reproducción,
- **Entonces** se combinan vía `MergingMediaSource` en una sola sesión reproducible.

### Métricas de éxito (cualitativas para uso personal)

| Criterio | Definición de éxito |
|---|---|
| **Sin anuncios** | Cero anuncios en reproducción, por construcción, en el C6K. |
| **Fluidez** | Reproducción y navegación con D-pad fluidas en el hardware objetivo (decodificación HW, R8 + Baseline Profile aplicados). |
| **Vertical slice** | El slice (videoId hardcodeado → InnerTube → Media3, sin ads) funciona en el C6K. |
| **Motor propio** | El extractor InnerTube propio iguala a la referencia NewPipe y reemplaza al scaffold. |

---

## 9. Resumen de fases (alto nivel)

```
START (vertical slice)  ->  FASE 1 (anónima)  ->  FASE 2 (con cuenta)
```

| Fase | Qué entrega | Sesión | Motor de extracción | UI |
|---|---|---|---|---|
| **START — Vertical slice** | `videoId` hardcodeado → InnerTube → reproducción con Media3, sin ads. **Sin UI.** Prueba la hipótesis central el día 1. | — | NewPipe scaffold | — |
| **FASE 1 — Anónima** | Home (trending) + búsqueda + detalle + reproducción sin ads + **videos relacionados (read-only)** + **continuar viendo (local)**. Sin login. | Anónima (`SessionGateway`) | Motor propio con NewPipe como scaffold/red de seguridad | Compose for TV |
| **FASE 2 — Con cuenta** | Home personalizada, suscripciones, historial remoto, acciones de cuenta (like/suscribir/añadir a playlist), SponsorBlock; además páginas de canal, playlists, suscripciones-sin-login y Shorts con UI dedicada (Fase 2+). | OAuth device-code (sin microG) | Motor propio | Compose for TV |

**Nota de riesgo de cuenta:** el riesgo de baneo de cuenta aplica **solo** estando logueado (Fase 2). Recomendación: usar una cuenta de Google "quemable" (burner).

**Frontera exacta de funcionalidades de Fase 1 (decisión confirmada):** en **Fase 1** entra únicamente, como adición de navegación, **videos relacionados en modo read-only**; los datos extra son **solo locales** (ajustes, favoritos y continuar viendo). Quedan **diferidos a Fase 2 o posterior**: **páginas de canal, playlists, suscripciones-sin-login, Shorts** (si aparecen en feeds se tratan como videos normales, sin UI dedicada), **historial remoto** y **acciones de cuenta**. **Suscripciones, historial remoto y acciones de cuenta (like, suscribir, añadir a playlist), además de SponsorBlock,** requieren cuenta y van en **Fase 2**.

---

## 10. Arquitectura (resumen de alto nivel)

Solo como contexto del alcance; el detalle vive en el documento de arquitectura.

- **Multi-módulo Gradle** (fronteras forzadas por el compilador) + **Screaming Architecture** (organizado por dominio/feature) + **Hexagonal** (ports & adapters) + **Dependency Rule** de Clean Architecture (las dependencias de código apuntan HACIA ADENTRO, al dominio).
- Módulos: `:app` (composition root / DI), `:core:domain` (Kotlin puro, modelos + puertos), `:core:ui` (componentes Compose for TV compartidos), `:feature:catalog` / `:feature:search` / `:feature:video` / `:feature:playback` / `:feature:session` (dependen solo de `:core:domain` y `:core:ui`), y `:data:extraction` / `:data:player` / `:data:session` / `:data:persistence` (implementan los puertos).
- Puertos de dominio: `StreamProvider`, `VideoCatalog`, `VideoSearch`, `MediaPlayer`, `SessionGateway`, más los puertos de persistencia `SettingsStore`, `FavoritesStore` y `WatchProgressStore` (declarados en `:core:domain`, implementados por `:data:persistence`).
- Puertos/adaptadores se aplican **solo donde hay volatilidad real o valor de aislamiento para testing** (extraction, player, session, persistence), **no dogmáticamente en todo**.

---

## 11. Riesgo principal

El **extractor InnerTube propio es el riesgo #1** del proyecto: PoToken, resolución de n-signature, BotGuard, visitor-data y bloqueos de tipo "not a bot", con mantenimiento casi de guardia. **Mitigación:** el scaffold de **NewPipeExtractor** detrás del mismo puerto `StreamProvider` es la red de seguridad mientras el motor propio madura.

---

## 12. No-funcionales (Decisión #7 — confirmada / cerrada)

> Decisión #7 **confirmada y cerrada**: el bloque no-funcional queda decidido tal como se enumera.

- **TDD activo:** el dominio se prueba con fakes detrás de los puertos.
- **Persistencia local** (Room/DataStore) **solo** para ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore` / `PlaybackPosition`); sin historial local completo.
- **Distribución** = sideload al C6K.
- **Mantenimiento:** el motor propio es el riesgo #1; el scaffold de NewPipe es la red de seguridad.
- **Metas numéricas (confirmadas):** arranque en frío <= 2.5 s a home interactivo; primer frame de video <= 1.5 s; PSS <= 300 MB sobre 3 GB (objetivo blando); jank en scroll < 1% de frames; APK release modesto frente a 32 GB de ROM. Medir SIEMPRE en el C6K real con R8 full + Baseline Profile.

---

## 13. Decisiones confirmadas (resueltas el la fecha de confirmación)

Los siguientes detalles de negocio quedaron **decididos y cerrados** en la fecha de confirmación. Ya no son asunciones:

1. **Frontera exacta de Fase 1 vs Fase 2:** Fase 1 añade **solo** videos relacionados (read-only); páginas de canal, playlists, suscripciones-sin-login y Shorts quedan en **Fase 2 o posterior**. Suscripciones, historial remoto y acciones de cuenta van en **Fase 2**. Los datos extra de Fase 1 son solo locales (ajustes, favoritos, continuar viendo).
2. **Política de calidad:** ABR (adaptive bitrate) automático, acotado al hardware del C6K, con preferencia de códec **AV1 → VP9 → H.264**. Sin selector manual de calidad en la primera iteración de Fase 1 (se evalúa después).
3. **Integración con SponsorBlock:** **Sí, pero en Fase 2.** El dominio modela los segmentos saltables (p. ej. `SponsorSegment`) desde ya; la integración/adaptador se entrega en Fase 2. No en Fase 1 ni en el vertical slice.
4. **Subtítulos/captions:** **Diferidos** — no en el vertical slice ni en la Fase 1 inicial. El dominio **modela** `captionTracks` / `CaptionTrack`, que pueden quedar vacíos hasta implementar el soporte real más adelante.
5. **Idioma/región InnerTube:** `hl=es` (español) y `gl`/región heredada del dispositivo.
6. **Fallo de extracción / resultados vacíos / sin red:** `StreamProvider` (y los demás puertos de repositorio) exponen un **resultado tipado (sealed)** que distingue éxito / fallo-de-extracción / vacío / sin-red. La UI muestra un estado explícito de error/empty/offline con mensaje claro y acción de reintento; la app permanece estable, nunca crashea ni queda en blanco.
7. **Contenido restringido / age-gated:** En modo anónimo (Fase 1) no se garantiza; manejo **best-effort** y, si no es resoluble, mensaje explícito sin disrupción. La resolución real puede requerir cuenta (Fase 2).
8. **Datos locales en Fase 1 anónima:** **Solo** ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore` / `PlaybackPosition`). No hay historial local completo; nada sale del dispositivo.
9. **Interacción con mando / D-pad:** Convenciones estándar de Android TV / Google TV — foco, OK selecciona, BACK retrocede, controles propios de play/pausa/seek durante la reproducción. Sin atajos personalizados en Fase 1.
10. **"Continuar viendo" (local) en Fase 1:** **Sí**, mediante `WatchProgressStore` (`PlaybackPosition`), almacenado solo localmente.
11. **Sugerencias/autocompletado de búsqueda:** En Fase 1 solo si el costo es bajo; si no, diferidas. El historial de búsqueda se asocia a la cuenta en Fase 2 (no local en Fase 1).
