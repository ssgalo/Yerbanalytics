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

## Decisiones de la implementación

Nombres en inglés (CLAUDE.md §7): `LecturaValidator` → `ReadingValidator`, `lectura_invalida` → `invalid_reading`,
`ctx.metricaValida` → `ctx.isMetricValid`, `HardwareIncompletoRule` → `IncompleteHardwareRule`. Los de la spec que ya
estaban en inglés (`ReadingValidationGuard`, bypass `retries` y `hardware`, escenarios `INVALID_READING` y
`INCOMPLETE_HARDWARE`) no cambian.

### S-03

- **Piezas.** `ReadingMetrics` (la lectura en unidades canónicas: es el único lugar donde la CE pasa de µS/cm a dS/m),
  `ReadingValidator` (puro: qué métricas están fuera de rango), `ReadingValidationGuard` (relectura y descarte; es el
  `DemoControllable`) e `InvalidReadingEscalation` (la racha persistida y las alertas). Separar la escalación del guard
  deja a cada una con una sola razón para cambiar.
- **Métricas con rango.** Humedad (sustrato y ambiente), temperatura (aire y **también la del sustrato**: es una
  temperatura y el rango físico es el mismo), pH, CE y luz. N, P y K no tienen rango en la v2: nunca se invalidan.
- **Rangos editables sólo hacia adentro.** Cada mínimo llega hasta un pivote (humedad 50, temperatura 25, pH 7, CE 5,
  luz 50) y cada máximo parte de él. Así `min < max` es una restricción alcanzable y nadie puede ensanchar un rango
  más allá de lo físico (humedad y luz quedan acotadas a 0–100).
- **La lectura inválida no consume el pulso.** El motor decide con la primera respuesta de cada zona a un pulso. Si esa
  respuesta trae una métrica inválida con relecturas disponibles, el pulso sigue pendiente (no se llama a
  `recordResponse`) y el motor decide cuando llega la relectura que corrige o la tercera inválida, que ya descarta la
  métrica. Sin esto, "la relectura corrige" no tendría efecto sobre la decisión de riego del ciclo.
- **Descartar no borra.** El valor previo queda en la zona (el dashboard lo sigue mostrando) y su marca `humSusTs` no
  avanza; las reglas de riego leen la humedad por `RiegoRuleSupport.humedad`, que usa `ctx.validMetricRaw` y devuelve
  "sin dato". Es el único punto de lectura: R-01…R-06 y la regla de ciclo lo heredan.
- **Contador de relecturas por ciclo.** Vive en memoria, por (zona, métrica, ciclo de lectura): un pedido que nunca se
  contestó no deja al ciclo siguiente con menos relecturas. Un reinicio lo vuelve a cero (cuesta una relectura de más).
- **Racha por ciclo.** Un segundo descarte en el mismo ciclo (p. ej. una lectura manual además del pulso) no suma. Del
  tercer ciclo seguido en adelante la alerta sigue siendo `CRITICAL` (literal de la spec: "vuelve a fallar en el ciclo
  siguiente"); la racha se limpia con cualquier lectura válida de la métrica.
- **Cableado.** El guard es consumidor de parámetros y el catálogo se arma con todos los consumidores: depende con
  `@Lazy` de `CatalogoParametrosService` **y de `ConfiguracionService`** (éste depende del catálogo). Sin lo segundo el
  contexto no arranca; `ReadingValidationGuardWiringTest` lo cubre con el cableado real.
- **Migración.** `invalid_reading` es una entidad nueva: `ddl-auto=update` la crea. Sin script manual.
