# YouTroc

Cliente **open-source de YouTube para Android TV**, sin anuncios *por construcción*, escrito desde cero en Kotlin con Clean / Hexagonal / Screaming Architecture, Jetpack Compose for TV y Media3.

Es un **proyecto open-source serio**, construido desde cero con arquitectura hexagonal estricta y un stack moderno, y desarrollado a la vista de todos. En la línea de NewPipe o SmartTube, pero apostando a un código limpio, modular y testeable como base para crecer hacia una alternativa real.

> **Estado: `alpha` (v0.1.0-alpha).** Reproduce videos VOD y streams en vivo de verdad, pero es software temprano: varias funciones están completas a nivel de código pero aún sin validar en dispositivo, y hasta ahora solo se probó en un equipo (ver [Dispositivos](#dispositivos-probados)). Se agradecen reportes y pruebas en otros Android TV.

---

## Aviso legal

YouTroc **no está afiliado, asociado ni respaldado** por YouTube, Google o sus marcas: es un **cliente no oficial**. Acceder a YouTube por medios no oficiales **puede infringir sus Términos de Servicio**; se distribuye sin garantías y cada quien es responsable del uso que le dé. Ad-free *por construcción*: nunca ejecuta el reproductor oficial de Google, así que simplemente ignora los campos de anuncios que devuelve la API interna — no hay bloqueo por DNS, MITM ni parcheo de apps.

---

## Qué es

YouTroc replica la experiencia del YouTube oficial para TV (rail lateral colapsable, shelves, reproductor propio a pantalla completa) **sin anuncios**. La **regla de oro** del producto: comportarse como el YouTube real — un click en una tarjeta reproduce directo (sin pantalla intermedia), la info y los relacionados viven *dentro* del reproductor, y los streams en vivo se reproducen igual que los VOD.

La extracción corre sobre un **motor InnerTube propio**, migrado por capas *(strangler-fig)*: búsqueda, detalle de video, el feed regional del Home ("Popular en {región}") y la extracción de streams VOD (cliente `ANDROID_VR`, sin cipher ni PoToken, ensamblados en un DASH MPD propio) ya corren con motor propio, con [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) como *fallback* automático si el motor propio falla. Los streams en vivo todavía usan NewPipe. Ninguno de los dos motores **deserializa** los `adPlacements` / `playerAds`: no hay anuncios que saltar porque no existen en el modelo de datos.

## Estado de las funciones

| Función | Código | Validado en dispositivo |
|---|---|---|
| Home **multi-estantería** (Seguir viendo, Tendencias regional, temáticas, En vivo, Shorts; carga progresiva) | ✅ | ✅ (C6K) |
| **"Seguir viendo"** (historial local, solo en el dispositivo) | ✅ | ✅ (C6K) |
| Búsqueda con teclado en pantalla | ✅ | ✅ (C6K) |
| Reproducción VOD (DASH/progresivo, ABR) | ✅ | ✅ (C6K) |
| Reproducción **en vivo** (HLS / DASH-live) | ✅ | ✅ (C6K) |
| Player estilo Netflix: scrub desacoplado + **preview de frames** (storyboards) | ✅ | ✅ (C6K) |
| Selector de **calidad** (⚙ → Ajustes → Calidad) + recuperación ante 403 | ✅ | ✅ (C6K) |
| Panel **"A continuación"** dentro del player | ✅ | ✅ (C6K) |
| **Shorts** (fila + reproductor vertical con pager D-pad) | ✅ | ⏳ fila ✅ / player pendiente |
| Continuar viendo (posición local, reanudar) | ✅ | ✅ (C6K) |
| **HDR10 / HLG** en reproducción (fallback a SDR) | ✅ | ⏳ pendiente (render) |
| **Prefetch** especulativo del siguiente video | ✅ | ⏳ pendiente (ganancia de latencia) |

Fase 2 (login con cuenta vía OAuth device-code, páginas de canal, suscripciones, SponsorBlock, subtítulos) está fuera de alcance por ahora. Ver [`docs/05-roadmap-and-risks.md`](./docs/05-roadmap-and-risks.md).

## Dispositivos probados

YouTroc apunta a **Android TV en general** (minSdk 26 / Android 8.0+). La decodificación la hace el SoC y Media3 negocia el códec con *fallback* AV1 → VP9 → H.264, así que el diseño no depende de un modelo puntual.

| Dispositivo | Estado |
|---|---|
| **TCL 55C6K** (MediaTek Pentonic 700, AV1 HW) — equipo de referencia | ✅ VOD + live validados |
| Otros Android TV / Google TV | ❓ sin probar — **se busca ayuda de la comunidad** |

¿Lo probaste en otro equipo? Abrí un issue con el resultado (ver [reportar](#contribuir)).

---

## Arquitectura

Multi-módulo Gradle bajo **Screaming + Hexagonal + Clean Architecture**, con la regla de dependencias apuntando siempre hacia `:core:domain`. El dominio es Kotlin/JVM puro (cero Android, cero framework); los adaptadores (extracción, reproductor, persistencia) implementan puertos del dominio, y las features solo hablan con esos puertos — nunca con los adaptadores.

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

### Mapa de módulos

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `:core:domain` | Kotlin/JVM puro | El hexágono: `VideoId`, `Video`, `Stream`, `PlayableStreams`, `PlaybackManifest`, `PlaybackState`, `VideoQuality`, `VideoDetail`… + puertos (`StreamProvider`, `MediaPlayer`, `WatchProgressStore`, `VideoDetail`) y use cases. |
| `:core:ui` | Android lib | Design system Compose for TV: theme, tokens, `VideoCardUi`, `ShelfRow`, logo. |
| `:data:extraction` | Kotlin/JVM | **Motor InnerTube propio** (búsqueda, detalle, streams VOD, feed regional del Home) con **NewPipeExtractor** como *fallback* automático (strangler-fig); en vivo sigue en NewPipe. Detección VOD/live, ensamblado DASH, selección de streams. Ad-free por construcción. |
| `:data:player` | Android lib | Adaptador del puerto `MediaPlayer` sobre **Media3/ExoPlayer** (DASH, HLS, selección de calidad). |
| `:data:persistence` | Android lib | Adaptador de `WatchProgressStore` sobre **DataStore Preferences**. Local-only. |
| `:feature:playback` | Android lib | Overlay del reproductor hecho a mano en Compose for TV (transporte, scrubber, menú Ajustes→Calidad, indicador EN VIVO, panel "A continuación") + ViewModels. |
| `:feature:catalog` | Android lib | Container + presentacional del shelf Trending del Home. |
| `:feature:search` | Android lib | Container + presentacional de la búsqueda. |
| `:app` | Android app | Composition root: DI manual (`viewModelFactory`, sin Hilt), navegación (Navigation Compose), cableado puerto→adaptador. |

Patrones transversales: **container-presentational** (ViewModel + `StateFlow<UiState>` + composable presentacional), `sealed UiState`, atomic design, DI manual por factories, y disciplina fuerte de **gestión de foco D-pad** (el riesgo recurrente #1 de UI en TV).

## Stack técnico

- **Kotlin** 100% · Gradle multi-módulo con version catalog (`gradle/libs.versions.toml`).
- **Jetpack Compose for TV** (`androidx.tv:tv-material3`) — sin Leanback.
- **Media3 / ExoPlayer** — DASH adaptativo, HLS (live), `SurfaceView`, `DefaultTrackSelector`.
- Motor de extracción **InnerTube propio** (búsqueda, detalle, streams VOD, feed regional del Home), con **NewPipeExtractor** (JitPack) como *fallback* automático; en vivo sigue en NewPipe.
- **DataStore Preferences** · **Navigation Compose** · **Coil 3** · DI manual (sin Hilt).

---

## Compilar e instalar

Requisitos: JDK 17+, Android SDK (compileSdk 35, minSdk 26) y un dispositivo/emulador Android TV.

```bash
# Compilar el APK debug
./gradlew :app:assembleDebug

# Instalar por sideload en cualquier Android TV (ADB sobre red)
adb connect <ip-del-tv>:5555
adb -s <ip-del-tv>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Los APK publicados van en la pestaña **[Releases](../../releases)** (con su SHA-256 y escaneo de VirusTotal — ver [Seguridad](#seguridad-y-verificación)). `local.properties` (ruta del SDK) es local y no se versiona.

## Tests

```bash
./gradlew test          # todos los módulos
```

El dominio se testea con **TDD** (fakes detrás de los puertos). La UI Compose (foco, D-pad, scroll) se valida en dispositivo real, no con tests unitarios. Hay una prueba de integración *opt-in* de live tras `YOUTROC_LIVE=1` (golpea la red real; no corre por defecto).

---

## Seguridad y verificación

Cada [release](../../releases) incluye el **SHA-256** del APK y un **escaneo de VirusTotal**, para que verifiques que descargaste exactamente ese archivo y que está limpio. La versión `v0.1.0-alpha` dio **0/74 — limpio** ([reporte](https://www.virustotal.com/gui/file/daa5a3f93578c4c0bddc78318c5a94bc345aa1eab548004717d51e9f79f39bad)):

```bash
sha256sum YouTroc-v0.1.0-alpha.apk
# daa5a3f93578c4c0bddc78318c5a94bc345aa1eab548004717d51e9f79f39bad
```

> Los clientes no oficiales de YouTube / basados en NewPipe a veces reciben falsos positivos heurísticos en algún antivirus; por eso publicamos el reporte completo de VirusTotal y el checksum.

## Contribuir

¡Bienvenidas las contribuciones! Especialmente **pruebas en otros Android TV**, reportes de bugs y correcciones. Antes de abrir un PR leé **[CONTRIBUTING.md](./CONTRIBUTING.md)** (arquitectura, convenciones, TDD, commits) y la suite de diseño en **[`docs/`](./docs/README.md)**.

- 🐞 **Bugs / pedidos:** abrí un [issue](../../issues) con el modelo de tu TV y la versión de Android TV.
- 🔧 **Código:** conventional commits, respetá la regla de dependencias hacia `:core:domain`, y no metas tipos de framework en el dominio.

## Documentación

La suite completa de producto y arquitectura vive en [`docs/`](./docs/README.md) (Visión, ADRs, requerimientos funcionales/no funcionales, arquitectura, roadmap, glosario, diseño de UI).

## Licencia

**GPL-3.0-or-later** — ver [LICENSE](./LICENSE). YouTroc usa NewPipeExtractor (GPLv3+), por lo que el proyecto es copyleft bajo la misma licencia.
