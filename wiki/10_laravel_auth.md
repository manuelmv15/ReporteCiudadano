# 10 — Laravel: Autenticación

## AuthController.php

### `register(Request $request)`

**Validación:**
- `name`: required, string
- `email`: required, email, unique:users
- `password`: required, min:6, confirmed (necesita `password_confirmation`)

**Proceso:**
1. `DB::beginTransaction()`
2. `User::create()` con password hasheado con `Hash::make()`
3. `$user->createToken('api-token')->plainTextToken` (Sanctum)
4. `DB::commit()`
5. Retorna 201 con `{success, message, token, user}`

**Errores:**
- 422 → ValidationException con errores por campo
- 500 → rollback + mensaje genérico (debug: muestra excepción real)

---

### `login(Request $request)`

**Validación:**
- `email`: required, email
- `password`: required

**Proceso:**
1. `User::where('email')->first()`
2. `Hash::check($request->password, $user->password)`
3. Si falla alguno → 401 "Credenciales inválidas"
4. Si OK → `createToken('api-token')->plainTextToken`
5. Retorna 200 con `{success, token, user}`

Sin transacción DB (solo lectura + creación de token).

---

### `googleLogin(Request $request)`

**Validación:** `id_token`: required, string

**Proceso:**
1. Instancia `Google\Client` con `client_id` de `config('services.google.client_id')`
2. `$client->verifyIdToken($request->id_token)` → retorna payload del token
3. Si inválido → 401
4. `DB::beginTransaction()`
5. Busca usuario por `google_id` O `email` (para vincular cuentas existentes)
6. Si existe → actualiza `google_id`, `avatar_url` (si no tenía), `email_verified_at`
7. Si no existe → crea usuario con:
   - `name`, `email`, `google_id` del payload de Google
   - `avatar_url` de `payload['picture']`
   - `password` random de 32 chars (no importa, no se usa)
   - `email_verified_at = now()` (Google ya verificó el email)
8. `createToken()` → plainTextToken
9. `DB::commit()`
10. Retorna 200 con `{success, token, user}`

---

### `logout(Request $request)`

Solo borra el token actual:
```php
$request->user()->currentAccessToken()->delete();
```
Retorna 200 con mensaje de confirmación.

---

### `me(Request $request)`

Retorna el usuario autenticado:
```php
return response()->json(['success' => true, 'user' => $request->user()]);
```

---

### `getAllUser()`

Retorna todos los usuarios con campos seleccionados:
```php
User::select('id', 'name', 'email', 'avatar_url', 'score', 'level', 'created_at')->get()
```

No tiene paginación ni auth check adicional más allá del `auth:sanctum` del grupo de rutas.

---

## PasswordResetController.php

### `sendResetLink(Request $request)`

**Anti-enumeración:** Si el email no existe en DB, devuelve el mismo mensaje de éxito (evita que un atacante descubra qué emails están registrados).

**Proceso (si el email existe):**
1. Genera token de 6 dígitos: `str_pad(random_int(0, 999999), 6, '0', STR_PAD_LEFT)`
2. Guarda en tabla `password_reset_tokens` con `Hash::make($token)` (solo el hash)
3. Envía email via **Brevo API** (no SMTP Laravel estándar):
   ```php
   Http::withHeaders(['api-key' => env('BREVO_API_KEY')])
       ->post('https://api.brevo.com/v3/smtp/email', [...])
   ```
4. Template de email: `resources/views/emails/password-reset.blade.php`

---

### `resetPassword(Request $request)`

**Validación:**
- `email`: required, email
- `token`: required, string (el código de 6 dígitos)
- `password`: required, min:6, confirmed

**Proceso:**
1. Busca en `password_reset_tokens` por email
2. Si no existe → 422 "Token inválido o expirado"
3. Verifica expiración: `now()->diffInMinutes($record->created_at) > 60` → 422 "Token expirado" + borra el registro
4. `Hash::check($request->token, $record->token)` → 422 "Token inválido" si no coincide
5. `User::where('email')->update(['password' => Hash::make($request->password)])`
6. `DB::table('password_reset_tokens')->where('email')->delete()` (limpia el token usado)
7. Retorna 200 "Contraseña actualizada correctamente"

---

## ProfileController.php

### `update(Request $request)`
Solo permite actualizar el `name`. Retorna el usuario fresqueado.

### `uploadAvatar(Request $request)`
- Valida: `avatar` image, max:5120 (5MB)
- Si había avatar anterior y era local (no URL de Google) → `Storage::disk('public')->delete($oldPath)`
  - `avatarPathFromUrl()` extrae la ruta relativa; si la URL no tiene el prefijo del storage local → retorna null (no borra URLs de Google)
- Guarda en `storage/app/public/avatars/{random}.ext`
- Actualiza `avatar_url` del usuario con la URL pública

### `reports(Request $request)`
Reportes del usuario autenticado. Filtro opcional por `status`. Paginado.

### `votes(Request $request)`
Votos del usuario con datos del reporte asociado. Carga eager de categoría. Paginado.

### `updateFcmToken(Request $request)`
Actualiza `fcm_token` en tabla `users`. Sin lógica adicional.
