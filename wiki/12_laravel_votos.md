# 12 — Laravel: Sistema de Votos

## ReportVoteController.php

### Constantes

```php
MAX_DISTANCE_METERS = 500        // distancia máxima para votar
VOTE_EDIT_WINDOW_MINUTES = 5     // ventana para retirar/cambiar voto
```

---

### `store(Request $request, Report $report)` — POST /reports/{id}/votes

**Validación:**
- `type`: required, in:confirm,resolve
- `latitude`: required, numeric, between:-90,90
- `longitude`: required, numeric, between:-180,180

**Validación geoespacial:**
```php
$distance = $report->distanceInMetersTo($data['latitude'], $data['longitude']);
if ($distance > 500) {
    return 422 ["Debes estar a menos de 500m", "distance_meters": X]
}
```

**Proceso (dentro de DB transaction):**
1. `ReportVote::create(['report_id', 'user_id', 'type'])`
2. `$report->increment($column)` (votes_confirm o votes_resolve)
3. `$report->refresh()`
4. `$report->evaluateAutoStatus()` ← lógica de transición automática

**Control de concurrencia:**
- El UNIQUE index en DB `(report_id, user_id)` previene votos duplicados por el mismo usuario
- Si hay race condition → `QueryException` con código `23000` → 409 "Ya votaste este reporte con ese tipo"

**Respuesta exitosa:**
```json
{
    "success": true,
    "data": {
        "type": "confirm",
        "user_id": 1,
        "report_id": 42,
        "votes_confirm": 3,
        "votes_resolve": 0,
        "status": "pending",
        "created_at": "2026-06-23T10:00:00Z"
    }
}
```

---

### `destroy(Request $request, Report $report, string $type)` — DELETE /reports/{id}/votes/{type}

**Validaciones:**
1. Busca voto del usuario con ese tipo: `ReportVote::where(['report_id', 'user_id', 'type'])`
2. Si no existe → 404
3. Ventana de tiempo: `$vote->created_at->lt(now()->subMinutes(5))` → 403 "Solo puedes cambiar tu voto durante los primeros 5 minutos"

**Proceso (DB transaction):**
1. `$vote->delete()`
2. `$report->decrement($column)` (votes_confirm o votes_resolve)

Retorna el reporte fresqueado.

---

## Lógica de transiciones automáticas (Report::evaluateAutoStatus)

Llamada después de cada nuevo voto. Implementada en el modelo `Report`.

### Diagrama de estados

```
pending ──[confirm threshold]──► verified
    │                                │
    └──[resolve threshold]──► resolved ──[2h]──► archived
                                     ▲
pending/verified ──[resolve threshold]──┘

pending/verified ──[24h sin interacción]──► archived
```

### `meetsConfirmThreshold()` — ¿Pasar a `verified`?

Dos caminos:
```php
// Camino normal: 5+ votos de confirmación
if ($this->votes_confirm >= 5) return true;

// Camino rápido: 3+ votos Y al menos un EXPERTO confirmó
if ($this->votes_confirm >= 3 && $this->hasExpertConfirmVoter()) return true;
```

`hasExpertConfirmVoter()`:
```php
$this->votes()->where('type', 'confirm')
    ->whereHas('user', fn($q) => $q->where('level', 'experto'))
    ->exists()
```

### `meetsResolveThreshold()` — ¿Pasar a `resolved`?

```php
$total = $this->votes_confirm + $this->votes_resolve;
if ($total < 3) return false;                              // mínimo 3 votos en total
return ($this->votes_resolve / $total) >= 0.7;            // 70%+ son de resolución
```

### `evaluateAutoStatus()` — Lógica completa

```php
if (ya resolved o archived) return;  // estado terminal, no evaluar

$confirmVotersAwarded = ($this->verified_at !== null);  // ya recibieron puntos antes

if (status == 'pending' && meetsConfirmThreshold()) {
    transitionTo('verified')
    $owner->addScore(10)                               // RF-27: dueño gana 10 puntos
    awardVoters('confirm', 2)                          // RF-27: votantes confirm ganan 2
    $confirmVotersAwarded = true
}

if ((status == 'pending' || 'verified') && meetsResolveThreshold()) {
    transitionTo('resolved')
    awardVoters('resolve', 5)                          // RF-27: votantes resolve ganan 5
    if (!$confirmVotersAwarded) {
        awardVoters('confirm', 2)                      // evita doble pago si ya pasó por verified
    }
}
```

### `awardVoters(type, points)`

```php
$userIds = $this->votes()->where('type', $type)->pluck('user_id');
User::whereIn('id', $userIds)->get()->each(
    fn(User $u) => $u->addScore($points)
);
```

---

## Archivado automático (ArchiveStaleReports command)

Artisan command: `reports:archive-stale`

Scheduler lo ejecuta periódicamente (frecuencia configurada en `routes/console.php` o `AppServiceProvider`).

```php
// Reportes resueltos hace más de 2h (RF-18)
Report::where('status', 'resolved')
    ->where('resolved_at', '<=', now()->subHours(2))
    ->each(fn($r) => $r->archive())

// Reportes sin interacción en 24h (RF-13)
Report::whereIn('status', ['pending', 'verified'])
    ->where('updated_at', '<=', now()->subHours(24))
    ->each(fn($r) => $r->archive())
```

`archive()` en el modelo:
1. Cambia `status = 'archived'`, `status_changed_at = now()`, `archived_at = now()`
2. `ReportStatusChanged::dispatch($this, $previousStatus)` → evento de broadcasting
