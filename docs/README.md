# YouTroc — Suite de Especificación

> Índice maestro de la documentación de producto y arquitectura de **YouTroc**: un cliente de YouTube para Android TV, sin anuncios *por construcción*, construido desde cero en Kotlin para el único dispositivo objetivo TCL 55C6K.

---

## Tabla de contenidos

| # | Documento | Qué contiene |
|---|---|---|
| 00 | [Visión y Alcance](./00-vision-and-scope.md) | El PRD/Product Brief: el problema (ads en YouTube TV), la decisión raíz de ideología (cliente propio vía B, no patching), la propuesta de valor frente a SmartTube, alcance IN/OUT, criterios de éxito y resumen de fases START / Fase 1 / Fase 2. |
| 01 | [Registro de Decisiones de Negocio (ADR)](./01-business-decisions-adr.md) | Las decisiones raíz (ADR-0 a ADR-9 + #7 no-funcional) con Contexto, Decisión, Razón, Consecuencias, Alternativas y Evidencia verificada, incluido ADR-9 (#8) que cierra el alcance de Fase 1 y los detalles de producto. Fuente de verdad de las decisiones. |
| 02 | [Requerimientos Funcionales](./02-functional-requirements.md) | QUÉ hace la app, por módulo de feature (`:feature:catalog`, `:feature:search`, `:feature:video`, `:feature:playback`, `:feature:session`) y por fase, con IDs estables (`RF-*`) y criterios Dado/Cuando/Entonces. |
| 03 | [Requerimientos No Funcionales (NFR)](./03-non-functional-requirements.md) | CÓMO debe comportarse: rendimiento, fiabilidad, mantenibilidad, seguridad/privacidad, testing TDD, observabilidad, distribución y disciplina de build, cuantificados contra el TCL 55C6K con IDs `RNF-*`. |
| 04 | [Arquitectura y Diseño Técnico](./04-architecture-and-design.md) | El grafo de módulos Gradle, la regla de dependencias hacia `:core:domain`, la tabla puerto → adaptador, el mecanismo anti-ads por construcción, el flujo de reproducción y la config de Media3 / Compose for TV. |
| 05 | [Roadmap y Registro de Riesgos](./05-roadmap-and-risks.md) | El plan incremental por riesgo (Hito 0 vertical slice → Fase 1 anónima → Fase 2 con cuenta) y la tabla de 7 riesgos encabezada por la rotura de extracción (riesgo #1). |
| 06 | [Glosario y Modelo de Dominio](./06-glossary-and-domain-model.md) | El glosario técnico (InnerTube, PoToken, n-signature, DASH, etc.) y el lenguaje ubicuo de `:core:domain`: entidades y value objects (`VideoId`, `Video`, `Stream`, `PlayableStreams`, `Session`, `PlaybackPosition`, …). |
| 07 | [Diseño de UI](./07-ui-design.md) | El plan de UI que replica el YouTube-for-TV moderno (rail izquierdo colapsable + hero inmersivo hand-built + shelves + player propio) en Compose for TV: realidades 2026 de `androidx.tv`, wireframes, mapeo componente → `androidx.tv`, atomic design + container-presentational, alcance de UI de Fase 1 y guardas de rendimiento. |

---

## ¿Cómo leer estos documentos?

El orden recomendado va de la intención al detalle:

1. Empieza por **00 — Visión y Alcance** para entender el *porqué* y el *qué* a alto nivel.
2. Sigue con **01 — ADR** para conocer cada decisión raíz con su evidencia: es la fuente de verdad. Si un documento posterior contradice un ADR, gana el ADR (o se abre una pregunta).
3. Consulta **06 — Glosario y Modelo de Dominio** en cualquier momento como referencia de vocabulario; conviene tenerlo abierto al leer los requerimientos.
4. Profundiza con **02 — Funcionales** y **03 — No Funcionales** para el QUÉ y el CÓMO medibles.
5. Cierra con **04 — Arquitectura**, **07 — Diseño de UI** y **05 — Roadmap y Riesgos** para llevar las decisiones a estructura de código, a la interfaz y a un plan de entrega.

Convención compartida por toda la suite: los detalles de negocio que **las DECISIONS de origen no cubrían** se marcaron en su momento como **`(Asunción — a confirmar)`** con un default PRD razonable. En la fecha de confirmación el dueño cerró **TODAS** esas asunciones; ya no quedan huecos abiertos. Cada documento conserva la decisión afirmada inline y la recopila al final en su sección *Decisiones confirmadas*. No se inventan decisiones de negocio.

---

## Estado de decisiones

### Decisiones cerradas (Aceptadas)

| Decisión | Resumen | Documento(s) |
|---|---|---|
| **Ideología (raíz)** | Cliente propio desde cero (vía B), NO patching de la app oficial. Ad-free por construcción ignorando `adPlacements`/`playerAds`. | [01 · ADR-0](./01-business-decisions-adr.md) |
| **Target de hardware** | Único dispositivo: TCL 55C6K (MediaTek Pentonic 700, 3 GB RAM, AV1 por hardware). Es el piso y el techo. | [01 · ADR-1](./01-business-decisions-adr.md) |
| **Lenguaje** | Kotlin 100%; Rust y código nativo descartados (la decodificación la hace el SoC, no el lenguaje). | [01 · ADR-8](./01-business-decisions-adr.md) |
| **#1 — Producto** | Réplica de la experiencia oficial de YouTube TV (experiencia completa). | [01 · ADR-2](./01-business-decisions-adr.md) |
| **#2 — Cuenta/sesión** | `SessionGateway` desde el día 1. Fase 1 anónima; Fase 2 OAuth device-code (sin microG). | [01 · ADR-3](./01-business-decisions-adr.md) |
| **#3 — Arquitectura** | Multi-módulo Gradle + Screaming + Hexagonal + Dependency Rule hacia `:core:domain`. | [01 · ADR-4](./01-business-decisions-adr.md) |
| **#4 — Extracción** | Motor InnerTube propio detrás de `StreamProvider`, con andamio NewPipeExtractor (estrategia strangler-fig). | [01 · ADR-5](./01-business-decisions-adr.md) |
| **#5 — UI** | Jetpack Compose for TV; Leanback descartado. R8 full mode + Baseline Profile day-one. | [01 · ADR-6](./01-business-decisions-adr.md) |
| **#6 — Reproductor** | Media3/ExoPlayer como motor; controles propios en Compose. SurfaceView, AV1/VP9→H.264, MergingMediaSource. | [01 · ADR-7](./01-business-decisions-adr.md) |
| **#7 — No funcionales** | **Cerrada.** TDD activo (dominio con fakes detrás de los puertos), persistencia local Room/DataStore solo para ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore`), distribución por sideload, motor propio como riesgo #1 con NewPipe como red de seguridad. Metas NFR confirmadas (arranque ≤ 2.5 s, primer frame ≤ 1.5 s, PSS ≤ 300 MB, jank < 1%). | [01 · Decisiones no funcionales](./01-business-decisions-adr.md), [03 · NFR](./03-non-functional-requirements.md) |
| **#8 — Alcance de Fase 1 y detalles de producto** | **Cerrada (ADR-9).** Frontera Fase 1/Fase 2, datos locales, política de calidad ABR, SponsorBlock, captions, idioma InnerTube, manejo de errores, age-gated, D-pad y metas NFR. | [01 · ADR-9](./01-business-decisions-adr.md) |
| **#9 — Diseño de UI** | **Cerrada.** Look YouTube-for-TV moderno (rail izquierdo colapsable + hero inmersivo **hand-built** + shelves + player propio) en Compose for TV; atomic design + container-presentational + `sealed UiState`. `Carousel` descartado para el hero (API experimental). | [07 · Diseño de UI](./07-ui-design.md) |
| **ADR-10 — Pivote a multi-target + open-source** | YouTroc pasa a **open-source (GPL-3.0-or-later)** y a target **multi-target Android TV**; el TCL 55C6K queda como dispositivo de referencia/baseline. | [01 · ADR-10](./01-business-decisions-adr.md) |

### Detalles de producto (cerrados en la fecha de confirmación)

Todos los detalles que antes atravesaban la suite como asunciones quedaron **decididos y cerrados** (ADR-9 / Decisión #8). Ya no bloquean nada; la frontera de Fase 1 está fijada. Su resolución vive, por documento, en las secciones *Decisiones confirmadas* / *Preguntas resueltas*:

- **Frontera Fase 1 vs Fase 2** → Fase 1 añade solo **videos relacionados read-only**; páginas de canal, playlists, suscripciones-sin-login, Shorts (sin UI dedicada), historial remoto y acciones de cuenta van a **Fase 2 o posterior**.
- **Política de calidad/resolución** → **ABR automático** acotado al hardware del C6K (AV1 → VP9 → H.264), **sin selector manual** en la primera iteración de Fase 1.
- **SponsorBlock** → **Sí, en Fase 2**; el dominio modela `SponsorSegment` desde ya.
- **Subtítulos/captions** → **diferidos**; el dominio modela `captionTracks` / `CaptionTrack` (posiblemente vacíos).
- **Idioma/región InnerTube** → `hl=es` + `gl`/región heredada del dispositivo.
- **Fallo de extracción / vacío / sin red** → **resultado tipado (sealed)** en los puertos; UI error/empty/offline con reintento, app estable.
- **Contenido restringido / age-gated** → best-effort en anónimo; puede requerir cuenta (Fase 2).
- **Datos locales en Fase 1 anónima** → solo ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore` / `PlaybackPosition`); nada sale del dispositivo; sin historial local.
- **Interacción con mando / D-pad** → convenciones estándar de Android TV / Google TV; sin atajos personalizados en Fase 1.
- **"Continuar viendo" (local) en Fase 1** → **Sí**, vía `WatchProgressStore`.
- **Objetivos numéricos de los NFR** → arranque ≤ 2.5 s, primer frame ≤ 1.5 s, PSS ≤ 300 MB (blando), jank < 1%, APK modesto; medidos en el C6K real con R8 full + Baseline Profile.

> Nota de consistencia: las contradicciones puntuales que existían entre documentos (frontera de Fase 1, "continuar viendo", subtítulos y selector manual de calidad) quedaron **resueltas y unificadas** en toda la suite en la fecha de confirmación.
