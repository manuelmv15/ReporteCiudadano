<div align="center">

# Reporte Ciudadano

### Reporta problemas de tu ciudad en dos toques y deja que la comunidad los verifique

![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Mapbox](https://img.shields.io/badge/Mapbox-000000?style=flat&logo=mapbox&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=flat&logo=firebase&logoColor=black)
![Laravel](https://img.shields.io/badge/API-Laravel-FF2D20?style=flat&logo=laravel&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24-3DDC84?style=flat)

</div>

---

## ¿Qué es?

Reporte Ciudadano es una app Android para reportar problemas de infraestructura urbana: baches, alumbrado dañado, fugas de agua, basura acumulada, problemas de tráfico o de seguridad.

Funciona con una mecánica parecida a Waze. Las personas que están cerca de un problema votan si **sigue ahí** o si **ya se resolvió**, y el reporte cambia de estado solo según esos votos. No hace falta que un operador municipal cierre los reportes.

## Funcionalidades

**Reportar**
- Reporte en dos toques: toque largo en el mapa, eliges la categoría en un menú radial y se envía.
- Reporte rápido con el botón **+**, que usa tu ubicación GPS.
- Foto y descripción opcionales, que se pueden agregar después.
- Funciona sin conexión: los reportes y votos se guardan en el teléfono (Room) y se sincronizan al volver la red (WorkManager).
- Puedes retirar tu propio reporte en los primeros 5 minutos si tiene menos de 3 votos.

**Votar**
- Solo pueden votar las personas que están a menos de 500 m del reporte.
- Un voto por persona, que se puede cambiar durante 5 minutos.
- Los reportes cambian de estado solos (ver [Ciclo de vida de un reporte](#ciclo-de-vida-de-un-reporte)).

**Mapa en vivo**
- Mapa de Mapbox con marcadores por categoría y estado.
- Filtros por categoría, estado y antigüedad (1 h, 6 h, 24 h).
- Carga solo los reportes de la zona visible.
- Iluminación del mapa según la hora del día, o fija si la eliges en ajustes.

**Alertas de proximidad**
- Notificación push (Firebase Cloud Messaging) al acercarte a un reporte activo, con botones para votar desde la misma notificación.
- Configuras qué categorías te interesan y el radio de alerta (100 a 500 m).
- Solo avisa cuando te estás moviendo y no repite el mismo reporte en 2 horas.

**Perfil y reputación**
- Inicio de sesión con correo o con Google, y modo invitado para ver el mapa sin cuenta.
- Puntaje de confiabilidad: +10 por reporte confirmado, +2 por voto "Sigue ahí" y +5 por voto "Ya se resolvió".
- Niveles: Nuevo, Colaborador, Guardián y Experto. Los marcadores de usuarios con más puntaje se ven más grandes.
- Historial de tus reportes y tus votos, con estadísticas.
- Tutorial al registrarte, que se puede volver a ver desde ajustes.

## Ciclo de vida de un reporte

| Estado | Cuándo pasa | En el mapa |
|---|---|---|
| **Pendiente** | Al crearse el reporte | Color de su categoría |
| **Verificado** | Con 5 votos "Sigue ahí" (los usuarios nivel Experto verifican con 3) | Verde, marcador más grande |
| **Resuelto** | Cuando "Ya se resolvió" llega al 70 % de los votos, con un mínimo de 3 votos | Gris durante 2 h y luego desaparece |
| **Archivado** | Después de 24 h sin ninguna interacción | Ya no se muestra |

El detalle de las reglas, con ejemplos, está en [`CRITERIOS_VOTOS.md`](CRITERIOS_VOTOS.md).

## Categorías

Vialidad · Alumbrado · Agua · Tráfico · Seguridad · Basura

## Tecnologías

| Área | Herramientas |
|---|---|
| App | Java, Android SDK 36 (mínimo Android 7.0 / API 24), Material Design 3, View Binding |
| Mapa y ubicación | Mapbox Maps SDK 11, Google Play Services Location, Activity Recognition |
| Red | Retrofit, OkHttp, Gson, Glide |
| Datos locales | Room, WorkManager, EncryptedSharedPreferences |
| Notificaciones | Firebase Cloud Messaging |
| Login | Google Sign-In |
| Backend | API REST en Laravel con Sanctum: [JefersonDeLaCruz/laravel_api](https://github.com/JefersonDeLaCruz/laravel_api) |

## Cómo compilarlo

### Requisitos

- Android Studio (o JDK 17+ y Android SDK 36 si compilas desde la terminal)
- Un token público de Mapbox (empieza con `pk.`). Se crea gratis en [account.mapbox.com](https://account.mapbox.com/)

### Pasos

1. Clona el repositorio:

   ```bash
   git clone https://github.com/manuelmv15/ReporteCiudadano.git
   cd ReporteCiudadano
   ```

2. Agrega tu token de Mapbox al archivo `local.properties`, en la raíz del proyecto. Android Studio crea este archivo solo; si no existe, créalo:

   ```properties
   MAPBOX_ACCESS_TOKEN=pk.tu_token_de_mapbox
   ```

   Este archivo no se sube al repositorio.

3. Compila e instala en un dispositivo o emulador:

   ```bash
   ./gradlew installDebug
   ```

   O ábrelo en Android Studio y presiona **Run**.

### Backend

La app se conecta a la API definida en `BASE_URL` dentro de [`ApiClient.java`](app/src/main/java/com/bombayashi/reporteciudadano/network/ApiClient.java). Para usar tu propio servidor, levanta [laravel_api](https://github.com/JefersonDeLaCruz/laravel_api) y cambia esa URL. Desde el emulador de Android, tu computadora es `http://10.0.2.2:8000/api/`.

## Estructura

```
app/src/main/java/com/bombayashi/reporteciudadano/
├── *Activity.java  # Login, registro, recuperación de contraseña, onboarding y pantalla principal
├── ui/             # Mapa, menú radial, detalle del reporte, perfil
├── network/        # Cliente Retrofit y servicios de la API
├── model/          # Modelos de datos (reportes, votos, usuarios)
├── db/             # Base de datos local con Room (caché y acciones pendientes)
├── work/           # Sincronización en segundo plano con WorkManager
├── service/        # Notificaciones push de Firebase
└── util/           # Sesión, ajustes, notificaciones y helpers
```

## Documentos

- [`ReporteCiudadano_v1_planteamiento.pdf`](ReporteCiudadano_v1_planteamiento.pdf): planteamiento inicial del proyecto y sus requerimientos.
- [`CRITERIOS_VOTOS.md`](CRITERIOS_VOTOS.md): reglas de cambio de estado por votos.

## Equipo

| Nombre | GitHub |
|---|---|
| Carlos Manuel Meléndez Villatoro | [@manuelmv15](https://github.com/manuelmv15) |
| César Josué Zuleta Villalobos | [@CesarZV23005](https://github.com/CesarZV23005) |
| Jeferson Alexis De La Cruz Ventura | [@JefersonDeLaCruz](https://github.com/JefersonDeLaCruz) |
