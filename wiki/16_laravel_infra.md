# 16 — Laravel: Infraestructura Docker

## docker-compose.yml — Servicios

### Servicio: `app` (PHP-FPM)

```yaml
build: context: .  # usa el Dockerfile raíz
container_name: api_app
volumes:
  - ./src:/var/www/html:delegated
depends_on: [db]
network: api_net
```

Expone puerto 9000 (fastcgi) internamente. No expuesto al host.

### Servicio: `nginx`

```yaml
image: nginx:alpine
container_name: api_nginx
ports:
  - "8082:80"   # accesible en host:8082
volumes:
  - ./src:/var/www/html:delegated
  - ./nginx/default.conf:/etc/nginx/conf.d/default.conf
depends_on: [app]
```

### Servicio: `cloudflared`

```yaml
image: cloudflare/cloudflared:latest
container_name: api_cloudflared
command: tunnel run --token ${CLOUDFLARE_TUNNEL_TOKEN} --protocol http2
depends_on: [nginx]
restart: unless-stopped
```

Conecta `nginx:80` (interno) → `api.manuelmv.net` (público HTTPS).
Token en `.env` del directorio raíz del proyecto.

### Servicio: `scheduler`

```yaml
build: context: .
container_name: api_scheduler
command: php artisan schedule:work
volumes:
  - ./src:/var/www/html:delegated
depends_on: [db]
restart: unless-stopped
```

Contenedor separado solo para el scheduler. El mismo Dockerfile que `app`.
Ejecuta `schedule:work` que hace loop infinito ejecutando los scheduled commands cuando les toca.

### Servicio: `phpmyadmin`

```yaml
image: phpmyadmin:latest
container_name: api_phpmyadmin
environment:
  PMA_HOST: db
  PMA_USER: apiuser
  PMA_PASSWORD: apipass
depends_on: [db]
```

No expone puerto al host directamente en la config mostrada (accesible internamente en `api_net`).

### Servicio: `db` (MySQL 8)

```yaml
image: mysql:8.0
container_name: api_db
environment:
  MYSQL_ROOT_PASSWORD: rootpass
  MYSQL_DATABASE: apidb
  MYSQL_USER: apiuser
  MYSQL_PASSWORD: apipass
  TZ: America/El_Salvador
command: --sql-mode="NO_ENGINE_SUBSTITUTION,ALLOW_INVALID_DATES,STRICT_TRANS_TABLES"
ports:
  - "3307:3306"   # accesible desde host en puerto 3307
volumes:
  - db_data:/var/lib/mysql   # persistencia en volume Docker
```

---

## Dockerfile

```dockerfile
FROM php:8.4-fpm
ARG UID=1000
ARG GID=1000

# Extensiones PHP
RUN docker-php-ext-install pdo_mysql mbstring exif pcntl bcmath gd

# Composer 2
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

# Usuario no-root con mismo UID/GID que el host (evita problemas de permisos)
RUN groupadd -g ${GID} laravel && useradd -u ${UID} -g ${GID} -m laravel
RUN chown -R laravel:laravel /var/www/html
USER laravel

WORKDIR /var/www/html
EXPOSE 9000
```

Extensiones: `pdo_mysql`, `mbstring`, `exif`, `pcntl`, `bcmath`, `gd` (para procesamiento de imágenes).

---

## nginx/default.conf

```nginx
server {
    listen 80;
    root /var/www/html/public;
    index index.php;

    location / {
        try_files $uri $uri/ /index.php?$query_string;
    }

    location ~ \.php$ {
        fastcgi_pass app:9000;   # contenedor PHP-FPM
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        include fastcgi_params;
    }
}
```

Configuración estándar para Laravel. Todo lo que no sea archivo estático → `index.php`.

---

## Variables de entorno importantes (.env en src/)

| Variable | Uso |
|----------|-----|
| `APP_KEY` | Clave de encriptación de Laravel |
| `DB_HOST=db` | Nombre del contenedor MySQL |
| `DB_DATABASE=apidb` | |
| `DB_USERNAME=apiuser` | |
| `DB_PASSWORD=apipass` | |
| `FIREBASE_CREDENTIALS_PATH` | Path relativo al JSON de Firebase (desde `base_path()`) |
| `BREVO_API_KEY` | API key para envío de emails de reset de contraseña |
| `GOOGLE_CLIENT_ID` | Client ID de Google OAuth (para verificar ID tokens) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Token del tunnel (en `.env` del directorio raíz, no en src/) |

---

## Red Docker

```yaml
networks:
  api_net:
    driver: bridge
```

Todos los contenedores están en `api_net`. Se comunican por nombre de contenedor:
- `nginx` → `app:9000` (fastcgi)
- `app` → `db:3306` (mysql)
- `cloudflared` → `nginx:80` (http)
- `scheduler` → `db:3306` (mysql)
