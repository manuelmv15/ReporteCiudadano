# 13 — Laravel: Modelos Eloquent

## User.php

**Traits:** `HasApiTokens` (Sanctum), `HasFactory`, `Notifiable`

**Fillable:** `name`, `email`, `password`, `google_id`, `avatar_url`, `score`, `level`, `fcm_token`, `onboarding_done`

**Hidden:** `password`, `remember_token`

**Casts:**
- `email_verified_at` → datetime
- `password` → hashed (Laravel 11: auto-hash en asignación)
- `onboarding_done` → boolean
- `score` → integer

### Sistema de niveles

```php
const LEVEL_NUEVO       = 'nuevo'       // 0+ puntos
const LEVEL_COLABORADOR = 'colaborador' // 20+ puntos
const LEVEL_GUARDIAN    = 'guardian'    // 100+ puntos
const LEVEL_EXPERTO     = 'experto'     // 300+ puntos

const SCORE_COLABORADOR = 20
const SCORE_GUARDIAN    = 100
const SCORE_EXPERTO     = 300
```

### `addScore(int $points)`

```php
$this->increment('score', $points);
$level = self::levelForScore($this->score);
if ($level !== $this->level) {
    $this->update(['level' => $level]);
}
```

Incrementa atómico en DB. Recalcula nivel solo si cambió (evita UPDATE innecesario).

### `levelForScore(int $score): string`

```php
return match(true) {
    $score >= 300 => 'experto',
    $score >= 100 => 'guardian',
    $score >= 20  => 'colaborador',
    default       => 'nuevo',
};
```

---

## Report.php

**Fillable:** `user_id`, `category_id`, `latitude`, `longitude`, `description`, `photo_path`, `status`, `status_changed_at`, `votes_confirm`, `votes_resolve`, `verified_at`, `resolved_at`, `archived_at`

**Relaciones:**
- `user()` → BelongsTo User
- `category()` → BelongsTo Category
- `votes()` → HasMany ReportVote

**Casts:**
- `latitude`, `longitude` → decimal:7
- `votes_confirm`, `votes_resolve` → integer
- `status_changed_at`, `verified_at`, `resolved_at`, `archived_at` → datetime

### Constantes de umbrales

```php
// Confirmación (RF-11/RF-30)
VOTES_CONFIRM_THRESHOLD        = 5   // votos confirm para pasar a verified (camino normal)
VOTES_CONFIRM_THRESHOLD_EXPERT = 3   // si hay un experto, bastan 3

// Resolución (RF-12)
RESOLVE_MIN_TOTAL_VOTES  = 3     // mínimo votos totales para evaluar resolve
RESOLVE_RATIO_THRESHOLD  = 0.7   // 70% de votos deben ser resolve

// Scoring (RF-27)
SCORE_OWNER_VERIFIED   = 10  // dueño cuando reporte pasa a verified
SCORE_CONFIRM_MATCH    = 2   // votantes confirm cuando se verifica
SCORE_RESOLVE_MATCH    = 5   // votantes resolve cuando se resuelve

// Archivado (RF-13/RF-18)
RESOLVED_VISIBLE_HOURS = 2   // horas que un resolved es visible antes de archivar
STALE_HOURS            = 24  // horas sin interacción para archivar pending/verified
```

### `distanceInMetersTo(float $latitude, float $longitude): float`

Implementa la fórmula de Haversine:

```php
$earthRadius = 6371000;  // metros
$a = sin(Δlat/2)² + cos(lat1) * cos(lat2) * sin(Δlon/2)²
return $earthRadius * 2 * atan2(sqrt($a), sqrt(1-$a))
```

Usada en `ReportVoteController` para validar los 500m.

### `evaluateAutoStatus()` — ver 12_laravel_votos.md

### `archiveStaleReports(): array`

Static method. Usada por el Artisan command `reports:archive-stale`. Ver 12_laravel_votos.md.

---

## ReportVote.php

**Fillable:** `report_id`, `user_id`, `type`

**Relaciones:**
- `report()` → BelongsTo Report
- `user()` → BelongsTo User

Sin lógica adicional. Toda la lógica está en el controller o el modelo Report.

---

## Category.php

**Fillable:** `name`, `slug`, `icon`, `active`

**Casts:** `active` → boolean

**Relaciones:**
- `reports()` → HasMany Report

### Categorías seedeadas (CategorySeeder)

| id | name | slug | icon |
|----|------|------|------|
| 1 | Vialidad | vialidad | (icono) |
| 2 | Alumbrado | alumbrado | (icono) |
| 3 | Agua | agua | (icono) |
| 4 | Tráfico | trafico | (icono) |
| 5 | Seguridad | seguridad | (icono) |
| 6 | Basura | basura | (icono) |
| 7 | Otros | otros | (icono) |

El mapeo en Android (`CategoryMapper.java`) convierte slug → ID numérico para el request.
