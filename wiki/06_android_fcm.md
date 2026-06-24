# 06 — Android: Firebase Cloud Messaging

## MyFirebaseMessagingService.java

Extiende `FirebaseMessagingService`. Declarado en `AndroidManifest.xml`.

### `onMessageReceived(RemoteMessage)`

Flujo completo de procesamiento:

```
FCM mensaje llega
    │
    ▼
NotificationChannelHelper.createNotificationChannels(this)  // idempotente
    │
    ▼
FcmNotificationManager.parseNotificationPayload(remoteMessage)  → NotificationPayloadModel
    │
    ▼
validateNotificationData(payload)  // campos mínimos: reportId, lat, lng, title, body
    │ inválido → return (silencio)
    ▼
isDuplicateNotification(reportId)  // busca en Room (FcmNotificationCacheEntity)
    │ duplicado → cacheNotification() + return (no muestra)
    ▼
isUserInVotingRange(lat, lng)  // verifica distancia actual del usuario ≤ 500m
    │ fuera de rango → return (suprime)
    ▼
ActivityStateManager.isUserMoving()  // RF-24: si está parado o en vehículo → suprimir
    │ no moviendo → return (suprime)
    ▼
cacheNotification(payload)   // guarda en Room
markAsProcessed(reportId)    // marca en Room
    │
    ▼
showNotification(payload)    // muestra notificación del sistema
```

### `showNotification(payload)`

Crea notificación con:
- Small icon: `warning_24px`
- Deep link: `reporteciudadano://show_report?id={reportId}` → `MainActivity`
- `BigTextStyle` para expandir texto largo
- **Acciones inline (RF-22):**
  - "Sigue ahí" → `VoteActionReceiver.ACTION_VOTE_CONFIRM`
  - "Ya se resolvió" → `VoteActionReceiver.ACTION_VOTE_RESOLVE`
- PendingIntent con `reportId * 10 + 1` (confirm) y `reportId * 10 + 2` (resolve) como request codes únicos

Canal elegido por `NotificationChannelHelper.getChannelIdForNotification(type, distance)`.

### `onNewToken(String token)`

Cuando FCM genera un nuevo token:
1. Llama `sendTokenToServer(token)`
2. Lee auth token de `TokenManager`
3. Si no hay sesión activa → log warning, no envía
4. Si hay sesión → POST `/api/me/fcm-token?fcm_token=<token>`

---

## VoteActionReceiver.java

`BroadcastReceiver` para las acciones de notificación.

Constantes:
```java
ACTION_VOTE_CONFIRM = "com.bombayashi.reporteciudadano.ACTION_VOTE_CONFIRM"
ACTION_VOTE_RESOLVE = "com.bombayashi.reporteciudadano.ACTION_VOTE_RESOLVE"
EXTRA_REPORT_ID = "report_id"
EXTRA_LAT = "latitude"
EXTRA_LNG = "longitude"
```

Al recibir el broadcast:
1. Extrae `report_id`, `latitude`, `longitude` del Intent
2. Crea `VoteRequest` con el tipo correspondiente
3. Llama `ApiClient.getInstance().submitVote(reportId, voteRequest).enqueue()`
4. En `onResponse` exitoso → cancela la notificación con `NotificationManager.cancel(reportId)`

---

## NotificationChannelHelper.java

Crea canales para Android O+ (`Build.VERSION_CODES.O`). Es idempotente (el sistema ignora si ya existen).

Canales creados:
| ID | Nombre | Importance | Uso |
|----|--------|------------|-----|
| `"report_notifications"` | "Reportes cercanos" | HIGH | Reportes nuevos cerca |
| `"status_updates"` | "Actualizaciones de estado" | DEFAULT | Cambios de estado de reportes votados |

`getChannelIdForNotification(type, distance)`:
- `type == "new_report"` → `"report_notifications"`
- Cualquier otro → `"status_updates"`

---

## ActivityStateManager.java

Detecta el estado de actividad física del usuario (RF-24).

- Usa `ActivityRecognitionClient` de Google Play Services
- Registra `ActivityTransitionReceiver` para transiciones: IN_VEHICLE, ON_BICYCLE, WALKING, RUNNING, STILL
- `isUserMoving()` retorna `true` si la última actividad detectada es `WALKING` o `RUNNING`
- Las notificaciones de reportes cercanos solo se muestran si `isUserMoving() == true`

---

## FcmNotificationManager.java

Clase utilitaria singleton que gestiona:

- `parseNotificationPayload(remoteMessage)` → extrae datos del `RemoteMessage` a `NotificationPayloadModel`
- `validateNotificationData(payload)` → valida campos requeridos no nulos
- `isDuplicateNotification(reportId)` → consulta `FcmNotificationCacheDao`
- `isUserInVotingRange(lat, lng)` → calcula distancia con la última ubicación conocida ≤ 500m
- `cacheNotification(payload)` → inserta en `FcmNotificationCacheEntity`
- `markAsProcessed(reportId)` → update en Room

---

## NotificationPayloadModel.java

POJO con los campos del payload FCM:

```java
int reportId
double latitude
double longitude
String status
String type        // "new_report" | "status_changed"
String title       // título de la notificación (viene del FCM data payload)
String body        // cuerpo
float distance     // distancia calculada del usuario (opcional)
```
