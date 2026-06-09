# Documentación — Componentes

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
