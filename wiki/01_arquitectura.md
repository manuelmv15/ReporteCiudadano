# 01 — Arquitectura General

## Diagrama de componentes

```
[Android App]
    │
    │  HTTPS (Bearer Token)
    ▼
[Cloudflare Tunnel] ──► api.manuelmv.net
    │
    ▼
[Nginx :8082] ──► fastcgi ──► [PHP-FPM :9000 (Laravel)]
                                        │
                                        ├── MySQL 8 (apidb)
                                        └── Firebase Admin SDK ──► FCM ──► [Android App]
```

## Componentes

### Android App (`com.bombayashi.reporteciudadano`)
- Una sola Activity principal: `MainActivity`
- Un Fragment central: `MapFragment` (ocupa toda la pantalla)
- Activities secundarias: `LoginActivity`, `RegisterActivity`, `OnboardingActivity`, `ForgotPasswordActivity`, `ResetPasswordActivity`
- Room DB local para caché offline
- WorkManager para sincronización de acciones pendientes
- Firebase FCM para recibir push notifications

### Laravel API (servidor local `192.168.1.138`)
- Ruta base: `/mnt/almacenamiento/proyectos/laravel_api/`
- Código fuente en `src/`
- Expuesto por Cloudflare Tunnel en `https://api.manuelmv.net/api/`
- Auth: Laravel Sanctum (tokens de acceso personal)
- Notificaciones: Kreait Firebase Admin SDK

### Base de datos MySQL
- Contenedor Docker `api_db`
- Puerto mapeado: `3307:3306`
- Base: `apidb`, usuario: `apiuser`
- 6 tablas principales: `users`, `categories`, `reports`, `report_votes`, `personal_access_tokens`, `password_reset_tokens`

### Cloudflare Tunnel
- Contenedor `api_cloudflared` usando `cloudflare/cloudflared:latest`
- Token en `.env` como `CLOUDFLARE_TUNNEL_TOKEN`
- Protocolo `http2`
- Permite acceso HTTPS externo sin abrir puertos en el router

### Scheduler
- Contenedor Docker separado `api_scheduler`
- Ejecuta `php artisan schedule:work` en loop
- Corre `reports:archive-stale` periódicamente
  - Archiva reportes `resolved` con más de 2h (RF-18)
  - Archiva reportes `pending`/`verified` sin interacción en 24h (RF-13)

## Flujo principal de datos

1. Usuario abre app → `MainActivity` → carga `MapFragment`
2. `MapFragment` solicita ubicación GPS via `LocationTracker`
3. Al mover cámara (debounce 600ms), fetch de reportes en viewport actual
4. Reportes se pintan como marcadores en Mapbox con colores por estado
5. Toque largo en mapa → `RadialMenuDialogFragment` → selección de categoría
6. App valida ≤500m de distancia → POST `/api/reports` → reporte creado
7. Laravel dispara `ReportCreated` event → `NotificationService` → FCM push a otros usuarios
8. App recibe FCM → `MyFirebaseMessagingService` → muestra notificación con acciones
9. Toque en notificación → deep link `reporteciudadano://show_report?id=X` → `ReportDetailBottomSheet`

## Manejo offline (RF-05)

Cuando no hay conexión:
- Reportes se cargan desde Room (`ReportCacheEntity`)
- Acciones (crear reporte, votar, retirar) se guardan en `PendingActionEntity`
- Al recuperar conexión: `NetworkCallback` dispara `SyncManager.syncNow()`
- `ReportSyncWorker` procesa la cola contra la API
