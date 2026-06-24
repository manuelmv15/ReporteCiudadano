# 15 — Laravel: Notificaciones Push (FCM)

## NotificationService.php

### Constructor

```php
$credentialsPath = base_path(env('FIREBASE_CREDENTIALS_PATH'));
$factory = (new Factory)->withServiceAccount($credentialsPath);
$this->messaging = $factory->createMessaging();
```

Archivo de credenciales: `storage/app/firebase-credentials.json` (JSON de service account de Firebase).

Si falla la inicialización → `$this->messaging = null` y todas las notificaciones se silencian (no crashea).

Librería: `kreait/firebase-php` (Kreait Firebase Admin SDK).

---

### `notifyNearbyUsers(Report $report)`

Llamada al crear un reporte nuevo.

```php
$users = User::whereNotNull('fcm_token')
    ->where('id', '!=', $report->user_id)  // no notificar al creador
    ->get();
```

Para cada usuario con token:
```php
$message = CloudMessage::new()
    ->withToken($user->fcm_token)
    ->withNotification(Notification::create(
        title: $report->category->name,
        body: substr($report->description, 0, 100)
    ))
    ->withData([
        'report_id' => (string) $report->id,
        'latitude'  => (string) $report->latitude,
        'longitude' => (string) $report->longitude,
        'status'    => $report->status,
        'type'      => 'new_report',
    ]);

$this->messaging->send($message);
```

**Importante:** El filtro de distancia lo hace el cliente Android (`FcmNotificationManager.isUserInVotingRange()`). El servidor notifica a TODOS los usuarios con token, y el Android decide si mostrar o suprimir según distancia.

Errores de envío individual se loguean pero no interrumpen el loop.

---

### `notifyVoters(Report $report, string $previousStatus)`

Llamada al cambiar el estado de un reporte (manual o automático).

```php
$voterIds = $report->votes()->pluck('user_id')->unique();
$users = User::whereIn('id', $voterIds)->whereNotNull('fcm_token')->get();
```

Para cada votante con token:
```php
->withNotification(Notification::create(
    title: 'Estado del reporte cambió',
    body: "{$report->category->name}: {$previousStatus} → {$report->status}"
))
->withData([
    'report_id'       => (string) $report->id,
    'new_status'      => $report->status,
    'previous_status' => $previousStatus,
])
```

No incluye `latitude`/`longitude` en el data (no es reporte nuevo, no se evalúa distancia en cliente).

---

## Eventos de broadcasting (Pusher/Laravel Echo — opcional)

Laravel también dispara eventos que implementan `ShouldBroadcast`. Útiles si se agrega un dashboard web.

### ReportCreated

- Canal: `Channel('reports')` (canal público)
- Evento: `report.created`
- Payload: id, lat, lng, status, description, category_id, user_id, votos, photo_path, created_at

### ReportStatusChanged

- Canal: `Channel('reports')` (canal público)
- Evento: `report.status.changed`
- Payload: id, status, previous_status, lat, lng, votos, updated_at

Ambos se disparan en `ReportController` y en `Report::archive()`. Si no hay driver de broadcasting configurado (Pusher key vacía), Laravel los ignora silenciosamente.

---

## Email (Brevo / Sendinblue)

### PasswordResetMail.php (alternativo, no usado actualmente)

Existe como Mailable de Laravel pero el controller usa HTTP directo a Brevo:
```php
Http::withHeaders(['api-key' => env('BREVO_API_KEY')])
    ->post('https://api.brevo.com/v3/smtp/email', [...])
```

Template: `resources/views/emails/password-reset.blade.php` — muestra el código de 6 dígitos.

Variables del template:
- `$token` — código de 6 dígitos (en texto plano, no hasheado)
- `$email` — email del destinatario
