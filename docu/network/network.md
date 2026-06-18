# Network Documentation

## [2026-06-07] Configuración de URL de producción

### Archivos tocados
- `app/src/main/res/values/strings.xml` — actualizado `api_base_url` a `https://api.manuelmv.net/api/`.
- `app/src/main/java/com/bombayashi/reporteciudadano/network/ApiClient.java` — verificado `BASE_URL`.

### Resumen
Se ajustó la configuración para apuntar al backend de producción. Se incluyó `HttpLoggingInterceptor` para facilitar la depuración de peticiones en desarrollo.

### TODOs / Próximos pasos
- [ ] Implementar reintentos en caso de falla de red.
- [ ] Agregar manejo de timeouts personalizados.

---

## [2026-06-07] Integración de Mapbox Access Token

### Archivos tocados
- `local.properties` — Se añadió `MAPBOX_ACCESS_TOKEN` (ignorado por git).
- `app/build.gradle` — Configurado para leer el token y generar `R.string.mapbox_access_token`.

### Resumen
Se implementó un sistema seguro para gestionar el token de Mapbox sin exponerlo en el repositorio. El token se inyecta en tiempo de compilación como un recurso de cadena.


## [2026-06-15] Endpoints password recovery en ApiService

### Archivos tocados
- `network/ApiService.java` — agregados `forgotPassword(@Body ForgotPasswordRequest)` y `resetPassword(@Body ResetPasswordRequest)`
- `model/ForgotPasswordRequest.java` — nuevo modelo `{ email }`
- `model/ResetPasswordRequest.java` — nuevo modelo `{ email, token, password, password_confirmation }`


---

## [2026-06-18] ApiService — getReportsByBounds()

### Archivos tocados
- `network/ApiService.java` — nuevo método `getReportsByBounds()` con 4 query params de bounding box

### Firma
```java
@GET("reports")
Call<ReportResponse> getReportsByBounds(
    @Query("lat_min") double latMin,
    @Query("lat_max") double latMax,
    @Query("lng_min") double lngMin,
    @Query("lng_max") double lngMax,
    @Query("per_page") int perPage
);
```

Misma URL que `getReports()`, pero con filtro geográfico. La API retorna solo reportes dentro del bounding box.
