# 🤖 CLAUDE.md — Reglas del Agente

> Este archivo define el comportamiento del agente en cada sesión de trabajo.
> Se aplica automáticamente en todo momento.

---

## 📁 Sistema de Documentación Automática

Toda sesión de trabajo **debe documentarse en tiempo real** en la carpeta `docu/`.
No esperar al final — actualizar el archivo correspondiente **a medida que se trabaja**.

### 🗂️ Tabla de módulos

| Si editás archivos en...          | Documentá en...                     |
|----------------------------------|-------------------------------------|
| `**/api/**` o `**/routes/**`     | `docu/api/api.md`                   |
| `**/login/**` o `*auth*`         | `docu/login/login.md`               |
| `**/dashboard/**`                | `docu/dashboard/dashboard.md`       |
| `**/components/**`               | `docu/components/components.md`     |
| `**/db/**` o `**/models/**`      | `docu/db/db.md`                     |
| Cualquier otro                   | `docu/general/general.md`           |

Si un cambio toca **múltiples módulos**, actualizar **cada archivo correspondiente**.

---

## ✍️ Formato de entrada en cada `.md`

```markdown
## [YYYY-MM-DD] Título corto de lo que se hizo

### Archivos tocados
- `ruta/al/archivo.ext` — qué se hizo ahí

### TODOs / Próximos pasos
- [ ] Tarea pendiente concreta 1
- [ ] Tarea pendiente concreta 2
```

---

## 📋 Reglas estrictas

1. **Documentar ANTES de pasar al siguiente paso**, no al final.
2. Si `docu/modulo/modulo.md` no existe, **crearlo**.
3. Siempre actualizar `docu/_index/index.md` con un resumen de una línea.
4. Los TODOs deben ser **accionables y concretos**, no genéricos.
5. **Nunca borrar entradas anteriores** — solo agregar al final.
6. Cada entrada lleva fecha real del día en que se realiza el trabajo.

---

## 🔌 Foco: Módulo API

el agente debe:

- Documentar **toda ruta** creada o modificada en `docu/api/api.md`
- Registrar: método HTTP, path, descripción, parámetros esperados, respuesta esperada
- Anotar dependencias externas (DBs, servicios, middlewares)
- Marcar rutas como `[ ] pendiente`, `[x] implementada` o `[!] con bug conocido`

### Plantilla extra para rutas API

```markdown
### Ruta: [MÉTODO] /path/endpoint

| Campo        | Detalle                         |
|-------------|---------------------------------|
| Método      | GET / POST / PUT / DELETE        |
| Auth        | Sí / No / Bearer Token          |
| Body        | `{ campo: tipo }` o N/A         |
| Respuesta   | `{ campo: tipo }`               |
| Estado      | [ ] pendiente / [x] lista / [!] bug |
| Notas       | Observaciones adicionales       |
```

---

## 🗃️ Estructura esperada de `docu/`

```
docu/
├── _index/
│   └── index.md        ← resumen general de todos los cambios
├── api/
│   └── api.md          
├── login/
│   └── login.md
├── dashboard/
│   └── dashboard.md
├── components/
│   └── components.md
├── db/
│   └── db.md
└── general/
    └── general.md
```


---
<!-- Reglas de documentación automática -->

# 📁 Sistema de Documentación Automática

Documentá en tiempo real en `docu/` según los archivos que tocás:

| Archivos en...            | Documentá en...                   |
|--------------------------|-----------------------------------|
| `**/login/**`, `*auth*`  | `docu/login/login.md`             |
| `**/dashboard/**`        | `docu/dashboard/dashboard.md`     |
| `**/api/**`, `**/routes/**` | `docu/api/api.md`              |
| `**/components/**`       | `docu/components/components.md`   |
| `**/db/**`, `**/models/**` | `docu/db/db.md`                |
| Cualquier otro           | `docu/general/general.md`         |

Formato de cada entrada:

```markdown
## [YYYY-MM-DD] Título

### Archivos tocados
- `ruta/archivo` — qué cambió

### TODOs / Próximos pasos
- [ ] Tarea concreta
```

Reglas: documentá ANTES de pasar al siguiente paso. Actualizá siempre `docu/_index/index.md`.
