# DEFENSA — ReporteCiudadano
> Guía personal de preparación. Rúbrica total: 10 pts.

---

## RÚBRICA RÁPIDA

| Criterio | Pts | Para sacar EXCELENTE |
|---|---|---|
| Funcionalidad en vivo (10 min, sin IA/internet) | 2.0 | Implementar autónomamente, explicar cada paso mientras programás |
| Demostración funcionalidad completa | 1.5 | Ejecutar TODAS las funcionalidades sin errores, flujo completo |
| Preguntas técnicas (proyecto + Android general) | 1.5 | Responder con dominio el proyecto COMPLETO (incluyendo lo del compañero) |
| Descripción del proyecto (idea + beneficios + usuarios) | 1.5 | Describir con seguridad, argumentos sólidos |
| Explicar conceptos técnicos | 1.5 | Vocabulario técnico, vincular decisiones con teoría del curso |
| Expresión adecuada | 0.75 | Fluidez, sin leer apuntes, vocabulario técnico |
| Orden en la presentación | 0.75 | Estructura lógica: intro → técnico → demo → cierre |
| Vestimenta formal y protocolo | 0.5 | Formal, puntual, profesional |

**TIEMPO TOTAL POR ESTUDIANTE: ~20-25 min**

---

## 1. DESCRIPCIÓN DEL PROYECTO (1.5 pts)

### Idea principal
**ReporteCiudadano** es una aplicación Android que permite a ciudadanos reportar problemas urbanos (baches, alumbrado roto, basura, etc.) geolocalizados en un mapa en tiempo real. La comunidad valida y resuelve los reportes mediante un sistema de votos.

### Beneficios / Valor
- Centraliza reportes ciudadanos en un mapa colaborativo
- Sistema de votos comunitario: distingue reportes reales de falsos mediante validación colectiva
- Notificaciones push inteligentes: solo avisa cuando el usuario está caminando cerca del problema
- Funciona offline: guarda acciones y las sincroniza al recuperar conexión
- Transparencia del estado: `pending` → `verified` → `resolved` → `archived`

### Tipos de usuario (perfiles)
| Perfil | Descripción |
|---|---|
| **Ciudadano reportador** | Crea reportes de problemas que ve en la calle |
| **Ciudadano verificador** | Vota "Sigue ahí" o "Ya se resolvió" en reportes cercanos |
| **Observador** | Solo consulta el mapa, no crea reportes (sin login) |

---

## 2. CONCEPTOS TÉCNICOS APLICADOS (1.5 pts)

### Arquitectura general
```
[Android App] ──HTTPS Bearer Token──► [Cloudflare Tunnel]
                                              │
                                        [Nginx :8082]
                                              │
                                      [PHP-FPM Laravel]
                                         │         │
                                      MySQL 8    Firebase Admin SDK
                                                      │
                                                  FCM ──► [Android App]
```

### Componentes Android del curso usados
| Componente | Implementación en el proyecto |
|---|---|
| **Activity** | `MainActivity`, `LoginActivity`, `RegisterActivity`, `OnboardingActivity`, `ForgotPasswordActivity`, `ResetPasswordActivity` |
| **Fragment** | `MapFragment` (pantalla principal), `ReportDetailBottomSheet`, `RadialMenuDialogFragment`, `UserProfileBottomSheet` |
| **ViewModel** | `MapViewModel` — sobrevive rotaciones, guarda marcadores y estado del poller |
| **LiveData** | `vm.reportUpdated` — actualiza marcadores desde el poller sin tocar UI en background |
| **Room** | 3 entidades: `report_cache`, `pending_actions`, `fcm_notification_cache` |
| **WorkManager** | `ReportSyncWorker` — sincroniza acciones offline cuando vuelve la conexión |
| **Retrofit** | `ApiClient` — consumo de REST API con OkHttp |
| **SharedPreferences** | `TokenManager` (JWT token + user_id), `SettingsManager` (filtros del mapa, onboarding) |
| **BroadcastReceiver** | `VoteActionReceiver` (acciones de notificación), `ActivityTransitionReceiver` (movimiento físico) |
| **Service** | `MyFirebaseMessagingService` — recibe mensajes FCM |
| **FusedLocationProvider** | `LocationTracker` — GPS continuo para mapa y validación de distancia |
| **Permisos en runtime** | `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION` |

### Patrones de diseño usados
- **Singleton**: `ApiClient`, `TokenManager`, `AppDatabase`
- **Repository**: `ReportRepository` — acceso centralizado a datos de reportes
- **Observer** (LiveData): `vm.reportUpdated` observado en `MapFragment`
- **Interceptor** (OkHttp): `AuthInterceptor` inyecta Bearer token y captura 401

### Persistencia de datos
- **Local (Room)**: caché de reportes, acciones pendientes offline, caché FCM
- **Remota (MySQL via API)**: usuarios, reportes, votos, categorías, tokens
- **SharedPreferences**: token de sesión, configuración del mapa

### Consumo de API REST
- Retrofit + OkHttp con `AuthInterceptor`
- Bearer Token (Laravel Sanctum)
- Gson para deserialización JSON
- Manejo de errores HTTP: 401 → redirect login, 409 → voto duplicado, 422 → validación, 403/500 → snackbar

---

## 3. FLUJO COMPLETO DE LA APP (para la demo — 1.5 pts)

### Flujo principal paso a paso
```
1. App abre → MainActivity → verifica token en SharedPreferences
2. Sin sesión → LoginActivity → email/pass o Google Sign-In
3. Con sesión → carga MapFragment
4. MapFragment carga estilo Mapbox STANDARD + terreno 3D
5. Solicita permisos GPS → FusedLocationProvider trackea ubicación
6. Camera idle 600ms → fetch reportes del viewport actual (bounding box)
7. Marcadores coloreados por estado: PENDING(amarillo) / VERIFIED(naranja) / RESOLVED(verde)
8. Zoom < 13 → cambia a heatmap automáticamente
9. Long press en mapa (o FAB +) → RadialMenuDialogFragment → 8 categorías
10. Validación: ≤500m del punto elegido → POST /api/reports → nuevo marcador
11. Toque en marcador → ReportDetailBottomSheet → votar "Sigue ahí" / "Ya se resolvió"
12. Votación requiere estar ≤500m del reporte
13. FCM notifica a otros usuarios cercanos → notificación con botones de voto inline
14. Sin conexión → acciones se guardan en Room → WorkManager sincroniza al volver
```

### Estados de reporte y transiciones
```
pending → verified (cuando llega a X votos "confirm")
verified → resolved (cuando llega a X votos "resolve")
resolved → archived (scheduler automático: 2h después de resolved)
pending/verified → archived (sin interacción en 24h)
```

---

## 4. FUNCIONALIDADES CANDIDATAS PARA DESARROLLO EN VIVO (2.0 pts)

> El docente elige UNA. Prepará todas. Sin IA/internet. Máx 10 min.

### A) Agregar un nuevo endpoint simple en Laravel (sugerido)
**Ejemplo: GET /api/reports/count — contar reportes por status**
```php
// En routes/api.php (dentro del grupo auth:sanctum)
Route::get('reports/count', [ReportController::class, 'count']);

// En ReportController.php
public function count() {
    $counts = Report::selectRaw('status, count(*) as total')
                    ->whereNotIn('status', ['archived'])
                    ->groupBy('status')
                    ->get();
    return response()->json(['success' => true, 'counts' => $counts]);
}
```

### B) Agregar un método en Android — mostrar distancia en el BottomSheet
```java
// En ReportDetailBottomSheet.java, en setupVoting() o bindData()
if (userLocation != null && report.getLatitude() != 0) {
    float[] results = new float[1];
    Location.distanceBetween(
        userLocation.getLatitude(), userLocation.getLongitude(),
        report.getLatitude(), report.getLongitude(), results
    );
    int distMeters = (int) results[0];
    tvDistance.setText(distMeters + " m de distancia");
    tvDistance.setVisibility(View.VISIBLE);
}
```

### C) Nuevo filtro de reportes — filtrar por "mis reportes"
```java
// En MapFragment.java, en passesFilters(report)
if (filterOnlyMine) {
    int myUserId = TokenManager.getInstance(requireContext()).getUserId();
    if (report.getUserId() != myUserId) return false;
}
```

### D) Agregar validación extra al crear reporte — categoría requerida
```java
// En onReportCategorySelected() en MapFragment.java
if (categoryId <= 0) {
    SnackbarHelper.showError(requireView(), "Seleccioná una categoría válida");
    return;
}
```

---

## 5. PREGUNTAS FRECUENTES — Respuestas preparadas (1.5 pts)

### Sobre el proyecto

**¿Cómo funciona el sistema de votos?**
> Cada usuario autenticado puede votar "confirm" (sigue ahí) o "resolve" (ya se resolvió) en reportes a ≤500m de su ubicación actual. La validación de distancia se hace tanto en el cliente (Haversine en `LocationUtil.java`) como en el servidor (Laravel calcula Haversine antes de insertar el voto). Si el reporte acumula suficientes votos "confirm" pasa a `verified`; con votos "resolve" pasa a `resolved`.

**¿Qué pasa cuando no hay internet?**
> `ConnectivityHelper.isOnline()` detecta si hay red. Sin conexión: los reportes se leen del caché de Room (`ReportCacheEntity`). Las acciones nuevas (crear reporte, votar) se guardan en `PendingActionEntity` con status "pending". Al volver la conexión, `NetworkCallback.onAvailable()` dispara `SyncManager.syncNow()` que lanza `ReportSyncWorker` via WorkManager.

**¿Cómo funciona el WorkManager?**
> `ReportSyncWorker` extiende `Worker`. Se registra con `NetworkType.CONNECTED` como constraint. Recorre la cola de `PendingActionEntity`, ejecuta cada acción de forma síncrona (`.execute()`). Si la API retorna 2xx o error definitivo (403/409/422) → borra la acción. Si retorna error transitorio (500/503) → incrementa `retryCount`. A los 5 intentos fallidos → `status = "failed"`. El Worker retorna `Result.retry()` si hubo errores de red.

**¿Cómo funciona FCM?**
> El servidor (Laravel) usa Firebase Admin SDK (Kreait) para enviar push notifications al crear o cambiar el estado de un reporte. El cliente tiene `MyFirebaseMessagingService` que extiende `FirebaseMessagingService`. Al recibir un mensaje: valida campos mínimos, verifica que no sea duplicado (Room), verifica que el usuario esté a ≤500m, verifica que el usuario esté caminando (RF-24 via `ActivityStateManager`). Si pasa todos los filtros → muestra la notificación con botones de voto inline.

**¿Qué es Sanctum y cómo se usa?**
> Laravel Sanctum es el sistema de autenticación via tokens de acceso personal. Al hacer login, el servidor genera un `plainTextToken` que el cliente guarda en `SharedPreferences` via `TokenManager`. Cada request envía ese token en el header `Authorization: Bearer <token>`. El `AuthInterceptor` de OkHttp lo inyecta automáticamente. Si el servidor devuelve 401, el callback de `ApiClient` redirige al LoginActivity.

**¿Por qué usaron Cloudflare Tunnel?**
> Para exponer la API Laravel (corriendo en servidor local en `192.168.1.138`) a internet de forma segura con HTTPS, sin abrir puertos en el router. El túnel corre como contenedor Docker y mapea tráfico de `api.manuelmv.net` al Nginx interno.

**¿Qué es el heatmap y cuándo aparece?**
> Al hacer zoom out (nivel < 13.0), en vez de mostrar marcadores individuales que saturarían la pantalla, se muestra un mapa de calor (HeatmapLayer de Mapbox). Los puntos se prefetchean al iniciar la app: GET `/api/reports/heatmap` retorna hasta 2000 puntos lat/lng. Se guardan en `MapViewModel` (sobreviven rotación de pantalla).

**¿Cómo funciona el menú radial?**
> `RadialMenuDialogFragment` es un `DialogFragment` que muestra 8 categorías (baches, alumbrado, agua, etc.) distribuidas en forma circular. Al hacer long press en el mapa o tocar el FAB "+", se abre con las coordenadas del punto elegido. Al seleccionar categoría → `MapFragment.onReportCategorySelected()` valida distancia y duplicados, luego POST a la API.

**¿Qué hace el ActivityStateManager?**
> Usa `ActivityRecognitionClient` de Google Play Services para detectar el estado de movimiento del usuario. Escucha transiciones de actividad (WALKING, RUNNING, STILL, IN_VEHICLE, ON_BICYCLE). Las notificaciones push de reportes cercanos solo se muestran si el usuario está CAMINANDO o CORRIENDO — idea: no molestar a alguien en auto que pasa rápido.

**¿Cómo funciona el debounce de la cámara?**
> `MapFragment` usa un `Handler` con un `Runnable` (`cameraIdleRunnable`). Al mover el mapa se cancela el Runnable anterior y se postea uno nuevo con 600ms de delay. Solo cuando el mapa lleva 600ms quieto se dispara el fetch de reportes. Evita llamadas a la API en cada pixel de movimiento.

---

### Sobre Android general

**Ciclo de vida de una Activity**
```
onCreate → onStart → onResume → [en uso]
         → onPause → onStop → onDestroy
         → onPause → onStop → onCreate (rotación/kill)
         → onPause → onResume (vuelve al frente)
```

**¿Cuándo usar Fragment vs Activity?**
> Activity = pantalla completa independiente. Fragment = sección reutilizable dentro de una Activity. En este proyecto: `MainActivity` es el contenedor, `MapFragment` ocupa toda la pantalla pero es Fragment para poder manejar el ciclo de vida del mapa con `onStart/onStop`.

**¿Qué es Room y por qué usarlo en vez de SQLite directo?**
> Room es la capa de abstracción de SQLite de Android Jetpack. Ventajas: verificación de queries en tiempo de compilación (no en runtime), soporte nativo para LiveData/Flow, evita código boilerplate de Cursor. Prohíbe I/O en el hilo principal — se detecta en compilación.

**¿Qué es LiveData?**
> Holder de datos observable que respeta el ciclo de vida (lifecycle-aware). Solo notifica a observers que están en estado activo (STARTED/RESUMED). Evita memory leaks y crashes por actualizar UI en Activities destruidas.

**¿Qué es ViewModel?**
> Clase diseñada para sobrevivir cambios de configuración (rotación de pantalla). En `MapFragment`, `MapViewModel` guarda los marcadores del mapa y el `ReportPoller` — sin ViewModel, rotar el teléfono destruiría todos los marcadores cargados.

**¿Qué es WorkManager?**
> API de Jetpack para trabajo en background garantizado, incluso si el app cierra o el sistema reinicia el dispositivo. Soporta constraints (red, carga), reintentos con backoff, trabajo periódico y único. Ideal para sincronización offline.

**¿Diferencia entre Service y BroadcastReceiver?**
> `Service`: corre en background de forma continua o por tarea larga. `BroadcastReceiver`: responde a eventos del sistema o del app (disparados con Intent) de forma breve y stateless. `VoteActionReceiver` es un BroadcastReceiver — recibe el Intent del botón de la notificación, hace el voto a la API, y termina.

**¿Qué son los Intents explícitos vs implícitos?**
> Explícito: nombra la clase destino (`new Intent(this, LoginActivity.class)`). Implícito: describe la acción y el sistema decide quién la maneja (`ACTION_VIEW` con una URL). En este proyecto se usan deep links: `reporteciudadano://show_report?id=X` — Intent implícito que abre la app desde una notificación.

**¿Qué es Retrofit?**
> Librería HTTP type-safe para Android. Define los endpoints como métodos Java en una interface (`ApiService.java`) con anotaciones (`@GET`, `@POST`, `@Body`, etc.). Retrofit genera la implementación. Usa OkHttp como cliente HTTP y Gson para serialización.

**¿Qué son los Interceptors de OkHttp?**
> Middleware de la cadena HTTP. Se ejecutan antes y/o después de cada request. `AuthInterceptor` agrega el header de autenticación a TODAS las requests sin repetir código en cada llamada. `HttpLoggingInterceptor` loguea el body completo para debugging.

**Buenas prácticas que aplicamos:**
- No hacer I/O en el hilo principal (Room en `ExecutorService`, Retrofit en callbacks)
- No retener referencias a Activity en background threads (LiveData + observers lifecycle-aware)
- Limpiar recursos en `onDestroyView` (`dbExecutor.shutdown()`, `binding = null`)
- Validar permisos en runtime antes de usarlos
- Caché local para experiencia offline

---

## 6. CHECKLIST DE LOGÍSTICA

### Antes de la defensa
- [ ] Proyecto compila sin errores en Android Studio
- [ ] App instalada y probada en dispositivo/emulador
- [ ] API en producción funcionando (`https://api.manuelmv.net/api/`)
- [ ] Repositorio GitHub con historial de commits visible
- [ ] Android Studio abierto con el proyecto cargado
- [ ] Cuenta de prueba creada y logueada en el dispositivo
- [ ] Batería del laptop y celular cargada

### Durante la defensa
- [ ] Estructura: introducción (2 min) → conceptos técnicos (5 min) → demo completa (5 min) → en vivo (10 min) → preguntas (3 min)
- [ ] No leer de apuntes — conocer el proyecto de memoria
- [ ] Hablar de TODO el proyecto, no solo "mi parte"
- [ ] Si el en vivo sale mal → explicar en voz alta el razonamiento (el docente evalúa el avance)

---

## 7. PENALIZACIONES — EVITAR

| Riesgo | Qué evitar |
|---|---|
| 0 en funcionalidad en vivo | Abrir ChatGPT o Google durante el desarrollo en vivo |
| 0 en demo completa | App sin compilar al llegar |
| Penalización preguntas | No poder explicar código del compañero |
| Penalización protocolo | Llegar tarde sin aviso |
| Anulación total | Plagio detectado |

---

## 8. ESTRUCTURA LÓGICA DE PRESENTACIÓN

```
[~2 min] INTRODUCCIÓN
  → "ReporteCiudadano es una app Android que..."
  → Problema que resuelve, usuarios target
  → Stack: Android Java + Laravel API + MySQL + Firebase + Mapbox

[~5 min] CONCEPTOS TÉCNICOS
  → Mostrar diagrama de arquitectura
  → Componentes Android usados (ViewModel, LiveData, Room, WorkManager, FCM)
  → Explicar decisiones: por qué Room en vez de SQLite crudo, por qué WorkManager para offline, por qué Sanctum

[~5 min] DEMOSTRACIÓN COMPLETA
  → Login con Google
  → Mapa cargando reportes del viewport
  → Crear reporte (long press o FAB)
  → Ver detalle y votar
  → Mostrar notificación FCM (si hay otra sesión activa)
  → Mostrar filtros
  → Mostrar heatmap (zoom out)

[~10 min] FUNCIONALIDAD EN VIVO
  → Lo que asigne el docente

[~3 min] CIERRE / PREGUNTAS
  → Qué faltó / qué mejoraríamos (clustering, Mi Perfil completo)
```
