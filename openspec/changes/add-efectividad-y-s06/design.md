# Design: add-efectividad-y-s06

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Evaluación | `be/service/HistorialService.evaluarSeguimiento()` (`@Scheduled`, 30 s): para eventos con `evoShow` y `evoEvaluadoTs == null`, si pasó `latencyMs`, compara `humSus` actual de la zona con `valorAntes`; `efectiva = |delta| ≥ umbral`; guarda `evoVerdict`, `bloqueoRepeticion = !efectiva` |
| Alta del seguimiento | `withSeguimiento(e, valorAntes)`: métrica fija `humSus`, "Humedad de sustrato", `%` |
| `FollowUpRule` | prio 20, rama SEGUIMIENTO; llama `evaluarSeguimiento()` y devuelve `NOOP_INFO`; engulle excepciones |
| Quién lee `bloqueoRepeticion` | Verificado (búsqueda en todo `src/main/java`): **nadie**. Lo declara `HistorialEventoEntity` y lo escribe `HistorialService` (en `false` en cada alta y en `!efectiva` al evaluar). Ninguna regla ni servicio lo consulta |
| Lectura de verificación | No existe |
| Sector del nodo testigo | Verificado: `nodo_testigo` se registra **por zona** (`zonaId`, sin `sectorId`). No hay forma de saber en qué sector está; E-01 habla del "sector del nodo testigo" |
| Excepción R-02 | `DeficitCriticoRule` aplica **siempre** el tope de `riego.exceptuado-bloqueo` (12 h) |
| Revisión humana | No existe endpoint |

## Decisiones

### D-1 — Un servicio de efectividad aparte
`EfectividadService` con tres evaluadores (`RiegoEvaluator`, `FertilizanteEvaluator`, `FitosanitarioEvaluator`) detrás de
una interfaz `AccionEvaluable { latencia(); evaluar(evento, contexto) → Efectiva|SinEfectividad|Pendiente }`. El
`@Scheduled` de `HistorialService` se mueve ahí; `HistorialService` queda como persistencia y consulta. Se borra `FollowUpRule`.

### D-2 — Delta de E-01 con signo
`efectiva = (humedadAhora − valorAntes) ≥ 8` (parámetro `efectividad.delta-riego`, 8 puntos, rango 3–20). Se
reemplaza `Math.abs`. Ojo: es un cambio de comportamiento visible (un riego que baja la humedad hoy da "Efectiva" si
cae ≥ umbral).

### D-3 — Lectura de verificación
A los `efectividad.verificacion-min` (30 min, 15–60) del cierre del riego del **sector del nodo testigo**, el servicio
publica `LEER_AHORA` a la MZ y marca ese pedido como **verificación**: la lectura que llega se usa para el evento y
**no** pasa por el motor (principio 7). Mecanismo: banderín `verificacionPendiente(zona)` que `NurseryService.updateTelemetry` consulta para fijar
`ejecutaMotor = false` y origen `OTRA` en el historial de lecturas.
**Sector del nodo testigo (cambio de alcance):** no se puede identificar (verificado). Se agrega `sectorId` opcional al nodo testigo en el registro de
hardware (v2 §12.10, "posición del nodo testigo"). Mientras no esté cargado, E-01 evalúa **cualquier riego** de la MZ (aproximación documentada en la traza).

### D-4 — E-02 / E-03
- E-02: latencia 14 días desde la **primera** aplicación correctiva (F-C2) de una racha; compara el diagnóstico más
  reciente con el que la disparó. "Severidad ≥ 1 nivel menor" con `SeveridadV2`.
- E-03: 7 días; severidad no aumentó **y** ningún vecino nuevo con el mismo diagnóstico (vecinos de
  `add-reglas-biotico` D-6). Un caso "sin diagnóstico vigente a los 7 días" queda `Pendiente` hasta 48 h más y luego
  `Sin efectividad` por falta de dato (decisión abierta, ver abajo).

### D-5 — S-06 como regla de seguridad
`SinEfectividadRule` en la rama correspondiente, con lectura del bloqueo desde el contexto
(`ctx.bloqueoPorEfectividad(tipo, alcance)`):
| Acción | Alcance | Excepción |
|---|---|---|
| Riego | toda la MZ | R-02, máx. 1 riego/12 h por sector |
| Fertilizante correctivo | el sector | el plan de nutrición (N-01) |
| Fitosanitario | el sector | — |
La mediasombra no se bloquea nunca.

### D-6 — Revisión humana
`POST /api/efectividad/{eventoId}/revision {detalle}`: levanta el bloqueo, deja evento con usuario y detalle. Permiso
`efectividad.revisar`. Es la misma que usa F-06 (`add-reglas-biotico`): se crea una vez.

### D-7 — Tope de R-02
Con S-06 en pie, se reevalúa la decisión D-2 de `align-reglas-existentes-v2`: opción (b) dejar el tope **sólo** cuando
el riego está bloqueado por S-06. *Recomendación:* mantenerlo siempre mientras el caudalímetro no exista (la v2 misma dice
que hasta entonces sólo E-01 da indicio), y registrarlo como desviación consciente.

### D-8 — F-05
Evaluador adicional: ante un diagnóstico Sano vigente en un sector con seguimiento abierto de un tratamiento
(correctivo o fitosanitario), cierra el seguimiento como "Efectiva" (`INFO`) antes de la latencia.

### D-9 — Migración
Los eventos de seguimiento existentes (todos de riego) se conservan; el campo `bloqueoRepeticion` histórico no se
reaplica (sólo los eventos evaluados desde este cambio bloquean). Sin script manual: no se tocan columnas.

## Preguntas abiertas
- ¿Qué hacer si no hay diagnóstico vigente al vencer la latencia (E-02/E-03)? Default: `Pendiente` hasta 48 h, luego `Sin efectividad`.
- ¿El bloqueo de riego por S-06 de la MZ levanta solo cuando una verificación posterior sí sube ≥ 8? La v2 dice "hasta que alguien registre la revisión": **no**, sólo la revisión.
