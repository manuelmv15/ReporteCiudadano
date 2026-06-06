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
