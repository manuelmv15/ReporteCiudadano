# 🗳️ Criterios de Cambio de Estado en Votos

## Estados Disponibles

Un reporte puede estar en uno de estos 3 estados:

### 1️⃣ **PENDING** (Por defecto)
- **Condición:** Reporte acaba de crearse, sin cambios
- **Visual:** Marker con color de su categoría (naranja, verde, azul, etc)
- **Radio del marker:** 28.0
- **Descripción:** "Aún no hay suficientes votos para cambiar el estado"

---

### 2️⃣ **VERIFIED** (Verificado ✓)
- **Condición:** **5 o más votos "Sigue ahí"**
- **Visual:** Marker **VERDE BRILLANTE** (#4CAF50)
- **Radio del marker:** 35.0 (más grande)
- **Descripción:** "Múltiples usuarios confirman que el problema sigue activo"

**Ejemplo:**
```
Reporte tiene:
✅ 5 votos "Sigue ahí"
❌ 1 voto "Ya se resolvió"
Total: 6 votos

Resultado: VERIFIED (porque confirmCount >= 5)
```

---

### 3️⃣ **RESOLVED** (Resuelto)
- **Condición:** **70% o más votos "Ya se resolvió" Y mínimo 3 votos totales**
- **Visual:** Marker **GRIS** (#9E9E9E)
- **Radio del marker:** 30.0
- **Descripción:** "La mayoría de usuarios confirma que el problema fue resuelto"

**Ejemplos:**
```
Caso 1:
✅ 1 voto "Sigue ahí"
❌ 2 votos "Ya se resolvió"
Total: 3 votos
Porcentaje "resolvió": 66.7% (2/3)
Resultado: PENDING (necesita 70%)

Caso 2:
✅ 0 votos "Sigue ahí"
❌ 7 votos "Ya se resolvió"
Total: 7 votos
Porcentaje "resolvió": 100% (7/7)
Resultado: RESOLVED (100% >= 70% ✓)

Caso 3:
✅ 2 votos "Sigue ahí"
❌ 7 votos "Ya se resolvió"
Total: 9 votos
Porcentaje "resolvió": 77.8% (7/9)
Resultado: RESOLVED (77.8% >= 70% ✓)
```

---

## 🎯 Reglas Importantes

1. **PENDING es la "línea base"** — Siempre comienza así
2. **VERIFIED y RESOLVED son mutuamente excluyentes** — Un reporte no puede ser ambos
3. **VERIFIED se dispara ANTES que RESOLVED** — Si tienes 5+ "Sigue ahí" pero solo 1 "Ya se resolvió" → VERIFIED
4. **Mínimo 3 votos totales para RESOLVED** — Con 2 votos, aunque sea 100% "Ya se resolvió", no cambia
5. **Los conteos se actualizan en tiempo real** — Cada nuevo voto puede cambiar el estado

---

## 📊 Tabla Resumen

| Estado | Condición | Visual | Radio |
|--------|-----------|--------|-------|
| **PENDING** | Menos de 5 "Sigue ahí" Y (menos de 3 votos O menos del 70% "Resolvió") | Color categoría | 28.0 |
| **VERIFIED** | confirmCount >= 5 | Verde (#4CAF50) | 35.0 |
| **RESOLVED** | resolveCount >= (total * 0.7) AND total >= 3 | Gris (#9E9E9E) | 30.0 |

---

## 🧮 Lógica de Determinación

```pseudocode
function determineStatus(confirmCount, resolveCount):
    total = confirmCount + resolveCount
    
    // Verificado: prioridad 1
    if confirmCount >= 5:
        return VERIFIED
    
    // Resuelto: prioridad 2
    if total >= 3 AND (resolveCount / total) >= 0.70:
        return RESOLVED
    
    // Por defecto
    return PENDING
```

---

## 🧪 Cómo Testear

### Test 1: Alcanzar VERIFIED
1. Crea un reporte
2. Vota "Sigue ahí" desde el mismo usuario (si puedes) o desde 5 usuarios diferentes
3. El marker **debe cambiar a VERDE** cuando confirmCount alcance 5

### Test 2: Alcanzar RESOLVED
1. Crea un reporte
2. Obtén 7 votos "Ya se resolvió" + 0 "Sigue ahí"
3. El marker **debe cambiar a GRIS** cuando resolveCount / 7 = 100% >= 70%

### Test 3: Prioridad VERIFIED sobre RESOLVED
1. Crea un reporte
2. Vota 5 veces "Sigue ahí" + 3 veces "Ya se resolvió"
3. El marker **debe estar VERDE** (VERIFIED tiene prioridad)

---

## ⚙️ Implementación en Código

**Archivo:** `VoteStateManager.java`

```java
private ReportStatusUpdate.Status determineStatus(int confirmCount, int resolveCount) {
    int totalVotes = confirmCount + resolveCount;

    // Verificado: 5+ votos "Sigue ahí"
    if (confirmCount >= 5) {
        return ReportStatusUpdate.Status.VERIFIED;
    }

    // Resuelto: 70%+ votos "Ya se resolvió" (mínimo 3 votos totales)
    if (totalVotes >= 3 && (resolveCount / (double) totalVotes) >= 0.7) {
        return ReportStatusUpdate.Status.RESOLVED;
    }

    return ReportStatusUpdate.Status.PENDING;
}
```

---

## 📲 Cómo Se Ve en la App

### Botones en ReportDetailBottomSheet
- **"Sigue ahí"** → Vota que el problema aún existe
- **"Ya se resolvió"** → Vota que el problema fue solucionado

### Contador de Votos
```
X confirman, Y dicen que ya se resolvió
```
(Ej: "5 confirman, 2 dicen que ya se resolvió")

### Color del Marker en el Mapa
- **Naranja/Verde/Azul** (color de categoría) → PENDING
- **Verde Brillante** → VERIFIED ✓
- **Gris** → RESOLVED

---

**¿Dudas?** Los criterios están hardcodeados y se aplican automáticamente cada vez que se vota.
