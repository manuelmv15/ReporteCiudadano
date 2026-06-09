# Login Documentation

## [2026-06-06] Google Sign-In en Android — integración con API Laravel

### Archivos tocados
- `app/src/main/java/.../LoginActivity.java` — actividad principal de login, Google Sign-In → POST /api/auth/google → guarda token en SharedPreferences → navega a MainActivity
- `app/src/main/java/.../model/GoogleLoginRequest.java` — POJO request con campo `id_token`
- `app/src/main/java/.../model/AuthResponse.java` — POJO response con `success`, `token`, `user`
- `app/src/main/java/.../network/ApiService.java` — interface Retrofit con `POST auth/google`
- `app/src/main/java/.../network/ApiClient.java` — singleton Retrofit, BASE_URL = `http://10.0.2.2:8000/api/`
- `app/src/main/res/layout/activity_login.xml` — layout con SignInButton, ProgressBar, TextView error
- `app/src/main/res/values/strings.xml` — agregado `google_web_client_id`, `api_base_url`
- `app/src/main/AndroidManifest.xml` — LoginActivity como launcher, INTERNET permission, MainActivity sin exported
- `gradle/libs.versions.toml` — deps: `play-services-auth:21.3.0`, `retrofit:2.11.0`, `okhttp logging:4.12.0`, `gson:2.11.0`
- `app/build.gradle` — implementadas nuevas deps
- `app/src/main/java/.../MainActivity.java` — eliminado comment con client ID expuesto

### Flujo implementado
1. `LoginActivity` arranca → chequea SharedPreferences por token existente
2. Si token existe → va directo a `MainActivity` (auto-login)
3. Usuario toca botón Google → `GoogleSignInClient` lanza selector de cuentas
4. Google devuelve `idToken` → POST a `/api/auth/google` con Retrofit
5. Laravel verifica token → devuelve `{ success, token, user }`
6. Token guardado en SharedPreferences (`auth_prefs`, key `token`)
7. Navega a `MainActivity`, `finish()` para sacar LoginActivity del backstack

### TODOs / Próximos pasos
- [x] Cambiar `BASE_URL` a `10.0.2.2:8080`
- [x] Implementar logout
- [ ] Agregar manejo de SHA-1 en Google Cloud Console para builds de release
- [ ] Mostrar nombre/foto del usuario en MainActivity después del login

---

## [2026-06-06] Login email/password + Register + Logout

### Archivos tocados
- `LoginActivity.java` — agregado login email/password + enlace a RegisterActivity, Google Sign-In mantenido
- `RegisterActivity.java` — nuevo, register con validación local (campos vacíos, contraseñas coinciden, mín 8 chars) → POST /api/register → guarda token → MainActivity
- `model/LoginRequest.java` — nuevo POJO `{ email, password }`
- `model/RegisterRequest.java` — nuevo POJO `{ name, email, password, password_confirmation }`
- `network/ApiService.java` — agregado `POST login`, `POST register`, `POST logout`
- `activity_login.xml` — rediseñado: campos email/password + botón login + link register + botón Google
- `activity_register.xml` — nuevo layout con 4 campos + botón register + link volver login
- `activity_main.xml` — agregado `btn_logout` y `tv_welcome`
- `MainActivity.java` — agregado logout: POST /api/logout → clear SharedPreferences → GoogleSignIn.signOut() → LoginActivity
- `AndroidManifest.xml` — registrado RegisterActivity

### Flujo login email/password
1. LoginActivity → email + password → validación no vacío
2. POST /api/login → token → SharedPreferences → MainActivity

### Flujo register
1. RegisterActivity → validación local → POST /api/register
2. Si éxito → guarda token → MainActivity con FLAG_CLEAR_TASK

### Flujo logout
1. MainActivity → btn_logout → POST /api/logout con Bearer token
2. Éxito o falla → clear prefs + GoogleSignIn.signOut() → LoginActivity con FLAG_CLEAR_TASK

### TODOs / Próximos pasos
- [ ] SHA-1 para builds de release en Google Cloud Console
- [ ] Mostrar nombre/avatar del usuario en MainActivity
- [ ] Manejar respuesta 422 de Laravel (errores de validación) con mensajes específicos

---

## [2026-06-07] Reemplazo de tv_error por SnackbarHelper

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/LoginActivity.java` — eliminado `TextView tvError`; `showError()` ahora delega a `SnackbarHelper.show()` con `Variant.ERROR`
- `app/src/main/java/com/bombayashi/reporteciudadano/RegisterActivity.java` — mismo cambio
- `app/src/main/res/layout/activity_login.xml` — eliminado `TextView tv_error` inline
- `app/src/main/res/layout/activity_register.xml` — eliminado `TextView tv_error` inline

### Motivación
El `tv_error` era un `TextView` estático sin animación, hardcodeado en color `#D32F2F`. Fue reemplazado por `SnackbarHelper` (MD3) que aparece con animación, ícono contextual y color semántico.

### TODOs / Próximos pasos
- [ ] Mostrar `SnackbarHelper.show(... Variant.SUCCESS)` tras login/registro exitoso antes de navegar
- [ ] Evaluar agregar outline rojo en `TextInputLayout` además del Snackbar para errores de validación

## [2026-06-09] RF-A02 — Migración a EncryptedSharedPreferences

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/util/TokenManager.java` — nuevo singleton; wraps EncryptedSharedPreferences con MasterKey AES256-GCM; expone saveAuth(), getToken(), getUserId(), getUserName(), getUserEmail(), isLoggedIn(), clearAuth(); fallback a SharedPreferences normal si crypto falla
- `gradle/libs.versions.toml` — agregada versión `securityCrypto = "1.1.0-alpha06"` y lib `security-crypto`
- `app/build.gradle` — agregada dependencia `libs.security.crypto`
- `LoginActivity.java` — saveAuthAndGoMain() usa TokenManager; import SharedPreferences eliminado
- `RegisterActivity.java` — onResponse() usa TokenManager; import SharedPreferences eliminado
- `UserProfileBottomSheet.java` — displayUserInfo() y handleLogout() usan TokenManager
- `MapFragment.java` — createReport, updateFabVisibility, handleExpiredSession, fabProfile usan TokenManager
- `ReportDetailBottomSheet.java` — isGuest(), setupOwnerControls(), saveDescription(), uploadPhoto(), clearTokenAndGoToLogin() usan TokenManager
- `VoteStateManager.java` — constructor y submitVote() usan TokenManager

### TODOs / Próximos pasos
- [x] RF-A02 completo
- [ ] Validar que token persiste entre reinicios de app en dispositivo físico
