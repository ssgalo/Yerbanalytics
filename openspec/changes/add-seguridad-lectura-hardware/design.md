# Design: add-seguridad-lectura-hardware

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Ingesta | `be/mqtt/MqttTelemetryReceiver` → `NurseryService.updateTelemetry(zoneId, payload)` (`@Transactional`). Verificado: normaliza el timestamp, ignora lecturas anteriores a la última, y luego asigna cada métrica presente (`zona.setHumSusRaw(...)`, `setTempRaw`, `setCeRaw(ContratoNodo.ceADsPorM(...))`, etc.) **sin ninguna comprobación de rango**. `LecturaZonaReceiver` sólo alimenta a las secuencias de Demo Expo |
| Punto de enganche S-03 | Entre la normalización del timestamp y el bloque `if (pm != null)`: validar cada `pm.*` y omitir la asignación de la inválida (queda el valor anterior, como en una lectura parcial) |
| Validación de rango | **No encontrada** (la búsqueda de rangos físicos sólo devolvió comentarios en `ContratoNodo`) |
| Reintento de lectura | Existe para el **pulso** (`PulsoSensadoService`: reenvía `LEER_AHORA` a la zona pendiente). No es por métrica |
| Frescura por métrica | Sólo humedad de sustrato (`StaleSensorRule` compara `zona.humSusTs`) |
| Hardware por tipo | `HardwareService` (`dispositivo.tipo`, `zonaId`, `sectorId`). Verificado: el nodo testigo se registra **por zona** (uno por MZ); electroválvula, bomba peristáltica y **mediasombra se registran por sector** y se rechaza una segunda del mismo tipo en el mismo sector |

## Decisiones

### D-1 — Dónde se valida
En un `LecturaValidator` puro (sin Spring), invocado por la ingesta **antes** de escribir en la zona. Devuelve
por métrica `VALIDA | INVALIDA`. Pura ⇒ testeable con tabla de casos.

### D-2 — Relectura inmediata
Una zona con alguna métrica inválida dispara un `LEER_AHORA` extra (mismo `ComandoZonaPublisher`), con un
contador en memoria por (zona, métrica) para los 2 reintentos. Se cuenta dentro del pulso en curso; si no hay
pulso en curso (lectura manual) se cuenta igual con su propio contador. Máximo 2 reintentos (parámetro).

### D-3 — "Descartar la métrica" sin tocar las demás
Al descartar, se marca la métrica **inválida en el ciclo** en un `Set` del `RuleContext`; las reglas que la
usan leen `ctx.metricaValida("humSus")` y, si no lo es, registran `SIN_DATO` en su traza (mecanismo que la
traza ya tiene) y no actúan. No se borra el valor anterior de la zona: el dashboard sigue mostrando el último
bueno, marcado como desactualizado.

### D-4 — Escalamiento WARNING → CRITICAL por métrica
Se persiste por (zona, métrica) el último ciclo en que falló (`lectura_invalida`: zona, métrica, ciclo, cuenta).
Segundo ciclo consecutivo ⇒ `CRITICAL`. Una lectura válida limpia el registro.

### D-5 — S-04
`HardwareIncompletoRule` consulta una vista del hardware ya cargada en el `RuleContext` (para no pegarle a la
base por regla). "Requerido" por rama:
| Rama | Requiere |
|---|---|
| RIEGO | electroválvula del sector + nodo testigo de la MZ |
| INSUMO | bomba(s) del sector |
| MEDIASOMBRA | al menos una mediasombra registrada en algún sector de la MZ (el hardware las registra por sector, no por MZ; la v2 habla de la MZ) |
Si falta un actuador, la rama correspondiente aborta (`ABORT_RIEGO`/`ABORT_INSUMO`); no `ABORT_ALL`, porque un
sector sin bomba sí puede regarse.
*Pregunta abierta:* ¿qué bombas se consideran requeridas (fertilizante y fitosanitario) o basta con una?
Depende de `add-dosificacion-base` (D-2). Por ahora: ambas.

### D-6 — Rangos en el catálogo
| Clave | Fábrica | Notas |
|---|---|---|
| `seguridad.rango-hum-min/max` | 0 / 100 | fijos por física, editables sólo hacia adentro |
| `seguridad.rango-temp-min/max` | −10 / 60 | °C |
| `seguridad.rango-ph-min/max` | 3 / 9 | |
| `seguridad.rango-ce-min/max` | 0 / 10 | dS/m (después de convertir desde µS/cm) |
| `seguridad.rango-luz-min/max` | 0 / 100 | % LDR |
| `seguridad.relecturas-invalidas` | 2 | 1–5 |
Restricción cruzada: min < max. Nota: `ce` llega en µS/cm y se persiste en dS/m; la validación va sobre el valor
**ya convertido**.
