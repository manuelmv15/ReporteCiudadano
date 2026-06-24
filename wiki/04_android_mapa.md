# 04 — Android: MapFragment

Archivo: `ui/MapFragment.java` (~1340 líneas). Es el núcleo de la app.

## Constantes clave

```java
DEFAULT_LATITUDE = -34.6037   // Buenos Aires (fallback sin GPS)
DEFAULT_LONGITUDE = -58.3816
DEFAULT_ZOOM = 15.0
MAX_REPORTS = 200             // máximo marcadores simultáneos en mapa
MIN_ZOOM_TO_LOAD = 13.0       // debajo de este zoom → heatmap en vez de marcadores
VIEWPORT_PAGE_SIZE = 50       // reportes por llamada de viewport
CAMERA_IDLE_DEBOUNCE_MS = 600 // espera 600ms tras parar cámara antes de fetch
VOTABLE_REPORTS_CHECK_INTERVAL_MS = 30_000  // check nearby cada 30 segundos
```

## Dependencias inyectadas

| Campo | Tipo | Responsabilidad |
|-------|------|-----------------|
| `vm` | `MapViewModel` | Estado persistente (marcadores, filtros, poller) |
| `markerRenderer` | `MarkerRenderer` | Dibuja/actualiza/elimina marcadores Mapbox |
| `locationTracker` | `LocationTracker` | FusedLocationProvider, permisos, tracking continuo |
| `nearbyChecker` | `NearbyReportChecker` | Detecta reportes votables a <500m |
| `headingManager` | `HeadingManager` | Brújula del dispositivo → rota modelo 3D del usuario |
| `appDatabase` | `AppDatabase` | Room DB para caché offline |

## Ciclo de vida completo

```
onCreateView()  → set Mapbox access token, inflate binding
onViewCreated() → init ViewModel, init LocationTracker, init nearbyChecker
                → observe vm.reportUpdated (updates de poller)
                → mapView.loadStyleUri(STANDARD) → en callback:
                    → carga terreno 3D (DEM)
                    → aplica iluminación dinámica
                    → setupUser3DLayer() (modelo .glb)
                    → setupAnnotationManager()
                    → setupMapListeners()
                    → setupFAB()
                    → requestUserLocation()
                    → prefetchHeatmapPoints()
                    → si hay datos en vm → repopulateMarkersFromViewModel()
                    → sino → loadReportsFromAPI()

onStart()  → mapView.onStart()
           → poller.start()
           → votableReportsHandler.post() (tick cada 30s)
           → locationTracker.startContinuous()
           → headingManager.startListening()

onStop()   → poller.stop()
           → votableReportsHandler.removeCallbacks()
           → locationTracker.stopContinuous()
           → headingManager.stopListening()
           → mapView.onStop()

onDestroyView() → limpia callbacks, dbExecutor.shutdown(), mapView.onDestroy()
                → unregister networkCallback
                → binding = null
```

## Carga de reportes por viewport

**Trigger:** cámara deja de moverse por 600ms (`cameraIdleRunnable`)

**Lógica:**
1. Zoom < 13 → limpia marcadores → muestra **heatmap**
2. Zoom ≥ 13 → apaga heatmap si activo
3. Sin conexión → carga desde Room (`loadReportsFromCache()`)
4. Con conexión → calcula bounding box actual → GET `/reports?lat_min&lat_max&lng_min&lng_max&per_page=50`
5. Respuesta → `markerRenderer.removeOutsideBounds()` (limpia los de fuera)
6. Por cada reporte nuevo → `markerRenderer.addReport()` + `cacheReport()`
7. Si `reportMarkers.size() >= 200` → no agrega más → muestra snackbar de límite

## Heatmap (RF-19)

- Prefetch al iniciar: GET `/reports/heatmap` → máx 2000 puntos lat/lng
- Se guarda en `vm.heatmapPoints` (sobrevive rotación)
- Al bajar zoom < 13 → `buildHeatmapLayer()`:
  - Crea `GeoJsonSource` con todos los puntos
  - Crea `HeatmapLayer` (opacidad 0.7, radio 20px)
  - Bindea al estilo Mapbox activo
- Al subir zoom ≥ 13 → remueve source y layer del estilo

## Modelo 3D del usuario

Archivo: `assets/arrow_direction.glb` (modelo de flecha 3D)

- **Setup:** `setupUser3DLayer(style)` al cargar el mapa
  - Registra el modelo con id `"user-arrow-model"`
  - Crea `GeoJsonSource` `"user-3d-source"` con geometría Point(0,0)
  - Crea `CircleLayer` `"user-halo-layer"` (halo azul #2196F3, borde blanco, radio 10px, opacidad 0.8)
  - Crea `ModelLayer` `"user-3d-layer"` (escala 40x40x40, rotación 90° en X)
- **Update:** `updateUser3DMarker(point, bearing)`
  - Actualiza la geometría del GeoJsonSource
  - Ajusta rotación Z del ModelLayer con el bearing del GPS
- `HeadingManager` lee el sensor de orientación y actualiza `markerRenderer.updateUserHeading()`

## Iluminación dinámica del mapa

`applyDynamicLighting()` consulta `SettingsManager.getMapPreset()`:
- `"auto"` → lee hora del día → asigna `dawn`/`day`/`dusk`/`night`
- Preset manual: `dawn`/`day`/`dusk`/`night`

Aplica: `mapboxMap.setStyleImportConfigProperty("basemap", "lightPreset", Value.valueOf(preset))`

## Creación de reporte

**Trigger A:** Long press en mapa → `setupMapListeners()`
**Trigger B:** FAB `fabAddReport` → usa `userLocation` directamente

**Validaciones antes de abrir el menú:**
1. `userLocation != null` (si no, solicita GPS)
2. `distanceMeters <= 500` entre `userLocation` y el punto del long press (RF-07+)
   - Si > 500m → snackbar de advertencia, no abre menú

**Flujo:**
1. Abre `RadialMenuDialogFragment` con el `Point`
2. Usuario selecciona categoría → `onReportCategorySelected(category, lat, lng)`
3. Validación duplicado: `hasSameCategoryNearby(categoryId, lat, lng, 50m)` — bloquea si hay uno de la misma categoría a <50m activo/pendiente/verificado
4. Validación login: si no está logueado → snackbar
5. Sin conexión → `queueOfflineReport()` → guarda en `PendingActionEntity`
6. Con conexión → POST `/api/reports` → si OK → `addReportMarker()`

## Filtros de reportes (RF-19)

**Filtros disponibles:**
- Estado: `all` / `pending` / `verified` / `resolved`
- Antigüedad: `all` / `1h` / `6h` / `24h`
- Categorías: checkboxes de 7 grupos: `vialidad`, `alumbrado`, `agua`, `trafico`, `seguridad`, `basura`, `otros`

**Persistencia:** `SharedPreferences` `"map_filters"`

**Aplicación:** `passesFilters(report)` — llamada en:
1. `markerRenderer.addReport()` — al agregar cada marcador
2. `applyFilters()` — al cambiar filtros en el diálogo, re-evalúa todos los marcadores en memoria

Los archivados SIEMPRE se filtran (nunca se pintan):
```java
if ("archived".equalsIgnoreCase(status)) return false;
```

## FABs (Floating Action Buttons)

| FAB | Acción |
|-----|--------|
| `fabMyLocation` | Centra cámara en `userLocation` |
| `fabAddReport` | Abre `RadialMenuDialogFragment` en `userLocation` |
| `fabFilter` | Abre diálogo de filtros |
| `fabProfile` | Abre `UserProfileBottomSheet` (si logueado) o `LoginActivity` |

`fabAddReport` solo visible si usuario está logueado (`updateFabVisibility()`).

## Onboarding contextual (RF-34+)

Solo una vez, después de obtener ubicación GPS. Si:
- `SettingsManager.isContextualOnboardingCompleted() == false`
- Usuario está logueado

Muestra secuencia de `MaterialTapTargetPrompt` apuntando a cada FAB:
1. `fabAddReport` → "Reporte Rápido"
2. `fabMyLocation` → "Tu Ubicación"
3. `fabFilter` → "Limpia el Mapa"
4. `fabProfile` → "Gestiona tu Perfil"

Al terminar la secuencia → `setContextualOnboardingCompleted(true)`

## Polling de cambios (ReportPoller)

`vm.getPoller()` es un `ReportPoller` que:
- Corre en `onStart()`, se detiene en `onStop()`
- Llama GET `/reports/stream/changes?since=<timestamp>&limit=50`
- Actualiza `vm.reportUpdated` (LiveData) con cada reporte del response
- `MapFragment` observa `vm.reportUpdated` → actualiza o agrega marcador

## Check de reportes cercanos

Cada 30 segundos (`votableReportsRunnable`):
- `nearbyChecker.check(reportMarkers, userLocation, uid)`
- Detecta reportes a <500m donde el usuario no ha votado
- Emite notificación local "hay reportes cerca que podés votar"

## Conectividad y sync

Al recuperar conexión (`NetworkCallback.onAvailable()`):
- `SyncManager.syncNow()` → WorkManager procesa `PendingActionEntity`

Al completar el Worker (`observeSyncWork()`):
- `pollForUpdates()` refresca el mapa
- Muestra snackbar con cuántas acciones se sincronizaron

## Deep link desde notificación

`showReportFromNotification(reportId)`:
1. Busca en `reportMarkers` (caché en memoria)
2. Si encontrado → `showReportDetails(report)`
3. Si no → GET `/reports/{id}` → muestra bottom sheet

Si el mapa no está listo cuando llega el intent → `pendingNotificationReportId` se guarda → se procesa en `setOnMapReadyCallback()`
