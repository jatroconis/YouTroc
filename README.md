# youtroc

Cliente de **YouTube para Android TV**, sin anuncios *por construcción*, escrito desde cero en Kotlin. Es un proyecto **personal y educativo**: su objetivo real es aprender arquitectura de software (Clean / Hexagonal / Screaming Architecture, TDD, Compose for TV) construyendo un producto de verdad, no distribuir una app.

---

## ⚠️ Aviso legal y de alcance

- **Proyecto personal y educativo.** No está afiliado, asociado ni respaldado por YouTube, Google o sus marcas.
- **Infringe los Términos de Servicio de YouTube.** Se ejecuta únicamente en el dispositivo del autor, sin fines de distribución ni comerciales.
- **Un solo dispositivo objetivo:** TCL 55C6K (SoC MediaTek Pentonic 700, 3 GB RAM, AV1 por hardware). Es el piso y el techo del diseño; no se busca compatibilidad amplia.
- **Distribución por sideload** (`adb install`). No hay build de release firmada ni publicación en tiendas.
- Repositorio **privado**. No redistribuir.

---

## Qué es

youtroc replica la experiencia del YouTube oficial para TV (rail lateral colapsable, shelves, reproductor propio a pantalla completa) pero **sin anuncios por construcción**: la extracción se hace con un motor tipo InnerTube (andamiado sobre NewPipeExtractor) que directamente **no deserializa** los `adPlacements` / `playerAds`, así que no hay anuncios que saltar — simplemente no existen en el modelo de datos.

La **regla de oro** del producto: comportarse como el YouTube real. Un click en una tarjeta reproduce directo (sin pantalla intermedia de confirmación), la información y los relacionados viven *dentro* del reproductor, y los streams en vivo se reproducen igual que los VOD.

## Estado actual — FASE 1 (anónima) feature-complete a nivel de código

| Área | Estado |
|---|---|
| Catálogo Trending (Home) | ✅ real, vía extracción anónima |
| Búsqueda | ✅ con teclado en pantalla |
| Reproducción VOD (DASH/progresivo, ABR) | ✅ validado en el TCL |
| Reproducción **en vivo** (HLS / DASH-live) | ✅ validado en el TCL |
| Selector de **calidad** (⚙ → Ajustes → Calidad) | ✅ code-complete |
| Panel **"A continuación"** dentro del player | ✅ code-complete |
| Continuar viendo (posición local) | ✅ vía `WatchProgressStore` |

> Fase 2 (con cuenta / OAuth device-code, páginas de canal, suscripciones, SponsorBlock, subtítulos) está fuera de alcance por ahora. Ver [`docs/05-roadmap-and-risks.md`](./docs/05-roadmap-and-risks.md).

---

## Arquitectura

Multi-módulo Gradle bajo **Screaming + Hexagonal + Clean Architecture**, con la regla de dependencias apuntando siempre hacia `:core:domain`. El dominio es Kotlin/JVM puro (cero Android, cero librerías de framework); los adaptadores concretos (extracción, reproductor, persistencia) implementan puertos del dominio, y las features solo hablan con esos puertos — nunca con los adaptadores.

```
:app  ──────────────► composition root (DI manual, navegación)
  │
  ├─ :feature:catalog ─┐
  ├─ :feature:search  ─┼─► dependen SOLO de :core:domain (+ :core:ui)
  └─ :feature:playback ┘     (nunca de :data:*)
  │
  ├─ :data:extraction ─┐
  ├─ :data:player     ─┼─► implementan puertos de :core:domain
  └─ :data:persistence ┘
  │
  ├─ :core:domain ────► el hexágono: entidades, value objects, puertos, use cases (Kotlin/JVM puro)
  └─ :core:ui ────────► design system Compose for TV (átomos/moléculas; no conoce dominio ni data)
```

### Mapa de módulos (real, actual)

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `:core:domain` | Kotlin/JVM puro | El corazón del hexágono: `VideoId`, `Video`, `Stream`, `PlayableStreams`, `PlaybackManifest`, `PlaybackState`, `VideoQuality`, `VideoDetail`… + puertos (`StreamProvider`, `MediaPlayer`, `WatchProgressStore`, `VideoDetail`) y use cases. |
| `:core:ui` | Android lib | Design system Compose for TV: theme, tokens, `VideoCardUi`, `ShelfRow`, logo. Sin dominio ni data. |
| `:data:extraction` | Kotlin/JVM | Adaptador de extracción sobre **NewPipeExtractor**. Detección VOD/live, ensamblado DASH, selección de streams. Ad-free por construcción. Aquí vive el riesgo #1 del proyecto. |
| `:data:player` | Android lib | Adaptador del puerto `MediaPlayer` sobre **Media3/ExoPlayer** (DASH, HLS, MergingMediaSource, selección de calidad). Nunca depende de `:data:extraction`. |
| `:data:persistence` | Android lib | Adaptador de `WatchProgressStore` sobre **DataStore Preferences**. Local-only. |
| `:feature:playback` | Android lib | Overlay del reproductor hecho a mano en Compose for TV (transporte, scrubber, menú Ajustes→Calidad, indicador EN VIVO, panel "A continuación") + ViewModels. Solo puertos. |
| `:feature:catalog` | Android lib | Container + presentacional del shelf Trending del Home. |
| `:feature:search` | Android lib | Container + presentacional de la búsqueda. |
| `:app` | Android app | Composition root: DI manual (`viewModelFactory`, sin Hilt), navegación (Navigation Compose), y el cableado puerto→adaptador. |

Patrones transversales: **container-presentational** (ViewModel + `StateFlow<UiState>` + composable presentacional), `sealed UiState`, atomic design, DI manual por factories, y una disciplina fuerte de **gestión de foco D-pad** (el riesgo recurrente #1 de UI en TV).

---

## Stack técnico

- **Kotlin** 100% · Gradle multi-módulo con version catalog (`gradle/libs.versions.toml`).
- **Jetpack Compose for TV** (`androidx.tv:tv-material3`) — sin Leanback.
- **Media3 / ExoPlayer** — DASH adaptativo, HLS (live), `SurfaceView`, `DefaultTrackSelector`.
- **NewPipeExtractor** (publicado en JitPack, no en Maven Central) como andamio de extracción.
- **DataStore Preferences** para persistencia local.
- **Navigation Compose** · **Coil 3** para thumbnails · DI manual (sin Hilt).

---

## Cómo compilar y ejecutar

Requisitos: JDK 17+, Android SDK (compileSdk 35, minSdk 26), y un dispositivo/emulador Android TV. El único objetivo soportado de verdad es el TCL 55C6K.

```bash
# Compilar el APK debug
./gradlew :app:assembleDebug

# Instalar por sideload en el TV (ADB sobre red)
adb connect <ip-del-tv>:5555
adb -s <ip-del-tv>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` (ruta del SDK) es local y está fuera del control de versiones.

## Tests

```bash
./gradlew test          # todos los módulos
./gradlew :core:domain:test :data:extraction:test
```

El dominio se testea con **TDD** (fakes detrás de los puertos). La UI Compose (foco, D-pad, scroll) se valida en el dispositivo real, no con tests unitarios — misma convención en todo el proyecto. Hay una prueba de integración *opt-in* de live tras `YOUTROC_LIVE=1` (golpea la red real, por eso no corre por defecto).

---

## Documentación

La suite completa de producto y arquitectura vive en [`docs/`](./docs/README.md):

| # | Documento |
|---|---|
| 00 | [Visión y Alcance](./docs/00-vision-and-scope.md) |
| 01 | [Registro de Decisiones (ADR)](./docs/01-business-decisions-adr.md) |
| 02 | [Requerimientos Funcionales](./docs/02-functional-requirements.md) |
| 03 | [Requerimientos No Funcionales](./docs/03-non-functional-requirements.md) |
| 04 | [Arquitectura y Diseño](./docs/04-architecture-and-design.md) |
| 05 | [Roadmap y Riesgos](./docs/05-roadmap-and-risks.md) |
| 06 | [Glosario y Modelo de Dominio](./docs/06-glossary-and-domain-model.md) |
| 07 | [Diseño de UI](./docs/07-ui-design.md) |

> Nota: la suite de `docs/` describe la visión de diseño completa. Algunos detalles de implementación evolucionaron respecto del plan original — por ejemplo, la pantalla de detalle independiente (`:feature:video`) se **re-homeó** dentro del reproductor como panel "A continuación", siguiendo la regla de oro de replicar el YouTube real. El mapa de módulos de este README refleja el estado **actual** del código.
