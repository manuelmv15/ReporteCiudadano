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



