# Documentación — Mapa

## [2026-06-07] MapFragment — Integración Mapbox + FAB de Ubicación

### Archivos tocados

**Dependencias:**
- `gradle/libs.versions.toml` — reemplazado Google Maps por `mapbox = "11.4.0"`, agregado `fragment = "1.8.2"`
- `app/build.gradle` — implementado `libs.mapbox.maps`; removido `google-play-services-maps` y `google-play-services-location`

**Configuración:**
- `app/src/main/AndroidManifest.xml` — removidos permisos de ubicación (Mapbox usa acceso_token en `local.properties`), removido meta-data Google Maps
- `app/src/main/res/values/strings.xml` — agregado `my_location`

**UI:**
- `app/src/main/res/layout/fragment_map.xml` — FrameLayout con MapView (Mapbox) + FAB
- `app/src/main/res/drawable/ic_my_location.xml` — ícono vector Material 24dp
- `app/src/main/res/layout/activity_main.xml` — Single Activity con FragmentContainerView

**Código:**
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MapFragment.java` — Fragment con:
  - MapView y MapboxMap setup
  - Lifecycle management (onStart/onStop/onDestroyView)
  - FAB para centrar en ubicación actual
  - `OnMapLongClickListener` para captura de coordenadas (Fase 3)
  - Centro por defecto en Buenos Aires (fallback)
- `app/src/main/java/com/bombayashi/reporteciudadano/MainActivity.java` — Single Activity container limpio

### Características implementadas

| Función | Detalles |
|---------|----------|
| Mapa     | Mapbox Streets style, zoom inicial = 15.0 |
| FAB      | Ubicado en inferior-izquierda, anima hacia coordenada capturada |
| Long-press | Captura coords + Snackbar INFO |
| Default  | Buenos Aires (-34.6037, -58.3816) como fallback |

### API Key (Mapbox)

✅ **Ya configurada** en `local.properties`:
```properties
MAPBOX_ACCESS_TOKEN=tu_token_aqui
```

Se inyecta automáticamente en `build.gradle` como `resValue "string", "mapbox_access_token"`

### TODOs / Próximos pasos
- [ ] Mostrar marcadores de reportes existentes (consumir API Bombayashi)
- [ ] Agregar LocationEngine para obtener ubicación real del dispositivo
- [ ] Customizar estilo del mapa (colores MD3)

---

## [2026-06-08] Fase 3: RadialMenuDialogFragment — Menú Radial "Modo Bomba"

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/RadialMenuDialogFragment.java` — nuevo DialogFragment transparente con 8 botones circulares
- `app/src/main/res/layout/dialog_radial_menu.xml` — layout con ConstraintLayout circular usando `layout_constraintCircle` (120dp radio, 45° spacing)
- `app/src/main/res/drawable/ic_category_*.xml` × 8 — iconos vectoriales para: Vialidad, Alumbrado, Agua, Tráfico, Seguridad, Parques, Basura, Otros
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MapFragment.java` — integración: OnMapLongClickListener ahora abre RadialMenu en lugar de mostrar snackbar

### Flujo implementado

1. Usuario toca mapa (long-press) → MapFragment captura `Point` con coordenadas
2. `showRadialMenu(point)` → lanza `RadialMenuDialogFragment` transparente
3. 8 botones circulares alrededor del centro → usuario toca categoría
4. `onCategorySelected()` → cierra diálogo, dispara `onReportCategorySelected()`
5. Por ahora: muestra Snackbar confirmando categoría + coords (TODO: conectar con API)

### Diseño

- **Fondo**: FrameLayout transparente + ConstraintLayout semi-transparente (alpha=0.85) oscurece el mapa
- **Botones**: 8 × FloatingActionButton mini, espaciados cada 45° en círculo de 120dp
- **Ángulos**: 0° (Vialidad), 45° (Alumbrado), 90° (Agua), 135° (Tráfico), 180° (Seguridad), 225° (Parques), 270° (Basura), 315° (Otros)
- **Iconos**: Blanco sobre fondos MD3 estándar, reconocibles de un vistazo

### Callback Pattern

```java
RadialMenuDialogFragment.newInstance(location, (category, lat, lng) -> {
    // Manejador del reporte
});
```

---

## [2026-06-08] Ubicación Actual + Marcadores de Reportes

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MapFragment.java` — FusedLocationProviderClient + ubicación actual + click listener para marcadores
- `app/src/main/java/com/bombayashi/reporteciudadano/model/Report.java` — modelo para almacenar datos de reportes
- `app/src/main/AndroidManifest.xml` — ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION
- `gradle/libs.versions.toml` + `app/build.gradle` — google-play-services-location 21.3.0

### Características implementadas

| Feature | Detalles |
|---------|----------|
| Ubicación actual | FusedLocationProviderClient obtiene última ubicación conocida, pide permisos en runtime |
| Fallback | Buenos Aires si no hay ubicación actual o permisos denegados |
| FAB "Mi ubicación" | Actualizado: ahora requiere ubicación actual / fallback a Buenos Aires |
| Marcadores | HashMap de Report objects con (id, category, lat, lng) |
| Click en marcador | Muestra SnackbarHelper INFO con categoría del reporte |

### Flujo de Ubicación

1. `onViewCreated()` → `requestUserLocation()`
2. Si permisos concedidos: `getCurrentUserLocation()` → FusedLocationProviderClient
3. Si ubicación obtenida: centra cámara + SnackbarHelper SUCCESS
4. Si no: fallback a Buenos Aires

---

## [2026-06-08] Indicadores Visuales — Usuario + Reportes (CircleAnnotations)

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MapFragment.java` — CircleAnnotationManager, markers para usuario y reportes
- `app/src/main/java/com/bombayashi/reporteciudadano/model/ReportResponse.java` — modelo con estructura de respuesta paginada
- `app/src/main/java/com/bombayashi/reporteciudadano/network/ApiService.java` — endpoint GET /reports

### Indicadores Implementados

#### Usuario (Mi ubicación)
- **Color**: Azul (`#2196F3`)
- **Radio**: 10dp
- **Opacidad**: 0.7
- **Stroke**: Blanco 3dp
- **Comportamiento**: Aparece cuando se obtiene ubicación actual, centrado por FAB

#### Reportes
- **Color**: Por categoría (Vialidad=Rojo, Alumbrado=Amarillo, Agua=Verde, Tráfico=Azul, Seguridad=Púrpura, Parques=Turquesa, Basura=Marrón)
- **Radio**: Según estado
  - pending: 5.0dp
  - verified: 8.0dp
  - resolved: 6.0dp
- **Opacidad**: Según estado
  - pending: 0.8
  - verified: 0.9
  - resolved: 0.5 (más transparente)
- **Stroke**: Blanco 2dp

### API Integration
```java
// GET /reports?status=pending,verified&per_page=100
ApiClient.getApiService().getReports("pending,verified", 100)
```

Respuesta paginada con estructura:
```json
{
  "current_page": 1,
  "per_page": 100,
  "total": N,
  "data": [
    {
      "id": 1,
      "category_id": 1,
      "user_id": 1,
      "latitude": -34.6037,
      "longitude": -58.3816,
      "description": "texto",
      "status": "pending|verified|resolved|archived",
      "user": {"id": 1, "name": "Usuario"},
      "category": {"id": 1, "name": "Vialidad", "slug": "vialidad"},
      "votes": {"confirm": 5, "resolve": 2}
    }
  ]
}
```

### Correcciones de Color (2026-06-08)
- ✅ `getCategoryColor()` retorna String hex (`"#FF6B6B"`) en lugar de int ARGB
- ✅ CircleAnnotationOptions usa colores hex: `.withCircleColor("#2196F3")`
- ✅ UserLocation marker: `"#2196F3"` (azul) + stroke `"#FFFFFF"` (blanco)
- ✅ Lint warnings: `tools:ignore="MissingConstraints"` en dialog_radial_menu.xml FABs

### Mapeo de Categorías API (2026-06-08)
Actualizado `getCategoryIdBySlug()` con IDs correctos de la API:
```
vialidad (1) → bache
alumbrado (2) → alumbrado-publico
agua (4) → fuga-de-agua
trafico (5) → semaforo-danado
seguridad (6) → inseguridad
parques → basura-acumulada (ID 3, temp)
basura (3) → basura-acumulada
```

Nota: El menú radial UI mantiene nombres en español, pero mapea a slugs correctos de la API.

### Respuesta API Envuelta (2026-06-08)
Creado modelo `CreateReportResponse` para parsear la respuesta real de POST /reports:
```json
{
  "success": true,
  "message": "Report created successfully",
  "report": {
    "id": 1,
    "latitude": 13.614625,
    "longitude": -87.908232,
    "category": { "id": 3, "name": "Basura acumulada", "slug": "basura-acumulada" },
    ...
  }
}
```

ApiService actualizado:
- ❌ Antes: `Call<ReportResponse.ReportData> createReport(...)`
- ✅ Ahora: `Call<CreateReportResponse> createReport(...)`

MapFragment ahora extrae `response.getReport()` correctamente.

### Estructura Real GET /reports (2026-06-08)
**Problema encontrado:** ReportResponse esperaba `data` en el nivel superior.
**Estructura real de API:**
```json
{
  "success": true,
  "reports": {              // ← Envuelto en "reports"
    "current_page": 1,
    "per_page": 100,
    "total": 34,
    "data": [               // ← Array aquí
      { "id": 1, "latitude": ..., "category": {...} },
      ...
    ]
  }
}
```

**Corrección aplicada:**
- Agregado field `reports` a ReportResponse
- Creado clase interna `PaginationData` para manejar la estructura
- `getData()` ahora extrae correctamente `reports.data`
- Logging mejorado para debuggeo de carga inicial

### TODOs / Próximos pasos
- [ ] Fase 4: Click handler en reportes para mostrar BottomSheetDialogFragment (ya implementado, solo testear)
- [ ] Fase 4: Verificar que los reportes nuevos aparezcan correctamente en el mapa
- [ ] Fase 4: Refrescar lista de reportes cada N segundos (polling)
- [ ] Fase 4: Filtros de categoría/estado para visualización selectiva
