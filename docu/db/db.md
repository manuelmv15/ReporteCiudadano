## [2026-06-09] Fix campo photo en ReportData

### Archivos tocados
- `app/src/main/java/.../model/ReportResponse.java` — `@SerializedName("photo")` → `@SerializedName("photo_path")`; añadido `getPhotoUrl()` construye `https://api.manuelmv.net/storage/` + `photo_path`; añadidos setters `setPhoto()` y `setDescription()`

### TODOs / Próximos pasos
- [!] Backend retorna 403 en `https://api.manuelmv.net/storage/reports/xxx.jpg` — necesita `php artisan storage:link` en el servidor
