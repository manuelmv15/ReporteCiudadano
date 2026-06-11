# Índice General de Documentación

<!-- El agente agrega entradas aquí: -->
<!-- - [YYYY-MM-DD] [modulo] — descripción -->
- [2026-06-06] [api] — AuthController: register, login, logout, users, Google OAuth (/auth/google), /me — Sanctum + google/apiclient
- [2026-06-06] [login] — Google Sign-In Android: LoginActivity + Retrofit → POST /api/auth/google → SharedPreferences token
- [2026-06-07] [components] — SnackbarHelper MD3: reutilizable con variantes ERROR/SUCCESS/INFO/WARNING, íconos vectoriales, colores centralizados en colors.xml y dimens.xml
- [2026-06-07] [login] — Reemplazo de tv_error por SnackbarHelper en LoginActivity y RegisterActivity
- [2026-06-07] [map] — MapFragment: Google Maps SDK, FAB mi ubicación, runtime permissions, OnMapLongClickListener para captura de coords
- [2026-06-07] [general] — BASE_URL de ApiClient cambiada a https://api.manuelmv.net/ (antes emulador local)
- [2026-06-07] [components] — Configuración de Tema Material 3 y Tipografía (ABeeZee + Noto Sans)
- [2026-06-07] [components] — Habilitación de ViewBinding y refactorización de Activities
- [2026-06-07] [network] — Integración segura de Mapbox Access Token vía local.properties
- [2026-06-08] [map] — Fase 3: RadialMenuDialogFragment — menú circular con 8 categorías (Vialidad, Alumbrado, Agua, Tráfico, Seguridad, Parques, Basura, Otros) al tocar el mapa
- [2026-06-08] [components] — RadialMenuDialogFragment + 8 iconos vectoriales de categorías, layout circular ConstraintLayout 120dp/45°
- [2026-06-08] [map] — FusedLocationProviderClient: carga ubicación actual del usuario, fallback a Buenos Aires, FAB sincronizado
- [2026-06-08] [db] — Report model: id, category, latitude, longitude, description, timestamp para almacenar reportes
- [2026-06-08] [map] — CircleAnnotations: indicador azul para usuario, reportes con color por categoría + estado (Mapbox AnnotationManager)
- [2026-06-08] [api] — ReportResponse model + GET /reports endpoint integrado, carga 100 reportes pending/verified
- [2026-06-08] [map] — Fix: colores hex para CircleAnnotations (#2196F3 user, #FF6B6B vialidad, etc.), Lint warnings resueltos, BottomSheetDialogFragment implementado
- [2026-06-08] [api] — Mapeo de categorías: sincronizados IDs reales de API (bache=1, alumbrado-publico=2, basura-acumulada=3, fuga-de-agua=4, semaforo-danado=5, inseguridad=6)
- [2026-06-08] [api] — CreateReportResponse model: API retorna {success, message, report}; ApiService.createReport() ahora parsea respuesta envuelta correctamente
- [2026-06-08] [api] — ReportResponse fix: estructura real es {success, reports{data, current_page, ...}}; añadida clase PaginationData para parsear correctamente
- [2026-06-08] [map] — Logging mejorado: onViewCreated sequence, AnnotationManager init, reportes cargados con detalles por reporte
- [2026-06-08] [api] — VoteRequest + VoteResponse models; ApiService endpoints: POST /reports/{id}/votes, DELETE /reports/{id}/votes/{type}, GET /reports/{id}
- [2026-06-08] [api] — ReportDetailResponse model para envuelve reportes individuales
- [2026-06-08] [components] — VoteState + VoteStateManager: state management para votación con LiveData, Haversine distance calc, 500m validation, 5-min edit window
- [2026-06-08] [components] — bottom_sheet_report_detail.xml: botones "Sigue ahí" / "Ya se resolvió", tvVoteCount, llDistanceWarning, pbVoteLoading
- [2026-06-08] [components] — ReportDetailBottomSheet: integración VoteStateManager, setupVoteObservers, updateVoteUI, cambio de voto con dialog
- [2026-06-08] [map] — MapFragment: implements OnReportStatusChangeListener, actualiza markers en tiempo real (color/tamaño) cuando estado cambia (PENDING/VERIFIED/RESOLVED)
- [2026-06-08] [components] — UserProfileBottomSheet + bottom_sheet_user_profile.xml: menú usuario con opciones Mi Perfil, Mis Reportes, Configuración, Cerrar Sesión
- [2026-06-08] [map] — FAB fab_profile en esquina superior derecha para abrir UserProfileBottomSheet, handleLogout() vuelve a LoginActivity
- [2026-06-09] [api] — POST /reports/{id}/votes: API retorna data con {type, user_id, report_id, votes_confirm, votes_resolve, created_at} ✅
- [2026-06-09] [api] — GET /reports/{id}: API retorna report completo con {votes, user_vote, user_voted_at} para detectar voto previo ✅
- [2026-06-09] [components] — ReportDetailBottomSheet: FIX initialize() llamado explícitamente, error 409 usa Snackbar, detecta voto duplicado
- [2026-06-09] [components] — VoteStateManager: agregado método refreshVoteState() para refrescar estado sin votar, initialize() ahora public
- [2026-06-09] [map] — MapFragment: pagination optimizada (25 reportes iniciales, máximo 200 en caché), flag isLoadingReports para evitar duplicados
- [2026-06-09] [general] — CRITERIOS_VOTOS.md: documento con todos los criterios de cambio de estado (PENDING/VERIFIED/RESOLVED) con ejemplos
- [2026-06-09] [api] — PENDIENTE: GET /reports/{id} debe retornar user_vote del usuario autenticado (fix en Laravel requerido)



- [2026-06-09] [map] — fab_add_report wired a showRadialMenu(); creado drawable add_24px; fix srcCompat vacío en fragment_map.xml
- [2026-06-09] [features] — docu/features/features.md creado: tracking de los 43 RF del planteamiento v1 (6 ✅ completos, 13 🔶 a medias, 24 ❌ sin iniciar)
- [2026-06-09] [components] — ReportDetailBottomSheet: detecta si reporte es del usuario actual, muestra botón "Editar descripción" solo al autor, permite editar y guardar vía PATCH /reports/{id}; LoginActivity/RegisterActivity: guardan user_id, user_name, user_email en prefs al login
- [2026-06-09] [components] — ReportDetailBottomSheet: owner no puede votar (botones ocultos, solo muestra resumen); owner puede agregar/reemplazar foto via galería; foto actual se muestra con Glide si existe
- [2026-06-09] [components] — ReportDetailBottomSheet: dos botones directos (Tomar foto / Galería) en lugar de dialog; cámara con FileProvider + permiso runtime CAMERA; endpoint corregido PATCH→PUT /reports/{id} (verificado en /api/docs); fix foto/desc no persiste entre aperturas via report.setPhoto()/setDescription()
- [2026-06-09] [db/components] — Fix crítico: API devuelve `photo_path` no `photo`; ReportData ahora usa @SerializedName("photo_path"); añadido getPhotoUrl() que construye URL completa con base https://api.manuelmv.net/storage/; PENDIENTE backend: storage:link da 403
- [2026-06-09] [components] — ReportDetailBottomSheet: tap en foto abre fullscreen Dialog negro; ivReportPhoto clickable con ripple feedback
- [2026-06-09] [general] — Backend: php artisan storage:link ejecutado; fotos ahora accesibles en https://api.manuelmv.net/storage/; RF-04 y RF-20 marcados ✅
- [2026-06-09] [map/login] — Mapa como pantalla inicial; FABs condicionales: fab_add_report oculto sin auth, fab_profile navega a LoginActivity sin auth
- [2026-06-09] [components] — ReportDetailBottomSheet: modo invitado completo; isGuest() + setupGuestMode() ocultan votos y muestran btnLoginToVote → LoginActivity
- [2026-06-09] [map] — MapFragment: handleExpiredSession() limpia token y actualiza FABs si API devuelve 401 al crear reporte
- [2026-06-09] [components] — ReportDetailBottomSheet: clearTokenAndGoToLogin() en votos/descripción/foto si API devuelve 401
- [2026-06-09] [features] — RF-A04 ✅ completo: backend confirmado auth:sanctum en POST/PUT/DELETE/PATCH; GET público
- [2026-06-09] [login] — RF-A02 ✅: TokenManager singleton con EncryptedSharedPreferences (AES256-GCM); migrados LoginActivity, RegisterActivity, UserProfileBottomSheet, MapFragment, ReportDetailBottomSheet, VoteStateManager
- [2026-06-10] [components] — fab_profile: nuevas activities ProfileActivity (Mi Perfil), MyReportsActivity (Mis Reportes, lista filtrada client-side), SettingsActivity (tema claro/oscuro/sistema + notificaciones); SettingsManager + ReporteCiudadanoApp aplican tema persistido
- [2026-06-10] [components] — fab_profile: bottom sheet "Mi Cuenta" ahora multi-página (Mi Perfil/Mis Reportes/Configuración cambian dentro del mismo sheet, sin abrir activities); fix bug Mis Reportes vacío (status filter inválido)
- [2026-06-10] [components] — Mi Perfil: quitado "ID de usuario"; agregado editar nombre y foto de perfil (cámara/galería), persistido local (TokenManager + filesDir/avatar.jpg) — pendiente endpoint backend para persistir server-side
- [2026-06-10] [api] — documentados endpoints pendientes Mi Perfil: PUT /me (nombre), POST /me/avatar, GET /me +score/level, GET /me/reports, GET /me/votes
- [2026-06-10] [api/components] — Mi Perfil conectado a backend: PUT /me, POST /me/avatar, GET /me, GET /me/reports todos [x] lista; UI muestra score/level (RF-26/RF-29); compilación OK
- [2026-06-11] [components] — Fix avatar no visible: backend tenía APP_URL=http://localhost (bug), corregido a https://api.manuelmv.net + config:clear; verificado avatar_url ya correcto en /reports y /me
- [2026-06-11] [components] — Fix #2 avatar: intento disallowHardwareConfig()+PREFER_ARGB_8888 insuficiente; persiste círculo verde sólido
- [2026-06-11] [components] — Fix #3 avatar: reemplazado Glide.circleCrop() por ShapeableImageView (estilo CircleImageView, cornerSize 50%) en ivUserAvatar/ivProfileAvatar
- [2026-06-11] [components] — Fix #4 avatar: ivUserAvatar centerInside dejaba ver fondo verde (background no se clipea); cambiado a centerCrop
- [2026-06-11] [components] — Fix #5 (causa raíz) avatar: android:tint/app:tint en ImageView teñía CUALQUIER drawable (incl. foto de Glide) de verde sólido; quitado tint de ivUserAvatar/ivProfileAvatar — RESUELTO
- [2026-06-11] [components] — Mi Perfil: RF-31 (conteo votos en Mis Reportes), RF-32 (página Mis Votos con accuracy %), RF-33 (estadísticas: reportes/confirmaciones/resueltos) implementados
