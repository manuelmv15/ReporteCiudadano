# 05 — Android: Room Database

## AppDatabase.java

```java
@Database(
    entities = {ReportCacheEntity.class, PendingActionEntity.class, FcmNotificationCacheEntity.class},
    version = 2,
    exportSchema = false
)
```

Singleton con double-checked locking. Usa `fallbackToDestructiveMigration()` — en versión conflict destruye y recrea la DB.

Nombre del archivo: `reporte_ciudadano.db`

---

## Entidad 1: ReportCacheEntity

**Tabla:** `report_cache`

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` (PK) | String | String del reportId numérico de la API |
| `latitude` | double | Latitud del reporte |
| `longitude` | double | Longitud del reporte |
| `status` | String | pending/verified/resolved/archived |
| `json` | String | JSON completo del `ReportResponse.ReportData` serializado con Gson |
| `cachedAt` | long | Timestamp System.currentTimeMillis() al momento del upsert |

**DAO:** `ReportCacheDao`
- `upsert(entity)` — `@Insert(onConflict = REPLACE)` — actualiza si ya existe el id
- `getAll()` — retorna lista completa
- `deleteAll()` — limpia toda la caché

**Uso:** `MapFragment.cacheReport()` guarda tras cada fetch exitoso. `loadReportsFromCache()` los parsea de vuelta con Gson cuando no hay conexión.

---

## Entidad 2: PendingActionEntity

**Tabla:** `pending_actions`

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` (PK, autoGenerate) | int | Autonumérico |
| `type` | String | Tipo de acción: ver constantes abajo |
| `payload` | String | JSON con los datos de la acción |
| `createdAt` | long | Timestamp de creación |
| `retryCount` | int | Cuántas veces se intentó (default 0) |
| `status` | String | `"pending"` o `"failed"` |

**Tipos de acción (constantes en PendingActionEntity):**
```java
TYPE_CREATE_REPORT = "create_report"
TYPE_VOTE          = "vote"
TYPE_RETRACT_REPORT = "retract_report"
STATUS_PENDING     = "pending"
STATUS_FAILED      = "failed"
```

**Payload por tipo:**

`TYPE_CREATE_REPORT`:
```json
{"category_id": 1, "latitude": 13.6929, "longitude": -89.2182, "description": "Reporte desde app"}
```

`TYPE_VOTE`:
```json
{"report_id": 42, "type": "confirm", "latitude": 13.6929, "longitude": -89.2182}
```

`TYPE_RETRACT_REPORT`:
```json
{"report_id": 42}
```

**DAO:** `PendingActionDao`
- `insert(entity)`
- `getPending()` — retorna las de `status = "pending"`
- `update(entity)`
- `delete(entity)`

**Ciclo de vida:** Al crear, tiene `status = "pending"` y `retryCount = 0`. Cada fallo de red en `ReportSyncWorker` incrementa `retryCount`. Si `retryCount >= 5` → `status = "failed"` (no se reintenta).

Errores definitivos del server (403, 409, 422) descartan la acción sin incrementar retryCount.

---

## Entidad 3: FcmNotificationCacheEntity

**Tabla:** `fcm_notification_cache`

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `reportId` (PK) | int | ID del reporte de la notificación |
| `receivedAt` | long | Timestamp de cuando se recibió |
| `processed` | boolean | Si ya se procesó/mostró |

**DAO:** `FcmNotificationCacheDao`
- `insert(entity)`
- `getByReportId(id)` — para check de duplicados
- `markAsProcessed(reportId)`
- `getUnprocessed()` — notificaciones pendientes de mostrar

**Uso:** `FcmNotificationManager` la usa para:
1. Detectar notificaciones duplicadas (mismo reportId en últimas X horas)
2. Cachear notificaciones recibidas offline para replay cuando vuelva conexión

---

## ExecutorService para operaciones DB

`MapFragment` usa:
```java
private final java.util.concurrent.ExecutorService dbExecutor = 
    java.util.concurrent.Executors.newSingleThreadExecutor();
```

Todas las operaciones Room se ejecutan en este executor (Room prohíbe I/O en main thread). Los resultados se devuelven a main thread con `requireActivity().runOnUiThread()`.

El executor se cierra en `onDestroyView()` con `dbExecutor.shutdown()`.
