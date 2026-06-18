# Bugs — Análisis Técnico Detallado
> Generado: 2026-06-18 | Basado en código fuente real

---

## BUG 1 — Polling cada 5s en vez de 30s

**Archivos:** `MapFragment.java:103` y `:115` | **Estado: ✅ Corregido**

La app tiene dos "relojes" que corren mientras el mapa está visible. Uno consulta al servidor si hay reportes nuevos o modificados. El otro revisa si hay reportes cercanos en los que el usuario puede votar. Ambos relojes estaban configurados para dispararse cada 5 segundos, cuando el valor correcto de producción es 30 segundos — alguien dejó el intervalo corto de testing y olvidó cambiarlo.

El efecto concreto: por cada usuario con la app abierta, el servidor recibe 24 consultas por minuto en vez de 4. Con 100 usuarios simultáneos eso son 2.400 requests por minuto innecesarios. Además, cada consulta despierta la CPU del dispositivo, acelerando el consumo de batería.

---

## BUG 2 — Ventana de edición de voto siempre reinicia

**Archivo:** `VoteStateManager.java:77–81` | **Estado: ✅ Corregido**

Cuando un usuario vota en un reporte, tiene 5 minutos para arrepentirse y cambiar o retirar su voto (RF-04). El servidor registra exactamente cuándo votó el usuario. El problema es que la app ignoraba ese timestamp del servidor y recalculaba la ventana desde el momento en que el usuario abría el panel del reporte — no desde cuando votó.

El resultado es que la ventana de 5 minutos se "reiniciaba" cada vez que el usuario cerraba y volvía a abrir el panel. Un usuario podía votar, esperar 4 minutos y 50 segundos, abrir el panel de nuevo, y tener otros 5 minutos completos para retractarse. La restricción temporal no funcionaba.

---

## BUG 3 — Thread de base de datos nunca se cierra

**Archivo:** `MapFragment.java:128` y `:1580` | **Estado: ✅ Corregido**

El mapa usa un hilo de trabajo dedicado para escribir en la base de datos local sin bloquear la UI. El problema es que ese hilo se creaba cuando el Fragment nacía pero nunca se cerraba cuando el Fragment moría.

En Android, cuando el usuario rota la pantalla o navega hacia atrás y vuelve al mapa, el sistema destruye y recrea el Fragment. Cada recreación generaba un nuevo hilo huérfano. Estos hilos además mantenían una referencia al contexto del Fragment destruido, lo que impedía que el garbage collector liberara esa memoria. En sesiones largas o con rotaciones frecuentes, esto acumula memoria que nunca se libera.

---

## BUG 4 — Forma obsoleta de pedir la ubicación

**Archivo:** `MapFragment.java:394–398` | **Estado: ✅ Corregido**

La app usaba una forma antigua de configurar las actualizaciones de ubicación GPS que Google marcó como obsoleta en versiones recientes de sus servicios. Aunque funcionaba, en versiones nuevas de Android esto genera advertencias en el sistema y podría tener comportamiento inesperado a medida que Google actualice sus librerías y deje de dar soporte al método viejo. Se migró a la API actual recomendada.

---

## BUG 5 — Caché de íconos del mapa sin límite de tamaño

**Archivo:** `MapFragment.java:93` | **Estado: ✅ Corregido**

Cada marcador en el mapa es una imagen (bitmap) generada con el ícono de la categoría del reporte, su color y su estado. Para no regenerar esas imágenes cada vez, la app las guarda en un caché. El problema es que ese caché era un diccionario simple sin límite: cada imagen nueva que se generaba se sumaba sin nunca eliminar las anteriores.

Con hasta 200 reportes en pantalla, cada uno con variantes según categoría y estado, el caché podía crecer a decenas de megabytes durante una sesión larga sin reiniciar la app. Esto presiona la RAM disponible y puede causar que Android cierre la app por falta de memoria. Se reemplazó por un LruCache que automáticamente descarta las imágenes menos usadas cuando llega a 100 entradas.

---

## BUG 6 — La app calculaba el estado del reporte en vez de preguntarle al servidor

**Archivo:** `VoteStateManager.java:390–403` | **Estado: ✅ Corregido**

Después de que un usuario vota, la app necesita mostrar si el reporte cambió de estado (pendiente → verificado → resuelto). En vez de usar el estado que el servidor devuelve directamente, la app lo recalculaba ella misma contando los votos.

El problema es que la lógica del servidor es más compleja: los usuarios con rol "Experto" tienen votos que pesan más (RF-30). La app no sabía eso. Entonces podía haber un reporte que el servidor ya marcó como verificado (porque votaron dos Expertos) pero la app lo seguía mostrando como pendiente hasta el próximo ciclo de polling. Se eliminó el recálculo local y ahora se usa directamente el estado que viene del servidor.

---

## BUG 7 — Se creaba un hilo nuevo por cada voto offline

**Archivo:** `VoteStateManager.java:203` | **Estado: ✅ Corregido**

Cuando el usuario vota sin conexión a internet, la app guarda el voto localmente para enviarlo después. Para hacer esa escritura en la base de datos sin bloquear la pantalla, usaba un hilo de trabajo. El bug es que creaba un hilo nuevo y desechable por cada voto offline, sin cerrar nunca el anterior.

Si el usuario vota varios reportes sin conexión (algo completamente válido), cada voto dejaba un hilo huérfano en memoria. Se corrigió usando un único hilo de instancia reutilizable, que se cierra cuando se cierra el panel del reporte.

---

## BUG 8 — Variable declarada que nunca se usó

**Archivo:** `MapFragment.java:91` | **Estado: ✅ Corregido**

Había una estructura de datos reservada para mantener círculos visuales de estado en el mapa, pero nunca se escribió en ella ni se leyó en ningún lugar del código. Ocupaba memoria y podía confundir a cualquiera que leyera el código asumiendo que esa funcionalidad estaba implementada. Se eliminó.

---

## BUG 9 — El mapa se trunca a 200 reportes sin avisar

**Archivo:** `MapFragment.java:86` y `:495–498` | **Estado: ⚠️ Pendiente**

La app tiene un límite interno de 200 reportes en pantalla para no sobrecargar la memoria. Cuando se alcanza ese límite, silenciosamente deja de mostrar reportes nuevos. No hay ningún mensaje al usuario, ni badge, ni indicación de ningún tipo.

Un usuario en el centro de Buenos Aires, donde hay alta densidad de reportes, puede estar viendo un mapa incompleto sin saberlo — simplemente faltan reportes y no hay forma de darse cuenta. El límite es razonable técnicamente, pero debe comunicarse. Fix pendiente: mostrar una notificación discreta cuando se alcanza el límite.

---

## Resumen de severidad real

| Bug | Severidad | Estado | Efecto en usuario |
|-----|-----------|--------|-------------------|
| POLL 5s | Alta | ✅ Corregido | Costos de servidor 6x, batería del dispositivo |
| VoteEditableUntil | Alta | ✅ Corregido | RF-04 (retracción) roto lógicamente |
| dbExecutor leak | Media | ✅ Corregido | Memory leak progresivo, posible ANR en sesiones largas |
| LocationRequest deprecated | Baja | ✅ Corregido | Warnings en build, posible comportamiento futuro roto |
| bitmapCache sin LRU | Media | ✅ Corregido | OOM en sesiones largas con muchos reportes |
| determineStatus() | Media | ✅ Corregido | Estado de reporte incorrecto en UI por hasta 30s |
| Executor por voto offline | Media | ✅ Corregido | Thread leak acumulativo en uso offline intenso |
| reportStatusCircles | Baja | ✅ Corregido | Desperdicio de memoria, confusión |
| MAX_REPORTS silencioso | Media | ⚠️ Pendiente | Usuario ve mapa incompleto sin explicación |
