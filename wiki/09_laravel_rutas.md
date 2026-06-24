# 09 — Laravel API: Todas las Rutas

Prefijo base: `/api/` (configurado en `bootstrap/app.php`)

## Rutas Públicas (sin autenticación)

| Método | Ruta | Controller@método | Descripción |
|--------|------|-------------------|-------------|
| GET | `/api/docs` | `DocsController@index` | Auto-documentación de endpoints |
| POST | `/api/register` | `AuthController@register` | Registro nuevo usuario |
| POST | `/api/login` | `AuthController@login` | Login con email/contraseña |
| POST | `/api/auth/google` | `AuthController@googleLogin` | Login/registro con Google ID token |
| POST | `/api/forgot-password` | `PasswordResetController@sendResetLink` | Envía email con código 6 dígitos |
| POST | `/api/reset-password` | `PasswordResetController@resetPassword` | Cambia contraseña con el código |
| GET | `/api/categories` | `CategoryController@index` | Lista categorías |
| GET | `/api/categories/{id}` | `CategoryController@show` | Detalle de categoría |
| GET | `/api/reports` | `ReportController@index` | Lista reportes (paginada + filtros) |
| GET | `/api/reports/heatmap` | `ReportController@heatmap` | Puntos para heatmap |
| GET | `/api/reports/{id}` | `ReportController@show` | Detalle de reporte |
| GET | `/api/reports/stream/changes` | `ReportStreamController@changes` | Cambios desde timestamp |

**Nota:** Las rutas de `/reports` públicas intentan autenticar opcionalmente si hay Bearer token. Si hay token válido, devuelven `user_vote` del usuario autenticado.

## Rutas Protegidas (`auth:sanctum`)

### Auth / Perfil
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/logout` | Invalida el token actual |
| GET | `/api/me` | Datos del usuario autenticado |
| PUT | `/api/me` | Actualiza nombre |
| POST | `/api/me/avatar` | Sube/reemplaza foto de perfil (multipart) |
| POST | `/api/me/fcm-token` | Actualiza token FCM |
| GET | `/api/me/reports` | Mis reportes (paginado, filtro status) |
| GET | `/api/me/votes` | Mi historial de votos (paginado) |
| GET | `/api/users` | Todos los usuarios (id, name, email, avatar_url, score, level) |

### Categorías (gestión admin)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/categories` | Crear categoría |
| PUT | `/api/categories/{id}` | Editar categoría |
| DELETE | `/api/categories/{id}` | Eliminar categoría |

### Reportes
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/reports` | Crear reporte |
| PUT | `/api/reports/{id}` | Editar reporte (solo owner) |
| DELETE | `/api/reports/{id}` | Retirar reporte (solo owner, restricciones) |
| PATCH | `/api/reports/{id}/status` | Cambiar estado (pending/verified/resolved/archived) |

### Votos
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/reports/{id}/votes` | Votar (requiere estar a ≤500m) |
| DELETE | `/api/reports/{id}/votes/{type}` | Retirar voto (solo en ventana de 5 min) |

## Parámetros importantes

### GET `/api/reports`
| Query param | Tipo | Descripción |
|-------------|------|-------------|
| `status` | string | Filtrar: pending/verified/resolved/archived |
| `category_id` | int | Filtrar por categoría |
| `updated_after` | datetime | Solo reportes actualizados después de esta fecha |
| `lat_min`, `lat_max`, `lng_min`, `lng_max` | float | Bounding box geográfico |
| `per_page` | int | Items por página (default 15) |

### GET `/api/reports/stream/changes`
| Query param | Tipo | Descripción |
|-------------|------|-------------|
| `since` | datetime ISO-8601 | Cambios desde esta fecha (default: hace 5 min) |
| `limit` | int | Máximo de reportes (default 50) |

### POST `/api/reports/{id}/votes`
Body JSON:
```json
{
    "type": "confirm",       // o "resolve"
    "latitude": 13.6929,     // ubicación actual del usuario
    "longitude": -89.2182
}
```

### POST `/api/reset-password`
Body JSON:
```json
{
    "email": "user@email.com",
    "token": "123456",            // código 6 dígitos
    "password": "newpass",
    "password_confirmation": "newpass"
}
```
