## Qué cambia
Descripción breve del cambio y su motivación. Enlazá el issue si aplica (`Closes #...`).

## Cómo probarlo
Pasos para verificar. Si tocaste UI/reproductor, indicá en qué dispositivo lo probaste.

## Checklist
- [ ] Respeta la regla de dependencias hacia `:core:domain` (sin tipos de framework en el dominio).
- [ ] Las features solo dependen de puertos (`:core:domain`/`:core:ui`), no de `:data:*`.
- [ ] `./gradlew test` pasa; lógica pura nueva con tests (TDD).
- [ ] `./gradlew :app:assembleDebug` compila.
- [ ] Commits con Conventional Commits.
- [ ] Sin foco D-pad atrapado en pantallas/paneles nuevos (validado en dispositivo si es UI).
- [ ] Documentación actualizada si cambió comportamiento o arquitectura.
