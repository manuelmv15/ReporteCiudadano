# 14 — Laravel: Base de Datos

Motor: MySQL 8.0. DB: `apidb`. Puerto externo: 3307.

## Tabla: `users`

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | bigint unsigned AI PK | |
| `name` | varchar(255) | |
| `email` | varchar(255) UNIQUE | |
| `password` | varchar(255) | Bcrypt hash |
| `google_id` | varchar(255) NULL UNIQUE | Sub de Google |
| `avatar_url` | varchar(255) NULL | URL local o de Google |
| `score` | int DEFAULT 0 | Puntuación acumulada |
| `level` | varchar DEFAULT 'nuevo' | nuevo/colaborador/guardian/experto |
| `fcm_token` | varchar NULL | Token FCM Android |
| `onboarding_done` | boolean DEFAULT false | |
| `email_verified_at` | timestamp NULL | |
| `remember_token` | varchar NULL | |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

## Tabla: `password_reset_tokens`

| Campo | Tipo | Notas |
|-------|------|-------|
| `email` | varchar PK | |
| `token` | varchar | Hash bcrypt del código 6 dígitos |
| `created_at` | timestamp NULL | Para verificar expiración (60 min) |

## Tabla: `personal_access_tokens` (Sanctum)

| Campo | Tipo |
|-------|------|
| `id` | bigint AI PK |
| `tokenable_type` | varchar |
| `tokenable_id` | bigint |
| `name` | varchar |
| `token` | varchar(64) UNIQUE |
| `abilities` | text NULL |
| `last_used_at` | timestamp NULL |
| `expires_at` | timestamp NULL |
| `created_at` | timestamp |
| `updated_at` | timestamp |

## Tabla: `categories`

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | bigint AI PK | |
| `name` | varchar | Ej: "Vialidad" |
| `slug` | varchar | Ej: "vialidad" |
| `icon` | varchar | Nombre de ícono |
| `active` | boolean DEFAULT true | |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

## Tabla: `reports`

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | bigint AI PK | |
| `user_id` | bigint FK → users.id CASCADE DELETE | |
| `category_id` | bigint FK → categories.id CASCADE DELETE | |
| `latitude` | decimal(10,7) | Hasta 7 decimales |
| `longitude` | decimal(10,7) | |
| `description` | varchar | max 500 chars |
| `photo_path` | varchar NULL | Ruta relativa en storage/public/reports/ |
| `status` | enum('pending','verified','resolved','archived') DEFAULT 'pending' | |
| `status_changed_at` | timestamp NULL | Última vez que cambió el status |
| `votes_confirm` | int DEFAULT 0 | |
| `votes_resolve` | int DEFAULT 0 | |
| `verified_at` | timestamp NULL | Cuándo pasó a verified |
| `resolved_at` | timestamp NULL | Cuándo pasó a resolved |
| `archived_at` | timestamp NULL | Cuándo fue archivado |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

## Tabla: `report_votes`

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | bigint AI PK | |
| `report_id` | bigint FK → reports.id CASCADE DELETE | |
| `user_id` | bigint FK → users.id CASCADE DELETE | |
| `type` | enum('confirm','resolve') | |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

**Índice único:** `(report_id, user_id)` — un usuario solo puede tener UN voto por reporte (no uno por tipo). Migración `2026_06_12` cambió el índice de `(report_id, user_id, type)` a `(report_id, user_id)`.

## Tablas Laravel estándar

- `cache` — caché de Laravel (database driver)
- `jobs` — queue jobs (database driver)
- `sessions` — sesiones web (no usadas en API-only mode)

## Diagrama de relaciones

```
users
  ├── reports (user_id)
  │       ├── categories (category_id)
  │       └── report_votes (report_id)
  │                └── users (user_id)
  └── personal_access_tokens (tokenable)
```

## Configuración MySQL en Docker

```yaml
environment:
  TZ: America/El_Salvador
command: --sql-mode="NO_ENGINE_SUBSTITUTION,ALLOW_INVALID_DATES,STRICT_TRANS_TABLES"
```

`ALLOW_INVALID_DATES` evita errores con fechas edge-case de MySQL. `STRICT_TRANS_TABLES` mantiene integridad estricta en inserts.
