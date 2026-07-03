# Changelog

Todos los cambios notables de YouTroc. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

### Added
- **Motor de extracción InnerTube propio** para búsqueda, detalle de video y extracción de streams VOD, migrado por capas *(strangler-fig)* detrás de los mismos puertos de dominio que ya existían. Cada capacidad prueba primero el motor propio y cae automáticamente a NewPipeExtractor si falla (own-first, NewPipe de respaldo). Los streams VOD se resuelven vía el cliente `ANDROID_VR` (sin cipher ni PoToken) y se ensamblan en un DASH MPD propio. Los streams en vivo siguen usando NewPipe.
- **HDR10 / HLG en el reproductor**: cuando el stream resuelto trae HDR y la pantalla lo soporta, el reproductor pide modo de color HDR (`Window.setColorMode`); si no se cumple alguna condición, la reproducción sigue en SDR sin cambios. *(Render HDR en pantalla real: validación on-device pendiente.)*
- **Prefetch especulativo del siguiente video**: mientras se reproduce el video actual, YouTroc resuelve en segundo plano el stream del primer video de "A continuación", para que avanzar a él sea casi instantáneo. Es conservador: un solo prefetch a la vez, solo después de reproducción estable, y se salta si el video actual cayó al fallback de NewPipe (para no sumar carga extra). *(Ganancia de latencia percibida: validación on-device pendiente.)*
- **Escalera de clientes para streams VOD**: la extracción prueba `ANDROID_VR → iOS → NewPipe` en orden. Si el cliente `ANDROID_VR` queda limitado por YouTube, el cliente `iOS` suele resolver el stream igual, evitando caer hasta NewPipe. Es una mejora monótona: solo agrega casos resueltos, nunca quita los que ya funcionaban.

### Changed
- **Home**: el feed principal dejó de ser el kiosco Trending genérico de NewPipe; ahora muestra un shelf **"Popular en {ciudad/región}"** armado por el motor propio, con NewPipe como *fallback*. El "Trending" global real fue retirado por YouTube del lado del servidor; este feed regional es el reemplazo funcional disponible hoy. *(Código listo; verificación visual on-device pendiente.)*

### Known limitations
- El shelf regional del Home depende de la señal de región/red que recibe la búsqueda semilla (config. regional del dispositivo); la etiqueta de ciudad/región y su contenido los arma YouTube del lado del servidor — no hay geolocalización propia en el cliente.
- HDR10/HLG y el prefetch especulativo están completos a nivel de código pero **sin validar en dispositivo real** (render HDR y ganancia de latencia percibida, respectivamente).

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
