# Changelog

Todos los cambios notables de YouTroc. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

## [0.2.0-alpha] - 2026-07-05

Segundo release público (pre-release). El reproductor se rediseñó estilo Netflix, el Home
pasó de un shelf a un feed completo tipo YouTube y la extracción VOD corre sobre motor
propio. Validado en el equipo de referencia (TCL 55C6K), salvo donde se indica.

### Added
- **Home multi-estantería** tipo YouTube: "Seguir viendo", Tendencias regional, Música,
  Videojuegos, Noticias, Deportes, Cine y tráilers, En vivo y Shorts. Carga progresiva
  (Tendencias primero; el resto se agrega al llegar sin robar el foco), aislamiento por
  estantería (una que falla no bloquea a las demás) y timeouts medidos en dispositivo real.
- **Historial local de reproducción** (título, canal y fecha; solo en el dispositivo, sin
  cuenta ni nube) que alimenta la estantería **"Seguir viendo"**: excluye videos terminados
  (≥95 %) y transmisiones en vivo, y reanuda donde quedaste.
- **Shorts**: fila de tarjetas verticales en el Home y reproductor vertical propio
  (D-pad ABAJO/ARRIBA cambia de short, BACK sale); los Shorts no escriben historial.
- **Rediseño Netflix del reproductor**: tres zonas de foco (timeline enfocable → controles →
  "A continuación"), scrubbing desacoplado — el cursor de preview se mueve libre por TODO el
  timeline (cargado o no) y el seek se confirma una sola vez al soltar — y la barra sostiene
  el destino mientras carga (un segundo avance continúa desde ahí, sin devolverse).
- **Miniaturas de preview al adelantar** (storyboards): al mover el cursor se ve el fotograma
  del punto de destino, como en Netflix/Prime. Usa las hojas de sprites oficiales del video;
  si un video no las trae (o es en vivo), la barra queda exactamente como antes.
- **Recuperación automática de calidad**: si una calidad fijada (p. ej. 4K) devuelve HTTP 403
  del lado del servidor, el reproductor baja un escalón y sigue desde la misma posición —
  antes quedaba pantalla negra permanente.
- **Motor de extracción InnerTube propio** para búsqueda, detalle de video y extracción de streams VOD, migrado por capas *(strangler-fig)* detrás de los mismos puertos de dominio que ya existían. Cada capacidad prueba primero el motor propio y cae automáticamente a NewPipeExtractor si falla (own-first, NewPipe de respaldo). Los streams VOD se resuelven vía el cliente `ANDROID_VR` (sin cipher ni PoToken) y se ensamblan en un DASH MPD propio. Los streams en vivo siguen usando NewPipe.
- **Escalera de clientes para streams VOD**: la extracción prueba `ANDROID_VR → iOS → NewPipe` en orden. Si el cliente `ANDROID_VR` queda limitado por YouTube, el cliente `iOS` suele resolver el stream igual, evitando caer hasta NewPipe. Es una mejora monótona: solo agrega casos resueltos, nunca quita los que ya funcionaban.
- **HDR10 / HLG en el reproductor**: cuando el stream resuelto trae HDR y la pantalla lo soporta, el reproductor pide modo de color HDR (`Window.setColorMode`); si no se cumple alguna condición, la reproducción sigue en SDR sin cambios. *(Render HDR en pantalla real: validación on-device pendiente.)*
- **Prefetch especulativo del siguiente video**: mientras se reproduce el video actual, YouTroc resuelve en segundo plano el stream del primer video de "A continuación", para que avanzar a él sea casi instantáneo. Es conservador: un solo prefetch a la vez, solo después de reproducción estable, y se salta si el video actual cayó al fallback de NewPipe (para no sumar carga extra). *(Ganancia de latencia percibida: validación on-device pendiente.)*

### Changed
- **Home**: el feed dejó de ser el kiosco Trending genérico de NewPipe. La estantería de
  Tendencias es regional (**"Popular en {ciudad/región}"**, motor propio) y su *fallback* de
  NewPipe ahora usa búsqueda del término regional en lugar del kiosco (ver Fixed).
- **Controles del reproductor**: quedaron exactamente play/pausa y ⚙ — los botones
  anterior/siguiente (que no hacían nada) se eliminaron, y el foco ya no puede escaparse del
  overlay con IZQUIERDA/DERECHA.

### Fixed
- **Pantalla negra al fijar 4K y adelantar**: el segmento 2160p puede devolver 403 del lado
  del servidor (limitación por video del cliente `ANDROID_VR`); ahora se degrada de calidad
  en vez de matar la reproducción.
- **"A veces sale el live" en lugar de Tendencias** (presente desde 0.1.0-alpha): el kiosco
  por defecto de NewPipe pasó a resolver "Live" cuando YouTube retiró la página clásica de
  Trending, y el *fallback* etiquetaba directos como Tendencias. Corregido con el fallback
  por búsqueda regional.
- **Menú de calidad sin foco**: al abrir ⚙ el foco podía no entrar al popup (contención del
  grupo de foco de los controles); ahora el menú recibe el foco siempre.
- **Seek limitado al buffer**: ya se puede saltar a cualquier punto del video, no solo hasta
  donde había cargado el timeline.

### Known limitations
- El contenido regional depende de la señal de región/red que recibe la búsqueda semilla
  (config. regional del dispositivo); la etiqueta de ciudad/región y su contenido los arma
  YouTube del lado del servidor — no hay geolocalización propia en el cliente.
- HDR10/HLG y el prefetch especulativo siguen **sin validar en dispositivo real** (render HDR
  y ganancia de latencia percibida, respectivamente).
- Las tarjetas de Shorts no muestran insignia "Nuevo" ni loop automático del short (sin señal
  de datos / soporte del puerto todavía).
- APK **debug-signed** (alpha); probado únicamente en TCL 55C6K.

## [0.1.0-alpha] - 2026-07-02

Primer release público (pre-release). Software temprano: reproduce de verdad, pero
varias funciones están completas a nivel de código y aún sin validar en dispositivo,
y hasta ahora solo se probó en el TCL 55C6K.

### Added
- **Catálogo Trending** en el Home vía extracción anónima (NewPipeExtractor).
- **Búsqueda** de videos con teclado en pantalla.
- **Reproductor propio** en Compose for TV: overlay hecho a mano (transporte, scrubber,
  auto-hide, gestión de foco D-pad).
- **Reproducción VOD** con DASH adaptativo / progresivo y ABR acotado al hardware.
- **Reproducción en vivo** (HLS / DASH-live) con indicador "EN VIVO". *(validado en C6K)*
- **Selector de calidad** estilo YouTube: ⚙ → Ajustes → Calidad, con lista acotada y scroll.
- **Panel "A continuación"** dentro del reproductor (info + relacionados), revelado por D-pad.
- **Continuar viendo**: persistencia local de la posición de reproducción (DataStore).
- Arquitectura multi-módulo Hexagonal + Screaming + Clean; ad-free *por construcción*.

### Known limitations
- Validado únicamente en **TCL 55C6K**; otros Android TV sin probar.
- Menú de calidad, panel "A continuación" y "continuar viendo" **sin validación on-device**.
- El motor de extracción es todavía el andamio **NewPipeExtractor** (motor InnerTube propio pendiente).
- Sin cuenta / login (Fase 2): sin home personalizado, suscripciones, subtítulos ni SponsorBlock.

[Unreleased]: https://github.com/jatroconis/YouTroc/compare/v0.1.0-alpha...HEAD
[0.1.0-alpha]: https://github.com/jatroconis/YouTroc/releases/tag/v0.1.0-alpha
