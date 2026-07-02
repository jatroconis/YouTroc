# Changelog

Todos los cambios notables de YouTroc. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

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
