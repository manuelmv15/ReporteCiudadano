# 03 — Android: Capa de Red

## ApiClient.java

Singleton Retrofit. Solo se inicializa una vez en `ReporteCiudadanoApp`.

```java
BASE_URL = "https://api.manuelmv.net/api/"
```

**Inicialización:**
```java
ApiClient.init(context)   // llamado en Application.onCreate()
ApiClient.getInstance()   // retorna ApiService ya construido
ApiClient.setOnUnauthorized(callback)  // callback para cuando el server devuelve 401
```

**OkHttpClient** tiene dos interceptores:
1. `AuthInterceptor` — agrega `Authorization: Bearer <token>` y captura 401
2. `HttpLoggingInterceptor` — loguea body completo en logcat

**Gson** como converter factory. Sin ningún adaptador personalizado.

---

## AuthInterceptor.java

Interceptor OkHttp que:
1. Lee el token desde `TokenManager`
2. Si hay token, agrega header: `Authorization: Bearer <token>`
3. Ejecuta la request
4. Si la respuesta es 401: llama `onUnauthorized` callback (registrado desde `MapFragment`)

El callback en `MapFragment` hace:
```java
ApiClient.setOnUnauthorized(() -> requireActivity().runOnUiThread(this::handleExpiredSession));
```

`handleExpiredSession()` muestra snackbar y redirige a `LoginActivity` después de 1.5s.
**No borra el token automáticamente** (podría ser error temporal del servidor).

---

## ApiService.java — Todos los endpoints

### Auth
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `auth/google` | Login con Google ID token |
| POST | `login` | Login email/password |
| POST | `register` | Registro nuevo usuario |
| POST | `forgot-password` | Solicitar reset de contraseña |
| POST | `reset-password` | Confirmar reset con token de 6 dígitos |
| POST | `logout` | Invalida token actual |
| GET | `me` | Datos del usuario autenticado |
| PUT | `me` | Actualizar nombre de perfil |
| POST | `me/avatar` | Subir foto de perfil (multipart) |
| GET | `me/reports` | Mis reportes (paginado, filtro status) |
| GET | `me/votes` | Mis votos (paginado) |
| POST | `me/fcm-token` | Actualizar token FCM (query param) |

### Reportes
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `reports` | Lista paginada (filtros: status, per_page) |
| GET | `reports` | Por bounding box (lat_min/max, lng_min/max) |
| GET | `reports/heatmap` | Puntos lat/lng para heatmap (máx 2000) |
| GET | `reports/stream/changes` | Cambios desde `since` (ISO-8601) |
| GET | `reports/{id}` | Detalle de reporte |
| POST | `reports` | Crear reporte |
| PUT | `reports/{id}` | Editar reporte (multipart: descripción + foto) |
| DELETE | `reports/{id}` | Retirar reporte |

### Votos
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `reports/{id}/votes` | Votar (confirm o resolve) |
| DELETE | `reports/{id}/votes/{type}` | Retirar voto |

---

## TokenManager.java

Wrapper de `SharedPreferences` (prefs name: `"auth_prefs"`).

```java
TokenManager.getInstance(context).getToken()        // String (vacío si no hay sesión)
TokenManager.getInstance(context).isLoggedIn()      // boolean
TokenManager.getInstance(context).getUserId()       // int (-1 si no hay sesión)
TokenManager.getInstance(context).saveToken(token, userId)
TokenManager.getInstance(context).clearToken()
```

El `user_id` se extrae de la respuesta de login y se guarda junto al token.
Se usa para identificar si un marcador en el mapa es propio del usuario (para mostrar botón editar/eliminar).

---

## ConnectivityHelper.java

```java
ConnectivityHelper.isOnline(context)   // true si hay red activa
```

Usa `NetworkCapabilities.NET_CAPABILITY_INTERNET`.
Se llama antes de cada llamada de red en `MapFragment` para decidir si ir a caché o a la API.
