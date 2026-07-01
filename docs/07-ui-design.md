# Diseño de UI — youtroc

> Cómo se construye la interfaz: replicar el look del YouTube-for-TV **actual** (2025-2026) con Jetpack Compose for TV, mapeado a la arquitectura hexagonal + screaming + atomic design del proyecto. Verificado contra fuentes primarias.

---

## 1. El diseño objetivo

youtroc replica el **YouTube-for-TV moderno**, el opuesto deliberado del look Leanback de SmartTube:

| | YouTube TV actual (lo que hacemos) | SmartTube (lo que rechazamos) |
|---|---|---|
| Navegación | **Rail vertical delgado a la izquierda** que se expande con el foco (íconos → íconos+etiquetas) | Columna fija de categorías + filas planas (Leanback) |
| Home | **Hero inmersivo** arriba + **shelves** (carruseles) de cards redondeadas | `ListRow` planas |
| Cards | 16:9 redondeadas (8dp), escalan (1.1x) + glow al enfocar | Cards Leanback |
| Player | Overlay propio estilo YouTube dic-2025 (título arriba-izq, 3 zonas) | Controles Leanback |

Base técnica: **`androidx.tv:tv-material 1.1.0` (ESTABLE, 2026-05-06, paquete `androidx.tv.material3`)** + los lazy layouts de `androidx.compose.foundation`.

---

## 2. Decisiones de diseño confirmadas

- **Look:** YouTube-for-TV moderno (rail + hero inmersivo + shelves + player propio). CONFIRMADO.
- **Hero inmersivo: hand-built focus-driven.** Se construye a mano (`Box` + `AsyncImage` + scrim + estado de foco). Se **descarta `Carousel`** para el hero por depender de `@ExperimentalTvMaterial3Api`; queda como posible variante futura aislada. CONFIRMADO.
- **Toolkit:** Jetpack Compose for TV (`androidx.tv`), coherente con la Decisión #5. CONFIRMADO.
- **Patrón de UI:** atomic design + container-presentational + UDF con `sealed UiState`. CONFIRMADO.
- **Referencia de código:** JetStream (`android/tv-samples`, Apache-2.0), reemplazando su tab bar superior por el `NavigationDrawer`. CONFIRMADO.

---

## 3. Realidades 2026 de `androidx.tv` (obligatorias)

Estos hechos están verificados y contradicen la mayoría de tutoriales viejos. Ignorarlos cuesta días:

1. **Pinear `tv-material 1.1.0`** (estable), paquete `androidx.tv.material3`.
2. **`ImmersiveList` fue ELIMINADO** (en `1.0.0-beta01`) → el hero se construye a mano. No es un drop-in.
3. **`TvLazyRow` / `TvLazyColumn` / `TvLazyVerticalGrid` ELIMINADOS** → usar los `Lazy*` de `androidx.compose.foundation`.
4. **`Carousel` y los `Chip` siguen `@ExperimentalTvMaterial3Api`** aun en estable → `@OptIn` aislado detrás de un solo átomo (por eso el hero es hand-built).
5. **API de foco renombrada:** `enter/exit` → `onEnter/onExit`; `cancelFocus()` → `cancelFocusChange()`. El JetStream viejo y muchos blogs usan la forma vieja.
6. **Trampa de docs:** `developer.android.com/training/tv/playback/compose` todavía muestra `1.0.0` y componentes eliminados. No copiar de ahí.

---

## 4. Wireframes

### Home (trending)
```
+----+---------------------------------------------------------------+
| Q  |  ####### HERO INMERSIVO (backdrop 16:9, cambia con foco) ##### |
| ⌂  |  ##  [scrim]  TENDENCIA                                    ## |
| ▤  |  ##  Título grande del video destacado                    ## |
| ⚙  |  ##  descripción corta...              [ ▶ Reproducir ]    ## |
|    |  Trending                                                     |
|    |  [card][card][CARD*][card][card] ->                           |
|    |  Música                                                       |
|    |  [card][card][card][card][card]  ->                           |
+----+---------------------------------------------------------------+
 rail colapsado = solo íconos; al enfocarlo se expande con scrim:
 [ Q Search · ⌂ Home · ⚙ Settings ]
 CARD* = card enfocada: scale 1.1x, esquinas 8dp, border + glow
```

### Búsqueda
```
+----+---------------------------------------------------------------+
| Q  |  [  campo de búsqueda ______________________ ]                |
| ⌂  |  +---- teclado D-pad en pantalla ----+  Sugerencias/Historial |
|    |  | q w e r t y u i o p               |  - consulta previa 1    |
|    |  | a s d f g h j k l                 |  - consulta previa 2    |
|    |  | z x c v b n m   <- espacio ->     |                         |
|    |  Resultados (LazyVerticalGrid de cards)                        |
|    |  [card][card][card][card]                                      |
+----+---------------------------------------------------------------+
```

### Detalle de video (read-only + relacionados)
```
+----+---------------------------------------------------------------+
| ⌂  |  #### backdrop / thumbnail grande 16:9 (scrim) ####           |
|    |  Título del video (Headline)                                  |
|    |  Canal · 1.2M vistas · hace 3 días                            |
|    |  [ ▶ Reproducir ]  [ ♥ Like ]  [ + Guardar ]  (read-only F1)  |
|    |  Descripción (truncada, expandible)...                        |
|    |  A continuación / Relacionados                                |
|    |  [card][card][card][card][card]  ->                           |
+----+---------------------------------------------------------------+
```

### Player a pantalla completa (overlay custom sobre ExoPlayer, look YouTube dic-2025)
```
+---------------------------------------------------------------------+
| Título del video (arriba-izquierda, no clickable)                   |
|                   [ superficie de video full-screen ]               |
|  ===================== scrubber =========|=====   12:34 / 20:00     |
|  [Canal (Descripción) (Suscribir)]  ( |< ) ( ⏸ ) ( >| )            |
|                                     [♥][👎][💬][CC][⚙]  <- pills    |
|  zona IZQUIERDA        transporte CENTRO (pausa centrada)  zona DER  |
+---------------------------------------------------------------------+
 D-pad L/R: 1ra vez revela el overlay; 2da vez hace seek. BACK cierra.
```

---

## 5. Mapeo componente por componente

| Elemento visual | Componente `androidx.tv` | Notas |
|---|---|---|
| Rail izquierdo colapsable | `ModalNavigationDrawer` | Estable. El switch ícono-solo vs ícono+etiqueta es lógica propia según `DrawerValue`. Manejar BACK para colapsar |
| Ítem del rail | `NavigationDrawerItem` | `leadingContent` = ícono siempre visible; texto cuando `DrawerValue.Open` |
| Hero inmersivo | **Hand-built:** `Box` + `AsyncImage` + scrim + `Text` + `Button`, sobre un `LazyRow` de cards; índice enfocado hoisteado a estado que dispara `AnimatedContent`/`Crossfade` del backdrop | `ImmersiveList` fue eliminado |
| Stack de shelves | `androidx.compose.foundation.lazy.LazyColumn` | NO `TvLazyColumn`. `key = { it.id }` |
| Fila de cada shelf | `androidx.compose.foundation.lazy.LazyRow` + `Modifier.focusGroup()` | `contentPadding` para respiro; `BringIntoViewSpec` custom para pivote ~30% |
| Card de video | `ClassicCard` / `StandardCardContainer` | Estable. Radio default 8dp (customizable). `focusedScale` default 1.1f. Estilo vía `CardScale`/`CardBorder`/`CardGlow` |
| Tile custom focuseable | `Surface(onClick=...)` | Primitiva atómica de foco; centralizar tokens de scale/glow/border acá |
| Grid de resultados | `androidx.compose.foundation.lazy.grid.LazyVerticalGrid` | Keys estables; thumbnails con tamaño acotado |
| Input / sugerencias / historial | `BasicTextField`/`OutlinedTextField` + `ListItem`/`DenseListItem` | Teclado D-pad = grid custom de teclas focuseables |
| Chips de tema (opcional) | `FilterChip`/`AssistChip` | `@ExperimentalTvMaterial3Api` — diferido |
| CTA primario (Play) | `WideButton` / `Button` | Estable |
| Acciones secundarias (Like/Save) | `IconButton` / `Button` | Read-only/visual en Fase 1 |
| Texto (título/canal/meta) | `Text` (`androidx.tv.material3`) | NO mezclar `material3` mobile con `tv-material` |
| Tema / esquema oscuro | `MaterialTheme` + `ColorScheme` + `Typography` + `Shapes` (`androidx.tv.material3`) | Paleta oscura, acento rojo, tokens overscan |
| Superficie de video | ExoPlayer (`androidx.media3`) vía `AndroidView` / `PlayerSurface` | No existe player tv-material |
| Controles del player | **Overlay Compose custom:** `Surface` pill + `IconButton` + `Slider` + `Text`; D-pad vía modificador `handleDPadKeyEvents` en `KeyUp` | Emula look YouTube dic-2025 |

---

## 6. Arquitectura de UI

Flechas de dependencia hacia adentro: `:feature -> :core:domain <- :data`; `:feature -> :core:ui`.

### Atomic design → módulos
- **`:core:ui`** (design system; depende SOLO de Compose + `androidx.tv`; NO conoce dominio ni data; todo `@Preview`-able sin device):
  - **Átomos:** `TvCard` (envuelve `ClassicCard`/`StandardCardContainer` con 8dp + `CardScale`/`CardBorder`/`CardGlow`), `FocusableThumbnail` (`Surface` + Coil `AsyncImage` + `onFocusChanged`), `NavRailItem`, `Pill`/`Chip`, tokens de `Text`/`Icon`. `Surface` es LA primitiva atómica de foco.
  - **Moléculas:** `ShelfRow` (`LazyRow` + `focusGroup` + `SectionHeader` + `itemsIndexed` con keys estables), `SectionHeader`, y los modificadores compartidos de foco/D-pad (`handleDPadKeyEvents`, `focusOnInitialVisibility`, focus-restorer).
- **`:feature:{catalog, search, video, playback}`**: organismos (`FeaturedHero`, `TrendingShelfList`, `VideoDetailHeader`, `PlayerControls`), templates (scaffolds sin estado), y **Screen** (container) + **ViewModel**. En tiempo de compilación NO pueden importar `:data:*`.
- **`:app`**: composition root — el `NavHost`, DI (`@Binds` puerto → adaptador), theme host, y el `ModalNavigationDrawer` envolviendo el `NavHost`.

### Container-presentational (verbatim de JetStream)
- **`<Feature>Screen` = CONTAINER** (en `:feature`): `hiltViewModel()`; `val state by vm.uiState.collectAsStateWithLifecycle()`; `when(state)` despacha a un `Content(...)` sin estado, o `Loading()`/`Error()`/`Empty()`/`Offline()`; hoistea la navegación como callbacks.
- **`<Feature>Content` = PRESENTACIONAL** (usa solo `:core:ui`): `@Composable` puro que recibe estado inmutable + callbacks; sin ViewModel, sin puerto, sin Hilt. Cada `Content` tiene `@Preview` con estado fake — el pago del split.

### UDF + estado sellado
- Por pantalla: `sealed interface <Feature>UiState { data object Loading; data class Content(...); data object Empty; data class Error(reason); data object Offline }`.
- El ViewModel inyecta las **interfaces** de `:core:domain` (p. ej. `HomeViewModel @Inject constructor(private val videoCatalog: VideoCatalog)`), arma el estado con `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`, y mapea el `sealed UiState` **1:1 desde el `StreamResult` sellado** del dominio (`Success`/`NotAvailable`/`Offline`/`Error`) → los modos de fallo del stream/player son estados de UI de primera clase. Estado baja (`StateFlow`), eventos suben (callbacks). La frontera hexagonal vive en el constructor del ViewModel; DI liga puerto→adaptador en `:app`.

### Navegación
`androidx.navigation:navigation-compose`: un único `NavHost` en `:app` con rutas type-safe (`@Serializable`). Las pantallas reciben la navegación como callbacks (`onVideoClick`/`goToPlayer`/`onBack`). El `ModalNavigationDrawer` envuelve el `NavHost`. El player es un **destino top-level propio** (su overlay inmersivo ocupa toda la superficie). **NO** copiar el `DashboardTopBar` (tab bar) de JetStream — es el look que rechazamos.

---

## 7. Referencia de código

- **JetStream** (`android/tv-samples`, Apache-2.0) — referencia canónica. **Copiar:** container-presentational + `sealed UiState` + UDF (`HomeScreen.kt`/`HomeScreenViewModel.kt`), patrón de hero (`FeaturedMoviesCarousel.kt`), shelves (`MoviesRow.kt`), familia `Card`, el `VideoPlayer`, y los utils de foco/D-pad (`ModifierUtils.kt`). **Reemplazar** su `DashboardTopBar` por el `NavigationDrawer` y portar el código de foco a `onEnter`/`onExit`.
- **`TvMaterialCatalog` + `ImmersiveListSamples.kt`** (`android/tv-samples`) — cada componente aislado; el `NavigationDrawer` y el patrón hand-built del hero.
- Clientes GPL (familia Jellyfin) — **solo estudiar, no copiar código** (licencia incompatible).

---

## 8. Alcance de UI en Fase 1

**Entra:**
1. Shell — `ModalNavigationDrawer` (rail colapsado, expand-on-focus, scrim) envolviendo el `NavHost`. Ítems del rail: **Search, Home, Settings** (no Suscripciones/Biblioteca: Fase 1 es anónima). Tema oscuro tv-material, acento rojo, tokens overscan.
2. Home = trending — hero inmersivo hand-built + `LazyColumn` de `ShelfRow` (shelves derivados de trending: "Trending", "Música", "Gaming"). Shorts fuera del feed.
3. Búsqueda — campo + teclado D-pad + sugerencias/historial (`ListItem`) + `LazyVerticalGrid` de resultados.
4. Detalle de video + relacionados (**read-only**) — header inmersivo, metadata, CTA Play que lanza el player, shelf de relacionados. Like/Save/Subscribe visuales/no-op.
5. Player a pantalla completa — ExoPlayer + overlay custom (título arriba-izq, 3 zonas, scrubber; D-pad L/R revela y luego hace seek; BACK cierra). Destino top-level.
6. Transversal — render de `sealed UiState` (Loading/Content/Empty/Error/Offline) en cada pantalla, focus restoration a la card exacta al volver del rail, `@Preview` en todos los presentacionales.

**Diferido:** previews inline al enfocar; escrituras/cuenta (sign-in, Suscripciones, Biblioteca, Like/Save/Subscribe reales, comentarios); voz; `Chip`s; `TabRow`; Shorts.

---

## 9. Lenguaje visual

- **Tema:** Dark Material 3 vía `androidx.tv.material3 MaterialTheme` (NO mezclar con `compose.material3` mobile). Superficies casi negras; elevadas apenas más claras. **Acento rojo YouTube** para estados activo/seleccionado/foco. Alto contraste para 10-foot.
- **Tipografía:** Roboto, escala TV (Display/Headline/Title/Body/Label). Headline para hero y headings; Title para títulos de card; Body/Label para canal + metadata. Truncar con elipsis (sin wrap).
- **Spacing / grid:** márgenes overscan-safe **58dp laterales / 28dp arriba-abajo** (5%). Grid de 12 columnas (52dp col, 20dp gutter). Anchos de card recomendados (los fija el dev): 268dp 3-up, 196dp 4-up, 124dp 5-up.
- **Cards:** thumbnail 16:9, esquinas **8dp** (default customizable), bloque de contenido = ancho del thumbnail. Foco: scale 1.1x + border (solo en foco) + glow (2-32dp). Pills del player: forma totalmente redondeada, look frosted.
- **Precisión:** YouTube no publica specs exactos; los números son los valores de referencia de la guía oficial de Android TV. **Validar contra screenshots reales en el TCL 55C6K.**

---

## 10. Foco y movimiento (D-pad)

- **Modelo:** `Surface` es la primitiva de foco; `Card`/`Button`/`ListItem`/`NavigationDrawerItem` se construyen sobre ella → tokens de scale/glow/border centralizados en `:core:ui`. `Modifier.focusable()` en átomos; `Modifier.focusGroup()` por shelf y por panel de contenido; `onFocusChanged` dispara el swap del backdrop del hero.
- **Focus restoration:** al volver del rail, el foco cae en la MISMA card. `Modifier.focusRestorer()` (aún `@ExperimentalComposeUiApi` — pinear) o `focusProperties` + `saveFocusedChild()`/`restoreFocusedChild()`. **Port 2026:** `enter/exit` → `focusProperties { onEnter { }; onExit { } }`; `cancelFocus()` → `cancelFocusChange()`. Foco inicial vía `FocusRequester` + `Modifier.onPlaced { requestFocus() }` una vez.
- **Rail expand-on-focus:** `rememberDrawerState(DrawerValue.Closed)`; el `drawerContent` renderiza rail compacto (Closed) o con etiquetas (Open); el switch es lógica propia. Abrir al enfocar el rail; BACK colapsa.
- **Scale/motion:** configurar foco vía `CardDefaults`/`ClickableSurfaceDefaults` (no `animateFloatAsState` a mano). Scale ~1.1x, border ~3dp (solo foco), glow (2-32dp). Timing propio ~120-180ms.
- **Edge-scroll pivot:** `contentPadding` en cada `LazyRow`/`LazyColumn` para que la card y el glow no se recorten en overscan; `BringIntoViewSpec` custom vía `LocalBringIntoViewSpec` para pivote fijo ~30% desde la izquierda (`pivotOffsets` fue eliminado).
- **Player D-pad:** interceptar con `handleDPadKeyEvents` en `KeyUp` (evita doble disparo): 1er L/R revela overlay, 2do hace seek; CENTER = play/pause; BACK cierra. Auto-hide por inactividad; long-press = fast-seek.

---

## 11. Guardas de rendimiento (día 1, en el C6K de 3GB)

No son gold-plating: sin esto, los shelves con imágenes hacen jank y arriesgan OOM.
- **Baseline Profile** propio (el de tv-material + uno generado con Macrobenchmark flingeando el `LazyColumn` del Home) + R8 full en release.
- **Thumbnails con tamaño explícito** (`size`/`aspectRatio`, **nunca `wrapContentSize`** → fuerza decode full-res = OOM real) para que Coil 3 los downsamplee al tamaño de la card.
- `MemoryCache.maxSizePercent` ~0.20-0.25, un solo `ImageLoader` compartido, pedir URLs de thumbnail del tamaño de la card, decodificar thumbs opacos como `RGB_565`.
- **Keys estables** en cada `items()`, modelos inmutables, hoistear lambdas `onClick`, pasar URLs como `String` (no `Painter`) para preservar strong-skipping.
- Prefetch de Compose 1.9 (`LazyLayoutCacheWindow`) tuneado conservador.

---

## 12. Riesgos y esfuerzo

- **Hero inmersivo = trabajo real** (no drop-in): hoisting de foco, `AnimatedContent` del backdrop, expand de altura, scrim. Referencia: `ImmersiveListSamples.kt` + JetStream.
- **APIs experimentales aisladas:** si en el futuro se usa `Carousel`/`Chip`, encapsularlos con `@OptIn` detrás de un átomo.
- **Player = objetivo VISUAL, no componente:** el look YouTube dic-2025 se emula con overlay Compose sobre Media3; validar el layout real en el C6K (rolling server-side, varía por device/cuenta).
- **Docs viejas = trampa:** ignorar `TvLazy*`/`ImmersiveList`/`enter=exit=` de tutoriales y de `developer.android.com/training/tv/...`.
- **Rendimiento en 3GB = riesgo de primera clase:** las guardas de la §11 se construyen desde el primer shelf.
- **Frontera hexagonal mecánica:** un check de dependencias de módulos para que `:feature` nunca importe `:data`. Los puertos viven en `:core:domain` (JetStream no tiene módulo de dominio separado; inyecta un repositorio concreto).

---

## 13. Referencias

- JetStream — `https://github.com/android/tv-samples/tree/main/JetStreamCompose` (Apache-2.0)
- `android/tv-samples` (TvMaterialCatalog + ImmersiveListSamples) — `https://github.com/android/tv-samples`
- Release notes `androidx.tv` (fuente de verdad de versión/API) — `https://developer.android.com/jetpack/androidx/releases/tv`
- Migrating Compose for TV from alpha to stable (Paul Lammertsma) — `https://medium.com/androiddevelopers/migrating-compose-for-tv-from-alpha-to-stable-b0074d6fd350`
- Guías de diseño Android TV (navigation drawer, cards, focus, immersive list) — `https://developer.android.com/design/ui/tv/guides/components/navigation-drawer`
