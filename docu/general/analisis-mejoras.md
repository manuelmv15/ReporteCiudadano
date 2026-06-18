# Análisis de Mejoras — ReporteCiudadano
> Generado: 2026-06-17 | Base: 43 RF completos (100%)

---

## Bugs Críticos (corregir antes de producción)

| Archivo                 | Línea   | Problema                                                                                                                           | Fix                                                 |
| ----------------------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| `MapFragment.java`      | 103     | `POLL_INTERVAL_MS = 5_000` — comentario dice "volver a 30_000 luego". Nunca se cambió. 6x más API hits de lo planeado              | Cambiar a `30_000`                                  |
| `MapFragment.java`      | 115     | `VOTABLE_REPORTS_CHECK_INTERVAL_MS` mismo problema de intervalo de testing                                                         | Restaurar valor de producción                       |
| `VoteStateManager.java` | 78–81   | Ventana de edición de voto (5min) se calcula desde `now`, no desde cuando el usuario votó — siempre reinicia la ventana            | Calcular desde `getUserVotedAt()` del servidor      |
| `MapFragment.java`      | 129     | `dbExecutor` nunca se cierra en `onDestroyView()` — thread leak                                                                    | Llamar `dbExecutor.shutdown()` en `onDestroyView()` |
| `MapFragment.java`      | 394     | `LocationRequest.create()` deprecated — advertencias en Android nuevo                                                              | Migrar a `LocationRequest.Builder`                  |
| `bitmapCache`           | —       | Sin eviction LRU — crece indefinidamente en sesiones largas                                                                        | Reemplazar `HashMap` por `LruCache`                 |
| `VoteStateManager.java` | 390–403 | `determineStatus()` duplica lógica server-side (5 votos verified, 70% resolved). Diverge para usuarios Experto (RF-30 usa 3 votos) | Obtener estado siempre del servidor                 |
| `VoteStateManager.java` | 203     | Crea nuevo executor en cada voto offline, nunca lo cierra                                                                          | Reutilizar executor de instancia                    |
| `MapFragment.java`      | 87–93   | `reportStatusCircles` declarado pero nunca populado ni usado — campo muerto                                                        | Eliminar el campo                                   |
|                         |         |                                                                                                                                    |                                                     |

---

## Deuda Técnica Moderada

- **`MapFragment.java` tiene 1591 líneas** — maneja location, polling, filtros, offline queue, markers, notificaciones. Candidatos a extraer:
  - `MarkerRenderer` — toda la lógica de bitmaps y capas Mapbox
  - `LocationTracker` — fusedClient + callbacks
  - `ReportPoller` — el handler de polling periódico
  - `NearbyReportChecker` — lógica de votable reports nearby

- **Sin ViewModel/Repository** — estado vive directo en Fragments. Rotación de pantalla = llamadas API duplicadas + pérdida de estado UI.

- **Sin OkHttp Interceptor para auth** — `"Bearer " + token` manual en cada llamada; `handleExpiredSession()` scattered en múltiples fragments. Un `AuthInterceptor` centralizaría esto.

- **Category ID mapping duplicado** — `getCategoryIdBySlug()` y `normalizeCategoryGroup()` son mappings paralelos que pueden divergir. Necesita un `CategoryMapper` como única fuente de verdad.

- **`ReportSyncWorker.java:62`** — `catch (Exception e)` genérico trata errores de parseo JSON igual que errores de red → retry infinito hasta que WorkManager abandone.

---

## Gaps de UX

| Problema | Detalle |
|---|---|
| Sin estado vacío | "Mis Reportes" y "Mis Votos" muestran RecyclerView vacío sin mensaje cuando no hay datos |
| Distancia no visible antes de votar | Error "Debes estar a 500m" aparece DESPUÉS del tap. Mostrar distancia proactivamente |
| Notificación abre root, no reporte | `MapFragment:1444` — tap en notificación FCM abre `MainActivity`, no el reporte específico. Infraestructura `showReportFromNotification()` ya existe |
| Filtros no persisten | Al salir y re-entrar, todos los filtros vuelven a "todos". Guardar en `SharedPreferences` |
| Retracción sin countdown | Usuario no sabe que tiene 5min para retirar su reporte ni cuánto tiempo queda |
| Snackbar con coordenadas raw | `"Tu ubicación (±12m): -34.6037, -58.3816"` — output de desarrollador visible al usuario |
| Radial menu no discoverable | Long press en mapa no tiene hint ni tooltip. Nuevo usuario no lo encuentra |
| Sin loading en carga inicial | Reportes aparecen de golpe sin skeleton/shimmer/progress |
| Sin accesibilidad en radial menu | Íconos sin `contentDescription` — invisible para TalkBack |

---

## Nuevas Funciones Propuestas

### Alto impacto

#### 1. Comentarios en reporte
- API: `GET /reports/{id}/comments`, `POST /reports/{id}/comments` (max 280 chars)
- Android: sección expandible en `ReportDetailBottomSheet`
- El gap más obvio: usuarios necesitan comunicar "lo arreglaron a medias", "es peor ahora"

#### 2. Deep link a reporte específico
- `reporteciudadano://report/{id}` → `MainActivity` + auto-abrir detail sheet
- Infraestructura ya existe en `showReportFromNotification()`
- Solo falta `<intent-filter>` en `AndroidManifest.xml` + `AppLinks` verification
- Permite compartir reportes por WhatsApp/Telegram

#### 3. "Watching" de reportes ajenos
- `POST /reports/{id}/watch` — suscribirse a un reporte que no es propio
- Push cuando cambia estado (verificado, resuelto, archivado)
- Útil para vecinos del reportero

#### 4. Heatmap overlay
- Toggle en FAB de filtros
- Mapbox `HeatmapLayer` nativo — weight por categoría o densidad
- Muestra clusters sistémicos, valor para usuarios y municipio

#### 5. Timeline del reporte
- Dentro del detail sheet: "Creado → Verificado (5 votos) → Archivado"
- API: `GET /reports/{id}/timeline` o derivable de campos existentes
- Hace los reportes sentirse vivos en lugar de snapshots estáticos

### Impacto medio

#### 6. Leaderboard comunitario
- `GET /leaderboard?radius=5km` — top 10 colaboradores cercanos
- Tab en `UserProfileBottomSheet` o sección en mapa
- Impulsa engagement con el sistema de puntos (RF-26/29)

#### 7. Feedback de sync offline
- Notificación local cuando `ReportSyncWorker` completa: "3 acciones sincronizadas"
- Ahora el sync es completamente silencioso — usuario no sabe si sus votos offline llegaron

#### 8. Chips de filtros rápidos
- Chips sobre el mapa: "Cerca de mí" / "Urgentes (<1h)" / "Para votar"
- Más discoverable que el dialog de filtros actual
- Estado de chip → llama `applyFilters()` existente

#### 9. Countdown de archivo en detail sheet
- Chip pequeño: "Se archivará en ~18h sin actividad"
- Calculable con `created_at` + `STALE_HOURS=24` existentes
- Motiva a usuarios cercanos a votar antes que desaparezca

#### 10. Dark mode
- `Style.DARK` en Mapbox + Material You dynamic color
- La mayoría de componentes ya usan colores de tema

#### 11. Foto obligatoria para categorías críticas
- Para "inseguridad" y "fuga-de-agua": prompt a añadir foto en los primeros 10min
- Mejora calidad de reportes sin bloquear la creación rápida (RF-01/02)

#### 12. Estadísticas por categoría en perfil
- Pie chart / bar chart: "tus categorías más reportadas"
- Derivable client-side de `GET /me/reports` existente — 0 cambios de backend

#### 13. Modo mapa para municipios (read-only admin view)
- Vista sin auth que muestra estadísticas agregadas
- Podría convertirse en un dashboard web separado con la misma API

---

## Riesgos en Producción

| Riesgo | Impacto | Detalle |
|---|---|---|
| `MAX_REPORTS=200` hardcap silencioso | Alto | En centro urbano denso, mapa truncado sin aviso al usuario (`MapFragment:495-498`) |
| FCM token refresh sin auth | Medio | Si usuario no está logueado cuando FCM renueva token, servidor nunca recibe el nuevo — notificaciones dejan de llegar |
| Vote offline + reporte archivado antes del sync | Medio | Voto se descarta con `isDefinitiveRejection` silenciosamente — usuario nunca sabe |
| `reportAgeHours()` asume UTC hardcoded | Medio | Strips `Z` suffix; si servidor cambia timezone, todos los filtros de antigüedad se rompen (`MapFragment:735`) |
| `hasSameCategoryNearby()` usa grados, no metros | Bajo | `radiusDeg = 0.00045` varía con latitud — ~50m en ecuador, ~40m a -34° (Bs As) |
| `reportStatusCircles` HashMap nunca usado | Bajo | Memoria desperdiciada, confunde a futuros desarrolladores |

---

## Prioridad de Acción Sugerida

### Sprint inmediato (bugs, 0 features)
1. `POLL_INTERVAL_MS` → `30_000` (1 línea, impacto inmediato en costos de servidor)
2. Cerrar `dbExecutor` en `onDestroyView()`
3. Eliminar `reportStatusCircles`
4. Fix `VoteStateManager` — calcular ventana desde timestamp del servidor

### Sprint corto (UX sin backend nuevo)
5. Chips de filtros rápidos
6. Deep link a reporte (infraestructura ya existe)
7. Countdown de archivo en detail sheet
8. Distancia proactiva antes de votar
9. Feedback de sync offline

### Sprint medio (features nuevas)
10. Comentarios en reporte (mayor impacto percibido por usuarios)
11. Watching de reportes
12. Timeline del reporte

### Refactor (deuda técnica)
13. Extraer `MarkerRenderer` de MapFragment
14. Introducir `ReportRepository` + `MapViewModel`
15. `AuthInterceptor` en OkHttp
