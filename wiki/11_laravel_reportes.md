# 11 — Laravel: Reportes

## ReportController.php

### `index(Request $request)` — GET /reports

**Filtros disponibles (query params):**

```php
->when($request->filled('status'),       fn($q) => $q->where('status', $request->string('status')))
->when($request->filled('category_id'),  fn($q) => $q->where('category_id', $request->integer('category_id')))
->when($request->filled('updated_after'),fn($q) => $q->where('updated_at', '>', $request->date('updated_after')))
->when(lat_min + lat_max + lng_min + lng_max,  // bounding box
    fn($q) => $q->whereBetween('latitude', ...)->whereBetween('longitude', ...))
->latest()
->paginate(per_page, default 15)
```

**Eager loading:** `user:id,name,avatar_url,score,level` + `category`

**Autenticación opcional:** Intenta autenticar con Bearer token:
```php
$user = $request->user() ?? $this->authenticateFromToken($request);
```
Si hay usuario autenticado → añade `user_vote` a cada reporte (el voto del usuario para ese reporte).

**`authenticateFromToken()`:** Busca en `PersonalAccessToken::findToken($bearerToken)` → retorna el usuario dueño del token. Ruta pública pero con enriquecimiento opcional si hay token.

---

### `show(Request $request, Report $report)` — GET /reports/{id}

Carga relaciones. Intenta autenticar. Si hay usuario → busca su voto en `ReportVote`:
```php
$vote = ReportVote::where('report_id', $report->id)
    ->where('user_id', $user->id)
    ->first();
```

Respuesta incluye:
- `votes`: `{confirm: int, resolve: int}`
- `user_vote`: `"confirm"` | `"resolve"` | null
- `user_voted_at`: ISO-8601 | null
- Todos los campos del reporte

---

### `store(Request $request)` — POST /reports

**Validación:**
- `category_id`: required, existe en tabla `categories`
- `latitude`: required, numeric, between:-90,90
- `longitude`: required, numeric, between:-180,180
- `description`: required, string, max:500
- `photo`: nullable, image, max:5120 (5MB)

**Proceso:**
1. Si hay foto → `$request->file('photo')->store('reports', 'public')` → retorna path relativo
2. `Report::create()` con `status = 'pending'` por defecto
3. `ReportCreated::dispatch($report)` → evento de broadcasting (canal `reports`, evento `report.created`)
4. `NotificationService->notifyNearbyUsers($report)` → FCM push a todos los usuarios con token

Retorna 201 con el reporte y relaciones.

---

### `update(Request $request, Report $report)` — PUT /reports/{id}

**Autorización:** `authorizeOwner()` — verifica `report->user_id === $request->user()->id`, sino 403.

**Campos editables:** `category_id` (opcional), `description` (opcional), `photo` (opcional)

Si hay nueva foto → borra la anterior (`Storage::disk('public')->delete($report->photo_path)`), guarda la nueva.

---

### `destroy(Request $request, Report $report)` — DELETE /reports/{id}

**Autorización:** Solo owner.

**Restricciones:**
1. `$report->created_at->lt(now()->subMinutes(5))` → 403 "Solo podés retirar en los primeros 5 minutos"
2. `($report->votes_confirm + $report->votes_resolve) >= 3` → 403 "No se puede retirar con 3+ votos"

Si pasa → borra foto del storage → `$report->delete()`

---

### `updateStatus(Request $request, Report $report)` — PATCH /reports/{id}/status

**Validación:** `status`: required, in:pending,verified,resolved,archived

**Proceso:**
1. Guarda `$previousStatus`
2. Actualiza `status`, `status_changed_at = now()`
3. Actualiza timestamp específico:
   - `verified` → `verified_at = now()`
   - `resolved` → `resolved_at = now()`
   - `archived` → `archived_at = now()`
4. `ReportStatusChanged::dispatch($report, $previousStatus)` → evento de broadcasting
5. `NotificationService->notifyVoters($report, $previousStatus)` → FCM a quienes votaron

---

### `heatmap()` — GET /reports/heatmap

```php
Report::whereNotIn('status', ['archived', 'resolved'])
    ->select('latitude', 'longitude')
    ->latest()
    ->limit(2000)
    ->get()
```

Solo activos (pending/verified). Máximo 2000 puntos. Sin auth requerida.

---

## ReportStreamController.php — GET /reports/stream/changes

Endpoint de polling para el app Android.

```php
$since = $request->has('since') ? $request->date('since') : now()->subMinutes(5);
```

Busca reportes donde:
```php
->where('created_at', '>=', $since)
->orWhere('updated_at', '>=', $since)
```

Retorna:
```json
{
    "success": true,
    "timestamp": "2026-06-23T10:00:00Z",
    "count": 5,
    "reports": [...]
}
```

El `timestamp` de la respuesta se usa como próximo `since` en el siguiente poll.

Carga eager: `category`, `user:id,name,avatar_url`.

Sin autenticación requerida. Máximo `limit` reportes (default 50).

---

## Autorización de owner

```php
private function authorizeOwner(Request $request, Report $report): void
{
    if ($report->user_id !== $request->user()->id) {
        abort(response()->json([...], 403));
    }
}
```

Usa `abort()` con response JSON en vez de `abort(403)` para mantener el content-type JSON.
