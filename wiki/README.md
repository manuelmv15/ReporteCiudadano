# ReporteCiudadano — Documentación Técnica Completa

Sistema de reporte ciudadano geolocalizado. App Android + API Laravel en servidor local.

## Índice

| Archivo | Contenido |
|---------|-----------|
| [01_arquitectura.md](01_arquitectura.md) | Visión global: componentes, flujo de datos, infraestructura |
| [02_android_overview.md](02_android_overview.md) | Estructura del proyecto Android, actividades, ciclo de vida |
| [03_android_network.md](03_android_network.md) | Capa de red: Retrofit, ApiService, interceptores, autenticación |
| [04_android_mapa.md](04_android_mapa.md) | MapFragment completo: marcadores, heatmap, filtros, 3D, viewport |
| [05_android_db.md](05_android_db.md) | Room Database: entidades, DAOs, caché offline, acciones pendientes |
| [06_android_fcm.md](06_android_fcm.md) | Firebase Cloud Messaging: recepción, notificaciones, votos desde notif |
| [07_android_sync.md](07_android_sync.md) | WorkManager: ReportSyncWorker, SyncManager, cola offline |
| [08_android_models.md](08_android_models.md) | Modelos de datos Android (POJOs Retrofit) |
| [09_laravel_rutas.md](09_laravel_rutas.md) | Todas las rutas API: método, auth, params, respuesta |
| [10_laravel_auth.md](10_laravel_auth.md) | Auth: registro, login, Google OAuth, logout, reset password |
| [11_laravel_reportes.md](11_laravel_reportes.md) | CRUD reportes, heatmap, stream de cambios, geovalla |
| [12_laravel_votos.md](12_laravel_votos.md) | Sistema de votos: distancia, umbrales, transiciones automáticas |
| [13_laravel_modelos.md](13_laravel_modelos.md) | Modelos Eloquent: User, Report, ReportVote, Category y su lógica |
| [14_laravel_db.md](14_laravel_db.md) | Esquema completo de base de datos (migraciones) |
| [15_laravel_notificaciones.md](15_laravel_notificaciones.md) | NotificationService: FCM push a usuarios cercanos y votantes |
| [16_laravel_infra.md](16_laravel_infra.md) | Docker Compose, Nginx, Cloudflare Tunnel, Scheduler |
| [17_flujos_completos.md](17_flujos_completos.md) | Flujos end-to-end: crear reporte, votar, FCM, offline sync, scoring |

## Stack tecnológico

- **Android**: Java, Retrofit 2, Room, WorkManager, Firebase FCM, Mapbox Maps SDK v11, MVVM
- **Backend**: Laravel 11, PHP 8.4-FPM, Laravel Sanctum, MySQL 8
- **Infraestructura**: Docker Compose, Nginx, Cloudflare Tunnel
- **Notificaciones push**: Firebase Admin SDK (Kreait)
- **Email**: Brevo (SMTP/API)
- **URL pública**: `https://api.manuelmv.net/api/`
