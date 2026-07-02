# YouTroc — Registro de Decisiones de Negocio (ADR)

> Propósito: dejar por escrito, ANTES de escribir código, cada decisión de negocio y arquitectura que define a YouTroc, con su contexto, razón, consecuencias y evidencia verificada.

## Contexto del proyecto

**YouTroc** es un CLIENTE de YouTube sin anuncios para Android TV, construido desde cero. Es de uso personal (sideload), NO está destinado a tiendas de aplicaciones, y se trata como un proyecto serio y grande. El único usuario y dueño es el propio desarrollador (actualizado por **ADR-10**: ahora proyecto open-source, con distribución por sideload y releases en GitHub).

Este documento es la fuente de verdad de las decisiones. Cada ADR es independiente y se puede revisar/superar más adelante manteniendo el historial. El orden de lectura recomendado es de ADR-0 hacia abajo: las primeras decisiones (ideología, hardware) condicionan a las siguientes.

### Convenciones de este registro

| Campo | Significado |
| --- | --- |
| **Estado** | `Aceptada`, `Propuesta`, `Superada`, `Rechazada`. |
| **Contexto** | El problema o la fuerza que obliga a decidir. |
| **Decisión** | Lo que se decide hacer, en una frase accionable. |
| **Razón** | Por qué esa decisión y no otra. |
| **Consecuencias** | Lo que esta decisión habilita y lo que cuesta (positivo y negativo). |
| **Alternativas consideradas** | Opciones evaluadas y por qué se descartaron. |
| **Evidencia** | Hechos de investigación verificados que sostienen la decisión. |

> Marcado de huecos: este registro nació con valores por defecto propuestos marcados como **(Asunción — a confirmar)**. En la fecha de confirmación el negocio cerró TODAS esas asunciones; ya no quedan huecos abiertos. Las decisiones de producto antes pendientes quedaron consolidadas en **ADR-9**.

---

## ADR-0 — Ideología: cliente propio desde cero, NO patching de la app oficial

**Estado:** Aceptada

**Contexto:**
Existen dos caminos para obtener YouTube sin anuncios:
- **(A) PATCHING** — parchear la app oficial (linaje Vanced → ReVanced → Morphe).
- **(B) CLIENTE DESDE CERO** — construir un cliente propio que hable directamente con la API interna **InnerTube** de YouTube.

El objetivo del proyecto es Android TV. Hay que decidir el camino raíz porque condiciona todo lo demás.

**Decisión:**
Construir un cliente desde cero (camino B) que consume la API **InnerTube**. La ausencia de anuncios es **por construcción**: nunca se ejecuta el reproductor oficial de Google, por lo que simplemente se ignoran los campos `adPlacements`/`playerAds` que devuelve InnerTube.

**Razón:**
- El patching NO funciona en TV. ReVanced/Morphe parchean únicamente el paquete de teléfono (`com.google.android.youtube`); la app de TV es un paquete Leanback distinto (`com.google.android.youtube.tv`) que ningún set de parches mantenido ataca. ReVanced declinó explícitamente el soporte de TV y deriva a los usuarios a SmartTube.
- Al no ejecutar el reproductor de Google, no hay que bloquear ni interceptar nada: los anuncios viajan por el MISMO CDN `googlevideo` que el contenido, así que el bloqueo por DNS fracasa en YouTube. Evitar el reproductor oficial elimina el problema en origen.

**Consecuencias:**
- (+) Sin anuncios por diseño, sin frágiles parches que rompen con cada actualización del APK oficial.
- (+) Sin necesidad de DNS sinkhole, sin MITM, sin microG.
- (+) Control total del cliente: es el diferenciador central del proyecto.
- (−) Asumimos la responsabilidad de extraer streams reproducibles desde InnerTube, lo que conlleva el riesgo de mantenimiento del motor de extracción (ver ADR-5).
- (−) Dependemos de la estabilidad de la API InnerTube y del CDN `googlevideo`; somos el mejor CLIENTE, no un mejor YouTube.

**Alternativas consideradas:**
- **Patching de la app oficial (camino A):** descartado porque no hay paquete de TV parcheado y mantenido; el linaje ReVanced/Morphe solo cubre el teléfono.
- **Bloqueo por DNS / Pi-hole:** descartado porque los anuncios provienen del mismo CDN que el contenido; bloquear el dominio rompería también la reproducción.
- **MITM / microG sobre app oficial:** descartado por complejidad, fragilidad y porque microG solo aplica a apps parcheadas.

**Evidencia (verificada):**
- ReVanced declinó el soporte de TV de forma explícita (revanced-manager issue **#3385**) y redirige a SmartTube.
- Separación de paquetes confirmada: teléfono `com.google.android.youtube` vs TV `com.google.android.youtube.tv`; los parches mantenidos solo atacan el de teléfono.
- Los anuncios de YouTube se sirven desde el mismo CDN `googlevideo` que los streams de contenido, por lo que el bloqueo por dominio no los separa del video.

---

## ADR-1 — Target de hardware único: TCL 55C6K (2025)

> **Estado:** parcialmente **superseded por ADR-10** — el C6K pasa de "piso y techo" a **dispositivo de referencia/baseline**; el objetivo es ahora multi-target Android TV.

**Estado:** Aceptada

**Contexto:**
SmartTube y proyectos similares cargan el peso de la compatibilidad universal (cientos de modelos de TV box, SoCs viejos, Android TV antiguo). YouTroc es para un único dueño con un único equipo conocido. Hay que decidir si optimizamos para "todo el mundo" o para un target concreto.

**Decisión:**
Tratar el **TCL 55C6K (2025)** como el ÚNICO target soportado y como el piso de capacidades. Nada por debajo de él importa. Eso libera al proyecto del lastre de compatibilidad universal y habilita el stack más moderno disponible.

**Razón:**
- Conocemos el equipo exacto, por lo que podemos asumir sus capacidades sin pruebas de detección por dispositivo.
- Un target moderno y fijo permite usar Compose for TV, Media3, decodificación AV1 por hardware y prácticas modernas (R8 full mode, Baseline Profile) sin compromisos hacia atrás.

**Consecuencias:**
- (+) No se necesita sondeo de códecs por dispositivo ni rutas de compatibilidad legacy.
- (+) Decisiones de stack libres de restricciones de hardware antiguo (ver ADR-6, ADR-7, ADR-8).
- (−) El cliente no es portable a otros equipos sin revisar supuestos de hardware; aceptado conscientemente por ser de uso personal.
- (−) Si en el futuro se agregan más equipos, varios supuestos (códecs, RAM) deberán revalidarse.

**Especificaciones del target:**

| Característica | Valor |
| --- | --- |
| Modelo | TCL 55C6K (2025) |
| SoC | MediaTek **Pentonic 700** |
| RAM | 3 GB |
| ROM | 32 GB |
| SO | Google TV (moderno) |
| Pantalla | 4K 144Hz, QD-Mini LED, Dolby Vision IQ |
| Decodificación | **AV1 por hardware** (también VP9/H.264) |

**Alternativas consideradas:**
- **Compatibilidad universal estilo SmartTube:** descartada; agrega complejidad y limita el stack a denominadores comunes antiguos sin beneficio para el dueño único.
- **Target por gama mínima genérica:** descartada por la misma razón; no hay segundo equipo que justificarla.

**Evidencia (verificada):**
- El C6K monta el SoC **Pentonic 700**, con **3 GB de RAM** y **decodificación AV1 por hardware**, lo que valida el uso de AV1/VP9 como formatos preferidos (ver ADR-7).
- SmartTube es un proyecto orientado a compatibilidad amplia (~99% Java + Leanback + ExoPlayer), un baggage que un target único nos permite evitar.

---

## ADR-2 (Decisión #1) — Producto: réplica de la experiencia oficial de YouTube TV

**Estado:** Aceptada

**Contexto:**
Hay que definir QUÉ producto es YouTroc: una herramienta mínima, o una réplica completa de la experiencia de YouTube en TV. La definición del producto fija el alcance de las features.

**Decisión:**
YouTroc es una **réplica de la experiencia del cliente oficial de YouTube TV** (experiencia completa), tratada como un proyecto serio y grande, con expansión futura abierta.

**Razón:**
- El dueño quiere reemplazar de verdad la app oficial en su TV, no un visor recortado.
- Una réplica completa fija una vara de calidad clara (la app oficial) y deja espacio de crecimiento.

**Consecuencias:**
- (+) Norte de producto claro: paridad con YouTube TV.
- (+) Justifica la inversión en arquitectura modular y motor propio (ADR-4, ADR-5).
- (−) Alcance grande; obliga a un faseo estricto para no intentar todo a la vez (ver PHASING y ADR-3).
- (−) La frontera exacta de features de cada fase quedó definida explícitamente en ADR-9.

**Faseo del producto (resumen; ver ADR-3 para cuenta/sesión):**

| Etapa | Alcance |
| --- | --- |
| **START** (sin UI) | `videoId` hardcodeado → InnerTube → reproducción con Media3, sin anuncios. Prueba la hipótesis central el día 1. |
| **FASE 1** (anónima) | Home (trending) + búsqueda + detalle de video (metadatos + reproducir) + reproducción sin anuncios + videos RELACIONADOS en modo read-only. Datos SOLO locales: ajustes, favoritos y continuar viendo. Motor propio con andamio NewPipe. UI Compose for TV. |
| **FASE 2** (cuenta) | Login OAuth device-code → home personalizado, suscripciones, historial remoto, acciones de cuenta (like, suscribir, añadir a playlist) y SponsorBlock. |

**Frontera de FASE 1 — Decisión confirmada (resuelta en la fecha de confirmación; detalle en ADR-9):**
FASE 1 (anónima) INCLUYE: home = feed de trending; búsqueda; detalle de video (metadatos + reproducir); reproducción sin anuncios; y videos RELACIONADOS en modo read-only (única adición de navegación). Como datos SOLO locales guarda ajustes, favoritos y continuar viendo (watch progress / `PlaybackPosition`). FASE 1 EXCLUYE (diferido a FASE 2 o posterior): páginas de canal, playlists, suscripciones sin login, Shorts (si aparecen en feeds se tratan como videos normales, sin UI dedicada), historial remoto y acciones de cuenta.

**Otras políticas de producto — Decisiones confirmadas (resueltas en la fecha de confirmación; detalle en ADR-9):**
- **Calidad/resolución:** ABR (adaptive bitrate) automático, acotado al hardware del C6K, con preferencia de códec AV1 → VP9 → H.264. SIN selector manual de calidad en la primera iteración de FASE 1.
- **SponsorBlock:** integración en FASE 2; el dominio MODELA desde ya los segmentos saltables (`SponsorSegment`). NO se integra en FASE 1 ni en el vertical slice.
- **Subtítulos/captions:** diferidos; NO entran en la FASE 1 inicial. El dominio MODELA `captionTracks` / `CaptionTrack`, que puede quedar vacío hasta implementar el soporte real.
- **Idioma/región de InnerTube:** `hl=es` (español) y `gl`/región heredada del dispositivo.
- **Comportamiento ante fallo de extracción / resultados vacíos / sin red:** los puertos de repositorio exponen un resultado TIPADO (sealed) que distingue éxito / fallo-de-extracción / vacío / sin-red; la UI muestra estado explícito de error/empty/offline con mensaje claro y reintento, sin crashear.
- **Contenido restringido / age-gated:** manejo best-effort en modo anónimo; si no es resoluble, mensaje explícito sin disrupción (la resolución real puede requerir cuenta, FASE 2).
- **Continuar viendo (watch progress) local en FASE 1:** SÍ, guardado localmente vía `WatchProgressStore` (`PlaybackPosition`); nada sale del dispositivo.

**Alternativas consideradas:**
- **Cliente mínimo (solo pegar un `videoId` y reproducir):** descartado como producto final; sí se usa como hito START interno, no como objetivo.

**Evidencia (verificada):**
- La viabilidad técnica de un cliente completo de TV está demostrada por SmartTube, que ofrece experiencia completa sobre Leanback + ExoPlayer; YouTroc apunta a la misma amplitud con stack moderno.

---

## ADR-3 (Decisión #2) — Cuenta/sesión por fases, con `SessionGateway` desde el día 1

**Estado:** Aceptada

**Contexto:**
Una réplica completa necesita, tarde o temprano, identidad de usuario (home personalizado, suscripciones, historial). Pero el login agrega complejidad y riesgo de baneo. Hay que decidir cómo y cuándo introducir la sesión sin contaminar la arquitectura.

**Decisión:**
Definir un puerto de sesión/identidad, **`SessionGateway`**, desde el día 1, con dos implementaciones por fase:
- **FASE 1 = Anónima:** sin login. Home = trending, búsqueda, reproducción sin anuncios.
- **FASE 2 = OAuth 2.0 device-authorization** (estilo SmartTube: el flujo "ingresar código en youtube.com/activate", **SIN microG**), que habilita home personalizado, suscripciones e historial.

**Razón:**
- Tener el puerto desde el inicio evita reescrituras cuando llegue la cuenta; el resto del sistema solo conoce el puerto, no la fase.
- El flujo device-code es el adecuado para TV (sin teclado cómodo) y no requiere microG porque no dependemos de la app oficial.

**Consecuencias:**
- (+) FASE 1 entrega valor (sin anuncios) sin asumir riesgo de baneo de cuenta.
- (+) El salto a FASE 2 es agregar un adaptador detrás de `SessionGateway`, sin tocar features.
- (−) El riesgo de baneo de cuenta aplica SOLO cuando hay login → se recomienda usar una **cuenta de Google "quemable" (burner)** para FASE 2.
- (−) microG es irrelevante para este proyecto (solo importa para el login de apps parcheadas); no se invierte en ello.

**Qué datos locales se guardan en FASE 1 anónima (Decisión confirmada):**
FASE 1 guarda LOCALMENTE y SOLO: ajustes (`SettingsStore`), favoritos (`FavoritesStore`) y continuar viendo (`WatchProgressStore` / `PlaybackPosition`). NO hay lista completa de historial local; nada sale del dispositivo. El historial remoto de la cuenta es de FASE 2 (ver ADR-9).

**Alternativas consideradas:**
- **Login desde el día 1:** descartado; agrega riesgo de baneo y complejidad antes de validar la hipótesis central (sin anuncios).
- **Login con microG / replicando app oficial:** descartado; microG solo aplica a apps parcheadas, que ya descartamos en ADR-0.
- **Usuario/contraseña directos:** descartado; el flujo device-code es el estándar seguro y cómodo en TV.

**Evidencia (verificada):**
- SmartTube usa exactamente el flujo OAuth device-code ("ingresar código en youtube.com/activate") sin microG, lo que prueba su viabilidad en Android TV.
- microG solo es relevante para el login de apps parcheadas (camino A descartado en ADR-0), por lo que no aporta a un cliente propio.

---

## ADR-4 (Decisión #3) — Arquitectura multi-módulo: Hexagonal + Screaming + Clean

**Estado:** Aceptada

**Contexto:**
Un proyecto grande y de larga vida, con un motor de extracción volátil (ADR-5), necesita límites duros entre dominio, features e infraestructura para poder testear, aislar el riesgo y cambiar piezas sin romper todo. Hay que decidir la estructura.

**Decisión:**
Adoptar una arquitectura **multi-módulo Gradle** (límites forzados por el compilador) que combina:
- **Screaming Architecture:** el nivel superior se organiza por dominio/feature, no por capas técnicas.
- **Hexagonal (ports & adapters):** el dominio define puertos; la infraestructura los implementa como adaptadores.
- **Clean Architecture — Regla de Dependencia:** las dependencias de código apuntan HACIA ADENTRO, hacia el dominio.

**Grafo de módulos:**

| Módulo | Rol | Depende de |
| --- | --- | --- |
| `:app` | Composition root; el ÚNICO módulo que conoce features y adapters a la vez; inyecta adapters en los ports (DI). | features + adapters |
| `:core:domain` | Kotlin PURO (sin Android SDK): modelos de dominio + ports. | NADA |
| `:core:ui` | Componentes Compose for TV compartidos (theme, focus helpers). | (UI compartida) |
| `:feature:catalog` | Feature de catálogo/feeds. | `:core:domain`, `:core:ui` |
| `:feature:search` | Feature de búsqueda. | `:core:domain`, `:core:ui` |
| `:feature:video` | Feature de detalle de video. | `:core:domain`, `:core:ui` |
| `:feature:playback` | Feature de reproducción (controles propios). | `:core:domain`, `:core:ui` |
| `:feature:session` | Feature de sesión/cuenta. | `:core:domain`, `:core:ui` |
| `:data:extraction` | Implementa `StreamProvider` y feeds de extracción. | `:core:domain` |
| `:data:player` | Implementa `MediaPlayer` sobre Media3. | `:core:domain` |
| `:data:session` | Implementa `SessionGateway`. | `:core:domain` |
| `:data:persistence` | Persistencia local (Room/DataStore). | `:core:domain` |

> Regla dura: los módulos `:feature:*` dependen SOLO de `:core:domain` y `:core:ui`; NUNCA de `:data:*`. Las features conocen únicamente los ports.

**Puertos del dominio:**

| Puerto | Responsabilidad |
| --- | --- |
| `StreamProvider` | Resuelve streams reproducibles ignorando los campos de anuncios = núcleo anti-ads, intercambiable de motor (ver ADR-5). |
| `VideoCatalog` | Feeds (home/trending, etc.). |
| `VideoSearch` | Búsqueda de videos. |
| `MediaPlayer` | Control de reproducción (play/pause/seek). |
| `SessionGateway` | Sesión: FASE 1 Anónima / FASE 2 OAuth (ver ADR-3). |
| `SettingsStore` | Preferencias/ajustes locales (implementado por `:data:persistence`). |
| `FavoritesStore` | Favoritos locales (implementado por `:data:persistence`). |
| `WatchProgressStore` | Continuar viendo / `PlaybackPosition` local (implementado por `:data:persistence`). |

**Razón:**
- Los límites por módulo los fuerza el compilador: una feature no PUEDE importar un adaptador aunque alguien lo intente.
- El dominio puro y sin Android permite TDD con fakes detrás de los ports.
- Aísla el riesgo #1 (extracción) detrás de `StreamProvider`, intercambiable sin tocar features.

**Consecuencias:**
- (+) Testabilidad alta: dominio probado con fakes; features sin dependencia de infraestructura.
- (+) El motor de extracción se puede cambiar (andamio NewPipe → motor propio) sin tocar el resto.
- (+) `:app` es el único punto que conoce el cableado completo (DI).
- (−) Más ceremonia de módulos y configuración Gradle que un monolito.
- (−) Disciplina arquitectónica obligatoria: pragmatismo explícito (ver abajo) para no sobre-aplicar ports.

**Pragmatismo (anti-dogma):**
Aplicar ports/adapters SOLO donde hay volatilidad real o valor de aislamiento para testing: **extraction, player, session, persistence**. NO se introducen puertos dogmáticamente en todas partes.

**Alternativas consideradas:**
- **Monolito de un solo módulo por capas técnicas:** descartado; no fuerza límites por compilador y mezcla dominio con infraestructura.
- **Ports & adapters en todos los componentes:** descartado por dogmático; agrega ceremonia sin volatilidad que lo justifique.

**Evidencia (verificada):**
- El motor de extracción es el riesgo #1 del proyecto (ADR-5); aislarlo detrás de un puerto intercambiable es la mitigación arquitectónica directa de ese riesgo.
- La estrategia de andamio (NewPipe → motor propio) detrás del mismo `StreamProvider` solo es viable si existe el puerto, lo que justifica la arquitectura hexagonal.

---

## ADR-5 (Decisión #4) — Motor de extracción propio, con andamio NewPipeExtractor

**Estado:** Aceptada

**Contexto:**
El corazón del producto es resolver streams reproducibles desde InnerTube ignorando los anuncios. Hacerlo bien es el diferenciador central del proyecto, pero también es la pieza más volátil y arriesgada (defensas anti-bot de YouTube). Hay que decidir si construirlo o reutilizar una librería.

**Decisión:**
Construir un **motor de extracción InnerTube propio desde cero**, detrás del puerto `StreamProvider`. Para mitigar el riesgo y secuenciar el trabajo:
1. Iniciar la vertical slice usando **NewPipeExtractor como ANDAMIO/baseline** detrás del MISMO puerto `StreamProvider`.
2. Construir el motor propio EN PARALELO detrás de ese mismo puerto.
3. Reemplazar el andamio cuando el motor propio iguale a la referencia.

**Razón:**
- El motor propio es el diferenciador central del proyecto.
- El andamio NewPipe garantiza una vertical slice funcional desde temprano y sirve de red de seguridad y de referencia de comportamiento mientras maduramos el motor propio.
- El puerto único permite cambiar de motor sin tocar features (habilitado por ADR-4).

**Consecuencias:**
- (+) Vertical slice reproduciendo video real desde el inicio (con andamio), sin esperar al motor propio.
- (+) Diferenciador real: control total del protocolo InnerTube.
- (+) Red de seguridad: si el motor propio falla, el andamio sigue disponible detrás del mismo puerto.
- (−) **Es el RIESGO #1 del proyecto:** PoToken, resolución de la n-signature, BotGuard, visitor-data y bloqueos de tipo "not a bot" exigen mantenimiento casi de guardia (near on-call).
- (−) Dependencia continua de los cambios del lado de YouTube; el motor puede romperse sin aviso.

**Alternativas consideradas:**
- **Usar NewPipeExtractor como motor definitivo:** descartado como objetivo final; anula el diferenciador del producto, aunque se acepta como andamio temporal.
- **No abstraer el motor (acoplarlo a las features):** descartado; impediría el patrón andamio→propio y violaría ADR-4.

**Evidencia (verificada):**
- Las defensas anti-bot de YouTube (PoToken, n-signature, BotGuard, visitor-data, bloqueos "not a bot") son reales y cambiantes, lo que confirma el alto riesgo y la necesidad del andamio como red de seguridad.
- NewPipeExtractor es un extractor de referencia maduro y de código abierto, válido como baseline detrás de `StreamProvider`.

---

## ADR-6 (Decisión #5) — UI: Jetpack Compose for TV

**Estado:** Aceptada

**Contexto:**
Hay que elegir el toolkit de UI para Android TV. Las opciones principales son Leanback (el clásico, usado por SmartTube) y Jetpack Compose for TV (moderno). El target es un único equipo moderno (ADR-1).

**Decisión:**
Usar **Jetpack Compose for TV** (`androidx.tv` tv-material + lazy lists de Compose foundation). Construir nuestra propia UI de controles del reproductor sobre el puerto `MediaPlayer`. **R8 full mode** + un **Baseline Profile** específico de la app son práctica obligatoria desde el día 1.

**Razón:**
- Leanback está oficialmente deprecado; Compose for TV es la dirección oficial y moderna.
- En el C6K (3 GB) el delta de RAM/GC de Compose es negligible, así que no hay penalización práctica.
- R8 full mode + Baseline Profile son las palancas reales de rendimiento de arranque y runtime, agnósticas del lenguaje.

**Consecuencias:**
- (+) Stack de UI moderno, declarativo y alineado con el soporte oficial a futuro.
- (+) Controles de reproductor 100% propios y a medida sobre el puerto `MediaPlayer`.
- (−) Compose for TV es más nuevo que Leanback; menos ejemplos de TV en la comunidad.
- (−) Disciplina obligatoria de R8 + Baseline Profile desde el inicio (no opcional).

**Alternativas consideradas:**
- **Leanback (estilo SmartTube):** descartado; está deprecado oficialmente y no aprovecha el target moderno único.
- **Vistas XML clásicas no-Leanback:** descartadas; sin las facilidades de foco/D-pad de TV y fuera de la dirección oficial.

**Evidencia (verificada):**
- SmartTube es ~99% Java + **Leanback** + ExoPlayer; Leanback está oficialmente **deprecado**, lo que motiva elegir Compose for TV en un proyecto nuevo con target moderno.
- El target C6K tiene 3 GB de RAM (ADR-1), suficiente para que el costo de RAM/GC de Compose sea negligible.

---

## ADR-7 (Decisión #6) — Reproductor: Media3/ExoPlayer como motor, controles propios

**Estado:** Aceptada

**Contexto:**
"El reproductor" no es una sola cosa. Hay que decidir qué partes construimos y cuáles reutilizamos, evitando reimplementar infraestructura genérica ya muy trabajada.

**Decisión:**
Usar **Media3/ExoPlayer** como MOTOR de reproducción (no reimplementarlo). Construir SOLO nuestra propia UI de controles en Compose for TV, sobre el puerto `MediaPlayer`.

Las tres capas del "reproductor":

| Capa | Responsable | ¿Lo construimos? |
| --- | --- | --- |
| 1. Decodificación | Hardware del SoC vía `MediaCodec` | NO — nunca nuestro |
| 2. Motor de reproducción | **Media3/ExoPlayer** | NO — librería de infraestructura |
| 3. UI de controles | Nuestra, en Compose for TV | SÍ — es lo nuestro |

**Configuración de Media3 (en `:data:player`):**
- `MediaCodecSelector` **hardware-first**.
- **`SurfaceView`** (no `TextureView`).
- Preferir **AV1/VP9** (decodificables por hardware en Pentonic 700), luego **H.264**.
- **`MergingMediaSource`** para combinar los streams DASH separados de YouTube (audio-only + video-only).
- `DefaultLoadControl` con tope de memoria (memory-capped).
- `setEnableDecoderFallback(true)`.
- Equipo único conocido → **no se necesita sondeo de códecs por dispositivo**.

**Razón:**
- El motor de reproducción es infraestructura genérica y profundamente trabajada; reimplementarlo sería esfuerzo perdido. Tanto SmartTube como NewPipe usan ExoPlayer.
- La diferenciación está en los controles (capa 3), no en el motor.
- La decodificación la hace el chip (capa 1), no el lenguaje de la app (ver ADR-8).

**Consecuencias:**
- (+) Reproducción robusta sobre infraestructura madura; foco del esfuerzo en lo que diferencia (controles).
- (+) AV1/VP9 por hardware aprovecha el Pentonic 700 (ADR-1).
- (+) `MergingMediaSource` resuelve la naturaleza DASH separada de los streams de YouTube.
- (−) Acoplamiento al modelo de Media3 dentro de `:data:player` (aislado por el puerto `MediaPlayer`, ADR-4).

**Alternativas consideradas:**
- **Reimplementar el motor de reproducción:** descartado; es infraestructura genérica, no aporta diferenciación y multiplicaría el riesgo.
- **`TextureView` en lugar de `SurfaceView`:** descartado; `SurfaceView` es la opción correcta para video en TV (mejor path de composición/rendimiento).

**Evidencia (verificada):**
- Tanto **SmartTube** como **NewPipe** usan **ExoPlayer** como motor de reproducción, confirmando que es la elección de infraestructura correcta.
- El Pentonic 700 del C6K tiene **decodificación AV1 por hardware** (ADR-1), lo que valida preferir AV1/VP9 antes que H.264.
- YouTube entrega streams **DASH separados** de audio-only y video-only, lo que obliga a `MergingMediaSource` para combinarlos.

---

## ADR-8 — Lenguaje: Kotlin 100%; Rust descartado

**Estado:** Aceptada

**Contexto:**
Surge la tentación de usar Rust "por velocidad". Hay que decidir el lenguaje del proyecto con criterio técnico, no por moda.

**Decisión:**
**Kotlin 100%.** Sin Rust, sin código nativo. Stack: Compose for TV, Media3/ExoPlayer, Coil (carga de imágenes con downsampling), OkHttp + kotlinx.serialization (red/JSON), Room y/o DataStore (persistencia), R8 + Baseline Profile.

**Razón:**
- No se puede escribir la capa de UI/app de Android en Rust; la UI es Kotlin/Compose.
- La decodificación de video la hace el decodificador de hardware del SoC, no el lenguaje de la app.
- No hay hot loop de cómputo que acelerar; introducir Rust agregaría complejidad de FFI sin beneficio.
- La velocidad = cuellos de botella entendidos, NO el lenguaje elegido. Las palancas reales (selección de códec, presupuesto de 16ms por frame, presupuesto de memoria, Baseline Profiles + R8) son todas agnósticas del lenguaje.

**Consecuencias:**
- (+) Un solo lenguaje y toolchain; sin frontera FFI ni complejidad nativa.
- (+) Acceso pleno a Compose for TV, Media3 y al ecosistema Android idiomático.
- (−) Ninguna capacidad "de bajo nivel" extra de Rust, pero el análisis muestra que no se necesita.

**Principios de diseño que sostienen la decisión:**
- La velocidad se logra entendiendo los cuellos de botella, no eligiendo el lenguaje.
- Construir desde cero donde se DIFERENCIA (el extractor, ADR-5); usar librería donde es infraestructura genérica (el reproductor, ADR-7).
- La decodificación la hace el chip; la app corre en la TV del usuario; somos el mejor CLIENTE, no un mejor YouTube.

**Alternativas consideradas:**
- **Rust (total o parcial vía FFI):** descartado; no puede escribir la UI de Android, no acelera la decodificación (la hace el SoC) y no hay hot loop que justifique el costo de FFI.
- **C/C++ nativo (NDK):** descartado por las mismas razones; sin hot loop de cómputo, solo agrega complejidad.

**Evidencia (verificada):**
- No se puede escribir la capa de UI/app de Android en **Rust**; la UI de Android es Kotlin/Compose.
- La **decodificación de video la realiza el decodificador de hardware del SoC** (vía `MediaCodec`), no el lenguaje de la app, por lo que cambiar de lenguaje no mejora el rendimiento de reproducción.
- No existe un hot loop de cómputo en el cliente que justifique aceleración nativa.

---

## Decisiones no funcionales (Decisión #7 — Cerrada)

**Estado:** Aceptada (cerrada en la fecha de confirmación)

| Tema | Decisión | Marca |
| --- | --- | --- |
| Testing | **TDD activo:** dominio probado con fakes detrás de los ports. | Confirmada |
| Persistencia local | **Room y/o DataStore** para continuar viendo/favoritos/ajustes (puertos `WatchProgressStore` / `FavoritesStore` / `SettingsStore`). | Confirmada |
| Distribución | **Sideload** al C6K (trivial para el dueño). | Confirmada |
| Mantenimiento | El motor propio es el riesgo #1; el andamio NewPipe es la red de seguridad. | Derivado de ADR-5 |

Estas decisiones no funcionales quedaron confirmadas formalmente en la fecha de confirmación, junto con las anteriores; ya no son una propuesta pendiente de revisión.

---

## ADR-9 (Decisión #8) — Alcance de FASE 1 y detalles de producto

**Estado:** Aceptada

**Contexto:**
ADR-2 fijó el producto (réplica de YouTube TV) y dejó marcada como pendiente la frontera exacta de FASE 1 y varias políticas de producto (calidad, subtítulos, SponsorBlock, idioma, manejo de errores, age-gated, datos locales). En la fecha de confirmación el negocio cerró TODOS esos huecos. Este ADR los registra formalmente como decisiones afirmadas para que el resto de la suite documental y el código partan de la misma frontera.

**Decisión:**
Consolidar el alcance de FASE 1 y los detalles de producto asociados:

1. **Frontera FASE 1 / FASE 2.**
   - FASE 1 (anónima) INCLUYE: home = feed de trending; búsqueda; detalle de video (metadatos + reproducir); reproducción sin anuncios; y videos RELACIONADOS en modo read-only (única adición de navegación respecto del mínimo).
   - FASE 1 EXCLUYE (diferido a FASE 2 o posterior): páginas de canal, playlists, suscripciones sin login, Shorts (si aparecen en feeds se tratan como videos normales, sin UI dedicada), historial remoto y acciones de cuenta.
   - FASE 2 (con cuenta, OAuth device-code) INCLUYE: home personalizada, suscripciones, historial remoto de la cuenta, acciones de cuenta (like, suscribir, añadir a playlist) y SponsorBlock.

2. **Datos locales (FASE 1).** Se guarda LOCALMENTE y SOLO: ajustes, favoritos y continuar viendo (watch progress / `PlaybackPosition`), a través de los puertos de persistencia `SettingsStore`, `FavoritesStore` y `WatchProgressStore` (definidos en `:core:domain`, implementados por `:data:persistence`). NO hay lista completa de historial local; nada sale del dispositivo. El historial remoto es de FASE 2.

3. **Calidad / ABR.** ABR (adaptive bitrate) automático, acotado al hardware del C6K, con preferencia de códec AV1 → VP9 → H.264 (coherente con ADR-7). SIN selector manual de calidad en la primera iteración de FASE 1 (se evalúa después).

4. **SponsorBlock.** Se entrega en FASE 2. El dominio MODELA desde ya los segmentos saltables (`SponsorSegment`) para tener el modelo listo; la integración/adaptador llega en FASE 2. NO en FASE 1 ni en el vertical slice.

5. **Subtítulos / captions.** Diferidos: NO entran en el vertical slice ni en la FASE 1 inicial. El dominio MODELA `captionTracks` / `CaptionTrack`, que puede quedar vacío hasta que se decida e implemente el soporte real.

6. **Idioma / región InnerTube.** `hl=es` (español) y `gl`/región heredada del dispositivo.

7. **Error / vacío / sin red.** `StreamProvider` y los demás puertos de repositorio exponen un resultado TIPADO (sealed) que distingue éxito / fallo-de-extracción / vacío / sin-red. La UI muestra un estado explícito de error/empty/offline con mensaje claro y acción de reintento; la app permanece estable, nunca crashea ni muestra pantalla en blanco.

8. **Contenido restringido / age-gated.** En modo anónimo (FASE 1) no se garantiza: manejo best-effort y, si no es resoluble, mensaje explícito sin disrupción. La resolución real puede requerir cuenta (FASE 2).

9. **Búsqueda.** Sugerencias/autocompletado en FASE 1 solo si el costo es bajo; si no, diferidas. El historial de búsqueda se asocia a la cuenta en FASE 2 (no local en FASE 1).

10. **Mando / D-pad.** Convenciones estándar de Android TV / Google TV: navegación por foco, OK selecciona, BACK retrocede, controles propios de play/pausa/seek durante la reproducción. Sin atajos personalizados en FASE 1.

11. **Metas NFR (medidas SIEMPRE en el C6K real con R8 full + Baseline Profile).**
    - Arranque en frío ≤ 2.5 s hasta home interactivo.
    - Primer frame de video ≤ 1.5 s.
    - PSS ≤ 300 MB sobre 3 GB (objetivo blando).
    - Jank en scroll < 1% de frames.
    - APK release modesto frente a los 32 GB de ROM.

**Razón:**
- Cerrar la frontera elimina la contradicción que arrastraba ADR-2 (¿relacionados/captions/continuar viendo en FASE 1?) y da un alcance accionable para el vertical slice.
- Modelar SponsorBlock y captions desde el dominio (aunque la entrega sea posterior) evita reescrituras y mantiene el dominio estable detrás de los puertos (ADR-4).
- El resultado tipado por puerto hace explícitos error/vacío/offline, sosteniendo la promesa de estabilidad sin pantallas en blanco.
- Las metas NFR fijan una vara medible en el hardware real, coherente con ADR-1/ADR-6.

**Consecuencias:**
- (+) Alcance de FASE 1 inequívoco; el vertical slice y los docs aguas abajo parten de la misma frontera.
- (+) Dominio preparado (`SponsorSegment`, `CaptionTrack`, `PlaybackPosition`) sin pagar todavía el costo de integración.
- (+) Manejo de error/empty/offline uniforme vía resultado sealed en los puertos.
- (−) Algunas features deseables (captions reales, selector manual de calidad, SponsorBlock) quedan explícitamente fuera de la primera entrega.
- (−) Las metas NFR obligan a medir en el C6K real (no en emulador) desde temprano.

**Alternativas consideradas:**
- **Incluir captions y selector manual de calidad en FASE 1:** descartado; suben el alcance del vertical slice sin ser críticos para validar la hipótesis (sin anuncios). Se difieren, con el dominio ya modelado.
- **Guardar historial local completo en FASE 1:** descartado; el historial es de la cuenta (FASE 2). FASE 1 solo persiste ajustes, favoritos y continuar viendo.
- **Selector manual de calidad en vez de ABR:** descartado para la primera iteración; el ABR acotado al hardware del C6K cubre el caso sin UI extra.

**Evidencia (verificada):**
- El Pentonic 700 del C6K decodifica AV1 por hardware (ADR-1), lo que sostiene la cadena de preferencia AV1 → VP9 → H.264 del ABR.
- YouTube entrega streams DASH separados (ADR-7); el ABR opera sobre ese material combinado por `MergingMediaSource`.
- R8 full mode + Baseline Profile son las palancas reales de arranque/runtime (ADR-6/ADR-8), por lo que las metas NFR se miden con ellos activos en el C6K real.

---

## ADR-10 — Pivote a multi-target Android TV y liberación open-source

**Estado:** Aceptada

**Contexto:**
El proyecto arrancó como cliente personal para un único equipo (ADR-1: TCL 55C6K como "piso y techo") y de uso personal por sideload. Con la Fase 1 funcional (VOD y streams en vivo validados on-device en el C6K), el dueño decide cambiar el horizonte: liberar el proyecto como open-source (en la línea de NewPipe/SmartTube) y apuntar a Android TV en general, no a un único equipo.

**Decisión:**
1. **Licencia.** YouTroc pasa a ser **open-source bajo GPL-3.0-or-later**.
2. **Target de hardware.** El objetivo pasa a **multi-target Android TV** (minSdk 26); el **TCL 55C6K queda como dispositivo de referencia/baseline**, no como piso y techo.
3. **Naturaleza del proyecto.** Es un proyecto **serio y de código abierto** —no un ejercicio educativo—, con un disclaimer honesto (cliente no oficial, no afiliado a YouTube/Google; el acceso por medios no oficiales puede infringir sus ToS).

**Razón:**
- La arquitectura ya es Android TV genérica (hexagonal, Media3 con fallback de códec AV1→VP9→H.264), sin lógica atada al C6K, así que multi-target es factible a bajo costo.
- Abrir el código habilita colaboración y pruebas en más equipos (hoy el gap #1 es la validación en un solo dispositivo).
- La licencia GPL-3.0 **no es realmente opcional**: NewPipeExtractor —del que depende la extracción— es GPLv3+ (copyleft), así que el proyecto debe ser GPLv3-compatible; además es la norma del ecosistema.

**Consecuencias:**
- (−) Supersede la parte de **ADR-1** que fijaba "único dispositivo / piso y techo" (el C6K ahora es baseline de referencia).
- (−) Deja sin efecto el encuadre de "uso estrictamente personal / no redistribuir"; el sideload sigue siendo el método de distribución, ahora vía **releases públicas en GitHub**.
- (−) Aparecen necesidades nuevas: `LICENSE`, `CONTRIBUTING`, CI, plantillas de issue/PR, versionado semántico (primer release **v0.1.0-alpha**) y compatibilidad más amplia a validar con la comunidad.
- (+) **No cambia** la ideología ad-free-por-construcción (ADR-0) ni el resto del stack.

**Alternativas consideradas:**
- **Seguir personal/privado:** descartado por decisión del dueño.
- **Licencia permisiva (MIT/Apache):** descartada; **incompatible** con el copyleft GPLv3+ de NewPipeExtractor.

**Evidencia (verificada):**
- El POM de `com.github.TeamNewPipe:NewPipeExtractor:v0.26.3` declara "GNU General Public License v3.0 or later".
- Reproducción VOD + live validada on-device en el C6K.

---

## Resumen de trazabilidad

| ADR | Decisión | Estado |
| --- | --- | --- |
| ADR-0 | Cliente propio desde cero (no patching) | Aceptada |
| ADR-1 | Target único TCL 55C6K | Aceptada |
| ADR-2 (#1) | Producto: réplica de YouTube TV | Aceptada |
| ADR-3 (#2) | Cuenta/sesión por fases (`SessionGateway`) | Aceptada |
| ADR-4 (#3) | Arquitectura multi-módulo hexagonal+screaming+clean | Aceptada |
| ADR-5 (#4) | Motor de extracción propio con andamio NewPipe | Aceptada |
| ADR-6 (#5) | UI Compose for TV | Aceptada |
| ADR-7 (#6) | Reproductor Media3/ExoPlayer, controles propios | Aceptada |
| ADR-8 | Kotlin 100%; Rust descartado | Aceptada |
| ADR-9 (#8) | Alcance de FASE 1 y detalles de producto | Aceptada |
| #7 | No funcionales (TDD, persistencia, distribución) | Cerrada (Aceptada) |
| ADR-10 | Pivote a multi-target Android TV y liberación open-source (GPL-3.0-or-later) | Aceptada |
