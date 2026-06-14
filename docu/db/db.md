## [2026-06-09] Fix campo photo en ReportData

### Archivos tocados
- `app/src/main/java/.../model/ReportResponse.java` — `@SerializedName("photo")` → `@SerializedName("photo_path")`; añadido `getPhotoUrl()` construye `https://api.manuelmv.net/storage/` + `photo_path`; añadidos setters `setPhoto()` y `setDescription()`

### TODOs / Próximos pasos
- [!] Backend retorna 403 en `https://api.manuelmv.net/storage/reports/xxx.jpg` — necesita `php artisan storage:link` en el servidor

---

## [2026-06-13] RF-05: Room DB offline + WorkManager sync

### Archivos tocados
- `app/src/main/java/.../db/ReportCacheEntity.java` — nueva: cache de reportes (id, lat, lng, status, json completo de ReportData via Gson, cachedAt)
- `app/src/main/java/.../db/PendingActionEntity.java` — nueva: cola de acciones (CREATE_REPORT/VOTE/RETRACT_REPORT), payload JSON, status PENDING/SYNCING/FAILED, retryCount
- `app/src/main/java/.../db/ReportCacheDao.java`, `PendingActionDao.java` — nuevos DAOs
- `app/src/main/java/.../db/AppDatabase.java` — nueva: Room database (reporte_ciudadano.db, version 1, fallbackToDestructiveMigration)
- `app/src/main/java/.../work/ReportSyncWorker.java` — nuevo: Worker que procesa PendingActionEntity (constraint NetworkType.CONNECTED), descarta acciones con 403/409/422, reintenta el resto con backoff exponencial (MAX_RETRIES=5 → status FAILED)
- `app/src/main/java/.../work/SyncManager.java` — nuevo: syncNow() (OneTimeWorkRequest, unique work "report_sync") y schedulePeriodicSync() (cada 15min, llamado desde ReporteCiudadanoApp.onCreate)
- `app/src/main/java/.../util/ConnectivityHelper.java` — nuevo: ConnectivityHelper.isOnline(context)
- `app/build.gradle` / `gradle/libs.versions.toml` — añadidas deps Room 2.7.1 (runtime+compiler) y WorkManager 2.10.0
- `AndroidManifest.xml` — añadido permiso ACCESS_NETWORK_STATE
- `ui/MapFragment.java` — cachea cada reporte cargado en Room (cacheReport); si sin conexión, loadReportsFromCache() pinta el mapa desde Room; creación de reporte sin conexión → queueOfflineReport() (PendingActionEntity CREATE_REPORT)
- `ui/vote/VoteStateManager.java` — submitVote sin conexión → queueOfflineVote(): encola PendingActionEntity VOTE y actualiza VoteState localmente (optimista); nuevo VoteEvent.Type.OFFLINE_QUEUED
- `ui/ReportDetailBottomSheet.java` — retractReport() sin conexión → queueOfflineRetract() (PendingActionEntity RETRACT_REPORT); maneja OFFLINE_QUEUED como Snackbar INFO

### TODOs / Próximos pasos
- [ ] CREATE_REPORT offline no soporta foto (solo category_id/lat/lng/description)
- [ ] Si dos PendingAction de VOTE para el mismo reporte quedan en cola y el primero falla con 409 (voto duplicado real), el segundo también se descarta — revisar si hace falta lógica de "un voto pendiente por reporte"
- [x] Probar ciclo completo: crear reporte/voto/retiro en modo avión, verificar sync al reconectar ✅ (2026-06-13, ver fix abajo)

---

## [2026-06-13] Fix RF-05: sync no se disparaba al reconectar / mapa no se refrescaba

### Problema
Reporte creado en modo avión quedaba en `PendingAction` pero al reconectar no aparecía en el mapa: el `periodicWorkRequest` (15min) no corre inmediatamente, y aunque corriera, nada refrescaba el mapa con el reporte recién sincronizado.

### Archivos tocados
- `ui/MapFragment.java` — `registerConnectivityCallback()`: `ConnectivityManager.registerDefaultNetworkCallback`, en `onAvailable()` llama `SyncManager.syncNow()` inmediatamente (no espera el periodic). `observeSyncWork()`: observa `WorkManager.getWorkInfosForUniqueWorkLiveData("report_sync")`, al `SUCCEEDED` llama `pollForUpdates()` para traer el reporte recién creado. Ambos registrados en `onViewCreated`, callback desregistrado en `onDestroyView`

### TODOs / Próximos pasos
- [ ] Verificar en dispositivo real: modo avión → crear reporte → reactivar datos → debe aparecer marcador sin reabrir la app
