# Features — Estado vs Planteamiento v1

> Actualizar cada vez que se implementa una función nueva.
> Última revisión: 2026-06-09

**Leyenda:** ✅ completo · 🔶 a medias · ❌ no iniciado

---

## Módulo A — Autenticación

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-A01 | Registro con correo/contraseña o Google OAuth | ✅ | LoginActivity + RegisterActivity + /auth/google |
| RF-A02 | Sesión persistente con token en EncryptedSharedPreferences | 🔶 | Token guardado en SharedPreferences normal, pendiente migrar a EncryptedSharedPreferences |
| RF-A03 | Recuperación de contraseña vía correo (API Laravel SMTP) | ❌ | No implementado en Android ni en API |
| RF-A04 | Modo invitado: ver mapa sin auth, bloquear crear/votar con 401 | ❌ | No existe flujo guest |
| RF-A05 | Perfil (nombre, avatar) creado automáticamente en primer login | 🔶 | API crea usuario pero app no muestra perfil completo |
| RF-A06 | Cerrar sesión elimina token del dispositivo y lo revoca en servidor | ✅ | handleLogout() en UserProfileBottomSheet |

---

## Módulo 1 — Reporte ultrarrápido

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-01 | Toque largo en mapa → menú radial con 8 categorías | ✅ | RadialMenuDialogFragment + OnMapLongClickListener |
| RF-02 | Toque en categoría envía reporte sin formulario adicional | ✅ | POST /reports desde RadialMenuDialogFragment |
| RF-03 | GPS capturado del punto tocado, sin confirmación extra | ✅ | coords del LongClick pasadas directamente |
| RF-04 | Foto y descripción opcionales desde tarjeta del reporte | ❌ | BottomSheet no tiene opción de editar foto/descripción post-creación |
| RF-05 | Sin conexión: guardar en Room DB + WorkManager sync | ❌ | No hay Room DB ni WorkManager |
| RF-06 | Retirar propio reporte en primeros 5 min si < 3 votos | ❌ | No implementado |

---

## Módulo 2 — Sistema de votos comunitarios

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-07 | Solo usuarios dentro de 500 m pueden votar | 🔶 | Validación Haversine en cliente (VoteStateManager), falta validación server-side confirmada |
| RF-08 | Votos "Sigue ahí" y "Ya se resolvió" | ✅ | ReportDetailBottomSheet con ambos botones |
| RF-09 | Un voto por usuario; puede cambiarlo hasta 5 min después | 🔶 | Cambio de voto con dialog implementado; ventana 5 min en VoteStateManager pero pendiente verificar lógica server |
| RF-10 | Conteo actualizado en tiempo real tras respuesta servidor | 🔶 | Se actualiza al recibir respuesta pero no hay polling/WebSocket |
| RF-11 | Auto-cierre cuando votos "Ya se resolvió" >= 70% con min 3 | 🔶 | Lógica en API Laravel (pendiente confirmar); cliente refleja estado RESOLVED |
| RF-12 | Sello "Verificado" al llegar a 5 votos "Sigue ahí" | 🔶 | Estado VERIFIED en cliente; marcador cambia; sello visual no diferenciado claramente |
| RF-13 | Auto-archivo a las 24 h sin interacción | ❌ | Solo en servidor (pendiente confirmar); cliente no muestra reportes archivados |
| RF-14 | Creador puede votar "Ya se resolvió" en su propio reporte | 🔶 | No hay restricción explícita; flujo normal permite votar el propio reporte |
| RF-15 | Concurrencia de votos con bloqueo optimista en servidor | 🔶 | Documentado en API; cliente no aplica lógica de conflicto (correcto según spec) |

---

## Módulo 3 — Mapa en vivo

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-16 | Marcadores crecen proporcionalmente a votos "Sigue ahí" | 🔶 | Tamaño varía por estado (PENDING/VERIFIED/RESOLVED) pero no proporcional a conteo exacto de votos |
| RF-17 | Reportes verificados muestran ícono distinto y mayor tamaño | 🔶 | CircleAnnotation más grande para VERIFIED; sin ícono especial (círculo vs ícono) |
| RF-18 | Reportes resueltos en gris durante 2 h antes de desaparecer | 🔶 | Color gris para RESOLVED pero no desaparecen automáticamente después de 2 h |
| RF-19 | Filtros por categoría, estado y antigüedad (1h/6h/24h) | ❌ | No hay filtros en el mapa |
| RF-20 | Tocar marcador → tarjeta con categoría, votos, foto, botones | 🔶 | ReportDetailBottomSheet muestra categoría y votos; foto no implementada |

---

## Módulo 4 — Alertas de proximidad

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-21 | Notificación push al acercarse a < 300 m de reporte activo | ❌ | FCM no integrado |
| RF-22 | Notificación con botones "Sigue ahí" / "Ya se resolvió" | ❌ | FCM no integrado |
| RF-23 | Usuario configura categorías de alertas y radio (100–500 m) | ❌ | No hay pantalla de configuración |
| RF-24 | Alertas solo si usuario en movimiento (ActivityRecognition API) | ❌ | No implementado |
| RF-25 | No más de 1 alerta del mismo reporte por usuario en 2 horas | ❌ | FCM no integrado |

---

## Módulo 5 — Puntuación y confiabilidad

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-26 | Score de confiabilidad por reportes y votos acertados | ❌ | No implementado en cliente |
| RF-27 | Puntos: +10 reporte confirmado, +2 voto "Sigue ahí", +5 voto "Ya se resolvió" | ❌ | No implementado en cliente |
| RF-28 | Reportes de usuarios con score alto = mayor tamaño base en mapa | ❌ | No implementado |
| RF-29 | Niveles: Nuevo/Colaborador/Guardián/Experto | ❌ | No implementado en cliente |
| RF-30 | Expertos verifican reporte con 3 votos en lugar de 5 | ❌ | No implementado |

---

## Módulo 6 — Perfil e historial

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-31 | Ver todos los reportes propios con votos, sello y estado final | ❌ | UserProfileBottomSheet tiene opción "Mis Reportes" pero no navega a ningún lugar |
| RF-32 | Historial de votos propios con accuracy % por tipo | ❌ | No implementado |
| RF-33 | Estadísticas: reportes creados, confirmaciones recibidas, problemas resueltos | ❌ | No implementado |

---

## Módulo 7 — Onboarding

| ID | Requerimiento | Estado | Notas |
|----|--------------|--------|-------|
| RF-34 | Onboarding automático en primer registro | ❌ | No implementado |
| RF-35 | Botón "Omitir" en cualquier momento | ❌ | No implementado |
| RF-36 | Onboarding accesible desde ajustes | ❌ | No hay pantalla de ajustes |
| RF-37 | Estado "onboarding completado" en SharedPreferences | ❌ | No implementado |

---

## Resumen de progreso

| Módulo | Total RF | ✅ Completos | 🔶 A medias | ❌ No iniciados |
|--------|----------|-------------|------------|----------------|
| Autenticación (A) | 6 | 2 | 2 | 2 |
| Reporte ultrarrápido (1) | 6 | 3 | 0 | 3 |
| Votos comunitarios (2) | 9 | 1 | 7 | 1 |
| Mapa en vivo (3) | 5 | 0 | 4 | 1 |
| Alertas de proximidad (4) | 5 | 0 | 0 | 5 |
| Puntuación y confiabilidad (5) | 5 | 0 | 0 | 5 |
| Perfil e historial (6) | 3 | 0 | 0 | 3 |
| Onboarding (7) | 4 | 0 | 0 | 4 |
| **TOTAL** | **43** | **6 (14%)** | **13 (30%)** | **24 (56%)** |
