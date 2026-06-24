# 02 — Android: Overview del Proyecto

## Paquete raíz: `com.bombayashi.reporteciudadano`

## Estructura de carpetas (`app/src/main/java/`)

```
com.bombayashi.reporteciudadano/
├── MainActivity.java               ← Activity principal (contenedor del Fragment)
├── LoginActivity.java              ← Login email/pass + Google
├── RegisterActivity.java           ← Registro de nueva cuenta
├── OnboardingActivity.java         ← Slides de bienvenida (solo primera vez)
├── ForgotPasswordActivity.java     ← Solicitar reset de contraseña
├── ResetPasswordActivity.java      ← Ingresar código y nueva contraseña
├── ReporteCiudadanoApp.java        ← Application class (inicializa ApiClient)
│
├── db/                             ← Room Database
│   ├── AppDatabase.java            ← RoomDatabase singleton (v2, 3 entidades)
│   ├── ReportCacheEntity.java      ← Tabla cache de reportes
│   ├── ReportCacheDao.java
│   ├── PendingActionEntity.java    ← Tabla acciones offline pendientes
│   ├── PendingActionDao.java
│   ├── FcmNotificationCacheEntity.java ← Cache de notificaciones FCM vistas
│   └── FcmNotificationCacheDao.java
│
├── model/                          ← POJOs para Retrofit (request/response)
│   ├── AuthResponse.java
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── GoogleLoginRequest.java
│   ├── ForgotPasswordRequest.java
│   ├── ResetPasswordRequest.java
│   ├── Report.java
│   ├── ReportRequest.java
│   ├── ReportResponse.java         ← Incluye inner class ReportData y Category
│   ├── ReportDetailResponse.java
│   ├── ReportStreamResponse.java
│   ├── CreateReportResponse.java
│   ├── HeatmapResponse.java        ← Inner class Point con lat/lng
│   ├── MyVotesResponse.java
│   ├── VoteRequest.java
│   ├── VoteResponse.java
│   ├── SimpleResponse.java
│   ├── UpdateProfileRequest.java
│   ├── UpdateReportRequest.java
│   ├── AvatarUploadResponse.java
│   └── NotificationPayloadModel.java
│
├── network/
│   ├── ApiClient.java              ← Singleton Retrofit (BASE_URL fija)
│   ├── ApiService.java             ← Interface Retrofit con todos los endpoints
│   └── AuthInterceptor.java        ← Inyecta Bearer token + maneja 401
│
├── service/
│   ├── MyFirebaseMessagingService.java   ← Recibe FCM, muestra notificación
│   ├── FcmNotificationManager.java      ← Validación, dedup, caché FCM
│   ├── NotificationChannelHelper.java   ← Crea canales Android O+
│   ├── VoteActionReceiver.java          ← BroadcastReceiver para botones de notif
│   └── ActivityStateManager.java        ← Detecta si usuario está en movimiento
│
├── ui/
│   ├── MapFragment.java            ← Fragment principal (1300+ líneas)
│   ├── MapViewModel.java           ← ViewModel: marcadores, poller, filtros
│   ├── MarkerRenderer.java         ← Dibuja/actualiza marcadores en Mapbox
│   ├── LocationTracker.java        ← FusedLocationProvider wrapper
│   ├── NearbyReportChecker.java    ← Detecta reportes votables cercanos
│   ├── ReportDetailBottomSheet.java ← Bottom sheet con detalle del reporte
│   ├── UserProfileBottomSheet.java  ← Perfil, mis reportes, mis votos
│   ├── RadialMenuDialogFragment.java ← Menú radial para seleccionar categoría
│   ├── ReportPoller.java            ← Polling periódico de /reports/stream/changes
│   ├── ReportRepository.java        ← Acceso centralizado a datos de reportes
│   ├── MyReportsAdapter.java        ← RecyclerView adapter mis reportes
│   ├── MyVotesAdapter.java          ← RecyclerView adapter mis votos
│   ├── SnackbarHelper.java          ← Snackbars tipificados (SUCCESS/ERROR/WARNING/INFO)
│   └── vote/
│       ├── VoteState.java           ← Enum: NONE/CONFIRM/RESOLVE
│       └── VoteStateManager.java    ← Estado de voto por reporte en sesión
│
├── util/
│   ├── TokenManager.java           ← SharedPreferences: JWT token + user_id
│   ├── CategoryMapper.java         ← Slug → ID de categoría y grupo de filtro
│   ├── ConnectivityHelper.java     ← Checa si hay conexión activa
│   ├── HeadingManager.java         ← SensorManager: orientación del dispositivo
│   ├── LocationUtil.java           ← Haversine, constante 500m
│   ├── SettingsManager.java        ← Preferencias: mapa preset, onboarding
│   └── remember_token (campo)      ← Solo en User model del server
│
└── work/
    ├── ReportSyncWorker.java       ← Worker que procesa cola pendiente
    └── SyncManager.java            ← Scheduleó/dispara el Worker
```

## Application class: `ReporteCiudadanoApp`

Inicializa `ApiClient.init(context)` una sola vez al arrancar la app.
Esto crea el singleton Retrofit con el `AuthInterceptor` y `HttpLoggingInterceptor`.

## Actividades y navegación

```
MainActivity (launcher)
    ├── Si no logueado: → LoginActivity
    │       ├── → RegisterActivity
    │       ├── → ForgotPasswordActivity → ResetPasswordActivity
    │       └── Google Sign-In
    └── Si logueado: muestra MapFragment
            └── UserProfileBottomSheet
                └── → logout → LoginActivity
```

`MainActivity` también maneja deep links:
- Scheme: `reporteciudadano://show_report?id=<reportId>`
- Abre la app desde una notificación FCM y navega al reporte

## Permisos declarados en `AndroidManifest.xml`

| Permiso | Uso |
|---------|-----|
| `INTERNET` | Llamadas a la API |
| `ACCESS_NETWORK_STATE` | Detectar conexión |
| `ACCESS_FINE_LOCATION` | GPS preciso para mapa y votos |
| `ACCESS_COARSE_LOCATION` | Fallback de ubicación |
| `CAMERA` | Fotos para reportes |
| `POST_NOTIFICATIONS` | Android 13+ para FCM |
| `ACTIVITY_RECOGNITION` | Detectar si el usuario está en movimiento (RF-24) |

## Componentes registrados

- **Service**: `MyFirebaseMessagingService` — recibe mensajes FCM
- **Receiver**: `ActivityStateManager$ActivityTransitionReceiver` — transiciones de actividad física
- **Receiver**: `VoteActionReceiver` — acciones de voto desde notificación
- **Provider**: `FileProvider` — para compartir archivos de foto (autoridad: `${applicationId}.fileprovider`)
- **MetaData**: `default_notification_channel_id = "report_notifications"`
