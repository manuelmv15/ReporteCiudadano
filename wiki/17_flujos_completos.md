# 17 — Flujos End-to-End Completos

## Flujo 1: Registro y primer inicio de sesión

```
[Android] RegisterActivity
    ↓ POST /api/register {name, email, password, password_confirmation}
[Laravel] AuthController@register
    ↓ validate → User::create → createToken
    ↓ retorna {token, user}
[Android] TokenManager.saveToken(token, user.id)
    ↓ si !onboarding_done → OnboardingActivity (slides)
    ↓ MainActivity → MapFragment (carga el mapa)
```

---

## Flujo 2: Login con Google

```
[Android] LoginActivity → Google Sign-In SDK
    ↓ obtiene id_token
    ↓ POST /api/auth/google {id_token}
[Laravel] AuthController@googleLogin
    ↓ Google\Client::verifyIdToken(id_token)
    ↓ busca user por google_id O email
    ↓ crea o actualiza usuario
    ↓ retorna {token, user}
[Android] TokenManager.saveToken → MainActivity
```

---

## Flujo 3: Crear reporte (online)

```
[Android] Usuario long-press en mapa (o FAB)
    ↓ valida ubicación GPS disponible
    ↓ valida distancia ≤ 500m entre userLocation y punto del long press
    ↓ RadialMenuDialogFragment → usuario selecciona categoría
    ↓ hasSameCategoryNearby() → busca en reportMarkers a <50m misma categoría
    ↓ ConnectivityHelper.isOnline() → true
    ↓ POST /api/reports {category_id, latitude, longitude, description}
        (con Authorization: Bearer <token>)

[Laravel] ReportController@store
    ↓ validate
    ↓ si hay foto → store en storage/public/reports/
    ↓ Report::create(status='pending')
    ↓ ReportCreated::dispatch() → canal 'reports' broadcast
    ↓ NotificationService@notifyNearbyUsers(report)
        → User::whereNotNull('fcm_token').where('id', '!=', report.user_id).get()
        → para cada user → CloudMessage via FCM → llega a otros Android

    ↓ retorna 201 {success, report}

[Android] addReportMarker(report) → MarkerRenderer dibuja marcador en mapa
    ↓ Snackbar "Reporte creado exitosamente"
```

---

## Flujo 4: Crear reporte (offline)

```
[Android] ConnectivityHelper.isOnline() → false
    ↓ queueOfflineReport(categoryId, lat, lng, description)
        → JSONObject payload
        → AppDatabase.pendingActionDao().insert(PendingActionEntity(TYPE_CREATE_REPORT, payload))
    ↓ Snackbar "Sin conexión: reporte guardado, se enviará cuando vuelva la conexión"

[Red vuelve]
[Android] NetworkCallback.onAvailable()
    ↓ SyncManager.syncNow(context)
    ↓ OneTimeWorkRequest<ReportSyncWorker>(constraint: CONNECTED)
    ↓ WorkManager encola

[ReportSyncWorker.doWork()]
    ↓ dao.getPending() → lista de PendingActionEntity
    ↓ para TYPE_CREATE_REPORT:
        ↓ parsea JSON → ReportRequest
        ↓ ApiClient.getInstance().createReport(request).execute() [síncrono]
        ↓ si 2xx o 403/409/422 → dao.delete(action)
        ↓ si otro error → retryCount++ → Result.retry()

[observeSyncWork()] → SUCCEEDED → pollForUpdates() + snackbar
```

---

## Flujo 5: Votar un reporte

```
[Android] Usuario toca marcador → MarkerRenderer.showReportDetails()
    ↓ ReportDetailBottomSheet.newInstance(report, userLocation)
    ↓ Bottom sheet muestra detalles del reporte
    ↓ Usuario pulsa "Confirmar" o "Resolver"
    ↓ valida isLoggedIn()
    ↓ calcula distancia entre userLocation y report.lat/lng
    ↓ si > 500m → mensaje de error
    ↓ POST /api/reports/{id}/votes {type, latitude, longitude}

[Laravel] ReportVoteController@store
    ↓ validate
    ↓ report.distanceInMetersTo(lat, lng) → si > 500 → 422
    ↓ DB::transaction()
        ↓ ReportVote::create()
        ↓ report.increment('votes_confirm' o 'votes_resolve')
        ↓ report.refresh()
        ↓ report.evaluateAutoStatus()
            ↓ si meetsConfirmThreshold() y status='pending':
                → transitionTo('verified')
                → owner.addScore(10)
                → awardVoters('confirm', 2) [score += 2 para cada confirmante]
                → ReportStatusChanged::dispatch()
                → NotificationService@notifyVoters() [FCM a votantes]
            ↓ si meetsResolveThreshold() y status='pending'/'verified':
                → transitionTo('resolved')
                → awardVoters('resolve', 5)
                → [si no había awarded before] awardVoters('confirm', 2)
    ↓ retorna 201 {votes_confirm, votes_resolve, status}

[Android] Actualiza contadores en bottom sheet
    ↓ onReportStatusChanged() → markerRenderer.updateReport() [color del marcador]
```

---

## Flujo 6: Recibir y actuar sobre notificación FCM

```
[Laravel] NotificationService@notifyNearbyUsers() o notifyVoters()
    ↓ FCM envía push a token del dispositivo

[Android] MyFirebaseMessagingService@onMessageReceived()
    ↓ parseNotificationPayload() → NotificationPayloadModel
    ↓ validateNotificationData() → campos mínimos
    ↓ isDuplicateNotification(reportId) → consulta Room
    ↓ isUserInVotingRange(lat, lng) → distancia con última ubicación ≤ 500m
    ↓ ActivityStateManager.isUserMoving() → usuario caminando o corriendo
    ↓ cacheNotification() → Room
    ↓ markAsProcessed()
    ↓ showNotification():
        → NotificationCompat.Builder
        → deep link: reporteciudadano://show_report?id={reportId}
        → acciones: "Sigue ahí" (VoteActionReceiver CONFIRM)
                    "Ya se resolvió" (VoteActionReceiver RESOLVE)

[Usuario toca notificación]
    ↓ MainActivity@handleNotificationIntent()
    ↓ data.getScheme() == "reporteciudadano" && data.getHost() == "show_report"
    ↓ reportId = data.getQueryParameter("id")
    ↓ MapFragment.showReportFromNotification(reportId)
        ↓ busca en reportMarkers (caché en memoria)
        ↓ si encontrado → ReportDetailBottomSheet.show()
        ↓ si no → GET /api/reports/{id} → carga + muestra

[Usuario toca "Sigue ahí" desde notificación]
    ↓ VoteActionReceiver@onReceive(ACTION_VOTE_CONFIRM)
    ↓ VoteRequest(type="confirm", lat, lng)
    ↓ ApiClient.submitVote(reportId, request).enqueue()
    ↓ si exitoso → NotificationManager.cancel(reportId)
```

---

## Flujo 7: Archivado automático

```
[Scheduler container - php artisan schedule:work]
    ↓ ejecuta reports:archive-stale en el horario configurado

[ArchiveStaleReports@handle]
    ↓ Report::archiveStaleReports()
        ↓ resolved con resolved_at ≤ now() - 2h → archive()
        ↓ pending/verified con updated_at ≤ now() - 24h → archive()
        
    [archive() en cada reporte]
        ↓ status = 'archived', status_changed_at = now(), archived_at = now()
        ↓ save()
        ↓ ReportStatusChanged::dispatch(report, previousStatus)

[Android - próximo poll de ReportPoller]
    ↓ GET /api/reports/stream/changes?since=<lastTimestamp>
    ↓ recibe reportes archivados
    ↓ vm.reportUpdated → MapFragment observa
    ↓ passesFilters() → "archived" → false → markerRenderer.removeReport()
    ↓ marcador desaparece del mapa
```

---

## Flujo 8: Polling de actualizaciones (ReportPoller)

```
[MapFragment.onStart()]
    ↓ vm.getPoller().start()

[ReportPoller - cada N segundos]
    ↓ GET /api/reports/stream/changes?since=<lastTimestamp>&limit=50
    ↓ si éxito → guarda timestamp de la respuesta como nuevo 'since'
    ↓ para cada reporte en la respuesta:
        → vm.reportUpdated.postValue(report)

[MapFragment.reportUpdated observer]
    ↓ si reporte en reportMarkers → markerRenderer.updateReport()
    ↓ si reporte nuevo → markerRenderer.addReport()
```

---

## Flujo 9: Reset de contraseña

```
[Android] ForgotPasswordActivity
    ↓ POST /api/forgot-password {email}
[Laravel] → genera token 6 dígitos → hash → guarda en DB → email via Brevo
    ↓ retorna {success: true, message: "Si el correo existe..."}
    (respuesta es siempre la misma para evitar enumeración)

[Android] ResetPasswordActivity
    ↓ usuario ingresa código de 6 dígitos + nueva contraseña
    ↓ POST /api/reset-password {email, token, password, password_confirmation}
[Laravel] → busca en DB → verifica expiración (60 min) → Hash::check(token, hash)
    ↓ si OK → User.password = Hash::make(newPassword) → borra registro del token
    ↓ retorna {success: true}
[Android] → navega a LoginActivity
```
