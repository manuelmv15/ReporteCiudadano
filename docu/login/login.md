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
- [ ] Cambiar `BASE_URL` en `ApiClient.java` al URL real del servidor (hoy apunta a emulador local `10.0.2.2:8000`)
- [ ] Implementar logout: borrar token de SharedPreferences + `googleSignInClient.signOut()` + volver a LoginActivity
- [ ] Agregar manejo de SHA-1 en Google Cloud Console para builds de release
- [ ] Mostrar nombre/foto del usuario en MainActivity después del login
