# Requerimientos No Funcionales (NFR) — YouTroc

> Define los atributos de calidad medibles del cliente ad-free de YouTube para Android TV, cuantificados contra el único dispositivo objetivo (TCL 55C6K) y trazables a las decisiones de arquitectura y fasing.

---

## 1. Propósito y alcance

Este documento describe **CÓMO** debe comportarse YouTroc (atributos de calidad), no **QUÉ** hace (eso vive en los requerimientos funcionales). Cada requerimiento tiene un **ID estable** (`RNF-<CATEGORÍA>-NN`) para poder referenciarlo desde specs, tasks, pruebas y revisiones.

Reglas de lectura:

- Todos los objetivos numéricos quedaron **confirmados como decisiones cerradas** (ver §11); ya no hay marcadores de asunción pendientes.
- El dispositivo de referencia es el **TCL 55C6K**; es el **piso y el techo** de medición. Nada por debajo de ese hardware importa, y todo número se valida **sobre el device real**, nunca en debug ni en emulador.
- Las fases siguen el plan del proyecto: **Slice vertical** (sin UI) → **Fase 1** (anónima) → **Fase 2** (cuenta OAuth).

### 1.1 Dispositivo de referencia (baseline de medición)

| Característica | Valor | Implicación para NFR |
|---|---|---|
| SoC | MediaTek Pentonic 700 | Decodificación **hardware AV1** disponible; el decode NO lo hace la app |
| RAM | 3 GB | Presupuesto de memoria ajustado; GC y carga de imágenes deben ser frugales |
| ROM | 32 GB | APK e índices locales (Room/DataStore) deben ser modestos |
| SO | Google TV (moderno) | Stack moderno permitido (Compose for TV, Media3) |
| Pantalla | 4K, 144 Hz, QD-Mini LED, Dolby Vision IQ | UI a 60 fps objetivo; salida de video hasta 4K |
| Decode video | AV1 por hardware | Preferir AV1/VP9; evitar decode por software |

---

## 2. RNF de Rendimiento (`RNF-PERF`)

El rendimiento es producto de **cuellos de botella entendidos**, no del lenguaje elegido. Las palancas reales son: selección de códec, presupuesto de frame de 16 ms, presupuesto de memoria y Baseline Profile + R8.

| ID | Requerimiento | Métrica / Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-PERF-01** | Presupuesto de frame de UI | ≤ **16 ms** por frame (60 fps) en navegación y scroll de listas | `dumpsys gfxinfo` / Macrobenchmark `FrameTimingMetric` en device real | 1 |
| **RNF-PERF-02** | Jank en scroll de listas (lazy lists Compose) | **< 1 %** de frames con jank durante scroll sostenido del home | Macrobenchmark `JankStats` / `FrameTimingMetric` P99 | 1 |
| **RNF-PERF-03** | Arranque en frío (cold start) hasta home interactivo | **≤ 2.5 s** en el C6K | Macrobenchmark `StartupTimingMetric` (TTID/TTFD), device real | 1 |
| **RNF-PERF-04** | Tiempo hasta primer frame de video (click → primer frame) | **≤ 1.5 s** en red doméstica estable | Traza de eventos Media3 (`onRenderedFirstFrame`) | Slice / 1 |
| **RNF-PERF-05** | Presupuesto de memoria de la app en reproducción estable | PSS objetivo **≤ 300 MB** (objetivo **blando**) sobre los 3 GB totales, sin ser candidato del lowmemorykiller | `dumpsys meminfo`, perfilado de heap en device real | 1 |
| **RNF-PERF-06** | Preferencia de códec por hardware | Orden de selección **AV1 → VP9 → H.264**, todos por **MediaCodec hardware-first**; nunca caer a decode por software en operación normal | `MediaCodecSelector` hardware-first; inspección de `Format`/decoder activo en logs | Slice / 1 |
| **RNF-PERF-07** | Combinación de streams DASH separados | Audio-only + video-only combinados vía **MergingMediaSource** sin desincronización audible | Reproducción manual + asserts de `MediaSource` | Slice / 1 |
| **RNF-PERF-08** | Control de buffer acotado en memoria | **DefaultLoadControl** con límites de buffer ajustados al presupuesto de 3 GB; sin crecimiento ilimitado | Revisión de config + `meminfo` bajo reproducción larga | 1 |
| **RNF-PERF-09** | Carga de imágenes frugal | **Coil** con downsampling al tamaño real del componente; sin decodificar miniaturas a resolución completa | Inspección de `ImageRequest` (size/scale), perfilado de memoria | 1 |
| **RNF-PERF-10** | Superficie de render de video | Uso de **SurfaceView** (no TextureView) para path de composición eficiente y menor consumo | Revisión de código del player UI | Slice / 1 |
| **RNF-PERF-11** | Política de calidad: ABR automático | Selección **ABR (adaptive bitrate) automática**, acotada al hardware del C6K, con preferencia de códec **AV1 → VP9 → H.264**; **sin selector manual de calidad** en la primera iteración de Fase 1 (se evalúa después) | Pruebas manuales de selección de track | 1 |

### 2.1 Escenarios de aceptación (rendimiento)

**RNF-PERF-01 / 02 — Scroll fluido del home**

- **Dado** que el usuario está en el home con feeds cargados,
- **Cuando** desplaza verticalmente con el D-pad de forma sostenida,
- **Entonces** ningún tramo de scroll debe exceder el presupuesto de 16 ms por frame de forma que produzca jank perceptible (P99 dentro de objetivo).

**RNF-PERF-06 — Decode por hardware**

- **Dado** un video con render AV1 disponible,
- **Cuando** se inicia la reproducción en el C6K,
- **Entonces** se selecciona el decoder **hardware** AV1 (o VP9 si AV1 no aplica), y el decode por software solo ocurre como último recurso del fallback (ver RNF-FIAB-02).

---

## 3. RNF de Fiabilidad (`RNF-FIAB`)

La fiabilidad se concentra en el punto más frágil del sistema: la **extracción** vía InnerTube. El motor propio es el **riesgo #1**; el scaffold NewPipeExtractor detrás del mismo puerto **StreamProvider** es la red de seguridad.

Todos los puertos de repositorio (empezando por **StreamProvider**) exponen un **resultado tipado (sealed)** que distingue **éxito / fallo-de-extracción / vacío / sin-red**; la UI deriva de él estados explícitos de error/empty/offline con mensaje claro y acción de reintento, y la app permanece estable (nunca crashea ni muestra pantalla en blanco).

| ID | Requerimiento | Métrica / Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-FIAB-01** | Manejo de fallo de extracción sin crash | Todo fallo de **StreamProvider** (PoToken, n-signature, BotGuard, visitor-data, bloqueo "not a bot") se traduce en la variante **fallo-de-extracción** del resultado tipado (sealed) del dominio, nunca en un crash | Tests de dominio con fakes que simulan fallo; pruebas manuales | Slice / 1 |
| **RNF-FIAB-02** | Decoder fallback de reproducción | **`setEnableDecoderFallback(true)`**: si el decoder preferido falla al inicializar, Media3 reintenta con otro decoder antes de fallar la reproducción | Revisión de config + prueba forzando fallo de decoder | Slice / 1 |
| **RNF-FIAB-03** | Estado vacío (empty state) | Resultados vacíos (búsqueda sin hits, feed sin items) se representan con la variante **vacío** del resultado tipado y muestran un estado vacío claro, no una pantalla en blanco ni un error | Tests de presentación + manual | 1 |
| **RNF-FIAB-04** | Estado sin red (offline) | La variante **sin-red** del resultado tipado lleva a un estado offline explícito con acción de reintento; la app no se cuelga ni cierra | Test con red deshabilitada en device | 1 |
| **RNF-FIAB-05** | Degradación ante cambio de InnerTube | Si el motor propio falla por cambios del lado servidor, el sistema puede **reactivar el scaffold NewPipe** detrás del mismo puerto sin tocar features | Switch de implementación en :app (DI); test de contrato del puerto | 1 |
| **RNF-FIAB-06** | Contrato de puerto estable ante swap de motor | Cambiar de motor (NewPipe ↔ propio) NO altera el contrato de **StreamProvider** visto por `:feature:*` | Suite de tests de contrato compartida entre adapters | 1 |
| **RNF-FIAB-07** | Ignorar campos de ads de forma robusta | Los campos `adPlacements` / `playerAds` de la respuesta InnerTube se ignoran siempre; su presencia, ausencia o cambio de forma **nunca** rompe la resolución del stream | Tests de parsing con payloads con y sin ads | Slice / 1 |
| **RNF-FIAB-08** | Contenido restringido / age-gated | En modo anónimo (Fase 1) **no se garantiza**: manejo *best-effort* y, si no es resoluble, mensaje explícito sin disrupción; la resolución real puede requerir cuenta (Fase 2) | Tests + manual sobre IDs conocidos | 1 / 2 |

### 3.1 Escenarios de aceptación (fiabilidad)

**RNF-FIAB-01 — Fallo de extracción controlado**

- **Dado** que el motor de extracción no logra resolver un stream (p. ej. bloqueo BotGuard),
- **Cuando** el usuario intenta reproducir,
- **Entonces** se presenta un mensaje de error de dominio con opción de reintento, y la app permanece estable (sin crash).

**RNF-FIAB-04 — Sin red**

- **Dado** que el dispositivo no tiene conectividad,
- **Cuando** el usuario abre el home o ejecuta una búsqueda,
- **Entonces** se muestra el estado offline con acción de reintento, sin pantalla en blanco ni cierre.

**RNF-FIAB-05 — Red de seguridad del scaffold**

- **Dado** que el motor propio empieza a fallar tras un cambio del lado de YouTube,
- **Cuando** se reconfigura la inyección de **StreamProvider** hacia el adapter NewPipe,
- **Entonces** la reproducción vuelve a funcionar sin modificar ningún módulo `:feature:*`.

---

## 4. RNF de Mantenibilidad (`RNF-MANT`)

La mantenibilidad se sostiene sobre **fronteras impuestas por el compilador** (multi-módulo Gradle), la **Regla de Dependencia** apuntando hacia el dominio, y la capacidad de **reemplazar el motor** sin tocar features.

| ID | Requerimiento | Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-MANT-01** | Motor swappable detrás de `StreamProvider` | El motor de extracción es una implementación intercambiable del puerto; `:feature:*` desconoce la implementación concreta | Inspección del grafo de módulos; tests de contrato | Slice / 1 |
| **RNF-MANT-02** | Estrategia de resiliencia de extracción | Scaffold **NewPipeExtractor** como baseline + motor propio en paralelo detrás del **mismo** puerto; swap cuando el propio iguale a la referencia | Comparación de resultados motor vs. referencia | 1 |
| **RNF-MANT-03** | Regla de Dependencia (Clean) | Las dependencias de código apuntan **hacia adentro**, hacia `:core:domain`; este no depende de nada ni del Android SDK | Boundaries de Gradle (falla de compilación si se viola) | Todas |
| **RNF-MANT-04** | Fronteras de módulo enforced | `:feature:*` depende solo de `:core:domain` y `:core:ui`; **nunca** de `:data:*`. Solo `:app` conoce features y adapters a la vez | Configuración de dependencias Gradle | Todas |
| **RNF-MANT-05** | Ports & adapters con criterio | Puertos/adapters solo donde hay **volatilidad real** o **valor de aislamiento para test**: extracción, player, session, persistence — no dogmáticamente en todo | Revisión de arquitectura | Todas |
| **RNF-MANT-06** | Composición en un único root | La inyección de adapters en puertos ocurre solo en `:app` (composition root) | Revisión de DI | Todas |
| **RNF-MANT-07** | Dominio puro y portable | `:core:domain` es Kotlin puro (sin Android SDK), testeable fuera del device | Compilación del módulo sin dependencias Android | Todas |

---

## 5. RNF de Seguridad y Privacidad (`RNF-SEG`)

YouTroc se distribuye como **open-source** por sideload y releases en GitHub (todavía sin publicación en tiendas), sin monetización. La privacidad es por diseño: **sin telemetría, sin ads, sin tracking**.

| ID | Requerimiento | Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-SEG-01** | Sin ads por construcción | Nunca se ejecuta el player oficial de Google; los campos de ads de InnerTube se ignoran. **Sin DNS blocking, sin MITM, sin microG** | Revisión de código de extracción/player | Slice / 1 |
| **RNF-SEG-02** | Sin telemetría | La app **no** envía analíticas, crash-reporting remoto ni telemetría de ningún tipo a terceros | Auditoría de red saliente (solo InnerTube + googlevideo CDN) | Todas |
| **RNF-SEG-03** | Fase 1 anónima sin login | Fase 1 opera sin cuenta: no se solicitan ni almacenan credenciales | Revisión de **SessionGateway** (modo Anonymous) | 1 |
| **RNF-SEG-04** | Riesgo de ban acotado a Fase 2 | El riesgo de baneo de cuenta aplica **solo** estando logueado; se recomienda **cuenta Google quemable (burner)** para Fase 2 | Documentación + flujo de login Fase 2 | 2 |
| **RNF-SEG-05** | Login OAuth sin microG | Fase 2 usa **OAuth 2.0 device-authorization** (flujo de código en `youtube.com/activate`, estilo SmartTube); microG es irrelevante al proyecto | Revisión del adapter de session OAuth | 2 |
| **RNF-SEG-06** | Datos locales mínimos (anónimo) | En Fase 1 se persiste **solo localmente** y **nada sale del device**: **ajustes** (`SettingsStore`), **favoritos** (`FavoritesStore`) y **continuar viendo / watch progress** (`WatchProgressStore`, `PlaybackPosition`). **No** hay lista completa de historial local (el historial remoto es de Fase 2) | Revisión de esquema Room/DataStore de `:data:persistence` | 1 |
| **RNF-SEG-07** | Custodia de tokens (Fase 2) | Tokens OAuth se almacenan solo localmente en el device, nunca se transmiten a terceros ni se loguean | Revisión de :data:session + logs | 2 |
| **RNF-SEG-08** | Sin secretos en logs | Ningún token, cookie, visitor-data o dato sensible aparece en logs de diagnóstico | Revisión de la capa de observabilidad (ver §7) | Todas |

---

## 6. RNF de Testing y Calidad (`RNF-TEST`)

**TDD activo**: el dominio se prueba con **fakes detrás de los puertos**, fuera del device.

| ID | Requerimiento | Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-TEST-01** | TDD como práctica activa | Lógica de dominio desarrollada con ciclo test-first | Revisión de PRs / orden de commits | Todas |
| **RNF-TEST-02** | Dominio testeado con fakes de puertos | `:core:domain` testeado contra **fakes** de **StreamProvider**, **VideoCatalog**, **VideoSearch**, **MediaPlayer**, **SessionGateway**, **SettingsStore**, **FavoritesStore** y **WatchProgressStore** — sin red ni device | Suite unitaria JVM (Kotlin puro) | Todas |
| **RNF-TEST-03** | Tests de contrato de puerto | Cada puerto con múltiples adapters (p. ej. extracción NewPipe vs. propio) comparte una suite de contrato | Ejecución de la suite sobre cada adapter | 1 |
| **RNF-TEST-04** | Benchmarks de rendimiento en device | Métricas de RNF-PERF medidas con Macrobenchmark **en el C6K real** | CI/manual sobre device, no emulador | 1 |
| **RNF-TEST-05** | Aislamiento de capas de feature | `:feature:*` testeable sin adapters reales, inyectando fakes de puertos | Tests de presentación con fakes | 1 |

---

## 7. RNF de Observabilidad (`RNF-OBS`)

El motor de extracción es el subsistema más frágil; necesita **diagnóstico** propio para depurar bloqueos (PoToken, n-signature, BotGuard) que requieren mantenimiento casi "on-call".

| ID | Requerimiento | Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-OBS-01** | Logging de diagnóstico del motor | El **StreamProvider** emite logs estructurados de las etapas de extracción (resolución de stream, PoToken, n-signature, visitor-data) para diagnóstico local | Inspección de logs durante fallos | Slice / 1 |
| **RNF-OBS-02** | Diagnóstico de selección de decoder | Se registra el decoder/códec efectivamente seleccionado y los eventos de fallback | Logs de Media3 / player UI | Slice / 1 |
| **RNF-OBS-03** | Observabilidad local únicamente | Todo el diagnóstico es **local** (logcat / almacenamiento del device); jamás se envía a un backend remoto (coherente con RNF-SEG-02) | Auditoría de red saliente | Todas |
| **RNF-OBS-04** | Logs sin datos sensibles | Los logs respetan RNF-SEG-08: sin tokens, cookies ni identificadores sensibles | Revisión de la capa de logging | Todas |
| **RNF-OBS-05** | Niveles de log configurables | El verbosity del motor se puede elevar **localmente en runtime** para depurar, sin recompilar con cambios invasivos | Revisión de config de logging | 1 |

---

## 8. RNF de Distribución (`RNF-DIST`)

> Actualización: ver **ADR-10** — distribución ahora por releases open-source en GitHub; el sideload directo al dispositivo sigue vigente como método de instalación.

| ID | Requerimiento | Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-DIST-01** | Distribución por sideload | El artefacto se instala por **sideload** directo al **TCL 55C6K**; trivial (instalación manual por adb) | Instalación manual (adb/sideload) | Todas |
| **RNF-DIST-02** | Fuera de tiendas | YouTroc **no** se publica en app stores; no aplican requisitos de policy de tiendas | N/A (decisión de proyecto) | Todas |
| **RNF-DIST-03** | Target único, sin probing de códec | Un único device objetivo libera de compatibilidad universal: **sin per-device codec probing** | Revisión de config del player | Todas |
| **RNF-DIST-04** | Footprint de instalación modesto | APK release **modesto frente a los 32 GB de ROM** e índices locales acordes; sin assets innecesarios (objetivo cualitativo, sin tope numérico duro) | Tamaño de APK release | 1 |

---

## 9. RNF de Disciplina de Build (`RNF-BUILD`)

Práctica **obligatoria desde el día uno**: **R8 full mode** + **Baseline Profile** específico de la app, y **medir siempre en el device real**.

| ID | Requerimiento | Objetivo | Verificación | Fase |
|---|---|---|---|---|
| **RNF-BUILD-01** | R8 full mode obligatorio | Builds release con **R8 en full mode** (shrinking + optimización + ofuscación) | Inspección de config Gradle release | Todas |
| **RNF-BUILD-02** | Baseline Profile obligatorio | **Baseline Profile específico de la app** incluido y verificado activo en release | `dumpsys` de compilación AOT / Macrobenchmark con/sin profile | 1 |
| **RNF-BUILD-03** | Medir solo en device real | Todas las métricas de rendimiento y arranque se miden en el **C6K real**, **nunca** en debug ni emulador | Procedimiento de medición documentado | Todas |
| **RNF-BUILD-04** | No medir sobre builds debug | Prohibido derivar conclusiones de rendimiento de builds debug (sin R8, sin AOT del profile) | Revisión de la metodología de benchmarking | Todas |
| **RNF-BUILD-05** | Stack moderno sin lastre legacy | Compose for TV (`androidx.tv`) + Media3; **Leanback** queda excluido por estar deprecado | Revisión de dependencias | 1 |

### 9.1 Escenario de aceptación (build)

**RNF-BUILD-02 / 03 — Medición válida**

- **Dado** un APK release con **R8 full** y **Baseline Profile** incluidos,
- **Cuando** se ejecutan Macrobenchmark de startup y frame timing **en el TCL 55C6K**,
- **Entonces** los resultados se aceptan como válidos; cualquier medición tomada en debug o emulador se descarta.

---

## 10. Matriz de trazabilidad (NFR ↔ decisión / fase)

| Categoría | IDs | Decisión raíz | Fase de mayor foco |
|---|---|---|---|
| Rendimiento | RNF-PERF-01..11 | Player Media3, códec hardware, frame/memory budget | Slice / 1 |
| Fiabilidad | RNF-FIAB-01..08 | Extracción propia + scaffold NewPipe, decoder fallback | Slice / 1 |
| Mantenibilidad | RNF-MANT-01..07 | Multi-módulo + Hexagonal + Clean | Todas |
| Seguridad/Privacidad | RNF-SEG-01..08 | Ad-free por construcción, SessionGateway, burner account | 1 / 2 |
| Testing/Calidad | RNF-TEST-01..05 | TDD con fakes detrás de puertos | Todas |
| Observabilidad | RNF-OBS-01..05 | Diagnóstico del motor de extracción | Slice / 1 |
| Distribución | RNF-DIST-01..04 | Sideload al C6K, target único | Todas |
| Disciplina de build | RNF-BUILD-01..05 | R8 full + Baseline Profile, medir en device real | Todas |

---

## 11. Decisiones confirmadas (resueltas en la fecha de confirmación)

Lo que antes eran defaults PRD-level a confirmar quedó **cerrado como decisión**. Cada ítem ya está reflejado en los RNF correspondientes; aquí se listan las resoluciones:

1. **RNF-PERF-02** — Jank en scroll: **< 1 %** de frames con jank. **Confirmado.**
2. **RNF-PERF-03** — Arranque en frío al home interactivo: **≤ 2.5 s** en el C6K. **Confirmado.**
3. **RNF-PERF-04** — Tiempo hasta primer frame de video: **≤ 1.5 s**. **Confirmado.**
4. **RNF-PERF-05** — Presupuesto de memoria: **PSS ≤ 300 MB** (objetivo **blando**, sin límite duro). **Confirmado.**
5. **RNF-PERF-11** — Política de calidad: **ABR automático** acotado al hardware del C6K con preferencia **AV1 → VP9 → H.264**; **sin selector manual** en la primera iteración de Fase 1 (se evalúa después). **Confirmado.**
6. **RNF-FIAB-03 / 04** — Estados vacío y offline derivados del **resultado tipado (sealed)** de los puertos (variantes vacío / sin-red), con mensaje claro y acción de reintento; la app nunca crashea ni muestra pantalla en blanco. **Confirmado.**
7. **RNF-FIAB-08** — Contenido restringido / age-gated: en modo anónimo (Fase 1) **no se garantiza**; manejo *best-effort* y mensaje explícito sin disrupción; la resolución real puede requerir cuenta (Fase 2). **Confirmado.**
8. **RNF-SEG-06** — Datos locales de Fase 1: **solo** ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore` / `PlaybackPosition`); **nada sale del device**; sin historial local completo (el historial remoto es de Fase 2). **Confirmado.**
9. **RNF-OBS-05** — Niveles de log configurables localmente en runtime, sin recompilar. **Confirmado.**
10. **RNF-DIST-04** — APK release **modesto** frente a los 32 GB de ROM; objetivo cualitativo, sin tope numérico duro. **Confirmado.**

Decisiones de alcance adicionales confirmadas que tocan estos NFR:

- **Idioma/región InnerTube**: `hl=es` (español); `gl`/región heredada del dispositivo.
- **Subtítulos/captions**: **diferidos** (no en el slice vertical ni en la Fase 1 inicial). El dominio **modela** `captionTracks` / `CaptionTrack`, pero puede quedar vacío hasta decidir el soporte real más adelante.
- **SponsorBlock**: **Fase 2**. El dominio **modela** segmentos saltables (`SponsorSegment`) desde ya; la integración/adaptador se entrega en Fase 2 (no en Fase 1 ni en el slice vertical).
- **Mando / D-pad**: convenciones estándar de Android TV / Google TV (navegación por foco, OK selecciona, BACK retrocede, controles propios de play/pausa/seek durante la reproducción); sin atajos personalizados en Fase 1.
- **Decisión #7 (cerrada)**: TDD activo (dominio testeado con fakes detrás de los puertos); persistencia local Room/DataStore; distribución por sideload al C6K; el motor propio es el riesgo #1 y NewPipeExtractor es la red de seguridad.

## 12. Decisiones de negocio / producto (resueltas en la fecha de confirmación)

Las preguntas de alcance de Fase 1 quedaron resueltas con el dueño:

1. **Arranque en frío / primer frame** → objetivos **≤ 2.5 s** (cold start) y **≤ 1.5 s** (primer frame) en el C6K (RNF-PERF-03 / 04).
2. **Presupuesto de memoria** → **PSS ≤ 300 MB** como **objetivo blando** (no límite duro) sobre los 3 GB (RNF-PERF-05).
3. **Política de calidad/resolución** → **ABR automático** acotado al hardware con preferencia **AV1 → VP9 → H.264**; **sin selector manual** en la primera iteración de Fase 1 (RNF-PERF-11).
4. **Datos locales de Fase 1** → **solo** ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore` / `PlaybackPosition`); nada sale del device; sin historial local completo (RNF-SEG-06).
5. **Fallo de extracción / vacío / sin red** → **resultado tipado (sealed)** de los puertos con estados explícitos de error/empty/offline, copy claro y reintento; la app permanece estable (RNF-FIAB-01 / 03 / 04).
6. **Contenido restringido / age-gated** → en Fase 1 (anónimo) **best-effort** con mensaje explícito sin disrupción; la resolución real puede requerir cuenta en Fase 2 (RNF-FIAB-08).
7. **SponsorBlock y subtítulos/captions** → ambos **diferidos a Fase 2** a nivel de integración; el dominio **modela** `SponsorSegment` y `CaptionTrack` desde ya. No impactan el rendimiento/memoria/observabilidad de Fase 1.
8. **Idioma/región InnerTube** → `hl=es` y `gl`/región heredada del dispositivo.
