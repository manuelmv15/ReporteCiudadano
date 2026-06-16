# Features — Estado vs Planteamiento v1

> Actualizar cada vez que se implementa una función nueva.
> Última revisión: 2026-06-15

**Leyenda:** ✅ completo · 🔶 a medias · ❌ no iniciado

---

## Módulo A — Autenticación

| ID     | Requerimiento                                                       | Estado | Notas                                                                                                                                                                                                                                                                                                                                           |
| ------ | ------------------------------------------------------------------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RF-A01 | Registro con correo/contraseña o Google OAuth                       | ✅      | LoginActivity + RegisterActivity + /auth/google                                                                                                                                                                                                                                                                                                 |
| RF-A02 | Sesión persistente con token en EncryptedSharedPreferences          | ✅      | TokenManager singleton (util/TokenManager.java) usa EncryptedSharedPreferences AES256-GCM/SIV; fallback a prefs normales si crypto no disponible; guarda token + user_id + user_name + user_email; todos los callers migrados (LoginActivity, RegisterActivity, UserProfileBottomSheet, MapFragment, ReportDetailBottomSheet, VoteStateManager) |
| RF-A03 | Recuperación de contraseña vía correo (API Laravel SMTP)            | ❌      | No implementado en Android ni en API                                                                                                                                                                                                                                                                                                            |
| RF-A04 | Modo invitado: ver mapa sin auth, bloquear crear/votar con 401      | ✅      | MainActivity como launcher; mapa visible sin auth; fab_add_report oculto; fab_profile → LoginActivity; ReportDetailBottomSheet oculta botones de voto y muestra btnLoginToVote; 401 en crear/votar/editar/foto → clearToken + redirect a login; backend confirmado: POST/PUT/DELETE/PATCH auth:sanctum, GET público                             |
| RF-A05 | Perfil (nombre, avatar) creado automáticamente en primer login      | ✅      | UserProfileBottomSheet (pageProfile) muestra nombre/email/avatar; editar nombre (PUT /me) y foto (POST /me/avatar) persisten server-side                                                                                                                                                                                                        |
| RF-A06 | Cerrar sesión elimina token del dispositivo y lo revoca en servidor | ✅      | handleLogout() en UserProfileBottomSheet                                                                                                                                                                                                                                                                                                        |

---

## Módulo 1 — Reporte ultrarrápido

| ID    | Requerimiento                                             | Estado | Notas                                                                                                                               |
| ----- | --------------------------------------------------------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| RF-01 | Toque largo en mapa → menú radial con 8 categorías        | ✅      | RadialMenuDialogFragment + OnMapLongClickListener                                                                                   |
| RF-02 | Toque en categoría envía reporte sin formulario adicional | ✅      | POST /reports desde RadialMenuDialogFragment                                                                                        |
| RF-03 | GPS capturado del punto tocado o centro de mira (crosshair) | ✅      | Mira central (crosshair) añadida; captura de coordenadas vía mapboxMap.getCameraState().getCenter(); alta precisión |
| RF-04 | Foto y descripción opcionales desde tarjeta del reporte   | ✅      | Owner edita descripción + sube foto (cámara/galería) via PUT /reports/{id}; foto fullscreen al tap; storage:link activo en servidor |
| RF-05 | Sin conexión: guardar en Room DB + WorkManager sync       | ✅      | db/ (AppDatabase, ReportCacheEntity, PendingActionEntity + DAOs) + work/ReportSyncWorker + SyncManager. MapFragment cachea reportes en Room al cargar; si offline, pinta desde caché. Creación de reporte, votos y retiro encolan PendingActionEntity (CREATE_REPORT/VOTE/RETRACT_REPORT) cuando ConnectivityHelper.isOnline()=false; ReportSyncWorker (constraint NetworkType.CONNECTED + backoff) los reenvía. Periodic sync cada 15min desde ReporteCiudadanoApp. Foto en creación offline no soportada. |
| RF-06 | Retirar propio reporte en primeros 5 min si < 3 votos     | ✅      | Backend: ReportController::destroy() valida created_at<5min y votes_confirm+votes_resolve<3 (403 si no). Cliente: btnRetractReport en ReportDetailBottomSheet (solo owner+condiciones), DELETE /reports/{id}, remueve marker via onReportRetracted(). Offline: encola RETRACT_REPORT vía RF-05 |

---

## Módulo 2 — Sistema de votos comunitarios

| ID    | Requerimiento                                              | Estado | Notas                                                                                                                                 |
| ----- | ---------------------------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| RF-07 | Solo usuarios dentro de 500 m pueden votar                 | ✅      | Validación Haversine en cliente (VoteStateManager) + server-side en ReportVoteController::store() línea 34-39 (distanceInMetersTo, 422 si >500m)                                           |
| RF-08 | Votos "Sigue ahí" y "Ya se resolvió"                       | ✅      | ReportDetailBottomSheet con ambos botones                                                                                             |
| RF-09 | Un voto por usuario; puede cambiarlo hasta 5 min después   | ✅     | Server: unique(report_id,user_id) impide votos duplicados; DELETE /reports/{id}/votes/{type} rechaza con 403 tras 5 min (ReportVoteController). Cliente: VoteStateManager (delete+submit) dentro de la ventana de 5 min      |
| RF-10 | Conteo actualizado y sincronizado entre capas             | ✅      | Fetch preventivo al abrir detalle; OnReportDataUpdated sincroniza cache de MapFragment; fin de "amnesia de interacción" |
| RF-11 | Auto-cierre cuando votos "Ya se resolvió" >= 70% con min 3 | ✅      | Report::meetsResolveThreshold(): votes_resolve/total >= RESOLVE_RATIO_THRESHOLD(0.7) con RESOLVE_MIN_TOTAL_VOTES(3); llamado en evaluateAutoStatus() post-voto. Cliente refleja estado RESOLVED. |
| RF-12 | Sello "Verificado" al llegar a 5 votos "Sigue ahí"         | ✅      | Marcador: getStatusStrokeColor() verde #4CAF50 para verified. Detalle: tvStatus con color dinámico (verde=verified, azul=resolved, naranja=pending, gris=archived) en displayReportInfo() |
| RF-13 | Auto-archivo a las 24 h sin interacción                    | ✅      | Comando `reports:archive-stale` (Report::archiveStaleReports, STALE_HOURS=24) programado cada 5 min vía Schedule; verificado end-to-end. Cliente no filtra reportes archivados del mapa (ver RF-18)        |
| RF-14 | Creador puede votar "Ya se resolvió" en su propio reporte  | ✅      | setupOwnerControls() oculta solo btnConfirm (GONE); btnResolve visible y funcional para owner. updateVoteUI() ya no hace early return para owner. Server no bloquea owner. |
| RF-15 | Concurrencia de votos con bloqueo optimista en servidor    | ✅      | DB unique(report_id,user_id,type) previene race conditions; QueryException 23000 → 409 "Ya votaste". DB::transaction() en store/destroy. Cliente no necesita lógica extra (correcto per spec). |

---

## Módulo 3 — Mapa en vivo

| ID    | Requerimiento                                                | Estado | Notas                                                                                             |
| ----- | ------------------------------------------------------------ | ------ | ------------------------------------------------------------------------------------------------- |
| RF-16 | Escala dinámica y Throttling de zoom                      | ✅      | Marcadores escalan suavemente (base 1.06); Throttling de updates (>0.1 zoom) para rendimiento; FPS estables |
| RF-17 | Marcadores integrados M3 (Single Layer)                   | ✅      | Bitmap dinámico con sombra, brillo interno y borde de estado; Halo dorado para "Mi Reporte"; rendimiento GPU optimizado |
| RF-18 | Reportes resueltos en gris durante 2 h antes de desaparecer  | ✅      | Server: `reports:archive-stale` archiva resueltos con `resolved_at` > 2h (RESOLVED_VISIBLE_HOURS) → status=archived, verificado. Cliente: `passesFilters()` en MapFragment excluye status="archived" de `addReportMarker`/`applyFilters`, desaparece del mapa |
| RF-19 | Filtros por categoría, estado y antigüedad (1h/6h/24h)       | ✅      | FAB "Filtros" (fab_filter) abre dialog_map_filters.xml: checkboxes de 7 categorías + RadioGroup estado (pendiente/verificado/resuelto) + RadioGroup antigüedad (1h/6h/24h); `applyFilters()` re-evalúa reportMarkers ya cargados sin re-fetch |
| RF-20 | Tocar marcador → tarjeta con categoría, votos, foto, botones | ✅      | Categoría, votos, foto con Glide + fullscreen tap; storage activo                                 |

---

## Módulo 4 — Alertas de proximidad

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-21 | Notificación push al acercarse a < 300 m de reporte activo | ❌ | FCM no integrado |
| RF-22 | Notificación con botones "Sigue ahí" / "Ya se resolvió" | ❌ | FCM no integrado |
| RF-23 | Usuario configura categorías de alertas y radio (100–500 m) | ❌ | No hay pantalla de configuración |
| RF-24 | Alertas solo si usuario en movimiento (ActivityRecognition API) | ❌ | No implementado |
| RF-25 | No más de 1 alerta del mismo reporte por usuario en 2 horas | ❌ | FCM no integrado |

---

## Módulo 5 — Puntuación y confiabilidad

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-26 | Score de confiabilidad por reportes y votos acertados | ✅ | GET /me devuelve score; UserProfileBottomSheet (pageProfile) lo muestra como "X pts" |
| RF-27 | Puntos: +10 reporte confirmado, +2 voto "Sigue ahí", +5 voto "Ya se resolvió" | ✅ | Implementado en laravel_api (User::addScore + Report::evaluateAutoStatus); cliente refleja vía GET /me al refrescar perfil |
| RF-28 | Reportes de usuarios con score alto = mayor tamaño base en mapa | ✅ | UserInfo (ReportResponse) ahora deserializa score/level; MapFragment.getUserSizeMultiplier() aplica 1.0/1.1/1.2/1.3 según level (Nuevo/Colaborador/Guardián/Experto) en addReportMarker y updateMarkersScale |
| RF-29 | Niveles: Nuevo/Colaborador/Guardián/Experto | ✅ | GET /me devuelve level; UserProfileBottomSheet (pageProfile) lo muestra como badge |
| RF-30 | Expertos verifican reporte con 3 votos en lugar de 5 | ✅ | Implementado server-side en Report::evaluateAutoStatus (laravel_api), sin cambios necesarios en cliente |

---

## Módulo 6 — Perfil e historial

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-31 | Ver todos los reportes propios con votos, sello y estado final | ✅ | UserProfileBottomSheet (pageReports) usa GET /me/reports (server-side); lista categoría, descripción, fecha, estado, conteo de votos (👍/✅) |
| RF-32 | Historial de votos propios con accuracy % por tipo | ✅ | UserProfileBottomSheet (pageVotes), nueva opción "Mis Votos"; usa GET /me/votes; accuracy calculado client-side comparando vote.type vs report.status final (verified/resolved) |
| RF-33 | Estadísticas: reportes creados, confirmaciones recibidas, problemas resueltos | ✅ | UserProfileBottomSheet (pageProfile), fila de stats vía GET /me/reports (loadProfileStats) |

---

## Módulo 7 — Onboarding

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-34 | Onboarding automático en primer registro | ❌ | No implementado |
| RF-35 | Botón "Omitir" en cualquier momento | ❌ | No implementado |
| RF-36 | Onboarding accesible desde ajustes | ❌ | No hay pantalla de ajustes |
| RF-37 | Estado "onboarding completado" en SharedPreferences | ❌ | No implementado |

---

## Resumen de progreso

| Módulo | Total RF | ✅ Completos | 🔶 A medias | ❌ No iniciados |
|--------|----------|-------------|------------|----------------|
| Autenticación (A) | 6 | 4 | 1 | 1 |
| Reporte ultrarrápido (1) | 6 | 6 | 0 | 0 |
| Votos comunitarios (2) | 9 | 9 | 0 | 0 |
| Mapa en vivo (3) | 5 | 5 | 0 | 0 |
| Alertas de proximidad (4) | 5 | 0 | 0 | 5 |
| Puntuación y confiabilidad (5) | 5 | 4 | 0 | 1 |
| Perfil e historial (6) | 3 | 3 | 0 | 0 |
| Onboarding (7) | 4 | 0 | 0 | 4 |
| **TOTAL** | **43** | **35 (81%)** | **0 (0%)** | **8 (19%)** |
