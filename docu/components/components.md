# Documentación — Componentes

## [2026-06-13] RF-28 — Tamaño de marcador según score/level del autor

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/model/ReportResponse.java` — `UserInfo` ahora deserializa `score` y `level` (ya venían del backend en `/reports` y `/reports/{id}` pero el modelo Android los ignoraba).
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MapFragment.java` — nuevo método `getUserSizeMultiplier(ReportData)`: `"experto"`=1.3, `"guardian"`=1.2, `"colaborador"`=1.1, `"nuevo"`=1.0 (strings en minúsculas/sin acento, igual a `User::LEVEL_*` de laravel_api). `addReportMarker()` aplica el multiplicador al `withIconSize()` inicial; `updateMarkersScale()` lo combina con el `scaleFactor` por zoom (antes aplicaba el mismo tamaño a todos los marcadores).

### Notas
- Multiplicador hardcodeado en cliente según `level` string que devuelve la API. Verificado contra `User.php`: `LEVEL_NUEVO='nuevo'`, `LEVEL_COLABORADOR='colaborador'`, `LEVEL_GUARDIAN='guardian'`, `LEVEL_EXPERTO='experto'`.
- Data de prueba creada en prod (api.manuelmv.net) pa verificar visualmente: 9 usuarios `rf28*_<ts>@test.local` (5 voters + 4 owners) y ~43 reportes cerca de San Francisco Gotera, Morazán, El Salvador (lat≈13.70-13.73, lon≈-88.10 a -88.99... revisar coords reales en owners, ver script /tmp/rf28_seed.sh). Owners verificados: colaborador score=20, guardian score=100, experto score=300, nuevo score=0.

### TODOs / Próximos pasos
- [ ] Verificar visualmente en mapa (zona San Carlos, Morazán, lat≈13.6975 lon≈-88.0656) reportes id=54(nuevo)/55(colaborador)/56(guardian)/57(experto) — deben verse 1.0/1.1/1.2/1.3x de tamaño respectivamente.
- [ ] Considerar limpieza de los ~47 reportes/9 usuarios de prueba en prod una vez verificado (no hay endpoint de borrado masivo, requiere acceso directo a DB).

## [2026-06-13] RF-18/RF-19 — Ocultar archivados y filtros de mapa (categoría/estado/antigüedad)

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MapFragment.java` — RF-18: `addReportMarker` ahora llama a `passesFilters()` antes de pintar; reportes `status="archived"` nunca se muestran (se siguen guardando en `reportMarkers` para mantener el dato, pero sin marcador visual). Nuevos métodos: `removeMarkerVisuals()` (extrae limpieza de icon+stroke), `passesFilters()`, `normalizeCategoryGroup()`, `reportAgeHours()`, `applyFilters()` (re-evalúa todos los reportes conocidos sin re-fetch), `showFilterDialog()` (MaterialAlertDialogBuilder con checkboxes de categoría + RadioGroup de estado + RadioGroup de antigüedad 1h/6h/24h). Nuevos campos: `filterCategories` (Set), `filterStatus`, `filterAge`. FAB `fab_filter` en `setupFAB()` abre el diálogo.
- `app/src/main/res/layout/fragment_map.xml` — agregado botón `fab_filter` en el floating toolbar.
- `app/src/main/res/layout/dialog_map_filters.xml` — nuevo layout: 7 checkboxes de categoría (vialidad/alumbrado/agua/trafico/seguridad/basura/otros), RadioGroup estado (todos/pendiente/verificado/resuelto), RadioGroup antigüedad (cualquiera/1h/6h/24h).
- `app/src/main/res/drawable/filter_alt_24px.xml` — nuevo ícono vector 24dp.

### Notas
- RF-18: el servidor ya archiva reportes resueltos >2h vía `reports:archive-stale`; este cambio hace que el cliente los excluya del mapa (antes se pintaban en gris igual que "resolved").
- RF-19: filtros viven en memoria del fragment (no persisten entre sesiones); aplican sobre `reportMarkers` ya cargado, sin nuevas llamadas a la API.

### TODOs / Próximos pasos
- [ ] Persistir filtros seleccionados en SharedPreferences si se quiere mantener entre sesiones.
- [ ] Confirmar formato exacto de `created_at` del backend (asumido ISO-8601 UTC sin offset) para `reportAgeHours()`.

## [2026-06-07] SnackbarHelper — componente reutilizable MD3

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/SnackbarHelper.java` — nuevo helper estático con 4 variantes
- `app/src/main/res/drawable/ic_snackbar_error.xml` — ícono vector 20dp
- `app/src/main/res/drawable/ic_snackbar_success.xml` — ícono vector 20dp
- `app/src/main/res/drawable/ic_snackbar_info.xml` — ícono vector 20dp
- `app/src/main/res/drawable/ic_snackbar_warning.xml` — ícono vector 20dp
- `app/src/main/res/values/colors.xml` — colores semánticos MD3 para cada variante
- `app/src/main/res/values/dimens.xml` — archivo nuevo con escala de espaciado centralizada

### API pública

```java
// Básico (LENGTH_LONG)
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.ERROR);
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.SUCCESS);
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.INFO);
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.WARNING);

// Corto
SnackbarHelper.showShort(view, "mensaje", SnackbarHelper.Variant.SUCCESS);

// Con acción (LENGTH_INDEFINITE)
SnackbarHelper.showWithAction(view, "mensaje", Variant.INFO, "Reintentar", v -> { ... });
```

### Colores por variante

| Variante | Background  | Text/Icon  |
|----------|-------------|------------|
| ERROR    | `#B3261E`   | `#FFFFFF`  |
| SUCCESS  | `#1E8E3E`   | `#FFFFFF`  |
| INFO     | `#0B57D0`   | `#FFFFFF`  |
| WARNING  | `#E37400`   | `#FFFFFF`  |

### Anchor view recomendado
Siempre pasar `findViewById(android.R.id.content)` como anchor para que el Snackbar aparezca en la parte inferior de la activity.

### TODOs / Próximos pasos
- [ ] Agregar soporte para mostrar Snackbar de SUCCESS al completar registro/login exitoso
- [ ] Considerar variante `NEUTRAL` usando `colorInverseSurface` del tema MD3 para mensajes genéricos

---

## [2026-06-07] Configuración de Tema Material 3 y Tipografía Personalizada

### Archivos tocados
- `app/src/main/res/values/themes.xml` — Renombrado tema a `Theme.ReporteCiudadano` (sync con Manifest) y vinculación con tipografía.
- `app/src/main/res/values-night/themes.xml` — Igual que el anterior para modo oscuro.
- `app/src/main/res/values/typography.xml` — Nuevo, define estilos `TextAppearance` mapeados a las nuevas fuentes.
- `app/src/main/res/font/abeezee_regular.ttf` — Renombrado para compatibilidad Android.
- `app/src/main/res/font/notosanslinearb_regular.ttf` — Renombrado para compatibilidad Android.

### Resumen
Se aplicó el sistema de colores Material 3 generado y se configuró la tipografía global siguiendo el diseño solicitado:
- **ABeeZee**: Aplicada a todos los estilos de `Display`, `Headline` y `Title`.
- **Noto Sans Linear B**: Aplicada a todos los estilos de `Body` y `Label`.

### TODOs
- [ ] Verificar consistencia visual en dispositivos con diferentes densidades.

---

## [2026-06-07] Habilitación de ViewBinding

### Archivos tocados
- `app/build.gradle` — Habilitado `buildFeatures { viewBinding true }`.
- `LoginActivity.java` — Refactorizado para usar `ActivityLoginBinding`.
- `RegisterActivity.java` — Refactorizado para usar `ActivityRegisterBinding`.
- `MainActivity.java` — Refactorizado para usar `ActivityMainBinding`.

### Resumen
Se habilitó **ViewBinding** en todo el proyecto para eliminar el uso de `findViewById`, mejorando la seguridad de tipos y reduciendo el código repetitivo en las Activities.

### TODOs
- [ ] Migrar Fragments si se añaden en el futuro.

---

## [2026-06-08] RadialMenuDialogFragment — Menú Circular de Categorías

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/RadialMenuDialogFragment.java` — DialogFragment con listener callback + ViewBinding
- `app/src/main/res/layout/dialog_radial_menu.xml` — ConstraintLayout circular (8 FAB mini en 120dp radio, 45° spacing)
- `app/src/main/res/drawable/ic_category_*.xml` × 8 — vectores MD3 para categorías (Vialidad, Alumbrado, Agua, Tráfico, Seguridad, Parques, Basura, Otros)

### Características
- **Fondo transparente** — diálogo translúcido sobre el mapa
- **Layout circular** — usando `layout_constraintCircle` + `layout_constraintCircleAngle`
- **8 botones** — FloatingActionButton mini distribuidos cada 45° alrededor del centro
- **Callback pattern** — listener `OnCategorySelectedListener` pasado al crear la instancia
- **Cierre automático** — el diálogo se cierra al tocar cualquier categoría

### API pública

```java
RadialMenuDialogFragment dialog = RadialMenuDialogFragment.newInstance(
    location,
    (category, latitude, longitude) -> {
        // Lógica del reporte
    }
);
dialog.show(getChildFragmentManager(), "radial_menu");
```

### TODOs / Próximos pasos
- [ ] Fase 4: Conectar callback con endpoint API de reportes
- [ ] Fase 4: Agregar animación de entrada/salida (scale + fade)
- [ ] Fase 4: Soporte para descripción textual del reporte antes de enviar



## [2026-06-09] Detección de autor y edición de descripción en ReportDetailBottomSheet

### Archivos tocados
- `app/src/main/java/.../ui/ReportDetailBottomSheet.java` — añadido `setupOwnerControls()`: lee `user_id` de prefs, compara con `report.getUserId()`, muestra `btnEditDescription` solo al autor. `showEditDescriptionDialog()` abre AlertDialog con EditText. `saveDescription()` llama PATCH /reports/{id}
- `app/src/main/res/layout/bottom_sheet_report_detail.xml` — añadido `btnEditDescription` (TextButton, visibility=gone por defecto)
- `app/src/main/java/.../network/ApiService.java` — añadido `@PATCH("reports/{id}") updateReport()`
- `app/src/main/java/.../model/UpdateReportRequest.java` — nuevo modelo con campo `description`
- `app/src/main/java/.../LoginActivity.java` — `saveTokenAndGoMain` → `saveAuthAndGoMain(AuthResponse)`: guarda también `user_id`, `user_name`, `user_email`; añadidas constantes `KEY_USER_ID`, `KEY_USER_NAME`, `KEY_USER_EMAIL`
- `app/src/main/java/.../RegisterActivity.java` — igual: guarda id/name/email al registrar
- `app/src/main/res/drawable/ic_edit.xml` — nuevo ícono lápiz

### TODOs / Próximos pasos
- [x] Verificar backend: PATCH no existe → corregido a PUT /reports/{id} (verificado en /api/docs)
- [x] Actualizar `report` en memoria tras guardar → implementado via `report.setDescription()` / `report.setPhoto()`

## [2026-06-09] Cámara, galería y fix persistencia de foto en ReportDetailBottomSheet

### Archivos tocados
- `app/src/main/res/layout/bottom_sheet_report_detail.xml` — `btnAddPhoto` reemplazado por `llPhotoButtons` con `btnTakePhoto` ("Tomar foto") + `btnPickPhoto` ("Galería") lado a lado
- `app/src/main/res/drawable/ic_add_photo.xml` — ícono cámara
- `app/src/main/res/drawable/ic_gallery.xml` — ícono galería
- `app/src/main/AndroidManifest.xml` — `uses-permission CAMERA`, `uses-feature camera required=false`, FileProvider declarado con `${applicationId}.fileprovider`
- `app/src/main/res/xml/file_provider_paths.xml` — nuevo, expone `cache-path` para fotos temporales de cámara
- `app/src/main/java/.../ui/ReportDetailBottomSheet.java` — `galleryLauncher` (GetContent), `cameraLauncher` (TakePicture con FileProvider URI), `cameraPermissionLauncher` (RequestPermission); `launchCamera()` crea tmpFile en cacheDir + obtiene URI via FileProvider; upload foto ahora via `PUT /reports/{id}` multipart; success callback llama `report.setPhoto(url)` para persistir en memoria
- `app/src/main/java/.../model/ReportResponse.java` — añadidos `setPhoto(String)` y `setDescription(String)` a `ReportData`
- `app/src/main/java/.../network/ApiService.java` — `PATCH /reports/{id}` → `@Multipart @PUT("reports/{id}")` con `@Part("description") RequestBody` + `@Part MultipartBody.Part photo`; eliminado endpoint inexistente `POST /reports/{id}/photo`; eliminado import `UpdateReportRequest`

### TODOs / Próximos pasos
- [ ] Verificar que `PUT /reports/{id}` acepte `photo = null` (solo actualizar descripción sin borrar foto existente)
- [ ] Comprimir imagen antes de subir si supera 5MB (límite del backend)

## [2026-06-09] Foto fullscreen al tap en ReportDetailBottomSheet

### Archivos tocados
- `app/src/main/java/.../ui/ReportDetailBottomSheet.java` — `showFullscreenPhoto(url)`: abre Dialog fullscreen negro con ImageView; tap cierra. Listener set al cargar foto existente y tras upload exitoso
- `app/src/main/res/layout/bottom_sheet_report_detail.xml` — `ivReportPhoto` ahora clickable + focusable + ripple foreground

### TODOs / Próximos pasos
- [ ] Agregar gesto de pinch-to-zoom en la vista fullscreen

## [2026-06-09] ReportDetailBottomSheet — modo invitado: bloquear votación sin auth

### Archivos tocados
- `app/src/main/java/.../ui/ReportDetailBottomSheet.java` — agregado `isGuest()` (chequea token en SharedPreferences); `setupGuestMode()` oculta `llVoteButtons`, `llDistanceWarning`, `tvUserVoteStatus`, `pbVoteLoading` y muestra `btnLoginToVote`; `onViewCreated` salta `initializeVoteManager`/`setupVoteObservers`/`setupButtonListeners` si es invitado; click en `btnLoginToVote` hace dismiss + navega a LoginActivity
- `app/src/main/res/layout/bottom_sheet_report_detail.xml` — agregado `btnLoginToVote` (MaterialButton OutlinedButton, visibility=gone) antes de `llDistanceWarning`

### TODOs / Próximos pasos
- [ ] Agregar ícono de persona/login en `btnLoginToVote` en lugar de ic_snackbar_info
- [ ] Verificar que tras login exitoso el usuario pueda votar sin reabrir el bottom sheet (actualmente necesita reabrir)

## [2026-06-09] ReportDetailBottomSheet — manejo de 401 por sesión expirada

### Archivos tocados
- `app/src/main/java/.../ui/ReportDetailBottomSheet.java` — agregado `clearTokenAndGoToLogin()`: limpia token, hace dismiss y navega a LoginActivity; en ERROR event de votos: si contiene "401" llama `clearTokenAndGoToLogin()`; en `saveDescription()` y `uploadPhoto()` callbacks: `else if (response.code() == 401)` → `clearTokenAndGoToLogin()`

### TODOs / Próximos pasos
- [ ] Considerar mostrar Snackbar antes del dismiss en `clearTokenAndGoToLogin()` para que el usuario sepa por qué fue redirigido

## [2026-06-10] fab_profile — Mi Perfil, Mis Reportes y Configuración

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/ProfileActivity.java` — nueva activity, muestra nombre/email/id desde TokenManager
- `app/src/main/res/layout/activity_profile.xml` — layout perfil
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MyReportsActivity.java` — nueva activity, lista reportes propios
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MyReportsAdapter.java` — RecyclerView adapter, filtra GET /reports por user_id == TokenManager.getUserId()
- `app/src/main/res/layout/activity_my_reports.xml` / `item_my_report.xml` — layouts lista
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/SettingsActivity.java` — nueva activity, tema (sistema/claro/oscuro) + switch notificaciones + versión app
- `app/src/main/res/layout/activity_settings.xml` — layout configuración
- `app/src/main/java/com/bombayashi/reporteciudadano/util/SettingsManager.java` — SharedPreferences para tema y notificaciones, aplica AppCompatDelegate.setDefaultNightMode
- `app/src/main/java/com/bombayashi/reporteciudadano/ReporteCiudadanoApp.java` — Application class, aplica tema guardado en onCreate
- `app/src/main/AndroidManifest.xml` — registra ReporteCiudadanoApp + 3 activities nuevas
- `app/src/main/res/drawable/ic_arrow_back.xml` — ícono back nuevo
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java` — los 3 ítems del menú ("Mi Perfil", "Mis Reportes", "Configuración") ahora navegan a sus activities en vez de Toast "Próximamente"

### Notas
- "Mis Reportes" no tiene endpoint dedicado en API; se filtra client-side sobre GET /reports?status=pending,verified,resolved,archived&per_page=100
- Tap en item de "Mis Reportes" no abre detalle (solo lista informativa)

### TODOs / Próximos pasos
- [ ] MyReportsActivity: tap en item abre ReportDetailBottomSheet o detalle similar
- [ ] MyReportsActivity: mostrar conteo de votos (confirm/resolve) por reporte
- [ ] ProfileActivity: avatar real (foto de usuario) si backend lo soporta
- [ ] Backend: considerar endpoint GET /reports?user_id= para evitar filtrado client-side con per_page=100

## [2026-06-10] fab_profile — rediseño: bottom sheet multi-página (reemplaza activities)

### Archivos tocados
- `app/src/main/res/layout/bottom_sheet_user_profile.xml` — root cambia a FrameLayout con 4 páginas (pageMenu, pageProfile, pageReports, pageSettings) intercambiadas por visibilidad; mantiene estructura visual "Mi Cuenta" original
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java` — reescrito: showPage()/showMenu() para navegar sin cerrar el sheet; cada página con su propio botón volver (back arrow); Mis Reportes carga lazy (solo primera vez que se abre)
- Eliminados: `ProfileActivity`, `MyReportsActivity`, `SettingsActivity` (activities standalone, reemplazadas por páginas del bottom sheet) + sus layouts y entradas en AndroidManifest.xml
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MyReportsAdapter.java` — sin cambios, reusado dentro del bottom sheet

### Fix bug "Mis Reportes no lista nada"
- Causa: `getReports("pending,verified,resolved,archived", 100)` — API no soporta status como lista separada por comas (solo "" o un solo status), devolvía vacío
- Fix: `getReports("", 100)` igual que MapFragment, luego filtro client-side por `report.getUserId() == TokenManager.getUserId()`

### TODOs / Próximos pasos
- [ ] Mis Reportes: tap en item abre ReportDetailBottomSheet
- [ ] Mis Reportes: mostrar conteo de votos (confirm/resolve)
- [ ] Backend: endpoint GET /reports?user_id= para evitar per_page=100 + filtro client-side

## [2026-06-10] Mi Perfil — editar nombre + foto de perfil (local)

### Archivos tocados
- `app/src/main/res/layout/bottom_sheet_user_profile.xml` — pageProfile: quitado bloque "ID de usuario"; avatar clickable con badge ic_edit (btnEditAvatar); nombre con botón lápiz (btnEditName)
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java` — agregado: showAvatarPickerDialog() (Tomar foto / Galería, reusa patrón cámara+FileProvider de ReportDetailBottomSheet), saveAvatar() copia imagen a filesDir/avatar.jpg, showEditNameDialog() edita nombre mostrado
- `app/src/main/java/com/bombayashi/reporteciudadano/util/TokenManager.java` — agregado getAvatarPath()/setAvatarPath()/setUserName()

### Notas
- Edición de nombre y foto es SOLO LOCAL (SharedPreferences + filesDir) — no hay endpoint backend para persistir perfil/avatar; al volver a loguearse se sobreescribe el nombre con el del servidor
- Avatar se carga con Glide.circleCrop() + signature(timestamp) para forzar refresh tras cambiar foto

### TODOs / Próximos pasos
- [x] Backend: endpoint PUT/PATCH /api/me para persistir name + avatar server-side
- [x] Una vez exista endpoint, subir avatar.jpg al guardar y dejar de sobreescribir nombre local en login

## [2026-06-10] Mi Perfil — conectado a backend (PUT /me, POST /me/avatar, GET /me, GET /me/reports)

### Archivos tocados
- `app/src/main/res/layout/bottom_sheet_user_profile.xml` — pageProfile: agregado tvProfileLevel (badge) + tvProfileScore debajo de tvProfileEmail (RF-26/RF-29)
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java`:
  - `loadAvatar()` ahora prioriza `TokenManager.getAvatarUrl()` (remoto) sobre `getAvatarPath()` (local)
  - `setupProfilePage()` muestra score/level cacheados y llama `refreshProfile()` (GET /me) para refrescar nombre/avatar/score/level
  - `showEditNameDialog()` → `updateProfileName()` llama PUT /me (`updateProfile()`), actualiza UI/TokenManager solo si éxito
  - `saveAvatar()` ahora sube el archivo con `uploadAvatar()` (POST /me/avatar multipart), guarda `avatar_url` remoto devuelto en TokenManager
  - `loadMyReports()` reemplazado: usa `getMyReports(token, "", 100)` en vez de `getReports("", 100)` + filtro client-side
- `app/src/main/java/com/bombayashi/reporteciudadano/util/TokenManager.java` — agregado getAvatarUrl()/setAvatarUrl(), getScore()/getLevel()/setScoreAndLevel()
- `app/src/main/java/com/bombayashi/reporteciudadano/model/AuthResponse.java` — User: agregado avatarUrl, score, level
- `app/src/main/java/com/bombayashi/reporteciudadano/model/UpdateProfileRequest.java` (nuevo) — body `{ name }` para PUT /me
- `app/src/main/java/com/bombayashi/reporteciudadano/model/AvatarUploadResponse.java` (nuevo) — respuesta `{ success, message, avatar_url }`
- `app/src/main/java/com/bombayashi/reporteciudadano/network/ApiService.java` — agregado getMe(), updateProfile(), uploadAvatar(), getMyReports()

### Notas
- Compilación verificada con `./gradlew :app:compileDebugJavaWithJavac -q` sin errores
- GET /me/votes (RF-32) sigue pendiente, no requerido para esta pasada

### TODOs / Próximos pasos
- [ ] Implementar GET /me/votes + modelo + UI para historial de votos (RF-32)
- [ ] Mis Reportes: tap en item abre ReportDetailBottomSheet (pendiente de pasada anterior)

## [2026-06-11] Fix: avatar no se mostraba (avatar_url con host "localhost")

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java`:
  - `loadAvatar()`: ignora `avatarUrl` vacío, agrega `.placeholder()/.error()` con drawable actual para no dejar el ImageView en blanco si falla la carga

### Notas
- Causa raíz: backend Laravel con `APP_URL=http://localhost` en `.env` — `avatar_url` devuelto por `/me` y `/reports` apuntaba a `http://localhost/storage/...` (no resuelve desde el dispositivo)
- Corregido en backend: `.env` → `APP_URL=https://api.manuelmv.net`, `php artisan config:clear` + `docker restart api_app`
- Verificado vía `GET /api/reports`: `user.avatar_url` ahora devuelve `https://api.manuelmv.net/storage/avatars/...` correctamente
- Workaround client-side `fixAvatarUrl()` se agregó y luego se quitó (innecesario tras fix de backend); se conserva solo `.placeholder()/.error()` como safety net

### TODOs / Próximos pasos
- [x] Backend: corregir APP_URL en .env de producción a `https://api.manuelmv.net`

## [2026-06-11] Fix #2: avatar cargaba bien pero se renderizaba como círculo sólido

### Archivos tocados (intento #1, insuficiente)
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java` — `loadAvatar()`: agregado `.format(DecodeFormat.PREFER_ARGB_8888)` + `.disallowHardwareConfig()` antes de `.circleCrop()`

### Notas
- Logcat confirmó "Glide load OK" (descarga e decode exitosos, pixeles del JPEG correctos vía verificación manual) pero UI mostraba círculo verde sólido
- Reinstalando la app el problema persistió igual con ARGB_8888 forzado → `circleCrop()` no era (solo) el problema

## [2026-06-11] Fix #3: reemplazado Glide.circleCrop() por ShapeableImageView

### Archivos tocados
- `app/src/main/res/values/themes.xml` — agregado estilo `CircleImageView` (`cornerSize=50%`)
- `app/src/main/res/layout/bottom_sheet_user_profile.xml` — `ivUserAvatar` e `ivProfileAvatar` cambiados de `ImageView` a `com.google.android.material.imageview.ShapeableImageView` con `app:shapeAppearanceOverlay="@style/CircleImageView"`
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java` — quitado `.circleCrop()`/`.disallowHardwareConfig()`/`.format()` de todos los `Glide.load()` de avatar (remoto y local); el recorte circular ahora lo hace el ShapeableImageView vía clipping de vista, no transformación de bitmap

### Notas
- Lo que se veía como "círculo verde sólido" era el ícono placeholder `ic_my_location` (que ya es un ícono circular/punto), indicando que el recurso de Glide nunca reemplazaba el placeholder al usar `circleCrop()` + `.placeholder()/.error()` con Drawable de un ImageView normal
- ShapeableImageView evita por completo transformaciones de bitmap para el recorte circular — Glide solo decodifica y hace `.into()` normal

### Fix #4 — fondo verde visible alrededor de la foto (ivUserAvatar)
- `ivUserAvatar` tenía `scaleType="centerInside"` + `background=colorPrimaryContainer`; ShapeableImageView solo clipea el `src`, no el `background`, dejando ver el fondo verde donde la foto no llena el círculo
- Cambiado a `scaleType="centerCrop"` (igual que `ivProfileAvatar`) para que la foto llene todo el círculo

## [2026-06-11] Fix #5 (causa raíz real): android:tint tiñendo la foto entera de verde sólido

### Archivos tocados
- `app/src/main/res/layout/bottom_sheet_user_profile.xml` — quitado `android:tint="?attr/colorOnPrimaryContainer"` de `ivUserAvatar` y `app:tint="?attr/colorOnPrimaryContainer"` de `ivProfileAvatar`

### Notas
- Diagnóstico vía logcat: Glide descargaba y decodificaba la foto correctamente ("Glide OK ... resource=BitmapDrawable@..."), `avatar_url` coincidía con la BD (usuario 12, 1qyoSpx1Y...jpg)
- Causa real: `android:tint`/`app:tint` en un ImageView aplica un colorFilter a CUALQUIER drawable que se le asigne (placeholder, error, o foto cargada por Glide). Con tintMode default (SRC_IN), una foto opaca se vuelve un círculo/cuadrado sólido del color del tint — exactamente lo visto en todos los intentos anteriores
- Los `tint` originales solo tenían sentido para el ícono placeholder `ic_my_location`; al quitarlos, el placeholder se ve con sus colores propios (aceptable) y la foto real se muestra sin teñir
- Fixes #2/#3/#4 (disallowHardwareConfig, ShapeableImageView, centerCrop) no eran necesarios para el bug pero se mantienen como mejoras válidas (ShapeableImageView simplifica el recorte circular vs Glide.circleCrop)

## [2026-06-11] Mi Perfil: conteo de votos, historial de votos con precisión, estadísticas (RF-31/32/33)

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/model/ReportResponse.java` — agregado `votes_confirm`/`votes_resolve` a `ReportData` (fallback cuando no viene el objeto `votes` anidado), getters `getVotesConfirm()`/`getVotesResolve()`
- `app/src/main/java/com/bombayashi/reporteciudadano/model/MyVotesResponse.java` — nuevo modelo para `GET /me/votes` (paginado), incluye `VoteData.isCorrect()`: compara `type` del voto contra `report.status` final (confirm correcto si verified/resolved, resolve correcto si resolved; null si reporte aún pending)
- `app/src/main/java/com/bombayashi/reporteciudadano/network/ApiService.java` — agregado `GET me/votes` (`getMyVotes`)
- `app/src/main/res/layout/item_my_report.xml` — agregado `tvVotes` (👍/✅ conteos)
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MyReportsAdapter.java` — bind de `tvVotes` con `votes_confirm`/`votes_resolve`
- `app/src/main/res/layout/item_my_vote.xml` — nuevo item para historial de votos (tipo, descripción reporte, fecha, badge Acertado/Errado/Pendiente)
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/MyVotesAdapter.java` — nuevo adapter
- `app/src/main/res/layout/bottom_sheet_user_profile.xml` — nueva opción de menú "Mis Votos" (`llMyVotes`), nueva página `pageVotes` (RecyclerView + `tvVotesAccuracySummary`); `pageProfile` ahora incluye fila de estadísticas (`tvStatReports`/`tvStatConfirmations`/`tvStatResolved`)
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/UserProfileBottomSheet.java` — `setupVotesPage()`/`loadMyVotes()`/`buildAccuracySummary()` (RF-32); `loadProfileStats()` agrega `GET /me/reports` para calcular reportes creados, suma de `votes_confirm` (confirmaciones recibidas) y conteo `status=resolved` (RF-33)

### TODOs / Próximos pasos
- [ ] RF-27 (puntos automáticos): ya implementado en API (laravel_api), pendiente verificar en cliente que `score`/`level` se refrescan tras votar (refreshProfile ya se llama solo al abrir Mi Perfil)
- [ ] RF-13 auto-archivo 24h: pendiente comando programado en laravel_api

## [2026-06-15] Módulo 2 — Votos comunitarios completado (RF-07/11/12/14/15)

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/ReportDetailBottomSheet.java` — RF-12: `displayReportInfo()` aplica color a `tvStatus` según estado (verde=verified, azul=resolved, naranja=pending, gris=archived). RF-14: `setupOwnerControls()` ya no oculta `llVoteButtons` completo — solo oculta `btnConfirm` (GONE); owner puede votar "Ya se resolvió". `updateVoteUI()` eliminado early return `if (isOwner) return` para que owner reciba UI de votación funcional.
- `docu/features/features.md` — RF-07/11/12/14/15 actualizados a ✅; resumen módulo 2: 9/9; total: 35/43 (81%)

### TODOs / Próximos pasos
- [ ] Verificar en dispositivo que owner ve solo btnResolve (btnConfirm GONE) y puede votar
- [ ] Verificar que tvStatus muestra color correcto en los 4 estados (pending/verified/resolved/archived)
- [ ] RF-07 server: probar con usuario fuera de 500m que recibe 422 desde API
