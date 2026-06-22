# Bugs — Análisis Técnico Detallado
> Actualizado: 2026-06-22

---

## BUG 9 — El mapa se trunca a 200 reportes sin avisar

**Archivos:** `MapFragment.java:90,121,495–502` | **Estado: ✅ Corregido**

La app tiene un límite interno de 200 reportes en pantalla. El path de carga de viewport (fetch inicial) ya mostraba un snackbar al alcanzar el límite. Pero el path de `applyReportUpdate()` — que se ejecuta en cada ciclo de polling — saltaba silenciosamente el reporte nuevo sin ningún aviso al usuario.

Fix: se agregó el flag `maxReportsWarningShown` (instancia del Fragment). Cuando `applyReportUpdate()` descarta un reporte por límite, muestra el snackbar informativo una sola vez por sesión de mapa (el flag evita spam cada 30s). El flag se resetea en `clearAll()` para que al volver a una zona densa se vuelva a avisar.
