## [2026-06-07] Cambio de URL base de la API a producción

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/network/ApiClient.java` — BASE_URL cambiado de `http://10.0.2.2:8080/api/` (emulador local) a `https://api.manuelmv.net/api/`

### Notas
- Primer cambio sin sufijo `/api/` causó 404: rutas Sanctum/Laravel viven bajo prefijo `/api/`. Corregido manteniendo `/api/` en BASE_URL.

### TODOs / Próximos pasos
- [ ] Probar login, registro y demás llamadas de red contra `https://api.manuelmv.net/api/`
