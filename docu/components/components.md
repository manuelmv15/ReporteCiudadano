# Documentación — Componentes

## [2026-06-07] SnackbarHelper — componente reutilizable MD3

### Archivos tocados
- `app/src/main/java/com/bombayashi/reporteciudadano/ui/SnackbarHelper.java` — nuevo helper estático con 4 variantes
- `app/src/main/res/drawable/ic_snackbar_error.xml` — ícono vector 20dp
- `app/src/main/res/drawable/ic_snackbar_success.xml` — ícono vector 20dp
- `app/src/main/res/drawable/ic_snackbar_info.xml` — ícono vector 20dp
- `app/src/main/res/drawable/ic_snackbar_warning.xml` — ícono vector 20dp
- `app/src/main/res/values/colors.xml` — colores semánticos MD3 para cada variante
- `app/src/main/res/values/dimens.xml` — archivo nuevo con escala de espaciado centralizada

### API pública

```java
// Básico (LENGTH_LONG)
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.ERROR);
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.SUCCESS);
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.INFO);
SnackbarHelper.show(view, "mensaje", SnackbarHelper.Variant.WARNING);

// Corto
SnackbarHelper.showShort(view, "mensaje", SnackbarHelper.Variant.SUCCESS);

// Con acción (LENGTH_INDEFINITE)
SnackbarHelper.showWithAction(view, "mensaje", Variant.INFO, "Reintentar", v -> { ... });
```

### Colores por variante

| Variante | Background  | Text/Icon  |
|----------|-------------|------------|
| ERROR    | `#B3261E`   | `#FFFFFF`  |
| SUCCESS  | `#1E8E3E`   | `#FFFFFF`  |
| INFO     | `#0B57D0`   | `#FFFFFF`  |
| WARNING  | `#E37400`   | `#FFFFFF`  |

### Anchor view recomendado
Siempre pasar `findViewById(android.R.id.content)` como anchor para que el Snackbar aparezca en la parte inferior de la activity.

### TODOs / Próximos pasos
- [ ] Agregar soporte para mostrar Snackbar de SUCCESS al completar registro/login exitoso
- [ ] Considerar variante `NEUTRAL` usando `colorInverseSurface` del tema MD3 para mensajes genéricos
