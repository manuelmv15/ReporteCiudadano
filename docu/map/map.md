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
- [ ] Fase 3: Implementar RadialMenuDialogFragment en OnMapLongClickListener
- [ ] Customizar estilo del mapa (colores MD3)
