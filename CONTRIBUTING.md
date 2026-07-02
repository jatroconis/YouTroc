# Contribuir a YouTroc

¡Gracias por tu interés! YouTroc es un proyecto **open-source** que apunta a ser una alternativa real y bien construida. Toda ayuda suma — desde probar en un TV distinto hasta corregir un bug o sumar una feature.

## Formas de contribuir

- **Probar en tu Android TV** y reportar el resultado (modelo, versión de Android TV, qué funcionó y qué no). Es lo más valioso hoy: solo se validó en un equipo.
- **Reportar bugs** con pasos de reproducción, logs (`adb logcat`) y datos del dispositivo.
- **Mejorar la documentación** (`docs/`, README, este archivo).
- **Código**: correcciones y features alineadas con el roadmap (`docs/05-roadmap-and-risks.md`).

## Antes de escribir código, leé el diseño

La fuente de verdad vive en [`docs/`](./docs/README.md):

- `01-business-decisions-adr.md` — las decisiones raíz (ADRs). Si algo contradice un ADR, gana el ADR.
- `04-architecture-and-design.md` — el grafo de módulos y la regla de dependencias.
- `06-glossary-and-domain-model.md` — el vocabulario del dominio.
- `07-ui-design.md` — el diseño de UI para TV.

## Reglas de arquitectura (no negociables)

YouTroc es **Hexagonal + Screaming + Clean**. Respetá esto o el PR no entra:

1. **La regla de dependencias apunta a `:core:domain`.** El dominio es Kotlin/JVM **puro**: cero imports de Android, Media3, NewPipe, Compose. Si un tipo de framework aparece en `:core:domain`, está mal.
2. **Las features hablan con puertos, no con adaptadores.** `:feature:*` depende de `:core:domain` (+ `:core:ui`), **nunca** de `:data:*`.
3. **Los adaptadores no se filtran entre sí.** `:data:player` no conoce NewPipe; `:data:extraction` no conoce Media3.
4. **Container-presentational.** ViewModel + `StateFlow<UiState>` (sealed) + composable presentacional sin lógica.
5. **DI manual** por factories (sin Hilt), cableada solo en `:app`.

## Testing (TDD)

- El **dominio y los mapeos puros** se testean con TDD: escribí el test que falla primero (RED), después el código mínimo (GREEN). Fakes detrás de los puertos, sin mocks de framework.
- La **UI de Compose** (foco D-pad, scroll, navegación) se valida **en dispositivo real**, no con tests unitarios — es la convención del proyecto.
- Corré `./gradlew test` antes de abrir el PR. La build de release (`./gradlew :app:assembleRelease`) debe pasar con R8.

> **Ojo con el foco D-pad**: es el riesgo #1 de UI en TV. Contené el foco por contenedor (`focusGroup()` + `focusProperties { exit = Cancel }`), nunca ancles foco en un item de una lista perezosa, y movelo con `LaunchedEffect`-después-de-componer.

## Estilo y commits

- **Kotlin** idiomático, coherente con el código existente.
- **Artefactos en inglés** por defecto (identificadores, comentarios); la UI copy y los docs van en español (convención del repo).
- **[Conventional Commits](https://www.conventionalcommits.org/)**: `feat(playback): ...`, `fix(extraction): ...`, `docs: ...`, `refactor: ...`, `test: ...`.
- Commits chicos y enfocados; un cambio lógico por commit.

## Flujo de Pull Request

1. Forkeá y creá una rama descriptiva (`feat/live-quality-menu`).
2. Hacé el cambio con sus tests; corré `./gradlew test`.
3. Abrí el PR contra `master` con una descripción clara (qué, por qué, cómo probarlo). Completá la plantilla.
4. Enlazá el issue si aplica.

## Licencia de tus contribuciones

Al contribuir aceptás que tu aporte se licencie bajo **GPL-3.0-or-later**, igual que el resto del proyecto.
