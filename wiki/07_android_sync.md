# 07 — Android: WorkManager y Sincronización Offline

## SyncManager.java

Clase utilitaria para enqueue de trabajo de sync.

```java
SyncManager.syncNow(context)
```

Crea un `OneTimeWorkRequest` de `ReportSyncWorker` con:
- `Constraint: NetworkType.CONNECTED` — solo corre con red activa
- `ExistingWorkPolicy.REPLACE` — si ya hay uno en cola, lo reemplaza
- Nombre único: `"report_sync"`

---

## ReportSyncWorker.java

`Worker` de WorkManager que procesa la cola de acciones pendientes.

### Lógica principal (`doWork()`)

```java
PendingActionDao dao = db.pendingActionDao();
boolean anyFailedDueToNetwork = false;

for (PendingActionEntity action : dao.getPending()) {
    try {
        boolean handled = switch (action.type) {
            case TYPE_CREATE_REPORT → syncCreateReport(action)
            case TYPE_VOTE          → syncVote(action)
            case TYPE_RETRACT_REPORT → syncRetractReport(action)
            default → true  // tipo desconocido: descartar
        };

        if (handled) {
            dao.delete(action);          // éxito o rechazo definitivo → borrar
        } else {
            action.retryCount++;
            if (action.retryCount >= MAX_RETRIES) {  // MAX = 5
                action.status = STATUS_FAILED;
            }
            dao.update(action);
        }
    } catch (JSONException e) {
        action.status = STATUS_FAILED;   // payload corrupto → no reintentar
        dao.update(action);
    } catch (Exception e) {
        anyFailedDueToNetwork = true;    // error de red → reintentar todo
    }
}

return anyFailedDueToNetwork ? Result.retry() : Result.success();
```

### Métodos de sincronización

**`syncCreateReport(action)`:**
- Parsea JSON de `action.payload`
- Construye `ReportRequest(categoryId, latitude, longitude, description)`
- `.execute()` en OkHttp (síncrono — Worker ya corre en background thread)
- Retorna `true` si código 2xx o "rechazo definitivo" (403, 409, 422)
- Retorna `false` si código de error transitorio (500, 503, etc.)

**`syncVote(action)`:**
- Parsea `report_id`, `type`, `latitude`, `longitude`
- Construye `VoteRequest(type, latitude, longitude)`
- POST `/reports/{reportId}/votes`
- Misma lógica de éxito/fallo

**`syncRetractReport(action)`:**
- Parsea `report_id`
- DELETE `/reports/{reportId}`
- Misma lógica

**`isDefinitiveRejection(code)`:**
```java
return code == 403 || code == 409 || code == 422;
```
Estos códigos indican que la acción no tiene sentido reintentar (no autorizado, conflicto, validación fallida).

### Cuándo se dispara

| Trigger | Código |
|---------|--------|
| Al volver la conexión | `NetworkCallback.onAvailable()` → `SyncManager.syncNow()` |
| Al abrir el mapa con conexión | `MapFragment.onViewCreated()` → `if isOnline → SyncManager.syncNow()` |
| WorkManager periódico | Si el worker retorna `Result.retry()`, WorkManager lo reintenta con backoff |

### Observación del resultado

`MapFragment.observeSyncWork()` observa el `WorkInfo` del work único `"report_sync"`:
- Si `State.SUCCEEDED` → llama `pollForUpdates()` y muestra snackbar con count de acciones sincronizadas
