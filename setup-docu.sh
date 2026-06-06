#!/bin/bash

# setup-docu.sh
# Instala el sistema de documentación automática en tu proyecto
# Uso: bash setup-docu.sh

set -e

echo "🚀 Instalando sistema de documentación automática..."

# 1. Crear estructura de carpetas
mkdir -p .claude/skills/docu
mkdir -p docu/_index

# 2. Crear CLAUDE.md (o appendear si ya existe)
if [ -f "CLAUDE.md" ]; then
  echo "" >> CLAUDE.md
  echo "---" >> CLAUDE.md
  echo "<!-- Reglas de documentación automática -->" >> CLAUDE.md
  cat << 'EOF' >> CLAUDE.md

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
EOF
  echo "✅ Reglas agregadas a CLAUDE.md existente"
else
  cp "$(dirname "$0")/CLAUDE.md" ./CLAUDE.md 2>/dev/null || echo "⚠️  Copiá CLAUDE.md manualmente"
  echo "✅ CLAUDE.md creado"
fi

# 3. Copiar SKILL.md
cat << 'EOF' > .claude/skills/docu/SKILL.md
# Skill: docu

Actualizá la documentación del módulo según los archivos que acabás de tocar.

## Pasos

1. Detectá el módulo por los archivos modificados
2. Abrí o creá `docu/{modulo}/{modulo}.md`
3. Agregá entrada al final:

```markdown
## [FECHA] Descripción breve

### Archivos tocados
- `ruta/archivo` — qué cambió y por qué

### TODOs / Próximos pasos
- [ ] Próximo paso concreto
```

4. Actualizá `docu/_index/index.md` con: `- [FECHA] [modulo] — descripción`
5. Confirmá: `📝 docu/{modulo}/{modulo}.md actualizado`

## Reglas
- No borrés entradas anteriores
- Creá la carpeta si no existe
- TODOs específicos y accionables
EOF
echo "✅ Skill /docu instalada en .claude/skills/docu/"

# 4. Crear índice
if [ ! -f "docu/_index/index.md" ]; then
cat << 'EOF' > docu/_index/index.md
# Índice General de Documentación

<!-- El agente agrega entradas aquí: -->
<!-- - [YYYY-MM-DD] [modulo] — descripción -->
EOF
echo "✅ Índice creado en docu/_index/index.md"
fi
