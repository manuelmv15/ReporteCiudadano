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
