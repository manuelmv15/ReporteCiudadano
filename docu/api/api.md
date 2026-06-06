# API Documentation

## [2026-06-06] AuthController — rutas de autenticación

### Archivos tocados
- `src/app/Http/Controllers/API/AuthController.php` — implementado register, login, getAllUser, logout
- `src/routes/api.php` — creado con rutas auth públicas y protegidas
- `src/bootstrap/app.php` — registrado api.php en withRouting
- `src/app/Models/User.php` — agregado HasApiTokens trait

### Dependencias
- `laravel/sanctum ^4.3` — instalado vía composer, migration publicada y ejecutada

---

### Ruta: POST /api/register

| Campo     | Detalle                                                    |
|-----------|------------------------------------------------------------|
| Método    | POST                                                       |
| Auth      | No                                                         |
| Body      | `{ name: string, email: string, password: string, password_confirmation: string }` |
| Respuesta | `{ success: bool, message: string, token: string, user: object }` |
| Estado    | [x] lista                                                  |

---

### Ruta: POST /api/login

| Campo     | Detalle                                                    |
|-----------|------------------------------------------------------------|
| Método    | POST                                                       |
| Auth      | No                                                         |
| Body      | `{ email: string, password: string }`                      |
| Respuesta | `{ token: string (Bearer ...), user: object }`             |
| Estado    | [x] lista                                                  |

---

### Ruta: POST /api/logout

| Campo     | Detalle                          |
|-----------|----------------------------------|
| Método    | POST                             |
| Auth      | Bearer Token (auth:sanctum)      |
| Body      | N/A                              |
| Respuesta | `{ success: bool, message: string }` |
| Estado    | [x] lista                        |

---

### Ruta: GET /api/users

| Campo     | Detalle                                           |
|-----------|---------------------------------------------------|
| Método    | GET                                               |
| Auth      | Bearer Token (auth:sanctum)                       |
| Body      | N/A                                               |
| Respuesta | `{ success: bool, users: [{ id, name, email, created_at }] }` |
| Estado    | [x] lista                                         |

---

## [2026-06-06] Google Auth + endpoint /me

### Archivos tocados
- `src/app/Http/Controllers/API/AuthController.php` — agregado googleLogin, me
- `src/routes/api.php` — agregado POST /auth/google, GET /me
- `src/config/services.php` — agregado google.client_id
- `src/.env` / `.env.example` — agregado GOOGLE_CLIENT_ID=

### Dependencias
- `google/apiclient ^2.19` — instalado vía composer

---

### Ruta: POST /api/auth/google

| Campo     | Detalle                                                              |
|-----------|----------------------------------------------------------------------|
| Método    | POST                                                                 |
| Auth      | No                                                                   |
| Body      | `{ id_token: string }` — JWT de Google del cliente móvil            |
| Respuesta | `{ success: bool, token: string (Bearer ...), user: object }`        |
| Estado    | [x] lista                                                            |
| Notas     | Verifica token con Google API. Crea user si no existe, o actualiza google_id/avatar si ya tiene cuenta con ese email |

---

### Ruta: GET /api/me

| Campo     | Detalle                                      |
|-----------|----------------------------------------------|
| Método    | GET                                          |
| Auth      | Bearer Token (auth:sanctum)                  |
| Body      | N/A                                          |
| Respuesta | `{ success: bool, user: object }`            |
| Estado    | [x] lista                                    |

---

### TODOs / Próximos pasos
- [ ] Poner GOOGLE_CLIENT_ID real en .env (obtener desde Google Cloud Console)
- [ ] Agregar rate limiting a /login y /auth/google
- [ ] Proteger /users con policy (solo admin)
