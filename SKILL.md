# Skill: docu

Actualizá la documentación del módulo correspondiente según los archivos que acabás de tocar.

## Pasos

1. **Detectá el módulo** revisando qué archivos modificaste en esta acción.
2. **Abrí o creá** el archivo `docu/{modulo}/{modulo}.md`.
3. **Agregá una nueva entrada** al final del archivo con este formato exacto:

```markdown
## [FECHA-HOY] Descripción breve

### Archivos tocados
- `ruta/archivo` — qué cambió y por qué

### TODOs / Próximos pasos
- [ ] Próximo paso concreto
- [ ] Otro pendiente si aplica
```

4. **Actualizá el índice** en `docu/_index/index.md` agregando una línea:
   `- [FECHA] [modulo] — descripción breve`

5. Confirmá con un mensaje corto: `📝 docu/{modulo}/{modulo}.md actualizado`

## Reglas
- No resumás ni condensés entradas anteriores
- Si no existe la carpeta del módulo, creála
- Los TODOs deben ser específicos y accionables
- Una entrada por cada acción significativa, no una por sesión
